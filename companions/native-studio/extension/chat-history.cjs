// SPDX-License-Identifier: Apache-2.0
'use strict';
const crypto=require('node:crypto'),fs=require('node:fs'),path=require('node:path');
const keyName=id=>'chat-history.key.'+id;
class ChatHistory{
    constructor(directory,secrets){
        fs.mkdirSync(directory,{recursive:true,mode:0o700});
        this.db=new (require('node:sqlite').DatabaseSync)(path.join(directory,'chat-history.sqlite'));
        this.secrets=secrets;
        this.db.exec(`PRAGMA busy_timeout=3500; PRAGMA secure_delete=ON;
            CREATE TABLE IF NOT EXISTS options(enabled INTEGER NOT NULL);
            INSERT INTO options SELECT 0 WHERE NOT EXISTS(SELECT 1 FROM options);
            CREATE TABLE IF NOT EXISTS chats(id TEXT PRIMARY KEY, scope TEXT NOT NULL, version INTEGER NOT NULL,
                updated INTEGER NOT NULL, nonce BLOB NOT NULL, tag BLOB NOT NULL, body BLOB NOT NULL);`);
    }
    enabled(){return !!this.db.prepare('SELECT enabled FROM options').get().enabled;}
    enable(value){this.db.prepare('UPDATE options SET enabled=?').run(value?1:0);}
    async recent(scope){
        if(!this.enabled())return [];
        const rows=this.db.prepare('SELECT * FROM chats WHERE scope=? ORDER BY updated DESC LIMIT 50').all(scope);
        const result=[];
        for(const row of rows.reverse()){
            const stored=await this.secrets.get(keyName(row.id));
            if(!stored)continue;
            const cipher=crypto.createDecipheriv('aes-256-gcm',Buffer.from(stored,'hex'),row.nonce);
            cipher.setAAD(Buffer.from(JSON.stringify([row.id,scope,row.version])));cipher.setAuthTag(row.tag);
            const value=JSON.parse(Buffer.concat([cipher.update(row.body),cipher.final()]).toString('utf8'));
            result.push([row.id,{...value,version:row.version}]);
        }
        return result;
    }
    async save(scope,id,version,value){
        if(!this.enabled())return;
        let key=version?await this.secrets.get(keyName(id)):undefined;
        // Concurrent windows may continue the same transcript. Keep both continuations.
        if(!key&&version){id=crypto.randomUUID();version=0;}
        if(!key){
            key=crypto.randomBytes(32).toString('hex');
            await this.secrets.store(keyName(id),key);
        }
        const nonce=crypto.randomBytes(12),cipher=crypto.createCipheriv('aes-256-gcm',Buffer.from(key,'hex'),nonce);
        cipher.setAAD(Buffer.from(JSON.stringify([id,scope,version+1])));
        const encrypted=Buffer.concat([cipher.update(JSON.stringify(value),'utf8'),cipher.final()]);
        if(!this.enabled()){if(!version)await this.secrets.delete(keyName(id));return;}
        const args=[version+1,Date.now(),nonce,cipher.getAuthTag(),encrypted,id,scope];
        if(version){
            const change=this.db.prepare('UPDATE chats SET version=?,updated=?,nonce=?,tag=?,body=? WHERE id=? AND scope=? AND version=? AND (SELECT enabled FROM options)=1').run(...args,version);
            if(!change.changes)return this.save(scope,crypto.randomUUID(),0,value);
        }else{
            const change=this.db.prepare('INSERT INTO chats(version,updated,nonce,tag,body,id,scope) SELECT ?,?,?,?,?,?,? WHERE (SELECT enabled FROM options)=1').run(...args);
            if(!change.changes){await this.secrets.delete(keyName(id));return;}
        }
        return {id,version:version+1};
    }
    async forget(id){
        this.db.prepare('DELETE FROM chats WHERE id=?').run(id);
        await this.secrets.delete(keyName(id));
    }
    close(){this.db.close();}
}
module.exports={ChatHistory};

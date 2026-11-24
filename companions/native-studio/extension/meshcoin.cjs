// SPDX-License-Identifier: Apache-2.0
'use strict';
const crypto=require('node:crypto');
const fs=require('node:fs');
const path=require('node:path');
const DOMAIN='meshlit.meshcoin.v1';
const ZERO='0'.repeat(64);
const HEX=/^[a-f0-9]{64}$/;
const MAX=10000;
const sha=(...data)=>{const h=crypto.createHash('sha256');for(const d of data)h.update(d);return h.digest('hex');};
const walletId=key=>sha(Buffer.from(key,'hex'));
const bytes=(...parts)=>Buffer.from(parts.join('\n'));
const receiptBytes=r=>bytes('meshlit.contribution.v1',r.jobId,r.nodeId,r.generatedTokens,r.awardedAtMs,r.previousHash,r.publicKey);
const receiptHash=r=>sha(receiptBytes(r),Buffer.from(r.signature,'hex'));
const blockBytes=b=>bytes(DOMAIN,b.height,b.previous,b.receiptHash,b.keeper,b.bps);
const sign=(key,message)=>crypto.sign('sha256',message,key).toString('hex');
function verify(key,message,sig){
    if(typeof sig!=='string'||!/^(?:[a-f0-9]{2}){1,80}$/.test(sig))return false;
    const publicKey=crypto.createPublicKey({key:Buffer.from(key,'hex'),format:'der',type:'spki'});
    return publicKey.asymmetricKeyDetails?.namedCurve==='prime256v1'&&crypto.verify('sha256',message,publicKey,Buffer.from(sig,'hex'));
}
function validUUID(s){return typeof s==='string'&&/^[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}$/i.test(s);}
function checkBlock(b,key,node,prev){
    const r=b.receipt;
    if(!r||r.nodeId!==node||r.publicKey!==key||typeof r.jobId!=='string'||!r.jobId.trim()||r.jobId.length>128||
      !Number.isInteger(r.generatedTokens)||r.generatedTokens<1||r.generatedTokens>32768||!Number.isSafeInteger(r.awardedAtMs)||r.awardedAtMs<=0||
      r.previousHash!==(prev?.receiptHash||'')||!verify(key,receiptBytes(r),r.signature)||receiptHash(r)!==b.receiptHash||
      b.height!==(prev?.height||0)+1||b.previous!==(prev?.hash||ZERO)||!Number.isInteger(b.bps)||b.bps<0||b.bps>10000||
      (b.keeper!==''&&(!HEX.test(b.keeper)||b.keeper===walletId(key)))||(!b.keeper&&b.bps!==0)||
      !verify(key,blockBytes(b),b.signature)||sha(blockBytes(b),Buffer.from(b.signature,'hex'))!==b.hash)throw Error('MeshCoin block or receipt did not verify');
}
const keeperUnits=b=>Math.floor(b.receipt.generatedTokens*b.bps/10000);
function coinSerial(b,index){
    if(!Number.isInteger(index)||index<1||index>b.receipt.generatedTokens)throw Error('Choose a coin in this block');
    return crypto.createHash('sha512').update(bytes(DOMAIN,'coin',b.receiptHash,index)).digest('hex');
}
const ackBytes=(hash,keeper)=>bytes(DOMAIN,'recorded',hash,keeper);
class Wallet{
    constructor(dir,identity){
        this.identity=identity;this.owner=walletId(identity.key);
        fs.mkdirSync(dir,{recursive:true,mode:0o700});
        const {DatabaseSync}=require('node:sqlite');
        this.db=new DatabaseSync(path.join(dir,'meshcoin.sqlite'));
        this.db.exec(`PRAGMA busy_timeout=3500; PRAGMA journal_mode=WAL;
CREATE TABLE IF NOT EXISTS blocks(owner TEXT,height INTEGER,hash TEXT UNIQUE,job TEXT,body TEXT,PRIMARY KEY(owner,height),UNIQUE(owner,job));
CREATE TABLE IF NOT EXISTS acks(hash TEXT PRIMARY KEY,proof TEXT);
CREATE TABLE IF NOT EXISTS config(k TEXT PRIMARY KEY,v TEXT);
CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY,stamp TEXT,session TEXT,event TEXT,peer TEXT);`);
    }
    close(){this.db.close();}
    config(k,fallback){const row=this.db.prepare('SELECT v FROM config WHERE k=?').get(k);return row?JSON.parse(row.v):fallback;}
    set(k,v){this.db.prepare('INSERT OR REPLACE INTO config VALUES (?,?)').run(k,JSON.stringify(v));}
    audit(session,event,peer=''){
        this.db.prepare('INSERT INTO audit(stamp,session,event,peer) VALUES (?,?,?,?)').run(new Date().toISOString(),session,event,peer);
        this.db.exec('DELETE FROM audit WHERE id <= (SELECT MAX(id)-1000 FROM audit)');
    }
    events(){return this.db.prepare('SELECT stamp,session,event,peer FROM audit ORDER BY id DESC LIMIT 60').all();}
    chain(owner=this.owner){return this.db.prepare('SELECT body FROM blocks WHERE owner=? ORDER BY height').all(owner).map(r=>JSON.parse(r.body));}
    verified(owner=this.owner,key=this.identity.key,node=this.identity.node){
        const blocks=this.chain(owner);let prev;
        for(const b of blocks){checkBlock(b,key,node,prev);prev=b;}
        return blocks;
    }
    transaction(fn){
        this.db.exec('BEGIN IMMEDIATE');
        try{const value=fn();this.db.exec('COMMIT');return value;}
        catch(e){this.db.exec('ROLLBACK');throw e;}
    }
    insert(owner,b){this.db.prepare('INSERT INTO blocks VALUES(?,?,?,?,?)').run(owner,b.height,b.hash,b.receipt.jobId,JSON.stringify(b));}
    record(job,tokens,policy){
        if(!this.config('record',false)||!Number.isInteger(tokens)||tokens<1||tokens>32768)return;
        return this.transaction(()=>{
            if(this.db.prepare('SELECT 1 FROM blocks WHERE owner=? AND job=?').get(this.owner,job))return;
            const prev=this.verified().at(-1);
            if((prev?.height||0)>=MAX)throw Error('Wallet receipt limit reached');
            const {node,key,privateKey}=this.identity;
            const r={jobId:job,nodeId:node,generatedTokens:tokens,awardedAtMs:Date.now(),previousHash:prev?.receiptHash||'',publicKey:key};
            r.signature=sign(privateKey,receiptBytes(r));
            const b={height:(prev?.height||0)+1,previous:prev?.hash||ZERO,receiptHash:receiptHash(r),receipt:r,keeper:policy?.keeper||'',bps:policy?.bps||0};
            b.signature=sign(privateKey,blockBytes(b));b.hash=sha(blockBytes(b),Buffer.from(b.signature,'hex'));
            checkBlock(b,key,node,prev);this.insert(this.owner,b);return b;
        });
    }
    page(key,height,anchor){
        if(!this.config('share:'+walletId(key),false))throw Error('The contributor must enable receipt sharing for this device.');
        if(!Number.isInteger(height)||height<0||height>MAX)throw Error('Invalid chain cursor');
        const head=height?this.db.prepare('SELECT hash FROM blocks WHERE owner=? AND height=?').get(this.owner,height)?.hash:ZERO;
        if(anchor!==head)throw Error('The peer has a different chain history');
        return this.db.prepare('SELECT body FROM blocks WHERE owner=? AND height>? ORDER BY height LIMIT 16').all(this.owner,height).map(x=>JSON.parse(x.body));
    }
    acceptPage(peer,blocks){
        if(!Array.isArray(blocks)||blocks.length>16)throw Error('Invalid chain page');
        const owner=walletId(peer.key);
        if(owner===this.owner)throw Error('Cannot import a replica of this wallet');
        this.transaction(()=>{
            let previous=this.verified(owner,peer.key,peer.node).at(-1);
            for(const b of blocks){
                if(b.height>MAX)throw Error('Peer wallet exceeds receipt limit');
                if(previous&&b.height<=previous.height){
                    if(this.db.prepare('SELECT hash FROM blocks WHERE owner=? AND height=?').get(owner,b.height)?.hash!==b.hash)throw Error('Conflicting chain history');
                    continue;
                }
                checkBlock(b,peer.key,peer.node,previous);this.insert(owner,b);previous=b;
            }
        });
    }
    acknowledge(peer,hash,proof){
        const keeper=walletId(peer),row=this.db.prepare('SELECT body FROM blocks WHERE owner=? AND hash=?').get(this.owner,hash);
        if(!row||JSON.parse(row.body).keeper!==keeper||!verify(peer,ackBytes(hash,keeper),proof))throw Error('Recordkeeper signature does not match the agreed block');
        this.db.prepare('INSERT OR IGNORE INTO acks VALUES(?,?)').run(hash,JSON.stringify({key:peer,signature:proof}));
    }
    async sync(peer,call){
        const owner=walletId(peer.key);
        let last=this.verified(owner,peer.key,peer.node).at(-1);
        for(;;){
            const {blocks}=await call({op:'walletPage',height:last?.height||0,anchor:last?.hash||ZERO});
            if(!Array.isArray(blocks)||blocks.length>16||blocks.some((b,i)=>b.height!==(last?.height||0)+i+1))throw Error('Invalid or repeated chain page');
            this.acceptPage(peer,blocks);if(blocks.length)last=blocks.at(-1);
            if(blocks.length<16)break;
        }
        for(const b of this.verified(owner,peer.key,peer.node)){
            if(b.keeper!==this.owner||this.db.prepare('SELECT 1 FROM acks WHERE hash=?').get(b.hash))continue;
            const proof=sign(this.identity.privateKey,ackBytes(b.hash,this.owner));
            const answer=await call({op:'walletAck',hash:b.hash,proof});
            if(answer.ok!==true)throw Error('The contributor did not confirm the recordkeeping acknowledgment');
            this.db.prepare('INSERT OR IGNORE INTO acks VALUES(?,?)').run(b.hash,JSON.stringify({key:this.identity.key,signature:proof}));
        }
    }
    hasAcknowledgment(b){
        const row=this.db.prepare('SELECT proof FROM acks WHERE hash=?').get(b.hash);
        if(!row)return false;
        const {key,signature}=JSON.parse(row.proof);
        if(walletId(key)!==b.keeper||!verify(key,ackBytes(b.hash,b.keeper),signature))throw Error('Invalid recordkeeper acknowledgment');
        return true;
    }
    backup(){
        return this.transaction(()=>{
            this.totals();
            const rows=this.db.prepare('SELECT body FROM blocks ORDER BY owner,height').all();
            const blocks=rows.map(r=>JSON.parse(r.body));
            const acks=this.db.prepare('SELECT hash,proof FROM acks').all().map(r=>({hash:r.hash,...JSON.parse(r.proof)}));
            return {format:'meshlit.meshcoin.backup.v1',owner:this.owner,node:this.identity.node,blocks,acks};
        });
    }
    restore(data){
        if(data?.format!=='meshlit.meshcoin.backup.v1'||data.owner!==this.owner||data.node!==this.identity.node||
            !Array.isArray(data.blocks)||!Array.isArray(data.acks)||data.acks.length>data.blocks.length)throw Error('Choose a MeshCoin backup for this node identity.');
        return this.transaction(()=>{
            const tips=new Map();
            for(const b of data.blocks){
                const r=b.receipt,owner=walletId(r.publicKey),previous=tips.get(owner);
                if(b.height>MAX||!validUUID(r.nodeId)||(owner===this.owner&&r.nodeId!==this.identity.node)||
                    (previous&&previous.receipt.nodeId!==r.nodeId))throw Error('Invalid backup identity or block height.');
                checkBlock(b,r.publicKey,r.nodeId,previous);
                const existing=this.db.prepare('SELECT hash FROM blocks WHERE owner=? AND height=?').get(owner,b.height);
                if(existing&&existing.hash!==b.hash)throw Error('Conflicting backup history.');
                if(!existing)this.insert(owner,b);
                tips.set(owner,b);
            }
            for(const {hash,key,signature} of data.acks){
                const row=this.db.prepare('SELECT body FROM blocks WHERE hash=?').get(hash);
                if(!row||JSON.parse(row.body).keeper!==walletId(key)||!verify(key,ackBytes(hash,walletId(key)),signature))throw Error('Invalid backup recordkeeper signature.');
                this.db.prepare('INSERT OR IGNORE INTO acks VALUES(?,?)').run(hash,JSON.stringify({key,signature}));
            }
            this.totals();
        });
    }
    totals(){
        let earned=0,pending=0,paid=0,kept=0,generated=0;
        const blocks=this.verified(),keeperBlocks=[];
        for(const b of blocks){const count=b.receipt.generatedTokens,cut=keeperUnits(b);generated+=count;earned+=count-cut;
            if(this.hasAcknowledgment(b))paid+=cut;else pending+=cut;}
        const receipts=this.db.prepare('SELECT DISTINCT owner FROM blocks WHERE owner<>?').all(this.owner);
        for(const {owner} of receipts){
            const first=this.db.prepare('SELECT body FROM blocks WHERE owner=? AND height=1').get(owner);
            const {publicKey,nodeId}=JSON.parse(first.body).receipt;
            if(walletId(publicKey)!==owner)throw Error('Replica contributor identity changed');
            for(const block of this.verified(owner,publicKey,nodeId))if(block.keeper===this.owner&&this.hasAcknowledgment(block)){kept+=keeperUnits(block);keeperBlocks.push(block);}
        }
        return {blocks,keeperBlocks,generated,earned,kept,paid,pending};
    }
}
module.exports={Wallet,walletId,coinSerial,keeperUnits,checkBlock,validUUID,ZERO};

'use strict';
const test=require('node:test'),assert=require('node:assert/strict');
const {buildCommand,sshIntent,htmlSource}=require('./build-tools.cjs');
test('SSH chat actions recognize project tasks and build fixed reviewed commands',()=>{
 assert.deepEqual(sshIntent('Please SSH to desktop and build my app.'),{action:'build',host:'desktop'});
 assert.deepEqual(sshIntent('/ssh test laptop'),{action:'test',host:'laptop'});
 assert.equal(sshIntent('ssh to desktop and run a shell'),undefined);
 assert.equal(buildCommand('Linux or macOS','/srv/My app','cargo','test'),"cd '/srv/My app' && cargo test");
 const windows=buildCommand('Windows','C:\\Work\\app','npm','build').split(' ').at(-1);
 assert.match(Buffer.from(windows,'base64').toString('utf16le'),/npm\.cmd run build/);
 assert.throws(()=>buildCommand('Linux or macOS',"/srv/my'app",'npm','test'));
 assert.throws(()=>buildCommand('Windows','C:\\app; autre','npm','build'));
});
test('web app generation opens only complete reviewable HTML',()=>{
 assert.equal(htmlSource('```html\n<html><p>hello</p></html>\n```'),'<html><p>hello</p></html>');
 assert.throws(()=>htmlSource('<html><p>incomplete'));
});
test('multi-file app writes only new source files and the generated npm app actually builds',async t=>{
 const {projectFiles,saveProject}=require('./build-tools.cjs'),fs=require('node:fs/promises'),os=require('node:os'),path=require('node:path');
 const root=await fs.mkdtemp(path.join(os.tmpdir(),'meshlit-project-test-'));t.after(()=>fs.rm(root,{recursive:true,force:true}));
 const json=files=>JSON.stringify({files});
 for(const name of ['../other','C:/unsafe','AUX.txt','.vscode/tasks.json','sub//file'])assert.throws(()=>projectFiles(json([{path:name,content:'x'}])));
 assert.throws(()=>projectFiles(json([{path:'Main',content:'a'},{path:'main/a',content:'b'}])));
 const files=projectFiles(json([{path:'package.json',content:JSON.stringify({scripts:{build:'node src/build.cjs'}})},{path:'src/build.cjs',content:"require('node:fs').writeFileSync('result.txt','built')"}]));
 const dir=await saveProject(root,files);
 require('node:child_process').execSync('npm run build',{cwd:dir,stdio:'pipe'});
 assert.equal(await fs.readFile(path.join(dir,'result.txt'),'utf8'),'built');
});

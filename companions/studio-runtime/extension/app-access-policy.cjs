const {performance}=require('node:perf_hooks');
const ACTIONS=new Set(['snapshot','press','set_value']);
const BLOCKED=new Set(['com.apple.systempreferences','com.apple.SecurityAgent','com.apple.keychainaccess','com.apple.Passwords','com.apple.loginwindow','com.apple.AuthorizationHost','com.meshlit.desktop']);
const TASK_OPERATIONS=25,TASK_LIFETIME_MS=10*60*1000;
class AppAccessPolicy {
 constructor({clock=()=>performance.now()}={}){this.clock=clock;this.stop();}
 stop(){this.apps=new Set();this.grants=new Map();this.taskEpochs=new Map();this.generation=(this.generation||0)+1;}
 allow(apps){if(!Array.isArray(apps)||apps.length>10||apps.some(id=>typeof id!=='string'||!id.match(/^[A-Za-z0-9][A-Za-z0-9.-]{1,179}$/)||BLOCKED.has(id)))throw Error('Choose up to ten supported applications');this.stop();this.apps=new Set(apps);}
 validate(args){
  if(!args||!ACTIONS.has(args.action)||!this.apps.has(args.bundle_id)||BLOCKED.has(args.bundle_id))throw Error('App access is off or outside the selected scope');
  if(args.action!=='snapshot'&&(typeof args.node_id!=='string'||!args.node_id.match(/^[A-Fa-f0-9-]{36}$/)))throw Error('Take a fresh app snapshot before acting');
  if(args.action==='set_value'&&(typeof args.text!=='string'||Buffer.byteLength(args.text)>8192))throw Error('Text must be at most 8 KiB');
  return this.generation;
 }
 taskScope(binding){
  if(binding===undefined)return undefined;
  if(!binding||typeof binding.owner!=='string'||!binding.owner.match(/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i)||typeof binding.model!=='string'||!binding.model.trim()||binding.model.length>2048||typeof binding.task!=='string'||!binding.task.trim()||binding.task.length>16000)throw Error('App access task needs a conversation, selected model and human task');
  return Object.freeze({owner:binding.owner,model:binding.model,task:binding.task,generation:this.generation,epoch:this.taskEpochs.get(binding.owner)||0});
 }
 validateTask(scope){
  if(!scope||scope.generation!==this.generation||scope.epoch!==(this.taskEpochs.get(scope.owner)||0))throw Error('App access task ended or was revoked');
 }
 grantTask(args,scope){
  this.validate(args);this.validateTask(scope);
  const key=scope.owner+'\0'+args.bundle_id;
  if(this.grants.has(key)){this.checkGrant(this.grants.get(key),scope);return;}
  this.grants.set(key,{...scope,bundle:args.bundle_id,remaining:TASK_OPERATIONS,expires:this.clock()+TASK_LIFETIME_MS});
 }
 checkGrant(grant,scope){
  this.validateTask(scope);
  if(grant.generation!==scope.generation||grant.epoch!==scope.epoch||grant.owner!==scope.owner||grant.model!==scope.model||grant.task!==scope.task)throw Error('App access task or selected model changed');
  if(this.clock()>=grant.expires)throw Error('App access task approval expired. Start a new task to review access again');
  if(grant.remaining<=0)throw Error('App access task reached its 25-operation limit. Start a new task to review access again');
 }
 consumeTask(args,scope){
  this.validate(args);this.validateTask(scope);
  const grant=this.grants.get(scope.owner+'\0'+args.bundle_id);
  if(!grant)return false;
  this.checkGrant(grant,scope);grant.remaining--;return true;
 }
 endTask(owner){
  if(typeof owner!=='string')return;
  this.taskEpochs.set(owner,(this.taskEpochs.get(owner)||0)+1);
  for(const [key,grant]of this.grants)if(grant.owner===owner)this.grants.delete(key);
 }
}
module.exports={AppAccessPolicy,TASK_OPERATIONS,TASK_LIFETIME_MS};

const ACTIONS=new Set(['snapshot','press','set_value']);
const BLOCKED=new Set(['com.apple.systempreferences','com.apple.SecurityAgent','com.apple.keychainaccess','com.apple.Passwords','com.apple.loginwindow','com.apple.AuthorizationHost','com.meshlit.desktop']);
class AppAccessPolicy {
 constructor(){this.stop();}
 stop(){this.apps=new Set();this.generation=(this.generation||0)+1;}
 allow(apps){if(!Array.isArray(apps)||apps.length>10||apps.some(id=>typeof id!=='string'||!id.match(/^[A-Za-z0-9][A-Za-z0-9.-]{1,179}$/)||BLOCKED.has(id)))throw Error('Choose up to ten supported applications');this.stop();this.apps=new Set(apps);}
 validate(args){
  if(!args||!ACTIONS.has(args.action)||!this.apps.has(args.bundle_id)||BLOCKED.has(args.bundle_id))throw Error('App access is off or outside the selected scope');
  if(args.action!=='snapshot'&&(typeof args.node_id!=='string'||!args.node_id.match(/^[A-Fa-f0-9-]{36}$/)))throw Error('Take a fresh app snapshot before acting');
  if(args.action==='set_value'&&(typeof args.text!=='string'||Buffer.byteLength(args.text)>8192))throw Error('Text must be at most 8 KiB');
  return this.generation;
 }
}
module.exports={AppAccessPolicy};

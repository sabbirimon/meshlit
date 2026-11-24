'use strict';
const fs=require('node:fs'),path=require('node:path');
function resolveRuntime(directory,config){
 if(config?.schema===undefined||config?.schema===1)return config;
 if(config.schema!==2)throw Error('Unsupported runtime manifest version');
 if(config.kind!=='portable'||!config.backend||!Array.isArray(config.backend.classpath)||config.backend.classpath.length<1||config.backend.classpath.length>8)throw Error('Invalid portable runtime manifest');
 const root=fs.realpathSync(directory);
 function local(relative,glob=false){
  if(typeof relative!=='string'||relative.length>300||relative.includes('\\')||relative.startsWith('/')||relative.split('/').some(p=>!p||p==='.'||p==='..')||/[\x00-\x1f]/.test(relative))throw Error('Runtime paths must stay inside the application');
  const wildcard=glob&&relative.endsWith('/*'),base=wildcard?relative.slice(0,-2):relative;if(base.includes('*'))throw Error('Invalid runtime wildcard');
  const resolved=fs.realpathSync(path.join(root,base));if(!resolved.startsWith(root+path.sep))throw Error('Runtime path escapes the application');
  return wildcard?path.join(resolved,'*'):resolved;
 }
 if(config.backend.main!=='com.meshlit.desktop.MainKt')throw Error('Unexpected portable backend');
 return {...config,python:local(config.python),pythonArgs:['-B','-s'],agentHost:local(config.agentHost),
  backend:[local(config.backend.java),'-Dmeshlit.dev.resources='+local(config.backend.resources),'-cp',config.backend.classpath.map(p=>local(p,true)).join(path.delimiter),'-Djava.library.path='+path.join(local(config.backend.resources),'local'),config.backend.main,'--ide-backend']};
}
module.exports={resolveRuntime};

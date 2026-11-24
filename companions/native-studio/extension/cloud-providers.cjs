// SPDX-License-Identifier: Apache-2.0
'use strict';
const {execFile}=require('node:child_process');
const {promisify}=require('node:util');
const providers={
    aws:{name:'Amazon Web Services',cli:'aws',url:'https://console.aws.amazon.com/',args:['sts','get-caller-identity','--output','json'],fields:['Account','Arn']},
    gcp:{name:'Google Cloud',cli:'gcloud',url:'https://console.cloud.google.com/',args:['projects','list','--limit=20','--format=json(projectId,name)'],fields:['projectId','name']},
    azure:{name:'Microsoft Azure',cli:'az',url:'https://portal.azure.com/',args:['account','show','--query','{name:name,id:id}','--output','json'],fields:['name','id']}
};
async function show(provider,vscode,details,run=promisify(execFile)){
    const item=providers[provider];
    const action=await vscode.window.showQuickPick(['View current account or projects','Open provider console'],{title:item.name});
    if(!action)return;
    if(action==='Open provider console')return vscode.env.openExternal(vscode.Uri.parse(item.url));
    let result;
    try{
        result=await run(item.cli,item.args,{timeout:15000,maxBuffer:256*1024,windowsHide:true,
            env:{...process.env,AWS_PAGER:'',AWS_CLI_AUTO_PROMPT:'off',CLOUDSDK_CORE_DISABLE_PROMPTS:'1',AZURE_CORE_COLLECT_TELEMETRY:'no'}});
    }catch(error){
        throw Error(error.code==='ENOENT'?'Install the '+item.cli+' CLI, add its executable to PATH and sign in, or choose Open provider console.':'Could not read '+item.name+' inventory. Check the CLI sign-in and network connection.');
    }
    const records=JSON.parse(result.stdout);
    const list=Array.isArray(records)?records:[records];
    return details(item.name,list.length?list.slice(0,20).map(row=>item.fields.map(key=>String(row[key]??''))):[['Projects','No visible projects']]);
}
module.exports={providers,show};

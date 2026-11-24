const key='meshlit.usage.savedUnlock';
const valid=password=>typeof password==='string'&&password.length>=12&&password.length<=256;

function create({secrets,send,report,preference}) {
  let pending, writing, generation=0;
  async function restore(shouldUnlock) {
    if(!secrets||pending||(preference&&!preference.enabled()))return;
    const attempt=generation;
    try {
      const password=await secrets.get(key);
      if(attempt!==generation||pending||!shouldUnlock()||!valid(password))return;
      request(password,false);
    } catch {
      report('Saved unlock is unavailable. Use Unlock chat to enter your passphrase.');
    }
  }
  function request(password,remember) {
    if(pending||!valid(password))return false;
    generation++;
    pending={password:remember?password:undefined};
    try {send({op:'unlock',password});}
    catch(error){pending=undefined;throw error;}
    return true;
  }
  async function accepted() {
    const password=pending?.password;
    pending=undefined;
    if(!password)return;
    const update=Promise.resolve().then(async()=>{
      await secrets.store(key,password);
      await preference?.set(true);
    });
    writing=update;
    try {await update;}
    catch {report('Usage is unlocked for this session. Studio could not save the passphrase.');}
    finally {if(writing===update)writing=undefined;}
  }
  function reset() {generation++;pending=undefined;}
  async function forget() {
    generation++;
    if(pending)pending.password=undefined;
    if(writing)await writing.catch(()=>{});
    await preference?.set(false);
    await secrets.delete(key);
  }
  return {restore,request,accepted,reset,forget};
}

module.exports={create,valid};

const test = require('node:test');
const assert = require('node:assert/strict');
const {setupIntent,actions} = require('./chat-setup.cjs');
const {render} = require('./chat-view.cjs');
test('setup commands are explicit, independent of model readiness and scoped to General Chat', () => {
  for (const [input, action] of Object.entries({
    'Can you pair my laptop?':'device', 'Pair a remote device':'device', 'Use LM Bionic':'bionic',
    'connect to LM Studio':'lmstudio', 'Connect Ollama':'ollama', 'load a local model':'local',
    'choose an online provider':'cloud', 'open the dashboard':'management', 'open coding IDE':'ide'
  })) assert.equal(setupIntent(input),action);
  for (const input of ['Explain how to connect Ollama', 'Use Ollama with secret xyz', 'Pair my laptop and run code', null])
    assert.equal(setupIntent(input),undefined);
  const view=render('General chat','chat','test',{general:true});
  for (const key of Object.keys(actions)) assert.ok(view.includes('id="setup-'+key+'"'));
  assert.ok(!render('Project','plan','test',{}).includes('id="setup-ollama"'));
  new (require('node:vm').Script)(view.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1]);
});

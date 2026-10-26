const {test} = require('node:test');
const assert = require('node:assert/strict');
const {launchArguments} = require('./launcher.cjs');
test('mac bundle handoff preserves paths as literal arguments', () => {
 assert.deepEqual(launchArguments('darwin','/Applications/Meshlit.app','/tmp/project; echo no'), ['/usr/bin/open',['-n','-a','/Applications/Meshlit.app','--args','--workspace','/tmp/project; echo no']]);
});
test('native launch is shell-free and relative/control paths are rejected', () => {
 assert.deepEqual(launchArguments('linux','/opt/meshlit','/tmp/project'), ['/opt/meshlit',['--workspace','/tmp/project']]);
 assert.throws(()=>launchArguments('linux','./untrusted','/tmp/project'));
 assert.throws(()=>launchArguments('linux','/bin/app','/tmp/project\nother'));
});

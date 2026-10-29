// Source labels describe the configured endpoint, never inferred compute capabilities.
const sources = [
 {id:'local-server',label:'Local model server',detail:'One model server on this computer; select its reported model ID.'},
 {id:'multi-local',label:'Multiple local models',detail:'Connect a running multi-model server. Meshlit does not start or pool servers automatically.'},
 {id:'remote-device',label:'Remote device',detail:'An owner-configured HTTPS endpoint or authenticated loopback tunnel.'},
 {id:'group-cluster',label:'Group / cluster endpoint',detail:'Connect an already-running cluster coordinator. A saved profile is not proof of sharding.'},
 {id:'cloud',label:'Cloud / third-party provider',detail:'Choose a vendor or your hosted OpenAI-compatible service.'}
];
const label = profile => sources.find(s=>s.id===profile.sourceKind)?.label || profile.service;
module.exports={sources,label};

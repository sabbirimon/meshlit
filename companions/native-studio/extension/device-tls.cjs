// SPDX-License-Identifier: Apache-2.0
'use strict';
const crypto = require('node:crypto');

// Encode a minimal self-signed X.509 certificate for the existing Studio P-256
// identity. TLS proves possession; peers pin the SPKI, independent of renewal.
function der(tag, ...parts) {
    const bytes = Buffer.concat(parts);
    const length = bytes.length < 128 ? [bytes.length] :
        bytes.length < 256 ? [0x81, bytes.length] : [0x82, bytes.length >> 8, bytes.length & 255];
    return Buffer.concat([Buffer.from([tag, ...length]), bytes]);
}
function credentials(identity) {
    const seq = (...parts) => der(0x30, ...parts);
    const algorithm = Buffer.from('300a06082a8648ce3d040302','hex');
    const name = seq(der(0x31,seq(Buffer.from('0603550403','hex'),der(0x0c,Buffer.from('Meshlit device')))));
    const time = ms => der(0x18,Buffer.from(new Date(ms).toISOString().replace(/[-:]/g,'').replace('T','').slice(0,14)+'Z'));
    const now = Date.now();
    const serial = crypto.randomBytes(16);
    serial[0] = (serial[0] & 0x7f) || 1;
    const body = seq(der(2,serial),algorithm,name,seq(time(now-86400000),time(now+365*86400000)),name,Buffer.from(identity.key,'hex'));
    const sig = crypto.sign('sha256',body,identity.privateKey);
    const cert = seq(body,algorithm,der(3,Buffer.from([0]),sig)).toString('base64').match(/.{1,64}/g).join('\n');
    return {key:identity.privateKey,cert:'-----BEGIN CERTIFICATE-----\n'+cert+'\n-----END CERTIFICATE-----',minVersion:'TLSv1.3'};
}
function deviceId(key) {
    return crypto.createHash('sha256').update(Buffer.from(key,'hex')).digest('hex').slice(0,20);
}
function socketKey(socket) {
    const cert = socket.getPeerX509Certificate();
    if (!cert || cert.publicKey.asymmetricKeyDetails?.namedCurve !== 'prime256v1')
        throw Error('The other device did not supply a Studio identity');
    return cert.publicKey.export({type:'spki',format:'der'}).toString('hex');
}
module.exports = {credentials,deviceId,socketKey};

// SPDX-License-Identifier: Apache-2.0
'use strict';
const dgram = require('node:dgram');
const os = require('node:os');
const net = require('node:net');
const group = '239.192.77.41', port = 43741;
function localAddresses() {
    return Object.values(os.networkInterfaces()).flat().filter(a => a && !a.internal && a.family==='IPv4');
}
async function discover(self, listenPort, onDevice) {
    const socket = dgram.createSocket({type:'udp4',reuseAddr:true});
    const memberships = new Set();
    const advert = Buffer.from(JSON.stringify({service:'meshlit-studio-v1',...self,port:listenPort}));
    function announce() {
        const adapters=localAddresses();
        const addresses=adapters.map(a=>a.address);
        for(const ip of memberships) if(!addresses.includes(ip)) {
            try {socket.dropMembership(group,ip);} catch {}
            memberships.delete(ip);
        }
        for(const adapter of adapters) {
            const ip=adapter.address;
            const mask=adapter.netmask.split('.').map(Number);
            const broadcast=ip.split('.').map((n,i)=>Number(n)|(255-mask[i])).join('.');
            if(broadcast!==ip)socket.send(advert,port,broadcast,()=>{});
            try {
                if(!memberships.has(ip)){socket.addMembership(group,ip);memberships.add(ip);}
                socket.setMulticastInterface(ip);
                socket.send(advert,port,group,()=>{});
            } catch { /* Interface may change between enumeration and send. */ }
        }
    }
    socket.on('message',(bytes,source)=>{
        if(bytes.length>600 || !net.isIPv4(source.address))return;
        try {
            const ad=JSON.parse(bytes);
            if(ad.service!=='meshlit-studio-v1'||ad.id===self.id||!/^[a-f0-9]{20}$/.test(ad.id)||
                typeof ad.name!=='string'||ad.name.length>128||/[\x00-\x1f]/.test(ad.name)||
                !Number.isInteger(ad.port)||ad.port<1||ad.port>65535)return;
            onDevice({id:ad.id,name:ad.name,host:source.address,port:ad.port,seen:Date.now()});
        } catch {}
    });
    const {once}=require('node:events');
    try {
        socket.bind(port);await once(socket,'listening');socket.setMulticastTTL(1);socket.setBroadcast(true);
    } catch(err){try{socket.close();}catch{} throw err;}
    socket.on('error',()=>{});announce();
    const timer=setInterval(announce,2500);
    return ()=>{clearInterval(timer);socket.close();};
}
module.exports={discover};

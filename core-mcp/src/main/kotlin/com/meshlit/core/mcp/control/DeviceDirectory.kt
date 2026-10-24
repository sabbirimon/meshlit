package com.meshlit.core.mcp.control

import java.security.MessageDigest
import kotlinx.serialization.Serializable

@Serializable enum class DeviceKind { ANDROID, COMPUTER, SERVER, NAS, ROUTER, SWITCH, FIREWALL, BROWSER, RASPBERRY_PI, MICROCONTROLLER, SENSOR, RADIO, OTHER }
@Serializable enum class DeviceAccess { OBSERVE, SETTINGS, MODELS, CLUSTER, RECOVERY, TASKS, WORKSPACE, CLOUD, BROWSER }
@Serializable enum class EnrollmentState { PENDING, APPROVED, REVOKED }
@Serializable data class EnrolledDevice(val id:String,val name:String,val kind:DeviceKind,val credentialHash:String,
    val state:EnrollmentState=EnrollmentState.PENDING,val access:Set<DeviceAccess> = emptySet(),
    val requestedRoles:Set<String> = emptySet(),val createdAtMs:Long=System.currentTimeMillis(),val lastSeenAtMs:Long=createdAtMs,
    val expiresAtMs:Long=createdAtMs+7L*24*60*60*1000)
@Serializable data class DeviceGroup(val id:String,val name:String,val memberIds:Set<String>)
@Serializable data class DeviceDirectorySnapshot(val devices:List<EnrolledDevice> = emptyList(),val groups:List<DeviceGroup> = emptyList())
interface DeviceDirectoryStore { fun load():DeviceDirectorySnapshot;fun save(snapshot:DeviceDirectorySnapshot) }
/** Owner approval is separate from claimed capabilities. Groups are local metadata. */
class DeviceDirectory(private val store:DeviceDirectoryStore,
    private val clock:()->Long = { System.currentTimeMillis() },private val deviceLimit:()->Int = { 10000 }) {
    private var snapshot=store.load()
    @Synchronized fun read()=snapshot
    private fun persist(next:DeviceDirectorySnapshot){store.save(next);snapshot=next}
    @Synchronized fun request(id:String,name:String,kind:DeviceKind,token:String,roles:Set<String>):EnrolledDevice {
        require(id.matches(Regex("[a-zA-Z0-9_-]{1,36}")) && name.isNotBlank() && name.length<=80)
        require(token.matches(Regex("[a-fA-F0-9]{64}")))
        require(roles.size<=10 && roles.all{it in setOf("control","compute","storage","tools","routing","monitoring","sensors","actuation","media","preprocessing")})
        val hash=hash(token)
        snapshot.devices.firstOrNull{it.id==id}?.let{existing ->
            require(MessageDigest.isEqual(existing.credentialHash.toByteArray(),hash.toByteArray())){"Identity already enrolled with another credential"}
            require(existing.state!=EnrollmentState.REVOKED){"Device revoked; use a new owner invitation and identity"}
            if(existing.expiresAtMs<=clock()) {
                val renewed=existing.copy(state=EnrollmentState.PENDING,access=emptySet(),requestedRoles=roles,
                    expiresAtMs=clock()+7L*24*60*60*1000)
                persist(snapshot.copy(devices=snapshot.devices.map{if(it.id==id) renewed else it}))
                return renewed
            }
            return existing
        }
        require(snapshot.devices.none{it.credentialHash==hash}){"Credential already belongs to another identity"}
        require(deviceLimit() in 1..10000 && snapshot.devices.size<deviceLimit()){"Device directory is full"}
        val now=clock()
        val entry=EnrolledDevice(id,name,kind,hash,requestedRoles=roles,createdAtMs=now,lastSeenAtMs=now,
            expiresAtMs=now+7L*24*60*60*1000)
        persist(snapshot.copy(devices=snapshot.devices+entry));return entry
    }
    @Synchronized fun approve(id:String,access:Set<DeviceAccess>):EnrolledDevice {
        require(access.isNotEmpty())
        val entry=snapshot.devices.single{it.id==id};require(entry.state!=EnrollmentState.REVOKED && entry.expiresAtMs>clock())
        val approved=entry.copy(state=EnrollmentState.APPROVED,access=access)
        persist(snapshot.copy(devices=snapshot.devices.map{if(it.id==id) approved else it}));return approved
    }
    @Synchronized fun revoke(id:String){
        require(snapshot.devices.any{it.id==id})
        persist(snapshot.copy(devices=snapshot.devices.map{if(it.id==id) it.copy(state=EnrollmentState.REVOKED,access=emptySet()) else it},
            groups=snapshot.groups.map{it.copy(memberIds=it.memberIds-id)}))
    }
    @Synchronized fun authenticate(token:String):EnrolledDevice? {
        if(!token.matches(Regex("[a-fA-F0-9]{64}"))) return null
        val hash=hash(token)
        return snapshot.devices.firstOrNull{it.state==EnrollmentState.APPROVED && it.expiresAtMs>clock() &&
            MessageDigest.isEqual(it.credentialHash.toByteArray(),hash.toByteArray())}
    }
    @Synchronized fun heartbeat(id:String,now:Long=System.currentTimeMillis()){
        val entry=snapshot.devices.firstOrNull{it.id==id && it.state==EnrollmentState.APPROVED} ?: return
        if(now-entry.lastSeenAtMs>=10_000) persist(snapshot.copy(devices=snapshot.devices.map{if(it.id==id) it.copy(lastSeenAtMs=now) else it}))
    }
    @Synchronized fun group(id:String,name:String,members:Set<String>):DeviceGroup {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,36}")) && name.isNotBlank() && name.length<=80)
        require(members.isNotEmpty() && members.all{member ->snapshot.devices.any{it.id==member && it.state==EnrollmentState.APPROVED}})
        require(snapshot.groups.any{it.id==id} || snapshot.groups.size<16)
        val entry=DeviceGroup(id,name,members)
        persist(snapshot.copy(groups=snapshot.groups.filterNot{it.id==id}+entry));return entry
    }
    @Synchronized fun removeGroup(id:String){persist(snapshot.copy(groups=snapshot.groups.filterNot{it.id==id}))}
    companion object { fun hash(token:String)=MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString(""){"%02x".format(it)} }
}

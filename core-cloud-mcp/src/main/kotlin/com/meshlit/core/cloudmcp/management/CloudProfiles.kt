package com.meshlit.core.cloudmcp.management

import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

@Serializable enum class CloudVendor { AWS, AZURE, DIGITALOCEAN, GCP, OPENROUTER, CUSTOM }
@Serializable enum class EnvironmentPurpose { CLOUD, API, SSH, WEB_LOGIN }
@Serializable enum class CloudActor { HUMAN, AGENT }
@Serializable data class CloudFunction(val id:String,val path:String) {
    fun validate(){
        require(id.matches(Regex("[a-z][a-z0-9_-]{0,39}"))) { "Invalid function ID" }
        require(path.length in 1..400 && path.startsWith('/') && !path.startsWith("//") && path.none{it.isWhitespace() || it in "?#%\\"} && path.split('/').none{it==".." || it=="."}) { "Use an absolute API path without query, encoding or traversal" }
    }
}
@Serializable data class CloudProfile(val id:String,val name:String,val vendor:CloudVendor,val environmentId:String,
    val region:String="us-east-1",val project:String="",val customOrigin:String="",val functions:List<CloudFunction> = emptyList(),
    val maxAgentCallsPerDay:Int=20,val minimumRefreshSeconds:Int=30,val meteredReadsAllowed:Boolean=false,val humanEnabled:Boolean=false,val agentEnabled:Boolean=false,val humanActions:Set<String> = emptySet(),val agentActions:Set<String> = emptySet()) {
    fun actions():List<String> = when(vendor){
        CloudVendor.AWS->listOf("identity","instances","costs")
        CloudVendor.AZURE->listOf("subscriptions","resources","costs")
        CloudVendor.DIGITALOCEAN->listOf("account","droplets","gpu-droplets","balance")
        CloudVendor.GCP->listOf("projects","instances","billing-info")
        CloudVendor.OPENROUTER->listOf("key-info","models")
        CloudVendor.CUSTOM->functions.map{it.id}
    }
    fun validate(){
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && environmentId.matches(Regex("[A-Za-z0-9_-]{1,80}")))
        require(name.isNotBlank() && name.length<=100)
        require(maxAgentCallsPerDay in 0..1000 && minimumRefreshSeconds in 5..3600)
        require(region.matches(Regex("[a-z]{2}(?:-[a-z]+)+-[0-9]"))) { "Invalid AWS region" }
        require(project.isEmpty() || project.matches(Regex("[a-z][a-z0-9-]{4,61}[a-z0-9]"))) { "Invalid GCP project ID" }
        require(functions.size<=16 && functions.map{it.id}.distinct().size==functions.size);functions.forEach{it.validate()}
        if(vendor==CloudVendor.CUSTOM){
            val url=customOrigin.toHttpUrl()
            require(url.isHttps && url.port==443 && url.username.isEmpty() && url.password.isEmpty() && url.encodedPath=="/" && url.query==null && url.fragment==null) { "Custom cloud requires an HTTPS origin on port 443" }
            require(functions.isNotEmpty()) { "Configure at least one read-only function" }
        }
        require(humanActions.all{it in actions()} && agentActions.all{it in actions()}) { "Unknown cloud action" }
    }
    fun authorize(actor:CloudActor,action:String,environment:EnvironmentProfile,now:Long){
        validate();environment.validate()
        require(environment.purpose in setOf(EnvironmentPurpose.CLOUD,EnvironmentPurpose.API)) { "Choose a cloud/API environment" }
        require(environment.id==environmentId) { "Credential environment mismatch" }
        require(environment.expiresAtMs==null || environment.expiresAtMs>now) { "Credential environment expired; rotate credentials" }
        require(vendor!=CloudVendor.AWS || action!="costs" || meteredReadsAllowed) { "AWS Cost Explorer can charge per query; enable metered reads first" }
        when(actor){
            CloudActor.HUMAN->require(humanEnabled && action in humanActions) { "Enable this action in human settings" }
            CloudActor.AGENT->require(agentEnabled && environment.agentAllowed && action in agentActions) { "Agent cloud action or environment is not delegated" }
        }
    }
    fun credentialNames()=when(vendor){
        CloudVendor.AWS->listOf("AWS_ACCESS_KEY_ID","AWS_SECRET_ACCESS_KEY","AWS_SESSION_TOKEN")
        CloudVendor.AZURE->listOf("AZURE_ACCESS_TOKEN","AZURE_SUBSCRIPTION_ID")
        CloudVendor.DIGITALOCEAN->listOf("DIGITALOCEAN_TOKEN")
        CloudVendor.GCP->listOf("GOOGLE_ACCESS_TOKEN")
        CloudVendor.OPENROUTER->listOf("OPENROUTER_API_KEY")
        CloudVendor.CUSTOM->listOf("CLOUD_API_TOKEN")
    }
}
/** Entire environment stays encrypted; public descriptions contain variable names only. */
@Serializable data class EnvironmentProfile(val id:String,val name:String,val variables:Map<String,String> = emptyMap(),
    val agentAllowed:Boolean=false,val expiresAtMs:Long?=null,val purpose:EnvironmentPurpose=EnvironmentPurpose.CLOUD,val serviceBinding:String="") {
    fun validate(){
        require(id.matches(Regex("[A-Za-z0-9_-]{1,80}")) && name.isNotBlank() && name.length<=100)
        require(variables.size<=64 && variables.entries.all{(key,value)->key.matches(Regex("[A-Z_][A-Z0-9_]{0,79}")) && value.length in 1..(if(key=="SSH_PRIVATE_KEY") 65536 else 8192) && value.none{it=='\u0000' || (key!="SSH_PRIVATE_KEY" && (it=='\r' || it=='\n'))}})
        require(variables.values.sumOf{it.length}<=131072)
        require(serviceBinding.length<=253)
        if(purpose==EnvironmentPurpose.WEB_LOGIN || purpose==EnvironmentPurpose.API && serviceBinding.isNotBlank()){
            val url=serviceBinding.toHttpUrl();require(url.isHttps && url.port==443 && url.username.isEmpty() && url.password.isEmpty() && url.encodedPath=="/" && url.query==null && url.fragment==null) { "Service credentials require an exact HTTPS origin" }
        }
        if(purpose==EnvironmentPurpose.SSH) require(serviceBinding.isNotBlank() && serviceBinding.none{it.isWhitespace() || it in "/@?#"}) { "Bind SSH credentials to a host" }
        require(expiresAtMs==null || expiresAtMs>0)
    }
    fun publicView()=EnvironmentDescription(id,name,variables.keys.sorted(),agentAllowed,expiresAtMs,purpose,serviceBinding)
}
@Serializable data class EnvironmentDescription(val id:String,val name:String,val variableNames:List<String>,val agentAllowed:Boolean,val expiresAtMs:Long?,val purpose:EnvironmentPurpose,val serviceBinding:String)

@Serializable data class CloudReadAllowance(val utcDay:Long,val agentCalls:Int=0,val lastActionAtMs:Map<String,Long> = emptyMap()) {
    fun admit(profile:CloudProfile,actor:CloudActor,action:String,now:Long):CloudReadAllowance {
        require(now>=0)
        val day=now/86_400_000;val previous=if(utcDay==day) this else CloudReadAllowance(day)
        previous.lastActionAtMs[action]?.let{last->require(now>=last && now-last>=profile.minimumRefreshSeconds*1000L){ "Cloud refresh cooldown is active" }}
        require(actor!=CloudActor.AGENT || previous.agentCalls<profile.maxAgentCallsPerDay) { "Agent daily cloud request limit reached" }
        return previous.copy(agentCalls=previous.agentCalls+if(actor==CloudActor.AGENT) 1 else 0,lastActionAtMs=previous.lastActionAtMs+(action to now))
    }
}

package com.meshlit.core.cloudmcp.management

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.UnknownHostException
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler

@Serializable data class CloudObservation(val profileId:String,val action:String,val fetchedAtMs:Long,val source:String,
    val data:JsonElement,val partial:Boolean=false,val note:String="Explicit refresh; provider data may lag and is not a complete bill")

/** Explicit read-only APIs: no arbitrary methods, automatic retries, redirect forwarding or background billing queries. */
class CloudApiClient(private val client:OkHttpClient=defaultClient()) {
    companion object {
        internal fun publicAddress(address:InetAddress):Boolean {
            val b=address.address
            return !address.isAnyLocalAddress && !address.isLoopbackAddress && !address.isSiteLocalAddress && !address.isLinkLocalAddress && !address.isMulticastAddress &&
                if(b.size==4){val a=b[0].toInt() and 255;val c=b[1].toInt() and 255;a in 1..223 && !(a==100 && c in 64..127) && !(a==192 && c==0) && !(a==198 && c in 18..19)} else (b[0].toInt() and 254)!=252
        }
        fun defaultClient()=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(25,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).dns {hostname ->
                val addresses=Dns.SYSTEM.lookup(hostname)
                if(addresses.isEmpty() || addresses.any{!publicAddress(it)}) throw UnknownHostException("Cloud endpoint must resolve exclusively to public addresses")
                addresses
            }.build()
    }
    internal fun request(profile:CloudProfile,environment:EnvironmentProfile,action:String,page:Int,now:Long):Request {
        profile.validate();environment.validate();require(action in profile.actions() && page in 1..100)
        val vars=environment.variables
        fun bound(request:Request):Request {
            if(environment.purpose==EnvironmentPurpose.API && environment.serviceBinding.isNotBlank()) require(environment.serviceBinding.trimEnd('/')=="https://${request.url.host}" && request.url.port==443) { "API environment is bound to another service origin" }
            return request
        }
        if(profile.vendor==CloudVendor.AWS){
            require(page==1){"AWS pagination requires a future opaque cursor adapter"}
            if(action=="costs"){
                require(profile.meteredReadsAllowed && profile.region=="us-east-1"){"AWS costs require metered-read consent and us-east-1 endpoint profile"}
                val calendar=Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply{timeInMillis=now}
                val format=SimpleDateFormat("yyyy-MM-dd",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")}
                val end=format.format(calendar.time);calendar.set(Calendar.DAY_OF_MONTH,1);val start=format.format(calendar.time)
                require(start!=end){"Month-to-date costs are unavailable on the first UTC day; refresh tomorrow"}
                val body=buildJsonObject{put("TimePeriod",buildJsonObject{put("Start",start);put("End",end)});put("Granularity","MONTHLY");put("Metrics",buildJsonArray{add("UnblendedCost")})}.toString()
                return bound(AwsQuerySigner.request("ce","us-east-1",body,vars,now))
            }
            val service=if(action=="identity") "sts" else "ec2"
            val body=if(action=="identity") "Action=GetCallerIdentity&Version=2011-06-15" else "Action=DescribeInstances&MaxResults=100&Version=2016-11-15"
            return bound(AwsQuerySigner.request(service,profile.region,body,vars,now))
        }
        val tokenName=profile.credentialNames().first()
        val publicCatalog=profile.vendor==CloudVendor.OPENROUTER && action=="models"
        val token=vars[tokenName] ?: if(publicCatalog) "" else error("Save $tokenName in the chosen environment")
        require(publicCatalog && token.isEmpty() || token.length in 8..8192 && token.none{it.isWhitespace()}) { "Invalid token format" }
        fun subscription():String=(vars["AZURE_SUBSCRIPTION_ID"] ?: error("Save AZURE_SUBSCRIPTION_ID")).also{require(it.matches(Regex("[a-fA-F0-9-]{36}")))}
        fun project():String=profile.project.also{require(it.isNotEmpty()){ "Configure a GCP project ID" }}
        val url=when(profile.vendor){
            CloudVendor.AZURE->{require(page==1){"Azure pagination requires a future opaque cursor adapter"};when(action){
                "subscriptions"->"https://management.azure.com/subscriptions?api-version=2022-12-01"
                "resources"->"https://management.azure.com/subscriptions/${subscription()}/resources?api-version=2021-04-01"
                else->"https://management.azure.com/subscriptions/${subscription()}/providers/Microsoft.CostManagement/query?api-version=2025-03-01"
            }}
            CloudVendor.DIGITALOCEAN->"https://api.digitalocean.com/v2/"+when(action){"account"->"account";"balance"->"customers/my/balance";"gpu-droplets"->"droplets?page=$page&per_page=100&type=gpus";else->"droplets?page=$page&per_page=100"}
            CloudVendor.GCP->{require(page==1){"GCP pagination requires a future opaque cursor adapter"};when(action){
                "projects"->"https://cloudresourcemanager.googleapis.com/v1/projects?pageSize=100"
                "instances"->"https://compute.googleapis.com/compute/v1/projects/${project()}/aggregated/instances?maxResults=100&returnPartialSuccess=true"
                else->"https://cloudbilling.googleapis.com/v1/projects/${project()}/billingInfo"
            }}
            CloudVendor.OPENROUTER->{require(page==1);"https://openrouter.ai/api/v1/"+if(action=="models") "models" else "key"}
            CloudVendor.CUSTOM->{require(page==1);profile.customOrigin.trimEnd('/')+profile.functions.single{it.id==action}.path}
            CloudVendor.AWS->error("AWS handled above")
        }
        val builder=Request.Builder().url(url).apply{if(token.isNotEmpty()) header("Authorization","Bearer $token")}
        if(profile.vendor==CloudVendor.AZURE && action=="costs"){
            val payload="""{"type":"ActualCost","timeframe":"MonthToDate","dataset":{"granularity":"None","aggregation":{"totalCost":{"name":"PreTaxCost","function":"Sum"}}}}"""
            builder.post(payload.toRequestBody("application/json".toMediaType()))
        }
        return bound(builder.build())
    }
    suspend fun execute(profile:CloudProfile,environment:EnvironmentProfile,actor:CloudActor,action:String,page:Int=1):CloudObservation=withContext(Dispatchers.IO){
        val now=System.currentTimeMillis();profile.authorize(actor,action,environment,now)
        val request=request(profile,environment,action,page,now)
        coroutineScope {
            val call=client.newCall(request);val watcher=launch(Dispatchers.IO){try{awaitCancellation()}finally{call.cancel()}}
            try{call.execute().use{response ->
                check(response.isSuccessful){"Cloud HTTP ${response.code}; verify token expiry, service permissions, region and quota"}
                val body=response.body ?: error("Empty cloud response")
                require(body.contentLength()<=4*1024*1024){"Cloud response exceeds 4 MiB"}
                val source=body.source();require(!source.request(4L*1024*1024+1)){"Cloud response exceeds 4 MiB"}
                val raw=source.readUtf8()
                val data=if(profile.vendor==CloudVendor.AWS && action!="costs") awsXml(raw) else Json.parseToJsonElement(raw)
                val (safe,truncated)=bounded(redact(data,environment.variables.values))
                val partial=truncated || hasMore(data) || profile.vendor==CloudVendor.GCP && action=="instances" && (data as? JsonObject)?.get("warning")!=null
                CloudObservation(profile.id,action,System.currentTimeMillis(),request.url.newBuilder().query(null).build().toString(),safe,partial,
                    if(profile.vendor==CloudVendor.GCP && action=="billing-info") "Billing association only; spend requires configured BigQuery billing export" else "Page $page only; refresh explicitly. Provider billing can lag; cost queries exclude tax unless provider says otherwise")
            }}finally{watcher.cancel()}
        }
    }
    /** Bound returned/persisted payload, not just HTTP input; retain only actual values. */
    internal fun bounded(data:JsonElement):Pair<JsonElement,Boolean> {
        var remaining=128*1024;var truncated=false
        fun node(value:JsonElement,depth:Int):JsonElement {
            if(remaining<64 || depth>16){truncated=true;return JsonNull}
            remaining-=2
            return when(value){
                is JsonObject->{
                    val entries=linkedMapOf<String,JsonElement>()
                    if(value.size>100) truncated=true
                    for((key,child) in value.entries.take(100)){
                        if(key.length>128 || remaining<64){truncated=true;continue}
                        remaining-=JsonPrimitive(key).toString().toByteArray().size+2
                        entries[key]=node(child,depth+1)
                    };JsonObject(entries)
                }
                is JsonArray->{
                    if(value.size>100) truncated=true
                    val items=mutableListOf<JsonElement>()
                    for(child in value.take(100)){if(remaining<64){truncated=true;break};remaining--;items+=node(child,depth+1)}
                    JsonArray(items)
                }
                is JsonPrimitive->{
                    val safe=if(value.isString && value.content.length>2048){truncated=true;JsonPrimitive(value.content.take(2048)+" [truncated]")}else value
                    val bytes=safe.toString().toByteArray().size
                    if(bytes>remaining){truncated=true;JsonNull}else{remaining-=bytes;safe}
                }
            }
        }
        return node(data,0) to truncated
    }
    internal fun hasMore(data:JsonElement):Boolean=when(data){
        is JsonObject->data.entries.any{(key,value)->key in setOf("nextLink","nextPageToken","NextPageToken","nextToken","next") && (value is JsonPrimitive && value.isString && value.content.isNotBlank() || value is JsonArray && value.isNotEmpty()) || hasMore(value)}
        is JsonArray->data.any{hasMore(it)}
        else->false
    }
    internal fun redact(data:JsonElement,secrets:Collection<String>):JsonElement=when(data){
        is JsonObject->JsonObject(data.mapValues{(key,value)->if(Regex("(?i)(password|secret|access.?token|refresh.?token|api.?key|private.?key|authorization|credential)").containsMatchIn(key)) JsonPrimitive("[redacted]") else redact(value,secrets)})
        is JsonArray->JsonArray(data.map{redact(it,secrets)})
        is JsonPrimitive->if(data.isString) JsonPrimitive(secrets.filter{it.length>=4}.sortedByDescending{it.length}.fold(data.content){text,secret->text.replace(secret,"[redacted]")}) else data
        else->data
    }
    internal fun awsXml(raw:String):JsonObject {
        require(!raw.contains("<!DOCTYPE",true) && !raw.contains("<!ENTITY",true)){"XML entities are forbidden"}
        val fields=setOf("Account","Arn","UserId","instanceId","instanceType","instanceState","name","nextToken")
        val values=mutableMapOf<String,MutableList<String>>();var tag="";var text=StringBuilder()
        val factory=SAXParserFactory.newInstance().apply{isNamespaceAware=false}
        factory.newSAXParser().parse(InputSource(StringReader(raw)),object:DefaultHandler(){
            override fun startElement(uri:String?,localName:String?,qName:String?,attributes:org.xml.sax.Attributes?){tag=qName.orEmpty();text=StringBuilder()}
            override fun characters(ch:CharArray,start:Int,length:Int){if(tag in fields && text.length<4096) text.append(ch,start,length)}
            override fun endElement(uri:String?,localName:String?,qName:String?){if(qName==tag && tag in fields && text.isNotEmpty()) values.getOrPut(tag){mutableListOf()}.add(text.toString().take(4096));tag=""}
        })
        require(values.isNotEmpty() || raw.contains("DescribeInstancesResponse")){"Unexpected AWS response"}
        return buildJsonObject{values.forEach{(key,list)->put(key,buildJsonArray{list.take(1000).forEach{add(it)}})}}
    }
}

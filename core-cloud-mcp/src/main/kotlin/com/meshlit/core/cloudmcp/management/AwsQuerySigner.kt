package com.meshlit.core.cloudmcp.management

import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Restricted SigV4 query signing: fixed root path, no query or caller-supplied headers. */
internal object AwsQuerySigner {
    private fun hash(text:String)=hex(MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)))
    private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun hmac(key:ByteArray,text:String)=Mac.getInstance("HmacSHA256").run{init(SecretKeySpec(key,"HmacSHA256"));doFinal(text.toByteArray(Charsets.UTF_8))}
    fun request(service:String,region:String,body:String,variables:Map<String,String>,now:Long):Request {
        require(service in setOf("sts","ec2","ce"));require(region.matches(Regex("[a-z]{2}(?:-[a-z]+)+-[0-9]")))
        val key=variables["AWS_ACCESS_KEY_ID"] ?: error("Save AWS_ACCESS_KEY_ID")
        val secret=variables["AWS_SECRET_ACCESS_KEY"] ?: error("Save AWS_SECRET_ACCESS_KEY")
        require(key.matches(Regex("[A-Z0-9]{16,128}")) && secret.length in 16..128 && secret.none{it.isWhitespace()}) { "Invalid AWS credential format" }
        val session=variables["AWS_SESSION_TOKEN"]?.also{require(it.none{c->c.isWhitespace()})}
        require(!key.startsWith("ASIA") || !session.isNullOrBlank()) { "Temporary AWS credentials require AWS_SESSION_TOKEN" }
        val date=SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'",Locale.US).apply{timeZone=TimeZone.getTimeZone("UTC")}.format(Date(now))
        val day=date.take(8);val suffix=if(region.startsWith("cn-")) "amazonaws.com.cn" else "amazonaws.com"
        val host="$service.$region.$suffix"
        val type=if(service=="ce") "application/x-amz-json-1.1" else "application/x-www-form-urlencoded"
        val headers=sortedMapOf("content-type" to type,"host" to host,"x-amz-date" to date)
        if(service=="ce") headers["x-amz-target"]="AWSInsightsIndexService.GetCostAndUsage"
        session?.let{headers["x-amz-security-token"]=it}
        val names=headers.keys.joinToString(";")
        val canonical="POST\n/\n\n"+headers.entries.joinToString(""){"${it.key}:${it.value}\n"}+"\n$names\n${hash(body)}"
        val scope="$day/$region/$service/aws4_request"
        val signing=hmac(hmac(hmac(hmac(("AWS4$secret").toByteArray(),day),region),service),"aws4_request")
        val signature=hex(hmac(signing,"AWS4-HMAC-SHA256\n$date\n$scope\n${hash(canonical)}"))
        return Request.Builder().url("https://$host/").apply{headers.forEach{(name,value)->header(name,value)}}
            .header("Authorization","AWS4-HMAC-SHA256 Credential=$key/$scope, SignedHeaders=$names, Signature=$signature")
            .post(body.toByteArray(Charsets.UTF_8).toRequestBody(type.toMediaType())).build()
    }
}

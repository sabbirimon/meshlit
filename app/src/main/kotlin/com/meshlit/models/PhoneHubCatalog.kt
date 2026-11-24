package com.meshlit.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.serialization.json.*
import java.util.concurrent.TimeUnit

internal data class ModelPage(val ids:List<String>,val next:String?)
internal object PhoneHubCatalog {
    private val http=OkHttpClient.Builder().callTimeout(18,TimeUnit.SECONDS).followRedirects(false).build()
    private val images=LruCache<String,Bitmap>(48)
    suspend fun page(query:String,next:String?,token:String,author:String?=null,sort:String?=null,ggufOnly:Boolean=false,capacity:Long?=null):ModelPage=withContext(Dispatchers.IO) {
        val url=next?.toHttpUrl() ?: "https://huggingface.co/api/models".toHttpUrl().newBuilder()
            .addQueryParameter("search",query).addQueryParameter("limit","50").apply{
                if(ggufOnly)addQueryParameter("filter","gguf")
                if(capacity!=null)addQueryParameter("expand","gguf")
                if(author!=null)addQueryParameter("author",author)
                if(sort!=null)addQueryParameter("sort",sort)
            }.build()
        require(url.isHttps && url.host=="huggingface.co" && url.port==443 && url.encodedPath=="/api/models")
        val req=Request.Builder().url(url).apply{if(token.isNotBlank())header("Authorization","Bearer $token")}.build()
        http.newCall(req).execute().use { response->
            require(response.isSuccessful){"Hugging Face returned HTTP ${response.code}"}
            val body=response.body.source().readByteArrayBounded(1500000)
            val ids=Json.parseToJsonElement(body.decodeToString()).jsonArray.mapNotNull{row->
                val weights=(row.jsonObject["gguf"] as? JsonObject)?.get("totalFileSize")?.jsonPrimitive?.longOrNull
                if(capacity!=null && (weights==null || weights !in 1..capacity)) null else row.jsonObject["id"]?.jsonPrimitive?.contentOrNull}
            val link=response.header("Link").orEmpty().split(',').firstOrNull{it.contains("rel=\"next\"")}?.substringAfter('<')?.substringBefore('>')
            ModelPage(ids,link)
        }
    }
    private fun okio.BufferedSource.readByteArrayBounded(limit:Long):ByteArray{
        require(!request(limit+1)){"Catalog response too large"};return readByteArray()
    }
    suspend fun logo(publisher:String):Bitmap?=withContext(Dispatchers.IO){
        if(!publisher.matches(Regex("[A-Za-z0-9_.-]{1,96}")))return@withContext null
        images.get(publisher)?.let{return@withContext it}
        for(kind in listOf("organizations","users")){
            val result=http.newCall(Request.Builder().url("https://huggingface.co/api/$kind/$publisher/avatar").build()).execute().use{
                if(!it.isSuccessful)null else Json.parseToJsonElement(it.body.source().readByteArrayBounded(16000).decodeToString()).jsonObject["avatarUrl"]?.jsonPrimitive?.contentOrNull}
            if(result==null)continue
            val link=result.toHttpUrl()
            if(!link.isHttps || link.host!="cdn-avatars.huggingface.co" || link.port!=443)continue
            val bitmap=http.newCall(Request.Builder().url(link).build()).execute().use{
                if(!it.isSuccessful)return@use null
                val bytes=it.body.source().readByteArrayBounded(1024*1024)
                val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                if(bounds.outWidth<=0 || bounds.outHeight<=0)return@use null
                val opts=BitmapFactory.Options().apply { var size=maxOf(bounds.outWidth,bounds.outHeight);while(size>128){inSampleSize=if(inSampleSize<2)2 else inSampleSize*2;size/=2} }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,opts)
            }
            if(bitmap!=null){images.put(publisher,bitmap);return@withContext bitmap}
        }
        null
    }
}

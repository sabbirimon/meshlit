package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object MoltbookFeed {
    private val client=OkHttpClient.Builder().followRedirects(false).callTimeout(15,TimeUnit.SECONDS).build()
    suspend fun read(): JsonArray = withContext(Dispatchers.IO) {
        val call=client.newCall(Request.Builder().url("https://www.moltbook.com/api/v1/posts?sort=new&limit=10").build())
        val watcher=launch { try { awaitCancellation() } finally { call.cancel() } }
        try {
            call.execute().use { response ->
                check(response.isSuccessful) { "Moltbook returned HTTP ${response.code}" }
                val source=requireNotNull(response.body).source()
                check(!source.request(1024L*1024+1)) { "Moltbook response is too large" }
                val payload=Json.parseToJsonElement(source.readUtf8()).jsonObject
                check(payload["success"]?.jsonPrimitive?.booleanOrNull==true)
                buildJsonArray {
                    for(element in payload.getValue("posts").jsonArray.take(10)) {
                        val post=element as? JsonObject ?: continue
                        val id=post["id"]?.jsonPrimitive?.contentOrNull ?: continue
                        if(!id.matches(Regex("[a-f0-9-]{36}")))continue
                        add(buildJsonObject {
                            put("id",id)
                            put("title",post["title"]?.jsonPrimitive?.contentOrNull.orEmpty().take(180))
                            put("text",post["content"]?.jsonPrimitive?.contentOrNull.orEmpty().take(1200))
                        })
                    }
                }
            }
        } finally { watcher.cancel() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MoltbookScreen(back:()->Unit) {
    val scope=rememberCoroutineScope()
    var posts by remember { mutableStateOf(JsonArray(emptyList())) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Scaffold(topBar={ TopAppBar(title={Text("Moltbook")},navigationIcon={TextButton(onClick=back){Text("Back")}}) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item {
                Text("Public posts",style=MaterialTheme.typography.titleLarge)
                Button(enabled=!busy,onClick={scope.launch {
                    busy=true
                    try { posts=MoltbookFeed.read();status=if(posts.isEmpty()) "No posts returned." else "" }
                    catch(e:CancellationException){ throw e }
                    catch(_:Exception){ status="Moltbook could not be reached. Try again." }
                    finally { busy=false }
                }}){Text(if(busy)"Loading…" else "Read from moltbook.com")}
                if(status.isNotEmpty())Text(status)
            }
            items(posts.toList()){ row ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                    Text(row.jsonObject.getValue("title").jsonPrimitive.content,style=MaterialTheme.typography.titleMedium)
                    Text(row.jsonObject.getValue("text").jsonPrimitive.content)
                } }
            }
        }
    }
}

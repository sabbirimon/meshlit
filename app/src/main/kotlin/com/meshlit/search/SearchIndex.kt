package com.meshlit.search

import com.meshlit.chat.ChatConversation
import com.meshlit.core.mcp.control.*
import com.meshlit.models.LibraryModel
import com.meshlit.ui.modern.WorkspaceDestinations
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class SearchArticle(val id:String,val title:String,val text:String,val importedAtMs:Long)
internal fun encodeSearchArticles(records:List<SearchArticle>):ByteArray=Json.encodeToString(records).toByteArray().also{
    require(it.size<=4*1024*1024){"Article index exceeds 4 MiB; remove an article before importing more"}
}
enum class SearchCategory(val label:String) { APP("App"), CHATS("Chats"), ARTICLES("Articles"), DEVICES("Devices"), WEB("Web") }
data class AppSearchResult(val id:String,val title:String,val detail:String,val category:SearchCategory,
    val destination:String?=null,val conversationId:String?=null,val messageId:String?=null,val articleId:String?=null)
data class SearchOption(val title:String,val detail:String,val destination:String,val keywords:String="")

/** Current connected screens only. Secret stores, arbitrary disk paths and remote settings are never scanned. */
object AppSearchIndex {
    val options=listOf(
        SearchOption("Output token ceiling","Manual / Automatic cluster budget, history and token speed","chat-options","tokens generation limit speed TPS context"),
        SearchOption("Chat internet access","Per-chat web search and approved page tools","chat-options","web online search internet permissions"),
        SearchOption("Search access","Local content, Internet search and separate agent grants","search","articles Brave token API key"),
        SearchOption("Chat instructions","System instructions and temperature","chat-options","prompt sampling answers personality"),
        SearchOption("Chat history","Messages included in the next request","chat-options","conversation context memory"),
        SearchOption("Chat model routing","Local, provider or scenario route","chat-options","LLM selection"),
        SearchOption("Fonts and readability","Font size, color contrast and interface font","appearance","text typography"),
        SearchOption("Themes and layout","Light / dark, accents, surface and adaptive sidebar","appearance","desktop mobile colors glass"),
        SearchOption("Microphone and voice","Offline / online speech adapters and installed voices","voice","STT TTS hands free"),
        SearchOption("Device access settings","Owner-approved device enrollment and scopes","network","other phone desktop permissions settings"),
        SearchOption("Model context and quantization","Runtime options; reload model to apply","models","KV cache weights context"),
    )
    fun matches(query:String,text:String):Boolean=words(query).all{text.contains(it,true)}
    private fun words(query:String)=query.trim().take(600).split(Regex("\\s+")).filter{it.isNotBlank()}.take(75)
    fun snippet(text:String,query:String):String {
        val needle=words(query).firstOrNull().orEmpty()
        val at=if(needle.isEmpty()) 0 else text.indexOf(needle,ignoreCase=true).coerceAtLeast(0)
        var start=(at-64).coerceAtLeast(0)
        if(start>0 && Character.isLowSurrogate(text[start]) && Character.isHighSurrogate(text[start-1])) start--
        var end=(start+320).coerceAtMost(text.length)
        if(end<text.length && end>0 && Character.isHighSurrogate(text[end-1]) && Character.isLowSurrogate(text[end])) end--
        return (if(start>0) "…" else "")+text.substring(start,end).replace('\n',' ')+(if(end<text.length) "…" else "")
    }
    fun search(query:String,chats:List<ChatConversation>,models:List<LibraryModel>,articles:List<SearchArticle>,directory:DeviceDirectorySnapshot):List<AppSearchResult> {
        if(query.isBlank()) return emptyList()
        val out=mutableListOf<AppSearchResult>()
        WorkspaceDestinations.search(query).forEach{out+=AppSearchResult("page:${it.id}",it.title,it.description,SearchCategory.APP,it.id)}
        options.filter{matches(query,"${it.title} ${it.detail} ${it.keywords}")}.forEachIndexed{i,it->out+=AppSearchResult("option:$i:${it.destination}",it.title,it.detail,SearchCategory.APP,it.destination)}
        models.take(200).filter{matches(query,"${it.name} ${it.source} ${it.phase} ${it.metadata?.quantization.orEmpty()}")}.forEach{out+=AppSearchResult("model:${it.id}",it.name,"Model library · ${it.phase} · ${it.source}",SearchCategory.APP,"models")}
        chats.take(40).forEach{chat->
            if(matches(query,chat.title)) out+=AppSearchResult("chat:${chat.id}",chat.title,"Conversation title",SearchCategory.CHATS,conversationId=chat.id)
            chat.messages.takeLast(100).filter{matches(query,it.text)}.take(20).forEach{message->out+=AppSearchResult("message:${message.id}",chat.title,"${message.role}: ${snippet(message.text,query)}",SearchCategory.CHATS,conversationId=chat.id,messageId=message.id)}
        }
        articles.take(20).filter{matches(query,"${it.title}\n${it.text}")}.forEach{out+=AppSearchResult("article:${it.id}",it.title,snippet(it.text,query),SearchCategory.ARTICLES,articleId=it.id)}
        directory.devices.filter{it.state==EnrollmentState.APPROVED}.take(1000).forEach{device->
            val text="${device.name} ${device.kind} ${device.access.joinToString()} ${device.requestedRoles.joinToString()} settings"
            if(matches(query,text)) out+=AppSearchResult("device:${device.id}",device.name,"Saved access: ${device.access.joinToString()} · ${device.kind}. Live remote settings are not fetched.",SearchCategory.DEVICES,"network")
        }
        return out.take(200)
    }
}

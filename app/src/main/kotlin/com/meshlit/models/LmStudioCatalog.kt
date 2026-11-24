package com.meshlit.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

internal data class CatalogFamily(val name:String,val path:String,val summary:String="",val publisher:String?=null,val capabilities:String="",val sizes:String="",val downloads:Long?=null,val updated:String="")

internal class LmStudioCatalog {
    private val familyPath=Regex("/models/[\\w.-]+")
    private val variantPath=Regex("/models/[\\w.-]+/[\\w.-]+")
    private val hubRepo=Regex("https://huggingface\\.co/([\\w.-]+/[\\w.-]+)/?")

    private suspend fun page(path:String)=withContext(Dispatchers.IO) {
        require(path=="/models" || familyPath.matches(path) || variantPath.matches(path))
        Jsoup.connect("https://lmstudio.ai$path").followRedirects(false).timeout(12000)
            .maxBodySize(5*1024*1024).userAgent("Meshlit/Android").get()
    }
    suspend fun families():List<CatalogFamily> = page("/models")
        .select("a[data-model-page-has-downloads=true]").mapNotNull { link ->
            val path=link.attr("href")
            val name=link.selectFirst("h2")?.text()
            if(name.isNullOrBlank() || !familyPath.matches(path)) null else CatalogFamily(name,path,link.selectFirst("p")?.text().orEmpty(),publisher(name),
                link.select("[data-model-page-capabilities] [title]").joinToString(" · "){it.attr("title")},
                link.selectFirst("[aria-label=Parameter sizes]")?.text().orEmpty(),
                link.selectFirst("[data-model-page-downloads]")?.attr("data-model-page-downloads")?.toLongOrNull(),
                link.selectFirst("time")?.attr("datetime").orEmpty().take(10))
        }.distinctBy { it.path }.also { require(it.isNotEmpty()) { "LM Studio returned no downloadable models." } }

    private fun publisher(name:String):String? {
        val publishers=mapOf("qwen" to "Qwen","gemma" to "google","granite" to "ibm-granite","mistral" to "mistralai",
            "ministral" to "mistralai","devstral" to "mistralai","bonsai" to "prism-ml","lfm" to "LiquidAI",
            "nemotron" to "nvidia","deepseek" to "deepseek-ai","muse" to "meta-llama","gpt-oss" to "openai","phi" to "microsoft")
        return publishers.entries.firstOrNull{name.startsWith(it.key,true)}?.value
    }
    suspend fun variants(family:CatalogFamily):List<CatalogFamily> = page(family.path)
        .select("[data-model-page-artifacts] a[href]").mapNotNull { link ->
            val path=link.attr("href")
            if(variantPath.matches(path)) family.copy(name=path.substringAfterLast('/'),path=path) else null
        }.distinctBy { it.path }

    suspend fun repositories(variant:CatalogFamily):List<String> = page(variant.path)
        .select("a[href]").mapNotNull { hubRepo.matchEntire(it.attr("href"))?.groupValues?.get(1) }
        .filter { it.contains("gguf",ignoreCase=true) }.distinct()
}

package com.meshlit.core.inference.importers

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

/**
 * Parses the Ollama registry catalog (`/v2/_catalog`) and one
 * namespace-level tag listing per model into a list of
 * [ImportedModelSource].
 *
 * Only the public registry at `registry.ollama.ai` is supported.
 * Private registries require auth headers and are intentionally out
 * of scope for Phase 3.
 */
class OllamaRegistryParser(
    private val baseRegistryUrl: String = "https://registry.ollama.ai",
    private val http: HttpFetcher = DefaultHttpFetcher,
) {

    suspend fun listCatalog(maxModels: Int = 200): MeshlitResult<List<OllamaCatalogEntry>> {
        val url = "$baseRegistryUrl/v2/_catalog"
        val body = when (val r = http.get(url)) {
            is MeshlitResult.Success -> r.value
            is MeshlitResult.Failure -> return MeshlitResult.Failure(r.error)
        }
        val models = parseCatalog(body).take(maxModels)
        return MeshlitResult.Success(models)
    }

    /**
     * Pulls the tags for one namespace/model. Returns an empty list
     * if the namespace/model has no published tags.
     */
    suspend fun listTags(namespace: String, model: String): MeshlitResult<List<String>> {
        val url = "$baseRegistryUrl/v2/$namespace/$model/tags/list"
        val body = when (val r = http.get(url)) {
            is MeshlitResult.Success -> r.value
            is MeshlitResult.Failure -> return MeshlitResult.Failure(r.error)
        }
        return MeshlitResult.Success(parseTagNames(body))
    }

    /**
     * Convenience: enumerate the catalog and return at most one entry
     * per `namespace/model` (no tag drilldown). Used for the curated
     * catalog refresh on app start.
     */
    suspend fun catalogToSources(): MeshlitResult<List<ImportedModelSource>> {
        val cat = when (val r = listCatalog()) {
            is MeshlitResult.Success -> r.value
            is MeshlitResult.Failure -> return MeshlitResult.Failure(r.error)
        }
        val out = cat.map { entry ->
            ImportedModelSource(
                displayName = "${entry.namespace}/${entry.model}",
                url = "$baseRegistryUrl/v2/${entry.namespace}/${entry.model}",
                sha256 = null,
                sizeBytes = null,
                format = ImportedModelFormat.UNKNOWN,
            )
        }
        return MeshlitResult.Success(out)
    }

    internal fun parseCatalog(body: String): List<OllamaCatalogEntry> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        // `root["repositories"]` is `JsonNull` when the JSON value is
        // explicit null (e.g. `{"repositories":null}`); `.jsonArray`
        // throws on `JsonNull`. Treat anything that isn't a real array
        // as an empty list.
        val reposElement = root["repositories"]
        if (reposElement !is kotlinx.serialization.json.JsonArray) return emptyList()
        val repos = reposElement
        return repos.mapNotNull { element ->
            val obj = element.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            // Ollama names are "namespace/model"; library/* are official.
            val parts = name.split('/', limit = 2)
            if (parts.size != 2) return@mapNotNull null
            OllamaCatalogEntry(namespace = parts[0], model = parts[1])
        }
    }

    internal fun parseTagNames(body: String): List<String> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        // `root["tags"]` may be `JsonNull` — same guard as parseCatalog.
        val tagsElement = root["tags"]
        if (tagsElement !is kotlinx.serialization.json.JsonArray) return emptyList()
        return tagsElement.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.content }
    }

    data class OllamaCatalogEntry(val namespace: String, val model: String)
}

/**
 * Head-only helper for HEAD-style checks. Mirrors the signature used
 * by [HuggingFaceResolver] so a single fetcher implementation can
 * back both.
 */
suspend fun HttpFetcher.headOnly(url: String): Long? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        conn.getHeaderField("Content-Length")?.toLongOrNull().also { conn.disconnect() }
    }.getOrNull()
}

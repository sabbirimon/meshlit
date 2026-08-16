package com.meshlit.core.inference.importers

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

/**
 * Resolves a HuggingFace repo + file path into an [ImportedModelSource].
 *
 * Two requests are issued:
 *  1. `GET https://huggingface.co/api/models/{repoId}` to enumerate
 *     `siblings[]` and pick the matching file.
 *  2. `HEAD {rawUrl}` to read the `X-Linked-SHA256` header that HF
 *     attaches to its redirect CDN. Falls back to `Content-Length`
 *     only when SHA is missing.
 *
 * Public-only repos for now — no auth header is sent.
 */
class HuggingFaceResolver(
    private val httpClient: HttpFetcher = DefaultHttpFetcher,
    private val baseApiUrl: String = "https://huggingface.co/api/models",
) {

    suspend fun resolve(
        repoId: String,
        fileName: String,
        reference: String = "main",
    ): MeshlitResult<ImportedModelSource> {
        val repo = repoId.trim()
        if (repo.isBlank()) {
            return MeshlitResult.Failure(MeshlitError.Invalid("hf.blank_repo"))
        }
        if (fileName.isBlank()) {
            return MeshlitResult.Failure(MeshlitError.Invalid("hf.blank_file"))
        }

        val apiUrl = "$baseApiUrl/$repo"
        val body = when (val r = httpClient.get(apiUrl)) {
            is MeshlitResult.Success -> r.value
            is MeshlitResult.Failure -> return MeshlitResult.Failure(r.error)
        }

        val matched = findSibling(body, fileName)
            ?: return MeshlitResult.Failure(
                MeshlitError.Invalid("hf.file_not_found:$fileName")
            )

        val rawUrl = "https://huggingface.co/$repo/resolve/$reference/$fileName"
        val headInfo = httpClient.head(rawUrl)
        val sha256 = headInfo.linkedSha256
        val sizeBytes = matched.sizeBytes ?: headInfo.contentLength

        val displayName = fileName.substringAfterLast('/').ifBlank { repo.replace('/', '-') }
        val source = ImportedModelSource(
            displayName = displayName,
            url = rawUrl,
            sha256 = sha256,
            sizeBytes = sizeBytes,
            format = ImportedModelFormat.fromFileName(fileName),
        )
        return MeshlitResult.Success(source)
    }

    private fun findSibling(apiBody: String, fileName: String): SiblingMatch? {
        val root = runCatching { Json.parseToJsonElement(apiBody).jsonObject }
            .getOrNull() ?: return null
        val siblings = root["siblings"]?.jsonArray ?: return null
        for (element in siblings) {
            val obj = element.jsonObject
            val rfilename = obj["rfilename"]?.jsonPrimitive?.content ?: continue
            if (rfilename == fileName || rfilename.endsWith("/$fileName")) {
                val size = (obj["size"] as? JsonPrimitive)?.content?.toLongOrNull()
                return SiblingMatch(fileName = rfilename, sizeBytes = size)
            }
        }
        return null
    }

    private data class SiblingMatch(val fileName: String, val sizeBytes: Long?)
}

/**
 * Internal HTTP helper. We use the JDK's HttpURLConnection to avoid
 * pulling OkHttp into the inference module's tests; the production
 * app can wrap a coroutine-friendly client by passing a different
 * [HttpFetcher] implementation.
 */
interface HttpFetcher {
    suspend fun get(url: String): MeshlitResult<String>
    suspend fun head(url: String): HeadInfo
}

data class HeadInfo(
    val contentLength: Long?,
    val linkedSha256: String?,
)

object DefaultHttpFetcher : HttpFetcher {
    override suspend fun get(url: String): MeshlitResult<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "MeshlitImporter/1.0")
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream.bufferedReader().use { it.readText() }.also {
                conn.disconnect()
                if (code !in 200..299) {
                    throw java.io.IOException("HTTP $code from $url")
                }
            }
        }.fold(
            onSuccess = { MeshlitResult.Success(it) },
            onFailure = { MeshlitResult.Failure(MeshlitError.Network("hf.get", it)) },
        )
    }

    override suspend fun head(url: String): HeadInfo = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "MeshlitImporter/1.0")
                instanceFollowRedirects = true
            }
            // Force the connect so headers are populated.
            val code = conn.responseCode
            if (code !in 200..299 && code != 302) {
                throw java.io.IOException("HEAD $code for $url")
            }
            val length = conn.getHeaderField("Content-Length")?.toLongOrNull()
            val linkedSha = conn.getHeaderField("X-Linked-SHA256")
                ?: conn.getHeaderField("X-Linked-Sha256")
            HeadInfo(contentLength = length, linkedSha256 = linkedSha).also { conn.disconnect() }
        }.getOrElse { HeadInfo(contentLength = null, linkedSha256 = null) }
    }
}
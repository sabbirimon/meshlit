package com.meshlit.core.cloudmcp.rag

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * On-device RAG store. Stores small text documents + their
 * embedding vectors under a namespace and returns the top-K most
 * similar matches for a query embedding.
 *
 * **Status: in-memory stub.** Real persistence would use Room +
 * sqlite-vss for vector similarity search via the KNN operator.
 * The Room + sqlite-vss dependencies are already declared in
 * `core-cloud-mcp/build.gradle.kts`; this stub is the seam that
 * gets swapped for a Room DAO without changing call sites. The
 * follow-up PR wires KSP and the `RagDocument` entity.
 *
 * Cosine similarity is computed in-memory — fine for a few
 * hundred docs per namespace, which matches the local-only
 * use case. When the user picks [RagMode.Remote] the call
 * routes to [RemoteRagStore] and the local store is bypassed.
 */
class LocalRagStore(
    context: Context,
) {
    private data class Entry(
        val namespace: String,
        val text: String,
        val embedding: FloatArray,
    )

    private val _size = MutableStateFlow(0)
    val size: StateFlow<Int> = _size.asStateFlow()

    private val entries = mutableListOf<Entry>()

    fun put(namespace: String, text: String, embedding: FloatArray) {
        entries.add(Entry(namespace, text, embedding))
        _size.value = entries.size
    }

    /**
     * Return the top-[k] entries in [namespace] ranked by cosine
     * similarity to [query]. Empty list if the namespace has no
     * documents. Returned pairs are (text, similarity).
     */
    fun query(namespace: String, query: FloatArray, k: Int = 5): List<Pair<String, Float>> {
        return entries
            .asSequence()
            .filter { it.namespace == namespace }
            .map { it to cosine(it.embedding, query) }
            .sortedByDescending { it.second }
            .take(k)
            .map { it.first.text to it.second }
            .toList()
    }

    fun count(namespace: String): Int =
        entries.count { it.namespace == namespace }

    fun clear(namespace: String? = null) {
        if (namespace == null) {
            entries.clear()
        } else {
            entries.removeAll { it.namespace == namespace }
        }
        _size.value = entries.size
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)
        return if (denom == 0f) 0f else dot / denom
    }
}
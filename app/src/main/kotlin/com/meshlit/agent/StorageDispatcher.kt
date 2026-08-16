package com.meshlit.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Base64
import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.core.cloudmcp.agent.AgentCapabilityTools
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * `agent_storage_*` dispatcher (list / read / write). Backed by
 * the Storage Access Framework (SAF) so we never need
 * READ/WRITE_EXTERNAL_STORAGE.
 *
 * **How SAF + the registry fit together:**
 *  - The user grants a directory tree once via
 *    `Intent.ACTION_OPEN_DOCUMENT_TREE`. We persist the returned
 *    tree URI in `SettingsRepository.agentCapabilityAllowlistNow
 *    ("storage")` (one URI per entry).
 *  - At call time the dispatcher resolves the URI through
 *    `DocumentsContract`, validates the requested relative path
 *    against the tree root (path-traversal rejection), and
 *    performs the operation.
 *  - Multiple tree URIs = multiple sandboxes. Each list/read/write
 *    targets the first tree whose root matches the requested
 *    relative path; we don't auto-pick across trees.
 *
 * **Path-traversal rejection:**
 *  - Relative path is split on '/'.
 *  - Any segment equal to `..` rejects the call.
 *  - We normalize '.' segments out before walking the tree.
 *
 * **Why not DocumentFile:**
 *  `DocumentFile` is a thin convenience wrapper over the same
 *  APIs; using `DocumentsContract` directly avoids the extra
 *  classpath dependency.
 */
class StorageDispatcher(
    private val appContext: Context,
    private val registry: AgentCapabilityRegistry,
    private val settings: SettingsRepository,
) {
    /**
     * Trigger the system tree-picker. Called from the Settings
     * screen — not from the agent loop. Returns the persisted
     * tree URI as a String, or null if the user backed out.
     */
    fun launchTreePicker(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }
        return intent
    }

    /**
     * Persist a tree URI picked via [launchTreePicker]. Takes
     * `persistable=true` permission and writes the URI string into
     * the storage allowlist.
     */
    suspend fun grantTree(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val took = runCatching {
            appContext.contentResolver.takePersistableUriPermission(treeUri, flags)
        }.isSuccess
        if (took) {
            settings.addAgentCapabilityAllowlistEntry(
                tag = AgentCapability.Storage.tag,
                entry = treeUri.toString(),
            )
        }
        took
    }

    /**
     * Drop a tree URI from the allowlist and release the
     * persistable permission.
     */
    suspend fun revokeTree(treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settings.removeAgentCapabilityAllowlistEntry(
            tag = AgentCapability.Storage.tag,
            entry = treeUri.toString(),
        )
        true
    }

    suspend fun list(args: JsonObject): McpEvent.ToolResult {
        if (!registry.isAllowed(AgentCapability.Storage)) {
            return error("permission-denied: storage")
        }
        val relPath = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val traversal = checkTraversal(relPath)
        if (traversal != null) return traversal

        return withContext(Dispatchers.IO) {
            val tree = pickTreeFor(relPath)
                ?: return@withContext error("no-granted-tree for path '$relPath'")
            runCatching {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    tree.uri, tree.documentId,
                )
                val entries = appContext.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_SIZE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    ),
                    null, null, null,
                )?.use { cursor ->
                    buildList {
                        val idIdx = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        )
                        val nameIdx = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        )
                        val mimeIdx = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_MIME_TYPE,
                        )
                        val sizeIdx = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_SIZE,
                        )
                        val modifiedIdx = cursor.getColumnIndexOrThrow(
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        )
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(nameIdx) ?: continue
                            val isDir = cursor.getString(mimeIdx) ==
                                DocumentsContract.Document.MIME_TYPE_DIR
                            add(buildJsonObject {
                                put("name", JsonPrimitive(name))
                                put("isDirectory", JsonPrimitive(isDir))
                                put("sizeBytes", JsonPrimitive(
                                    if (isDir) -1L else cursor.getLong(sizeIdx),
                                ))
                                put("lastModifiedMs", JsonPrimitive(cursor.getLong(modifiedIdx)))
                                put("mimeType", JsonPrimitive(cursor.getString(mimeIdx) ?: ""))
                                put("documentId", JsonPrimitive(cursor.getString(idIdx) ?: ""))
                            })
                        }
                    }
                } ?: emptyList()
                ok(buildJsonObject {
                    put("tree", JsonPrimitive(tree.uri.toString()))
                    put("path", JsonPrimitive(relPath))
                    put("entries", buildJsonArray {
                        for (e in entries) add(e)
                    })
                }.toString())
            }.getOrElse { err ->
                error("storage-list-failed: ${err.javaClass.simpleName}: ${err.message}")
            }
        }
    }

    suspend fun read(args: JsonObject): McpEvent.ToolResult {
        if (!registry.isAllowed(AgentCapability.Storage)) {
            return error("permission-denied: storage")
        }
        val relPath = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val traversal = checkTraversal(relPath)
        if (traversal != null) return traversal
        val maxBytes = (args["maxBytes"]?.jsonPrimitive?.contentOrNull
            ?.toIntOrNull() ?: (1024 * 1024))
            .coerceIn(1, 10 * 1024 * 1024)

        return withContext(Dispatchers.IO) {
            val tree = pickTreeFor(relPath)
                ?: return@withContext error("no-granted-tree for path '$relPath'")
            runCatching {
                val docUri = resolveDocUri(tree.uri, tree.documentId, relPath)
                    ?: return@runCatching error("path-not-found: $relPath")
                appContext.contentResolver.openInputStream(docUri)?.use { input ->
                    val buf = ByteArray(maxBytes)
                    var total = 0
                    while (total < maxBytes) {
                        val n = input.read(buf, total, maxBytes - total)
                        if (n <= 0) break
                        total += n
                    }
                    val bytes = buf.copyOf(total)
                    ok(buildJsonObject {
                        put("path", JsonPrimitive(relPath))
                        put("bytes", JsonPrimitive(bytes.size))
                        put("mimeType", JsonPrimitive(
                            appContext.contentResolver.getType(docUri) ?: "application/octet-stream",
                        ))
                        put("dataBase64", JsonPrimitive(
                            Base64.encodeToString(bytes, Base64.NO_WRAP),
                        ))
                    }.toString())
                } ?: error("open-failed: $relPath")
            }.getOrElse { err ->
                error("storage-read-failed: ${err.javaClass.simpleName}: ${err.message}")
            }
        }
    }

    suspend fun write(args: JsonObject): McpEvent.ToolResult {
        if (!registry.isAllowed(AgentCapability.Storage)) {
            return error("permission-denied: storage")
        }
        val relPath = args["path"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val traversal = checkTraversal(relPath)
        if (traversal != null) return traversal
        val b64 = args["contentBase64"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (b64.isBlank()) return error("missing 'contentBase64'")
        val mimeType = args["mimeType"]?.jsonPrimitive?.contentOrNull
            ?: "application/octet-stream"

        return withContext(Dispatchers.IO) {
            val tree = pickTreeFor(relPath)
                ?: return@withContext error("no-granted-tree for path '$relPath'")
            runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val docUri = resolveOrCreateDocUri(tree.uri, tree.documentId, relPath, mimeType)
                    ?: return@runCatching error("path-create-failed: $relPath")
                appContext.contentResolver.openOutputStream(docUri, "wt")?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: return@runCatching error("open-output-failed: $relPath")
                ok(buildJsonObject {
                    put("path", JsonPrimitive(relPath))
                    put("bytes", JsonPrimitive(bytes.size))
                    put("status", JsonPrimitive("written"))
                }.toString())
            }.getOrElse { err ->
                error("storage-write-failed: ${err.javaClass.simpleName}: ${err.message}")
            }
        }
    }

    // ---- internals -------------------------------------------------

    /**
     * Reject path-traversal attempts. Returns an error
     * [McpEvent.ToolResult] if the path is malicious, `null`
     * otherwise (caller proceeds).
     */
    private fun checkTraversal(relPath: String): McpEvent.ToolResult? {
        if (relPath.isBlank()) return error("path cannot be empty")
        val parts = relPath.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) {
            return error("path traversal rejected: '$relPath'")
        }
        return null
    }

    private data class TreeRef(val uri: Uri, val documentId: String)

    /**
     * Resolve a relative path against every granted tree; return
     * the first whose documentId matches the requested root path.
     * v1: we treat each granted tree as a separate sandbox and
     * pick the tree whose documentId equals the leading
     * path-segment, OR the first tree if the path is `.` / root.
     */
    private fun pickTreeFor(relPath: String): TreeRef? {
        val allowed = settings.agentCapabilityAllowlistNow(AgentCapability.Storage.tag)
        val parsed = allowed.mapNotNull { entry ->
            runCatching {
                val uri = Uri.parse(entry)
                val docId = DocumentsContract.getTreeDocumentId(uri)
                TreeRef(uri, docId)
            }.getOrNull()
        }
        if (parsed.isEmpty()) return null
        val firstSegment = relPath.split('/').firstOrNull { it.isNotEmpty() && it != "." }
        return if (firstSegment == null) parsed.first()
        else parsed.firstOrNull { it.documentId.endsWith(":$firstSegment") } ?: parsed.first()
    }

    private fun resolveDocUri(
        treeUri: Uri,
        rootDocumentId: String,
        relPath: String,
    ): Uri? {
        val parts = relPath.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty()) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocumentId)
        }
        var current = rootDocumentId
        for (seg in parts) {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, current)
            val nextId = appContext.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
                arrayOf(seg),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: return null
            current = nextId
        }
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, current)
    }

    private fun resolveOrCreateDocUri(
        treeUri: Uri,
        rootDocumentId: String,
        relPath: String,
        mimeType: String,
    ): Uri? {
        val existing = resolveDocUri(treeUri, rootDocumentId, relPath)
        if (existing != null) return existing
        // Create intermediate directories.
        val parts = relPath.split('/').filter { it.isNotEmpty() && it != "." }
        var current = rootDocumentId
        for ((i, seg) in parts.withIndex()) {
            val isLast = i == parts.lastIndex
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, current)
            val nextId = appContext.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                "${DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
                arrayOf(seg),
                null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            current = nextId ?: run {
                val newDocUri = DocumentsContract.createDocument(
                    appContext.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, current),
                    if (isLast) mimeType
                    else DocumentsContract.Document.MIME_TYPE_DIR,
                    seg,
                ) ?: return null
                DocumentsContract.getDocumentId(newDocUri)
            }
        }
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, current)
    }

    private fun ok(body: String) = McpEvent.ToolResult(
        providerId = AgentCapabilityTools.PROVIDER_ID,
        callId = "",
        ok = true,
        body = body,
    )

    private fun error(message: String) = McpEvent.ToolResult(
        providerId = AgentCapabilityTools.PROVIDER_ID,
        callId = "",
        ok = false,
        body = message,
    )

    @Suppress("unused")
    private val keepFileRef = File::class

    @Suppress("unused")
    private val keepArrayRef = JsonArray::class
}
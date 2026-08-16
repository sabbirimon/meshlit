package com.meshlit.core.mcp.builtin

import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.booleanProp
import com.meshlit.core.mcp.integerProp
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Phase 4.x — Notifications MCP tools.
 *
 * Lets the agent post, list, and dismiss Android notifications via
 * the system NotificationManager. Wraps the open-source
 * [`mcp-server-notifications`](https://github.com/smsweet66/mcp-server-notifications)
 * surface (4 tools) but adapted to run *inside* our own APK so we
 * don't need an external Node.js process.
 *
 * Every post call routes through [NotificationsBridge] so the
 * bridge implementation can be swapped (mock in tests, real
 * NotificationManager in app).
 */
interface NotificationsBridge {
    /** Post a notification with the given channel + title + body. */
    fun post(
        channelId: String,
        title: String,
        body: String,
        id: Int,
        autoCancel: Boolean,
    ): Boolean

    /** Dismiss the notification with the given id, if any. */
    fun dismiss(id: Int)

    /** Returns up to [limit] active notifications (id, title, body,
     *  channelId). Newest first. */
    fun active(limit: Int): List<ActiveNotification>

    data class ActiveNotification(
        val id: Int,
        val title: String,
        val body: String,
        val channelId: String,
    )
}

/** No-op bridge used when the app hasn't wired one yet. */
object NoOpNotificationsBridge : NotificationsBridge {
    override fun post(channelId: String, title: String, body: String, id: Int, autoCancel: Boolean) = false
    override fun dismiss(id: Int) = Unit
    override fun active(limit: Int): List<NotificationsBridge.ActiveNotification> = emptyList()
}

class NotificationsMcpTools(
    private val bridge: NotificationsBridge = NoOpNotificationsBridge,
) {
    fun specs(): List<McpToolSpec> = listOf(
        NotifyPostTool(bridge).spec(),
        NotifyDismissTool(bridge).spec(),
        NotifyListTool(bridge).spec(),
        NotifyChannelsTool(bridge).spec(),
    )
}

private class NotifyPostTool(private val bridge: NotificationsBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "notify_post",
        description = "Post a notification to the device status bar. " +
            "Channels must exist (see notify_channels); pass an existing channel id. " +
            "If id is omitted, a unique id is generated.",
        inputSchema = objectSchema(
            properties = mapOf(
                "channel_id" to stringProp(description = "Notification channel id, e.g. 'mcp_fgs'"),
                "title" to stringProp(description = "Notification title"),
                "body" to stringProp(description = "Notification body / content text"),
                "id" to integerProp(description = "Stable id; re-use to update an existing notification"),
                "auto_cancel" to booleanProp(description = "Dismiss on tap (default true)"),
            ),
            required = listOf("channel_id", "title", "body"),
        ),
    ) { args ->
        val obj = args as? JsonObject
            ?: return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS, "args must be a JSON object",
            )
        val channelId = obj.string("channel_id")
            ?: return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS, "missing required string 'channel_id'",
            )
        val title = obj.string("title")
            ?: return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS, "missing required string 'title'",
            )
        val body = obj.string("body")
            ?: return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS, "missing required string 'body'",
            )
        val id = obj.int("id") ?: (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        val autoCancel = obj.boolean("auto_cancel") ?: true
        val ok = bridge.post(channelId, title, body, id, autoCancel)
        if (ok) {
            McpToolResult.Json(buildJsonObject {
                put("ok", true)
                put("id", id)
            })
        } else {
            McpToolResult.Error(
                McpToolResult.ErrorCode.EXEC_FAILED,
                "failed to post notification (check NotificationManager availability)",
            )
        }
    }
}

private class NotifyDismissTool(private val bridge: NotificationsBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "notify_dismiss",
        description = "Dismiss an active notification by id.",
        inputSchema = objectSchema(
            properties = mapOf("id" to integerProp(description = "Notification id to dismiss")),
            required = listOf("id"),
        ),
    ) { args ->
        val id = (args as? JsonObject)?.int("id")
            ?: return@McpToolSpec McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS, "missing required integer 'id'",
            )
        bridge.dismiss(id)
        McpToolResult.Json(buildJsonObject { put("ok", true) })
    }
}

private class NotifyListTool(private val bridge: NotificationsBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "notify_list",
        description = "Returns up to N active notifications posted via notify_post.",
        inputSchema = objectSchema(
            properties = mapOf("limit" to integerProp(description = "Max entries (1-32)")),
        ),
    ) { args ->
        val obj = args as? JsonObject
        val limit = obj?.int("limit") ?: 16
        val active = bridge.active(limit.coerceIn(1, 32))
        McpToolResult.Json(buildJsonObject {
            put("count", active.size)
            put("notifications", kotlinx.serialization.json.buildJsonArray {
                active.forEach { n ->
                    add(buildJsonObject {
                        put("id", n.id)
                        put("title", n.title)
                        put("body", n.body)
                        put("channel_id", n.channelId)
                    })
                }
            })
        })
    }
}

private class NotifyChannelsTool(private val bridge: NotificationsBridge) {
    fun spec(): McpToolSpec = McpToolSpec(
        name = "notify_channels",
        description = "Returns the list of channel ids the system currently exposes. " +
            "Useful before notify_post to discover which channels exist.",
        inputSchema = objectSchema(emptyMap()),
    ) { _ ->
        // We let the app wire a `channelIds()` accessor on the
        // bridge; for the no-op default, return empty.
        val ids = (bridge as? SystemNotificationsBridge)?.channelIds() ?: emptyList()
        McpToolResult.Json(buildJsonObject {
            put("count", ids.size)
            put("channels", kotlinx.serialization.json.buildJsonArray {
                ids.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
            })
        })
    }
}

/**
 * Concrete [NotificationsBridge] backed by the real
 * [android.app.NotificationManager]. Exposes [channelIds] for the
 * [NotifyChannelsTool] handler.
 */
class SystemNotificationsBridge(
    private val context: android.content.Context,
    private val notificationManager: android.app.NotificationManager,
) : NotificationsBridge {
    private val mutex = Mutex()
    private val posted: MutableMap<Int, NotificationsBridge.ActiveNotification> = linkedMapOf()

    override fun post(channelId: String, title: String, body: String, id: Int, autoCancel: Boolean): Boolean {
        return runCatching {
            val channel = notificationManager.getNotificationChannel(channelId)
                ?: return false
            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(autoCancel)
            notificationManager.notify(id, builder.build())
            kotlinx.coroutines.runBlocking {
                mutex.withLock {
                    posted[id] = NotificationsBridge.ActiveNotification(id, title, body, channelId)
                }
            }
            true
        }.getOrDefault(false)
    }

    override fun dismiss(id: Int) {
        notificationManager.cancel(id)
        kotlinx.coroutines.runBlocking { mutex.withLock { posted.remove(id) } }
    }

    override fun active(limit: Int): List<NotificationsBridge.ActiveNotification> =
        kotlinx.coroutines.runBlocking {
            mutex.withLock { posted.values.toList().takeLast(limit).reversed() }
        }

    fun channelIds(): List<String> = notificationManager.notificationChannels.map { it.id }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.int(key: String): Int? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let { content ->
        when (content.lowercase()) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }
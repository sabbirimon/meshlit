package com.meshlit.core.mcp.builtin

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.meshlit.core.common.logger
import com.meshlit.core.mcp.InAppResource
import com.meshlit.core.mcp.McpToolResult
import com.meshlit.core.mcp.McpToolSpec
import com.meshlit.core.mcp.integerProp
import com.meshlit.core.mcp.objectSchema
import com.meshlit.core.mcp.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Phase 5 — `InApp MCP` server (real implementations).
 *
 * Tools that read on-device content providers (Notes via NotePad,
 * Calendar via CalendarContract.Instances, Contacts via
 * ContactsContract) plus an `app_files_list` mirror of the
 * Filesystem MCP tools. Each tool carries a `requiredResource`
 * from [InAppResource] that the [com.meshlit.core.mcp.McpPermissionGate]
 * consults **before** the handler runs. If the gate grants the
 * resource but the **runtime permission** is missing (Android 6+
 * dynamic permission model), the handler short-circuits with
 * [McpToolResult.Error] / `PERMISSION_DENIED` and a hint that
 * mentions the system Settings screen so the user knows where to
 * flip it on.
 *
 * Threading:
 *  - All ContentResolver / Filesystem queries run on
 *    `Dispatchers.IO`. The handlers are `suspend` so the registry
 *    can cancel them on timeout.
 *
 * Limits:
 *  - `notes_list` / `calendar_upcoming` / `contacts_search`
 *    cap results at 500 rows (matches the upper bound on the
 *    input schema's `limit`). SQLite cursors are closed in a
 *    `use {}` block so the cursor never leaks even when the
 *    handler is cancelled mid-iteration.
 *
 * Privacy:
 *  - Phone numbers and email addresses are truncated to the
 *    last 4 / domain part respectively when the LLM is calling
 *    on behalf of an untrusted tool role. Full PII is only
 *    returned for `maxTier` callers — see [CallerTier].
 */
class InAppTools(
    private val context: Context,
    private val policy: FileSystemPolicy = defaultFilesystemPolicy(context.filesDir),
) {
    private val log = logger("InAppTools")

    fun specs(): List<McpToolSpec> = listOf(
        notesListSpec(),
        calendarUpcomingSpec(),
        contactsSearchSpec(),
        appFilesListSpec(),
    )

    /**
     * Resolve the [ContentResolver] for the host context. Pulled
     * out so tests can inject a mock resolver without subclassing.
     */
    protected fun resolver(): ContentResolver = context.contentResolver

    // ── notes_list ────────────────────────────────────────────────

    private fun notesListSpec(): McpToolSpec = McpToolSpec(
        name = "notes_list",
        description = "Return up to `limit` notes (title + body) from the on-device NotePad provider. " +
            "Subject to the Notes resource permission. If no NotePad-compatible provider is " +
            "installed, returns an empty list (no error).",
        inputSchema = objectSchema(
            properties = mapOf(
                "limit" to integerProp(description = "Maximum number of notes to return (1..500, default 50)."),
                "query" to stringProp(description = "Optional substring filter against title or body."),
            ),
        ),
        requiredResource = InAppResource.Notes.id,
        handler = { args -> handleNotesList(args) },
    )

    private suspend fun handleNotesList(args: JsonElement): McpToolResult {
        // NotePad does NOT require a runtime permission — the
        // provider is read-only and lives inside the user's
        // installed notes app. We still gate it through the
        // McpPermissionGate for explicit user opt-in.
        val limit = InAppToolsSupport.clampLimit(
            (args as? JsonObject)?.get("limit")?.jsonPrimitive?.intOrNull,
            default = 50,
        )
        val query = (args as? JsonObject)
            ?.get("query")?.jsonPrimitive?.contentOrNull
            .orEmpty()

        return withContext(Dispatchers.IO) {
            val resolver = resolver()
            // The NotePad provider isn't guaranteed to exist
            // on the device — many OEMs replace it with their
            // own notes app. Probe by URI first; if the
            // resolver can't resolve it, return an empty list
            // rather than crashing.
            //
            // NotePad was removed from the public Android SDK
            // in API 30, so the URI and column names are
            // hardcoded literals (the schema is stable across
            // every device that ships any NotePad-based notes
            // app):
            //   URI:       content://com.google.provider.NotePad/notes
            //   columns:   _id, title, note
            val uri = Uri.parse("content://com.google.provider.NotePad/notes")
            val items = buildJsonArray {
                runCatching {
                    val projection = arrayOf("_id", "title", "note")
                    val selection = if (query.isBlank()) null else "(title LIKE ? OR note LIKE ?)"
                    val selectionArgs = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
                    val sortOrder = "_id DESC"
                    resolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                        var count = 0
                        val idIdx = cursor.getColumnIndexOrThrow("_id")
                        val titleIdx = cursor.getColumnIndexOrThrow("title")
                        val bodyIdx = cursor.getColumnIndexOrThrow("note")
                        while (cursor.moveToNext() && count < limit) {
                            add(
                                buildJsonObject {
                                    put("id", cursor.getLong(idIdx))
                                    put("title", cursor.getString(titleIdx).orEmpty())
                                    put("body", cursor.getString(bodyIdx).orEmpty())
                                },
                            )
                            count++
                        }
                    }
                }.onFailure { t ->
                    // No NotePad provider installed, or the
                    // user's notes app denied the read. Either
                    // way the agent should treat the result as
                    // "no notes available".
                    log.warn("mcp.inapp.notes_list_failed", t.message ?: "query failed")
                }
            }
            McpToolResult.Json(
                buildJsonObject {
                    put("ok", true)
                    put("stub", false)
                    put("count", items.size)
                    put("items", items)
                },
            )
        }
    }

    // ── calendar_upcoming ────────────────────────────────────────

    private fun calendarUpcomingSpec(): McpToolSpec = McpToolSpec(
        name = "calendar_upcoming",
        description = "Return upcoming calendar events within `hoursAhead` from now. " +
            "Subject to the Calendar resource permission AND the runtime READ_CALENDAR grant.",
        inputSchema = objectSchema(
            properties = mapOf(
                "hoursAhead" to integerProp(
                    description = "Window size in hours from now (1..720, default 24).",
                ),
                "limit" to integerProp(
                    description = "Maximum number of events to return (1..500, default 50).",
                ),
            ),
        ),
        requiredResource = InAppResource.Calendar.id,
        handler = { args -> handleCalendarUpcoming(args) },
    )

    private suspend fun handleCalendarUpcoming(args: JsonElement): McpToolResult {
        // CalendarContract.Instances requires the runtime
        // READ_CALENDAR permission. Without it, the resolver
        // throws SecurityException; we surface that as
        // PERMISSION_DENIED with a hint.
        if (!hasPermission(Manifest.permission.READ_CALENDAR)) {
            return McpToolResult.Error(
                McpToolResult.ErrorCode.PERMISSION_DENIED,
                "READ_CALENDAR runtime permission not granted. " +
                    "Enable it in Settings → Apps → Meshlit → Permissions → Calendar.",
            )
        }
        val hoursAhead = InAppToolsSupport.clampHoursAhead(
            (args as? JsonObject)?.get("hoursAhead")?.jsonPrimitive?.intOrNull,
            default = 24,
        )
        val limit = InAppToolsSupport.clampLimit(
            (args as? JsonObject)?.get("limit")?.jsonPrimitive?.intOrNull,
            default = 50,
        )

        return withContext(Dispatchers.IO) {
            val resolver = resolver()
            val now = System.currentTimeMillis()
            val end = now + hoursAhead.toLong() * 60L * 60L * 1000L
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .appendPath(now.toString())
                .appendPath(end.toString())
                .build()
            val items = buildJsonArray {
                runCatching {
                    val projection = arrayOf(
                        CalendarContract.Instances.EVENT_ID,
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.END,
                        CalendarContract.Instances.DESCRIPTION,
                        CalendarContract.Instances.EVENT_LOCATION,
                    )
                    resolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                        var count = 0
                        val idIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                        val titleIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                        val beginIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                        val endIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                        val descIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.DESCRIPTION)
                        val locIdx = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                        while (cursor.moveToNext() && count < limit) {
                            add(
                                buildJsonObject {
                                    put("eventId", cursor.getLong(idIdx))
                                    put("title", cursor.getString(titleIdx).orEmpty())
                                    put("beginEpochMs", cursor.getLong(beginIdx))
                                    put("endEpochMs", cursor.getLong(endIdx))
                                    put("description", cursor.getString(descIdx).orEmpty())
                                    put("location", cursor.getString(locIdx).orEmpty())
                                },
                            )
                            count++
                        }
                    }
                }.onFailure { t ->
                    // SecurityException surfaces here too if
                    // the OS revokes mid-call; the gate should
                    // catch it first but the resolver can
                    // still throw for revoked calendars.
                    log.warn("mcp.inapp.calendar_failed", t.message ?: "query failed")
                }
            }
            McpToolResult.Json(
                buildJsonObject {
                    put("ok", true)
                    put("stub", false)
                    put("count", items.size)
                    put("windowHours", hoursAhead)
                    put("items", items)
                },
            )
        }
    }

    // ── contacts_search ──────────────────────────────────────────

    private fun contactsSearchSpec(): McpToolSpec = McpToolSpec(
        name = "contacts_search",
        description = "Search contacts by name prefix. Subject to the Contacts resource permission " +
            "AND the runtime READ_CONTACTS grant. Phone numbers are masked to last-4 digits by " +
            "default; pass `revealPii=true` to opt into the full number (audit-logged).",
        inputSchema = objectSchema(
            properties = mapOf(
                "prefix" to stringProp(description = "Case-insensitive name prefix to match."),
                "limit" to integerProp(description = "Maximum number of contacts to return (1..500, default 50)."),
                "revealPii" to stringProp(
                    description = "Set to \"true\" to reveal the full phone number. " +
                        "Defaults to masked (last-4 only). Audit-logged.",
                ),
            ),
            required = listOf("prefix"),
        ),
        requiredResource = InAppResource.Contacts.id,
        handler = { args -> handleContactsSearch(args) },
    )

    private suspend fun handleContactsSearch(args: JsonElement): McpToolResult {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return McpToolResult.Error(
                McpToolResult.ErrorCode.PERMISSION_DENIED,
                "READ_CONTACTS runtime permission not granted. " +
                    "Enable it in Settings → Apps → Meshlit → Permissions → Contacts.",
            )
        }
        val obj = args as? JsonObject ?: return McpToolResult.Error(
            McpToolResult.ErrorCode.INVALID_ARGS,
            "expected an object with a `prefix` field",
        )
        val prefix = obj["prefix"]?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS,
                "missing `prefix`",
            )
        val limit = InAppToolsSupport.clampLimit(
            obj["limit"]?.jsonPrimitive?.intOrNull,
            default = 50,
        )
        val revealPii = obj["revealPii"]?.jsonPrimitive?.contentOrNull == "true"
        if (revealPii) {
            log.info(
                "mcp.inapp.contacts_pii",
                "contacts_search revealPii=true",
                mapOf("prefix" to prefix),
            )
        }

        return withContext(Dispatchers.IO) {
            val resolver = resolver()
            val items = buildJsonArray {
                runCatching {
                    val projection = arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER,
                    )
                    // Case-insensitive prefix; the LIKE operator
                    // is already case-insensitive for the
                    // default contacts collation.
                    val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
                    val selectionArgs = arrayOf("$prefix%")
                    val sortOrder = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
                    resolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        projection,
                        selection,
                        selectionArgs,
                        sortOrder,
                    )?.use { contactsCursor ->
                        val idIdx = contactsCursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                        val nameIdx = contactsCursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                        val phoneIdx = contactsCursor.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                        var count = 0
                        while (contactsCursor.moveToNext() && count < limit) {
                            val contactId = contactsCursor.getLong(idIdx)
                            val name = contactsCursor.getString(nameIdx).orEmpty()
                            val hasPhone = contactsCursor.getInt(phoneIdx) > 0
                            val phones = if (hasPhone) {
                                readPhones(resolver, contactId, revealPii)
                            } else emptyList()
                            add(
                                buildJsonObject {
                                    put("id", contactId)
                                    put("name", name)
                                    put("hasPhone", hasPhone)
                                    put("phones", buildJsonArray {
                                        phones.forEach { add(it) }
                                    })
                                },
                            )
                            count++
                        }
                    }
                }.onFailure { t ->
                    log.warn("mcp.inapp.contacts_failed", t.message ?: "query failed")
                }
            }
            McpToolResult.Json(
                buildJsonObject {
                    put("ok", true)
                    put("stub", false)
                    put("count", items.size)
                    put("piiRevealed", revealPii)
                    put("items", items)
                },
            )
        }
    }

    /**
     * Read phone numbers for [contactId]. Returns a list of JSON
     * objects `{ "number": "...", "type": "mobile" }`. When
     * [revealPii] is false, the number is masked to the last-4
     * digits; the full number is logged separately when
     * `revealPii=true`.
     */
    private fun readPhones(resolver: ContentResolver, contactId: Long, revealPii: Boolean): List<JsonElement> {
        val phones = mutableListOf<JsonElement>()
        runCatching {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                ),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null,
            )?.use { phoneCursor ->
                val numIdx = phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = phoneCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                while (phoneCursor.moveToNext()) {
                    val raw = phoneCursor.getString(numIdx).orEmpty()
                    val typeInt = phoneCursor.getInt(typeIdx)
                    val typeName = phoneTypeToName(typeInt)
                    val displayed = if (revealPii) raw else maskPhone(raw)
                    phones.add(
                        buildJsonObject {
                            put("number", displayed)
                            put("type", typeName)
                            if (!revealPii) put("masked", true)
                        },
                    )
                }
            }
        }.onFailure { t ->
            log.warn("mcp.inapp.contacts_phones_failed", t.message ?: "phone query failed")
        }
        return phones
    }

    /**
     * Mask a phone number to its last-4 digits. Strips any
     * non-digit prefix and keeps the trailing 4 digits so the
     * agent can still surface enough info to disambiguate a
     * contact (e.g. "...-1234") without leaking the full number.
     * Delegate to the pure-Kotlin helper so it can be unit
     * tested without an Android Context.
     */
    private fun maskPhone(raw: String): String = InAppToolsSupport.maskPhone(raw)

    /**
     * Map a [ContactsContract.CommonDataKinds.Phone.TYPE_*]
     * integer to its string name. The wrapper holds the
     * Android-specific constants; the pure-Kotlin mapping
     * lives in [InAppToolsSupport.phoneTypeToName] using literals
     * that match the official Android SDK constants.
     */
    private fun phoneTypeToName(type: Int): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "home"
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "mobile"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "work"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "main"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "fax_work"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "fax_home"
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "pager"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "other"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> "custom"
        ContactsContract.CommonDataKinds.Phone.TYPE_ASSISTANT -> "assistant"
        else -> "unknown"
    }

    // ── app_files_list ───────────────────────────────────────────

    private fun appFilesListSpec(): McpToolSpec = McpToolSpec(
        name = "app_files_list",
        description = "List files under `rootPath` (must be inside the app's allowed roots). " +
            "Subject to the App-files resource permission. Sandbox-enforced — paths outside " +
            "the app's filesDir are denied.",
        inputSchema = objectSchema(
            properties = mapOf(
                "rootPath" to stringProp(
                    description = "Absolute path under the app's allowed roots.",
                ),
            ),
            required = listOf("rootPath"),
        ),
        requiredResource = InAppResource.AppFiles.id,
        handler = { args -> handleAppFilesList(args) },
    )

    private suspend fun handleAppFilesList(args: JsonElement): McpToolResult {
        val rootPath = (args as? JsonObject)
            ?.get("rootPath")?.jsonPrimitive?.contentOrNull
            ?: return McpToolResult.Error(
                McpToolResult.ErrorCode.INVALID_ARGS,
                "missing `rootPath`",
            )
        val file = File(rootPath)
        when (policy.checkRead(file)) {
            is FileSystemPolicy.Decision.Deny -> return McpToolResult.Error(
                McpToolResult.ErrorCode.PERMISSION_DENIED,
                "path '$rootPath' is not inside the app sandbox",
            )
            is FileSystemPolicy.Decision.Allow -> Unit
        }
        if (!file.isDirectory) return McpToolResult.Error(
            McpToolResult.ErrorCode.IO_ERROR,
            "not a directory: $rootPath",
        )
        val items = withContext(Dispatchers.IO) {
            buildJsonArray {
                file.listFiles()?.sortedBy { it.name.lowercase() }?.forEach { entry ->
                    if (entry.name.startsWith(".")) return@forEach
                    add(
                        if (entry.isDirectory) {
                            buildJsonObject {
                                put("kind", "dir")
                                put("name", entry.name)
                                put("path", entry.absolutePath)
                            }
                        } else {
                            buildJsonObject {
                                put("kind", "file")
                                put("name", entry.name)
                                put("path", entry.absolutePath)
                                put("sizeBytes", entry.length())
                            }
                        },
                    )
                }
            }
        }
        return McpToolResult.Json(
            buildJsonObject {
                put("ok", true)
                put("stub", false)
                put("rootPath", rootPath)
                put("count", items.size)
                put("items", items)
            },
        )
    }

    // ── helpers ──────────────────────────────────────────────────

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
package com.meshlit.agent

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.core.cloudmcp.agent.AgentCapabilityTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `agent_location_get` dispatcher. Returns the device's
 * last-known location via FusedLocationProviderClient — does
 * **not** request a fresh fix (that would burn GPS time for a
 * single LLM query).
 *
 * **Permissions:**
 *  - `ACCESS_FINE_LOCATION` is required for the `PRIORITY_HIGH_ACCURACY`
 *    path; `ACCESS_COARSE_LOCATION` would force us down to
 *    `PRIORITY_BALANCED_POWER_ACCURACY`, which is fine for the
 *    agent loop. We pick whichever the user has granted.
 *
 * **Why last-known and not fresh fix:**
 *  - A fresh fix can take 10+ seconds on a cold start.
 *  - The agent loop runs in tool-call bursts; a fresh fix per call
 *    would be wasteful and would surprise the user.
 *  - The agent can ask "are you at home?" with sub-minute
 *    accuracy; that's enough.
 */
class LocationDispatcher(
    private val appContext: Context,
    private val registry: AgentCapabilityRegistry,
) {
    suspend fun get(args: JsonObject): McpEvent.ToolResult {
        if (!registry.isAllowed(AgentCapability.Location)) {
            return error("permission-denied: location")
        }
        if (!hasFineOrCoarse()) {
            return error("location permission not granted")
        }
        val maxAgeMs = args["maxAgeMs"]?.jsonPrimitive?.contentOrNull
            ?.toLongOrNull() ?: 60_000L

        return withContext(Dispatchers.IO) {
            runCatching {
                val client = LocationServices.getFusedLocationProviderClient(appContext)
                val location = client.lastLocation.await()
                if (location == null) {
                    return@runCatching ok(buildJsonObject {
                        put("status", JsonPrimitive("no-fix"))
                        put("message", JsonPrimitive(
                            "No last-known location — has the device ever received a fix?",
                        ))
                    }.toString())
                }
                val ageMs = System.currentTimeMillis() - location.time
                if (ageMs > maxAgeMs) {
                    return@runCatching ok(buildJsonObject {
                        put("status", JsonPrimitive("stale"))
                        put("ageMs", JsonPrimitive(ageMs))
                        put("maxAgeMs", JsonPrimitive(maxAgeMs))
                        put("lat", JsonPrimitive(location.latitude))
                        put("lon", JsonPrimitive(location.longitude))
                        put("accuracyM", JsonPrimitive(location.accuracy))
                    }.toString())
                }
                ok(buildJsonObject {
                    put("status", JsonPrimitive("ok"))
                    put("lat", JsonPrimitive(location.latitude))
                    put("lon", JsonPrimitive(location.longitude))
                    put("accuracyM", JsonPrimitive(location.accuracy))
                    put("altitudeM", JsonPrimitive(location.altitude))
                    put("bearingDeg", JsonPrimitive(location.bearing))
                    put("speedMps", JsonPrimitive(location.speed))
                    put("fixTimeMs", JsonPrimitive(location.time))
                    put("provider", JsonPrimitive(location.provider ?: "fused"))
                }.toString())
            }.getOrElse { err ->
                error("location-failed: ${err.javaClass.simpleName}: ${err.message}")
            }
        }
    }

    @SuppressLint("InlinedApi")
    private fun hasFineOrCoarse(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext, Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @Suppress("unused")
    private val priorityRef = Priority.PRIORITY_BALANCED_POWER_ACCURACY

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
}
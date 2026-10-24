package com.meshlit.ui.modern

import com.meshlit.core.common.RemoteEndpoint
import java.text.DateFormat
import java.util.Date

/** Labels for saved endpoints; the model/name is not a unique device identity. */
fun savedDeviceId(endpoint: RemoteEndpoint): String =
    endpoint.id.removePrefix("node-").takeLast(12)

fun savedDeviceAddedAt(endpoint: RemoteEndpoint): String =
    if (endpoint.addedAtMs > 0L && endpoint.addedAtMs <= System.currentTimeMillis())
        "Added ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(endpoint.addedAtMs))}"
    else "Added date unknown (older record)"

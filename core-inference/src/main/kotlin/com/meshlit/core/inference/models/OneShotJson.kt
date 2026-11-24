package com.meshlit.core.inference.models

import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

/** Even a server-requested follow-up must not repeat a billed/side-effecting POST. */
internal fun String.oneShotJsonBody():RequestBody {
    val body=toRequestBody("application/json".toMediaType())
    return object:RequestBody() {
        override fun contentType()=body.contentType()
        override fun contentLength()=body.contentLength()
        override fun isOneShot()=true
        override fun writeTo(sink:BufferedSink)=body.writeTo(sink)
    }
}

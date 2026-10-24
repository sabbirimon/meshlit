package com.meshlit.util

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Reads at most maxBytes plus one, so a growing file cannot escape its size cap. */
internal fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
    require(maxBytes > 0)
    val output = ByteArrayOutputStream(minOf(maxBytes, 8192))
    val buffer = ByteArray(8192)
    while (output.size() <= maxBytes) {
        val count = input.read(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

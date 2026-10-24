// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
// Earlier Apache-2.0 rights remain; see core-hyperl/LICENSE and NOTICE.
package com.meshlit.core.hyperl

// Generated from contracts/hyperl-1.json by scripts/generate_contract.py.
object HyperLContract {
    const val MAX_INPUTS = 8
    const val MAX_STEPS = 64
    const val MAX_VECTOR_ELEMENTS = 262144
    const val MAX_RETAINED_ELEMENTS = 1048576
    const val CANCELLATION_INTERVAL = 1024
    const val MAX_PROGRAM_CHARS = 65536
    const val MAX_INPUT_CHARS = 16777216
    const val LANGUAGE_FORMAT = "hyperl/1"
    const val RUNTIME_REVISION = "hyperl-cpu/1"
    const val IDENTIFIER_PATTERN = "[A-Za-z][A-Za-z0-9_]{0,31}"
    val operandCounts = mapOf("add" to 2, "multiply" to 2, "relu" to 1, "sum" to 1)
}

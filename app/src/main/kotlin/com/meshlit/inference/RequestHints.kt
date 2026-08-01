package com.meshlit.inference

import com.meshlit.core.inference.net.RequestHints as WireHints

/**
 * Local-side wrapper for the wire-level [com.meshlit.core.inference.net.RequestHints].
 *
 * The wire DTO lives in `:core-inference` because both the server
 * (router) and client parse/emit it. The wrapper here is a thin
 * alias that adds the Jobs-screen-side defaults and lets `:app`-only
 * call sites import from a familiar package without reaching into
 * `net`.
 *
 * The Jobs screen always sends `role=brain,gpu=false` for now — the
 * Phase 2 router picks it up; Phase 3 lets the user override.
 */
typealias RequestHints = WireHints

/** Build a default [RequestHints] suitable for the Jobs screen. */
fun defaultRequestHints(): RequestHints = RequestHints(role = "brain", needsGpu = false)
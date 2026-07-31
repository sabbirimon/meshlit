package com.meshlit.core.common

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Lightweight logging façade. Every background code path emits a
 * start/stop/failure line — this project's Phase 5 adaptive scheduler
 * depends on this instrumentation being consistent from day one
 * (BUILD_GUIDE §0, CLAUDE.md coding conventions).
 *
 * The tag is a stable string the orchestrator can group on. Module
 * name + event verb is the convention: "core.inference.load_start",
 * "core.router.dispatch_fail".
 */
interface MeshlitLogger {
    fun info(tag: String, message: String, context: Map<String, Any?> = emptyMap())
    fun warn(tag: String, message: String, context: Map<String, Any?> = emptyMap())
    fun error(tag: String, message: String, error: Throwable? = null, context: Map<String, Any?> = emptyMap())
}

/** SLF4J-backed implementation. */
class Slf4jLogger(private val delegate: Logger) : MeshlitLogger {
    override fun info(tag: String, message: String, context: Map<String, Any?>) {
        if (context.isEmpty()) delegate.info("[$tag] $message")
        else delegate.info("[$tag] $message ctx={}", context)
    }
    override fun warn(tag: String, message: String, context: Map<String, Any?>) {
        if (context.isEmpty()) delegate.warn("[$tag] $message")
        else delegate.warn("[$tag] $message ctx={}", context)
    }
    override fun error(tag: String, message: String, error: Throwable?, context: Map<String, Any?>) {
        if (error != null) {
            if (context.isEmpty()) delegate.error("[$tag] $message", error)
            else delegate.error("[$tag] $message ctx={}", context, error)
        } else {
            if (context.isEmpty()) delegate.error("[$tag] $message")
            else delegate.error("[$tag] $message ctx={}", context)
        }
    }
}

/** Convenience: get a logger for a class without an SLF4J boilerplate line. */
fun logger(forClass: Class<*>): MeshlitLogger =
    Slf4jLogger(LoggerFactory.getLogger(forClass))

fun logger(name: String): MeshlitLogger =
    Slf4jLogger(LoggerFactory.getLogger(name))

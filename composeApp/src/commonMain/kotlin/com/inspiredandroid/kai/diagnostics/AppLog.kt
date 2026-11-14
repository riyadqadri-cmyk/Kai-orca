package com.inspiredandroid.kai.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock

/** A bounded, observable log. Never send prompts, tool arguments, or credentials here. */
object AppLog {
    internal const val MAX_CHARS = 256_000
    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()
    private val secrets = MutableStateFlow<Set<String>>(emptySet())

    fun registerSecret(value: String) {
        if (value.isNotBlank()) secrets.update { it + value }
    }

    fun info(tag: String, message: String) = record("INFO", tag, message)

    fun error(tag: String, message: String, cause: Throwable? = null) {
        record("ERROR", tag, message + (cause?.let { "\n${it.stackTraceToString()}" } ?: ""))
    }

    /** The persistence observer saves the empty buffer; later events continue logging. */
    fun clear() {
        _text.value = ""
    }

    fun restore(previous: String) {
        _text.value = bounded(redact(previous))
    }

    private fun record(level: String, tag: String, message: String) {
        val line = "${Clock.System.now()} [$level] [$tag] ${redact(message).take(12_000)}\n"
        _text.update { bounded(it + line) }
    }

    internal fun redact(message: String): String {
        var safe = message
        // Replace longer values first in case one configured key is a prefix of another.
        for (secret in secrets.value.sortedByDescending { it.length }) {
            safe = safe.replace(secret, "[REDACTED]")
        }
        safe = Regex("(?i)Bearer\\s+[^\\s\"',}]+").replace(safe, "Bearer [REDACTED]")
        safe = Regex("(?i)([\"']?(?:api[-_]?key|access[-_]?token|refresh[-_]?token|password|authorization|cookie)[\"']?\\s*[:=]\\s*)[\"']?[^\\s\"',}&]+").replace(safe) {
            it.groupValues[1] + "[REDACTED]"
        }
        // URLs in exception messages may contain credentials in user-info or query values.
        safe = Regex("(https?://)[^/\\s@]+@").replace(safe, "$1[REDACTED]@")
        safe = Regex("([?&][^=\\s&]+)=([^&\\s]+)").replace(safe, "$1=[REDACTED]")
        return safe
    }

    private fun bounded(value: String): String {
        if (value.length <= MAX_CHARS) return value
        val tail = value.takeLast(MAX_CHARS)
        return tail.substringAfter('\n', tail)
    }
}

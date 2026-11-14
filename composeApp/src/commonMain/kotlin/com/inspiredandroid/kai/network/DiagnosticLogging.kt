package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.diagnostics.AppLog
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlin.time.TimeSource

private val startedAt = AttributeKey<TimeSource.Monotonic.ValueTimeMark>("diagnostics-started-at")

/** Metadata only: leave response bodies untouched, including streaming responses. */
internal val DiagnosticLogging = createClientPlugin("DiagnosticLogging") {
    onRequest { request, _ ->
        listOf("Authorization", "x-api-key", "x-goog-api-key").forEach { name ->
            request.headers[name]?.removePrefix("Bearer ")?.let(AppLog::registerSecret)
        }
        request.attributes.put(startedAt, TimeSource.Monotonic.markNow())
        val url = request.url.build()
        AppLog.info("HTTP", "${request.method.value} ${url.host}${url.encodedPath}")
    }
    onResponse { response ->
        val request = response.call.request
        val elapsed = request.attributes.getOrNull(startedAt)?.elapsedNow()?.inWholeMilliseconds
        val summary = "${request.method.value} ${request.url.host}${request.url.encodedPath} -> HTTP ${response.status.value} (${elapsed ?: "?"} ms)"
        if (response.status.isSuccess()) {
            val routedModel = response.headers["x-model"]
            val provider = response.headers["x-provider"]
            AppLog.info("HTTP", "$summary; model=${routedModel ?: "unreported"}; provider=${provider ?: "unreported"}")
        } else {
            val requestId = response.headers["x-request-id"] ?: response.headers["cf-ray"]
            val retryAfter = response.headers["retry-after"]
            AppLog.error("HTTP", "$summary; request-id=${requestId ?: "unavailable"}; retry-after=${retryAfter ?: "unavailable"}")
        }
    }
}

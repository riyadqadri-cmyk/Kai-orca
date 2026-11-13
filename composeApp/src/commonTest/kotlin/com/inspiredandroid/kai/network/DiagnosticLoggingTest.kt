package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.diagnostics.AppLog
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticLoggingTest {
    @Test
    fun logsHttpFailureMetadataWithoutConsumingBodyOrExposingCredentials() = runTest {
        AppLog.restore("")
        val body = """{"error":"All free providers failed"}"""
        val client = HttpClient(MockEngine { respond(body, HttpStatusCode.TooManyRequests, headersOf("retry-after", "30")) }) {
            install(DiagnosticLogging)
        }
        try {
            val response = client.get("https://example.com/chat/completions?api_key=query-secret") {
                bearerAuth("header-secret")
            }
            assertEquals(body, response.bodyAsText())
            val log = AppLog.text.value
            assertTrue(log.contains("HTTP 429"))
            assertTrue(log.contains("retry-after=30"))
            assertFalse(log.contains("query-secret"))
            assertFalse(log.contains("header-secret"))
        } finally {
            client.close()
        }
    }

    @Test
    fun successfulBodyRemainsReadableAndIsNotLogged() = runTest {
        AppLog.restore("")
        val body = "private-successful-answer"
        val client = HttpClient(MockEngine { respond(body) }) { install(DiagnosticLogging) }
        try {
            assertEquals(body, client.get("https://example.com/chat/completions").bodyAsText())
            assertTrue(AppLog.text.value.contains("HTTP 200"))
            assertFalse(AppLog.text.value.contains(body))
        } finally {
            client.close()
        }
    }
}

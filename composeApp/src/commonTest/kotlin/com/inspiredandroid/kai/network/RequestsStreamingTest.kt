package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.data.Service
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestsStreamingTest {
    @Test
    fun sendsStreamingAndThinkingAndDeliversIncrementalUpdates() = runTest {
        val events = """
            : keepalive
            data: {"choices":[{"delta":{"reasoning":"Thinking..."}}]}

            data: {"choices":[{"delta":{"content":"Hello "}}]}

            data: {"choices":[{"delta":{"content":"world"},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent()
        val updates = mutableListOf<Pair<String, String>>()
        lateinit var client: HttpClient
        val requests = Requests { configure ->
            HttpClient(
                MockEngine { request ->
                    val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                    assertEquals("true", body["stream"]?.jsonPrimitive?.content)
                    assertEquals("true", body["reasoning"]?.jsonObject?.get("enabled")?.jsonPrimitive?.content)
                    assertFalse(body.containsKey("thinking"))
                    respond(events, headers = headersOf("Content-Type", "text/event-stream"))
                },
            ) { configure(this) }.also { client = it }
        }
        try {
            val result = requests.openAICompatibleChatStream(
                Service.OpenAICompatible,
                ServiceCredentials("test-key", "openrouter/auto", "https://g4f.space/v1"),
                emptyList(),
                onUpdate = { text, thoughts -> updates += text to thoughts },
                thinkingEnabled = true,
            ).getOrThrow()
            assertEquals(listOf("", "Hello ", "Hello world"), updates.map { it.first })
            assertEquals("Thinking...", result.choices.single().message!!.effectiveReasoning)
            assertEquals("Hello world", result.choices.single().message!!.content)
        } finally {
            client.close()
        }
    }

    @Test
    fun handlesServersThatReturnJsonInsteadOfStreaming() = runTest {
        lateinit var client: HttpClient
        val requests = Requests { configure ->
            HttpClient(
                MockEngine {
                    respond("""{"choices":[{"message":{"role":"assistant","content":"whole answer"}}]}""", headers = headersOf("Content-Type", "application/json"))
                },
            ) { configure(this) }.also { client = it }
        }
        try {
            val response = requests.openAICompatibleChatStream(Service.OpenAICompatible, ServiceCredentials("key", "model", "https://example.com/v1"), emptyList()).getOrThrow()
            assertEquals("whole answer", response.choices.single().message!!.content)
        } finally {
            client.close()
        }
    }

    @Test
    fun identifiesOpenRouterFromOpaqueG4fModelMetadata() = runTest {
        lateinit var client: HttpClient
        var modelLookups = 0
        var completions = 0
        val requests = Requests { configure ->
            HttpClient(
                MockEngine { request ->
                    if (request.url.encodedPath.endsWith("/models")) {
                        modelLookups++
                        respond("""{"data":[{"id":"srv_test:auto","owned_by":"OpenRouter","label":"OpenRouter Auto"}]}""", headers = headersOf("Content-Type", "application/json"))
                    } else {
                        completions++
                        val body = Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject
                        assertEquals((completions == 1).toString(), body["reasoning"]?.jsonObject?.get("enabled")?.jsonPrimitive?.content)
                        respond("data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n", headers = headersOf("Content-Type", "text/event-stream"))
                    }
                },
            ) { configure(this) }.also { client = it }
        }
        try {
            val creds = ServiceCredentials("key", "srv_test:auto", "https://g4f.space/v1")
            // The metadata deadline uses wall time while MockEngine runs on its own dispatcher.
            withContext(Dispatchers.Default) {
                requests.openAICompatibleChatStream(Service.OpenAICompatible, creds, emptyList(), thinkingEnabled = true).getOrThrow()
                requests.openAICompatibleChatStream(Service.OpenAICompatible, creds, emptyList(), thinkingEnabled = false).getOrThrow()
            }
            assertEquals(1, modelLookups)
            assertEquals(2, completions)
        } finally {
            client.close()
        }
    }

    @Test
    fun propagatesStopCancellationFromUpdateCallback() = runTest {
        lateinit var client: HttpClient
        val requests = Requests { configure ->
            HttpClient(
                MockEngine {
                    respond("data: {\"choices\":[{\"delta\":{\"content\":\"hello\"}}]}\n\ndata: [DONE]\n\n", headers = headersOf("Content-Type", "text/event-stream"))
                },
            ) { configure(this) }.also { client = it }
        }
        try {
            assertFailsWith<CancellationException> {
                requests.openAICompatibleChatStream(Service.OpenAICompatible, ServiceCredentials("key", "openrouter/auto", "https://g4f.space/v1"), emptyList(), onUpdate = { _, _ -> throw CancellationException("Stopped") })
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun preservesHttpErrorsForExistingRetryAndFallbackHandling() = runTest {
        lateinit var client: HttpClient
        val requests = Requests { configure ->
            HttpClient(MockEngine { respond("""{"error":{"message":"rate limited"}}""", HttpStatusCode.TooManyRequests, headersOf("Content-Type", "application/json")) }) { configure(this) }.also { client = it }
        }
        try {
            val result = requests.openAICompatibleChatStream(Service.OpenAICompatible, ServiceCredentials("key", "openrouter/auto", "https://g4f.space/v1"), emptyList())
            assertTrue(result.exceptionOrNull() is OpenAICompatibleRateLimitExceededException)
        } finally {
            client.close()
        }
    }
}

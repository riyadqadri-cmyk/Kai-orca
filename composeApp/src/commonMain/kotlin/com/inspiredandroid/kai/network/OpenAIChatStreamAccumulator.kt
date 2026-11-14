package com.inspiredandroid.kai.network

import com.inspiredandroid.kai.network.dtos.openaicompatible.OpenAICompatibleChatResponseDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Assemble complete messages and tool calls before handing them to the agent loop. */
internal class OpenAIChatStreamAccumulator {
    private val answer = StringBuilder()
    private val thoughts = StringBuilder()
    private data class PendingCall(var id: String = "", var name: String = "", val arguments: StringBuilder = StringBuilder())
    private val calls = mutableMapOf<Int, PendingCall>()
    private var finishedChoice = false
    var done: Boolean = false
        private set
    val content: String get() = answer.toString()
    val reasoning: String get() = thoughts.toString()

    /** Accept one SSE data payload. Returns true when visible text changed. */
    fun accept(payload: String): Boolean {
        if (payload.isBlank()) return false
        if (payload.trim() == "[DONE]") {
            done = true
            return false
        }
        val event = Json.parseToJsonElement(payload).jsonObject
        event["error"]?.takeUnless { it is JsonNull }?.let {
            throw OpenAICompatibleGenericException("Streaming error: $it")
        }
        val choice = (event["choices"] as? JsonArray)?.map { it.jsonObject }
            ?.firstOrNull { ((it["index"] as? JsonPrimitive)?.intOrNull ?: 0) == 0 } ?: return false
        if (choice["finish_reason"] is JsonPrimitive && choice["finish_reason"] !is JsonNull) finishedChoice = true
        val delta = choice["delta"] as? JsonObject ?: return false
        val text = textValue(delta["content"])
        val reason = textValue(delta["reasoning_content"]).ifEmpty { textValue(delta["reasoning"]) }
        answer.append(text)
        thoughts.append(reason)
        (delta["tool_calls"] as? JsonArray)?.forEach { element ->
            val part = element.jsonObject
            val index = (part["index"] as? JsonPrimitive)?.intOrNull ?: 0
            val call = calls.getOrPut(index) { PendingCall() }
            (part["id"] as? JsonPrimitive)?.contentOrNull?.let { call.id = it }
            val function = part["function"] as? JsonObject
            (function?.get("name") as? JsonPrimitive)?.contentOrNull?.let { call.name += it }
            (function?.get("arguments") as? JsonPrimitive)?.contentOrNull?.let { call.arguments.append(it) }
        }
        return text.isNotEmpty() || reason.isNotEmpty()
    }

    fun response(): OpenAICompatibleChatResponseDto {
        // Never execute incomplete tool arguments or save a connection-truncated answer.
        if (!done && !finishedChoice) throw OpenAICompatibleConnectionException()
        if (answer.isBlank() && thoughts.isBlank() && calls.isEmpty()) throw OpenAICompatibleEmptyResponseException()
        val toolCalls = calls.entries.sortedBy { it.key }.map { (_, call) ->
            if (call.id.isBlank() || call.name.isBlank()) throw OpenAICompatibleGenericException("Incomplete streamed tool call")
            OpenAICompatibleChatResponseDto.ToolCall(
                id = call.id,
                function = OpenAICompatibleChatResponseDto.FunctionCall(call.name, call.arguments.toString()),
            )
        }
        return OpenAICompatibleChatResponseDto(
            listOf(
                OpenAICompatibleChatResponseDto.Choice(
                    OpenAICompatibleChatResponseDto.Choice.Message(
                        role = "assistant",
                        content = content,
                        reasoningContent = reasoning.takeIf { it.isNotBlank() },
                        toolCalls = toolCalls.takeIf { it.isNotEmpty() },
                    ),
                ),
            ),
        )
    }

    private fun textValue(value: kotlinx.serialization.json.JsonElement?): String = when (value) {
        is JsonPrimitive -> value.contentOrNull.orEmpty()
        is JsonArray -> value.joinToString("") { textValue((it as? JsonObject)?.get("text")) }
        else -> ""
    }
}

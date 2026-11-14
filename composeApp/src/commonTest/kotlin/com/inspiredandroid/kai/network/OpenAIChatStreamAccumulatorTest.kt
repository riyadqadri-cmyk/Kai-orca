package com.inspiredandroid.kai.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenAIChatStreamAccumulatorTest {
    @Test
    fun streamsAnswerAndOpenRouterReasoningSeparately() {
        val stream = OpenAIChatStreamAccumulator()
        assertTrue(stream.accept("""{"choices":[{"delta":{"reasoning":"Let me "}}]}"""))
        stream.accept("""{"choices":[{"delta":{"reasoning":"think."}}]}""")
        assertEquals("Let me think.", stream.reasoning)
        assertEquals("", stream.content)
        stream.accept("""{"choices":[{"delta":{"content":"The answer "}}]}""")
        stream.accept("""{"choices":[{"delta":{"content":"is 42."},"finish_reason":"stop"}]}""")
        stream.accept("[DONE]")
        val message = stream.response().choices.single().message!!
        assertEquals("The answer is 42.", message.content)
        assertEquals("Let me think.", message.reasoningContent)
    }

    @Test
    fun preservesDeepSeekReasoningAndUnicode() {
        val stream = OpenAIChatStreamAccumulator()
        stream.accept("""{"choices":[{"delta":{"reasoning_content":"تفكير"}}]}""")
        stream.accept("""{"choices":[{"delta":{"content":"مرحبا 👋"}}]}""")
        stream.accept("[DONE]")
        assertEquals("تفكير", stream.response().choices.single().message!!.effectiveReasoning)
        assertEquals("مرحبا 👋", stream.content)
    }

    @Test
    fun joinsInterleavedToolArgumentsAndPreservesCallOrder() {
        val stream = OpenAIChatStreamAccumulator()
        stream.accept("""{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"second","function":{"name":"web_search","arguments":"{\"query\":\""}},{"index":0,"id":"first","function":{"name":"get_local_time","arguments":"{"}}]}}]}""")
        stream.accept("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"}"}},{"index":1,"function":{"arguments":"hello\"}"}}]},"finish_reason":"tool_calls"}]}""")
        val calls = stream.response().choices.single().message!!.toolCalls!!
        assertEquals(listOf("first", "second"), calls.map { it.id })
        assertEquals("{}", calls[0].function.arguments)
        assertEquals("{\"query\":\"hello\"}", calls[1].function.arguments)
    }

    @Test
    fun rejectsDisconnectedPartialAnswersAndToolCalls() {
        val stream = OpenAIChatStreamAccumulator()
        stream.accept("""{"choices":[{"delta":{"content":"partial","tool_calls":[{"index":0,"id":"call","function":{"name":"execute_shell_command","arguments":"{\"command\":"}}]}}]}""")
        assertFailsWith<OpenAICompatibleConnectionException> { stream.response() }
    }

    @Test
    fun handlesUsageChunksAndFinishWithoutDone() {
        val stream = OpenAIChatStreamAccumulator()
        assertFalse(stream.accept("""{"choices":[],"usage":{"total_tokens":10}}"""))
        stream.accept("""{"choices":[{"index":0,"delta":{"content":"ok"},"finish_reason":"stop"}]}""")
        assertEquals("ok", stream.response().choices.single().message!!.content)
    }

    @Test
    fun rejectsSseErrorAndEmptyStream() {
        val stream = OpenAIChatStreamAccumulator()
        assertFailsWith<OpenAICompatibleGenericException> { stream.accept("""{"error":{"message":"upstream failed"}}""") }
        stream.accept("[DONE]")
        assertFailsWith<OpenAICompatibleEmptyResponseException> { stream.response() }
    }

    @Test
    fun ignoresOtherChoicesAndSupportsTextBlocks() {
        val stream = OpenAIChatStreamAccumulator()
        stream.accept("""{"choices":[{"index":1,"delta":{"content":"ignored"}},{"index":0,"delta":{"content":[{"type":"text","text":"hello"},{"type":"text","text":" world"}]}}]}""")
        stream.accept("[DONE]")
        assertEquals("hello world", stream.response().choices.single().message!!.content)
    }

    @Test
    fun rejectsIncompleteToolMetadata() {
        val stream = OpenAIChatStreamAccumulator()
        stream.accept("""{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{}"}}]},"finish_reason":"tool_calls"}]}""")
        assertFailsWith<OpenAICompatibleGenericException> { stream.response() }
    }
}

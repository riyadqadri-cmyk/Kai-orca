package com.inspiredandroid.kai.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenRouterReasoningTest {
    @Test
    fun recognizesOpenRouterAutoThroughG4f() {
        val config = openRouterReasoning(false, "https://g4f.space/v1", "srv_example:openrouter/auto", true)!!
        assertTrue(config.enabled)
        assertFalse(config.exclude)
    }

    @Test
    fun recognizesOpaqueG4fIdsUsingProviderMetadata() {
        assertTrue(openRouterReasoning(false, "https://g4f.space/v1", "srv_example:auto", true, "OpenRouter Auto")!!.enabled)
        assertNull(openRouterReasoning(false, "https://g4f.space/v1", "srv_other:auto", true, "Groq"))
    }

    @Test
    fun offIsAnExplicitReasoningPreference() {
        assertEquals(false, openRouterReasoning(false, "https://g4f.space/v1", "openrouter/auto", false)?.enabled)
    }

    @Test
    fun nativeServiceAndDirectHostUseOpenRouterFormat() {
        assertTrue(openRouterReasoning(true, "", "anything", true)!!.enabled)
        assertTrue(openRouterReasoning(false, "https://openrouter.ai/api/v1", "other/model", true)!!.enabled)
    }

    @Test
    fun doesNotSendOpenRouterParametersToUnrelatedProviders() {
        assertNull(openRouterReasoning(false, "https://api.deepseek.com", "deepseek-flash", true))
        assertNull(openRouterReasoning(false, "https://other.example/v1", "auto", true))
        assertNull(openRouterReasoning(false, "https://g4f.space/v1", "auto", true))
        assertNull(openRouterReasoning(false, "https://notopenrouter.ai/v1", "model", true))
    }
}

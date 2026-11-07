package com.inspiredandroid.kai.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Locks the DeepSeek vision gate. DeepSeek mixes vision and text-only models (V4.1-Flash accepts
 * images; V4-Pro-0813 and every pre-V4 generation do not), so a blanket family rule is wrong in
 * both directions. Sending image content-parts to a text-only id is a hard 400 that persists in
 * history and breaks every later turn in the conversation.
 */
class DeepSeekVisionGateTest {

    @Test
    fun `current vision models accept images`() {
        assertTrue(modelSupportsImages("deepseek-flash"))
        assertTrue(modelSupportsImages("deepseek-v4.1-flash"))
        assertTrue(modelSupportsImages("deepseek-v4-flash-vision-exp"))
        assertTrue(modelSupportsImages("deepseek-vl2"))
    }

    @Test
    fun `vision models are recognised through an aggregator prefix`() {
        assertTrue(modelSupportsImages("openrouter/deepseek-flash"))
        assertTrue(modelSupportsImages("OpenRouter/DeepSeek-V4.1-Flash"))
    }

    @Test
    fun `text-only deepseek ids reject images`() {
        assertFalse(modelSupportsImages("deepseek-v4-pro-0813"))
        assertFalse(modelSupportsImages("deepseek-chat"))
        assertFalse(modelSupportsImages("deepseek-reasoner"))
        assertFalse(modelSupportsImages("deepseek-v3.2"))
        assertFalse(modelSupportsImages("deepseek-r1"))
    }

    @Test
    fun `unknown deepseek ids default to text-only`() {
        // Allowlist semantics: unverified ids stay off so we can't 400 a whole conversation.
        assertFalse(modelSupportsImages("deepseek-something-unreleased"))
    }

    @Test
    fun `non-deepseek models keep the permissive default`() {
        assertTrue(modelSupportsImages("gpt-5.6"))
        assertTrue(modelSupportsImages("claude-opus-4.7"))
        assertFalse(modelSupportsImages("glm-4.6"))
    }
}

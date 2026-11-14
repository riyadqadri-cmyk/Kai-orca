package com.inspiredandroid.kai.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppLogTest {
    @BeforeTest
    fun reset() = AppLog.restore("")

    @Test
    fun preservesProviderFailureDetailsAndRedactsCredentials() {
        AppLog.registerSecret("configured-test-secret")
        AppLog.error("Provider", "Free/fast HTTP 429; retry-after=30; upstream=Groq; configured-test-secret", IllegalStateException("quota exhausted"))
        val log = AppLog.text.value
        assertTrue(log.contains("Free/fast HTTP 429"))
        assertTrue(log.contains("retry-after=30"))
        assertTrue(log.contains("upstream=Groq"))
        assertTrue(log.contains("IllegalStateException: quota exhausted"))
        assertFalse(log.contains("configured-test-secret"))
    }

    @Test
    fun redactsHeadersJsonAndUrlCredentials() {
        val log = AppLog.redact("""Bearer bearer-secret {"api_key":"json-secret","password":"pass-secret"} https://user:pass@example.com/api?key=query-secret&token=token-secret""")
        listOf("bearer-secret", "json-secret", "pass-secret", "user:pass", "query-secret", "token-secret").forEach {
            assertFalse(log.contains(it), "Leaked $it")
        }
        assertTrue(log.contains("example.com/api"))
    }

    @Test
    fun boundsTheBufferAndKeepsNewestEntries() {
        repeat(100) { AppLog.info("Test", "x".repeat(4_000)) }
        AppLog.info("Test", "newest-entry")
        assertTrue(AppLog.text.value.length <= AppLog.MAX_CHARS)
        assertTrue(AppLog.text.value.endsWith("newest-entry\n"))
    }

    @Test
    fun concurrentWritersDoNotLoseEntries() = runTest {
        coroutineScope {
            repeat(100) { id -> launch(Dispatchers.Default) { AppLog.info("Test", "entry-$id") } }
        }
        val entries = AppLog.text.value.lineSequence().filter { it.isNotBlank() }.toList()
        assertEquals(100, entries.size)
        assertEquals(100, entries.toSet().size)
    }

    @Test
    fun restoreKeepsPreviousSessionAndBoundsOversizedFiles() {
        AppLog.restore("previous-session\n")
        AppLog.info("App", "new-session")
        assertTrue(AppLog.text.value.startsWith("previous-session\n"))
        assertTrue(AppLog.text.value.contains("new-session"))
        AppLog.restore("old\n".repeat(AppLog.MAX_CHARS))
        assertTrue(AppLog.text.value.length <= AppLog.MAX_CHARS)
    }

    @Test
    fun clearRemovesOldEntriesAndLoggingContinuesWithRedaction() {
        AppLog.info("Test", "old-entry")
        AppLog.registerSecret("clear-test-secret")
        AppLog.clear()
        assertEquals("", AppLog.text.value)
        AppLog.info("Test", "new-entry clear-test-secret")
        assertFalse(AppLog.text.value.contains("old-entry"))
        assertFalse(AppLog.text.value.contains("clear-test-secret"))
        assertTrue(AppLog.text.value.contains("new-entry"))
    }
}

package by.sparkyanvil.fastcowscreenshot.platform.windows

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClipboardRetryTest {
    @Test
    fun `stops on first success`() {
        val sleeps = mutableListOf<Long>()
        var calls = 0
        val result = ClipboardRetry.attempt(sleep = { sleeps += it }) { n ->
            calls++
            if (n == 3) "ok" else null
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
        assertEquals(listOf(100L, 100L), sleeps)
    }

    @Test
    fun `gives up after max attempts`() {
        val sleeps = mutableListOf<Long>()
        var calls = 0
        val result = ClipboardRetry.attempt<String>(sleep = { sleeps += it }) {
            calls++
            null
        }
        assertNull(result)
        assertEquals(8, calls)
        assertEquals(List(7) { 100L }, sleeps)
        assertEquals(700L, sleeps.sum())
    }
}
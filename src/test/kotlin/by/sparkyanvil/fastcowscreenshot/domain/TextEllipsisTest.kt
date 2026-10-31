package by.sparkyanvil.fastcowscreenshot.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TextEllipsisTest {
    @Test
    fun `short text unchanged`() {
        assertEquals("abc", TextEllipsis.middle("abc", 5))
    }

    @Test
    fun `odd budget`() {
        assertEquals("ab…ij", TextEllipsis.middle("abcdefghij", 5))
    }

    @Test
    fun `even budget`() {
        assertEquals("ab…hij", TextEllipsis.middle("abcdefghij", 6))
    }

    @Test
    fun `rejects too small budget`() {
        assertFailsWith<IllegalArgumentException> { TextEllipsis.middle("abcdefghij", 4) }
    }
}
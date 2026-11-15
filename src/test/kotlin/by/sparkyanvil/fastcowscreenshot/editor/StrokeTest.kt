package by.sparkyanvil.fastcowscreenshot.editor

import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class StrokeTest {
    @Test
    fun `stroke requires points`() {
        assertFailsWith<IllegalArgumentException> { Stroke(0xFF0000, 4, emptyList()) }
    }

    @Test
    fun `stroke requires positive width`() {
        assertFailsWith<IllegalArgumentException> { Stroke(0xFF0000, 0, listOf(StrokePoint(1f, 1f))) }
        Stroke(0xFF0000, 2, listOf(StrokePoint(1f, 1f)))
    }
}
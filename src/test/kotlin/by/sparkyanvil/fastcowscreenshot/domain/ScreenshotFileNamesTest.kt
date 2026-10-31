package by.sparkyanvil.fastcowscreenshot.domain

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScreenshotFileNamesTest {
    private val t = LocalDateTime.of(2026, 10, 18, 20, 0, 48)

    @Test
    fun `base name uses local timestamp`() {
        assertEquals("Screenshot_2026-10-18_20-00-48", ScreenshotFileNames.baseName(t))
    }

    @Test
    fun `index 0 has no suffix`() {
        assertEquals("Screenshot_2026-10-18_20-00-48.png", ScreenshotFileNames.candidate(t, 0))
    }

    @Test
    fun `index 1 adds suffix`() {
        assertEquals("Screenshot_2026-10-18_20-00-48_1.png", ScreenshotFileNames.candidate(t, 1))
    }

    @Test
    fun `pads single digit time parts`() {
        assertEquals(
            "Screenshot_2026-01-02_03-04-05",
            ScreenshotFileNames.baseName(LocalDateTime.of(2026, 1, 2, 3, 4, 5)),
        )
    }

    @Test
    fun `rejects index out of range`() {
        assertFailsWith<IllegalArgumentException> { ScreenshotFileNames.candidate(t, 10000) }
    }
}
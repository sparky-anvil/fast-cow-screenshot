package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class StructureSizeTest {
    @Test
    fun `cursorinfo is 24 bytes`() {
        val info = CURSORINFO()
        assertEquals(24, info.size())
        assertEquals(24, info.cbSize)
    }

    @Test
    fun `openfilename is 152 bytes`() {
        assertEquals(152, OPENFILENAME().size())
    }

    @Test
    fun `browseinfo is 64 bytes`() {
        assertEquals(64, BROWSEINFO().size())
    }
}
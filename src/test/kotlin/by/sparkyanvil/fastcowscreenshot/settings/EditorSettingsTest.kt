package by.sparkyanvil.fastcowscreenshot.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class EditorSettingsTest {
    @Test
    fun `defaults are red and 4 px`() {
        val s = EditorSettings()
        assertEquals("#FF0000", s.penColor)
        assertEquals(0xFF0000, s.penRgb())
        assertEquals(4, s.effectivePenWidth())
    }

    @Test
    fun `palette color is parsed`() {
        assertEquals(0x2979FF, EditorSettings(penColor = "#2979FF").penRgb())
    }

    @Test
    fun `lowercase palette color is parsed`() {
        assertEquals(0xFFD800, EditorSettings(penColor = "#ffd800").penRgb())
    }

    @Test
    fun `color outside palette falls back to red`() {
        assertEquals(0xFF0000, EditorSettings(penColor = "#123456").penRgb())
        assertEquals(0xFF0000, EditorSettings(penColor = "red").penRgb())
    }

    @Test
    fun `unsupported width falls back to 4`() {
        assertEquals(4, EditorSettings(penWidth = 5).effectivePenWidth())
        assertEquals(4, EditorSettings(penWidth = 0).effectivePenWidth())
        assertEquals(16, EditorSettings(penWidth = 16).effectivePenWidth())
    }

    @Test
    fun `palette lists match`() {
        assertEquals(EditorSettings.PALETTE_RGB, EditorSettings.PALETTE.map { MultiRegionSettings.parseHexColor(it) })
        assertEquals(6, EditorSettings.PALETTE.size)
    }
}

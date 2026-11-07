package by.sparkyanvil.fastcowscreenshot.settings

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MultiRegionSettingsTest {
    @TempDir
    lateinit var tempDir: Path

    private fun k(ctrl: Boolean, code: Int, numpad: Boolean = false) = OverlayKey(ctrl, false, false, code, numpad)

    @Test
    fun `defaults match requirements`() {
        val s = MultiRegionSettings()
        assertEquals(10, s.maxRegions)
        assertEquals("#808080", s.fillColor)
        val map = s.keymap()
        assertEquals(listOf(k(false, 10), k(false, 10, true)), map.keys(OverlayAction.CONFIRM))
        assertEquals(listOf(k(false, 8), k(true, 0x5A)), map.keys(OverlayAction.REMOVE_LAST))
        assertEquals(listOf(k(false, 27)), map.keys(OverlayAction.CANCEL))
    }

    @Test
    fun `max regions is clamped`() {
        assertEquals(2, MultiRegionSettings(maxRegions = 1).effectiveMaxRegions())
        assertEquals(50, MultiRegionSettings(maxRegions = 99).effectiveMaxRegions())
        assertEquals(25, MultiRegionSettings(maxRegions = 25).effectiveMaxRegions())
    }

    @Test
    fun `fill rgb falls back to default`() {
        assertEquals(0x808080, MultiRegionSettings(fillColor = "red").fillRgb())
        assertEquals(0xFFFFFF, MultiRegionSettings(fillColor = "#FFFFFF").fillRgb())
    }

    @Test
    fun `parse hex color`() {
        assertEquals(0xFF00AA, MultiRegionSettings.parseHexColor("#ff00Aa"))
        assertNull(MultiRegionSettings.parseHexColor("#GGG"))
        assertNull(MultiRegionSettings.parseHexColor("808080"))
        assertNull(MultiRegionSettings.parseHexColor("#8080800"))
        assertNull(MultiRegionSettings.parseHexColor(" #808080"))
    }

    @Test
    fun `format hex color`() {
        assertEquals("#FF00AA", MultiRegionSettings.formatHexColor(0xFF00AA))
        assertEquals("#000000", MultiRegionSettings.formatHexColor(0))
    }

    @Test
    fun `key and withKey cover all slots`() {
        val base = MultiRegionSettings()
        val all = OverlayAction.entries.flatMap { a -> KeySlot.entries.map { s -> a to s } }
        assertEquals(6, all.size)
        for ((a, s) in all) {
            val changed = base.withKey(a, s, "F5")
            assertEquals("F5", changed.key(a, s))
            for ((a2, s2) in all) {
                if (a2 != a || s2 != s) assertEquals(base.key(a2, s2), changed.key(a2, s2))
            }
        }
    }

    @Test
    fun `invalid primary falls back to default`() {
        val s = MultiRegionSettings(confirmKey = "???")
        assertEquals(k(false, 10), s.resolvedKey(OverlayAction.CONFIRM, KeySlot.PRIMARY))
    }

    @Test
    fun `invalid or empty secondary is unassigned`() {
        assertNull(MultiRegionSettings(cancelKeyAlt = "???").resolvedKey(OverlayAction.CANCEL, KeySlot.SECONDARY))
        assertNull(MultiRegionSettings().resolvedKey(OverlayAction.CANCEL, KeySlot.SECONDARY))
        assertEquals(listOf(k(false, 27)), MultiRegionSettings().keymap().keys(OverlayAction.CANCEL))
    }

    @Test
    fun `default key per slot`() {
        assertEquals("Ctrl+Z", MultiRegionSettings.defaultKey(OverlayAction.REMOVE_LAST, KeySlot.SECONDARY))
        assertEquals("", MultiRegionSettings.defaultKey(OverlayAction.CANCEL, KeySlot.SECONDARY))
    }

    @Test
    fun `settings round trip through repository`() {
        val file = tempDir.resolve("cfg").resolve("settings.json")
        val repo = SettingsRepository(file, DefaultDirectoryProvider { tempDir })
        val saved = AppSettings(
            defaultDirectory = "C:\\s",
            multiRegion = MultiRegionSettings(maxRegions = 7, fillColor = "#FFFFFF", cancelKeyAlt = "Delete"),
        )
        repo.save(saved)
        assertEquals(saved, repo.load())
    }

    @Test
    fun `missing multiRegion object uses defaults`() {
        val file = tempDir.resolve("cfg").resolve("settings.json")
        Files.createDirectories(file.parent)
        Files.writeString(file, "{\"defaultDirectory\":\"C:\\\\s\"}")
        val repo = SettingsRepository(file, DefaultDirectoryProvider { tempDir })
        assertEquals(MultiRegionSettings(), repo.load().multiRegion)
    }
}
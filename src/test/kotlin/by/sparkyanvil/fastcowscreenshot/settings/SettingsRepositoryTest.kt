package by.sparkyanvil.fastcowscreenshot.settings

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    @TempDir
    lateinit var tempDir: Path

    private val provider = DefaultDirectoryProvider { Path.of("C:\\Users\\Александр\\Pictures\\Screenshots") }
    private val file: Path get() = tempDir.resolve("cfg").resolve("settings.json")

    @Test
    fun `first load creates file with defaults`() {
        val loaded = SettingsRepository(file, provider).load()
        assertEquals("C:\\Users\\Александр\\Pictures\\Screenshots", loaded.defaultDirectory)
        assertFalse(loaded.includeCursor)
        assertTrue(loaded.autoSaveEnabled)
        assertEquals(HotkeySettings(), loaded.hotkeys)
        assertTrue(Files.exists(file))
    }

    @Test
    fun `save and load round trip with cyrillic`() {
        val repo = SettingsRepository(file, provider)
        val saved = repo.defaultSettings().copy(
            defaultDirectory = "D:\\Скриншоты",
            lastSaveAsDirectory = "D:\\Projects\\Bug-42",
            includeCursor = true,
        )
        repo.save(saved)
        assertEquals(saved, repo.load())
    }

    @Test
    fun `corrupted file gives defaults and is kept`() {
        Files.createDirectories(file.parent)
        Files.writeString(file, "{not json", UTF_8)
        val repo = SettingsRepository(file, provider)
        assertEquals(repo.defaultSettings(), repo.load())
        assertEquals("{not json", Files.readString(file, UTF_8))
    }

    @Test
    fun `unknown keys ignored`() {
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            """
            {
              "version": 1,
              "defaultDirectory": "D:\\Shots",
              "lastSaveAsDirectory": "E:\\Out",
              "includeCursor": true,
              "autoSaveEnabled": false,
              "hotkeys": {"monitorUnderCursor": "Ctrl+Alt+1", "allMonitors": "Ctrl+Shift+2", "region": "Ctrl+Shift+3"},
              "extra": 5
            }
            """.trimIndent(),
            UTF_8,
        )
        val loaded = SettingsRepository(file, provider).load()
        assertEquals("D:\\Shots", loaded.defaultDirectory)
        assertEquals("E:\\Out", loaded.lastSaveAsDirectory)
        assertTrue(loaded.includeCursor)
        assertFalse(loaded.autoSaveEnabled)
        assertEquals("Ctrl+Alt+1", loaded.hotkeys.monitorUnderCursor)
    }

    @Test
    fun `missing directory on disk is not reset`() {
        val repo = SettingsRepository(file, provider)
        repo.save(repo.defaultSettings().copy(defaultDirectory = "Z:\\offline"))
        assertEquals("Z:\\offline", repo.load().defaultDirectory)
    }

    @Test
    fun `invalid hotkey text falls back to default`() {
        val settings = AppSettings(defaultDirectory = "x", hotkeys = HotkeySettings(region = "bad"))
        assertEquals(HotkeyBinding.defaultFor(HotkeyAction.REGION), settings.hotkeyBinding(HotkeyAction.REGION))
    }
}

package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.HotkeyService
import by.sparkyanvil.fastcowscreenshot.settings.DefaultDirectoryProvider
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import by.sparkyanvil.fastcowscreenshot.settings.SettingsRepository
import by.sparkyanvil.fastcowscreenshot.settings.SettingsStore
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsControllerTest {
    @TempDir
    lateinit var tempDir: Path

    private val direct = Executor { it.run() }
    private val hotkeys = mockk<HotkeyService>()
    private lateinit var defaults: DefaultDirectoryProvider
    private lateinit var store: SettingsStore
    private var folder: Path? = null
    private var writable = true

    private val ctrlAltP = HotkeyBinding(true, true, false, 'P'.code)

    @BeforeEach
    fun setUp() {
        every { hotkeys.register(any(), any()) } returns true
        every { hotkeys.unregisterAll() } just runs
        defaults = DefaultDirectoryProvider { tempDir.resolve("Screenshots") }
        store = SettingsStore(SettingsRepository(tempDir.resolve("settings.json"), defaults))
    }

    private fun controller() = SettingsController(
        store, hotkeys, defaults, { folder }, { writable }, {}, direct, direct,
    )

    @Test
    fun `registerAll reports failed actions`() {
        every { hotkeys.register(HotkeyAction.ALL_MONITORS, any()) } returns false
        assertEquals(listOf(HotkeyAction.ALL_MONITORS), controller().registerAll())
    }

    @Test
    fun `apply hotkey saves and registers`() {
        assertNull(controller().applyHotkey(HotkeyAction.REGION, ctrlAltP))
        assertEquals("Ctrl+Alt+P", store.current.hotkeys.region)
        verify { hotkeys.register(HotkeyAction.REGION, ctrlAltP) }
    }

    @Test
    fun `apply hotkey taken reverts`() {
        every { hotkeys.register(HotkeyAction.REGION, ctrlAltP) } returns false
        assertEquals(
            "Сочетание Ctrl+Alt+P занято другим приложением",
            controller().applyHotkey(HotkeyAction.REGION, ctrlAltP),
        )
        assertEquals("Ctrl+Shift+3", store.current.hotkeys.region)
    }

    @Test
    fun `duplicate with other action rejected`() {
        val dup = HotkeyBinding(true, false, true, '2'.code)
        assertEquals(
            "Сочетание Ctrl+Shift+2 уже используется для режима «Все мониторы»",
            controller().applyHotkey(HotkeyAction.REGION, dup),
        )
        assertEquals("Ctrl+Shift+3", store.current.hotkeys.region)
    }

    @Test
    fun `unwritable folder keeps old value`() {
        val before = store.current.defaultDirectory
        folder = Path.of("C:\\Windows\\System32")
        writable = false
        var err: String? = null
        controller().browseDirectory { err = it }
        assertEquals("В папку «C:\\Windows\\System32» нельзя записывать. Выберите другую папку.", err)
        assertEquals(before, store.current.defaultDirectory)
    }

    @Test
    fun `writable folder is saved`() {
        val dir = tempDir.resolve("Скриншоты")
        folder = dir
        var err: String? = "unset"
        controller().browseDirectory { err = it }
        assertNull(err)
        assertEquals(dir.toString(), store.current.defaultDirectory)
    }

    @Test
    fun `cancel folder changes nothing`() {
        val before = store.current.defaultDirectory
        folder = null
        var err: String? = "unset"
        controller().browseDirectory { err = it }
        assertNull(err)
        assertEquals(before, store.current.defaultDirectory)
    }

    @Test
    fun `reset restores initial`() {
        store.update { it.copy(defaultDirectory = "D:\\x") }
        assertNull(controller().resetDirectory())
        assertEquals(tempDir.resolve("Screenshots").toString(), store.current.defaultDirectory)
    }

    @Test
    fun `toggles are persisted`() {
        val c = controller()
        assertNull(c.setIncludeCursor(true))
        assertNull(c.setAutoSave(false))
        val loaded = SettingsRepository(tempDir.resolve("settings.json"), defaults).load()
        assertTrue(loaded.includeCursor)
        assertEquals(false, loaded.autoSaveEnabled)
    }

    @Test
    fun `recording unregisters all`() {
        controller().beginHotkeyRecording()
        verify { hotkeys.unregisterAll() }
    }
}

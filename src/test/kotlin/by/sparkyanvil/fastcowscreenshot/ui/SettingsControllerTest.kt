package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.HotkeyService
import by.sparkyanvil.fastcowscreenshot.settings.DefaultDirectoryProvider
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import by.sparkyanvil.fastcowscreenshot.settings.KeySlot
import by.sparkyanvil.fastcowscreenshot.settings.MultiRegionSettings
import by.sparkyanvil.fastcowscreenshot.settings.OverlayAction
import by.sparkyanvil.fastcowscreenshot.settings.OverlayKey
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

    private val enter = OverlayKey(false, false, false, 10)

    @Test
    fun `overlay key conflict is rejected`() {
        assertEquals(
            "Сочетание Enter уже назначено действию «Собрать снимок»",
            controller().applyOverlayKey(OverlayAction.CANCEL, KeySlot.PRIMARY, enter),
        )
        assertEquals("Esc", store.current.multiRegion.cancelKey)
    }

    @Test
    fun `overlay key conflict with secondary slot`() {
        assertEquals(
            "Сочетание Ctrl+Z уже назначено действию «Удалить последнюю область»",
            controller().applyOverlayKey(OverlayAction.CANCEL, KeySlot.SECONDARY, OverlayKey(true, false, false, 0x5A)),
        )
    }

    @Test
    fun `overlay key is saved`() {
        assertNull(controller().applyOverlayKey(OverlayAction.CANCEL, KeySlot.SECONDARY, OverlayKey(false, false, false, 0x51)))
        assertEquals("Q", store.current.multiRegion.cancelKeyAlt)
        val loaded = SettingsRepository(tempDir.resolve("settings.json"), defaults).load()
        assertEquals("Q", loaded.multiRegion.cancelKeyAlt)
    }

    @Test
    fun `same key in same slot is not a conflict`() {
        assertNull(controller().applyOverlayKey(OverlayAction.CONFIRM, KeySlot.PRIMARY, enter))
    }

    @Test
    fun `swapping keys is possible via free key`() {
        val c = controller()
        assertNull(c.applyOverlayKey(OverlayAction.CANCEL, KeySlot.PRIMARY, OverlayKey(false, false, false, 0x74)))
        assertNull(c.applyOverlayKey(OverlayAction.CONFIRM, KeySlot.PRIMARY, OverlayKey(false, false, false, 27)))
        assertNull(c.applyOverlayKey(OverlayAction.CANCEL, KeySlot.PRIMARY, enter))
        assertEquals("Esc", store.current.multiRegion.confirmKey)
        assertEquals("Enter", store.current.multiRegion.cancelKey)
    }

    @Test
    fun `secondary can be cleared`() {
        assertNull(controller().applyOverlayKey(OverlayAction.REMOVE_LAST, KeySlot.SECONDARY, null))
        assertEquals("", store.current.multiRegion.removeLastKeyAlt)
    }

    @Test
    fun `primary cannot be cleared`() {
        assertEquals(
            "Основное сочетание обязательно",
            controller().applyOverlayKey(OverlayAction.CONFIRM, KeySlot.PRIMARY, null),
        )
        assertEquals("Enter", store.current.multiRegion.confirmKey)
    }

    @Test
    fun `max regions validation`() {
        val c = controller()
        for (v in listOf("0", "51", "abc", "")) {
            assertEquals("Максимум областей — целое число от 2 до 50", c.setMaxRegions(v), v)
        }
        assertEquals(10, store.current.multiRegion.maxRegions)
    }

    @Test
    fun `max regions saved`() {
        assertNull(controller().setMaxRegions(" 25 "))
        assertEquals(25, store.current.multiRegion.maxRegions)
    }

    @Test
    fun `fill color validation`() {
        val c = controller()
        assertEquals("Цвет — в формате #RRGGBB, например #808080", c.setFillColor("#GGG"))
        assertEquals("Цвет — в формате #RRGGBB, например #808080", c.setFillColor("808080"))
        assertEquals("#808080", store.current.multiRegion.fillColor)
    }

    @Test
    fun `fill color normalized to upper case`() {
        assertNull(controller().setFillColor("#ff00aa"))
        assertEquals("#FF00AA", store.current.multiRegion.fillColor)
    }

    @Test
    fun `multi region hotkey conflicts with region mode`() {
        assertEquals(
            "Сочетание Ctrl+Shift+3 уже используется для режима «Область»",
            controller().applyHotkey(HotkeyAction.MULTI_REGION, HotkeyBinding(true, false, true, '3'.code)),
        )
    }

    private fun customizeMultiRegion() {
        store.update {
            it.copy(
                multiRegion = MultiRegionSettings(maxRegions = 25, fillColor = "#FFFFFF", cancelKey = "F5"),
                hotkeys = it.hotkeys.with(HotkeyAction.MULTI_REGION, "Ctrl+Alt+M"),
            )
        }
    }

    @Test
    fun `reset restores defaults`() {
        customizeMultiRegion()
        assertNull(controller().resetMultiRegion())
        assertEquals(MultiRegionSettings(), store.current.multiRegion)
        assertEquals("Ctrl+Shift+4", store.current.hotkeyBinding(HotkeyAction.MULTI_REGION).format())
        verify { hotkeys.register(HotkeyAction.MULTI_REGION, HotkeyBinding.defaultFor(HotkeyAction.MULTI_REGION)) }
    }

    @Test
    fun `reset keeps hotkey when default is taken`() {
        every {
            hotkeys.register(HotkeyAction.MULTI_REGION, HotkeyBinding.defaultFor(HotkeyAction.MULTI_REGION))
        } returns false
        customizeMultiRegion()
        assertEquals("Сочетание Ctrl+Shift+4 занято другим приложением", controller().resetMultiRegion())
        assertEquals(MultiRegionSettings(), store.current.multiRegion)
    }

    @Test
    fun `register all includes multi region`() {
        controller().registerAll()
        verify { hotkeys.register(HotkeyAction.MULTI_REGION, any()) }
    }
}
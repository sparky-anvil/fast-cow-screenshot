package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.HotkeyService
import by.sparkyanvil.fastcowscreenshot.settings.DefaultDirectoryProvider
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import by.sparkyanvil.fastcowscreenshot.settings.KeySlot
import by.sparkyanvil.fastcowscreenshot.settings.MultiRegionSettings
import by.sparkyanvil.fastcowscreenshot.settings.OverlayAction
import by.sparkyanvil.fastcowscreenshot.settings.OverlayKey
import by.sparkyanvil.fastcowscreenshot.settings.SettingsStore
import java.nio.file.Path
import java.util.concurrent.Executor

/** Логика окна настроек. Методы — на EDT, кроме registerAll (любой поток). */
internal class SettingsController(
    private val store: SettingsStore,
    private val hotkeys: HotkeyService,
    private val defaults: DefaultDirectoryProvider,
    private val chooseFolder: () -> Path?,
    private val isWritable: (Path) -> Boolean,
    private val openFolder: (Path) -> Unit,
    private val dialogExecutor: Executor,
    private val uiExecutor: Executor,
) {
    /** Регистрирует три клавиши из store.current. Возвращает действия, которые зарегистрировать не удалось. */
    fun registerAll(): List<HotkeyAction> {
        val s = store.current
        return HotkeyAction.entries.filterNot { hotkeys.register(it, s.hotkeyBinding(it)) }
    }

    /** Снимает все глобальные клавиши на время записи нового сочетания. */
    fun beginHotkeyRecording() {
        hotkeys.unregisterAll()
    }

    /** Отмена записи: клавиши регистрируются заново. */
    fun cancelHotkeyRecording() {
        registerAll()
    }

    /** Применяет новое сочетание. Возвращает текст ошибки или null при успехе. */
    fun applyHotkey(action: HotkeyAction, binding: HotkeyBinding): String? {
        val s = store.current
        val other = HotkeyAction.entries.firstOrNull { it != action && s.hotkeyBinding(it) == binding }
        if (other != null) {
            registerAll()
            return ERR_DUPLICATE_FORMAT.format(binding.format(), other.displayName)
        }
        val old = s.hotkeys.get(action)
        val saved = store.update { it.copy(hotkeys = it.hotkeys.with(action, binding.format())) }
        val failed = registerAll()
        if (action in failed) {
            store.update { it.copy(hotkeys = it.hotkeys.with(action, old)) }
            registerAll()
            return ERR_TAKEN_FORMAT.format(binding.format())
        }
        return if (saved) null else ERR_SAVE
    }

    /** Выбор директории в диалоге на dialogExecutor. onDone(текст ошибки или null) вызывается на uiExecutor. */
    fun browseDirectory(onDone: (String?) -> Unit) {
        dialogExecutor.execute {
            val chosen = chooseFolder()
            val result: String? = when {
                chosen == null -> null
                !isWritable(chosen) -> ERR_NOT_WRITABLE_FORMAT.format(chosen.toString())
                !store.update { it.copy(defaultDirectory = chosen.toString()) } -> ERR_SAVE
                else -> null
            }
            uiExecutor.execute { onDone(result) }
        }
    }

    /** defaultDirectory = defaults.initialDirectory(). Ошибка или null. */
    fun resetDirectory(): String? {
        val initial = defaults.initialDirectory().toString()
        return if (store.update { it.copy(defaultDirectory = initial) }) null else ERR_SAVE
    }

    /** Открывает директорию по умолчанию в проводнике. */
    fun openDirectory() {
        dialogExecutor.execute { openFolder(Path.of(store.current.defaultDirectory)) }
    }

    /** Включение указателя в снимок. Ошибка или null. */
    fun setIncludeCursor(value: Boolean): String? =
        if (store.update { it.copy(includeCursor = value) }) null else ERR_SAVE

    /** Автосохранение. Ошибка или null. */
    fun setAutoSave(value: Boolean): String? =
        if (store.update { it.copy(autoSaveEnabled = value) }) null else ERR_SAVE

    /**
     * Назначает клавишу оверлея. key == null — очистить слот (только SECONDARY).
     * @return null — успех; ERR_PRIMARY_REQUIRED; ERR_OVERLAY_DUPLICATE_FORMAT; ERR_SAVE.
     */
    fun applyOverlayKey(action: OverlayAction, slot: KeySlot, key: OverlayKey?): String? {
        val m = store.current.multiRegion
        if (key == null) {
            if (slot == KeySlot.PRIMARY) return ERR_PRIMARY_REQUIRED
        } else {
            for (a in OverlayAction.entries) {
                for (sl in KeySlot.entries) {
                    if (a == action && sl == slot) continue
                    if (m.resolvedKey(a, sl) == key) return ERR_OVERLAY_DUPLICATE_FORMAT.format(key.format(), a.displayName)
                }
            }
        }
        val text = key?.format() ?: ""
        return if (store.update { it.copy(multiRegion = it.multiRegion.withKey(action, slot, text)) }) null else ERR_SAVE
    }

    /** text.trim() → Int в MIN_REGIONS..MAX_REGIONS → сохранить. Иначе ERR_MAX_REGIONS. null — успех; ERR_SAVE. */
    fun setMaxRegions(text: String): String? {
        val n = text.trim().toIntOrNull()
        if (n == null || n !in MultiRegionSettings.MIN_REGIONS..MultiRegionSettings.MAX_REGIONS) return ERR_MAX_REGIONS
        return if (store.update { it.copy(multiRegion = it.multiRegion.copy(maxRegions = n)) }) null else ERR_SAVE
    }

    /** parseHexColor(text.trim()) → сохранить formatHexColor(rgb). Иначе ERR_FILL_COLOR. null — успех; ERR_SAVE. */
    fun setFillColor(text: String): String? {
        val rgb = MultiRegionSettings.parseHexColor(text.trim()) ?: return ERR_FILL_COLOR
        val hex = MultiRegionSettings.formatHexColor(rgb)
        return if (store.update { it.copy(multiRegion = it.multiRegion.copy(fillColor = hex)) }) null else ERR_SAVE
    }

    /** multiRegion = MultiRegionSettings(); глобальная клавиша MULTI_REGION = Ctrl+Shift+4. Первая ошибка или null. */
    fun resetMultiRegion(): String? {
        val saved = store.update { it.copy(multiRegion = MultiRegionSettings()) }
        val def = HotkeyBinding.defaultFor(HotkeyAction.MULTI_REGION)
        val hotkeyErr =
            if (store.current.hotkeyBinding(HotkeyAction.MULTI_REGION) != def) applyHotkey(HotkeyAction.MULTI_REGION, def) else null
        return hotkeyErr ?: (if (saved) null else ERR_SAVE)
    }

    companion object {
        const val ERR_SAVE: String = "Не удалось сохранить настройки"
        const val ERR_INVALID_COMBO: String = "Сочетание должно содержать Ctrl или Alt и клавишу A–Z, 0–9 или F1–F12"
        const val ERR_DUPLICATE_FORMAT: String = "Сочетание %s уже используется для режима «%s»"
        const val ERR_TAKEN_FORMAT: String = "Сочетание %s занято другим приложением"
        const val ERR_NOT_WRITABLE_FORMAT: String = "В папку «%s» нельзя записывать. Выберите другую папку."
        const val ERR_PRIMARY_REQUIRED: String = "Основное сочетание обязательно"
        const val ERR_OVERLAY_DUPLICATE_FORMAT: String = "Сочетание %s уже назначено действию «%s»"
        const val ERR_OVERLAY_INVALID: String =
            "Клавиша не поддерживается. Допустимы A–Z, 0–9, F1–F12, Enter, NumPad Enter, Backspace, Esc, Delete, Space"
        const val ERR_MAX_REGIONS: String = "Максимум областей — целое число от 2 до 50"
        const val ERR_FILL_COLOR: String = "Цвет — в формате #RRGGBB, например #808080"
        val FILL_PALETTE: List<String> =
            listOf("#808080", "#FFFFFF", "#000000", "#C0C0C0", "#FF00FF", "#2E86DE", "#27AE60", "#F1C40F")
    }
}

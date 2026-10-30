package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.HotkeyService
import by.sparkyanvil.fastcowscreenshot.settings.DefaultDirectoryProvider
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
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

    companion object {
        const val ERR_SAVE: String = "Не удалось сохранить настройки"
        const val ERR_INVALID_COMBO: String = "Сочетание должно содержать Ctrl или Alt и клавишу A–Z, 0–9 или F1–F12"
        const val ERR_DUPLICATE_FORMAT: String = "Сочетание %s уже используется для режима «%s»"
        const val ERR_TAKEN_FORMAT: String = "Сочетание %s занято другим приложением"
        const val ERR_NOT_WRITABLE_FORMAT: String = "В папку «%s» нельзя записывать. Выберите другую папку."
    }
}

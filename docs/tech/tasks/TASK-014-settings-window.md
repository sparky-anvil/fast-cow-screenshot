# TASK-014: Реализовать окно настроек (директория, указатель, автосохранение, переназначение клавиш)

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.3, .4, .21, .35, .43–.48, .53; US-007 AC-1–AC-6; US-001 AC-8 |
| ADR | ADR-001, ADR-002, ADR-003 |
| Зависит от | TASK-004, TASK-005, TASK-007, TASK-009, TASK-010, TASK-011 |
| Блокирует | TASK-015 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Окно настроек на Compose и его логика (`SettingsController`):
- выбор директории по умолчанию через стандартный диалог выбора папки Windows с проверкой записи, «Открыть папку»,
  «Сбросить»;
- флажки «Включать указатель мыши в снимок» и «Автоматически сохранять снимки»;
- переназначение трёх горячих клавиш.

`SettingsController.registerAll()` используется и при запуске приложения (TASK-015).

## 2. Контекст

- Логика вынесена в `SettingsController` и тестируется на моках. `SettingsWindow` только рисует и вызывает контроллер.
- Диалог выбора папки — `SHBrowseForFolderW` с `BIF_NEWDIALOGSTYLE | BIF_RETURNONLYFSDIRS`, на потоке `fcs-dialog`
  (COM STA). Возвращает PIDL → `SHGetPathFromIDListW` → путь; PIDL освобождается `Ole32.INSTANCE.CoTaskMemFree`.
- **Запись сочетания.** Пока ждём нажатие, все глобальные клавиши сняты (`unregisterAll`). Иначе нажатие текущего
  сочетания перехватит Windows и запустит захват. После записи или отмены всё регистрируется заново (`registerAll`).
- Коды клавиш AWT (`java.awt.event.KeyEvent.VK_*`) для `0–9`, `A–Z`, `F1–F12` численно совпадают с виртуальными
  кодами Windows. Поэтому `e.key.nativeKeyCode` передаётся в `HotkeyBinding` напрямую (исправлено TASK-017, CLR-003).
- Окно исключается из захвата (`WindowHandles.excludeFromCapture`) после первого показа.

**Выдержки из требований (дословно):**

> **REQ-001.43** Система должна предоставлять в настройках параметр «Директория по умолчанию» с выбором папки через стандартный диалог выбора папки Windows.
>
> **REQ-001.46** При выборе новой директории система должна проверять, что в неё можно записывать. Если нельзя, система должна показать ошибку и оставить прежнее значение.
>
> **REQ-001.48** Система должна предоставлять кнопку «Сбросить», которая возвращает директорию по умолчанию к начальному значению из REQ-001.44.
>
> **REQ-001.4** Система должна позволять переназначить горячую клавишу каждого режима в настройках. Новое значение должно сохраняться между перезапусками.
>
> **US-007 (заметки)** Окно настроек: поле только для чтения с путём, кнопки «Обзор…», «Открыть папку», «Сбросить».

## 3. Предусловия

- TASK-004 (`SettingsStore`, `AppSettings`, `HotkeyAction`, `HotkeyBinding`, `HotkeySettings.with`, `DefaultDirectoryProvider`),
  TASK-005 (`Shell32Api.SHBrowseForFolder`, `SHGetPathFromIDList`, `BROWSEINFO`, `WindowHandles`), TASK-007 (`HotkeyService`),
  TASK-009 (`DirectoryValidator`), TASK-011 (`ExplorerLauncher.openFolder`).

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/WindowsFolderDialog.kt` | Создать | диалог папки |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/HotkeyRecorder.kt` | Создать | object |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsController.kt` | Создать | логика |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsWindow.kt` | Создать | окно + `@Composable SettingsContent` |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsControllerTest.kt` | Создать | unit |
| 6 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/HotkeyRecorderTest.kt` | Создать | unit |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** SHBrowseForFolderW. Вызывать на fcs-dialog. Не бросает. */
internal class WindowsFolderDialog(private val shell32: Shell32Api = Shell32Api.INSTANCE) {
    /** Выбранная папка или null («Отмена»/виртуальная папка без пути). */
    fun show(): java.nio.file.Path? = TODO()
    companion object { const val TITLE: String = "Выберите директорию для снимков" }
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

internal object HotkeyRecorder {
    /** Коды клавиш-модификаторов AWT: VK_CONTROL, VK_SHIFT, VK_ALT, VK_WINDOWS, VK_ALT_GRAPH. */
    fun isModifierKey(awtKeyCode: Int): Boolean = TODO()
    /** HotkeyBinding или null, если нет Ctrl/Alt или клавиша не поддерживается. Не бросает. */
    fun toBinding(ctrl: Boolean, alt: Boolean, shift: Boolean, awtKeyCode: Int): HotkeyBinding? = TODO()
}

/** Логика окна настроек. Методы — на EDT, кроме registerAll (любой поток). */
internal class SettingsController(
    private val store: SettingsStore,
    private val hotkeys: HotkeyService,
    private val defaults: DefaultDirectoryProvider,
    private val chooseFolder: () -> java.nio.file.Path?,          // WindowsFolderDialog()::show
    private val isWritable: (java.nio.file.Path) -> Boolean,      // DirectoryValidator::isWritable
    private val openFolder: (java.nio.file.Path) -> Unit,         // { explorer.openFolder(it) }
    private val dialogExecutor: java.util.concurrent.Executor,
    private val uiExecutor: java.util.concurrent.Executor,
) {
    /** Регистрирует три клавиши из store.current. Возвращает действия, которые зарегистрировать не удалось. */
    fun registerAll(): List<HotkeyAction> = TODO()
    fun beginHotkeyRecording(): Unit = TODO()       // hotkeys.unregisterAll()
    fun cancelHotkeyRecording(): Unit = TODO()      // registerAll()
    /** Раздел 8.2. Возвращает текст ошибки или null при успехе. */
    fun applyHotkey(action: HotkeyAction, binding: HotkeyBinding): String? = TODO()
    /** Раздел 8.3. onDone(текст ошибки или null) вызывается на uiExecutor. */
    fun browseDirectory(onDone: (String?) -> Unit): Unit = TODO()
    /** defaultDirectory = defaults.initialDirectory(). Ошибка или null. */
    fun resetDirectory(): String? = TODO()
    fun openDirectory(): Unit = TODO()              // dialogExecutor.execute { openFolder(Path.of(store.current.defaultDirectory)) }
    fun setIncludeCursor(value: Boolean): String? = TODO()
    fun setAutoSave(value: Boolean): String? = TODO()

    companion object {
        const val ERR_SAVE: String = "Не удалось сохранить настройки"
        const val ERR_INVALID_COMBO: String = "Сочетание должно содержать Ctrl или Alt и клавишу A–Z, 0–9 или F1–F12"
        /** "Сочетание %s уже используется для режима «%s»" */
        const val ERR_DUPLICATE_FORMAT: String = "Сочетание %s уже используется для режима «%s»"
        /** "Сочетание %s занято другим приложением" */
        const val ERR_TAKEN_FORMAT: String = "Сочетание %s занято другим приложением"
        /** "В папку «%s» нельзя записывать. Выберите другую папку." */
        const val ERR_NOT_WRITABLE_FORMAT: String = "В папку «%s» нельзя записывать. Выберите другую папку."
    }
}

/** Окно настроек. show() — на EDT. */
internal class SettingsWindow(private val controller: SettingsController, private val store: SettingsStore) {
    /** Создаёт окно при первом вызове, далее — toFront(). */
    fun show(): Unit = TODO()
    fun dispose(): Unit = TODO()
    companion object {
        const val TITLE: String = "Настройки — fast-cow-screenshot"
        const val WIDTH: Int = 600
        const val HEIGHT: Int = 460
    }
}
```

## 7. Пошаговая реализация

1. `WindowsFolderDialog` (8.4).
2. `HotkeyRecorder` + тест.
3. `SettingsController` (8.1–8.3) + тест.
4. `SettingsWindow` (8.5).

## 8. Алгоритм и логика

### 8.1. registerAll

`s = store.current`; `failed = HotkeyAction.entries.filterNot { hotkeys.register(it, s.hotkeyBinding(it)) }`; вернуть `failed`.

### 8.2. applyHotkey(action, binding)

1. `s = store.current`; `other = HotkeyAction.entries.firstOrNull { it != action && s.hotkeyBinding(it) == binding }`.
   Если `other != null` → `registerAll()`; вернуть `ERR_DUPLICATE_FORMAT.format(binding.format(), other.displayName)`.
2. `old = s.hotkeys.get(action)`; `saved = store.update { it.copy(hotkeys = it.hotkeys.with(action, binding.format())) }`.
3. `failed = registerAll()`. Если `action in failed`:
   `store.update { it.copy(hotkeys = it.hotkeys.with(action, old)) }`; `registerAll()`;
   вернуть `ERR_TAKEN_FORMAT.format(binding.format())`.
4. Если `!saved` → `ERR_SAVE`, иначе `null`.

### 8.3. browseDirectory(onDone)

```
dialogExecutor.execute {
  chosen = chooseFolder()
  result: String? = when {
    chosen == null -> null
    !isWritable(chosen) -> ERR_NOT_WRITABLE_FORMAT.format(chosen.toString())
    !store.update { it.copy(defaultDirectory = chosen.toString()) } -> ERR_SAVE
    else -> null
  }
  uiExecutor.execute { onDone(result) }
}
```
`resetDirectory()` = `if (store.update { it.copy(defaultDirectory = defaults.initialDirectory().toString()) }) null else ERR_SAVE`.
`setIncludeCursor`/`setAutoSave` — аналогичный `update` одного поля (`includeCursor` / `autoSaveEnabled`), `ERR_SAVE` при `false`.

### 8.4. WindowsFolderDialog.show

```
display = Memory(WinConst.MAX_PATH * 2L).apply { clear() }
bi = BROWSEINFO().apply { hwndOwner = null; pszDisplayName = display; lpszTitle = WString(TITLE); ulFlags = BIF_RETURNONLYFSDIRS or BIF_NEWDIALOGSTYLE }
pidl = shell32.SHBrowseForFolder(bi) ?: return null
try { buf = CharArray(WinConst.MAX_PATH); if (!shell32.SHGetPathFromIDList(pidl, buf)) return null; return Path.of(Native.toString(buf)) }
finally { Ole32.INSTANCE.CoTaskMemFree(pidl) }
```

### 8.5. SettingsWindow / SettingsContent

- `state = mutableStateOf(store.current)`; в конструкторе `store.addListener { s -> SwingUtilities.invokeLater { state.value = s } }`.
- `show()`: если окна нет — `ComposeWindow().apply { title = TITLE; setSize(WIDTH, HEIGHT); defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE; setLocationRelativeTo(null); setContent { SettingsContent(state.value, controller) } }`,
  `isVisible = true`, затем `WindowHandles.excludeFromCapture(window)`. Иначе — `isVisible = true; toFront()`.
- `SettingsContent` (`MaterialTheme`, `Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()))`):
  1. `Text("Директория по умолчанию", fontWeight = Bold)`; `OutlinedTextField(value = s.defaultDirectory, onValueChange = {}, readOnly = true, singleLine = true, modifier = fillMaxWidth())`;
     `Row`: `Button("Обзор…") → controller.browseDirectory { dirError = it }`, `OutlinedButton("Открыть папку") → controller.openDirectory()`,
     `OutlinedButton("Сбросить") → dirError = controller.resetDirectory()`; если `dirError != null` — `Text(dirError, color = Color(0xFFD32F2F))`.
  2. `Row { Checkbox(s.includeCursor) { generalError = controller.setIncludeCursor(it) }; Text("Включать указатель мыши в снимок") }`.
  3. `Row { Checkbox(s.autoSaveEnabled) { generalError = controller.setAutoSave(it) }; Text("Автоматически сохранять снимки в директорию по умолчанию") }`.
  4. `Text("Горячие клавиши", fontWeight = Bold)`; для каждого `action`: `Row { Text(action.displayName, Modifier.width(200.dp)); Text(if (recording == action) "Нажмите сочетание… (Esc — отмена)" else s.hotkeyBinding(action).format(), Modifier.width(240.dp)); OutlinedButton("Изменить") { recording = action; hotkeyError = null; controller.beginHotkeyRecording() } }`;
     `hotkeyError` — красным под списком.
   5. Корневой модификатор окна: `Modifier.onPreviewKeyEvent { e -> … }` — **заменён TASK-017 (CLR-003)**; прежняя версия:
      только если `recording != null` и `e.type == KeyEventType.KeyDown`:
     ~~`k = e.nativeKeyEvent as java.awt.event.KeyEvent`~~; `Esc` (`VK_ESCAPE`) → `controller.cancelHotkeyRecording(); recording = null`;
     модификатор (`HotkeyRecorder.isModifierKey`) → ждать дальше; иначе `b = HotkeyRecorder.toBinding(k.isControlDown, k.isAltDown, k.isShiftDown, k.keyCode)`:
     `null` → `hotkeyError = ERR_INVALID_COMBO`, `controller.cancelHotkeyRecording()`; иначе `hotkeyError = controller.applyHotkey(action, b)`. В обоих случаях `recording = null`; вернуть `true`.
  6. `generalError` — красным внизу. Состояния `dirError`, `hotkeyError`, `generalError`, `recording` — `remember { mutableStateOf(...) }`.
- Чтобы `onPreviewKeyEvent` получал клавиши, корневой `Box` должен иметь фокус: `Modifier.focusRequester(fr).focusable()` и
  `LaunchedEffect(recording) { if (recording != null) fr.requestFocus() }`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | Папка без прав записи (`C:\Windows\System32`) | значение не меняется | — | `"В папку «C:\Windows\System32» нельзя записывать. Выберите другую папку."` |
| 2 | Отмена выбора папки | ничего | — | — |
| 3 | Сочетание занято другим процессом | старое значение восстановлено и зарегистрировано | `WARN` (HotkeyService) | `"Сочетание Ctrl+Alt+P занято другим приложением"` |
| 4 | Сочетание уже у другого режима | ничего не меняется | — | `"Сочетание Ctrl+Shift+2 уже используется для режима «Все мониторы»"` |
| 5 | Нет Ctrl/Alt или клавиша не поддерживается | ничего не меняется | — | `ERR_INVALID_COMBO` |
| 6 | Не удалось записать `settings.json` | значение действует до перезапуска | `ERROR` (SettingsStore) | `"Не удалось сохранить настройки"` |

## 10. Константы и тексты

Тексты (дословно): заголовок окна `"Настройки — fast-cow-screenshot"`; `"Директория по умолчанию"`, `"Обзор…"`,
`"Открыть папку"`, `"Сбросить"`, `"Включать указатель мыши в снимок"`,
`"Автоматически сохранять снимки в директорию по умолчанию"`, `"Горячие клавиши"`, `"Изменить"`,
`"Нажмите сочетание… (Esc — отмена)"`; заголовок диалога папки `"Выберите директорию для снимков"`; ошибки — раздел 6.
Цвет ошибок `#D32F2F`.

## 11. Тесты

`HotkeyRecorderTest`:

| # | Метод | When | Then |
|---|---|---|---|
| 1 | `` `maps ctrl shift digit`() `` | `toBinding(true,false,true, KeyEvent.VK_1)` | `HotkeyBinding(true,false,true,0x31)` |
| 2 | `` `maps alt f12`() `` | `toBinding(false,true,false, KeyEvent.VK_F12)` | `keyCode == 0x7B` |
| 3 | `` `rejects shift only`() `` | `toBinding(false,false,true, KeyEvent.VK_A)` | `null` |
| 4 | `` `rejects unsupported key`() `` | `toBinding(true,false,false, KeyEvent.VK_ENTER)` | `null` |
| 5 | `` `detects modifier keys`() `` | `isModifierKey(VK_SHIFT)`, `isModifierKey(VK_A)` | `true`, `false` |

`SettingsControllerTest`: `store` на `tempDir` (`defaults = { tempDir/"Screenshots" }`), `hotkeys = mockk<HotkeyService>()`
(по умолчанию `register(any(), any())` → `true`, `unregisterAll()` → `just Runs`), `direct = Executor { it.run() }`.

| # | Метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `` `registerAll reports failed actions`() `` | `register(ALL_MONITORS, any())` → false | `registerAll()` | `[ALL_MONITORS]` | REQ-001.3 |
| 2 | `` `apply hotkey saves and registers`() `` | — | `applyHotkey(REGION, Ctrl+Alt+P)` | `null`; `store.current.hotkeys.region == "Ctrl+Alt+P"`; `verify { hotkeys.register(REGION, Ctrl+Alt+P) }` | REQ-001.4 |
| 3 | `` `apply hotkey taken reverts`() `` | `register(REGION, Ctrl+Alt+P)` → false | `applyHotkey(REGION, Ctrl+Alt+P)` | `"Сочетание Ctrl+Alt+P занято другим приложением"`; `hotkeys.region == "Ctrl+Shift+3"` | REQ-001.3 |
| 4 | `` `duplicate with other action rejected`() `` | — | `applyHotkey(REGION, Ctrl+Shift+2)` | `"Сочетание Ctrl+Shift+2 уже используется для режима «Все мониторы»"`; значение не изменилось | — |
| 5 | `` `unwritable folder keeps old value`() `` | `chooseFolder` → `Path.of("C:\\Windows\\System32")`, `isWritable` → false | `browseDirectory { err = it }` | `err == "В папку «C:\\Windows\\System32» нельзя записывать. Выберите другую папку."`; `defaultDirectory` прежний | US-007 AC-4 |
| 6 | `` `writable folder is saved`() `` | `chooseFolder` → `tempDir/"Скриншоты"`, `isWritable` → true | `browseDirectory` | `err == null`; `defaultDirectory == (tempDir/"Скриншоты").toString()` | US-007 AC-2 |
| 7 | `` `cancel folder changes nothing`() `` | `chooseFolder` → null | `browseDirectory` | `err == null`, значение прежнее | — |
| 8 | `` `reset restores initial`() `` | `defaultDirectory` изменён на `"D:\\x"` | `resetDirectory()` | `defaultDirectory == (tempDir/"Screenshots").toString()` | US-007 AC-6 |
| 9 | `` `toggles are persisted`() `` | — | `setIncludeCursor(true)`, `setAutoSave(false)` | новый `SettingsRepository(...).load()` содержит `true` / `false` | REQ-001.53, .35 |
| 10 | `` `recording unregisters all`() `` | — | `beginHotkeyRecording()` | `verify { hotkeys.unregisterAll() }` | — |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.HotkeyRecorderTest"` | 5 тестов |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.SettingsControllerTest"` | 10 тестов |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная — шаги 21–27 TASK-015.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–4 выполнены; контракты — как в разделе 6.
- [x] Все случаи раздела 9 дают указанные тексты.
- [x] 15 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать `JFileChooser`, Material3.
- Не вызывать диалог папки на EDT.
- Не добавлять настройки сверх перечисленных.

## 15. Ловушки и подсказки

- `ComposeWindow` — `androidx.compose.ui.awt.ComposeWindow`.
- **Исправлено (CLR-003, TASK-017):** `e.nativeKeyEvent` в Compose 1.12.2 — `InternalKeyEvent`, приведение к
  `java.awt.event.KeyEvent` падает. Обработка нажатий в §8.5 п.5 заменена на
  `HotkeyRecorder.onKeyDown(e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.key.nativeKeyCode)` — см. TASK-017.
- `String.format` с кириллицей — `ERR_TAKEN_FORMAT.format(x)` (Kotlin `String.format`).
- `SHGetPathFromIDList` пишет строку с завершающим `\u0000` — `Native.toString(CharArray)` обрезает его.
- Окно закрывается крестиком через `HIDE_ON_CLOSE`. Приложение при этом не завершается (выход — только из трея).

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-31 | Review | Реализованы шаги 1–4, 15 тестов зелёные, `.\gradlew.bat build` успешен. Отклонение: поле окна названо `settingsState` вместо `state` (конфликт с `Frame.state` внутри `ComposeWindow.apply`). Ручная проверка (шаги 21–27 TASK-015) — после TASK-015. TASK-013 переведена в Done по указанию пользователя. |

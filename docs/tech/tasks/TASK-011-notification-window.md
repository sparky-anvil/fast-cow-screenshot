# TASK-011: Реализовать окно уведомлений (3 с, замена, «Сохранить как…», открыть папку с файлом)

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.21, .25, .31, .34, .37, .54, N12; US-005 AC-4, AC-6, AC-7; US-001 AC-6 |
| ADR | ADR-001, ADR-002 |
| Зависит от | TASK-003, TASK-005, TASK-010 |
| Блокирует | TASK-015 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Реализовать `CaptureNotifier` — собственное всплывающее окно в правом нижнем углу основного монитора. Окно не
забирает фокус, не попадает в снимки, скрывается через 3000 мс, новое уведомление заменяет старое. Клик по окну
открывает Проводник с выделенным файлом, кнопка «Сохранить как…» вызывает переданный обработчик.

## 2. Контекст

- Окно — один переиспользуемый `javax.swing.JWindow` с `androidx.compose.ui.awt.ComposePanel` внутри. Содержимое
  описывает `NotificationModel` — чистая модель, которая строится из `CaptureOutcome` (тестируется без UI).
- `focusableWindowState = false` — окно не активируется при показе. `isAlwaysOnTop = true`.
  `WindowHandles.excludeFromCapture(window)` после `addNotify()` убирает окно из снимков (REQ-001.21).
- Положение — в координатах AWT (не физических): `GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice`
  — это основной монитор, `Toolkit.getScreenInsets` — панель задач.
- Открытие папки с выделенным файлом — `SHOpenFolderAndSelectItems` на потоке `fcs-dialog` (там инициализирован COM).
- Все методы `NotificationWindow` вызываются на EDT.

**Выдержки из требований (дословно):**

> **REQ-001.34** После успешного захвата система должна показать уведомление с именем сохранённого файла (например, `Screenshot_2026-10-18_20-00-48.png`) и полным путём к папке, куда он сохранён. Клик по уведомлению открывает папку с файлом, и файл в ней выделен.
>
> **REQ-001.54** Уведомления приложения (об успехе и об ошибках) должны автоматически скрываться через 3 секунды после появления. Новое уведомление заменяет предыдущее, а не накладывается на него.
>
> **REQ-001.25** … показать уведомление «Не удалось скопировать в буфер обмена»; файл при этом сохраняется.
>
> **US-005 (заметки)** Макет уведомления: заголовок «Снимок сохранён», строка с именем файла, строка с путём к папке (длинный путь сокращается многоточием в середине), кнопка «Сохранить как…». Если путь длинный и сокращён, полный путь показывается во всплывающей подсказке.

## 3. Предусловия

- TASK-003: `TextEllipsis.middle`. TASK-005: `Shell32Api`, `WindowHandles`. TASK-010: `CaptureNotifier`, `CaptureOutcome`,
  `ClipboardResult`, `SaveResult`.
- `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/NotificationModel.kt` | Создать | data class + `NotificationModels` (object в том же файле) |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/NotificationContent.kt` | Создать | `@Composable` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/NotificationWindow.kt` | Создать | класс |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/ExplorerLauncher.kt` | Создать | класс |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/NotificationModelsTest.kt` | Создать | unit |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/** Что показать. folderTooltip — полный путь, если строка папки сокращена; иначе null. */
internal data class NotificationModel(
    val title: String,
    val lines: List<String>,
    val fileToReveal: java.nio.file.Path?,
    val showSaveAs: Boolean,
    val folderTooltip: String?,
)

internal object NotificationModels {
    const val MAX_PATH_CHARS: Int = 48
    const val TITLE_SAVED: String = "Снимок сохранён"
    const val TITLE_COPIED_ONLY: String = "Снимок скопирован в буфер обмена"
    const val TITLE_SAVE_FAILED: String = "Не удалось сохранить снимок"
    const val TITLE_CLIPBOARD_FAILED: String = "Не удалось скопировать в буфер обмена"
    const val TITLE_BOTH_FAILED: String = "Не удалось скопировать в буфер обмена и сохранить снимок"
    /** Таблица раздела 8. */
    fun fromOutcome(outcome: CaptureOutcome): NotificationModel = TODO()
    /** NotificationModel(title, lines, null, false, null). */
    fun message(title: String, lines: List<String>): NotificationModel = TODO()
}

/** Окно уведомлений. Все методы — на EDT. */
internal class NotificationWindow(
    private val onSaveAs: () -> Unit,
    private val explorer: ExplorerLauncher,
    private val dialogExecutor: java.util.concurrent.Executor,
) : CaptureNotifier {
    override fun showOutcome(outcome: CaptureOutcome) = show(NotificationModels.fromOutcome(outcome))
    override fun showMessage(title: String, lines: List<String>) = show(NotificationModels.message(title, lines))
    fun show(model: NotificationModel): Unit = TODO()
    fun hide(): Unit = TODO()
    /** window.dispose(), timer.stop(). */
    fun dispose(): Unit = TODO()

    companion object {
        const val DISPLAY_MS: Int = 3000
        const val WIDTH: Int = 360
        const val HEIGHT: Int = 130
        const val MARGIN: Int = 12
        const val SAVE_AS_TEXT: String = "Сохранить как…"
    }
}

@Composable
internal fun NotificationContent(model: NotificationModel, onClick: () -> Unit, onSaveAs: () -> Unit)
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** Проводник. Вызывать на fcs-dialog (COM STA). Не бросает. */
internal class ExplorerLauncher(private val shell32: Shell32Api = Shell32Api.INSTANCE) {
    /** Открывает папку файла и выделяет его (SHOpenFolderAndSelectItems). true — успех. */
    fun reveal(file: java.nio.file.Path): Boolean = TODO()
    /** Открывает саму папку: java.awt.Desktop.getDesktop().open(dir.toFile()). true — успех. */
    fun openFolder(dir: java.nio.file.Path): Boolean = TODO()
}
```

## 7. Пошаговая реализация

1. `NotificationModel.kt` + `NotificationModelsTest` (раздел 8).
2. `ExplorerLauncher.kt` (8.2).
3. `NotificationContent.kt` (8.3).
4. `NotificationWindow.kt` (8.4).
5. Ручная проверка — в TASK-015 (окно показывается только в собранном приложении). Здесь — компиляция и unit-тесты.

## 8. Алгоритм и логика

### 8.1. fromOutcome

`folderLine(dir)` = `TextEllipsis.middle(dir.toString(), MAX_PATH_CHARS)`; `tooltip(dir)` = полный путь, если он длиннее
`MAX_PATH_CHARS`, иначе `null`.

| clipboard | save | title | lines | fileToReveal | showSaveAs | folderTooltip |
|---|---|---|---|---|---|---|
| Success | `Saved(f)` | `TITLE_SAVED` | `[f.fileName, folderLine(f.parent)]` | `f` | true | `tooltip(f.parent)` |
| Success | `null` | `TITLE_COPIED_ONLY` | `[]` | null | true | null |
| Success | `Failed(d, r)` | `TITLE_SAVE_FAILED` | `[r, folderLine(d)]`; если `d.toString().isEmpty()` — `[r]` | null | true | `tooltip(d)` |
| Failure | `Saved(f)` | `TITLE_CLIPBOARD_FAILED` | `[f.fileName, folderLine(f.parent)]` | `f` | true | `tooltip(f.parent)` |
| Failure | `null` | `TITLE_CLIPBOARD_FAILED` | `[]` | null | true | null |
| Failure | `Failed(d, r)` | `TITLE_BOTH_FAILED` | `[r]` | null | true | null |

### 8.2. ExplorerLauncher.reveal

```
pidl = shell32.ILCreateFromPath(file.toString()) ?: run { log.warn("ILCreateFromPath failed for {}", file); return false }
try { hr = shell32.SHOpenFolderAndSelectItems(pidl, 0, null, 0); if (hr.toInt() != 0) { log.warn("SHOpenFolderAndSelectItems failed, HRESULT={}", hr.toInt()); return false }; return true }
finally { shell32.ILFree(pidl) }
```
`openFolder`: `try { Desktop.getDesktop().open(dir.toFile()); true } catch (e: Exception) { log.warn("Open folder failed: {}", e.toString()); false }`.

### 8.3. NotificationContent

```
MaterialTheme {
  Surface(color = Color(0xFF2B2B2B), modifier = Modifier.fillMaxSize().clickable(onClick = onClick)) {
    Column(Modifier.padding(12.dp)) {
      Text(model.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
      model.lines.forEachIndexed { i, line ->
        // последняя строка с путём: если model.folderTooltip != null и i == lines.lastIndex — обернуть в TooltipArea
        Text(line, color = Color(0xFFDDDDDD), fontSize = 12.sp, maxLines = 1)
      }
      if (model.showSaveAs) TextButton(onClick = onSaveAs) { Text(NotificationWindow.SAVE_AS_TEXT, color = Color(0xFF6CB4FF)) }
    }
  }
}
```
Подсказка: `TooltipArea(tooltip = { Surface(color = Color(0xFF444444)) { Text(tooltipText, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(6.dp)) } }) { Text(...) }`,
функция с `@OptIn(ExperimentalFoundationApi::class)`. Material — `androidx.compose.material.*`.

### 8.4. NotificationWindow

- `state = mutableStateOf<NotificationModel?>(null)`; `window: JWindow?` — создаётся при первом `show`:
  1. `JWindow()`, `focusableWindowState = false`, `isAlwaysOnTop = true`, `type = Window.Type.POPUP`, `setSize(WIDTH, HEIGHT)`.
  2. `panel = ComposePanel()`; `panel.setContent { state.value?.let { m -> NotificationContent(m, onClick = { onBodyClick(m) }, onSaveAs = { hide(); onSaveAs() }) } }`;
     `window.contentPane.add(panel)`.
  3. `window.addNotify()`; `WindowHandles.excludeFromCapture(window)`.
- `timer = javax.swing.Timer(DISPLAY_MS) { hide() }.apply { isRepeats = false }`.
- `show(model)`: `state.value = model`; вычислить позицию (ниже); `window.setLocation(x, y)`; `window.isVisible = true`;
  `timer.restart()`; `log.debug("Notification shown: {}", model.title)`.
  `gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration`; `b = gc.bounds`;
  `ins = Toolkit.getDefaultToolkit().getScreenInsets(gc)`; `x = b.x + b.width - ins.right - WIDTH - MARGIN`;
  `y = b.y + b.height - ins.bottom - HEIGHT - MARGIN`.
- `onBodyClick(m)`: `hide()`; если `m.fileToReveal != null` → `dialogExecutor.execute { explorer.reveal(file) }`.
- `hide()`: `timer.stop()`; `window?.isVisible = false`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | Новое уведомление, пока видно старое | содержимое заменяется, таймер перезапускается | `DEBUG` | одно окно |
| 2 | Файл удалён до клика | `reveal` → false | `WARN …` | Проводник не открывается |
| 3 | `excludeFromCapture` false | окно работает | `WARN` (в `WindowHandles`) | — |

## 10. Константы и тексты

Тексты (дословно): `"Снимок сохранён"`, `"Снимок скопирован в буфер обмена"`, `"Не удалось сохранить снимок"`,
`"Не удалось скопировать в буфер обмена"`, `"Не удалось скопировать в буфер обмена и сохранить снимок"`,
`"Сохранить как…"` (символ `…` U+2026). Цвета: фон `#2B2B2B`, заголовок `#FFFFFF`, строки `#DDDDDD`,
кнопка `#6CB4FF`, подсказка `#444444`. Размеры — раздел 6.

## 11. Тесты

`NotificationModelsTest`. `f = Path.of("C:\\Users\\Александр\\Pictures\\Screenshots\\Screenshot_2026-10-18_20-00-48.png")`
(папка — 39 символов, не сокращается); `shot = Screenshot(BufferedImage(1,1,TYPE_INT_RGB), LocalDateTime.of(2026,10,18,20,0,48), CaptureMode.ALL_MONITORS)`.

| # | Метод | Given | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `saved shows file name and folder`() `` | Success + `Saved(f)` | title `"Снимок сохранён"`; lines `["Screenshot_2026-10-18_20-00-48.png", "C:\\Users\\Александр\\Pictures\\Screenshots"]`; reveal `f`; saveAs true; tooltip null | REQ-001.34, US-005 AC-6 |
| 2 | `` `long folder is shortened with tooltip`() `` | Success + `Saved(Path.of("D:\\" + "a".repeat(60) + "\\x.png"))` | `lines[1].length == 48`, содержит `"…"`; tooltip = полный путь папки | US-005 |
| 3 | `` `autosave disabled`() `` | Success + null | title `"Снимок скопирован в буфер обмена"`, lines пусто, saveAs true | REQ-001.35 |
| 4 | `` `save failed offers save as`() `` | Success + `Failed(Path.of("E:\\shots"), "Папка недоступна")` | title `"Не удалось сохранить снимок"`; lines `["Папка недоступна", "E:\\shots"]`; reveal null; saveAs true | REQ-001.31, US-005 AC-4 |
| 5 | `` `clipboard failed but saved`() `` | `Failure("busy")` + `Saved(f)` | title `"Не удалось скопировать в буфер обмена"`; reveal `f` | REQ-001.25 |
| 6 | `` `both failed`() `` | `Failure("busy")` + `Failed(Path.of(""), "Папка недоступна")` | title `TITLE_BOTH_FAILED`; lines `["Папка недоступна"]` | REQ-001.24 |
| 7 | `` `message has no actions`() `` | `message("Не удалось сделать снимок", listOf("x"))` | reveal null; saveAs false | — |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.NotificationModelsTest"` | 7 тестов |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная проверка окна — шаги 5–8 раздела 12 TASK-015.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–4 выполнены; контракты — как в разделе 6.
- [x] Один экземпляр `JWindow` на всё время работы.
- [x] 7 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать `TrayIcon.displayMessage` и toast Windows.
- Не использовать Material3 и `application {}`.
- Не вызывать `reveal` на EDT.

## 15. Ловушки и подсказки

- `ComposePanel` нужно добавить в окно **до** `addNotify()`.
- `JWindow` с `focusableWindowState = false` получает клики мыши — этого достаточно для кнопки и клика по телу.
- `Path.of("")`.toString() — пустая строка (признак «папка неизвестна»).
- `Surface.clickable` перехватывает клик и под кнопкой: у `TextButton` свой обработчик, он срабатывает первым,
  клик по телу не вызывается.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-30 | Review | Реализованы NotificationModel/NotificationModels, NotificationContent, NotificationWindow, ExplorerLauncher и 7 тестов NotificationModelsTest. `gradlew build` — BUILD SUCCESSFUL, 7/7 тестов. Отклонений нет; в fromOutcome строка папки для пустого Path обрабатывается по таблице 8.1. Ручная проверка окна — в TASK-015. TASK-010 переведена в Done по указанию пользователя. |

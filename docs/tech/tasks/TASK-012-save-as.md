# TASK-012: Реализовать «Сохранить как…» (нативный диалог, .png, подтверждение перезаписи, последняя папка)

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.36–.41, .55; US-006 AC-1–AC-10 |
| ADR | ADR-002 |
| Зависит от | TASK-003, TASK-004, TASK-005, TASK-009, TASK-010 |
| Блокирует | TASK-015 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Действие «Сохранить как…» для последнего снимка:
1. открыть стандартный диалог сохранения Windows с фильтром `PNG (*.png)` и предзаполненным именем;
2. дописать `.png`;
3. запросить подтверждение, если файл уже есть;
4. записать PNG атомарно и запомнить папку.

## 2. Контекст

- Диалог — `GetSaveFileNameW` (Comdlg32) через JNA, без hook-процедуры. В таком режиме Windows показывает современный
  диалог Проводника. Подтверждение перезаписи — `MessageBoxW` (флаг `OFN_OVERWRITEPROMPT` **не** используется: имя
  меняется уже после диалога, при дописывании `.png`).
- Модальные нативные диалоги блокируют вызывающий поток. Поэтому всё действие выполняется на `fcs-dialog`, а не на EDT.
- Снимок фиксируется в момент вызова `run()` (TDD-001 §17).
- Логика (`SaveAsAction`) получает диалог и подтверждение как функции. Так она тестируется без окон.

**Выдержки из требований (дословно):**

> **REQ-001.38** Действие «Сохранить как…» должно открывать стандартный диалог сохранения файла Windows с предзаполненным именем по шаблону REQ-001.28 и фильтром `PNG (*.png)`. Начальная папка диалога — последняя папка, в которую пользователь сохранял через «Сохранить как…». Если такой папки ещё нет или она недоступна — директория по умолчанию.
>
> **REQ-001.39** Файл всегда сохраняется в формате PNG. Если имя, введённое пользователем, не заканчивается на `.png` (без учёта регистра), система должна дописать `.png`.
>
> **REQ-001.40** Если выбранный файл уже существует, система должна запросить подтверждение перезаписи.
>
> **REQ-001.41** Действие «Сохранить как…» должно сохранять копию снимка и не должно изменять директорию по умолчанию и ранее автосохранённый файл.
>
> **REQ-001.55** Система должна запоминать последнюю папку «Сохранить как…» между перезапусками приложения.
>
> **US-006 AC-10** Given диалог «Сохранить как» открыт When пользователь нажимает «Отмена» Then файлы не создаются, последняя папка «Сохранить как» не меняется, последний снимок остаётся доступен для повторного «Сохранить как…».

## 3. Предусловия

- TASK-003 (`ScreenshotFileNames`, `SaveAsFileName`), TASK-004 (`SettingsStore`, `AppSettings.lastSaveAsDirectory`),
  TASK-005 (`Comdlg32Api`, `OPENFILENAME`, `User32Api.MessageBox`, `WinConst`), TASK-009 (`PngFileWriter`, `IoErrorMessages`),
  TASK-010 (`LastScreenshotHolder`, `CaptureNotifier`).

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/WindowsSaveDialog.kt` | Создать | диалог |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/WindowsMessageBox.kt` | Создать | MessageBox |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SaveAsAction.kt` | Создать | логика |
| 4 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SaveAsActionTest.kt` | Создать | unit |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** GetSaveFileNameW. Вызывать на fcs-dialog. Не бросает. */
internal class WindowsSaveDialog(private val comdlg32: Comdlg32Api = Comdlg32Api.INSTANCE) {
    /** Путь, выбранный пользователем (как вернул диалог), или null — «Отмена»/ошибка. */
    fun show(initialDirectory: java.nio.file.Path, suggestedName: String): java.nio.file.Path? = TODO()

    companion object {
        const val TITLE: String = "Сохранить как"
        const val FILTER: String = "PNG (*.png)\u0000*.png\u0000\u0000"
    }
}

/** MessageBoxW. Вызывать на fcs-dialog. */
internal class WindowsMessageBox(private val user32: User32Api = User32Api.INSTANCE) {
    /** true — пользователь выбрал «Да». */
    fun confirmOverwrite(file: java.nio.file.Path): Boolean = TODO()
    /** Сообщение об ошибке с одной кнопкой OK. */
    fun showError(caption: String, text: String): Unit = TODO()

    companion object {
        const val MB_OK: Int = 0x0
        const val MB_ICONERROR: Int = 0x10
        const val OVERWRITE_CAPTION: String = "Сохранить как"
    }
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/** «Сохранить как…». run() — с любого потока, возвращается сразу. */
internal class SaveAsAction(
    private val lastScreenshot: LastScreenshotHolder,
    private val settings: SettingsStore,
    private val showDialog: (initialDirectory: java.nio.file.Path, suggestedName: String) -> java.nio.file.Path?,
    private val confirmOverwrite: (java.nio.file.Path) -> Boolean,
    private val writer: PngFileWriter,
    private val notifier: CaptureNotifier,
    private val dialogExecutor: java.util.concurrent.Executor,
    private val uiExecutor: java.util.concurrent.Executor,
) {
    fun run(): Unit = TODO()
    /** lastSaveAsDirectory, если это существующая папка; иначе defaultDirectory. */
    fun initialDirectory(s: AppSettings): java.nio.file.Path = TODO()

    companion object { const val SAVE_FAILED_TITLE: String = "Не удалось сохранить снимок" }
}
```

## 7. Пошаговая реализация

1. `WindowsMessageBox` (8.2), `WindowsSaveDialog` (8.1).
2. `SaveAsAction` (8.3) + `SaveAsActionTest`.
3. Ручная проверка — в TASK-015.

## 8. Алгоритм и логика

### 8.1. WindowsSaveDialog.show

```
filterChars = FILTER.toCharArray()
filterMem = Memory(filterChars.size * 2L).apply { write(0, filterChars, 0, filterChars.size) }
fileMem = Memory(WinConst.SAVE_DIALOG_BUFFER_CHARS * 2L).apply { clear() }
nameChars = suggestedName.toCharArray(); fileMem.write(0, nameChars, 0, nameChars.size)
ofn = OPENFILENAME().apply {
  hwndOwner = null; lpstrFilter = filterMem; nFilterIndex = 1
  lpstrFile = fileMem; nMaxFile = WinConst.SAVE_DIALOG_BUFFER_CHARS
  lpstrInitialDir = WString(initialDirectory.toString()); lpstrTitle = WString(TITLE)
  Flags = OFN_EXPLORER or OFN_PATHMUSTEXIST or OFN_HIDEREADONLY or OFN_NOCHANGEDIR
}
if (comdlg32.GetSaveFileName(ofn)) return Path.of(fileMem.getWideString(0))
code = comdlg32.CommDlgExtendedError()
if (code != 0) log.warn("GetSaveFileName failed, CommDlgExtendedError={}", code)
return null
```

### 8.2. WindowsMessageBox

`confirmOverwrite(file)`: `user32.MessageBox(null, "Файл «${file.fileName}» уже существует.\nЗаменить его?", OVERWRITE_CAPTION, MB_YESNO or MB_ICONWARNING or MB_TOPMOST or MB_SETFOREGROUND) == IDYES`.
`showError(caption, text)`: `MessageBox(null, text, caption, MB_OK or MB_ICONERROR or MB_TOPMOST or MB_SETFOREGROUND)`.

### 8.3. SaveAsAction.run

```
shot = lastScreenshot.get() ?: run { log.debug("Save as: no screenshot"); return }
if (!inProgress.compareAndSet(false, true)) { log.debug("Save as already open"); return }
dialogExecutor.execute {
  var target: Path? = null
  try {
    s = settings.current
    chosen = showDialog(initialDirectory(s), ScreenshotFileNames.candidate(shot.capturedAt, 0)) ?: return@execute
    target = SaveAsFileName.normalize(chosen)
    if (Files.exists(target) && !confirmOverwrite(target)) return@execute
    writer.writeAtomically(shot.image, target, replaceExisting = true)
    settings.update { it.copy(lastSaveAsDirectory = target.parent.toString()) }
    log.info("Saved as {}", target)
  } catch (e: IOException) {
    log.error("Save as failed: {}", e.toString())
    lines = listOfNotNull(IoErrorMessages.describe(e), target?.parent?.toString())
    uiExecutor.execute { notifier.showMessage(SAVE_FAILED_TITLE, lines) }
  } finally { inProgress.set(false) }
}
```
`initialDirectory(s)`: `last = s.lastSaveAsDirectory?.let { runCatching { Path.of(it) }.getOrNull() }`;
`if (last != null && Files.isDirectory(last)) last else Path.of(s.defaultDirectory)`.

**Пример:** `capturedAt = 2026-10-18 20:00:48`, `lastSaveAsDirectory = null`, `defaultDirectory = C:\…\Screenshots` →
диалог в `C:\…\Screenshots` с именем `Screenshot_2026-10-18_20-00-48.png`. Пользователь выбрал `D:\Projects\Bug-42\login-error` →
файл `D:\Projects\Bug-42\login-error.png`, `lastSaveAsDirectory = "D:\Projects\Bug-42"`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | Нет снимка | ничего | `DEBUG` | пункт недоступен (TASK-015) |
| 2 | «Отмена» | ничего не меняется | — | — |
| 3 | Файл есть, «Нет» | файл не меняется, папка не запоминается | — | — |
| 4 | Ошибка записи | уведомление | `ERROR "Save as failed: …"` | `"Не удалось сохранить снимок"` + причина + папка |
| 5 | Повторный вызов при открытом диалоге | игнор | `DEBUG` | — |
| 6 | Новый захват при открытом диалоге | сохраняется снимок на момент `run()` | — | — |

## 10. Константы и тексты

Дословно: заголовок диалога `"Сохранить как"`; фильтр `"PNG (*.png)"`; текст подтверждения
`"Файл «<имя>» уже существует.\nЗаменить его?"` (кавычки-«ёлочки»), заголовок `"Сохранить как"`;
уведомление `"Не удалось сохранить снимок"`.

## 11. Тесты

`SaveAsActionTest`: `direct = Executor { it.run() }`; `store = SettingsStore(SettingsRepository(tempDir/"settings.json") { tempDir/"default" })`,
`tempDir/"default"` создана; `holder = LastScreenshotHolder(direct)`; `shot1` — 40×30, `capturedAt = 2026-10-18 20:00:48`;
`notifier = mockk(relaxed = true)`; `dialogCalls` записывает аргументы; `writer = PngFileWriter()`.

| # | Метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `` `no screenshot does nothing`() `` | holder пуст | `run()` | диалог не вызван | REQ-001.42 |
| 2 | `` `first open uses default dir and template name`() `` | `holder.set(shot1)`, диалог → null | `run()` | аргументы `(tempDir/"default", "Screenshot_2026-10-18_20-00-48.png")` | US-006 AC-1 |
| 3 | `` `saves normalized png and remembers folder`() `` | диалог → `tempDir/"Bug-42"/"login-error"` (папка создана) | `run()` | есть `Bug-42/login-error.png` (читается ImageIO 40×30); `store.current.lastSaveAsDirectory == (tempDir/"Bug-42").toString()`; `defaultDirectory` прежний | US-006 AC-2, REQ-001.41, .55 |
| 4 | `` `reopens in last folder`() `` | после теста-сценария 3 второй `run()` | `run()` | 2-й вызов диалога с `tempDir/"Bug-42"` | US-006 AC-3 |
| 5 | `` `missing last folder falls back to default`() `` | `store.update { it.copy(lastSaveAsDirectory = (tempDir/"gone").toString()) }` | `run()` | аргумент `tempDir/"default"` | US-006 AC-5 |
| 6 | `` `jpg name gets png appended`() `` | диалог → `…/login.jpg` | `run()` | есть `login.jpg.png` | US-006 AC-6 |
| 7 | `` `declined overwrite keeps file`() `` | `x.png` = `"old"`, диалог → `x.png`, confirm → false | `run()` | `x.png` = `"old"`; `lastSaveAsDirectory == null` | US-006 AC-7 |
| 8 | `` `accepted overwrite replaces file`() `` | то же, confirm → true | `run()` | `ImageIO.read(x.png)` 40×30 | REQ-001.40 |
| 9 | `` `cancel changes nothing`() `` | диалог → null | `run()` | файлов в `tempDir/"default"` нет; `lastSaveAsDirectory == null` | US-006 AC-10 |
| 10 | `` `uses latest screenshot`() `` | `set(shot1)`, `set(shot2 80×60)` | `run()` | сохранённый файл 80×60 | US-006 AC-9 |
| 11 | `` `write failure shows notification`() `` | диалог → `tempDir/"missing"/"x"` (папки нет) | `run()` | `notifier.showMessage("Не удалось сохранить снимок", listOf("Папка недоступна", (tempDir/"missing").toString()))` | REQ-001.31 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.SaveAsActionTest"` | 11 тестов |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная проверка — шаги 9–13 раздела 12 TASK-015.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–3 выполнены; контракты — как в разделе 6.
- [x] Диалоги вызываются только внутри `dialogExecutor`.
- [x] 11 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать `FileDialog`, `JFileChooser`, `JOptionPane`.
- Не менять `defaultDirectory`.
- Не показывать уведомление об успехе «Сохранить как» (не требуется).

## 15. Ловушки и подсказки

- Строка фильтра содержит символы `\u0000`. `WString` для неё не подходит — только `Memory` + `write(CharArray)`.
- Буфер имени файла должен быть заполнен нулями (`clear()`) — признак конца строки.
- `Memory` должна жить до возврата из `GetSaveFileName` — держать в локальной переменной (не создавать inline в apply
  без ссылки).
- В тесте 11 `createTempFile` бросает `NoSuchFileException` → «Папка недоступна».

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-30 | Review | Реализованы WindowsSaveDialog, WindowsMessageBox, SaveAsAction и 11 тестов SaveAsActionTest. `gradlew build` — BUILD SUCCESSFUL, 11/11. Отклонений нет. Ручная проверка — шаги 9–13 TASK-015. TASK-011 переведена в Done по указанию пользователя. |

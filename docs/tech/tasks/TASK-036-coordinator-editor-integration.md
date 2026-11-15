# TASK-036: Встроить редактор в `CaptureCoordinator` и добавить `SaveAsAction.runFor`

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-004 §5, §6, §7, §9, §12 |
| Требования | REQ-003.2 – 3.6, 3.13, 3.14, 3.23, 3.37 – 3.39, 3.41 – 3.44, N4; US-011 AC-1 – AC-7, AC-9, AC-10, US-013 AC-1 |
| ADR | ADR-006 |
| Зависит от | TASK-031, TASK-033 |
| Блокирует | TASK-037 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Научить `CaptureCoordinator` при включённой настройке `openInEditor` передавать готовый снимок редактору вместо
немедленной записи в буфер и на диск, а результат редактора («Готово», «Копировать», «Сохранить как…», «Отмена»)
обрабатывать существующим конвейером. Окна редактора ещё нет (TASK-038): в приложение подключается временная
реализация `PassThroughEditor`, которая сразу возвращает снимок без изменений.

## 2. Контекст

`CaptureCoordinator` (пакет `capture`) — сценарии захвата. `trigger(mode)` ставит флаг `busy` (повторное нажатие
игнорируется), берёт снимок настроек `s = settings()` и на потоке `fcs-capture` выполняет `runCapture`. Готовое
изображение уходит в private `finish(image, mode, capturedAt, s, start)`: буфер → последний снимок → лог `Captured` →
`busy = false` → автосохранение на `fcs-save` → уведомление на EDT. Режимы `REGION` и `MULTI_REGION` сначала
показывают оверлей на EDT, результат возвращается в `onRegion` / `onMultiRegion`.

Новое поведение (TDD-004 §6):
1. Если `s.openInEditor == true`, сразу после `monitors.desktop()` запоминается рабочая область монитора под курсором:
   `editorArea = monitors.monitorUnderCursor(desktop).workArea` (окно редактора откроется на этом мониторе).
2. Везде, где раньше вызывался `finish(...)`, вызывается новый `complete(...)`: при `editorArea == null` — `finish`,
   иначе — `openEditor(EditorRequest(...))`.
3. `openEditor`: флаг `editorOpen = true`, `busy` остаётся `true`; на EDT — `editor.open(request, EditorSession(request))`.
4. `EditorSession` (внутренний класс) реализует `EditorHost` — обратные вызовы окна редактора:
   - `done(snapshot)` — на `fcs-capture`: `EditorRenderer.render(snapshot)` → `editorOpen = false` → существующий
     `finish(image, mode, capturedAt, settings(), doneNs)` (текущие настройки, время захвата — исходное);
   - `copy(snapshot, onResult)` — рендер → `clipboard.write` → последний снимок → уведомление → `onResult(ok)` на EDT;
   - `saveAs(snapshot, onSaved)` — рендер → лямбда `saveAs(Screenshot, onSaved)` (в приложении —
     `SaveAsAction.runFor`);
   - `cancel()` — `editorOpen = false`, `busy = false`.
   `done` и `cancel` срабатывают один раз; после них всё игнорируется.
5. Нажатие клавиши захвата при `editorOpen == true` → `trigger` возвращает `false` и вызывает на EDT
   `editor.bringToFront()`.

`SaveAsAction` (пакет `ui`) — «Сохранить как…» последнего снимка (`run()`). Добавляется `runFor(shot, onSaved)` —
то же, но для переданного снимка; после успешной записи файла он становится последним снимком и вызывается `onSaved`.

**Выдержки из требований (дословно):**

> **REQ-003.2** Если флажок выключен, обработка снимка должна полностью соответствовать REQ-001 и REQ-002: буфер, автосохранение, уведомление, последний снимок. Редактор не открывается.
>
> **REQ-003.3** Если флажок включён, после успешного захвата в любом режиме система должна открыть снимок в редакторе. До действия пользователя в редакторе (раздел 3.6) система не должна менять буфер обмена, создавать файлы, менять последний снимок (REQ-001.36) и показывать уведомление об успешном захвате.
>
> **REQ-003.4** Изменение флажка применяется со следующего захвата. Уже открытый редактор не закрывается.
>
> **REQ-003.5** Отменённый захват (REQ-001.17, REQ-001.18, REQ-002.18) не открывает редактор.
>
> **REQ-003.6** Если в настройках включено «Включать указатель мыши в снимок» …, указатель рисуется на снимке до открытия редактора и редактируется как часть изображения.
>
> **REQ-003.13** Пока редактор открыт, система должна игнорировать глобальные горячие клавиши захвата (расширение REQ-001.5). При нажатии такой клавиши окно редактора выводится на передний план и показывается подсказка «Завершите редактирование снимка».
>
> **REQ-003.14** Одновременно может быть открыт только один редактор.
>
> **REQ-003.23** Для снимка режима «Несколько областей» редактор должен открываться с раскладкой, рассчитанной по REQ-002.20. Каждая область — отдельный фрагмент, пустоты залиты цветом REQ-002.21. …
>
> **REQ-003.37** «Готово» … формирует итоговое изображение, закрывает редактор и передаёт изображение в конвейер REQ-001: буфер (REQ-001.23–26), автосохранение (REQ-001.27–35, с учётом настройки REQ-001.35), последний снимок (REQ-001.36), уведомление (REQ-001.34, REQ-001.54).
>
> **REQ-003.38** «Копировать» … помещает текущее итоговое изображение в буфер обмена (REQ-001.23–25) и обновляет последний снимок. Файл не сохраняется. Редактор остаётся открытым, показывается уведомление «Скопировано в буфер обмена».
>
> **REQ-003.39** «Сохранить как…» … открывает диалог по правилам REQ-001.38–41 и REQ-001.55 и сохраняет текущее итоговое изображение. Последний снимок обновляется. Буфер и директория по умолчанию не меняются. Редактор остаётся открытым.
>
> **REQ-003.41** При «Отмене» буфер обмена, файлы и последний снимок не меняются, кроме результатов уже выполненных «Копировать» и «Сохранить как…».
>
> **REQ-003.42** Имя автосохраняемого файла (REQ-001.28) строится по времени захвата, а не по времени нажатия «Готово».
>
> **REQ-003.43** «Готово» без изменений даёт изображение, идентичное снимку, который был бы получен при выключенном редакторе.
>
> **REQ-003.44** Если запись в буфер или на диск по «Готово» завершилась ошибкой, применяется обработка REQ-001.25 и REQ-001.31. Отредактированное изображение остаётся последним снимком …
>
> **REQ-003.N4** От «Готово» до появления изображения в буфере ≤ 500 мс для 3 × 4K и 100 штрихов.

## 3. Предусловия

- TASK-031: `AppSettings.openInEditor: Boolean`.
- TASK-033: `editor/EditorSnapshot.kt` (`EditorSnapshot.initial(base, sources, fillRgb)`, свойства `width`, `height`),
  `editor/EditorRenderer.kt` (`render(snapshot): BufferedImage`).
- TASK-032: `editor/Stroke.kt` (нужен в тестах).
- Существуют (см. код): `capture/CaptureCoordinator.kt` (конструктор из 14 параметров, `trigger`, `runCapture`,
  `captureArea`, `onRegion`, `onMultiRegion`, `crop`, `finish`, `fail`, `CAPTURE_FAILED_TITLE`),
  `capture/ClipboardResult.kt` (`Success`, `Failure(reason)`), `capture/CaptureNotifier.kt` (`showOutcome`,
  `showMessage(title, lines)`), `capture/LastScreenshotHolder.kt` (`get`, `set`), `domain/MonitorInfo.kt`
  (`bounds`, `workArea`), `settings/MultiRegionSettings.kt` (`fillRgb()`, `DEFAULT_FILL_RGB = 0x808080`),
  `ui/SaveAsAction.kt`, `app/FastCowScreenshotApp.kt`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/EditorRequest.kt` | Создать | `EditorRequest` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/EditorHost.kt` | Создать | `EditorHost` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/ScreenshotEditor.kt` | Создать | `ScreenshotEditor` |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/CaptureCoordinator.kt` | Изменить | параметры `editor`, `saveAs`; `editorOpen`; `complete`, `openEditor`, `EditorSession` |
| 5 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SaveAsAction.kt` | Изменить | `runFor`, общий `start` |
| 6 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/app/PassThroughEditor.kt` | Создать | временная реализация |
| 7 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/app/FastCowScreenshotApp.kt` | Изменить | аргументы конструктора координатора |
| 8 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/capture/CaptureCoordinatorTest.kt` | Изменить | фабрика `coordinator`, фейки, тесты 1–16 |
| 9 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SaveAsActionTest.kt` | Изменить | тесты 17–19 |

**Файлы, которые изменять нельзя:** все остальные (в том числе `editor/*`, `SettingsWindow.kt`, `NotificationWindow.kt`).

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
// capture/EditorRequest.kt
package by.sparkyanvil.fastcowscreenshot.capture

/**
 * Снимок для открытия в редакторе (TDD-004 §5). Неизменяем.
 * @property image итог захвата TYPE_INT_RGB (с указателем мыши, если он включён); редактор его не изменяет.
 * @property mode режим захвата.
 * @property capturedAt время нажатия горячей клавиши (для имени файла, REQ-003.42).
 * @property fragments прямоугольники фрагментов на image: для MULTI_REGION — места регионов после склейки,
 *   иначе один PixelRect(0, 0, image.width, image.height).
 * @property fillRgb цвет пустот 0xRRGGBB: для MULTI_REGION — s.multiRegion.fillRgb(), иначе 0x808080.
 * @property workArea рабочая область монитора под курсором в момент нажатия клавиши (физические px).
 * @property startNs System.nanoTime() начала отсчёта REQ-003.N1.
 */
internal data class EditorRequest(
    val image: BufferedImage,
    val mode: CaptureMode,
    val capturedAt: LocalDateTime,
    val fragments: List<PixelRect>,
    val fillRgb: Int,
    val workArea: PixelRect,
    val startNs: Long,
)
```

```kotlin
// capture/EditorHost.kt
package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.editor.EditorSnapshot

/** Обратные вызовы редактора для координатора. Вызываются с EDT; возвращаются сразу. */
internal interface EditorHost {
    /** «Готово»: итог в буфер, автосохранение, последний снимок, уведомление. Сессия завершается. */
    fun done(result: EditorSnapshot)

    /** «Копировать»: итог в буфер и последний снимок. onResult(true — в буфере) вызывается на EDT. */
    fun copy(result: EditorSnapshot, onResult: (Boolean) -> Unit)

    /** «Сохранить как…». onSaved вызывается на EDT после успешной записи файла. */
    fun saveAs(result: EditorSnapshot, onSaved: () -> Unit)

    /** «Отмена»: сессия завершается без результата. */
    fun cancel()
}
```

```kotlin
// capture/ScreenshotEditor.kt
package by.sparkyanvil.fastcowscreenshot.capture

/** Окно редактора (реализация — ui.EditorWindow, TASK-038). Все методы вызываются на EDT. */
internal interface ScreenshotEditor {
    /** Открывает редактор. Результат — через host. Если окно открыть нельзя — бросает исключение. */
    fun open(request: EditorRequest, host: EditorHost)

    /** Выводит открытый редактор на передний план с подсказкой «Завершите редактирование снимка». Нет редактора — ничего. */
    fun bringToFront()
}
```

`CaptureCoordinator` — новый конструктор (новые параметры `editor` после `multiRegionSelector` и `saveAs` после
`lastScreenshot`; остальные без изменений):

```kotlin
internal class CaptureCoordinator(
    private val monitors: MonitorProvider,
    private val capturer: ScreenCapturer,
    private val cursor: CursorService,
    private val clipboard: ClipboardWriter,
    private val saver: ScreenshotSaver,
    private val regionSelector: RegionSelector,
    private val multiRegionSelector: MultiRegionSelector,
    private val editor: ScreenshotEditor,
    private val notifier: CaptureNotifier,
    private val lastScreenshot: LastScreenshotHolder,
    private val saveAs: (Screenshot, () -> Unit) -> Unit,
    private val settings: () -> AppSettings,
    private val clock: () -> LocalDateTime,
    private val captureExecutor: Executor,
    private val saveExecutor: Executor,
    private val uiExecutor: Executor,
) {
    /** true, пока открыт редактор (от openEditor до done/cancel). */
    val isEditorOpen: Boolean get() = TODO()
    // остальные члены — раздел 8
}
```

`SaveAsAction` — новый метод:

```kotlin
/**
 * «Сохранить как…» для снимка из редактора (REQ-003.39). С любого потока, возвращается сразу.
 * Диалог и запись — как run(). После успешной записи: lastScreenshot.set(shot), затем uiExecutor.execute { onSaved() }.
 * Если диалог уже открыт — ничего не делает (DEBUG "Save as already open").
 */
fun runFor(shot: Screenshot, onSaved: () -> Unit)
```

```kotlin
// app/PassThroughEditor.kt
package by.sparkyanvil.fastcowscreenshot.app

/** Временная реализация ScreenshotEditor до TASK-038: сразу «Готово» без изменений. */
internal class PassThroughEditor : ScreenshotEditor {
    override fun open(request: EditorRequest, host: EditorHost) =
        host.done(EditorSnapshot.initial(request.image, request.fragments, request.fillRgb))

    override fun bringToFront() = Unit
}
```

## 7. Пошаговая реализация

### Шаг 1. Контракты `capture`
- Создать `EditorRequest.kt`, `EditorHost.kt`, `ScreenshotEditor.kt` по разделу 6 (импорты: `java.awt.image.BufferedImage`,
  `java.time.LocalDateTime`, `…domain.CaptureMode`, `…domain.PixelRect`, `…editor.EditorSnapshot`).
- **Результат:** компиляция.

### Шаг 2. `SaveAsAction.runFor`
- Тело `run()` после проверки `shot == null` перенести в `private fun start(shot: Screenshot, onSaved: (() -> Unit)?)`;
  `run()` вызывает `start(shot, null)`, `runFor(shot, onSaved)` вызывает `start(shot, onSaved)`.
- В `start` сразу после `log.info("Saved as {}", normalized)` добавить:

```kotlin
if (onSaved != null) {
    lastScreenshot.set(shot)
    uiExecutor.execute { onSaved() }
}
```

- KDoc класса: `/** «Сохранить как…». run() и runFor() — с любого потока, возвращаются сразу. */`.
- **Результат:** компиляция, существующие `SaveAsActionTest` проходят.

### Шаг 3. `CaptureCoordinator`
- Конструктор — раздел 6. Поле `private val editorOpen = AtomicBoolean(false)`, свойство `isEditorOpen`.
- Изменения `trigger`, `runCapture`, `onRegion`, `onMultiRegion`; новые `complete`, `wholeRequest`, `openEditor`,
  внутренний класс `EditorSession`; константы — раздел 8 и 10. Метод `finish` и `crop` **не менять**.
- **Результат:** `.\gradlew.bat compileKotlin` падает только в `FastCowScreenshotApp.kt` (нет аргументов) — шаг 4.

### Шаг 4. `PassThroughEditor` и `FastCowScreenshotApp`
- Создать `app/PassThroughEditor.kt` (раздел 6; импорты `…capture.EditorHost`, `…capture.EditorRequest`,
  `…capture.ScreenshotEditor`, `…editor.EditorSnapshot`).
- В `FastCowScreenshotApp`, в вызове `CaptureCoordinator(...)`: после строки
  `MultiRegionOverlay({ hotkeys.unregisterAll() }, { restoreHotkeys() }),` добавить `PassThroughEditor(),`;
  после строки `last,` добавить `{ shot, onSaved -> saveAsAction.runFor(shot, onSaved) },`.
- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 5. Тесты — раздел 11. **Результат:** `.\gradlew.bat build` проходит.

### Шаг 6. Ручная проверка — раздел 12.

## 8. Алгоритм и логика

**`trigger`** — заменить блок отказа:

```kotlin
if (!busy.compareAndSet(false, true)) {
    if (editorOpen.get()) {
        log.debug("Hotkey ignored: editor is open")
        uiExecutor.execute { editor.bringToFront() }
    } else {
        log.debug("Hotkey ignored: capture in progress")
    }
    return false
}
```

**`runCapture`** — после `val desktop = monitors.desktop()`:

```kotlin
val editorArea = if (s.openInEditor) monitors.monitorUnderCursor(desktop).workArea else null
```

и ветки `when`:

```kotlin
CaptureMode.MONITOR_UNDER_CURSOR -> {
    val area = monitors.monitorUnderCursor(desktop).bounds
    val image = captureArea(area, desktop, snap)
    complete(image, mode, capturedAt, s, start, editorArea?.let { wholeRequest(image, mode, capturedAt, it, start) })
}
CaptureMode.ALL_MONITORS -> {
    val image = captureArea(desktop.bounds, desktop, snap)
    complete(image, mode, capturedAt, s, start, editorArea?.let { wholeRequest(image, mode, capturedAt, it, start) })
}
```

В ветках `REGION` и `MULTI_REGION` передать `editorArea` последним аргументом в `onRegion(...)` / `onMultiRegion(...)`.

**`onRegion`** — новый последний параметр `editorArea: PixelRect?`. Первая строка метода (до `try`):
`val selectedNs = System.nanoTime()`. Вызов `finish(image, mode, capturedAt, s, start)` заменить на:

```kotlin
complete(image, mode, capturedAt, s, start, editorArea?.let { wholeRequest(image, mode, capturedAt, it, selectedNs) })
```

**`onMultiRegion`** — новый последний параметр `editorArea: PixelRect?`. Вызов
`finish(image, CaptureMode.MULTI_REGION, capturedAt, s, start)` заменить на:

```kotlin
val request = editorArea?.let { area ->
    EditorRequest(
        image, CaptureMode.MULTI_REGION, capturedAt,
        layout.placements.map { PixelRect(it.targetLeft, it.targetTop, it.source.width, it.source.height) },
        s.multiRegion.fillRgb(), area, confirmNs,
    )
}
complete(image, CaptureMode.MULTI_REGION, capturedAt, s, start, request)
```

Строку лога `Multi-region confirm-to-clipboard` оставить как есть.

**Новые private-методы:**

```kotlin
/** Поток: fcs-capture. request == null — обычный конвейер, иначе — редактор. */
private fun complete(image: BufferedImage, mode: CaptureMode, capturedAt: LocalDateTime, s: AppSettings,
                     start: Long, request: EditorRequest?) {
    if (request == null) finish(image, mode, capturedAt, s, start) else openEditor(request)
}

private fun wholeRequest(image: BufferedImage, mode: CaptureMode, capturedAt: LocalDateTime,
                         workArea: PixelRect, startNs: Long): EditorRequest =
    EditorRequest(image, mode, capturedAt, listOf(PixelRect(0, 0, image.width, image.height)),
        MultiRegionSettings.DEFAULT_FILL_RGB, workArea, startNs)

/** Поток: fcs-capture. busy остаётся true до done/cancel. */
private fun openEditor(request: EditorRequest) {
    editorOpen.set(true)
    log.info("Editor opening {} {}x{}", request.mode, request.image.width, request.image.height)
    val session = EditorSession(request)
    uiExecutor.execute {
        try {
            editor.open(request, session)
        } catch (e: Exception) {
            log.error("Editor failed to open", e)
            session.fallback()
        }
    }
}

private fun releaseEditor() {
    editorOpen.set(false)
    busy.set(false)
}

private fun elapsedMs(startNs: Long): Long = (System.nanoTime() - startNs) / 1_000_000
```

**`EditorSession`** — `private inner class EditorSession(private val request: EditorRequest) : EditorHost`, поле
`private val closed = AtomicBoolean(false)`:

```
done(result):
    if (!closed.compareAndSet(false, true)) return
    doneNs = System.nanoTime()
    try captureExecutor.execute {
        try {
            image = EditorRenderer.render(result)
            log.info("Editor done: rendered {}x{} in {} ms", image.width, image.height, elapsedMs(doneNs))
            editorOpen.set(false)
            finish(image, request.mode, request.capturedAt, settings(), doneNs)
        } catch (e: Exception) { editorOpen.set(false); fail(e) }
    } catch (e: RejectedExecutionException) { releaseEditor() }

copy(result, onResult):
    if (closed.get()) return
    try captureExecutor.execute {
        ok = try {
            image = EditorRenderer.render(result)
            clip = clipboard.write(image)
            lastScreenshot.set(Screenshot(image, request.capturedAt, request.mode))
            if (clip is ClipboardResult.Success) { log.info("Editor copied {}x{}", image.width, image.height); true }
            else { log.warn("Editor copy: clipboard failed"); false }
        } catch (e: Exception) { log.error("Editor copy failed", e); false }
        uiExecutor.execute {
            notifier.showMessage(if (ok) EDITOR_COPIED_TITLE else EDITOR_COPY_FAILED_TITLE, emptyList())
            onResult(ok)
        }
    } catch (e: RejectedExecutionException) { log.warn("Editor copy rejected: {}", e.message) }

saveAs(result, onSaved):
    if (closed.get()) return
    try captureExecutor.execute {
        try {
            image = EditorRenderer.render(result)
            saveAs(Screenshot(image, request.capturedAt, request.mode), onSaved)   // поле координатора
        } catch (e: Exception) {
            log.error("Editor save-as failed", e)
            uiExecutor.execute { notifier.showMessage(EDITOR_SAVE_FAILED_TITLE, listOf(e.message ?: e.javaClass.simpleName)) }
        }
    } catch (e: RejectedExecutionException) { log.warn("Editor save-as rejected: {}", e.message) }

cancel():
    if (!closed.compareAndSet(false, true)) return
    log.info("Editor cancelled")
    releaseEditor()

fallback():        // открыть окно не удалось — отдать исходный снимок обычным конвейером
    if (!closed.compareAndSet(false, true)) return
    try captureExecutor.execute {
        editorOpen.set(false)
        try { finish(request.image, request.mode, request.capturedAt, settings(), request.startNs) }
        catch (e: Exception) { fail(e) }
    } catch (e: RejectedExecutionException) { releaseEditor() }
```

Внутри `EditorSession` имя `saveAs` в `saveAs(Screenshot(...), onSaved)` — это поле координатора
(`this@CaptureCoordinator.saveAs`), а не метод сессии: пиши явно `this@CaptureCoordinator.saveAs(...)`.

**Пример (US-013 AC-1):** регионы `(0,0)–(100,50)` и `(500,300)–(700,400)` → `StitchLayout` 300×150, placements
`A → (0,0)`, `B → (100,50)` → `request.fragments = [PixelRect(0,0,100,50), PixelRect(100,50,200,100)]`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | `openInEditor == false` | как раньше, `monitorUnderCursor` для редактора не вызывается | — | как раньше |
| 2 | Захват отменён (оверлей вернул null) | редактор не открывается, `busy = false` | `"Region capture cancelled"` / `"Multi-region capture cancelled"` | — |
| 3 | `editor.open` бросил исключение | `fallback()`: исходный снимок через `finish` | `ERROR "Editor failed to open"` | обычное уведомление |
| 4 | Рендер в `done` упал | `editorOpen = false`, `fail(e)` | `ERROR "Capture failed"` | `"Не удалось сделать снимок"` |
| 5 | Буфер занят при `copy` | последний снимок обновлён, `onResult(false)` | `WARN "Editor copy: clipboard failed"` | `"Не удалось скопировать в буфер обмена"` |
| 6 | Рендер в `copy` упал | `onResult(false)` | `ERROR "Editor copy failed"` | `"Не удалось скопировать в буфер обмена"` |
| 7 | Рендер в `saveAs` упал | диалог не открывается | `ERROR "Editor save-as failed"` | `"Не удалось сохранить снимок"` + текст ошибки |
| 8 | Повторный `done` / `cancel` / `copy` после закрытия | игнорируется | — | — |
| 9 | Клавиша захвата при открытом редакторе | `trigger == false`, `bringToFront` на EDT | `DEBUG "Hotkey ignored: editor is open"` | окно вперёд (TASK-038) |
| 10 | Ошибка буфера/диска в `finish` после «Готово» | существующая обработка REQ-001.25/31 | существующий | существующий |
| 11 | `captureExecutor` отклонил задачу `done`/`fallback` | `releaseEditor()` | — | — |

## 10. Константы и тексты

| Имя | Тип | Значение | Где |
|---|---|---|---|
| `EDITOR_COPIED_TITLE` | `String` | `"Скопировано в буфер обмена"` | `CaptureCoordinator.companion` |
| `EDITOR_COPY_FAILED_TITLE` | `String` | `"Не удалось скопировать в буфер обмена"` | `CaptureCoordinator.companion` |
| `EDITOR_SAVE_FAILED_TITLE` | `String` | `"Не удалось сохранить снимок"` | `CaptureCoordinator.companion` |

## 11. Тесты

`CaptureCoordinatorTest.kt`. Добавить фейк и поля:

```kotlin
private class FakeEditor : ScreenshotEditor {
    var request: EditorRequest? = null
    var host: EditorHost? = null
    var opens = 0
    var fronts = 0
    var failOpen = false
    override fun open(request: EditorRequest, host: EditorHost) {
        opens++
        if (failOpen) throw IllegalStateException("no window")
        this.request = request
        this.host = host
    }
    override fun bringToFront() { fronts++ }
}

private val editor = FakeEditor()
private val saveAsShots = mutableListOf<Screenshot>()
private var saveAsCallback: (() -> Unit)? = null
private val editorOn = AppSettings(defaultDirectory = "C:\\s", openInEditor = true)
```

Фабрика:

```kotlin
private fun coordinator(
    settings: AppSettings = AppSettings(defaultDirectory = "C:\\s"),
    captureExecutor: Executor = direct,
    settingsProvider: (() -> AppSettings)? = null,
) = CaptureCoordinator(
    monitors, capturer, cursor, clipboard, saver, selector, multiSelector, editor, notifier, last,
    { shot, onSaved -> saveAsShots.add(shot); saveAsCallback = onSaved },
    settingsProvider ?: { settings }, { time }, captureExecutor, direct, direct,
)
```

Помощники: `initial(req) = EditorSnapshot.initial(req.image, req.fragments, req.fillRgb)`; `req()` =
`assertNotNull(editor.request)`; `host()` = `assertNotNull(editor.host)`; `written = slot<BufferedImage>()` с
`every { clipboard.write(capture(written)) } returns ClipboardResult.Success`.
Напоминание: `b.workArea = PixelRect(-100, 0, 100, 90)`, `desktop.bounds = PixelRect(-100, 0, 300, 100)`,
пиксель `FakeCapturer` = `((x + 1000) shl 8) or y` в координатах стола.

| # | Метод | Given / When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `editor disabled keeps pipeline`() `` | `coordinator().trigger(ALL_MONITORS)` | `editor.opens == 0`; `clipboard.write` 1 раз; `notifier.showOutcome` 1 раз | 3.2, US-011 AC-1 |
| 2 | `` `editor enabled opens editor instead of pipeline`() `` | `coordinator(editorOn).trigger(ALL_MONITORS)` | `opens == 1`; `req().image` 300×100; `req().fragments == [PixelRect(0,0,300,100)]`; `mode == ALL_MONITORS`; `capturedAt == time`; `workArea == b.workArea`; `fillRgb == 0x808080`; `clipboard.write` 0; `saver.save` 0; `showOutcome` 0; `last.get() == null`; `isBusy`; `isEditorOpen` | 3.3, US-011 AC-2 |
| 3 | `` `hotkey while editor open brings it to front`() `` | после №2: `trigger(MONITOR_UNDER_CURSOR)` | `false`; `editor.fronts == 1`; `capturer.calls == 1`; `opens == 1` | 3.13, 3.14, US-011 AC-9 |
| 4 | `` `done without changes delivers identical image`() `` | после №2: `host().done(initial(req()))` | `written.captured` 300×100, все пиксели равны `req().image`; `last.get()?.mode == ALL_MONITORS`, `capturedAt == time`; `saver.save(any(), time, Path.of("C:\\s"))`; `showOutcome` 1; `!isBusy`; `!isEditorOpen`; затем `trigger(ALL_MONITORS) == true` | 3.37, 3.42, 3.43, US-011 AC-4 |
| 5 | `` `done renders strokes`() `` | после №2: `done(initial(req()).copy(strokes = listOf(Stroke(0x00FF00, 4, listOf(StrokePoint(10f, 10f), StrokePoint(30f, 10f))))))` | `written.captured.getRGB(20, 10) and 0xFFFFFF == 0x00FF00` | 3.37, US-011 AC-3 |
| 6 | `` `done uses current autosave setting`() `` | `var current = editorOn`; `coordinator(settingsProvider = { current }).trigger(ALL_MONITORS)`; `current = editorOn.copy(autoSaveEnabled = false)`; `done(initial)` | `clipboard.write` 1; `saver.save` 0 раз; `showOutcome` 1 | TDD-004 §3 |
| 7 | `` `cancel touches nothing and frees coordinator`() `` | после №2: `host().cancel()` | `clipboard.write` 0; `saver.save` 0; `last.get() == null`; `!isBusy`; `!isEditorOpen`; `trigger(ALL_MONITORS) == true`, `fronts == 0`, `opens == 2` | 3.41, US-011 AC-7 |
| 8 | `` `copy writes clipboard and keeps editor open`() `` | после №2: `val results = mutableListOf<Boolean>()`; `host().copy(initial(req())) { results.add(it) }` | `clipboard.write` 1; `last.get()?.mode == ALL_MONITORS`; `verify { notifier.showMessage("Скопировано в буфер обмена", emptyList()) }`; `results == [true]`; `isBusy`; `isEditorOpen`; `saver.save` 0; `showOutcome` 0 | 3.38, US-011 AC-5 |
| 9 | `` `copy failure is reported`() `` | `clipboard.write` → `Failure("busy")`; после №2 `copy` | `showMessage("Не удалось скопировать в буфер обмена", emptyList())`; `results == [false]`; `last.get() != null` | 3.38, §9 №5 |
| 10 | `` `save as passes rendered image`() `` | после №2: `var saved = 0`; `host().saveAs(initial(req())) { saved++ }` | `saveAsShots.size == 1`; изображение 300×100; `capturedAt == time`; `mode == ALL_MONITORS`; `saved == 0`; `assertNotNull(saveAsCallback).invoke()` → `saved == 1`; `clipboard.write` 0; `isBusy` | 3.39, US-011 AC-6 |
| 11 | `` `multi region opens editor with stitched fragments`() `` | `coordinator(editorOn.copy(multiRegion = MultiRegionSettings(fillColor = "#FFFFFF")))`, `multiSelector.nextRects = [r(-100,0,10,10), r(150,50,20,10)]`, `trigger(MULTI_REGION)` | `req().image` 30×20; `req().fragments == [PixelRect(0,0,10,10), PixelRect(10,10,20,10)]`; `mode == MULTI_REGION`; `fillRgb == 0xFFFFFF`; `clipboard.write` 0 | 3.23, US-013 AC-1 |
| 12 | `` `cancelled region does not open editor`() `` | `coordinator(editorOn)`, `selector.nextRect = null`, `trigger(REGION)` | `opens == 0`; `!isBusy`; `!isEditorOpen` | 3.5, US-011 AC-10 |
| 13 | `` `region request contains cropped image`() `` | `coordinator(editorOn)`, `selector.nextRect = r(-50,10,20,5)`, `trigger(REGION)` | `req().image` 20×5; `getRGB(0,0) and 0xFFFFFF == ((-50 + 1000) shl 8) or 10`; `fragments == [PixelRect(0,0,20,5)]`; `workArea == b.workArea` | 3.3 |
| 14 | `` `editor open failure falls back to pipeline`() `` | `editor.failOpen = true`; `coordinator(editorOn).trigger(ALL_MONITORS)` | `clipboard.write` 1; `showOutcome` 1; `last.get()?.mode == ALL_MONITORS`; `!isBusy`; `!isEditorOpen` | §9 №3 |
| 15 | `` `done twice is ignored`() `` | после №2: `done(initial)`, `done(initial)`, `cancel()` | `clipboard.write` 1; `showOutcome` 1 | §9 №8 |
| 16 | `` `editor flag applies to next capture`() `` | `var current = editorOn`; `coordinator(settingsProvider = { current })`; `trigger(ALL)`; `current = editorOn.copy(openInEditor = false)` (редактор ещё открыт: `isEditorOpen`); `host().cancel()`; `trigger(ALL)` | `opens == 1`; после второго `trigger` — `clipboard.write` 1, `showOutcome` 1 | 3.4 |

Существующие тесты `CaptureCoordinatorTest` должны пройти без изменений, кроме фабрики `coordinator(...)`.

`SaveAsActionTest.kt` — добавить (`Files.createDirectories(tempDir.resolve("out"))` в начале каждого):

| # | Метод | Given / When | Then | Покрывает |
|---|---|---|---|---|
| 17 | `` `run for saves given screenshot and updates last`() `` | `holder.set(shot(40,30))`; `dialogResult = tempDir.resolve("out").resolve("edited.png")`; `val edited = shot(80,60)`; `var saved = 0`; `action.runFor(edited) { saved++ }` | файл существует, `ImageIO.read(file).width == 80`; `holder.get() === edited`; `saved == 1`; диалог предложил имя `"Screenshot_2026-10-18_20-00-48.png"` | 3.39 |
| 18 | `` `run for cancelled keeps last`() `` | `holder.set(original = shot(40,30))`; `dialogResult = null`; `runFor(shot(80,60)) { saved++ }` | `holder.get() === original`; `saved == 0` | 3.39 |
| 19 | `` `run for declined overwrite keeps last`() `` | существующий файл `out\edited.png` (записать любой текст); `confirmResult = false`; `dialogResult` = этот файл; `runFor(shot(80,60)) { saved++ }` | `holder.get() === original`; `saved == 0`; содержимое файла не изменилось | 3.39, REQ-001.40 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.capture.*"` | Все тесты проходят |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.SaveAsActionTest"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (`.\gradlew.bat run`):
1. Флажок «Открывать снимок в редакторе» выключен → `Ctrl+Shift+1`, `Ctrl+Shift+2`, `Ctrl+Shift+3`, `Ctrl+Shift+4`
   работают как раньше (буфер, файл, уведомление).
2. Включить флажок → `Ctrl+Shift+2` → в логе `Editor opening ALL_MONITORS WxH`, затем `Editor done: rendered WxH in N ms`
   и `Captured ALL_MONITORS WxH in N ms`; буфер, файл и уведомление — как без редактора (временная реализация).
3. С включённым флажком `Ctrl+Shift+3` → `Esc` на оверлее → в логе `Region capture cancelled`, строки `Editor opening`
   нет.
4. Выключить флажок.

## 13. Критерии готовности (Definition of Done)

- [ ] Сигнатуры совпадают с разделом 6; порядок параметров конструктора — как в разделе 6.
- [ ] `finish` и `crop` не изменены; старые тесты координатора проходят.
- [ ] Тесты 1–19 написаны и проходят.
- [ ] Ручная проверка 1–4 выполнена.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не создавать окно редактора и не трогать `ui/*`, кроме `SaveAsAction.kt` (TASK-037, TASK-038).
- Не менять поведение при выключенном флажке.
- Не вызывать `monitors.monitorUnderCursor` для редактора, если `s.openInEditor == false`.
- Не рендерить изображение на EDT.

## 15. Ловушки и подсказки

- `editorOpen` сбрасывается **до** `busy` (внутри `finish` `busy = false`): иначе нажатие клавиши в промежутке
  вызовет `bringToFront` для уже закрытого редактора — это безвредно, но порядок задан именно так.
- Внутри `inner class EditorSession` метод `saveAs` сессии перекрывает поле координатора с тем же именем — вызывай
  поле как `this@CaptureCoordinator.saveAs(...)`.
- `selectedNs` в `onRegion` и `confirmNs` в `onMultiRegion` берутся **до** `captureExecutor.execute`.
- В тестах с `direct`-исполнителями `editor.open` вызывается синхронно внутри `trigger`, поэтому после `trigger`
  `editor.host` уже заполнен.
- В тесте №3 `monitorUnderCursor` уже замокан на `b` — дополнительных `every` не нужно.
- `PassThroughEditor` будет удалён в TASK-038 — не добавляй в него логику.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

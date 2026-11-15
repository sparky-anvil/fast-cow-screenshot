# TASK-037: Создать логику окна редактора `EditorController`

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-004 §4, §6, §8, §10 |
| Требования | REQ-003.9, 3.10, 3.15, 3.16, 3.18, 3.19, 3.21, 3.24 – 3.30, 3.33, 3.34, 3.37 – 3.40, 3.45; US-012 AC-1 – AC-5, US-013 AC-2 – AC-9, US-014 AC-2 – AC-4, US-011 AC-3, AC-5 – AC-8 |
| ADR | ADR-006 |
| Зависит от | TASK-034, TASK-035, TASK-036 |
| Блокирует | TASK-038 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать `ui.EditorController` — всю логику окна редактора без Compose и Swing: масштаб и прокрутку, выбор инструмента,
цвета и толщины, рисование штриха и перетаскивание фрагмента по событиям мыши (в координатах холста), сдвиг стрелками,
Undo/Redo/«Склеить», завершение («Готово», «Копировать», «Сохранить как…», «Отмена» с подтверждением), строку
состояния. Окно (TASK-038) только рисует состояние контроллера и передаёт ему события.

## 2. Контекст

Готовые части:
- `editor.EditorState` (TASK-034) — позиции, штрихи, инструмент, история, `isDirty`, `snapshot()`;
  `moveFragment(index, to): MoveOutcome` (`Moved(shift)`, `Unchanged`, `Intersects`, `OutOfLimits`, `Blocked`).
- `editor.FragmentLayout` (TASK-032) — `fragmentAt`, `clampPosition`, `intersectsAny`.
- `editor.Viewport` (TASK-035) — масштаб и сдвиг; `fit`, `zoomAt`, `scrollBy`, `shiftedBy`, `clamp`, `toImageX/Y`.
- `ui.EditorKeyAction` (TASK-035) — действие по клавише.
- `capture.EditorRequest`, `capture.EditorHost` (TASK-036) — входные данные и обратные вызовы координатора.
- `settings.EditorSettings` (TASK-031) — цвет/толщина, `PALETTE_RGB`, `PEN_WIDTHS`, `penRgb()`, `effectivePenWidth()`.

Координаты: события мыши приходят в px холста `(x, y)`; в координаты изображения переводит
`viewport.toImageX(x)` / `toImageY(y)`. Холст сообщает свой размер `onCanvasSize(w, h)`; при первом ненулевом
размере снимок вписывается (`zoomFit`).

Всё, что зависит от окна, контроллер получает лямбдами: `showHint(text)` — подсказка на 2 с; `confirmDiscard()` —
модальный вопрос «Закрыть без сохранения?» (true = «Закрыть»); `closeWindow()` — закрыть окно; `onCanvasChanged()` —
перерисовать холст; `onChromeChanged()` — обновить панель инструментов и строку состояния; `onPenChanged(settings)` —
сохранить цвет/толщину.

**Выдержки из требований (дословно):**

> **REQ-003.10** Масштаб меняется сочетанием `Ctrl` + колесо мыши (относительно точки под курсором), `Ctrl+0` — вписать в окно, `Ctrl+1` — 100 %. Диапазон — от 10 % до 800 %. … Если снимок больше холста, есть прокрутка (колесо — по вертикали, `Shift` + колесо — по горизонтали).
>
> **REQ-003.15** Инструмент «Карандаш» рисует штрих произвольной формы: от нажатия левой кнопки мыши до её отпускания. …
>
> **REQ-003.16** Одиночный клик без движения ставит точку диаметром, равным толщине.
>
> **REQ-003.19** Система должна запоминать последние выбранные цвет и толщину …
>
> **REQ-003.24** Инструментом «Перемещение» пользователь перетаскивает фрагмент левой кнопкой мыши. Фрагмент сдвигается на целое число физических пикселей и не масштабируется. …
>
> **REQ-003.25** … Если во время перетаскивания фрагмент пересекается с другим, система подсвечивает это … Если отпустить кнопку в положении с пересечением, фрагмент возвращается туда, откуда его начали тащить, и показывается подсказка «Области не должны пересекаться».
>
> **REQ-003.26** … Размер Ш×В в окне обновляется сразу.
>
> **REQ-003.29** Фрагменты можно двигать стрелками клавиатуры: выбранный (последний перемещённый или кликнутый) фрагмент сдвигается на 1 px, с `Shift` — на 10 px. Ограничения REQ-003.25 и REQ-003.27 действуют: сдвиг, приводящий к пересечению, не выполняется.
>
> **REQ-003.30** … Подсказка: «Перемещение недоступно после рисования. Отмените штрихи, чтобы двигать области».
>
> **REQ-003.40** «Отмена» (кнопка, `Esc`, закрытие окна) закрывает редактор без результата. Если в редакторе не было изменений или текущее изображение уже скопировано/сохранено действием REQ-003.38/39, окно закрывается сразу. Иначе система спрашивает «Закрыть без сохранения? Изменения будут потеряны.» с вариантами «Закрыть» и «Вернуться».
>
> **REQ-003.45** При выходе из приложения через трей с открытым редактором действует правило REQ-003.40 …
>
> **REQ-003 §6:** Окно редактора потеряло фокус во время рисования … — текущий штрих завершается в последней известной точке. … «Копировать», затем ещё штрих, затем `Esc` — есть несохранённые изменения, система спрашивает подтверждение.
>
> **US-012 AC-2** Given масштаб просмотра 50 %, толщина 8 px When пользователь рисует штрих и нажимает «Готово» Then на итоговом изображении толщина линии 8 px …
>
> **US-013 AC-2** … перетаскивает B так, что его левый верхний угол оказывается в (0,50) Then изображение 200×150 … размер в окне обновился сразу.

## 3. Предусловия

- TASK-031, TASK-032, TASK-033, TASK-034, TASK-035, TASK-036 выполнены (классы перечислены в §2).
- Существует `settings/MultiRegionSettings.kt` — `formatHexColor(rgb): String`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/DragPreview.kt` | Создать | `DragPreview` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorController.kt` | Создать | `EditorController` |
| 3 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorControllerTest.kt` | Создать | тесты 1–30 |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
// ui/DragPreview.kt
package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint

/** Перетаскиваемый фрагмент: index — индекс фрагмента, position — его позиция-кандидат, intersects — пересечение. */
internal data class DragPreview(val index: Int, val position: PixelPoint, val intersects: Boolean)
```

```kotlin
// ui/EditorController.kt
package by.sparkyanvil.fastcowscreenshot.ui

/**
 * Логика окна редактора (TDD-004). Без Compose и Swing. Только EDT, не потокобезопасен.
 * После закрытия (isClosed) все методы ничего не делают, closeForExit() возвращает true.
 */
internal class EditorController(
    private val request: EditorRequest,
    private val host: EditorHost,
    pen: EditorSettings,
    private val onPenChanged: (EditorSettings) -> Unit,
    private val showHint: (String) -> Unit,
    private val confirmDiscard: () -> Boolean,
    private val closeWindow: () -> Unit,
    private val onCanvasChanged: () -> Unit,
    private val onChromeChanged: () -> Unit,
) {
    /** EditorState(request.image, request.fragments, request.fillRgb, multiRegion = isMultiRegion). */
    val state: EditorState

    /** request.mode == CaptureMode.MULTI_REGION. */
    val isMultiRegion: Boolean

    /** Текущий масштаб/сдвиг; до onCanvasSize — Viewport(1.0, 0.0, 0.0). */
    var viewport: Viewport
        private set

    /** Текущий цвет 0xRRGGBB (из pen.penRgb()) и толщина (pen.effectivePenWidth()). */
    var penRgb: Int
        private set
    var penWidth: Int
        private set

    /** Рисуемый штрих (между pointerDown и концом жеста) или null. */
    var liveStroke: Stroke?
        private set

    /** Перетаскиваемый фрагмент или null. */
    var drag: DragPreview?
        private set

    /** Точка изображения под указателем (для круга карандаша) или null, если указатель вне холста. */
    var hoverPoint: StrokePoint?
        private set

    /** Индекс фрагмента под указателем при инструменте MOVE или null. */
    var hoverFragment: Int?
        private set

    /** Последний кликнутый/перемещённый фрагмент (для стрелок) или null. */
    var selectedFragment: Int?
        private set

    var isClosed: Boolean
        private set

    val isGestureActive: Boolean get() = TODO()        // liveStroke != null || drag != null
    val isDirty: Boolean get() = TODO()                // state.isDirty
    val showMoveBlockedHint: Boolean get() = TODO()    // isMultiRegion && state.hasStrokes

    /** Границы изображения для показа: (0,0,state.width,state.height) или, во время drag, объединение с кандидатом. */
    val displayBounds: PixelRect get() = TODO()

    /** STATUS_FORMAT.format(displayBounds.width, displayBounds.height, viewport.percent). */
    val statusText: String get() = TODO()

    fun onCanvasSize(width: Int, height: Int)
    fun zoomFit()
    fun zoom100()
    /** scrollX/scrollY — Compose scrollDelta (1.0 = один щелчок колеса; Shift даёт scrollX). */
    fun onWheel(x: Float, y: Float, scrollX: Float, scrollY: Float, ctrl: Boolean)
    fun selectTool(tool: EditorTool)
    fun selectColor(rgb: Int)
    fun selectWidth(width: Int)
    fun stitch()
    fun undo()
    fun redo()
    fun pointerDown(x: Float, y: Float)
    fun pointerMove(x: Float, y: Float)
    fun pointerUp(x: Float, y: Float)
    fun pointerExit()
    fun focusLost()
    fun nudge(dx: Int, dy: Int)
    fun onKey(action: EditorKeyAction)
    fun done()
    fun copy()
    fun saveAs()
    fun requestCancel()
    /** Выход из приложения (REQ-003.45): true — редактор закрыт (или уже был закрыт), false — пользователь выбрал «Вернуться». */
    fun closeForExit(): Boolean

    companion object {
        const val MSG_INTERSECT: String = "Области не должны пересекаться"
        const val MSG_MOVE_BLOCKED: String =
            "Перемещение недоступно после рисования. Отмените штрихи, чтобы двигать области"
        const val MSG_FINISH_EDITING: String = "Завершите редактирование снимка"
        const val STATUS_FORMAT: String = "%d × %d px · %d %%"
        const val HINT_MS: Int = 2000
        const val MIN_POINT_DISTANCE: Float = 0.5f
    }
}
```

## 7. Пошаговая реализация

### Шаг 1. `DragPreview.kt` — по разделу 6.
### Шаг 2. `EditorController.kt`
- Приватные поля: `canvasWidth = 0`, `canvasHeight = 0`, `fitted = false`, `dragStart: StrokePoint? = null`,
  `livePoints = ArrayList<StrokePoint>()`.
- Инициализация: `isMultiRegion`, `state`, `viewport = Viewport(1.0, 0.0, 0.0)`, `penRgb = pen.penRgb()`,
  `penWidth = pen.effectivePenWidth()`, остальные `null`/`false`.
- Методы — раздел 8.
- **Результат:** компиляция.
### Шаг 3. Тесты — раздел 11. **Результат:** `.\gradlew.bat build` проходит.

## 8. Алгоритм и логика

Вспомогательное:

```
changed(chrome: Boolean): onCanvasChanged(); if (chrome) onChromeChanged()
clampView(): viewport = viewport.clamp(state.width, state.height, canvasWidth, canvasHeight)
imagePoint(x, y) = StrokePoint(viewport.toImageX(x), viewport.toImageY(y))
afterMove(outcome: MoveOutcome.Moved):
    viewport = viewport.shiftedBy(-outcome.shift.x * viewport.zoom, -outcome.shift.y * viewport.zoom)
    clampView()
savePen(): onPenChanged(EditorSettings(MultiRegionSettings.formatHexColor(penRgb), penWidth))
close(): isClosed = true; liveStroke = null; drag = null; livePoints.clear(); closeWindow()
```

**Размер и масштаб:**

```
onCanvasSize(w, h):
    if (isClosed) return
    canvasWidth = w; canvasHeight = h
    if (!fitted && w > 0 && h > 0) { fitted = true; zoomFit() } else { clampView(); changed(true) }
zoomFit(): if closed return; viewport = Viewport.fit(state.width, state.height, canvasWidth, canvasHeight); changed(true)
zoom100(): if closed return; viewport = viewport.zoomAt(1.0, canvasWidth / 2.0, canvasHeight / 2.0,
                                                    state.width, state.height, canvasWidth, canvasHeight); changed(true)
onWheel(x, y, scrollX, scrollY, ctrl):
    if (isClosed) return
    if (ctrl):
        d = if (scrollY != 0f) scrollY else scrollX
        if (d == 0f) return
        factor = if (d > 0f) 1.0 / Viewport.ZOOM_STEP else Viewport.ZOOM_STEP     // колесо к себе — уменьшить
        viewport = viewport.zoomAt(viewport.zoom * factor, x.toDouble(), y.toDouble(), state.width, state.height, canvasWidth, canvasHeight)
    else:
        if (scrollX == 0f && scrollY == 0f) return
        viewport = viewport.scrollBy(scrollX * Viewport.SCROLL_STEP_PX, scrollY * Viewport.SCROLL_STEP_PX,
                                     state.width, state.height, canvasWidth, canvasHeight)
    changed(true)
```

**Инструменты:**

```
selectTool(tool):
    if (isClosed || isGestureActive) return
    if (tool == MOVE && !isMultiRegion) return
    if (!state.selectTool(tool)) { showHint(MSG_MOVE_BLOCKED); return }
    hoverFragment = null; changed(true)
selectColor(rgb):  if closed or rgb !in EditorSettings.PALETTE_RGB return; penRgb = rgb; savePen(); state.selectTool(PENCIL); changed(true)
selectWidth(w):    if closed or w !in EditorSettings.PEN_WIDTHS return; penWidth = w; savePen(); state.selectTool(PENCIL); changed(true)
stitch():          if (isClosed || isGestureActive || !state.canMove) return; if (state.stitch()) { clampView(); changed(true) }
undo() / redo():   if (isClosed || isGestureActive) return; if (state.undo() / state.redo()) { clampView(); changed(true) }
```

**Мышь:**

```
pointerDown(x, y):
    if (isClosed || isGestureActive) return
    p = imagePoint(x, y)
    when (state.tool):
        PENCIL -> livePoints.clear(); livePoints.add(p); liveStroke = Stroke(penRgb, penWidth, livePoints); changed(false)
        MOVE   -> idx = FragmentLayout.fragmentAt(state.rects(), PixelPoint(floor(p.x).toInt(), floor(p.y).toInt())) ?: return
                  selectedFragment = idx; dragStart = p
                  drag = DragPreview(idx, state.positions[idx], false); changed(true)

pointerMove(x, y):
    if (isClosed) return
    p = imagePoint(x, y); hoverPoint = p
    if (liveStroke != null):
        last = livePoints.last()
        if (hypot(p.x - last.x, p.y - last.y) >= MIN_POINT_DISTANCE) livePoints.add(p)
        changed(false); return
    d = drag; start = dragStart
    if (d != null && start != null):
        rects = state.rects(); from = state.positions[d.index]
        target = PixelPoint(from.x + (p.x - start.x).roundToInt(), from.y + (p.y - start.y).roundToInt())
        cand = FragmentLayout.clampPosition(rects, d.index, target)
        candRect = PixelRect(cand.x, cand.y, rects[d.index].width, rects[d.index].height)
        drag = DragPreview(d.index, cand, FragmentLayout.intersectsAny(rects, d.index, candRect))
        changed(true); return
    if (state.tool == MOVE) hoverFragment = FragmentLayout.fragmentAt(state.rects(), PixelPoint(floor(p.x).toInt(), floor(p.y).toInt()))
    changed(false)

pointerUp(x, y):  if (isClosed) return; pointerMove(x, y); endGesture()
pointerExit():    if (isClosed) return; hoverPoint = null; hoverFragment = null; changed(false)
focusLost():      if (isClosed) return; endGesture()

endGesture():                 // private
    stroke = liveStroke
    if (stroke != null):
        state.addStroke(Stroke(stroke.rgb, stroke.width, livePoints.toList()))
        liveStroke = null; livePoints.clear(); changed(true); return
    d = drag ?: return
    drag = null; dragStart = null
    when (val outcome = state.moveFragment(d.index, d.position)):
        is MoveOutcome.Moved -> afterMove(outcome)
        MoveOutcome.Intersects -> showHint(MSG_INTERSECT)
        else -> Unit
    changed(true)
```

**Стрелки и клавиши:**

```
nudge(dx, dy):
    if (isClosed || isGestureActive || !isMultiRegion || state.tool != MOVE) return
    idx = selectedFragment ?: return
    from = state.positions[idx]; target = PixelPoint(from.x + dx, from.y + dy)
    if (FragmentLayout.clampPosition(state.rects(), idx, target) != target) return      // REQ-003.27 — молча
    outcome = state.moveFragment(idx, target)
    if (outcome is MoveOutcome.Moved) { afterMove(outcome); changed(true) }            // Intersects — молча

onKey(action) = when (action):
    Done -> done(); Copy -> copy(); SaveAs -> saveAs(); Cancel -> requestCancel()
    Undo -> undo(); Redo -> redo(); ZoomFit -> zoomFit(); Zoom100 -> zoom100()
    ToolMove -> if (isMultiRegion) selectTool(MOVE)
    ToolPencil -> selectTool(PENCIL)
    is Nudge -> nudge(action.dx, action.dy)
```

**Завершение:**

```
done():          if (isClosed) return; endGesture(); snap = state.snapshot(); close(); host.done(snap)
copy():          if (isClosed) return; endGesture(); id = state.currentStateId
                 host.copy(state.snapshot()) { ok -> if (ok) { state.markSaved(id); changed(true) } }
saveAs():        if (isClosed) return; endGesture(); id = state.currentStateId
                 host.saveAs(state.snapshot()) { state.markSaved(id); changed(true) }
requestCancel(): if (isClosed) return; endGesture()
                 if (!state.isDirty || confirmDiscard()) { close(); host.cancel() }
closeForExit():  if (isClosed) return true; endGesture()
                 if (state.isDirty && !confirmDiscard()) return false
                 close(); host.cancel(); return true
displayBounds:   d = drag ?: return PixelRect(0, 0, state.width, state.height)
                 rects = state.rects().toMutableList(); r = rects[d.index]
                 rects[d.index] = PixelRect(d.position.x, d.position.y, r.width, r.height); return PixelRect.union(rects)
```

`floor` — `kotlin.math.floor`, `hypot` — `kotlin.math.hypot`, `roundToInt` — `kotlin.math.roundToInt`.

**Пример расчёта** (тест 9): холст 300×150, снимок 300×150 → `fit` → `Viewport(1.0, 0.0, 0.0)`.
`pointerDown(150, 100)` → точка изображения (150, 100) внутри B = (100,50,200,100) → `drag = (1, (100,50), false)`.
`pointerMove(50, 100)` → `target = (100 + (50 − 150), 50) = (0, 50)` → `clampPosition` оставляет `(0, 50)`; касание
с A → `intersects = false`; `displayBounds = (0,0,200,150)` → `"200 × 150 px · 100 %"`. `pointerUp` →
`Moved((0,0))` → `clampView`: контент 200 ≤ 300 → `offsetX = 50`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Пользователь |
|---|---|---|---|
| 1 | Клик инструментом MOVE мимо фрагментов | ничего | — |
| 2 | Отпускание с пересечением | позиция не меняется, история не меняется | подсказка `"Области не должны пересекаться"` |
| 3 | Выбор MOVE (кнопка или `V`) при штрихах | инструмент не меняется | подсказка `"Перемещение недоступно после рисования. Отмените штрихи, чтобы двигать области"` |
| 4 | `V` / «Перемещение» для снимка не «Несколько областей» | ничего | — |
| 5 | Undo/Redo/«Склеить»/инструмент во время жеста | игнорируется | — |
| 6 | «Готово»/«Копировать»/«Сохранить как…»/«Отмена» во время жеста | жест завершается (штрих сохраняется), затем действие | — |
| 7 | Потеря фокуса во время рисования/перетаскивания | как отпускание в последней точке | — |
| 8 | Цвет не из палитры, толщина не из списка | игнорируется | — |
| 9 | «Копировать» завершилось ошибкой (`onResult(false)`) | состояние остаётся «грязным» | уведомление показывает координатор |
| 10 | Любой вызов после закрытия | ничего; `closeForExit()` → `true` | — |

## 10. Константы и тексты

| Имя | Тип | Значение |
|---|---|---|
| `MSG_INTERSECT` | `String` | `"Области не должны пересекаться"` |
| `MSG_MOVE_BLOCKED` | `String` | `"Перемещение недоступно после рисования. Отмените штрихи, чтобы двигать области"` |
| `MSG_FINISH_EDITING` | `String` | `"Завершите редактирование снимка"` |
| `STATUS_FORMAT` | `String` | `"%d × %d px · %d %%"` (между числами — символ `×` U+00D7, перед `%` — пробел) |
| `HINT_MS` | `Int` | `2000` |
| `MIN_POINT_DISTANCE` | `Float` | `0.5f` (пиксели изображения) |

## 11. Тесты

`EditorControllerTest.kt`. Фейк и помощники:

```kotlin
private class FakeHost : EditorHost {
    val done = mutableListOf<EditorSnapshot>()
    val copies = mutableListOf<Pair<EditorSnapshot, (Boolean) -> Unit>>()
    val saves = mutableListOf<Pair<EditorSnapshot, () -> Unit>>()
    var cancels = 0
    override fun done(result: EditorSnapshot) { done.add(result) }
    override fun copy(result: EditorSnapshot, onResult: (Boolean) -> Unit) { copies.add(result to onResult) }
    override fun saveAs(result: EditorSnapshot, onSaved: () -> Unit) { saves.add(result to onSaved) }
    override fun cancel() { cancels++ }
}
```

- `img(w, h)` — `TYPE_INT_RGB`, пиксель = `x * 256 + y`; `r(...) = PixelRect(...)`; `p(x, y) = PixelPoint(x, y)`.
- `request(image, fragments, mode) = EditorRequest(image, mode, LocalDateTime.of(2026, 11, 15, 10, 0, 0), fragments, 0x808080, r(0, 0, 1920, 1040), 0L)`.
- `multi = request(img(300,150), [r(0,0,100,50), r(100,50,200,100)], MULTI_REGION)`;
  `single = request(img(40,30), [r(0,0,40,30)], REGION)`;
  `three = request(img(30,10), [r(0,0,10,10), r(10,0,10,10), r(20,0,10,10)], MULTI_REGION)`.
- Поля теста: `host = FakeHost()`, `hints`, `pens`, `confirmAnswer = false`, `confirmCalls`, `closeCalls`,
  `canvasChanges`, `chromeChanges`.
- `controller(req, pen = EditorSettings()) = EditorController(req, host, pen, { pens.add(it) }, { hints.add(it) },
  { confirmCalls++; confirmAnswer }, { closeCalls++ }, { canvasChanges++ }, { chromeChanges++ })`.
- `drawDot(c, x, y)` = `c.pointerDown(x, y); c.pointerUp(x, y)`.
- `Double` сравнивать с допуском `1e-3`.

| # | Метод | Given / When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `multi region starts with move tool and fit`() `` | `controller(multi)`, `onCanvasSize(300,150)` | `state.tool == MOVE`; `viewport == Viewport(1.0, 0.0, 0.0)`; `statusText == "300 × 150 px · 100 %"`; `isMultiRegion` | 3.9, 3.23 |
| 2 | `` `single starts with pencil and saved pen`() `` | `controller(single, EditorSettings("#2979FF", 16))` | `state.tool == PENCIL`; `penRgb == 0x2979FF`; `penWidth == 16`; `selectTool(MOVE)` → `tool == PENCIL`, `hints` пуст | 3.19, 3.22 |
| 3 | `` `fit scales large image`() `` | `controller(multi)`, `onCanvasSize(150, 1000)` | `viewport.zoom == 0.5`, `offsetX == 0.0`, `offsetY == 462.5`; `statusText == "300 × 150 px · 50 %"` | 3.9 |
| 4 | `` `pencil stroke follows pointer`() `` | `single`, холст 40×30; `pointerDown(5,5)`, `pointerMove(10,5)`, `pointerMove(20,5)` | во время: `liveStroke?.points?.size == 3`; после `pointerUp(20,5)`: `state.strokes.size == 1`, точки `[(5,5), (10,5), (20,5)]`; `liveStroke == null`; `isDirty` | 3.15, 3.21 |
| 5 | `` `stroke is stored in image pixels at zoom`() `` | `single`, холст 20×15 (`zoom = 0.5`); `drawDot(10f, 5f)` | точка штриха `(20, 10)`; `width == 4` | 3.17, US-012 AC-2 |
| 6 | `` `click makes a dot`() `` | `single`, холст 40×30; `drawDot(5f, 5f)` | 1 штрих, 1 точка | 3.16 |
| 7 | `` `near points are skipped`() `` | `single`, холст 40×30; `pointerDown(5,5)`, `pointerMove(5.2f, 5f)` | `liveStroke?.points?.size == 1` | 3.15 |
| 8 | `` `focus loss ends stroke at last point`() `` | `single`; `pointerDown(5,5)`, `pointerMove(15,5)`, `focusLost()` | 1 штрих, последняя точка `(15,5)`; `liveStroke == null` | REQ-003 §6 |
| 9 | `` `drag moves fragment and updates size live`() `` | `multi`, холст 300×150; `pointerDown(150,100)`, `pointerMove(50,100)` | `drag == DragPreview(1, p(0,50), false)`; `statusText == "200 × 150 px · 100 %"`; после `pointerUp(50,100)`: `state.positions == [p(0,0), p(0,50)]`, `drag == null`, `selectedFragment == 1`, `viewport.offsetX == 50.0` | 3.24, 3.26, US-013 AC-2 |
| 10 | `` `drop on overlap reverts with hint`() `` | `multi`, холст 300×150; `pointerDown(150,100)`, `pointerMove(100,80)` | `drag?.intersects == true`, `drag?.position == p(50,30)`; после `pointerUp(100,80)`: позиции исходные, `hints == ["Области не должны пересекаться"]`, `!state.canUndo` | 3.25, US-013 AC-4 |
| 11 | `` `drag is limited by size`() `` | `multi`, холст 300×150; `pointerDown(150,100)`, `pointerMove(1150,100)` | `drag?.position == p(100,50)`; после `pointerUp`: `!state.canUndo` | 3.27, US-013 AC-7 |
| 12 | `` `drag left keeps fragment under pointer`() `` | `multi`, `onCanvasSize(100,100)`, `zoom100()`, `onWheel(0f,0f,-0.5f,0f,false)` (→ `offsetX = −50`, `offsetY = −25`); `pointerDown(100,35)`, `pointerMove(-30,35)`, `pointerUp(-30,35)` | `state.positions == [p(30,0), p(0,50)]`; `viewport.offsetX == -80.0`; `viewport.offsetY == -25.0` | 3.26, US-013 AC-3 |
| 13 | `` `moving is blocked after drawing`() `` | `multi`, холст 300×150; `selectTool(PENCIL)`, `drawDot(10f,10f)`; `selectTool(MOVE)`; `stitch()`; `onKey(ToolMove)` | `state.tool == PENCIL`; `hints == [MSG_MOVE_BLOCKED, MSG_MOVE_BLOCKED]`; `showMoveBlockedHint`; позиции исходные; затем `undo()` → `!showMoveBlockedHint`; `selectTool(MOVE)` → `state.tool == MOVE` | 3.30, US-013 AC-8, AC-9 |
| 14 | `` `pen selection is saved and switches to pencil`() `` | `multi`, холст 300×150; `selectColor(0x2979FF)`, `selectWidth(16)`, `selectColor(0x123456)`, `selectWidth(5)`, `drawDot(10f,10f)` | `state.tool == PENCIL`; `pens == [EditorSettings("#2979FF", 4), EditorSettings("#2979FF", 16)]`; штрих `rgb == 0x2979FF`, `width == 16` | 3.18, 3.19, US-012 AC-3 |
| 15 | `` `arrows move selected fragment`() `` | `multi`, холст 300×150; `drawDot(150f,100f)` (клик по B, инструмент MOVE); `onKey(Nudge(-10,0))`; `onKey(Nudge(0,-1))`; `onKey(Nudge(20,0))` | после клика `selectedFragment == 1`, `!state.canUndo`; после первого сдвига `positions == [p(0,0), p(90,50)]`; второй (пересечение) и третий (лимит) — позиции те же, `hints` пуст | 3.29 |
| 16 | `` `arrows ignored without move tool`() `` | `single`: `onKey(Nudge(1,0))`; `multi`: `selectTool(PENCIL)`, `onKey(Nudge(1,0))` | позиции не изменились, `!state.canUndo` у обоих | 3.29 |
| 17 | `` `stitch applies layout`() `` | `controller(three)`, холст 30×10; `state.moveFragment(2, p(0,10))`, `state.moveFragment(1, p(20,0))`; `val before = chromeChanges`; `stitch()` | `state.positions == [p(0,0), p(10,0), p(0,10)]`; `chromeChanges > before` | 3.28, US-013 AC-6 |
| 18 | `` `undo and redo keys`() `` | `single`, холст 40×30; два `drawDot`; `onKey(Undo)`; `onKey(Redo)` | после Undo 1 штрих; после Redo 2; `!state.canRedo` | 3.34 |
| 19 | `` `undo ignored during gesture`() `` | `single`; `pointerDown(5,5)`; `onKey(Undo)` | `state.strokes` пуст; `liveStroke != null` | §9 №5 |
| 20 | `` `done finishes gesture and sends snapshot once`() `` | `single`; `pointerDown(5,5)`, `pointerMove(15,5)`, `onKey(Done)`; затем `pointerDown(1,1)`, `onKey(Done)` | `host.done.size == 1`; `host.done[0].strokes.size == 1`; `closeCalls == 1`; `isClosed`; `state.strokes.size == 1` | 3.37, US-011 AC-3 |
| 21 | `` `cancel without changes closes at once`() `` | `single`; `requestCancel()` | `confirmCalls == 0`; `host.cancels == 1`; `closeCalls == 1` | 3.40, US-011 AC-7 |
| 22 | `` `cancel with changes asks`() `` | `single`; `drawDot`; `confirmAnswer = false`; `requestCancel()`; затем `confirmAnswer = true`; `requestCancel()` | после первого: `confirmCalls == 1`, `host.cancels == 0`, `!isClosed`, 1 штрих; после второго: `host.cancels == 1`, `isClosed` | 3.40, US-011 AC-8 |
| 23 | `` `successful copy marks state saved`() `` | `single`; `drawDot`; `copy()`; `host.copies[0].second(true)`; `requestCancel()` | `host.copies[0].first.strokes.size == 1`; `!isDirty` до `requestCancel`; `confirmCalls == 0`; `host.cancels == 1` | 3.38, 3.40 |
| 24 | `` `failed copy keeps state dirty`() `` | `single`; `drawDot`; `copy()`; `host.copies[0].second(false)` | `isDirty` | §9 №9 |
| 25 | `` `stroke after copy asks on cancel`() `` | `single`; `drawDot(5f,5f)`; `copy()`; `second(true)`; `drawDot(9f,9f)`; `requestCancel()` (`confirmAnswer = false`) | `confirmCalls == 1`; `!isClosed` | REQ-003 §6 |
| 26 | `` `save as marks state saved`() `` | `single`; `drawDot`; `saveAs()`; `host.saves[0].second()` | `host.saves.size == 1`; `!isDirty` | 3.39, 3.40 |
| 27 | `` `close for exit`() `` | (а) `controller(single).closeForExit()`; (б) новый контроллер, `drawDot`, `confirmAnswer = false`, `closeForExit()`, затем `confirmAnswer = true`, `closeForExit()` | (а) `true`, `host.cancels == 1`; (б) первый `false` и `!isClosed`, второй `true`, `host.cancels == 2` | 3.45 |
| 28 | `` `ctrl wheel zooms around pointer`() `` | `multi`, холст 300×150; `onWheel(150f, 75f, 0f, -1f, true)` | `viewport.zoom == 1.25`; `viewport.percent == 125`; `viewport.toImageX(150f) == 150f` (допуск `0.01`); затем `onWheel(150f, 75f, 0f, 1f, true)` → `zoom == 1.0` | 3.10, US-014 AC-3 |
| 29 | `` `wheel scrolls vertically and shift wheel horizontally`() `` | как №28 после первого `onWheel` (zoom 1.25); `onWheel(0f,0f,0f,1f,false)`; `onWheel(0f,0f,1f,0f,false)` | после первого `offsetY == -37.5`; после второго `offsetX == -75.0` | 3.10 |
| 30 | `` `zoom shortcuts`() `` | `multi`, холст 150×1000; `onKey(Zoom100)`; `onKey(ZoomFit)` | после первого `viewport.zoom == 1.0`; после второго `0.5` | 3.10, US-014 AC-4 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.EditorControllerTest"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручной проверки нет (окно — TASK-038).

## 13. Критерии готовности (Definition of Done)

- [ ] Сигнатуры и константы совпадают с разделом 6.
- [ ] `EditorController.kt` не импортирует `androidx.compose`, `javax.swing`, `java.awt`, `com.sun.jna`.
- [ ] Тесты 1–30 написаны и проходят.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не создавать окно, таймеры, диалоги — только вызывать лямбды.
- Не рендерить итоговое изображение (это делает координатор).
- Не менять `EditorState`, `Viewport`, `FragmentLayout` — если не хватает метода, оформи CLR.

## 15. Ловушки и подсказки

- `liveStroke` во время рисования ссылается на изменяемый `livePoints` (без копирования на каждое движение — иначе
  O(n²)); в историю уходит копия `livePoints.toList()`. После `endGesture` сначала создай копию, потом `clear()`.
- `selectTool(MOVE)` для снимка не «Несколько областей» молча игнорируется (без подсказки) — подсказку показывает
  только блокировка штрихами.
- В `afterMove` используй `viewport.zoom` **до** изменения viewport.
- Числа с плавающей точкой: в тестах 3, 12, 28, 29 сравнивай `Double` с допуском, иначе тест упадёт на 1e-9.
- `"%d × %d px · %d %%".format(...)` — `String.format` в Kotlin; `%%` даёт один знак `%`.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

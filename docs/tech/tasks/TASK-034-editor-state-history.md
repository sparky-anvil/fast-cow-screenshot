# TASK-034: Создать состояние редактора с историей изменений `EditorState`

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-004 §5, §7, §8 |
| Требования | REQ-003.22, 3.23, 3.25 – 3.28, 3.30, 3.32 – 3.36, 3.40 (признак изменений), N7; US-012 AC-7, AC-8, US-013 AC-1 – AC-10 |
| ADR | ADR-006 |
| Зависит от | TASK-032, TASK-033 |
| Блокирует | TASK-037 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать `EditorState` — изменяемое состояние одного открытия редактора: позиции фрагментов, штрихи, текущий
инструмент, история Undo/Redo и признак «есть несохранённые изменения». Плюс вспомогательные типы `EditCommand`
(команда истории) и `MoveOutcome` (результат перемещения). Класс без UI, полностью покрывается unit-тестами.

## 2. Контекст

Типы из TASK-032/033: `Stroke`, `EditorTool { MOVE, PENCIL }`, `FragmentLayout` (геометрия), `EditorSnapshot`
(неизменяемый снимок состояния для рендера), `EditorRenderer.render(snapshot)`.

Как устроено состояние:
- Исходный снимок `base` не меняется. Фрагменты — `sources` (прямоугольники на `base`). Позиция фрагмента `i` —
  `positions[i]`, всегда нормализованы (минимальные `left` и `top` = 0).
- История — две стопки команд: `undo` и `redo`. Команда `AddStroke(stroke)` — штрих; `ChangeLayout(before, after)` —
  полный список позиций до и после (перемещение одного фрагмента или «Склеить»). Отменить = восстановить «до»,
  повторить = применить «после». Новая команда очищает стопку `redo`. Глубина истории не ограничена
  (требование ≥ 100).
- Каждая команда получает уникальный `id` (1, 2, 3, … по порядку создания). **Идентификатор текущего состояния**
  `currentStateId` = `id` верхней команды стопки `undo` или `0`, если стопка пуста (= исходный снимок). После
  «Копировать»/«Сохранить как…» редактор вызывает `markSaved(id)`; изменения «не сохранены» (`isDirty`), если текущее
  состояние не исходное и не сохранённое.
- Перемещать фрагменты и «Склеить» можно только для снимка режима «Несколько областей» (`multiRegion = true`) и только
  пока штрихов нет (`canMove`). Инструмент «Перемещение» нельзя выбрать, если `canMove == false`.

**Выдержки из требований (дословно):**

> **REQ-003.22** Для снимков режимов «Монитор под курсором», «Все мониторы», «Область» карандаш — инструмент по умолчанию и единственный инструмент.
>
> **REQ-003.23** Для снимка режима «Несколько областей» редактор должен открываться с раскладкой, рассчитанной по REQ-002.20. … Инструмент по умолчанию — «Перемещение».
>
> **REQ-003.25** … Если отпустить кнопку в положении с пересечением, фрагмент возвращается туда, откуда его начали тащить … изменение не попало в историю (US-013 AC-4).
>
> **REQ-003.30** Перемещать фрагменты и выполнять «Склеить» можно только пока на изображении нет штрихов карандаша. Когда есть хотя бы один штрих (с учётом Undo), инструмент «Перемещение» и «Склеить» недоступны. …
>
> **REQ-003.32** При одной области перемещение доступно, но не меняет итоговое изображение (ограничивающий прямоугольник одного фрагмента — сам фрагмент).
>
> **REQ-003.33** Система должна вести общую историю изменений: штрих карандаша, перемещение фрагмента (одно перетаскивание или одно нажатие стрелки — одно изменение), «Склеить».
>
> **REQ-003.34** «Отменить» … отменяет последнее изменение. «Повторить» … возвращает последнее отменённое. Новое изменение после отмены очищает список повтора. Кнопки недоступны, когда отменять или повторять нечего.
>
> **REQ-003.35** Глубина истории — не менее 100 изменений в рамках одного открытия редактора.
>
> **REQ-003.36** После отмены всех изменений изображение должно совпадать с исходным снимком пиксель в пиксель.
>
> **REQ-003.40** … Если в редакторе не было изменений или текущее изображение уже скопировано/сохранено действием REQ-003.38/39, окно закрывается сразу. …
>
> **US-013 AC-10** Given выполнено перемещение B и «Склеить» When пользователь дважды нажимает `Ctrl+Z` Then раскладка совпадает с исходной (AC-1) пиксель в пиксель.
>
> **US-012 AC-7** Given нарисовано 3 штриха When пользователь дважды нажимает `Ctrl+Z`, затем один раз `Ctrl+Y` Then на снимке штрихи 1 и 2 …

## 3. Предусловия

- TASK-032: `editor/Stroke.kt`, `editor/EditorTool.kt`, `editor/FragmentLayout.kt` (`placed`, `normalizeShift`,
  `intersectsAny`, `withinLimits`, `stitchPositions`).
- TASK-033: `editor/EditorSnapshot.kt` (`EditorSnapshot(...)`, `companion requireValidSources(base, sources)`),
  `editor/EditorRenderer.kt`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/EditCommand.kt` | Создать | `EditCommand` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/MoveOutcome.kt` | Создать | `MoveOutcome` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/EditorState.kt` | Создать | `EditorState` |
| 4 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/editor/EditorStateTest.kt` | Создать | тесты 1–19 |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
// editor/EditCommand.kt
package by.sparkyanvil.fastcowscreenshot.editor

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint

/** Команда истории редактора. id — уникален в рамках одного EditorState, растёт с 1. */
internal sealed interface EditCommand {
    val id: Long

    /** Добавлен штрих. */
    data class AddStroke(override val id: Long, val stroke: Stroke) : EditCommand

    /** Изменена раскладка фрагментов (перемещение или «Склеить»): позиции всех фрагментов до и после. */
    data class ChangeLayout(
        override val id: Long,
        val before: List<PixelPoint>,
        val after: List<PixelPoint>,
    ) : EditCommand
}
```

```kotlin
// editor/MoveOutcome.kt
package by.sparkyanvil.fastcowscreenshot.editor

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint

/** Результат EditorState.moveFragment. */
internal sealed interface MoveOutcome {
    /** Перемещение выполнено и записано в историю. shift — сдвиг нормализации, применённый ко всем фрагментам. */
    data class Moved(val shift: PixelPoint) : MoveOutcome

    /** После нормализации позиции не изменились; в историю ничего не записано. */
    data object Unchanged : MoveOutcome

    /** Новое положение пересекается с другим фрагментом; ничего не изменено. */
    data object Intersects : MoveOutcome

    /** Новое положение нарушает ограничение размера REQ-003.27; ничего не изменено. */
    data object OutOfLimits : MoveOutcome

    /** Перемещение недоступно (не «Несколько областей» или есть штрихи); ничего не изменено. */
    data object Blocked : MoveOutcome
}
```

```kotlin
// editor/EditorState.kt
package by.sparkyanvil.fastcowscreenshot.editor

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import java.awt.image.BufferedImage

/**
 * Состояние одного открытия редактора (TDD-004 §7). Не потокобезопасен — только EDT.
 * @param base исходный снимок TYPE_INT_RGB (не изменяется).
 * @param sources прямоугольники фрагментов на base (проверяются EditorSnapshot.requireValidSources).
 * @param fillRgb цвет пустот 0xRRGGBB.
 * @param multiRegion true — снимок режима «Несколько областей»: доступны «Перемещение» и «Склеить».
 * @throws IllegalArgumentException если sources невалидны.
 */
internal class EditorState(
    val base: BufferedImage,
    val sources: List<PixelRect>,
    val fillRgb: Int,
    val multiRegion: Boolean,
) {
    /** Позиции при открытии: левые верхние углы sources. */
    val initialPositions: List<PixelPoint>

    /** Текущие нормализованные позиции фрагментов (порядок как в sources). */
    var positions: List<PixelPoint>
        private set

    /** Текущие штрихи в порядке рисования. */
    var strokes: List<Stroke>
        private set

    /** Текущий инструмент; при открытии MOVE, если multiRegion, иначе PENCIL. */
    var tool: EditorTool
        private set

    val hasStrokes: Boolean get() = TODO()          // strokes.isNotEmpty()
    val canMove: Boolean get() = TODO()             // multiRegion && strokes.isEmpty()
    val canUndo: Boolean get() = TODO()
    val canRedo: Boolean get() = TODO()

    /** id верхней команды стопки undo; 0 — исходное состояние. */
    val currentStateId: Long get() = TODO()

    /** true — currentStateId != 0 и currentStateId != последнему markSaved. */
    val isDirty: Boolean get() = TODO()

    /** Ширина / высота итогового изображения: PixelRect.union(rects()). */
    val width: Int get() = TODO()
    val height: Int get() = TODO()

    /** Текущие прямоугольники фрагментов: FragmentLayout.placed(sources, positions). */
    fun rects(): List<PixelRect> = TODO()

    /** Выбор инструмента. MOVE при canMove == false → false (инструмент не меняется). Иначе true. */
    fun selectTool(tool: EditorTool): Boolean = TODO()

    /** Добавляет штрих в историю (очищает redo); инструмент становится PENCIL. */
    fun addStroke(stroke: Stroke): Unit = TODO()

    /** Перемещает фрагмент index в позицию to (до нормализации). Алгоритм §8. */
    fun moveFragment(index: Int, to: PixelPoint): MoveOutcome = TODO()

    /** «Склеить» (REQ-003.28). true — раскладка изменилась и записана в историю; false — недоступно или без изменений. */
    fun stitch(): Boolean = TODO()

    /** Отменяет последнюю команду. false — отменять нечего. */
    fun undo(): Boolean = TODO()

    /** Повторяет последнюю отменённую. false — повторять нечего. */
    fun redo(): Boolean = TODO()

    /** Запоминает stateId как сохранённое («Копировать»/«Сохранить как…» выполнены для этого состояния). */
    fun markSaved(stateId: Long): Unit = TODO()

    /** Неизменяемый снимок текущего состояния. */
    fun snapshot(): EditorSnapshot = TODO()
}
```

## 7. Пошаговая реализация

### Шаг 1. `EditCommand.kt`, `MoveOutcome.kt` — по разделу 6. **Результат:** компиляция.

### Шаг 2. `EditorState.kt`
- Приватные поля: `undoStack = ArrayList<EditCommand>()`, `redoStack = ArrayList<EditCommand>()`,
  `lastId: Long = 0`, `savedStateId: Long = 0`.
- Блок `init`: `EditorSnapshot.requireValidSources(base, sources)`; `initialPositions = sources.map { PixelPoint(it.left, it.top) }`;
  `positions = initialPositions`; `strokes = emptyList()`; `tool = if (multiRegion) MOVE else PENCIL`.
- Методы — по разделу 8.
- **Результат:** компиляция.

### Шаг 3. Тесты — раздел 11. **Результат:** `.\gradlew.bat build` проходит.

## 8. Алгоритм и логика

```
private fun push(command):  undoStack.add(command); redoStack.clear()
private fun nextId(): Long = ++lastId
private fun fixTool():      if (tool == MOVE && !canMove) tool = PENCIL

currentStateId = undoStack.lastOrNull()?.id ?: 0L
isDirty        = currentStateId != 0L && currentStateId != savedStateId
canUndo        = undoStack.isNotEmpty();  canRedo = redoStack.isNotEmpty()
width/height   = PixelRect.union(rects()).width / .height

selectTool(t):
    if (t == MOVE && !canMove) return false
    tool = t; return true

addStroke(stroke):
    push(AddStroke(nextId(), stroke)); strokes = strokes + stroke; tool = PENCIL

moveFragment(index, to):
    if (!canMove) return Blocked
    require(index in sources.indices) { "no fragment $index" }
    current = rects()
    candidate = PixelRect(to.x, to.y, sources[index].width, sources[index].height)
    if (FragmentLayout.intersectsAny(current, index, candidate)) return Intersects
    moved = current.toMutableList(); moved[index] = candidate
    if (!FragmentLayout.withinLimits(moved)) return OutOfLimits
    shift = FragmentLayout.normalizeShift(moved)
    after = moved.map { PixelPoint(it.left + shift.x, it.top + shift.y) }
    if (after == positions) return Unchanged
    push(ChangeLayout(nextId(), positions, after)); positions = after
    return Moved(shift)

stitch():
    if (!canMove) return false
    after = FragmentLayout.stitchPositions(rects())
    if (after == positions) return false
    push(ChangeLayout(nextId(), positions, after)); positions = after; return true

undo():
    cmd = undoStack.removeLastOrNull() ?: return false
    when (cmd) { AddStroke -> strokes = strokes.dropLast(1); ChangeLayout -> positions = cmd.before }
    redoStack.add(cmd); fixTool(); return true

redo():
    cmd = redoStack.removeLastOrNull() ?: return false
    when (cmd) { AddStroke -> strokes = strokes + cmd.stroke; ChangeLayout -> positions = cmd.after }
    undoStack.add(cmd); fixTool(); return true        // НЕ через push: redo не должен очищаться

markSaved(id): savedStateId = id
snapshot():    EditorSnapshot(base, sources, positions, initialPositions, fillRgb, strokes)
```

Инвариант: команда `ChangeLayout` в стопке `undo` всегда ниже любой `AddStroke` (перемещать можно только без штрихов),
поэтому `undo` для `AddStroke` всегда снимает последний штрих списка.

**Пример расчёта** (US-013): `base` 300×150, `A = r(0,0,100,50)`, `B = r(100,50,200,100)`.
`moveFragment(1, p(0,50))` → кандидат `r(0,50,200,100)` касается A → границы `(0,0)–(200,150)`, в лимитах
(`Σw = 300`, `Σh = 150`) → `shift = (0,0)` → `positions = [p(0,0), p(0,50)]`, `Moved(p(0,0))`, размер 200×150.
Далее `moveFragment(0, p(-30,0))` → `shift = (30,0)` → `[p(0,0), p(30,50)]`, 230×150.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение |
|---|---|---|
| 1 | Невалидные `sources` | `IllegalArgumentException` из `requireValidSources` |
| 2 | `moveFragment` с несуществующим индексом | `IllegalArgumentException("no fragment 5")` |
| 3 | Перемещение с пересечением | `Intersects`, состояние и история не меняются |
| 4 | Один фрагмент, любое перемещение | `Unchanged` (нормализация возвращает (0,0)) |
| 5 | `stitch` без пустых полос | `false`, история не меняется |
| 6 | `redo` возвращает штрих, когда выбран MOVE | инструмент становится PENCIL |
| 7 | `undo`/`redo` при пустой стопке | `false` |

## 10. Константы и тексты

Нет.

## 11. Тесты

`EditorStateTest.kt`. Помощники: `img(w, h)` — `TYPE_INT_RGB`, пиксель `(x, y)` = `x * 256 + y`;
`r(...) = PixelRect(...)`, `p(x, y) = PixelPoint(x, y)`;
`stroke(n) = Stroke(0xFF0000, 4, listOf(StrokePoint(n.toFloat(), 1f)))`;
`twoState() = EditorState(img(300,150), listOf(r(0,0,100,50), r(100,50,200,100)), 0x808080, multiRegion = true)`;
`threeState() = EditorState(img(30,10), listOf(r(0,0,10,10), r(10,0,10,10), r(20,0,10,10)), 0x808080, true)`;
`singleState() = EditorState(img(40,30), listOf(r(0,0,40,30)), 0x808080, multiRegion = false)`;
`samePixels(a, b)` — равные размеры и все `getRGB` равны.

| # | Метод | Given / When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `initial multi region state`() `` | `twoState()` | `positions == [p(0,0), p(100,50)]`, `tool == MOVE`, `canMove`, `!canUndo`, `!canRedo`, `!isDirty`, `currentStateId == 0`, `width == 300`, `height == 150` | 3.23, US-013 AC-1 |
| 2 | `` `non multi region allows only pencil`() `` | `singleState()` | `tool == PENCIL`; `!canMove`; `selectTool(MOVE) == false`, `tool == PENCIL`; `moveFragment(0, p(5,5)) == Blocked`; `stitch() == false` | 3.22, US-013 AC-11 |
| 3 | `` `move fragment updates layout`() `` | `twoState().moveFragment(1, p(0,50))` | `Moved(p(0,0))`; `positions == [p(0,0), p(0,50)]`; `width == 200`, `height == 150`; `canUndo`; `isDirty` | 3.26, US-013 AC-2 |
| 4 | `` `drag left normalizes to origin`() `` | после №3: `moveFragment(0, p(-30,0))` | `Moved(p(30,0))`; `positions == [p(0,0), p(30,50)]`; `width == 230` | 3.26, US-013 AC-3 |
| 5 | `` `overlap rejected without history`() `` | `twoState().moveFragment(1, p(50,20))` | `Intersects`; `positions` исходные; `!canUndo` | 3.25, US-013 AC-4 |
| 6 | `` `touching accepted`() `` | `twoState().moveFragment(1, p(100,0))` | `Moved(p(0,0))`; `positions == [p(0,0), p(100,0)]` | 3.25, US-013 AC-5 |
| 7 | `` `size limit rejected`() `` | `twoState().moveFragment(1, p(500,50))` | `OutOfLimits`; `positions` исходные; `!canUndo` | 3.27 |
| 8 | `` `no-op move is not recorded`() `` | `twoState().moveFragment(1, p(100,50))`; отдельно `EditorState(img(40,30), [r(0,0,40,30)], 0, true).moveFragment(0, p(25,-7))` | оба `Unchanged`; `!canUndo` у обоих | 3.32 |
| 9 | `` `stitch removes gaps after moves`() `` | `threeState()`: `moveFragment(2, p(0,10))`, `moveFragment(1, p(20,0))`, `stitch()` | оба перемещения `Moved`; `stitch() == true`; `positions == [p(0,0), p(10,0), p(0,10)]`; `width == 20`, `height == 20`; повторный `stitch() == false` | 3.28, US-013 AC-6 |
| 10 | `` `undo all moves restores original pixels`() `` | состояние №9, `undo()` ×3 | все `true`; `positions == initialPositions`; `samePixels(EditorRenderer.render(snapshot()), base)` | 3.36, US-013 AC-10 |
| 11 | `` `strokes block moving and stitching`() `` | `twoState().addStroke(stroke(1))` | `hasStrokes`; `!canMove`; `tool == PENCIL`; `selectTool(MOVE) == false`; `moveFragment(1, p(0,50)) == Blocked`; `stitch() == false` | 3.30, US-013 AC-8 |
| 12 | `` `undoing strokes unblocks moving`() `` | после №11: `undo()` | `!hasStrokes`; `canMove`; `selectTool(MOVE) == true` | 3.30, US-013 AC-9 |
| 13 | `` `redo stroke switches tool to pencil`() `` | `twoState()`: `addStroke(stroke(1))`, `undo()`, `selectTool(MOVE)`, `redo()` | `hasStrokes`; `tool == PENCIL` | §9 №6 |
| 14 | `` `undo redo and redo cleared by new change`() `` | `singleState()`: `addStroke(s1, s2, s3)`, `undo()` ×2, `redo()` | `strokes == [s1, s2]`, `canRedo`; затем `addStroke(s4)` → `!canRedo`, `strokes == [s1, s2, s4]` | 3.34, US-012 AC-7, AC-8 |
| 15 | `` `undo and redo on empty history return false`() `` | `singleState()` | `undo() == false`, `redo() == false` | 3.34 |
| 16 | `` `history keeps 150 changes`() `` | `singleState()`: 150 × `addStroke(stroke(i))`, затем 150 × `undo()` | все `undo()` вернули `true`; `strokes` пуст; `!isDirty`; `samePixels(EditorRenderer.render(snapshot()), base)` | 3.35, 3.36 |
| 17 | `` `dirty tracking follows saved state`() `` | `singleState()`: `addStroke(s1)` → `isDirty`; `markSaved(currentStateId)` → `!isDirty`; `addStroke(s2)` → `isDirty`; `undo()` → `!isDirty`; `undo()` → `!isDirty` (`currentStateId == 0`); `redo()` → `!isDirty`; `redo()` → `isDirty` | как в When | 3.40 |
| 18 | `` `snapshot reflects state`() `` | `twoState()`: `moveFragment(1, p(0,50))`, затем `addStroke(stroke(1))` | `snapshot().positions == positions`; `.strokes == [stroke(1)]`; `.initialPositions == [p(0,0), p(100,50)]`; `.fillRgb == 0x808080`; `.base === base` | — |
| 19 | `` `invalid sources rejected`() `` | `EditorState(img(30,20), [r(0,0,20,20), r(10,0,20,20)], 0, true)` | `assertFailsWith<IllegalArgumentException>` | §9 №1 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.editor.EditorStateTest"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

## 13. Критерии готовности (Definition of Done)

- [ ] Сигнатуры совпадают с разделом 6.
- [ ] Тесты 1–19 написаны и проходят.
- [ ] История не ограничена по глубине; `redo` не очищает стопку повтора.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не хранить копии изображения в истории.
- Не добавлять масштаб, жесты мыши, подсказки — это `EditorController` (TASK-037).
- Не делать класс потокобезопасным (используется только на EDT).

## 15. Ловушки и подсказки

- В `redo` добавляй команду в `undoStack` напрямую, **не** через `push` — иначе очистится стопка повтора.
- `positions` и `strokes` присваивай новыми неизменяемыми списками (`strokes + stroke`, `dropLast(1)`), не изменяй
  списки на месте: `snapshot()` отдаёт эти же списки в другой поток.
- `ArrayList.removeLastOrNull()` — функция Kotlin stdlib для `MutableList`; на JDK 21+ есть ещё Java-метод
  `removeLast()`, который бросает исключение на пустом списке — используй именно `removeLastOrNull()`.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

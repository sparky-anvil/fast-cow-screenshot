# TASK-032: Создать модель штрихов, инструменты и геометрию фрагментов `FragmentLayout`

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-004 §4, §5, §8 |
| Требования | REQ-003.15, 3.17, 3.25, 3.26, 3.27, 3.28, 3.32, N7; US-013 AC-2, AC-3, AC-5, AC-6, AC-7 |
| ADR | ADR-006 |
| Зависит от | — |
| Блокирует | TASK-033, TASK-034 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать новый пакет `editor` и в нём: типы штриха (`StrokePoint`, `Stroke`), перечисление инструментов `EditorTool`
и объект `FragmentLayout` с чистыми функциями геометрии фрагментов (границы, нормализация, пересечение, ограничение
размера, склейка, попадание точки).

## 2. Контекст

Редактор (TDD-004) показывает снимок как набор **фрагментов**. У снимка режима «Несколько областей» каждый регион —
отдельный фрагмент; у остальных режимов один фрагмент — весь снимок. У фрагмента есть неизменяемый размер (ширина и
высота в физических пикселях) и текущая позиция — левый верхний угол на итоговом изображении. Список «прямоугольников
фрагментов» (`List<PixelRect>`) = размер фрагмента + текущая позиция.

Правила:
- фрагменты не пересекаются: общая площадь двух прямоугольников должна быть 0; касание сторонами разрешено;
- итоговое изображение — ограничивающий прямоугольник всех фрагментов; после каждого изменения всё сдвигается так,
  чтобы его левый верхний угол был в (0, 0) (**нормализация**);
- ширина изображения не больше суммы ширин всех фрагментов, высота — не больше суммы высот;
- «Склеить» — существующий алгоритм `StitchLayout.compute` (удаление пустых полос по X и по Y).

Штрих карандаша — ломаная в координатах изображения (`Float`, пиксель `(i, j)` занимает `[i, i+1) × [j, j+1)`),
цвет `0xRRGGBB` и толщина в пикселях изображения.

Пакет `editor` может использовать только `domain` и `java.awt` (Java2D) — без Compose, Swing, JNA, `settings`,
`capture`, `ui` (conventions §3).

**Выдержки из требований (дословно):**

> **REQ-003.25** Фрагменты не должны пересекаться: никакой пиксель не может принадлежать двум фрагментам. Касание по стороне допускается. …
>
> **REQ-003.26** Итоговое изображение — ограничивающий прямоугольник всех фрагментов. Его левый верхний угол всегда (0, 0): холст сдвигается после каждого перемещения. …
>
> **REQ-003.27** Фрагмент можно перетащить за текущие границы изображения: изображение расширяется. Система ограничивает перемещение так, чтобы ширина изображения не превышала сумму ширин всех фрагментов, а высота — сумму их высот.
>
> **REQ-003.28** Действие «Склеить» применяет алгоритм REQ-002.20 к текущим позициям фрагментов: удаляет пустые полосы по X и по Y и сохраняет взаимное расположение фрагментов. Если пустых полос нет, раскладка не меняется.
>
> **US-013 AC-2** Given раскладка A (0,0) 100×50, B (100,50) 200×100 When пользователь перетаскивает B так, что его левый верхний угол оказывается в (0,50) Then изображение 200×150: A в (0,0), B в (0,50).
>
> **US-013 AC-3** Given раскладка из AC-2 When пользователь тащит A влево на 30 px Then изображение 230×150; A в (0,0), B в (30,50).
>
> **US-013 AC-5** Given A шириной 100 px в (0,0) When пользователь ставит B левым краем в x = 100 на той же высоте Then фрагменты стоят вплотную, перемещение принято.
>
> **US-013 AC-7** Given фрагменты 100×50 и 200×100 When пользователь тащит фрагмент далеко вправо Then ширина изображения не превышает 300 px, высота — 150 px.

## 3. Предусловия

- В проекте уже существуют (менять нельзя):
  - `domain/PixelRect.kt` — `data class PixelRect(left, top, width, height)`, `right`, `bottom`, `isEmpty`,
    `contains(PixelPoint)`, `intersect(other): PixelRect?` (null при нулевой площади), `translate(dx, dy)`,
    `companion fun union(rects: List<PixelRect>): PixelRect` (бросает при пустом списке);
  - `domain/PixelPoint.kt` — `data class PixelPoint(x: Int, y: Int)`;
  - `domain/StitchLayout.kt` — `StitchLayout.compute(regions: List<PixelRect>): StitchLayout`,
    `StitchLayout(width, height, placements: List<StitchPlacement>)`,
    `StitchPlacement(source: PixelRect, targetLeft: Int, targetTop: Int)`; `compute` бросает
    `IllegalArgumentException` при пустом списке, пустом или пересекающихся прямоугольниках.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/Stroke.kt` | Создать | `StrokePoint`, `Stroke` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/EditorTool.kt` | Создать | `EditorTool` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/FragmentLayout.kt` | Создать | `FragmentLayout` |
| 4 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/editor/FragmentLayoutTest.kt` | Создать | тесты 1–16 |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/editor/StrokeTest.kt` | Создать | тесты 17–18 |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
// editor/Stroke.kt
package by.sparkyanvil.fastcowscreenshot.editor

/** Точка штриха в координатах итогового изображения (физические пиксели, дробные). */
internal data class StrokePoint(val x: Float, val y: Float)

/**
 * Штрих карандаша. Неизменяем.
 * @property rgb цвет 0xRRGGBB (без альфы, штрих непрозрачный).
 * @property width толщина в пикселях изображения (> 0).
 * @property points точки ломаной в порядке рисования; 1 точка — «точка» диаметром width.
 * @throws IllegalArgumentException points пуст ("stroke must have points") или width <= 0 ("width must be positive: <w>").
 */
internal data class Stroke(val rgb: Int, val width: Int, val points: List<StrokePoint>)
```

```kotlin
// editor/EditorTool.kt
package by.sparkyanvil.fastcowscreenshot.editor

/** Инструменты редактора (REQ-003.8). */
internal enum class EditorTool { MOVE, PENCIL }
```

```kotlin
// editor/FragmentLayout.kt
package by.sparkyanvil.fastcowscreenshot.editor

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect

/** Чистая геометрия фрагментов редактора (TDD-004 §8). Без состояния, потокобезопасен. */
internal object FragmentLayout {
    /**
     * Прямоугольники фрагментов на изображении: i-й = размер sources[i] в позиции positions[i].
     * @throws IllegalArgumentException если размеры списков разные ("sizes differ: <a> vs <b>").
     */
    fun placed(sources: List<PixelRect>, positions: List<PixelPoint>): List<PixelRect> = TODO()

    /** Сдвиг, переносящий левый верхний угол PixelRect.union(rects) в (0,0): PixelPoint(-left, -top). rects не пуст. */
    fun normalizeShift(rects: List<PixelRect>): PixelPoint = TODO()

    /** true, если candidate имеет ненулевую общую площадь хотя бы с одним rects[j], j != index. */
    fun intersectsAny(rects: List<PixelRect>, index: Int, candidate: PixelRect): Boolean = TODO()

    /**
     * Ограничивает позицию фрагмента index (REQ-003.27): после переноса rects[index] в результат ширина
     * ограничивающего прямоугольника ≤ суммы ширин всех rects, высота ≤ суммы высот. Если фрагмент один — candidate.
     */
    fun clampPosition(rects: List<PixelRect>, index: Int, candidate: PixelPoint): PixelPoint = TODO()

    /** true, если union(rects).width ≤ Σ width и union(rects).height ≤ Σ height. */
    fun withinLimits(rects: List<PixelRect>): Boolean = TODO()

    /**
     * Позиции после «Склеить» (REQ-003.28) в порядке rects: StitchLayout.compute(rects), для каждого rects[i]
     * — (targetLeft, targetTop) его placement. rects: непустые, непересекающиеся.
     */
    fun stitchPositions(rects: List<PixelRect>): List<PixelPoint> = TODO()

    /** Индекс первого rects[i], для которого rects[i].contains(point); null — ни одного. */
    fun fragmentAt(rects: List<PixelRect>, point: PixelPoint): Int? = TODO()
}
```

## 7. Пошаговая реализация

### Шаг 1. `Stroke.kt`, `EditorTool.kt`
- По разделу 6. В `Stroke` — блок `init` с двумя `require` (тексты из KDoc).
- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 2. `FragmentLayout.kt`
- По разделам 6 и 8.
- **Результат:** компиляция проходит.

### Шаг 3. Тесты — раздел 11. **Результат:** `.\gradlew.bat build` проходит.

## 8. Алгоритм и логика

```
placed(sources, positions):
    require(sources.size == positions.size) { "sizes differ: ${sources.size} vs ${positions.size}" }
    return sources.indices.map { i -> PixelRect(positions[i].x, positions[i].y, sources[i].width, sources[i].height) }

normalizeShift(rects):
    b = PixelRect.union(rects); return PixelPoint(-b.left, -b.top)

intersectsAny(rects, index, candidate):
    return rects.indices.any { j -> j != index && rects[j].intersect(candidate) != null }

clampPosition(rects, index, candidate):
    if (rects.size == 1) return candidate
    others = rects.filterIndexed { j, _ -> j != index }
    o = PixelRect.union(others)
    w = rects[index].width; h = rects[index].height
    sumW = rects.sumOf { it.width }; sumH = rects.sumOf { it.height }
    x = candidate.x.coerceIn(o.right - sumW, o.left + sumW - w)
    y = candidate.y.coerceIn(o.bottom - sumH, o.top + sumH - h)
    return PixelPoint(x, y)

withinLimits(rects):
    b = PixelRect.union(rects)
    return b.width <= rects.sumOf { it.width } && b.height <= rects.sumOf { it.height }

stitchPositions(rects):
    layout = StitchLayout.compute(rects)
    byRect = layout.placements.associateBy { it.source }
    return rects.map { r -> val p = byRect.getValue(r); PixelPoint(p.targetLeft, p.targetTop) }

fragmentAt(rects, point):
    i = rects.indexOfFirst { it.contains(point) }; return if (i < 0) null else i
```

Почему диапазон `clampPosition` верный: ширина объединения `max(o.right, x + w) − min(o.left, x) ≤ sumW`
выполняется тогда и только тогда, когда `x ≥ o.right − sumW` и `x + w ≤ o.left + sumW`. Диапазон не пуст, так как
`o.width + w ≤ sumW + sumW`.

**Пример расчёта** (фрагменты US-013 AC-1/AC-7): `A = PixelRect(0, 0, 100, 50)`, `B = PixelRect(100, 50, 200, 100)`.
- `clampPosition([A, B], 1, PixelPoint(1000, 50))`: `o = A`, `sumW = 300`, `sumH = 150`;
  `x ∈ [100 − 300, 0 + 300 − 200] = [−200, 100]` → 100; `y ∈ [50 − 150, 0 + 150 − 100] = [−100, 50]` → 50.
  Результат `(100, 50)`, ширина объединения `300`.
- `clampPosition([A, B], 1, PixelPoint(0, 5000))` → `(0, 50)`.
- AC-3: `rects = [PixelRect(-30, 0, 100, 50), PixelRect(0, 50, 200, 100)]` → `normalizeShift = (30, 0)`.
- Склейка: `[PixelRect(0, 0, 100, 50), PixelRect(150, 80, 200, 100)]` → `stitchPositions = [(0, 0), (100, 50)]`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение |
|---|---|---|
| 1 | `placed` с разными размерами списков | `IllegalArgumentException("sizes differ: 2 vs 1")` |
| 2 | `normalizeShift`, `withinLimits` с пустым списком | исключение `PixelRect.union` (вызывающий код так не делает) |
| 3 | Касание сторонами | `intersectsAny == false` |
| 4 | Один фрагмент | `clampPosition` возвращает `candidate`; `intersectsAny == false` |
| 5 | `stitchPositions` с пересекающимися | исключение `StitchLayout.compute` (вызывающий код так не делает) |
| 6 | `Stroke` без точек / с `width = 0` | `IllegalArgumentException` с текстом из KDoc |

## 10. Константы и тексты

Нет.

## 11. Тесты

`FragmentLayoutTest.kt`; `A = PixelRect(0, 0, 100, 50)`, `B = PixelRect(100, 50, 200, 100)`, `r(...) = PixelRect(...)`,
`p(x, y) = PixelPoint(x, y)`:

| # | Метод | When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `placed applies positions keeping sizes`() `` | `placed([r(5,5,10,20), r(0,0,3,4)], [p(1,2), p(30,40)])` | `[r(1,2,10,20), r(30,40,3,4)]` | — |
| 2 | `` `placed rejects size mismatch`() `` | `placed([A, B], [p(0,0)])` | `assertFailsWith<IllegalArgumentException>` | §9 №1 |
| 3 | `` `normalize shift after dragging left`() `` | `normalizeShift([r(-30,0,100,50), r(0,50,200,100)])` | `p(30, 0)` | 3.26, AC-3 |
| 4 | `` `normalize shift is zero for normalized layout`() `` | `normalizeShift([A, B])` | `p(0, 0)` | 3.26 |
| 5 | `` `overlap is detected`() `` | `intersectsAny([A, B], 1, r(50,20,200,100))` | `true` | 3.25 |
| 6 | `` `touching sides is allowed`() `` | `intersectsAny([A, B], 1, r(100,0,200,100))` | `false` | 3.25, AC-5 |
| 7 | `` `fragment does not intersect itself`() `` | `intersectsAny([A, B], 0, A)` | `false` | — |
| 8 | `` `clamp limits width to sum of widths`() `` | `clampPosition([A, B], 1, p(1000, 50))` | `p(100, 50)`; `withinLimits(placed-результат [A, r(100,50,200,100)]) == true` | 3.27, AC-7 |
| 9 | `` `clamp limits height to sum of heights`() `` | `clampPosition([A, B], 1, p(0, 5000))` | `p(0, 50)` | 3.27, AC-7 |
| 10 | `` `clamp limits negative direction`() `` | `clampPosition([A, B], 1, p(-1000, -1000))` | `p(-200, -100)` | 3.27 |
| 11 | `` `clamp keeps allowed position`() `` | `clampPosition([A, B], 1, p(0, 50))` | `p(0, 50)` | AC-2 |
| 12 | `` `clamp single fragment returns candidate`() `` | `clampPosition([A], 0, p(-500, 700))` | `p(-500, 700)` | 3.32 |
| 13 | `` `within limits detects too wide layout`() `` | `withinLimits([A, r(500,0,200,100)])` | `false` (ширина 700 > 300) | 3.27 |
| 14 | `` `stitch removes gaps keeping order`() `` | `stitchPositions([r(0,0,100,50), r(150,80,200,100)])` | `[p(0,0), p(100,50)]` | 3.28, AC-6 |
| 15 | `` `stitch without gaps keeps positions`() `` | `stitchPositions([r(0,0,100,50), r(30,50,200,100)])` | `[p(0,0), p(30,50)]` | 3.28 |
| 16 | `` `fragment at point`() `` | `fragmentAt([A, B], p(150, 60))`, `p(99, 49)`, `p(100, 49)` | `1`, `0`, `null` | 3.24 |

`StrokeTest.kt`:

| # | Метод | Then |
|---|---|---|
| 17 | `` `stroke requires points`() `` | `Stroke(0xFF0000, 4, emptyList())` → `assertFailsWith<IllegalArgumentException>` |
| 18 | `` `stroke requires positive width`() `` | `Stroke(0xFF0000, 0, listOf(StrokePoint(1f, 1f)))` → `IllegalArgumentException`; `Stroke(0xFF0000, 2, listOf(StrokePoint(1f, 1f)))` создаётся |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.editor.*"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручной проверки нет.

## 13. Критерии готовности (Definition of Done)

- [ ] Сигнатуры совпадают с разделом 6.
- [ ] Тесты 1–18 написаны и проходят.
- [ ] В пакете `editor` нет импортов `androidx.compose`, `javax.swing`, `com.sun.jna`, `…settings`, `…capture`, `…ui`.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не менять `StitchLayout`, `PixelRect`, `PixelPoint`.
- Не создавать `EditorState`, `EditorSnapshot`, `EditorRenderer` (TASK-033, TASK-034).
- Не делать `FragmentLayout` классом с состоянием.

## 15. Ловушки и подсказки

- `PixelRect.intersect` возвращает `null` и при касании (нулевая площадь) — это и есть «касание разрешено».
- `coerceIn(min, max)` бросает исключение, если `min > max`; при корректных входных данных так не бывает (см. §8),
  поэтому дополнительных проверок не нужно.
- `associateBy { it.source }`: `PixelRect` — `data class`, сравнение по значениям; непересекающиеся непустые
  прямоугольники всегда различны.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

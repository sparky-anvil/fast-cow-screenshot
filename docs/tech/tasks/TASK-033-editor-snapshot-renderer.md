# TASK-033: Создать неизменяемый снимок состояния `EditorSnapshot` и рендер итога `EditorRenderer`

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-004 §5, §8 |
| Требования | REQ-003.11, 3.15, 3.16, 3.17, 3.20, 3.26, 3.31, 3.36, 3.43, N5, N7; US-011 AC-4, US-012 AC-2, AC-5, AC-6, US-014 AC-5 |
| ADR | ADR-006 |
| Зависит от | TASK-032 |
| Блокирует | TASK-034, TASK-036 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать `EditorSnapshot` — неизменяемое описание состояния редактора (исходный снимок, фрагменты, их позиции, цвет
заливки, штрихи) — и `EditorRenderer.render(snapshot)`, который строит итоговое изображение в физических пикселях
(Java2D). Рендер вызывается на потоке `fcs-capture` при «Готово», «Копировать», «Сохранить как…» (TASK-036).

## 2. Контекст

Модель редактора (TDD-004): исходный снимок `base` (`BufferedImage`, `TYPE_INT_RGB`) **никогда не изменяется**.
Фрагменты — прямоугольники `sources` на `base`. Для снимка режима «Несколько областей» это места регионов на склеенном
изображении; для остальных режимов — один прямоугольник `PixelRect(0, 0, base.width, base.height)`. Позиции
`positions[i]` — левый верхний угол фрагмента `i` на итоговом изображении; они нормализованы (минимальный `left` = 0,
минимальный `top` = 0). `initialPositions` — позиции при открытии = левые верхние углы `sources`.

Правила рендера:
1. Если `positions == initialPositions` (раскладка не менялась), итог = **точная копия** `base`. Это важно: на `base`
   может быть нарисован указатель мыши, выходящий за край фрагмента на заливку, и снимок без изменений должен совпасть
   со снимком без редактора пиксель в пиксель.
2. Иначе итог = заливка `fillRgb` размером ограничивающего прямоугольника + пиксели каждого фрагмента, скопированные из
   `base` построчно в его позицию.
3. Поверх — штрихи по порядку: сглаживание, скруглённые концы и стыки, толщина `Stroke.width` пикселей изображения.
   Штрих из одной точки — закрашенный круг диаметром `width`. Всё за границами изображения отсекается автоматически
   (рисуем в `BufferedImage` фиксированного размера).

Координаты штриха: пиксель `(i, j)` занимает `[i, i+1) × [j, j+1)`; точка `(10.0, 10.0)` — угол пикселя.

**Выдержки из требований (дословно):**

> **REQ-003.11** Масштаб и прокрутка влияют только на отображение. Итоговое изображение всегда в физических пикселях исходного снимка без масштабирования.
>
> **REQ-003.15** Инструмент «Карандаш» рисует штрих произвольной формы … Штрих — непрерывная линия со скруглёнными концами и сглаживанием, без разрывов при быстром движении мыши.
>
> **REQ-003.16** Одиночный клик без движения ставит точку диаметром, равным толщине.
>
> **REQ-003.20** Штрих рисуется только в пределах итогового изображения. Часть штриха за его границами отсекается, размер изображения не меняется.
>
> **REQ-003.26** … Пиксели вне фрагментов заливаются цветом REQ-002.21.
>
> **REQ-003.43** «Готово» без изменений даёт изображение, идентичное снимку, который был бы получен при выключенном редакторе.
>
> **REQ-003.N5** Пиксели, которых не касались штрихи, совпадают с исходным снимком … «Готово» без изменений — 0 отличающихся пикселей относительно снимка без редактора. Перемещённый фрагмент совпадает с исходной областью пиксель в пиксель.

## 3. Предусловия

- TASK-032: `editor/Stroke.kt` (`Stroke(rgb, width, points)`, `StrokePoint(x, y)`),
  `editor/FragmentLayout.kt` (`placed(sources, positions)`).
- Существуют: `domain/PixelRect.kt`, `domain/PixelPoint.kt`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/EditorSnapshot.kt` | Создать | `EditorSnapshot` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/EditorRenderer.kt` | Создать | `EditorRenderer` |
| 3 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/editor/EditorSnapshotTest.kt` | Создать | тесты 1–5 |
| 4 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/editor/EditorRendererTest.kt` | Создать | тесты 6–13 |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.editor

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import java.awt.image.BufferedImage

/**
 * Неизменяемое состояние редактора для рендера (TDD-004). base не изменяется никем, поэтому снимок можно передавать
 * между потоками.
 * @property base исходный снимок TYPE_INT_RGB.
 * @property sources прямоугольники фрагментов на base.
 * @property positions текущие левые верхние углы фрагментов на итоге (нормализованы: min left = 0, min top = 0).
 * @property initialPositions позиции при открытии (= левые верхние углы sources).
 * @property fillRgb цвет пустот 0xRRGGBB.
 * @property strokes штрихи в порядке рисования.
 */
internal data class EditorSnapshot(
    val base: BufferedImage,
    val sources: List<PixelRect>,
    val positions: List<PixelPoint>,
    val initialPositions: List<PixelPoint>,
    val fillRgb: Int,
    val strokes: List<Stroke>,
) {
    /** Ширина итога: max(right) прямоугольников FragmentLayout.placed(sources, positions). */
    val width: Int get() = TODO()

    /** Высота итога: max(bottom) тех же прямоугольников. */
    val height: Int get() = TODO()

    companion object {
        /**
         * Снимок при открытии редактора: positions = initialPositions = sources.map { PixelPoint(it.left, it.top) },
         * strokes пуст.
         * @throws IllegalArgumentException если sources невалидны (см. requireValidSources).
         */
        fun initial(base: BufferedImage, sources: List<PixelRect>, fillRgb: Int): EditorSnapshot = TODO()

        /**
         * Проверяет: sources не пуст ("sources must not be empty"); нет пустых ("empty source: <rect>"); каждый внутри
         * base ("source outside base: <rect>"); попарно не пересекаются ("sources intersect: <a> and <b>");
         * PixelRect.union(sources) == PixelRect(0, 0, base.width, base.height) ("sources must cover base bounds").
         * @throws IllegalArgumentException с указанным текстом.
         */
        fun requireValidSources(base: BufferedImage, sources: List<PixelRect>): Unit = TODO()
    }
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.editor

import java.awt.image.BufferedImage

/** Итоговое изображение редактора (Java2D). Без состояния. Поток: fcs-capture (любой, кроме EDT, по соглашениям). */
internal object EditorRenderer {
    /**
     * Строит новое изображение TYPE_INT_RGB размером snapshot.width × snapshot.height (алгоритм §8).
     * snapshot.base не изменяется.
     * @throws IllegalArgumentException если base не TYPE_INT_RGB ("base must be TYPE_INT_RGB").
     */
    fun render(snapshot: EditorSnapshot): BufferedImage = TODO()
}
```

## 7. Пошаговая реализация

### Шаг 1. `EditorSnapshot.kt`
- По разделам 6 и 8. `width`/`height` — вычисляемые свойства (`get()`), не поля конструктора.
- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 2. `EditorRenderer.kt`
- По разделу 8. **Результат:** компиляция проходит.

### Шаг 3. Тесты — раздел 11. **Результат:** `.\gradlew.bat build` проходит.

## 8. Алгоритм и логика

**`requireValidSources(base, sources)`**:

```
require(sources.isNotEmpty()) { "sources must not be empty" }
for s in sources: require(!s.isEmpty) { "empty source: $s" }
for s in sources: require(s.left >= 0 && s.top >= 0 && s.right <= base.width && s.bottom <= base.height) { "source outside base: $s" }
for i < j: require(sources[i].intersect(sources[j]) == null) { "sources intersect: ${sources[i]} and ${sources[j]}" }
require(PixelRect.union(sources) == PixelRect(0, 0, base.width, base.height)) { "sources must cover base bounds" }
```

**`initial`**: `requireValidSources(base, sources)`; `val start = sources.map { PixelPoint(it.left, it.top) }`;
`EditorSnapshot(base, sources, start, start, fillRgb, emptyList())`.

**`render(snapshot)`**:

```
base = snapshot.base
require(base.type == BufferedImage.TYPE_INT_RGB && base.raster.dataBuffer is DataBufferInt) { "base must be TYPE_INT_RGB" }
w = snapshot.width; h = snapshot.height
out = BufferedImage(w, h, TYPE_INT_RGB)
src = (base.raster.dataBuffer as DataBufferInt).data
dst = (out.raster.dataBuffer as DataBufferInt).data
if (snapshot.positions == snapshot.initialPositions):
    System.arraycopy(src, 0, dst, 0, src.size)            // w == base.width, h == base.height (requireValidSources)
else:
    dst.fill(snapshot.fillRgb)
    for i in sources.indices:
        s = sources[i]; p = positions[i]
        for row in 0 until s.height:
            System.arraycopy(src, (s.top + row) * base.width + s.left, dst, (p.y + row) * w + p.x, s.width)
if (snapshot.strokes.isNotEmpty()):
    g = out.createGraphics()
    try:
        g.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON)
        g.setRenderingHint(KEY_STROKE_CONTROL, VALUE_STROKE_PURE)
        g.setRenderingHint(KEY_RENDERING, VALUE_RENDER_QUALITY)
        for stroke in strokes: drawStroke(g, stroke)
    finally: g.dispose()
return out
```

**Эталонный фрагмент** (`drawStroke`, private-функция в `EditorRenderer.kt`):

```kotlin
private fun drawStroke(g: Graphics2D, stroke: Stroke) {
    g.color = Color(stroke.rgb)
    val first = stroke.points[0]
    if (stroke.points.size == 1) {
        val d = stroke.width.toDouble()
        g.fill(Ellipse2D.Double(first.x - d / 2, first.y - d / 2, d, d))
        return
    }
    g.stroke = BasicStroke(stroke.width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    val path = Path2D.Float()
    path.moveTo(first.x, first.y)
    for (k in 1 until stroke.points.size) path.lineTo(stroke.points[k].x, stroke.points[k].y)
    g.draw(path)
}
```

`java.awt.Color(rgb: Int)` — непрозрачный цвет из `0xRRGGBB`. Импорт `Stroke` здесь — наш
`by.sparkyanvil.fastcowscreenshot.editor.Stroke` (тот же пакет), а не `java.awt.Stroke`: **не** импортировать
`java.awt.Stroke`; `BasicStroke` импортировать как `java.awt.BasicStroke`.

**Пример расчёта:** `base` 30×20, `sources = [A = r(0,0,10,10), B = r(10,10,20,10)]`,
`positions = [p(0,0), p(0,10)]` → `placed = [r(0,0,10,10), r(0,10,20,10)]` → `width = 20`, `height = 20`.
Строка 0 фрагмента B: из индекса `10·30 + 10 = 310` в `10·20 + 0 = 200`, длина 20.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение |
|---|---|---|
| 1 | Невалидные `sources` | `IllegalArgumentException` с текстом из §6 |
| 2 | `base` не `TYPE_INT_RGB` | `IllegalArgumentException("base must be TYPE_INT_RGB")` |
| 3 | Штрих за границей | отсекается, размер не меняется |
| 4 | Нет штрихов | `Graphics2D` не создаётся |

## 10. Константы и тексты

Тексты исключений — из §6 и §8 дословно.

## 11. Тесты

Помощники в тестах: `img(w, h, fn: (x, y) -> Int)` — `BufferedImage(w, h, TYPE_INT_RGB)`, пиксель `setRGB(x, y, fn(x, y))`;
`rgb(img, x, y) = img.getRGB(x, y) and 0xFFFFFF`; `enc(x, y) = x * 256 + y`; `white(w, h) = img(w, h) { _, _ -> 0xFFFFFF }`.

`EditorSnapshotTest.kt`:

| # | Метод | Given / When | Then |
|---|---|---|---|
| 1 | `` `initial uses source corners`() `` | `initial(img(30,20){enc}, [r(0,0,10,10), r(10,10,20,10)], 0x808080)` | `positions == initialPositions == [p(0,0), p(10,10)]`; `strokes` пуст; `width == 30`, `height == 20` |
| 2 | `` `size follows positions`() `` | снимок №1 `.copy(positions = [p(0,0), p(0,10)])` | `width == 20`, `height == 20` |
| 3 | `` `overlapping sources rejected`() `` | `initial(img 30×20, [r(0,0,20,20), r(10,0,20,20)], 0)` | `IllegalArgumentException` |
| 4 | `` `sources must cover base`() `` | `initial(img 30×20, [r(0,0,10,10)], 0)` | `IllegalArgumentException`, `message == "sources must cover base bounds"` |
| 5 | `` `source outside base rejected`() `` | `initial(img 30×20, [r(0,0,30,20), r(30,0,5,5)], 0)` | `IllegalArgumentException`, сообщение начинается с `"source outside base"` |

`EditorRendererTest.kt`:

| # | Метод | Given | Then | Покрывает |
|---|---|---|---|---|
| 6 | `` `unchanged layout copies base exactly`() `` | `base = img(6,4){enc}`, `initial(base, [r(0,0,6,4)], 0)` | результат 6×4, `TYPE_INT_RGB`, все пиксели равны `base`; `result !== base` | 3.43, N5 |
| 7 | `` `unchanged multi layout keeps pixels outside fragments`() `` | `base = img(30,20){enc}`, затем `base.setRGB(15, 5, 0xABCDEF)` (на заливке); `initial(base, [r(0,0,10,10), r(10,10,20,10)], 0x123456)` | `rgb(result, 15, 5) == 0xABCDEF`; все пиксели равны `base` | 3.43 |
| 8 | `` `moved fragment is pixel exact and gaps filled`() `` | снимок №7 `.copy(positions = [p(0,0), p(0,10)])` | 20×20; `rgb(0,10) == enc(10,10)`; `rgb(19,19) == enc(29,19)`; `rgb(9,9) == enc(9,9)`; `rgb(15,5) == 0x123456` | 3.26, 3.31, N5 |
| 9 | `` `render does not modify base`() `` | снимок №6 со штрихом `Stroke(0xFF0000, 4, [sp(0,0), sp(6,4)])` | после `render` все пиксели `base` == `enc(x, y)` | — |
| 10 | `` `stroke uses color and width in image pixels`() `` | `white(40,20)`, один фрагмент, штрих `Stroke(0xFF0000, 4, [sp(5,10), sp(35,10)])` | `rgb(20,8)`, `rgb(20,9)`, `rgb(20,10)`, `rgb(20,11)` == `0xFF0000`; `rgb(20,5)` и `rgb(20,14)` == `0xFFFFFF` | 3.15, 3.17, US-012 AC-2 |
| 11 | `` `single point draws dot of stroke width`() `` | `white(30,30)`, `Stroke(0x0000FF, 8, [sp(15,15)])` | `rgb(15,15)` и `rgb(13,15)` == `0x0000FF`; `rgb(15,21)`, `rgb(15,8)`, `rgb(21,15)` == `0xFFFFFF` | 3.16, US-012 AC-5 |
| 12 | `` `stroke outside image is clipped`() `` | `white(20,10)`, `Stroke(0xFF0000, 4, [sp(10,5), sp(100,5)])` | результат 20×10; `rgb(19,5) == 0xFF0000` | 3.20, US-012 AC-6 |
| 13 | `` `strokes drawn in order`() `` | `white(20,10)`, `strokes = [Stroke(0xFF0000, 4, [sp(2,5), sp(18,5)]), Stroke(0x0000FF, 4, [sp(2,5), sp(18,5)])]` | `rgb(10,5) == 0x0000FF` | 3.33 |

`sp(x, y) = StrokePoint(x.toFloat(), y.toFloat())`. Для одного фрагмента в тестах 10–13:
`EditorSnapshot.initial(base, listOf(PixelRect(0, 0, base.width, base.height)), 0).copy(strokes = …)`.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.editor.*"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

## 13. Критерии готовности (Definition of Done)

- [ ] Сигнатуры совпадают с разделом 6.
- [ ] Тесты 1–13 написаны и проходят.
- [ ] `base` нигде не изменяется (нет `setRGB`/`createGraphics` на `base` в main-коде).
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать `getRGB`/`setRGB` в цикле по пикселям в main-коде — только `System.arraycopy` и `fill`.
- Не создавать `EditorState` (TASK-034).
- Не масштабировать изображение.

## 15. Ловушки и подсказки

- `java.awt.Stroke` и наш `Stroke` — разные типы. В `EditorRenderer.kt` не импортируй `java.awt.*` целиком
  (`import java.awt.*` подменит имя); импортируй по одному: `java.awt.BasicStroke`, `java.awt.Color`,
  `java.awt.Graphics2D`, `java.awt.RenderingHints`, `java.awt.geom.Ellipse2D`, `java.awt.geom.Path2D`.
- Сравнение `positions == initialPositions` — сравнение списков `data class` по значениям; это правильно.
- Полностью покрытые штрихом пиксели при сглаживании получают ровно цвет штриха — поэтому тесты 10–13 сравнивают точное
  значение. Не проверяй в тестах пиксели на самой границе штриха (там полутон).
- `TYPE_INT_RGB` хранит `0x00RRGGBB`; `getRGB` возвращает `0xFFRRGGBB` — в тестах маскировать `and 0xFFFFFF`.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

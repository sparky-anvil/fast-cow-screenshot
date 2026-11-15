# TASK-035: Создать модель масштаба `Viewport` и таблицу клавиш редактора `EditorKeymap`

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-004 §8, §10 |
| Требования | REQ-003.9, 3.10, 3.29, 3.34, 3.37 – 3.40 (клавиши), N7; US-014 AC-1 – AC-4 |
| ADR | ADR-006 |
| Зависит от | — |
| Блокирует | TASK-037 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать две чистые (без UI) части редактора:
1. `editor.Viewport` — масштаб и сдвиг отображения снимка на холсте: «вписать», масштаб относительно точки, прокрутка,
   ограничение сдвига, перевод координат холста в координаты изображения.
2. `ui.EditorKeymap` + `ui.EditorKeyAction` — перевод нажатой клавиши (код AWT `VK_*` и модификаторы) в действие
   редактора.

## 2. Контекст

**Координаты.** Холст — область окна, где рисуется снимок; координаты холста — физические пиксели (Compose `Canvas`
работает в px). Снимок размером `imageWidth × imageHeight` рисуется так: точка изображения `(ix, iy)` попадает в точку
холста `(offsetX + ix·zoom, offsetY + iy·zoom)`. Обратно: `ix = (cx − offsetX) / zoom`. `zoom = 1.0` — 100 %: один
пиксель снимка = один физический пиксель экрана.

**Ограничение сдвига** (по каждой оси отдельно): если снимок при текущем масштабе помещается в холст
(`imageWidth·zoom ≤ canvasWidth`), он центрируется: `offsetX = (canvasWidth − imageWidth·zoom) / 2`. Иначе сдвиг
ограничивается так, чтобы за краем снимка не было пустоты: `offsetX ∈ [canvasWidth − imageWidth·zoom, 0]`.

**Клавиши** редактора фиксированы. В Compose код клавиши — `e.key.nativeKeyCode` (AWT `VK_*`), модификаторы —
`e.isCtrlPressed`, `e.isAltPressed`, `e.isShiftPressed` (conventions §9). Решение выносится в чистую функцию —
её и делает эта задача. Подключение к окну — TASK-038.

**Выдержки из требований (дословно):**

> **REQ-003.9** При открытии снимок вписывается в холст целиком: масштаб `min(100 %, по ширине, по высоте)`. Сверх 100 % снимок при открытии не увеличивается.
>
> **REQ-003.10** Масштаб меняется сочетанием `Ctrl` + колесо мыши (относительно точки под курсором), `Ctrl+0` — вписать в окно, `Ctrl+1` — 100 %. Диапазон — от 10 % до 800 %. Текущий масштаб показывается в процентах. Если снимок больше холста, есть прокрутка (колесо — по вертикали, `Shift` + колесо — по горизонтали).
>
> **REQ-003.29** Фрагменты можно двигать стрелками клавиатуры: выбранный … фрагмент сдвигается на 1 px, с `Shift` — на 10 px. …
>
> **REQ-003.34** «Отменить» (`Ctrl+Z`, кнопка) … «Повторить» (`Ctrl+Y` и `Ctrl+Shift+Z`, кнопка) …
>
> **REQ-003.37** «Готово» (кнопка, `Ctrl+Enter`, `Ctrl+S`) …
>
> **REQ-003.38** «Копировать» (кнопка, `Ctrl+C`) …
>
> **REQ-003.39** «Сохранить как…» (кнопка, `Ctrl+Shift+S`) …
>
> **REQ-003.40** «Отмена» (кнопка, `Esc`, закрытие окна) …
>
> **US-013, заметка UI:** **Допущение:** клавиши `V` («Перемещение») и `P` («Карандаш»).
>
> **US-014 AC-2** Given снимок 300×200 When редактор открывается Then масштаб 100 %, снимок по центру холста.

## 3. Предусловия

- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.
- Пакет `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/` может ещё не существовать (создаётся TASK-032) —
  если его нет, создай.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/editor/Viewport.kt` | Создать | `Viewport` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorKeyAction.kt` | Создать | `EditorKeyAction` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorKeymap.kt` | Создать | `EditorKeymap` |
| 4 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/editor/ViewportTest.kt` | Создать | тесты 1–12 |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorKeymapTest.kt` | Создать | тесты 13–18 |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.editor

/**
 * Масштаб и сдвиг отображения снимка на холсте (TDD-004 §8). Неизменяем, потокобезопасен.
 * Размеры холста и изображения — в физических пикселях. Точка изображения (ix, iy) рисуется в
 * (offsetX + ix * zoom, offsetY + iy * zoom).
 * @property zoom масштаб, 1.0 = 100 %, всегда в [MIN_ZOOM, MAX_ZOOM] у значений, полученных из методов класса.
 */
internal data class Viewport(val zoom: Double, val offsetX: Double, val offsetY: Double) {
    /** Масштаб в процентах, округлённый: (zoom * 100).roundToInt(). */
    val percent: Int get() = TODO()

    /** Координата X изображения для координаты X холста. */
    fun toImageX(canvasX: Float): Float = TODO()

    /** Координата Y изображения для координаты Y холста. */
    fun toImageY(canvasY: Float): Float = TODO()

    /** Ограничение сдвига по правилам §8 (центрирование или прижатие к краям). canvasWidth/Height <= 0 → this. */
    fun clamp(imageWidth: Int, imageHeight: Int, canvasWidth: Int, canvasHeight: Int): Viewport = TODO()

    /**
     * Новый масштаб newZoom (обрезается в [MIN_ZOOM, MAX_ZOOM]) так, что точка изображения под (anchorX, anchorY)
     * остаётся на месте; затем clamp.
     */
    fun zoomAt(
        newZoom: Double,
        anchorX: Double,
        anchorY: Double,
        imageWidth: Int,
        imageHeight: Int,
        canvasWidth: Int,
        canvasHeight: Int,
    ): Viewport = TODO()

    /** Прокрутка: содержимое сдвигается на (-dx, -dy) px холста, затем clamp. dy > 0 — «вниз по снимку». */
    fun scrollBy(dx: Double, dy: Double, imageWidth: Int, imageHeight: Int, canvasWidth: Int, canvasHeight: Int): Viewport =
        TODO()

    /** Сдвиг без ограничения: offsetX + dx, offsetY + dy (используется для компенсации нормализации). */
    fun shiftedBy(dx: Double, dy: Double): Viewport = TODO()

    companion object {
        const val MIN_ZOOM: Double = 0.1
        const val MAX_ZOOM: Double = 8.0
        const val ZOOM_STEP: Double = 1.25
        const val SCROLL_STEP_PX: Double = 100.0

        /**
         * «Вписать» (REQ-003.9): zoom = min(1.0, canvasWidth / imageWidth, canvasHeight / imageHeight), обрезанный в
         * [MIN_ZOOM, MAX_ZOOM]; сдвиг — clamp (центрирование). Если canvasWidth <= 0 или canvasHeight <= 0 —
         * Viewport(1.0, 0.0, 0.0).
         */
        fun fit(imageWidth: Int, imageHeight: Int, canvasWidth: Int, canvasHeight: Int): Viewport = TODO()
    }
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/** Действие редактора по клавише (TDD-004 §10). */
internal sealed interface EditorKeyAction {
    data object Done : EditorKeyAction
    data object Copy : EditorKeyAction
    data object SaveAs : EditorKeyAction
    data object Cancel : EditorKeyAction
    data object Undo : EditorKeyAction
    data object Redo : EditorKeyAction
    data object ZoomFit : EditorKeyAction
    data object Zoom100 : EditorKeyAction
    data object ToolMove : EditorKeyAction
    data object ToolPencil : EditorKeyAction

    /** Сдвиг выбранного фрагмента на (dx, dy) пикселей изображения. */
    data class Nudge(val dx: Int, val dy: Int) : EditorKeyAction
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/** Фиксированные клавиши редактора. Чистая функция, потокобезопасна. */
internal object EditorKeymap {
    /**
     * @param keyCode код AWT (java.awt.event.KeyEvent.VK_*), в Compose — e.key.nativeKeyCode.
     * @return действие по таблице §8 или null, если клавиша не используется.
     */
    fun actionFor(ctrl: Boolean, alt: Boolean, shift: Boolean, keyCode: Int): EditorKeyAction? = TODO()
}
```

## 7. Пошаговая реализация

### Шаг 1. `Viewport.kt` — по разделам 6 и 8. **Результат:** компиляция.
### Шаг 2. `EditorKeyAction.kt`, `EditorKeymap.kt` — по разделам 6 и 8. **Результат:** компиляция.
### Шаг 3. Тесты — раздел 11. **Результат:** `.\gradlew.bat build` проходит.

## 8. Алгоритм и логика

**`Viewport`**:

```
percent = (zoom * 100).roundToInt()
toImageX(cx) = ((cx - offsetX) / zoom).toFloat()
toImageY(cy) = ((cy - offsetY) / zoom).toFloat()

clamp(iw, ih, cw, ch):
    if (cw <= 0 || ch <= 0) return this
    copy(offsetX = axis(offsetX, iw * zoom, cw), offsetY = axis(offsetY, ih * zoom, ch))
  где axis(off, content, canvas) = if (content <= canvas) (canvas - content) / 2.0
                                   else off.coerceIn(canvas - content, 0.0)

fit(iw, ih, cw, ch):
    if (cw <= 0 || ch <= 0) return Viewport(1.0, 0.0, 0.0)
    z = minOf(1.0, cw.toDouble() / iw, ch.toDouble() / ih).coerceIn(MIN_ZOOM, MAX_ZOOM)
    return Viewport(z, 0.0, 0.0).clamp(iw, ih, cw, ch)

zoomAt(newZoom, ax, ay, iw, ih, cw, ch):
    z = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    ix = (ax - offsetX) / zoom;  iy = (ay - offsetY) / zoom
    return Viewport(z, ax - ix * z, ay - iy * z).clamp(iw, ih, cw, ch)

scrollBy(dx, dy, iw, ih, cw, ch) = copy(offsetX = offsetX - dx, offsetY = offsetY - dy).clamp(iw, ih, cw, ch)
shiftedBy(dx, dy) = copy(offsetX = offsetX + dx, offsetY = offsetY + dy)
```

**Примеры расчёта:**
- `fit(11520, 2160, 3840, 2000)`: `min(1, 0.33333, 0.92593) = 0.333333`; контент `3840 × 720`;
  `offsetX = 0` (контент = холст), `offsetY = (2000 − 720) / 2 = 640`; `percent = 33`.
- `fit(300, 200, 3840, 2000)`: `zoom = 1.0`, `offsetX = 1770`, `offsetY = 900`, `percent = 100`.
- `Viewport(0.5, 100.0, 0.0).zoomAt(1.0, 300.0, 0.0, 2000, 1000, 1000, 1000)`: `ix = 400`, `offsetX' = 300 − 400 = −100`;
  контент 2000 > 1000 → `−100.coerceIn(−1000, 0) = −100`; по Y `iy = 0`, `offsetY' = 0`, контент 1000 = холст →
  `(1000 − 1000) / 2 = 0`. Итог `Viewport(1.0, −100.0, 0.0)`.
- `Viewport(1.0, -100.0, 0.0).scrollBy(5000.0, 0.0, 2000, 1000, 1000, 1000)` → `offsetX = −5100` → прижат к `−1000`.

**`EditorKeymap.actionFor`** (константы `java.awt.event.KeyEvent`):

| Условие | Клавиша | Действие |
|---|---|---|
| `alt` | любая | `null` |
| `ctrl && !shift` | `VK_ENTER`, `VK_S` | `Done` |
| `ctrl && !shift` | `VK_C` | `Copy` |
| `ctrl && !shift` | `VK_Z` | `Undo` |
| `ctrl && !shift` | `VK_Y` | `Redo` |
| `ctrl && !shift` | `VK_0`, `VK_NUMPAD0` | `ZoomFit` |
| `ctrl && !shift` | `VK_1`, `VK_NUMPAD1` | `Zoom100` |
| `ctrl && shift` | `VK_S` | `SaveAs` |
| `ctrl && shift` | `VK_Z` | `Redo` |
| `!ctrl` (shift любой) | `VK_ESCAPE` | `Cancel` |
| `!ctrl && !shift` | `VK_V` | `ToolMove` |
| `!ctrl && !shift` | `VK_P` | `ToolPencil` |
| `!ctrl`, `step = if (shift) 10 else 1` | `VK_LEFT` / `VK_RIGHT` / `VK_UP` / `VK_DOWN` | `Nudge(−step, 0)` / `Nudge(step, 0)` / `Nudge(0, −step)` / `Nudge(0, step)` |
| иначе | — | `null` |

Реализация — `when` по этим строкам сверху вниз.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение |
|---|---|---|
| 1 | Холст ещё не измерен (0×0) | `fit` → `Viewport(1.0, 0.0, 0.0)`; `clamp` → без изменений |
| 2 | `zoomAt` с 100.0 или 0.001 | обрезается до 8.0 / 0.1 |
| 3 | `Ctrl+Alt+S` | `null` (Alt запрещает любые действия) |
| 4 | `Ctrl+Shift+Enter`, `Ctrl+Shift+C` | `null` |

## 10. Константы и тексты

| Имя | Тип | Значение | Где |
|---|---|---|---|
| `MIN_ZOOM` | `Double` | `0.1` | `Viewport.kt` |
| `MAX_ZOOM` | `Double` | `8.0` | `Viewport.kt` |
| `ZOOM_STEP` | `Double` | `1.25` | `Viewport.kt` |
| `SCROLL_STEP_PX` | `Double` | `100.0` | `Viewport.kt` |

## 11. Тесты

`ViewportTest.kt` (сравнение `Double` — `assertEquals(expected, actual, 1e-6)`):

| # | Метод | When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `fit large image to canvas`() `` | `fit(11520, 2160, 3840, 2000)` | `zoom = 1/3`, `offsetX = 0`, `offsetY = 640`, `percent == 33` | 3.9, US-014 AC-1 |
| 2 | `` `fit small image is not enlarged and centered`() `` | `fit(300, 200, 3840, 2000)` | `zoom = 1.0`, `offsetX = 1770`, `offsetY = 900`, `percent == 100` | 3.9, US-014 AC-2 |
| 3 | `` `fit tiny image`() `` | `fit(2, 2, 100, 100)` | `zoom = 1.0`, `offsetX = 49`, `offsetY = 49` | REQ-003 §6 |
| 4 | `` `fit respects min zoom`() `` | `fit(100000, 10, 1000, 1000)` | `zoom = 0.1` | 3.10 |
| 5 | `` `fit with unknown canvas`() `` | `fit(100, 100, 0, 0)` | `Viewport(1.0, 0.0, 0.0)` | §9 №1 |
| 6 | `` `zoom at keeps anchor point`() `` | `Viewport(0.5, 100.0, 0.0).zoomAt(1.0, 300.0, 0.0, 2000, 1000, 1000, 1000)` | `Viewport(1.0, -100.0, 0.0)` | 3.10, US-014 AC-3 |
| 7 | `` `zoom is limited to range`() `` | `Viewport(1.0, 0.0, 0.0).zoomAt(100.0, 0.0, 0.0, 10, 10, 100, 100)` и `.zoomAt(0.001, …)` | `zoom == 8.0` и `zoom == 0.1` | 3.10 |
| 8 | `` `clamp centers small content`() `` | `Viewport(1.0, -500.0, 900.0).clamp(300, 200, 1000, 1000)` | `offsetX = 350`, `offsetY = 400` | 3.10 |
| 9 | `` `clamp pins large content to edges`() `` | `Viewport(1.0, 50.0, -5000.0).clamp(2000, 3000, 1000, 1000)` | `offsetX = 0`, `offsetY = -2000` | 3.10 |
| 10 | `` `scroll moves content opposite to delta`() `` | `Viewport(1.0, 0.0, 0.0).scrollBy(0.0, 100.0, 1000, 3000, 1000, 1000)` | `offsetY = -100`; `.scrollBy(5000.0, 0.0, 2000, 1000, 1000, 1000)` от `Viewport(1.0, -100.0, 0.0)` → `offsetX = -1000` | 3.10 |
| 11 | `` `converts canvas to image coordinates`() `` | `Viewport(0.5, 100.0, 20.0)`: `toImageX(300f)`, `toImageY(70f)` | `400f`, `100f` | — |
| 12 | `` `shifted by does not clamp`() `` | `Viewport(1.0, 0.0, 0.0).shiftedBy(-30.0, 5.0)` | `Viewport(1.0, -30.0, 5.0)` | — |

`EditorKeymapTest.kt` (`import java.awt.event.KeyEvent`, `a(ctrl, alt, shift, code) = EditorKeymap.actionFor(...)`):

| # | Метод | Then | Покрывает |
|---|---|---|---|
| 13 | `` `finish shortcuts`() `` | `a(true,false,false,VK_ENTER) == Done`; `VK_S` → `Done`; `VK_C` → `Copy`; `a(true,false,true,VK_S) == SaveAs`; `a(false,false,false,VK_ESCAPE) == Cancel`; `a(false,false,true,VK_ESCAPE) == Cancel` | 3.37 – 3.40 |
| 14 | `` `history shortcuts`() `` | `Ctrl+Z` → `Undo`; `Ctrl+Y` → `Redo`; `Ctrl+Shift+Z` → `Redo` | 3.34 |
| 15 | `` `zoom shortcuts`() `` | `Ctrl+VK_0`, `Ctrl+VK_NUMPAD0` → `ZoomFit`; `Ctrl+VK_1`, `Ctrl+VK_NUMPAD1` → `Zoom100` | 3.10, US-014 AC-4 |
| 16 | `` `tool shortcuts`() `` | `VK_V` → `ToolMove`; `VK_P` → `ToolPencil`; `Ctrl+V` → `null` | US-013 |
| 17 | `` `arrow nudges`() `` | `VK_LEFT` → `Nudge(-1, 0)`; `VK_DOWN` → `Nudge(0, 1)`; `Shift+VK_RIGHT` → `Nudge(10, 0)`; `Shift+VK_UP` → `Nudge(0, -10)`; `Ctrl+VK_LEFT` → `null` | 3.29 |
| 18 | `` `unused or alt combinations`() `` | `Ctrl+Alt+S` → `null`; `Ctrl+Shift+VK_ENTER` → `null`; `Ctrl+Shift+C` → `null`; `VK_A` → `null`; `Alt+VK_ESCAPE` → `null` | §9 №3, №4 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.editor.ViewportTest"` | Все тесты проходят |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.EditorKeymapTest"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

## 13. Критерии готовности (Definition of Done)

- [ ] Сигнатуры совпадают с разделом 6.
- [ ] Тесты 1–18 написаны и проходят.
- [ ] `Viewport.kt` не импортирует Compose, Swing и AWT.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не подключать клавиши к окну и не создавать окно (TASK-038).
- Не делать настройку клавиш.

## 15. Ловушки и подсказки

- В `fit` делить как `Double` (`cw.toDouble() / iw`), иначе целочисленное деление даст 0.
- `coerceIn(a, b)` бросает исключение при `a > b`; в `axis` ветка `coerceIn` выполняется только если
  `content > canvas`, тогда `canvas − content < 0` — диапазон корректен.
- Для `Ctrl+Shift+S` проверка `ctrl && shift` должна идти по таблице отдельно от `ctrl && !shift` — иначе
  `Ctrl+Shift+S` превратится в «Готово».

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

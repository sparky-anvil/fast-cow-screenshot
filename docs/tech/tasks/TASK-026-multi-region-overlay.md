# TASK-026: Реализовать оверлей `MultiRegionOverlay` для выделения нескольких областей

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-003 §4, §6, §7.1, §10 |
| Требования | REQ-002.3–2.19, 2.N1, 2.N2; US-008 AC-1–AC-9 |
| ADR | ADR-002, ADR-005 |
| Зависит от | TASK-025 |
| Блокирует | TASK-027 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать Swing-оверлей `MultiRegionOverlay : MultiRegionSelector`: окно на каждом мониторе с замороженным кадром,
выделение нескольких регионов мышью (логика — `MultiSelectionState` из TASK-025), локальные клавиши из
`MultiRegionRequest.keymap`, рамки с номерами, счётчик, подсказки. Подключение к приложению — TASK-027.

## 2. Контекст

Существующий `ui/RegionOverlay.kt` (режим «Область», REQ-001) — **образец**: как создаются окна на каждом мониторе,
как они размещаются в физических пикселях через `OverlayWindowPlacer.place`, как переводится точка мыши в координаты
стола через `CursorPosition.current()`, как кадр рисуется 1:1 без масштабирования. Прочитай его целиком перед началом.
`RegionOverlay.kt` не изменяй; нужный код повтори в новом файле.

На время открытого оверлея глобальные горячие клавиши снимаются (ADR-005): оверлей вызывает `onOpened()` при
открытии и `onClosed()` при закрытии; что именно делают лямбды, решает TASK-027.

**Выдержки из требований (дословно):**

> **REQ-002.3** По горячей клавише система должна зафиксировать замороженный кадр всех мониторов и показать оверлей так же, как в режиме «Область» (REQ-001.13).
>
> **REQ-002.5** Выделенные регионы должны оставаться визуально отмеченными на оверлее (рамка, незатемнённое содержимое) и пронумерованными в порядке добавления. Номера и рамки в итоговое изображение не попадают.
>
> **REQ-002.6** Во время выделения система должна показывать размер текущего региона в физических пикселях (как REQ-001.15) и счётчик «k / N» (выделено / лимит).
>
> **REQ-002.8** … показать на оверлее подсказку «Области не должны пересекаться» (≈ 2 с) … Во время перетаскивания пересечение должно подсвечиваться (например, красной рамкой).
>
> **REQ-002.11** … показывает подсказку «Достигнут лимит: N областей. Enter — собрать снимок».
>
> **REQ-002.14** Щелчок правой кнопкой мыши внутри существующего региона удаляет этот регион.
>
> **REQ-002.15** Щелчок правой кнопкой вне регионов ничего не делает.
>
> **REQ-002.16** Клавиша «Собрать снимок» (по умолчанию `Enter`, дополнительно `NumPad Enter`) закрывает оверлей и запускает склейку.
>
> **REQ-002.17** Если при нажатии «Собрать снимок» нет ни одного региона, оверлей не закрывается, система показывает подсказку «Выделите хотя бы одну область».
>
> **REQ-002.18** Клавиша «Отмена» (по умолчанию `Esc`) закрывает оверлей без создания снимка.
>
> **REQ-002.19** На оверлее должна быть ненавязчивая подсказка с текущими назначенными клавишами (например, «Enter — собрать · Backspace — удалить последнюю · Esc — отмена»).
>
> **REQ-002.N1** Оверлей появляется на всех мониторах ≤ 250 мс после нажатия клавиши.
>
> **REQ-002.N2** Добавление/удаление региона отображается на оверлее ≤ 50 мс; отрисовка при перетаскивании без заметных задержек.
>
> **US-008 AC-1** … оверлей с замороженным кадром, счётчиком «0 / 10» и подсказкой клавиш.

## 3. Предусловия

- TASK-025: `capture/MultiRegionSelector.kt`, `capture/MultiRegionRequest.kt`, `ui/MultiSelectionState.kt`
  (`PressResult`, `ReleaseResult`).
- TASK-024: `settings/OverlayKeymap.kt` (`actionFor(ctrl, alt, shift, keyCode, numpad): OverlayAction?`,
  `primaryName(action): String`), `settings/OverlayAction.kt`.
- Существуют: `ui/RegionOverlay.kt` (образец), `platform/windows/OverlayWindowPlacer.kt`
  (`place(window: java.awt.Window, bounds: PixelRect): PixelRect?` — реально занятый прямоугольник или null),
  `platform/windows/CursorPosition.kt` (`current(): PixelPoint?`).
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/MultiRegionOverlay.kt` | Создать | `MultiRegionOverlay`, private `MultiOverlayPanel` |
| 2 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/MultiRegionOverlayTextsTest.kt` | Создать | Тесты раздела 11 |

**Файлы, которые изменять нельзя:** все остальные, в том числе `RegionOverlay.kt`, `FastCowScreenshotApp.kt`.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/**
 * Оверлей «Несколько областей». Все методы — на EDT.
 * @param onOpened вызывается один раз в начале select, до создания окон.
 * @param onClosed вызывается один раз при закрытии (подтверждение, отмена, ошибка) до onResult.
 */
internal class MultiRegionOverlay(
    private val onOpened: () -> Unit,
    private val onClosed: () -> Unit,
) : MultiRegionSelector {
    override fun select(
        frame: BufferedImage,
        desktop: VirtualDesktop,
        request: MultiRegionRequest,
        onResult: (List<PixelRect>?) -> Unit,
    ): Unit = TODO()

    companion object {
        const val DIM_ALPHA: Int = 102
        const val BORDER_RGB: Int = 0x2E86DE
        const val INVALID_RGB: Int = 0xE74C3C
        const val BORDER_WIDTH: Int = 2                 // логических px, умножается на scaleX
        const val LABEL_BG_ARGB: Int = 0xA0000000.toInt()
        const val LABEL_FONT_PT: Int = 14               // умножается на scaleX
        const val HINT_FONT_PT: Int = 12                // умножается на scaleX
        const val LABEL_PADDING: Int = 4                // логических px, умножается на scaleX
        const val PANEL_MARGIN: Int = 16                // логических px, умножается на scaleX
        const val MESSAGE_DURATION_MS: Int = 2000

        const val MSG_INTERSECT: String = "Области не должны пересекаться"
        const val MSG_LIMIT_FORMAT: String = "Достигнут лимит: %d областей. %s — собрать снимок"
        const val MSG_EMPTY: String = "Выделите хотя бы одну область"
        const val HINT_KEYS_FORMAT: String = "%s — собрать · %s — удалить последнюю · %s — отмена"

        /** HINT_KEYS_FORMAT с keymap.primaryName(CONFIRM), (REMOVE_LAST), (CANCEL). */
        fun keysHint(keymap: OverlayKeymap): String = TODO()

        /** MSG_LIMIT_FORMAT с maxRegions и keymap.primaryName(CONFIRM). */
        fun limitMessage(maxRegions: Int, keymap: OverlayKeymap): String = TODO()
    }
}
```

**Используемые существующие контракты** (менять нельзя):

```kotlin
internal interface MultiRegionSelector { fun select(frame: BufferedImage, desktop: VirtualDesktop, request: MultiRegionRequest, onResult: (List<PixelRect>?) -> Unit) }
internal data class MultiRegionRequest(val maxRegions: Int, val keymap: OverlayKeymap)
internal class MultiSelectionState(val maxRegions: Int) // regions, dragRect, isDragging, dragIntersects, press, drag, release, removeLast, removeAt, counterLabel, sizeLabel
internal object OverlayWindowPlacer { fun place(window: java.awt.Window, bounds: PixelRect): PixelRect? }
internal object CursorPosition { fun current(): PixelPoint? }
```

## 7. Пошаговая реализация

### Шаг 1. Каркас класса и константы
- **Файл:** `MultiRegionOverlay.kt`. Класс, companion, `keysHint`, `limitMessage`. `select` пока пустой (`Unit`).
- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 2. Тесты текстов
- **Файл:** `MultiRegionOverlayTextsTest.kt` по разделу 11. **Результат:** тесты проходят.

### Шаг 3. `select`: окна
Локальные переменные внутри `select` (как в `RegionOverlay`): `startNs = System.nanoTime()`,
`done = AtomicBoolean(false)`, `state = MultiSelectionState(request.maxRegions)`, `windows = mutableListOf<JFrame>()`,
`panels = mutableListOf<MultiOverlayPanel>()`, `var message: String? = null`,
`timer = javax.swing.Timer(MESSAGE_DURATION_MS) { message = null; panels.forEach { it.message = null; it.repaint() } }`
с `timer.isRepeats = false`.

Локальные функции:
- `repaintAll()` — `panels.forEach { it.repaint() }`.
- `show(text: String)` — `panels.forEach { it.message = text }`, `timer.restart()`, `repaintAll()`.
- `finish(result: List<PixelRect>?)` — если `done.compareAndSet(false, true)`: `timer.stop()`,
  `windows.forEach { it.dispose() }`, затем `onClosed()` в `try { } catch (e: Exception) { log.warn("onClosed failed: {}", e.toString()) }`,
  затем `onResult(result)`.

Порядок:
1. `try {` `onOpened()`.
2. Для каждого `m in desktop.monitors` — создать окно **точно как в `RegionOverlay.select` (строки с `JFrame()` до
   `panels += panel`)**, с `MultiOverlayPanel(frame, desktop, state, keysHint(request.keymap))` вместо `OverlayPanel`;
   при `place == null` — `throw IllegalStateException("Overlay placement failed for ${m.deviceName}")`.
3. Мышь и клавиши — шаги 4, 5.
4. После цикла: фокус окну под курсором (как в `RegionOverlay`), лог
   `log.info("Multi-region overlay shown in {} ms", (System.nanoTime() - startNs) / 1_000_000L)`.
5. `} catch (e: Exception) { log.warn("Multi-region overlay closed: {}", e.toString()); finish(null) }`.

### Шаг 4. `select`: мышь
Функция точки `point(e)` — как в `RegionOverlay` (`CursorPosition.current() ?: …scale…`). Обработчики на `panel`:

| Событие | Действие |
|---|---|
| `mousePressed`, `BUTTON1` | `when (state.press(point(e)))`: `STARTED` → `repaintAll()`; `LIMIT_REACHED` → `show(limitMessage(request.maxRegions, request.keymap))`; `INSIDE_REGION` → `show(MSG_INTERSECT)` |
| `mousePressed`, `BUTTON3` | если `!state.isDragging && state.removeAt(point(e))` → `repaintAll()`; иначе ничего |
| `mouseDragged` | `state.drag(point(e))`; `repaintAll()` |
| `mouseReleased`, `BUTTON1` | `when (state.release(point(e)))`: `ADDED`, `TOO_SMALL` → `repaintAll()`; `INTERSECTS` → `show(MSG_INTERSECT)`; `NO_DRAG` → ничего |

### Шаг 5. `select`: клавиши
`KeyAdapter.keyPressed(e)` — добавить **только на `panel`** (`panel.addKeyListener(keys)`; на окно `f` не добавлять — иначе одно нажатие может обработаться дважды):

```
val action = request.keymap.actionFor(e.isControlDown, e.isAltDown, e.isShiftDown, e.keyCode,
                                      e.keyLocation == KeyEvent.KEY_LOCATION_NUMPAD) ?: return
e.consume()
when (action) {
  CONFIRM     -> if (state.regions.isEmpty()) show(MSG_EMPTY) else finish(state.regions)
  REMOVE_LAST -> if (state.removeLast()) repaintAll()
  CANCEL      -> finish(null)
}
```

Фокус: после цикла вызвать `toFront()`, `requestFocus()` окна и `requestFocusInWindow()` его панели — как в `RegionOverlay`.

### Шаг 6. `MultiOverlayPanel.paintComponent`
`private class MultiOverlayPanel(frame, desktop, state, hint: String) : JPanel()` с `var placedBounds: PixelRect`
и `var message: String? = null`. Порядок отрисовки (все координаты — физические px панели; преобразование `g2`
как в `OverlayPanel`: сбросить масштаб, оставить сдвиг, `NEAREST_NEIGHBOR`):

1. Фрагмент кадра под монитором и затемнение `Color(0, 0, 0, DIM_ALPHA)` — как в `OverlayPanel`.
2. Для каждого `(i, r)` в `state.regions.withIndex()`: `vis = r.intersect(p)`; если не null — нарисовать незатемнённый
   фрагмент кадра (как `sel` в `OverlayPanel`), рамку `BORDER_RGB` толщиной `bw = max(1, round(BORDER_WIDTH * scale))`
   (`g2.stroke` не менять — рисовать `bw` вложенных `drawRect` со сдвигом k = 0 until bw), номер `"${i + 1}"` в
   плашке `LABEL_BG_ARGB` белым жирным `Segoe UI` `LABEL_FONT_PT * scale` в левом верхнем углу `vis` с отступом `bw`.
3. `state.dragRect?.intersect(p)` — незатемнённый фрагмент и рамка цветом `if (state.dragIntersects) INVALID_RGB else BORDER_RGB`.
4. Размер при перетаскивании — как `paintLabel` в `OverlayPanel` (текст `state.sizeLabel()`, около курсора).
5. Плашка сверху по центру: строка 1 — `state.counterLabel()`; строка 2 — `message`, если не null.
   Шрифт `Segoe UI` BOLD `LABEL_FONT_PT * scale`, отступ сверху `PANEL_MARGIN * scale`, фон `LABEL_BG_ARGB`, текст белый.
6. Плашка снизу по центру: `hint`, шрифт `Segoe UI` PLAIN `HINT_FONT_PT * scale`, отступ снизу `PANEL_MARGIN * scale`,
   фон `LABEL_BG_ARGB`, текст белый.

Пункты 5 и 6 рисуются на **каждом** мониторе.

### Шаг 7. Сборка и ручная проверка
- **Результат:** `.\gradlew.bat build` проходит. Ручная проверка — после TASK-027 (здесь оверлей ещё не вызывается);
  в журнал записать «ручная проверка в TASK-027/029».

## 8. Алгоритм и логика

Перевод точки мыши: `CursorPosition.current()` — физические координаты стола (основной путь). Запасной путь
`placed.left + round(e.x * scale)`, где `scale = f.graphicsConfiguration.defaultTransform.scaleX`.
**Пример:** монитор 150 % размещён в `placed = PixelRect(-2880, 0, 2880, 1620)`, `e.x = 100` логических px →
`-2880 + 150 = -2730`.

Рисование фрагмента региона на панели: `l = vis.translate(-p.left, -p.top)`, источник в кадре —
`(vis.left - desktop.bounds.left, vis.top - desktop.bounds.top)`. **Пример:** `desktop.bounds.left = -2880`,
`p.left = 0`, регион `vis = PixelRect(100, 50, 200, 100)` → на панели `(100, 50)`, в кадре `(2980, 50)`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | `place` вернул null / исключение при создании окна | `finish(null)` | `WARN "Multi-region overlay closed: {}"` | оверлей закрыт |
| 2 | `onOpened()` бросил исключение | `finish(null)` (через тот же catch) | `WARN` как №1 | оверлей не открылся |
| 3 | `onClosed()` бросил исключение | `onResult` всё равно вызывается | `WARN "onClosed failed: {}"` | — |
| 4 | «Собрать» без регионов | оверлей открыт | — | `"Выделите хотя бы одну область"` 2 с |
| 5 | Нажатие ЛКМ при лимите | выделение не начато | — | сообщение лимита 2 с |
| 6 | Нажатие ЛКМ внутри региона / отпускание с пересечением | регион не добавлен | — | `"Области не должны пересекаться"` 2 с |
| 7 | Новое сообщение, пока старое на экране | заменить текст, таймер заново на 2000 мс | — | — |
| 8 | Нажатие клавиши, не назначенной ни одному действию | ничего | — | — |
| 9 | «Собрать» во время перетаскивания | `finish(state.regions)` — незавершённое выделение не входит | — | — |
| 10 | Повторный `finish` | игнорируется (`done`) | — | — |

## 10. Константы и тексты

Константы — раздел 6. Тексты UI (дословно):
- `"Области не должны пересекаться"`
- `"Достигнут лимит: %d областей. %s — собрать снимок"` → при 10 и Enter: `"Достигнут лимит: 10 областей. Enter — собрать снимок"`
- `"Выделите хотя бы одну область"`
- `"%s — собрать · %s — удалить последнюю · %s — отмена"` → по умолчанию `"Enter — собрать · Backspace — удалить последнюю · Esc — отмена"`
- Счётчик — `state.counterLabel()` (`"0 / 10"`).

## 11. Тесты

Файл: `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/MultiRegionOverlayTextsTest.kt`.
Окна в unit-тестах не создаются.

| # | Метод | Given | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `keys hint uses default primary keys`() `` | `MultiRegionSettings().keymap()` | `"Enter — собрать · Backspace — удалить последнюю · Esc — отмена"` | 2.19 |
| 2 | `` `keys hint follows reassigned keys`() `` | `MultiRegionSettings(confirmKey = "Space", cancelKey = "Ctrl+Q").keymap()` | `"Space — собрать · Backspace — удалить последнюю · Ctrl+Q — отмена"` | 2.19, 2.28 |
| 3 | `` `limit message`() `` | `limitMessage(10, MultiRegionSettings().keymap())` | `"Достигнут лимит: 10 областей. Enter — собрать снимок"` | 2.11 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.MultiRegionOverlayTextsTest"` | 3 теста проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная проверка оверлея выполняется в TASK-027 (шаги 1–9) после подключения.

## 13. Критерии готовности (Definition of Done)

- [x] Сигнатуры и константы совпадают с разделом 6.
- [x] Поведение мыши и клавиш — по таблицам шагов 4, 5; ошибки — по разделу 9.
- [x] `KeyAdapter` только на панели; `onClosed` вызывается ровно один раз до `onResult`.
- [x] Тесты раздела 11 проходят; выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не менять `RegionOverlay.kt` и не выносить из него общий код.
- Не подключать оверлей в `FastCowScreenshotApp` и `CaptureCoordinator` (TASK-027).
- Не вызывать `WindowHandles.excludeFromCapture` (кадр уже снят).
- Не создавать потоки; `Thread.sleep` запрещён — только `javax.swing.Timer`.
- Не рисовать рамки/номера в `frame` — только в `Graphics` панели.

## 15. Ловушки и подсказки

- `drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null)` — 2-я пара координат исключительная; так же, как в
  `OverlayPanel`.
- Сброс масштаба `g2.transform = AffineTransform.getTranslateInstance(t.translateX, t.translateY)` обязателен, иначе
  на мониторе 150 % кадр растянется (DPI-виртуализация Swing).
- `e.keyLocation == KeyEvent.KEY_LOCATION_NUMPAD` — единственный способ отличить `NumPad Enter` от `Enter`.
- `Ctrl+Z` в AWT: `keyCode == VK_Z (0x5A)`, `isControlDown == true`.
- Перерисовывай все панели при каждом изменении: регион может лежать на двух мониторах.
- `javax.swing.Timer` вызывает обработчик на EDT — дополнительных `invokeLater` не нужно.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-11-08 | Review | Реализованы MultiRegionOverlay (+ private MultiOverlayPanel) и 3 теста текстов. build — BUILD SUCCESSFUL. Ручная проверка — в TASK-027/029. Отклонений нет. |

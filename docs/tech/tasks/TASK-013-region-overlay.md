# TASK-013: Реализовать оверлей выделения области (окно на каждый монитор, 1:1, Esc/ПКМ, размер Ш×В)

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.13–.19, .21, N3; US-003 AC-1–AC-7 |
| ADR | ADR-001 |
| Зависит от | TASK-002, TASK-005, TASK-010 |
| Блокирует | TASK-015 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Реализовать `RegionSelector`: на каждом мониторе показать окно без рамки поверх всех окон. В окне — фрагмент
замороженного кадра в масштабе 1:1 к физическим пикселям, затемнённый на 40 % вне выделения. Пользователь тянет
прямоугольник левой кнопкой мыши (через границы мониторов) и видит размер `Ш×В`. Отпустил кнопку — результат передан
дальше. Esc или правая кнопка — отмена.

## 2. Контекст

- **Почему Swing/Java2D, а не Compose:** кадр рисуется прямо из `BufferedImage` без копий и без масштабирования
  (ADR-001).
- **Координаты.** Окно ставится на монитор через `SetWindowPos` в **физических** пикселях (EDT работает в
  per-monitor режиме AWT). После установки размер окна перечитывается через `GetWindowRect`, и дальше используется
  именно он (`placedBounds`).
- **Рисование 1:1.** Swing даёт `Graphics2D` с масштабом монитора (например, 1.5). Чтобы рисовать в пикселях
  устройства, масштаб сбрасывается с сохранением сдвига:
  `g2.transform = AffineTransform.getTranslateInstance(t.translateX, t.translateY)`.
- **Мышь.** Координаты `MouseEvent` логические и округлены — при 150 % теряется точность. Поэтому при каждом событии
  позиция берётся из `GetCursorPos` (`CursorPosition.current()`) — это глобальные физические пиксели.
- Windows сам «захватывает» мышь окном, в котором нажата кнопка. События перетаскивания приходят в него, даже когда
  курсор над другим монитором. Состояние выделения общее (`SelectionState`), при изменении перерисовываются все окна.
- Кадр снят до показа оверлея, поэтому оверлей в снимок не попадает (US-003 AC-7).

**Выдержки из требований (дословно):**

> **REQ-001.13** По горячей клавише режима «Область» система должна зафиксировать содержимое всех мониторов на момент нажатия («замороженный кадр») и показать поверх всех мониторов полноэкранный оверлей с этим кадром.
>
> **REQ-001.14** Система должна позволять выделить прямоугольную область перетаскиванием мыши с зажатой левой кнопкой. Выделение может пересекать границы мониторов.
>
> **REQ-001.15** Во время выделения система должна показывать размер выделенной области в физических пикселях (Ш×В).
>
> **REQ-001.16** Сразу после отпускания левой кнопки мыши система должна вырезать выделенную область из замороженного кадра и передать её дальше. Подтверждение и правка выделения не требуются.
>
> **REQ-001.17** При нажатии Esc или правой кнопки мыши во время выделения система должна закрыть оверлей.
>
> **REQ-001.N3** «Область»: оверлей с замороженным кадром появляется на всех мониторах ≤ 250 мс после нажатия клавиши.
>
> **US-003 (заметки)** Вне выделения кадр затемняется (например, на 40 %), выделенная область показывается без затемнения. Курсор в оверлее — перекрестие.

## 3. Предусловия

- TASK-002 (`SelectionGeometry`, `PixelRect`, `PixelPoint`, `VirtualDesktop`), TASK-005 (`User32Api.SetWindowPos`,
  `GetWindowRect`, `GetCursorPos`, `WindowHandles`, `WinConst`), TASK-010 (`RegionSelector`).

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SelectionState.kt` | Создать | чистая логика |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/RegionOverlay.kt` | Создать | `RegionOverlay` + `private class OverlayPanel` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/CursorPosition.kt` | Создать | object |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/OverlayWindowPlacer.kt` | Создать | object |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SelectionStateTest.kt` | Создать | unit |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/** Выделение. Только EDT, не потокобезопасен. */
internal class SelectionState {
    val anchor: PixelPoint? get() = TODO()
    /** Текущий прямоугольник (SelectionGeometry.rectFromPoints(anchor, current)) или null до press. */
    val rect: PixelRect? get() = TODO()
    fun press(point: PixelPoint): Unit = TODO()
    /** Игнорируется до press. */
    fun drag(point: PixelPoint): Unit = TODO()
    /** Итоговый прямоугольник (или null без press); состояние сбрасывается. */
    fun release(point: PixelPoint): PixelRect? = TODO()
    /** "201×201" (знак × U+00D7) или null, если rect == null. */
    fun sizeLabel(): String? = TODO()
}

/** Оверлей. Все методы — на EDT. */
internal class RegionOverlay : RegionSelector {
    override fun select(frame: java.awt.image.BufferedImage, desktop: VirtualDesktop, onResult: (PixelRect?) -> Unit): Unit = TODO()

    companion object {
        const val DIM_ALPHA: Int = 102            // 40 % от 255
        const val BORDER_RGB: Int = 0x2E86DE
        const val LABEL_BG_ARGB: Int = 0xA0000000.toInt()
        const val LABEL_FONT_PT: Int = 14         // умножается на scaleX монитора
        const val LABEL_PADDING: Int = 4          // логических px, умножается на scaleX
    }
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** Позиция курсора в физических пикселях. Вызывать на EDT или fcs-capture. */
internal object CursorPosition {
    /** GetCursorPos; null при ошибке. */
    fun current(): PixelPoint? = TODO()
}

/** Точное размещение окна в физических координатах. Вызывать на EDT для видимого окна. */
internal object OverlayWindowPlacer {
    /**
     * Дважды SetWindowPos(hwnd, HWND_TOPMOST, b.left, b.top, b.width, b.height, SWP_SHOWWINDOW),
     * затем GetWindowRect. Возвращает фактический прямоугольник; null — если вызов не удался.
     * При расхождении с b: WARN "Overlay placed at {} instead of {}".
     */
    fun place(window: java.awt.Window, bounds: PixelRect): PixelRect? = TODO()
}
```

## 7. Пошаговая реализация

1. `SelectionState` + `SelectionStateTest`.
2. `CursorPosition`, `OverlayWindowPlacer`.
3. `RegionOverlay` (8.1–8.3).
4. Компиляция и тесты. Ручная проверка — шаги 14–20 TASK-015.

## 8. Алгоритм и логика

### 8.1. select

```
startNs = nanoTime; done = AtomicBoolean(false); state = SelectionState(); windows = mutableListOf<JFrame>()
finish(rect) = if (done.compareAndSet(false, true)) { windows.forEach { it.dispose() }; onResult(rect) }
try {
  for (m in desktop.monitors) {
    f = JFrame().apply { isUndecorated = true; type = Window.Type.UTILITY; isAlwaysOnTop = true; isResizable = false }
    panel = OverlayPanel(frame, desktop, state, panels = …) ; panel.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
    f.contentPane = panel; f.setSize(100, 100); f.isVisible = true
    placed = OverlayWindowPlacer.place(f, m.bounds) ?: throw IllegalStateException("Overlay placement failed for ${m.deviceName}")
    panel.placedBounds = placed
    добавить слушатели (8.2) к f и panel; windows += f
  }
  окно, чей placedBounds содержит CursorPosition.current() (иначе первое): toFront(); requestFocus(); panel.requestFocusInWindow()
  log.info("Overlay shown in {} ms", ms)
} catch (e: Exception) { log.warn("Overlay closed: {}", e.toString()); finish(null) }
```

### 8.2. События

- `point(e)` = `CursorPosition.current() ?: PixelPoint(placed.left + (e.x * scale).roundToInt(), placed.top + (e.y * scale).roundToInt())`,
  где `scale = f.graphicsConfiguration.defaultTransform.scaleX`.
- `mousePressed`: `BUTTON1` → `state.press(point(e))`, перерисовать все панели; `BUTTON3` → `finish(null)`.
- `mouseDragged` → `state.drag(point(e))`, перерисовать все.
- `mouseReleased` `BUTTON1` → `finish(state.release(point(e)))`. Отмену < 2×2 делает координатор (TASK-010).
- `keyPressed` `VK_ESCAPE` (слушатель на каждом `JFrame` и панели, `panel.isFocusable = true`) → `finish(null)`.
- Shift, стрелки и другие клавиши игнорируются (REQ-001.56).
- `WindowListener.windowDeactivated` **не** обрабатывать (переход фокуса между окнами оверлея — нормален).

### 8.3. OverlayPanel.paintComponent(g)

```
g2 = g.create() as Graphics2D
t = g2.transform; scale = t.scaleX
g2.transform = AffineTransform.getTranslateInstance(t.translateX, t.translateY)
g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
p = placedBounds; sx = p.left - desktop.bounds.left; sy = p.top - desktop.bounds.top
g2.drawImage(frame, 0, 0, p.width, p.height, sx, sy, sx + p.width, sy + p.height, null)
g2.color = Color(0, 0, 0, DIM_ALPHA); g2.fillRect(0, 0, p.width, p.height)
sel = state.rect?.intersect(p)
if (sel != null) {
  l = sel.translate(-p.left, -p.top)
  g2.drawImage(frame, l.left, l.top, l.right, l.bottom, sx + l.left, sy + l.top, sx + l.right, sy + l.bottom, null)
  g2.color = Color(BORDER_RGB); g2.drawRect(l.left, l.top, l.width - 1, l.height - 1)
}
label = state.sizeLabel(); cur = CursorPosition.current()
if (label != null && cur != null && p.contains(cur)) {
  font = Font("Segoe UI", Font.BOLD, (LABEL_FONT_PT * scale).roundToInt()); pad = (LABEL_PADDING * scale).roundToInt()
  x = cur.x - p.left + 16 * scale; y = cur.y - p.top + 16 * scale + ascent  (если текст выходит за p — сдвинуть внутрь)
  фон Color(LABEL_BG_ARGB, true) прямоугольником с отступом pad, текст Color.WHITE
}
g2.dispose()
```

**Пример:** монитор A `(0, 0, 1920, 1080)` 100 % и B `(1920, 0, 3840, 2160)` 150 %, `desktop.bounds = (0, 0, 5760, 2160)`.
Для B: `sx = 1920`, `sy = 0`; окно рисует пиксели кадра x 1920..5759. Выделение `(1800, 100, 300×50)`: на A —
`l = (1800, 100, 120×50)`, на B — `l = (0, 100, 180×50)`. Подпись: `300×50`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | `place` вернул null / исключение (монитор отключён) | закрыть всё, `onResult(null)` | `WARN "Overlay closed: …"` | оверлей исчез |
| 2 | Фактический размер окна ≠ монитору | использовать фактический | `WARN "Overlay placed at …"` | — |
| 3 | `GetCursorPos` не сработал | fallback на `MouseEvent × scale` | — | — |
| 4 | Двойной вызов `finish` | второй игнорируется | — | — |
| 5 | Выделение в обратном направлении | `rectFromPoints` нормализует | — | — |

## 10. Константы и тексты

Константы — раздел 6. Текст подписи: `"<Ш>×<В>"` (× — U+00D7), например `"1920×1080"`.

## 11. Тесты

`SelectionStateTest`:

| # | Метод | When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `no rect before press`() `` | — | `rect == null`, `sizeLabel() == null` | — |
| 2 | `` `drag before press ignored`() `` | `drag((5,5))` | `rect == null` | — |
| 3 | `` `reverse drag across monitors`() `` | `press((2100,150))`, `drag((1800,100))` | `rect == PixelRect(1800,100,301,51)`, `sizeLabel() == "301×51"` | REQ-001.14, .15 |
| 4 | `` `release returns rect and resets`() `` | `press((0,0))`, `release((9,4))` | `PixelRect(0,0,10,5)`; после — `rect == null` | REQ-001.16 |
| 5 | `` `release without press is null`() `` | `release((1,1))` | `null` | — |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.SelectionStateTest"` | 5 тестов |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная — шаги 14–20 TASK-015.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–4 выполнены; контракты — как в разделе 6.
- [x] `onResult` вызывается ровно один раз во всех сценариях.
- [x] Рисование без масштабирования (`NEAREST_NEIGHBOR`, сброс масштаба).
- [x] 5 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать одно окно на весь виртуальный стол.
- Не копировать кадр (`getSubimage().copy`, `toComposeImageBitmap`).
- Не реализовывать правку выделения, модификаторы, лупу (REQ-001.56 — Won't).
- Не вырезать изображение в оверлее — это делает координатор.

## 15. Ловушки и подсказки

- `SetWindowPos` вызывать дважды: после первого вызова Windows может прислать `WM_DPICHANGED`, и AWT изменит размер
  окна. Второй вызов возвращает точный размер.
- `HWND_TOPMOST` = `HWND(Pointer.createConstant(-1L))`.
- `drawImage(img, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, observer)` — вторые координаты **исключительные**.
- `Color(LABEL_BG_ARGB, true)` — второй аргумент включает альфу.
- `JFrame.type = UTILITY` убирает кнопку с панели задач; задать до `isVisible = true`.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-30 | Review | Реализованы SelectionState, CursorPosition, OverlayWindowPlacer, RegionOverlay (+ OverlayPanel) и 5 тестов SelectionStateTest. `gradlew build` — BUILD SUCCESSFUL, 5/5. Отклонений нет. Ручная проверка оверлея (мультимонитор, HiDPI) — шаги 14–20 TASK-015. TASK-012 переведена в Done по указанию пользователя. |

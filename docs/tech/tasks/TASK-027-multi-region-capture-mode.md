# TASK-027: Добавить режим `MULTI_REGION` в координатор, склейку пикселей и подключение оверлея

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-003 §5, §6, §8, §9, §12 |
| Требования | REQ-002.1–2.3, 2.12, 2.16, 2.18, 2.20–2.26, 2.31, N3, N4; US-008 AC-1, AC-7, AC-9; US-009 AC-1–AC-7; US-010 AC-1 (клавиша) |
| ADR | ADR-005 |
| Зависит от | TASK-023, TASK-024, TASK-026 |
| Блокирует | TASK-028 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Добавить четвёртый режим захвата: глобальная клавиша `Ctrl+Shift+4` → кадр стола → `MultiRegionOverlay` →
склейка регионов (`StitchLayout` + новый `RegionStitcher`) → существующий конвейер `finish` (буфер, автосохранение,
уведомление, «Сохранить как…»). Подключить оверлей в `FastCowScreenshotApp` со снятием глобальных клавиш на время
оверлея.

## 2. Контекст

`CaptureCoordinator` (пакет `capture`) уже реализует режим `REGION`: на `fcs-capture` снимает кадр `desktop.bounds`,
на EDT вызывает `RegionSelector.select`, затем на `fcs-capture` вырезает прямоугольник (`crop`) и вызывает `finish`.
Новый режим устроен так же, но оверлей возвращает список регионов, а вместо `crop` — склейка.

Кадр уже содержит `#808080` в местах, не покрытых мониторами (`FrameAssembler`, REQ-001.11), поэтому после обрезки
регионов по `desktop.bounds` требование REQ-002.12 выполняется автоматически. Цвет заливки режима (REQ-002.21)
применяется только к пустотам склейки.

Глобальные клавиши регистрируются по `HotkeyAction.entries` (`SettingsController.registerAll`), id клавиши =
`ordinal + 1` (`WindowsHotkeyService`). Новый элемент enum добавляется **последним** — id 4.

**Выдержки из требований (дословно):**

> **REQ-002.1** Система должна предоставлять режим захвата «Несколько областей», запускаемый глобальной горячей клавишей по правилам REQ-001.2, REQ-001.3, REQ-001.5.
>
> **REQ-002.2** Горячая клавиша по умолчанию — `Ctrl+Shift+4`.
>
> **REQ-002.3** … Все последующие регионы вырезаются из этого одного кадра.
>
> **REQ-002.12** … Часть региона вне мониторов заливается цветом REQ-001.11 (`#808080`, как в режиме «Область»), а не цветом заливки пустот REQ-002.21.
>
> **REQ-002.18** Клавиша «Отмена» … закрывает оверлей без создания снимка: буфер обмена, файлы и последний снимок не меняются.
>
> **REQ-002.21** Пиксели итогового изображения, не принадлежащие ни одному региону, система должна заливать непрозрачным цветом из настройки «Цвет заливки» (по умолчанию `#808080`).
>
> **REQ-002.24** При одном регионе результат идентичен снимку режима «Область» с тем же прямоугольником.
>
> **REQ-002.25** Итоговое изображение обрабатывается конвейером REQ-001: буфер, автосохранение и имя файла, уведомление, «Сохранить как…».
>
> **REQ-002.26** Указатель мыши (если включён REQ-001.53) рисуется, только если его позиция в момент нажатия горячей клавиши попала внутрь одного из регионов; рисуется в соответствующей перенесённой позиции.
>
> **REQ-002.31** Изменение лимита не влияет на уже открытый оверлей; применяется со следующего запуска режима.
>
> **REQ-002.N3** От нажатия «Собрать снимок» до появления изображения в буфере ≤ 500 мс при 10 регионах с суммарным ограничивающим прямоугольником 3 × 4K.
>
> **US-009 AC-7** Given в настройках «Цвет заливки» = `#FFFFFF` When выполняется склейка регионов «по диагонали» Then все пустоты результата — ровно `#FFFFFF`.

## 3. Предусловия

- TASK-023: `domain/StitchLayout.kt` — `StitchLayout.compute(regions)`, `StitchPlacement(source, targetLeft, targetTop)`.
- TASK-024: `MultiRegionSettings` (`effectiveMaxRegions()`, `fillRgb()`, `keymap()`), `AppSettings.multiRegion`.
- TASK-025/026: `MultiRegionSelector`, `MultiRegionRequest`, `MultiRegionOverlay(onOpened, onClosed)`.
- Существуют: `capture/CaptureCoordinator.kt`, `app/FastCowScreenshotApp.kt`, `settings/HotkeySettings.kt`,
  `settings/HotkeyAction.kt`, `settings/HotkeyBinding.kt`, `domain/CaptureMode.kt`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/domain/CaptureMode.kt` | Изменить | `MULTI_REGION` последним |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/HotkeyAction.kt` | Изменить | `MULTI_REGION("Несколько областей", CaptureMode.MULTI_REGION)` последним |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/HotkeySettings.kt` | Изменить | поле `multiRegion = "Ctrl+Shift+4"` последним, ветки `get`/`with` |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/HotkeyBinding.kt` | Изменить | `defaultFor`: `MULTI_REGION -> '4'.code`; KDoc «Ctrl+Shift+1 / 2 / 3 / 4» |
| 5 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/RegionStitcher.kt` | Создать | `RegionStitcher` |
| 6 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/CaptureCoordinator.kt` | Изменить | параметр `multiRegionSelector`, ветка `MULTI_REGION`, `onMultiRegion` |
| 7 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/app/FastCowScreenshotApp.kt` | Изменить | `MultiRegionOverlay`, `restoreHotkeys()` |
| 8 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/capture/RegionStitcherTest.kt` | Создать | тесты 1–3 |
| 9 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/capture/CaptureCoordinatorTest.kt` | Изменить | вызов конструктора + тесты 4–13 |
| 10 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/settings/HotkeyBindingTest.kt` | Изменить | тесты 14–15 |

**Файлы, которые изменять нельзя:** все остальные (в том числе `SettingsWindow.kt`, `SettingsController.kt` — TASK-028;
`RegionOverlay.kt`, `MultiRegionOverlay.kt`).

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.capture

/** Копирование пикселей регионов по раскладке склейки. Без состояния. Поток: fcs-capture. */
internal object RegionStitcher {
    /**
     * Создаёт BufferedImage(layout.width, layout.height, TYPE_INT_RGB), заливает fillRgb, затем для каждой
     * StitchPlacement копирует source из frame построчно в (targetLeft, targetTop).
     * @param frame кадр TYPE_INT_RGB (DataBufferInt); пиксель (0,0) кадра = frameOrigin в координатах стола.
     * @param fillRgb 0xRRGGBB.
     * @throws IllegalArgumentException если какой-либо source выходит за пределы кадра ("region outside frame: <rect>").
     */
    fun stitch(frame: BufferedImage, frameOrigin: PixelPoint, layout: StitchLayout, fillRgb: Int): BufferedImage = TODO()
}
```

Изменения `CaptureCoordinator` — новый параметр **сразу после** `regionSelector`:

```kotlin
internal class CaptureCoordinator(
    private val monitors: MonitorProvider,
    private val capturer: ScreenCapturer,
    private val cursor: CursorService,
    private val clipboard: ClipboardWriter,
    private val saver: ScreenshotSaver,
    private val regionSelector: RegionSelector,
    private val multiRegionSelector: MultiRegionSelector,
    private val notifier: CaptureNotifier,
    // остальные параметры без изменений
)
```

Изменения `HotkeySettings`:

```kotlin
internal data class HotkeySettings(
    val monitorUnderCursor: String = "Ctrl+Shift+1",
    val allMonitors: String = "Ctrl+Shift+2",
    val region: String = "Ctrl+Shift+3",
    val multiRegion: String = "Ctrl+Shift+4",
)
```

`FastCowScreenshotApp` — новый private метод:

```kotlin
/** Повторная регистрация глобальных клавиш после закрытия оверлея «Несколько областей». Вызывается на EDT. */
private fun restoreHotkeys()
```

## 7. Пошаговая реализация

### Шаг 1. Enum и настройки клавиши
- `CaptureMode.kt`: `{ MONITOR_UNDER_CURSOR, ALL_MONITORS, REGION, MULTI_REGION }`; KDoc «Режимы захвата (REQ-001.1, REQ-002.1)».
- `HotkeyAction.kt`: строка `MULTI_REGION("Несколько областей", CaptureMode.MULTI_REGION),` последней.
- `HotkeySettings.kt`: поле `multiRegion`; `get`: `HotkeyAction.MULTI_REGION -> multiRegion`;
  `with`: `HotkeyAction.MULTI_REGION -> copy(multiRegion = text)`.
- `HotkeyBinding.kt`: в `defaultFor` ветка `HotkeyAction.MULTI_REGION -> '4'.code`.
- `CaptureCoordinator.kt`: временно в `when (mode)` ветка `CaptureMode.MULTI_REGION -> runMultiRegion(...)` из шага 3 —
  чтобы компилировалось, сразу выполняй шаги 2–3.

### Шаг 2. `RegionStitcher.kt`
- По разделам 6, 8. **Результат:** компиляция.

### Шаг 3. `CaptureCoordinator`
- Добавить параметр `multiRegionSelector` (раздел 6).
- В `runCapture`, `when (mode)`, ветка:

```kotlin
CaptureMode.MULTI_REGION -> {
    val frame = capturer.capture(desktop.bounds, desktop)
    val request = MultiRegionRequest(s.multiRegion.effectiveMaxRegions(), s.multiRegion.keymap())
    handedOver = true
    uiExecutor.execute {
        multiRegionSelector.select(frame, desktop, request) { rects ->
            onMultiRegion(rects, frame, desktop, snap, capturedAt, s, start)
        }
    }
}
```

- Новый private метод `onMultiRegion(rects: List<PixelRect>?, frame: BufferedImage, desktop: VirtualDesktop,
  snap: CursorSnapshot?, capturedAt: LocalDateTime, s: AppSettings, start: Long)` — алгоритм раздела 8; структура
  `try/catch/finally` **точно как в `onRegion`** (внешний `catch (e: RejectedExecutionException)` →
  `busy.set(false); snap?.close()`; внутренний `catch (e: Exception)` → `fail(e)`; `finally { snap?.close() }`).
- **Результат:** `.\gradlew.bat compileKotlin` падает только в `FastCowScreenshotApp.kt` (нет аргумента) — шаг 4.

### Шаг 4. `FastCowScreenshotApp`
- В конструктор `CaptureCoordinator` после `RegionOverlay(),` добавить
  `MultiRegionOverlay({ hotkeys.unregisterAll() }, { restoreHotkeys() }),`.
- Метод:

```kotlin
private fun restoreHotkeys() {
    val failed = controller.registerAll()
    if (failed.isNotEmpty()) log.warn("Hotkeys not restored after overlay: {}", failed)
}
```

- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 5. Тесты
- `CaptureCoordinatorTest`: в `coordinator(...)` добавить `multiSelector` после `selector`; добавить фейк и тесты.
- `RegionStitcherTest`, `HotkeyBindingTest` — по разделу 11.
- **Результат:** `.\gradlew.bat build` проходит.

### Шаг 6. Ручная проверка — раздел 12.

## 8. Алгоритм и логика

**`onMultiRegion`** (на `captureExecutor`):

```
confirmNs = System.nanoTime()                       // взять до captureExecutor.execute
clipped = rects?.mapNotNull { it.intersect(desktop.bounds) }?.filterNot { SelectionGeometry.isCancelled(it) }
if (clipped == null || clipped.isEmpty()) {
    log.info("Multi-region capture cancelled"); busy.set(false); return
}
origin = PixelPoint(desktop.bounds.left, desktop.bounds.top)
layout = StitchLayout.compute(clipped)
image = RegionStitcher.stitch(frame, origin, layout, s.multiRegion.fillRgb())
log.info("Multi-region stitched {} regions into {}x{}", clipped.size, image.width, image.height)
if (snap != null) {
    p = layout.placements.firstOrNull { it.source.contains(snap.hotspot) }
    if (p != null) cursor.paint(image, snap, PixelPoint(p.source.left - p.targetLeft, p.source.top - p.targetTop))
}
finish(image, CaptureMode.MULTI_REGION, capturedAt, s, start)
log.info("Multi-region confirm-to-clipboard {} ms", (System.nanoTime() - confirmNs) / 1_000_000)
```

`finish` синхронно пишет в буфер, затем планирует сохранение — поэтому лог после `finish` измеряет REQ-002.N3.

**`RegionStitcher.stitch`**:

```
require(frame.raster.dataBuffer is DataBufferInt)
out = BufferedImage(layout.width, layout.height, TYPE_INT_RGB); dst = (out.raster.dataBuffer as DataBufferInt).data
dst.fill(fillRgb)
src = (frame.raster.dataBuffer as DataBufferInt).data
для каждой p в layout.placements:
    fx = p.source.left - frameOrigin.x; fy = p.source.top - frameOrigin.y
    require(fx >= 0 && fy >= 0 && fx + w <= frame.width && fy + h <= frame.height) { "region outside frame: ${p.source}" }
    для row в 0 until h:
        System.arraycopy(src, (fy + row) * frame.width + fx, dst, (p.targetTop + row) * layout.width + p.targetLeft, w)
```

**Пример расчёта:** `desktop.bounds = PixelRect(-100, 0, 300, 100)` (origin (−100, 0)). Регионы
`A = PixelRect(-100, 0, 10, 10)`, `B = PixelRect(150, 50, 20, 10)` → layout 30×20, A → (0,0), B → (10,10).
Для B: `fx = 150 − (−100) = 250`, `fy = 50`; строка 0 копируется из индекса `50·300 + 250 = 15250` в `10·30 + 10 = 310`.
Указатель в (155, 52) внутри B → `imageOrigin = (150 − 10, 50 − 10) = (140, 40)`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | Оверлей вернул null (отмена) | `busy=false`, ничего не меняется | `INFO "Multi-region capture cancelled"` | — |
| 2 | Все регионы после обрезки пустые / < 2×2 | как №1 | как №1 | — |
| 3 | `StitchLayout.compute` / `stitch` бросили исключение | `fail(e)` | `ERROR "Capture failed"` | уведомление `"Не удалось сделать снимок"` |
| 4 | `captureExecutor` отклонил задачу | `busy=false`, `snap?.close()` | — | — |
| 5 | Повторная горячая клавиша при открытом оверлее | клавиши сняты (ADR-005); если всё же пришла — `busy` игнорирует | `DEBUG "Hotkey ignored: capture in progress"` | — |
| 6 | `registerAll` после оверлея вернул непустой список | продолжить | `WARN "Hotkeys not restored after overlay: {}"` | — |
| 7 | Указатель вне всех регионов | не рисуется, `snap.close()` один раз | — | — |

## 10. Константы и тексты

| Имя | Значение | Где |
|---|---|---|
| `HotkeySettings.multiRegion` по умолчанию | `"Ctrl+Shift+4"` | `HotkeySettings.kt` |
| `HotkeyAction.MULTI_REGION.displayName` | `"Несколько областей"` | `HotkeyAction.kt` |

Новых текстов UI нет (строка в окне настроек появится автоматически из `HotkeyAction.entries`).

## 11. Тесты

`RegionStitcherTest.kt` — кадр 10×4 `TYPE_INT_RGB`, пиксель (x,y) = `x * 16 + y`, `frameOrigin = PixelPoint(-5, 0)`:

| # | Метод | Given | Then |
|---|---|---|---|
| 1 | `` `copies regions and fills gaps`() `` | layout `StitchLayout(4, 2, [StitchPlacement(r(-5,0,2,1), 0, 0), StitchPlacement(r(2,2,2,1), 2, 1)])`, fill `0x123456` | `getRGB and 0xFFFFFF`: (0,0)=0, (1,0)=16, (2,1)=7·16+2=114, (3,1)=8·16+2=130; (2,0), (3,0), (0,1), (1,1) = `0x123456`; тип `TYPE_INT_RGB` |
| 2 | `` `single region equals crop`() `` | `StitchLayout.compute([r(-3,1,4,2)])` | 4×2, пиксель (i,j) = `(2+i)*16 + (1+j)` |
| 3 | `` `region outside frame throws`() `` | placement `r(4,0,2,1)` (fx = 9, 9+2 > 10) | `assertFailsWith<IllegalArgumentException>` |

`CaptureCoordinatorTest.kt` — добавить фейк:

```kotlin
private class FakeMultiSelector : MultiRegionSelector {
    var nextRects: List<PixelRect>? = null
    var lastRequest: MultiRegionRequest? = null
    override fun select(frame: BufferedImage, desktop: VirtualDesktop, request: MultiRegionRequest, onResult: (List<PixelRect>?) -> Unit) {
        lastRequest = request
        onResult(nextRects)
    }
}
```

`enc(x, y)` = `((x + 1000) shl 8) or y` (формула `FakeCapturer`). `desktop.bounds = PixelRect(-100, 0, 300, 100)`.

| # | Метод | Given | Then | Покрывает |
|---|---|---|---|---|
| 4 | `` `multi region stitches diagonal regions`() `` | rects `r(-100,0,10,10)`, `r(150,50,20,10)`; written slot | 30×20; (0,0)=enc(−100,0); (10,10)=enc(150,50); (0,15) и (15,5) = `0x808080` | 2.20, US-009 AC-1 |
| 5 | `` `multi region uses configured fill color`() `` | как №4, `multiRegion = MultiRegionSettings(fillColor = "#FFFFFF")` | (0,15) и (15,5) = `0xFFFFFF` | 2.21, US-009 AC-7 |
| 6 | `` `multi region cancel touches nothing`() `` | `nextRects = null` | `assertNothingTouched` | 2.18, US-008 AC-9 |
| 7 | `` `multi region empty after clipping is cancel`() `` | `nextRects = listOf(r(500,500,10,10))` (вне стола) | `assertNothingTouched` | §9 №2 |
| 8 | `` `multi region request reflects settings`() `` | `MultiRegionSettings(maxRegions = 25, confirmKey = "Space")` | `lastRequest.maxRegions == 25`, `lastRequest.keymap == settings.multiRegion.keymap()` | 2.31 |
| 9 | `` `single multi region equals region mode`() `` | rect `r(-50,10,20,5)`: `selector.nextRect` и `multiSelector.nextRects = listOf(rect)`; запустить `REGION`, затем `MULTI_REGION` (новый `last` не нужен — сравнить два захваченных `clipboard.write` через `mutableListOf` slot) | размеры равны; все пиксели равны | 2.24, US-009 AC-5 |
| 10 | `` `multi region cursor painted at moved position`() `` | `includeCursor = true`, `snapAt(155, 52)`, rects как №4 | `verify { cursor.paint(any(), snap, PixelPoint(140, 40)) }`, `snap.close()` 1 раз | 2.26 |
| 11 | `` `multi region cursor outside regions not painted`() `` | `snapAt(0, 90)`, rects как №4 | `paint` 0 раз, `close` 1 раз | 2.26 |
| 12 | `` `multi region goes through pipeline`() `` | rects как №4 | `last.get()?.mode == CaptureMode.MULTI_REGION`; `saver.save(any(), time, Path.of("C:\\s"))`; `notifier.showOutcome` 1 раз; `isBusy` false | 2.25, US-009 AC-6 |
| 13 | `` `multi region clips to desktop bounds`() `` | `nextRects = listOf(r(-150,0,100,10))` | результат 50×10, (0,0)=enc(−100,0) | 2.12 |

`HotkeyBindingTest.kt`:

| # | Метод | Then |
|---|---|---|
| 14 | `` `multi region default is ctrl shift 4`() `` | `HotkeyBinding.defaultFor(HotkeyAction.MULTI_REGION).format() == "Ctrl+Shift+4"`; `AppSettings(defaultDirectory = "C:\\s").hotkeyBinding(HotkeyAction.MULTI_REGION).format() == "Ctrl+Shift+4"` |
| 15 | `` `hotkey settings with and get multi region`() `` | `HotkeySettings().with(HotkeyAction.MULTI_REGION, "Ctrl+Alt+M").get(HotkeyAction.MULTI_REGION) == "Ctrl+Alt+M"`, `region` не изменился |

Существующие тесты `CaptureCoordinatorTest` должны пройти без изменений, кроме вызова конструктора.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.capture.*"` | Все тесты проходят |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.settings.HotkeyBindingTest"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (`.\gradlew.bat run`; лучше 2 монитора с разным масштабом):
1. `Ctrl+Shift+4` → на всех мониторах оверлей, сверху `"0 / 10"`, снизу `"Enter — собрать · Backspace — удалить последнюю · Esc — отмена"`; в логе `Multi-region overlay shown in N ms`, N ≤ 250.
2. Выделить 3 прямоугольника → три рамки с номерами 1, 2, 3, `"3 / 10"`, оверлей открыт.
3. Начать выделение с пересечением региона 1 → во время перетаскивания рамка красная; после отпускания — `"Области не должны пересекаться"` ~2 с, регион не добавлен.
4. `Backspace` → исчез регион 3; `Ctrl+Z` → исчез регион 2. ПКМ внутри региона 1 → исчез; ПКМ на пустом месте → ничего.
5. `Enter` без регионов → `"Выделите хотя бы одну область"`, оверлей открыт.
6. Выделить 2 региона «по диагонали» (один на каждом мониторе), `Enter` → уведомление; вставить в Paint: два фрагмента лесенкой, пустоты серые `#808080`; файл `Screenshot_…png` в папке по умолчанию.
7. `Ctrl+Shift+4`, выделить регион, `Esc` → оверлей закрыт, буфер не изменился.
8. После закрытия оверлея `Ctrl+Shift+1` снова работает (глобальные клавиши восстановлены).
9. Окно настроек: в «Горячие клавиши» есть строка «Несколько областей — Ctrl+Shift+4».

Результаты записать в журнал.

## 13. Критерии готовности (Definition of Done)

- [ ] Новые элементы enum — последними; `HotkeySettings.multiRegion` — последним полем.
- [ ] Сигнатуры совпадают с разделом 6; режимы REQ-001 работают как раньше (старые тесты проходят).
- [ ] Тесты 1–15 написаны и проходят.
- [ ] Ручная проверка 1–9 выполнена.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не менять `SettingsWindow.kt`, `SettingsController.kt` (раздел настроек — TASK-028).
- Не менять логику режимов `MONITOR_UNDER_CURSOR`, `ALL_MONITORS`, `REGION` и метод `crop`.
- Не повышать `AppSettings.CURRENT_VERSION`.
- Не вызывать `hotkeys.unregisterAll()` для режима `REGION`.

## 15. Ловушки и подсказки

- Порядок enum важен: id глобальной клавиши = `ordinal + 1`. Вставка `MULTI_REGION` не в конец сломает id других режимов.
- `restoreHotkeys` использует `controller`, объявленный в классе **ниже** `coordinator`. Лямбда `{ restoreHotkeys() }`
  вызывается позже (при закрытии оверлея), поэтому порядок инициализации не мешает — не переставляй свойства.
- `onClosed` вызывается на EDT; `registerAll` → `loop.invoke` синхронно ждёт поток `fcs-hotkeys` — это допустимо
  (так же работает окно настроек).
- `confirmNs` бери в `onMultiRegion` до `captureExecutor.execute`, а не внутри задачи — иначе не учтётся ожидание очереди.
- `snap` закрывается ровно один раз — в `finally` внутренней задачи или во внешнем `catch (RejectedExecutionException)`.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

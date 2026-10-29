# TASK-010: Реализовать координатор захвата, хранилище последнего снимка и потоки приложения

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.1, .5, .6, .9, .13, .16–.18, .22, .24, .27, .33, .35, .36, .53, N1, N2, N11; US-001 AC-5; US-003 AC-5, AC-6, AC-8; US-004 AC-2, AC-5; US-005 AC-5 |
| ADR | — |
| Зависит от | TASK-006, TASK-008, TASK-009 |
| Блокирует | TASK-011, TASK-012, TASK-013, TASK-014 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Связать захват, указатель, буфер, сохранение и уведомление в сценарии трёх режимов (`CaptureCoordinator`),
защитить от повторных нажатий, хранить последний снимок (`LastScreenshotHolder`) и создать именованные потоки
приложения (`AppThreads`). Всё тестируется на фейках, без Windows.

## 2. Контекст

- `trigger(mode)` вызывается с потока горячих клавиш и должен вернуться сразу: работа уходит на `captureExecutor`.
- Флаг `busy` (`AtomicBoolean`) поднимается в `trigger` и снимается, когда снимок записан в буфер, выбор области
  отменён или захват упал. Сохранение в файл идёт **после** снятия флага, на `saveExecutor` (REQ-001.33).
- Настройки берутся один раз — в момент `trigger`.
- Режим «Область»: кадр всего стола захватывается **до** показа оверлея («замороженный кадр»). Оверлей (`RegionSelector`)
  вызывается на `uiExecutor` и возвращает прямоугольник в координатах стола или `null` (отмена).
- Указатель (если включён) снимается в начале захвата, рисуется, только если его горячая точка внутри итоговой области,
  и всегда закрывается (`close()`).
- В тестах все executor-ы — `Executor { it.run() }` (синхронно) либо очередь, которую тест запускает сам.

**Выдержки из требований (дословно):**

> **REQ-001.5** Если захват или выделение области уже идёт, система должна игнорировать повторные нажатия горячих клавиш захвата.
>
> **REQ-001.17** При нажатии Esc или правой кнопки мыши во время выделения система должна закрыть оверлей. Снимок не создаётся, буфер обмена и файлы не изменяются.
>
> **REQ-001.24** Копирование в буфер должно выполняться независимо от результата сохранения файла: ошибка сохранения не должна мешать копированию, и наоборот.
>
> **REQ-001.33** Сохранение файла должно выполняться асинхронно и не блокировать следующий захват.
>
> **REQ-001.35** Система должна позволять отключить автосохранение в настройках, чтобы снимок попадал только в буфер обмена.
>
> **REQ-001.36** Система должна хранить в памяти последний успешный снимок до следующего захвата или до выхода из приложения.
>
> **REQ-001.53** … В режиме «Область» указатель попадает в снимок, только если эта позиция внутри выделенной области.

## 3. Предусловия

- TASK-002: `domain.*` (`PixelRect`, `PixelPoint`, `VirtualDesktop`, `CaptureMode`, `Screenshot`, `SelectionGeometry`).
- TASK-004: `AppSettings`.
- TASK-005: `DpiAwareness`, `WinConst.COINIT_APARTMENTTHREADED`.
- TASK-006: `MonitorProvider`, `ScreenCapturer`, `CursorService`, `CursorSnapshot`, `CaptureException`.
- TASK-008: `ClipboardWriter`, `ClipboardResult`.
- TASK-009: `ScreenshotSaver`, `SaveResult`, `IoErrorMessages`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/RegionSelector.kt` | Создать | интерфейс |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/CaptureOutcome.kt` | Создать | data class |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/CaptureNotifier.kt` | Создать | интерфейс |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/LastScreenshotHolder.kt` | Создать | класс |
| 5 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/CaptureCoordinator.kt` | Создать | класс |
| 6 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/app/AppThreads.kt` | Создать | класс |
| 7 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/capture/CaptureCoordinatorTest.kt` | Создать | unit |
| 8 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/capture/LastScreenshotHolderTest.kt` | Создать | unit |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет (`com.sun.jna.platform.win32.Ole32` — из `jna-platform`).

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.capture

/** Оверлей выбора области. select вызывается на EDT; onResult вызывается на EDT ровно один раз. */
internal interface RegionSelector {
    /** frame — кадр всего стола (пиксель (0,0) = desktop.bounds.left/top). onResult(rect в координатах стола) или onResult(null) при отмене. */
    fun select(frame: java.awt.image.BufferedImage, desktop: VirtualDesktop, onResult: (PixelRect?) -> Unit)
}

/** Итог захвата. save == null — автосохранение выключено. */
internal data class CaptureOutcome(val screenshot: Screenshot, val clipboard: ClipboardResult, val save: SaveResult?)

/** Уведомления. Методы вызываются на EDT. */
internal interface CaptureNotifier {
    fun showOutcome(outcome: CaptureOutcome)
    fun showMessage(title: String, lines: List<String>)
}

/** Последний снимок. Потокобезопасен. */
internal class LastScreenshotHolder(private val uiExecutor: java.util.concurrent.Executor) {
    fun get(): Screenshot? = TODO()
    /** Сохраняет снимок и вызывает каждого слушателя через uiExecutor.execute { listener(screenshot) }. */
    fun set(screenshot: Screenshot): Unit = TODO()
    fun addListener(listener: (Screenshot) -> Unit): Unit = TODO()
}

/** Сценарии захвата (TDD-001 §6). trigger потокобезопасен. */
internal class CaptureCoordinator(
    private val monitors: MonitorProvider,
    private val capturer: ScreenCapturer,
    private val cursor: CursorService,
    private val clipboard: ClipboardWriter,
    private val saver: ScreenshotSaver,
    private val regionSelector: RegionSelector,
    private val notifier: CaptureNotifier,
    private val lastScreenshot: LastScreenshotHolder,
    private val settings: () -> AppSettings,
    private val clock: () -> java.time.LocalDateTime,
    private val captureExecutor: java.util.concurrent.Executor,
    private val saveExecutor: java.util.concurrent.Executor,
    private val uiExecutor: java.util.concurrent.Executor,
) {
    /** true — захват запущен; false — проигнорирован (уже идёт). Не бросает. */
    fun trigger(mode: CaptureMode): Boolean = TODO()
    val isBusy: Boolean get() = TODO()

    companion object {
        const val CAPTURE_FAILED_TITLE: String = "Не удалось сделать снимок"
    }
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.app

/** Потоки приложения (conventions.md §6). */
internal class AppThreads(
    captureInit: () -> Unit = { DpiAwareness.enterPerMonitorV2() },
    dialogInit: () -> Unit = { Ole32.INSTANCE.CoInitializeEx(null, WinConst.COINIT_APARTMENTTHREADED) },
) {
    val capture: java.util.concurrent.ExecutorService   // 1 поток "fcs-capture", daemon, сначала captureInit()
    val save: java.util.concurrent.ExecutorService      // 1 поток "fcs-save", daemon
    val dialog: java.util.concurrent.ExecutorService    // 1 поток "fcs-dialog", daemon, сначала dialogInit()
    val ui: java.util.concurrent.Executor = java.util.concurrent.Executor { javax.swing.SwingUtilities.invokeLater(it) }
    /** shutdown() всех; save.awaitTermination(timeoutMs); затем shutdownNow() всех. */
    fun shutdown(timeoutMs: Long): Unit = TODO()
}
```

Фабрика потока: `Executors.newSingleThreadExecutor { r -> Thread({ init(); r.run() }, name).apply { isDaemon = true } }`.

## 7. Пошаговая реализация

1. `RegionSelector`, `CaptureOutcome`, `CaptureNotifier`.
2. `LastScreenshotHolder` (`AtomicReference`, `CopyOnWriteArrayList`) + тест.
3. `CaptureCoordinator` — раздел 8. Тест.
4. `AppThreads`.

## 8. Алгоритм и логика

**trigger(mode):**
1. `if (!busy.compareAndSet(false, true)) { log.debug("Hotkey ignored: capture in progress"); return false }`.
2. `capturedAt = clock()`, `s = settings()`, `start = System.nanoTime()`.
3. `try { captureExecutor.execute { runCapture(mode, capturedAt, s, start) } } catch (e: RejectedExecutionException) { busy.set(false); return false }`.
4. `return true`.

**runCapture (fcs-capture):**
```
snap = if (s.includeCursor) cursor.snapshot() else null
handedOver = false
try {
  desktop = monitors.desktop()
  when (mode) {
    MONITOR_UNDER_CURSOR -> { area = monitors.monitorUnderCursor(desktop).bounds; finish(captureArea(area, desktop, snap)…) }
    ALL_MONITORS        -> { area = desktop.bounds; то же }
    REGION -> {
      frame = capturer.capture(desktop.bounds, desktop)
      handedOver = true
      uiExecutor.execute { regionSelector.select(frame, desktop) { rect -> onRegion(rect, frame, desktop, snap, mode, capturedAt, s, start) } }
    }
  }
} catch (e: Exception) {
  log.error("Capture failed", e); busy.set(false)
  uiExecutor.execute { notifier.showMessage(CAPTURE_FAILED_TITLE, listOf(e.message ?: e.javaClass.simpleName)) }
} finally { if (!handedOver) snap?.close() }
```
`captureArea(area, desktop, snap)`: `image = capturer.capture(area, desktop)`; если `snap != null && area.contains(snap.hotspot)` →
`cursor.paint(image, snap, PixelPoint(area.left, area.top))`; вернуть `image`.

**onRegion (EDT) → captureExecutor.execute { … }:**
```
try {
  clipped = rect?.intersect(desktop.bounds)
  if (clipped == null || SelectionGeometry.isCancelled(clipped)) { log.info("Region capture cancelled"); busy.set(false); return }
  fr = SelectionGeometry.toFrameRect(clipped, PixelPoint(desktop.bounds.left, desktop.bounds.top))
  image = crop(frame, fr)
  if (snap != null && clipped.contains(snap.hotspot)) cursor.paint(image, snap, PixelPoint(clipped.left, clipped.top))
  finish(image, mode, capturedAt, s, start)
} catch (e: Exception) { как в runCapture } finally { snap?.close() }
```
`crop(frame, fr)`: `out = BufferedImage(fr.width, fr.height, TYPE_INT_RGB)`; `src`/`dst` — `DataBufferInt.data`;
для `row in 0 until fr.height`: `System.arraycopy(src, (fr.top + row) * frame.width + fr.left, dst, row * fr.width, fr.width)`.

**finish(image, mode, capturedAt, s, start) (fcs-capture):**
```
shot = Screenshot(image, capturedAt, mode)
clip = clipboard.write(image)
lastScreenshot.set(shot)
log.info("Captured {} {}x{} in {} ms", mode, image.width, image.height, (nanoTime - start) / 1_000_000)
busy.set(false)
saveExecutor.execute {
  save = if (!s.autoSaveEnabled) null else try { saver.save(image, capturedAt, Path.of(s.defaultDirectory)) }
         catch (e: Exception) { log.error("Auto-save failed", e); SaveResult.Failed(Path.of(""), IoErrorMessages.UNAVAILABLE) }
  uiExecutor.execute { notifier.showOutcome(CaptureOutcome(shot, clip, save)) }
}
```

**Пример (crop):** `desktop.bounds = (−100, 0, 300, 100)`, `rect = (−50, 10, 20, 5)` → `fr = (50, 10, 20, 5)`;
пиксель (0, 0) результата = пиксель (50, 10) кадра.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | Повторное нажатие | `false` | `DEBUG "Hotkey ignored: capture in progress"` | — |
| 2 | `CaptureException` / любое исключение захвата | `busy = false`, курсор закрыт | `ERROR "Capture failed"` | `showMessage("Не удалось сделать снимок", [message])` |
| 3 | Отмена / < 2×2 | `busy = false`, буфер, файл и уведомление не трогаются | `INFO "Region capture cancelled"` | — |
| 4 | Буфер `Failure` | сохранение всё равно выполняется | (пишет writer) | итоговое уведомление |
| 5 | `autoSaveEnabled = false` | `saver` не вызывается, `save = null` | — | итоговое уведомление |
| 6 | Исключение из `saver` | `Failed(Path.of(""), "Папка недоступна")` | `ERROR "Auto-save failed"` | итоговое уведомление |

## 10. Константы и тексты

- `CAPTURE_FAILED_TITLE = "Не удалось сделать снимок"`.
- Имена потоков: `"fcs-capture"`, `"fcs-save"`, `"fcs-dialog"`.

## 11. Тесты

Общие фикстуры `CaptureCoordinatorTest`:
- `A = MonitorInfo("\\\\.\\DISPLAY1", PixelRect(0,0,200,100), PixelRect(0,0,200,90), true)`,
  `B = MonitorInfo("\\\\.\\DISPLAY2", PixelRect(-100,0,100,100), PixelRect(-100,0,100,90), false)`, `desktop = VirtualDesktop(listOf(A, B))`.
- `capturer` — фейк: `capture(area, d)` возвращает `BufferedImage(area.width, area.height, TYPE_INT_RGB)`, где пиксель
  `(x, y) = ((area.left + x + 1000) shl 8) or (area.top + y)`; запоминает `area`.
- `monitors = mockk()`: `desktop()` → `desktop`, `monitorUnderCursor(desktop)` → `B`.
- `clipboard = mockk()`: `write(any())` → `ClipboardResult.Success`; `saver = mockk()`: `save(any(), any(), any())` → `SaveResult.Saved(Path.of("C:\\s\\x.png"))`.
- `notifier = mockk(relaxed = true)`, `cursor = mockk(relaxed = true)`, `direct = Executor { it.run() }`.
- `settings = AppSettings(defaultDirectory = "C:\\s")`, `clock = { LocalDateTime.of(2026,10,18,20,0,48) }`.
- `selector` — фейк, вызывающий `onResult(nextRect)` сразу.

| # | Метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `` `monitor mode captures monitor under cursor`() `` | — | `trigger(MONITOR_UNDER_CURSOR)` | `true`; `capturer.lastArea == B.bounds`; `clipboard.write` 1 раз; `last.get()?.image?.width == 100`; `saver.save(any(), clock(), Path.of("C:\\s"))`; `notifier.showOutcome(match { it.clipboard == Success && it.save is Saved })` | REQ-001.6, .23, .27, .36 |
| 2 | `` `all monitors captures desktop bounds`() `` | — | `trigger(ALL_MONITORS)` | `capturer.lastArea == PixelRect(-100,0,300,100)` | REQ-001.9 |
| 3 | `` `second trigger is ignored while busy`() `` | `captureExecutor` = очередь | `trigger` ×2, затем запустить очередь, затем `trigger` | `true`, `false`, после очереди `true`; захват выполнен 1 раз до третьего вызова | REQ-001.5, US-001 AC-5 |
| 4 | `` `save runs even if clipboard fails`() `` | `clipboard` → `Failure("busy")` | `trigger(ALL_MONITORS)` | `saver.save` вызван; outcome.clipboard is `Failure` | REQ-001.24 |
| 5 | `` `save failure keeps last screenshot`() `` | `saver` → `Failed(Path.of("C:\\s"), "Папка недоступна")` | `trigger(ALL_MONITORS)` | `last.get() != null`; `clipboard.write` вызван; outcome.save is `Failed` | REQ-001.31, US-004 AC-2 |
| 6 | `` `autosave disabled skips saver`() `` | `autoSaveEnabled = false` | `trigger(ALL_MONITORS)` | `saver` не вызван; `outcome.save == null` | REQ-001.35 |
| 7 | `` `cursor not requested when disabled`() `` | `includeCursor = false` | `trigger(ALL_MONITORS)` | `cursor.snapshot()` не вызван | REQ-001.22 |
| 8 | `` `cursor painted and closed when inside`() `` | `includeCursor = true`; `snapshot()` → `snap` (`hotspot = PixelPoint(-50, 10)`, relaxed) | `trigger(ALL_MONITORS)` | `cursor.paint(any(), snap, PixelPoint(-100, 0))`; `snap.close()` 1 раз | REQ-001.53 |
| 9 | `` `region cancel touches nothing`() `` | `nextRect = null` | `trigger(REGION)` | `clipboard`, `saver`, `notifier` не вызваны; `isBusy == false`; `last.get() == null` | REQ-001.17, US-004 AC-5 |
| 10 | `` `tiny region is cancel`() `` | `nextRect = PixelRect(5,5,1,1)` | `trigger(REGION)` | как 9 | REQ-001.18 |
| 11 | `` `region crops frame pixels`() `` | `nextRect = PixelRect(-50,10,20,5)` | `trigger(REGION)` | `clipboard.write` получил 20×5; его пиксель (0,0) `and 0xFFFFFF` = `((-50 + 1000) shl 8) or 10` | REQ-001.16, .19 |
| 12 | `` `region cursor outside selection is not painted`() `` | `includeCursor = true`, hotspot `(150, 50)`, `nextRect = PixelRect(-50,10,20,5)` | `trigger(REGION)` | `paint` не вызван; `close()` 1 раз | US-003 AC-8 |
| 13 | `` `capture failure shows message and frees busy`() `` | `monitors.desktop()` бросает `CaptureException("No monitors found")` | `trigger(ALL_MONITORS)` | `notifier.showMessage("Не удалось сделать снимок", listOf("No monitors found"))`; `isBusy == false` | §9 |

`LastScreenshotHolderTest`:

| # | Метод | Then |
|---|---|---|
| 1 | `` `set notifies listeners via executor`() `` | после `set(s)` слушатель получил `s`, `get() == s` |
| 2 | `` `initially empty`() `` | `get() == null` | 

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.capture.*"` | 13 + 2 (+4 из TASK-006) тестов |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–4 выполнены; контракты — как в разделе 6.
- [x] `busy` снимается во всех ветках (успех, отмена, исключение).
- [x] `CursorSnapshot.close()` вызывается ровно один раз во всех ветках.
- [x] 15 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не вызывать WinAPI и Swing из `CaptureCoordinator` (только через интерфейсы и executor-ы).
- Не реализовывать окна (TASK-011, 013) и сборку приложения (TASK-015).
- Не использовать `kotlinx.coroutines`.

## 15. Ловушки и подсказки

- В тесте 3 очередь: `val queue = ArrayDeque<Runnable>(); val queued = Executor { queue.add(it) }`.
- `catch (e: Exception)` в `runCapture`/`onRegion`/save — разрешённые границы потоков. Не ловить `Throwable`.
- Пиксели фейка содержат отрицательные `area.left + x`, поэтому прибавлено `1000`.
- Порядок в `finish` важен: сначала буфер, затем `lastScreenshot.set`, затем `busy = false`, затем сохранение.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-29 | Review | Реализованы RegionSelector, CaptureOutcome, CaptureNotifier, LastScreenshotHolder, CaptureCoordinator, AppThreads и 15 тестов (13 + 2), все пройдены; ``gradlew build`` - BUILD SUCCESSFUL. По решению пользователя TASK-008 и TASK-009 переведены в Done. Небольшое дополнение к 8: в onRegion добавлена обработка RejectedExecutionException (busy сбрасывается, курсор закрывается) - иначе busy мог остаться поднятым. Ручной проверки в задаче нет. |

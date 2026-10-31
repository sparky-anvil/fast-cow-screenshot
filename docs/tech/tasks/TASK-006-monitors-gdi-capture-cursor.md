# TASK-006: Реализовать перечисление мониторов, GDI-захват и отрисовку указателя мыши

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.6–.12, .19, .20, .22, .53, N1, N2, N5, N11; US-001 AC-1, AC-2, AC-7, AC-8; US-002 AC-1–AC-4 |
| ADR | ADR-002 |
| Зависит от | TASK-002, TASK-005 |
| Блокирует | TASK-008, TASK-010 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать интерфейсы захвата в пакете `capture` и их реализации на WinAPI:
- список мониторов в физических координатах и монитор под курсором;
- захват произвольного прямоугольника виртуального стола в `BufferedImage` с серой заливкой непокрытых зон;
- снимок формы и позиции указателя мыши и его отрисовка на изображении.

## 2. Контекст

- Все методы реализаций вызываются **только на потоке `fcs-capture`**. Этот поток при старте вызывает
  `DpiAwareness.enterPerMonitorV2()` (поток создаётся в TASK-010; в тестах — сам тест). Без этого Windows
  вернёт логические координаты.
- `EnumDisplayMonitors` перечисляет мониторы. Клонированные (дублированные) мониторы Windows отдаёт **одним** HMONITOR —
  так REQ-001.12 выполняется автоматически.
- Захват: экранный DC (`GetDC(null)`) → `BitBlt` в DIB-секцию 32 бит top-down (`biHeight = -h`) размера `area`. Пиксель
  DIB в памяти — `B, G, R, X`. Прочитанный как little-endian `Int`, он равен `0xXXRRGGBB`, то есть совпадает с форматом
  `TYPE_INT_RGB` (старший байт игнорируется).
- В непокрытых зонах `BitBlt` даёт мусор/чёрный цвет. Поэтому результат сначала целиком заливается `GAP_FILL_RGB`,
  а затем из DIB копируются только прямоугольники мониторов (`FrameAssembler`).
- Указатель `BitBlt` не захватывает (REQ-001.22 по умолчанию выполняется сам). Для REQ-001.53: в момент нажатия
  клавиши `snapshot()` делает копию иконки курсора (`CopyIcon`). Затем `paint()` рисует её на готовом изображении через
  `DrawIconEx` во временную DIB-заплатку 256×256.

**Выдержки из требований (дословно):**

> **REQ-001.6** Система должна определять целевой монитор по положению курсора мыши в момент нажатия горячей клавиши.
>
> **REQ-001.7** Система должна захватывать всю площадь целевого монитора в его физическом разрешении. Размер изображения должен совпадать с разрешением монитора из параметров дисплея Windows (например, 3840×2160 при масштабе 150 %).
>
> **REQ-001.8** Если положение курсора нельзя однозначно отнести к одному монитору (курсор вне всех мониторов или ошибка API), система должна захватывать основной (primary) монитор.
>
> **REQ-001.11** Части ограничивающего прямоугольника, которые не покрыты ни одним монитором, система должна заполнять непрозрачным серым цветом `#808080`.
>
> **REQ-001.53** Если флажок включён, система должна рисовать на снимке текущий системный указатель (с его формой) в той позиции, где он находился в момент нажатия горячей клавиши.
>
> **REQ-001 §6** Курсор точно на границе двух мониторов: выбирается монитор, которому принадлежит точка по правилу Windows `MonitorFromPoint(MONITOR_DEFAULTTOPRIMARY)`.
>
> **US-001 AC-8** Given указатель имеет форму стрелки, его горячая точка — в физических координатах (500, 300) монитора Then на снимке нарисована стрелка, её горячая точка — в (500, 300).

## 3. Предусловия

- TASK-002: `PixelRect`, `PixelPoint`, `MonitorInfo`, `VirtualDesktop`, `GAP_FILL_RGB` в `…fastcowscreenshot.domain`.
- TASK-005: `User32Api`, `Gdi32Api`, `CURSORINFO`, `WinConst`, `WinApiException`, `DpiAwareness` в `…platform.windows(.jna)`.
- `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

`C = src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/`, `P = src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/`,
`TC`, `TP` — те же пути в `src/test/kotlin`.

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `C/CaptureException.kt` | Создать | исключение |
| 2 | `C/MonitorProvider.kt` | Создать | интерфейс |
| 3 | `C/ScreenCapturer.kt` | Создать | интерфейс |
| 4 | `C/CursorSnapshot.kt` | Создать | интерфейс |
| 5 | `C/CursorService.kt` | Создать | интерфейс |
| 6 | `C/FrameAssembler.kt` | Создать | чистая сборка кадра |
| 7 | `P/WindowsMonitorProvider.kt` | Создать | реализация |
| 8 | `P/GdiScreenCapturer.kt` | Создать | реализация |
| 9 | `P/WindowsCursorService.kt` | Создать | реализация + `WindowsCursorSnapshot` (private-класс в том же файле) |
| 10 | `TC/FrameAssemblerTest.kt` | Создать | unit |
| 11 | `TP/GdiCaptureIntegrationTest.kt` | Создать | integration (Windows) |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.capture

/** Ожидаемый сбой захвата (WinAPI, нет мониторов). */
internal class CaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Мониторы. Вызывать на fcs-capture. */
internal interface MonitorProvider {
    /** Актуальная конфигурация (каждый вызов перечисляет заново — REQ-001.N11). @throws CaptureException */
    fun desktop(): VirtualDesktop
    /** Монитор под курсором по MonitorFromPoint(MONITOR_DEFAULTTOPRIMARY); при любой ошибке — desktop.primary. Не бросает. */
    fun monitorUnderCursor(desktop: VirtualDesktop): MonitorInfo
}

/** Захват пикселей. Вызывать на fcs-capture. */
internal interface ScreenCapturer {
    /**
     * Изображение TYPE_INT_RGB размера area.width × area.height; пиксель (0,0) = точка (area.left, area.top) стола.
     * Пиксели вне desktop.monitors = GAP_FILL_RGB. @throws CaptureException при сбое WinAPI.
     * @throws IllegalArgumentException если area.isEmpty.
     */
    fun capture(area: PixelRect, desktop: VirtualDesktop): java.awt.image.BufferedImage
}

/** Сохранённый указатель. Владеет нативной копией иконки; close() идемпотентен. */
internal interface CursorSnapshot : AutoCloseable {
    /** Позиция горячей точки, физические координаты стола. */
    val hotspot: PixelPoint
}

/** Указатель мыши. Вызывать на fcs-capture. */
internal interface CursorService {
    /** null — указатель скрыт или ошибка API (WARN в лог). Не бросает. */
    fun snapshot(): CursorSnapshot?
    /**
     * Рисует указатель на image (in-place). imageOrigin — координаты стола пикселя (0,0) изображения.
     * Ошибки WinAPI → WARN, изображение не меняется. Не бросает.
     */
    fun paint(image: java.awt.image.BufferedImage, cursor: CursorSnapshot, imageOrigin: PixelPoint)
}

/** Сборка кадра из DIB. Без состояния. */
internal object FrameAssembler {
    /** Прямоугольники мониторов, обрезанные по area (пустые пересечения отброшены), в порядке desktop.monitors. */
    fun coveredRects(area: PixelRect, desktop: VirtualDesktop): List<PixelRect> = TODO()
    /**
     * Создаёт BufferedImage(area.width, area.height, TYPE_INT_RGB), заливает GAP_FILL_RGB, затем для каждой строки
     * каждого прямоугольника из covered вызывает copy(startIndex, data, count), где data — массив пикселей изображения,
     * startIndex = (r.top - area.top + row) * area.width + (r.left - area.left), count = r.width.
     */
    fun assemble(area: PixelRect, covered: List<PixelRect>, copy: (startIndex: Int, data: IntArray, count: Int) -> Unit): java.awt.image.BufferedImage = TODO()
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

internal class WindowsMonitorProvider(private val user32: User32Api = User32Api.INSTANCE) : MonitorProvider
internal class GdiScreenCapturer(
    private val user32: User32Api = User32Api.INSTANCE,
    private val gdi32: Gdi32Api = Gdi32Api.INSTANCE,
) : ScreenCapturer
internal class WindowsCursorService(
    private val user32: User32Api = User32Api.INSTANCE,
    private val gdi32: Gdi32Api = Gdi32Api.INSTANCE,
) : CursorService {
    companion object { const val CURSOR_PATCH_SIZE: Int = 256 }
}
```

## 7. Пошаговая реализация

1. Интерфейсы и `CaptureException` (`C/…`). Компиляция.
2. `FrameAssembler` + `FrameAssemblerTest` → тест проходит.
3. `WindowsMonitorProvider` — раздел 8.1.
4. `GdiScreenCapturer` — раздел 8.2.
5. `WindowsCursorService` — раздел 8.3.
6. `GdiCaptureIntegrationTest` → `.\gradlew.bat test` проходит. Затем ручная проверка раздела 12.

## 8. Алгоритм и логика

### 8.1. WindowsMonitorProvider

`desktop()`:
1. `list = mutableListOf<MonitorInfo>()`. Callback `object : WinUser.MONITORENUMPROC { override fun apply(hMonitor, hdc, rect, data): Int }`:
   `info = WinUser.MONITORINFOEX()`; если `user32.GetMonitorInfo(hMonitor, info)` — добавить
   `MonitorInfo(Native.toString(info.szDevice), rc(info.rcMonitor), rc(info.rcWork), (info.dwFlags and MONITORINFOF_PRIMARY) != 0)`,
   где `rc(r) = PixelRect(r.left, r.top, r.right - r.left, r.bottom - r.top)`; иначе `WARN "GetMonitorInfo failed, GetLastError={}"`.
   Вернуть `1`.
2. `if (!user32.EnumDisplayMonitors(null, null, callback, null))` → `throw CaptureException(WinApiException.lastError("EnumDisplayMonitors").message ?: "EnumDisplayMonitors failed")`.
3. `if (list.isEmpty())` → `throw CaptureException("No monitors found")`.
4. `DEBUG "Monitor {} bounds={} primary={}"` для каждого. Вернуть `VirtualDesktop(list)`.

`monitorUnderCursor(desktop)`:
1. `p = POINT()`; если `!GetCursorPos(p)` → `WARN "GetCursorPos failed, GetLastError={}"`, вернуть `desktop.primary`.
2. `h = MonitorFromPoint(POINT.ByValue(p.x, p.y), MONITOR_DEFAULTTOPRIMARY)`; `null` → `desktop.primary`.
3. `info = MONITORINFOEX()`; `GetMonitorInfo(h, info)` false → `desktop.monitorAt(PixelPoint(p.x, p.y)) ?: desktop.primary`.
4. Вернуть `desktop.monitors.firstOrNull { it.deviceName == Native.toString(info.szDevice) } ?: desktop.monitorAt(PixelPoint(p.x, p.y)) ?: desktop.primary`.

### 8.2. GdiScreenCapturer.capture

```
require(!area.isEmpty) { "area is empty" }
start = System.nanoTime()
screenDc = user32.GetDC(null) ?: throw CaptureException(msg("GetDC"))
try {
  memDc = gdi32.CreateCompatibleDC(screenDc) ?: throw …("CreateCompatibleDC")
  try {
    bmi = WinGDI.BITMAPINFO(); h = bmi.bmiHeader
    h.biSize = h.size(); h.biWidth = area.width; h.biHeight = -area.height
    h.biPlanes = 1; h.biBitCount = 32; h.biCompression = BI_RGB
    bitsRef = PointerByReference()
    dib = gdi32.CreateDIBSection(screenDc, bmi, DIB_RGB_COLORS, bitsRef, null, 0) ?: throw …("CreateDIBSection")
    try {
      old = gdi32.SelectObject(memDc, dib)
      try {
        if (!gdi32.BitBlt(memDc, 0, 0, area.width, area.height, screenDc, area.left, area.top, SRCCOPY or CAPTUREBLT)) throw …("BitBlt")
        bits = bitsRef.value
        image = FrameAssembler.assemble(area, FrameAssembler.coveredRects(area, desktop)) { start, data, count ->
            bits.read(start * 4L, data, start, count) }
      } finally { if (old != null) gdi32.SelectObject(memDc, old) }
    } finally { gdi32.DeleteObject(dib) }
  } finally { gdi32.DeleteDC(memDc) }
} finally { user32.ReleaseDC(null, screenDc) }
DEBUG "BitBlt {}x{} at ({},{}) in {} ms"
```
`msg(f)` = `WinApiException.lastError(f).message`. `CaptureException(message, WinApiException)`.

DIB и изображение одного размера, поэтому индекс в DIB = индексу в `data`.

### 8.3. WindowsCursorService

`snapshot()`:
1. `ci = CURSORINFO()`; `!GetCursorInfo(ci)` → `WARN`, `null`.
2. `(ci.flags and CURSOR_SHOWING) == 0` или `ci.hCursor == null` → `null`.
3. `icon = CopyIcon(hCursor)` → `null` при неудаче (`WARN`).
4. `ii = WinGDI.ICONINFO()`; `!GetIconInfo(icon, ii)` → `DestroyIcon(icon)`, `WARN`, `null`.
5. Освободить `ii.hbmMask` и `ii.hbmColor` через `DeleteObject`, если не `null` (иначе утечка GDI — N7).
6. Вернуть `WindowsCursorSnapshot(PixelPoint(ci.ptScreenPos.x, ci.ptScreenPos.y), icon, ii.xHotspot, ii.yHotspot)`.
   `close()` — `DestroyIcon(icon)` один раз (`AtomicBoolean`).

`paint(image, cursor, origin)`:
1. `cursor as? WindowsCursorSnapshot ?: throw IllegalArgumentException("Unsupported CursorSnapshot")`.
2. `drawX = hotspot.x - xHotspot - origin.x`, `drawY = hotspot.y - yHotspot - origin.y`.
3. `patch = PixelRect(0, 0, image.width, image.height).intersect(PixelRect(drawX, drawY, 256, 256)) ?: return`.
4. `memDc = CreateCompatibleDC(null)`, DIB-секция 32 бит top-down размера `patch`. Скопировать строки `patch` из
   `data` изображения в DIB (`bits.write(rowStartInDib * 4L, data, indexInImage, patch.width)`).
5. `DrawIconEx(memDc, drawX - patch.left, drawY - patch.top, icon, 0, 0, 0, null, DI_NORMAL)`. Если `false` —
   `WARN "DrawIconEx failed, GetLastError={}"`, обратно не копировать.
6. Иначе скопировать строки из DIB обратно в `data` (`bits.read`). Освободить ресурсы в `finally`.

**Пример:** горячая точка (500, 300), `xHotspot = 0`, `yHotspot = 0`, монитор с `origin = (0, 0)` → `drawX = 500`,
`drawY = 300`, заплатка `(500, 300, 256, 256)`; стрелка рисуется в DIB от (0, 0).
Для «Все мониторы» с `origin = (−2560, 0)` и горячей точкой (−100, 50) → `drawX = 2460`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | Любой WinAPI-вызов захвата вернул null/false | ресурсы освобождены, `CaptureException` | (логирует вызывающий) | — |
| 2 | Нет мониторов | `CaptureException("No monitors found")` | — | — |
| 3 | Курсор вне мониторов / ошибка `GetCursorPos` | primary | `WARN` | — |
| 4 | Указатель скрыт (игра, ввод с тачскрина) | `snapshot()` → null | — | указателя нет |
| 5 | Указатель частично вне изображения | рисуется видимая часть | — | — |
| 6 | Монитор отключён между `desktop()` и `BitBlt` | `BitBlt` успешен, зона серая или чёрная | — | без сбоя (REQ-001 §6) |

## 10. Константы и тексты

`WindowsCursorService.CURSOR_PATCH_SIZE = 256`. Остальные — `WinConst`, `GAP_FILL_RGB`. Текстов UI нет.

## 11. Тесты

`FrameAssemblerTest` (desktop: A = `(0,0,4,2)` primary, B = `(-6,0,6,3)`; area = `PixelRect(-6,0,10,3)`):

| # | Метод | When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `covered rects are clipped monitor bounds`() `` | `coveredRects(area, desktop)` | `[PixelRect(0,0,4,2), PixelRect(-6,0,6,3)]` | REQ-001.9 |
| 2 | `` `gaps are filled with gray`() `` | `assemble(area, covered) { s, d, c -> d.fill(0x112233, s, s + c) }` | размер 10×3; `getRGB(6,2) and 0xFFFFFF == 0x808080` (под A); `getRGB(0,2)`, `getRGB(6,0)` → `0x112233` | REQ-001.11, US-002 AC-2 |
| 3 | `` `copy receives row start indexes`() `` | копия записывает `s` в список | список `[6, 16, 0, 10, 20]` | REQ-001.10 |
| 4 | `` `area outside monitors is all gray`() `` | `coveredRects(PixelRect(100,100,2,2), desktop)` | пустой список | — |

`GdiCaptureIntegrationTest` (`@EnabledOnOs(OS.WINDOWS)`, каждый тест выполняет код в новом `Thread`, где первым
вызывается `DpiAwareness.enterPerMonitorV2()`; результат передаётся через `AtomicReference`, `join(10000)`):

| # | Метод | Then |
|---|---|---|
| 1 | `` `desktop has exactly one primary monitor`() `` | `desktop().monitors.count { it.isPrimary } == 1` |
| 2 | `` `primary capture has monitor size`() `` | `capture(primary.bounds, d)` → `width == primary.bounds.width`, `height == primary.bounds.height` |
| 3 | `` `writes manual check images`() `` | пишет `build/manual-check/primary.png`, `build/manual-check/all-monitors.png` (`ImageIO.write`) и `build/manual-check/all-monitors-cursor.png` (snapshot + paint с `origin = PixelPoint(d.bounds.left, d.bounds.top)`); файлы существуют |
| 4 | `` `cursor snapshot can be closed twice`() `` | `snapshot()?.use { }` и повторный `close()` без исключения |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.capture.FrameAssemblerTest"` | 4 теста |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.platform.windows.GdiCaptureIntegrationTest"` | 4 теста |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (пользователь, через `question`):
1. Открыть `build\manual-check\primary.png` → размер равен разрешению основного монитора из «Параметры → Дисплей»
   (например, 3840×2160 при 150 %), изображение чёткое, без размытия.
2. Если мониторов ≥ 2: `all-monitors.png` → раскладка как в параметрах дисплея; зоны вне мониторов серые.
3. `all-monitors-cursor.png` → в позиции курсора нарисована стрелка; на `all-monitors.png` её нет.
4. Если доступна конфигурация 100 % + 150 % — повторить пункт 2: на стыке нет разрывов и наложений.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–6 выполнены; контракты — как в разделе 6.
- [x] Все GDI-ресурсы освобождаются в `finally`.
- [x] 8 тестов проходят; ручная проверка записана в журнал.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать `java.awt.Robot`, `GraphicsEnvironment` для координат.
- Не создавать потоки в `main`-коде (кроме тестов).
- Не делать захват по мониторам по отдельности — один `BitBlt` на `area`.
- Не трогать буфер обмена и файлы.

## 15. Ловушки и подсказки

- `biHeight` **отрицательный** — строки идут сверху вниз. С положительным изображение перевернётся.
- `biPlanes`, `biBitCount` в JNA — `Short`: `h.biPlanes = 1`, `h.biBitCount = 32` (Kotlin выведет литерал как `Short`
  при присваивании полю `Short`).
- `Pointer.read(offset: Long, buf: IntArray, index: Int, length: Int)` — смещение в **байтах**, индекс и длина — в элементах.
- `DataBufferInt`: `(image.raster.dataBuffer as java.awt.image.DataBufferInt).data`.
- `SelectObject` нужно вернуть в исходное состояние до `DeleteObject(dib)`: выбранный в DC битмап не удаляется.
- `CAPTUREBLT` включает в снимок layered-окна (всплывающие меню, подсказки).
- Тест 3 интеграционного класса **не** проверяет содержимое — его смотрит пользователь.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-29 | Review | Реализованы интерфейсы capture, FrameAssembler, WindowsMonitorProvider, GdiScreenCapturer, WindowsCursorService и тесты. FrameAssemblerTest - 4, GdiCaptureIntegrationTest - 4, все пройдены; `gradlew build` - BUILD SUCCESSFUL. Ручная проверка (раздел 12) выполнена пользователем: результат «Работает как описано». TASK-005 по решению пользователя переведена в Done перед началом. Отклонений нет. |

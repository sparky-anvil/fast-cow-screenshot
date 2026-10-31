# TASK-002: Реализовать доменную геометрию и модели (PixelRect, VirtualDesktop, SelectionGeometry)

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.9, .10, .11, .18, .19; US-002 AC-1, AC-2; US-003 AC-3, AC-6 |
| ADR | — |
| Зависит от | TASK-001 |
| Блокирует | TASK-004, TASK-006 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать чистые (без WinAPI и UI) классы геометрии в физических пикселях: прямоугольник и точка, описание монитора,
виртуальный рабочий стол с ограничивающим прямоугольником, геометрия выделения области, режимы захвата и модель снимка.
На этих классах строится весь захват.

## 2. Контекст

- **Виртуальный рабочий стол** Windows — общая система координат всех мониторов. Основной (primary) монитор
  начинается в (0, 0). Мониторы слева или выше имеют **отрицательные** координаты.
- **Физические пиксели** — реальные пиксели монитора без учёта масштаба Windows (125 %, 150 %…). Все координаты в
  проекте физические.
- `PixelRect` хранит `left`, `top`, `width`, `height`. `right = left + width` и `bottom = top + height` —
  **исключительные** границы: пиксель с x = right в прямоугольник не входит.
- Ограничивающий прямоугольник всех мониторов определяет размер снимка «Все мониторы». Части, не покрытые мониторами,
  потом заливаются серым (TASK-006).

**Выдержки из требований (дословно):**

> **REQ-001.9** Система должна захватывать все подключённые активные мониторы в одно изображение. Его размер равен ограничивающему прямоугольнику виртуального рабочего стола в физических пикселях.
>
> **REQ-001.10** Система должна сохранять взаимное расположение мониторов на изображении таким, как оно задано в параметрах дисплея Windows, включая мониторы с отрицательными координатами (слева или выше основного) и портретную ориентацию.
>
> **REQ-001.11** Части ограничивающего прямоугольника, которые не покрыты ни одним монитором, система должна заполнять непрозрачным серым цветом `#808080` (RGB 128, 128, 128). Цвет не настраивается.
>
> **REQ-001.18** Если выделение меньше 2×2 физических пикселей (например, одиночный клик), система должна считать его отменой и действовать как в REQ-001.17.
>
> **US-002 AC-1** Given монитор A 1920×1080 (основной) и монитор B 2560×1440, расположенный слева от A, верхние края выровнены When пользователь нажимает `Ctrl+Shift+2` Then создаётся одно изображение 4480×1440: B слева, A справа.

## 3. Предусловия

- Выполнена TASK-001: сборка работает, пакет `by.sparkyanvil.fastcowscreenshot` существует.
- `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

Базовый путь `M = src/main/kotlin/by/sparkyanvil/fastcowscreenshot/domain/`, `T = src/test/kotlin/by/sparkyanvil/fastcowscreenshot/domain/`.

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `M/PixelPoint.kt` | Создать | `PixelPoint` |
| 2 | `M/PixelRect.kt` | Создать | `PixelRect` |
| 3 | `M/MonitorInfo.kt` | Создать | `MonitorInfo` |
| 4 | `M/VirtualDesktop.kt` | Создать | `VirtualDesktop` |
| 5 | `M/SelectionGeometry.kt` | Создать | `SelectionGeometry` |
| 6 | `M/CaptureMode.kt` | Создать | `CaptureMode` |
| 7 | `M/Screenshot.kt` | Создать | `Screenshot` |
| 8 | `M/ImageConstants.kt` | Создать | `GAP_FILL_RGB` |
| 9 | `T/PixelRectTest.kt`, `T/VirtualDesktopTest.kt`, `T/SelectionGeometryTest.kt` | Создать | Тесты раздела 11 |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.domain

/** Точка в физических пикселях виртуального рабочего стола. Неизменяема. */
internal data class PixelPoint(val x: Int, val y: Int)

/**
 * Прямоугольник в физических пикселях. right/bottom исключительные. Неизменяем.
 * @throws IllegalArgumentException если width < 0 или height < 0 (проверка в init через require).
 */
internal data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    val right: Int get() = left + width
    val bottom: Int get() = top + height
    /** true, если width == 0 или height == 0. */
    val isEmpty: Boolean get() = TODO()
    /** true, если left <= x < right и top <= y < bottom. */
    fun contains(point: PixelPoint): Boolean = TODO()
    /** Пересечение; null, если пересечение пустое (нулевая ширина или высота). */
    fun intersect(other: PixelRect): PixelRect? = TODO()
    /** Сдвиг на (dx, dy) без изменения размера. */
    fun translate(dx: Int, dy: Int): PixelRect = TODO()

    companion object {
        /**
         * Ограничивающий прямоугольник. @throws IllegalArgumentException если rects пуст
         * (сообщение "rects must not be empty").
         */
        fun union(rects: List<PixelRect>): PixelRect = TODO()
    }
}

/**
 * Монитор. deviceName — имя устройства Windows ("\\.\DISPLAY1"), bounds — вся площадь,
 * workArea — площадь без панели задач. Всё в физических пикселях.
 */
internal data class MonitorInfo(
    val deviceName: String,
    val bounds: PixelRect,
    val workArea: PixelRect,
    val isPrimary: Boolean,
)

/** Снимок конфигурации мониторов на момент захвата. Неизменяем. */
internal data class VirtualDesktop(val monitors: List<MonitorInfo>) {
    // init: require(monitors.isNotEmpty()) { "monitors must not be empty" }
    /** PixelRect.union(monitors.map { it.bounds }). Вычислить один раз (val с инициализатором). */
    val bounds: PixelRect
    /** Первый монитор с isPrimary == true; если такого нет — monitors.first(). */
    val primary: MonitorInfo
    /** Первый монитор (в порядке списка), чьи bounds содержат point; null — если ни один. */
    fun monitorAt(point: PixelPoint): MonitorInfo? = TODO()
}

/** Геометрия выделения области. Без состояния. */
internal object SelectionGeometry {
    const val MIN_SIZE: Int = 2
    /** Прямоугольник по двум угловым пикселям, оба включительно. См. раздел 8. */
    fun rectFromPoints(a: PixelPoint, b: PixelPoint): PixelRect = TODO()
    /** true, если rect.width < MIN_SIZE или rect.height < MIN_SIZE. */
    fun isCancelled(rect: PixelRect): Boolean = TODO()
    /** Перевод из координат стола в координаты кадра, начинающегося в frameOrigin. */
    fun toFrameRect(rect: PixelRect, frameOrigin: PixelPoint): PixelRect = TODO()
}

/** Режимы захвата (REQ-001.1). */
internal enum class CaptureMode { MONITOR_UNDER_CURSOR, ALL_MONITORS, REGION }

/**
 * Готовый снимок. image — TYPE_INT_RGB; capturedAt — локальное время нажатия клавиши.
 * Изображение после создания Screenshot не изменяется.
 */
internal data class Screenshot(
    val image: java.awt.image.BufferedImage,
    val capturedAt: java.time.LocalDateTime,
    val mode: CaptureMode,
)
```

`ImageConstants.kt` (top-level):

```kotlin
/** Цвет заливки непокрытых зон, RGB без альфы (REQ-001.11). */
internal const val GAP_FILL_RGB: Int = 0x808080
```

## 7. Пошаговая реализация

### Шаг 1. Значения
- **Файлы:** `PixelPoint.kt`, `PixelRect.kt`, `CaptureMode.kt`, `ImageConstants.kt`.
- **Сделать:** по разделу 6 и алгоритмам раздела 8.
- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 2. Мониторы
- **Файлы:** `MonitorInfo.kt`, `VirtualDesktop.kt`, `Screenshot.kt`.
- **Результат:** компиляция проходит.

### Шаг 3. Выделение
- **Файл:** `SelectionGeometry.kt`.

### Шаг 4. Тесты
- **Файлы:** раздел 11. **Результат:** `.\gradlew.bat test` проходит.

## 8. Алгоритм и логика

**intersect:** `l = max(left, o.left)`, `t = max(top, o.top)`, `r = min(right, o.right)`, `b = min(bottom, o.bottom)`;
если `r <= l` или `b <= t` → `null`, иначе `PixelRect(l, t, r - l, b - t)`.

**union:** `l = min(left)`, `t = min(top)`, `r = max(right)`, `b = max(bottom)` → `PixelRect(l, t, r - l, b - t)`.

**rectFromPoints(a, b):** `left = min(a.x, b.x)`, `top = min(a.y, b.y)`, `width = abs(a.x - b.x) + 1`,
`height = abs(a.y - b.y) + 1`.

**toFrameRect(rect, origin):** `rect.translate(-origin.x, -origin.y)`.

**Пример:** мониторы A = (0, 0, 1920, 1080, primary) и B = (−2560, 0, 2560, 1440) → `bounds = (−2560, 0, 4480, 1440)`.
`monitorAt(−1, 0)` → B; `monitorAt(0, 0)` → A; `monitorAt(100, 1200)` → `null` (серая зона под A).

## 9. Ошибки и граничные случаи

| # | Ситуация | Ожидаемое поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | `PixelRect` с отрицательной шириной | `IllegalArgumentException` из `require` | — | исключение |
| 2 | `union(emptyList())` | `IllegalArgumentException("rects must not be empty")` | — | исключение |
| 3 | `VirtualDesktop(emptyList())` | `IllegalArgumentException("monitors must not be empty")` | — | исключение |
| 4 | Нет монитора с `isPrimary` | `primary = monitors.first()` | — | — |
| 5 | Прямоугольники касаются краем | `intersect` → `null` | — | — |

## 10. Константы и тексты

| Имя | Тип | Значение | Где объявить |
|---|---|---|---|
| `GAP_FILL_RGB` | `Int` | `0x808080` | `ImageConstants.kt` |
| `SelectionGeometry.MIN_SIZE` | `Int` | `2` | `SelectionGeometry.kt` |

Текстов UI нет.

## 11. Тесты

`PixelRectTest`:

| # | Метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `` `right and bottom are exclusive`() `` | `PixelRect(10, 20, 30, 40)` | `right`, `bottom`, `contains` | `40`, `60`; `contains(PixelPoint(39,59))` = true; `contains(PixelPoint(40,59))` = false | — |
| 2 | `` `intersect returns overlap`() `` | `(0,0,100,100)` и `(50,60,100,100)` | `intersect` | `PixelRect(50,60,50,40)` | — |
| 3 | `` `intersect returns null for touching rects`() `` | `(0,0,10,10)` и `(10,0,10,10)` | `intersect` | `null` | — |
| 4 | `` `union covers negative coordinates`() `` | `(0,0,1920,1080)`, `(-2560,0,2560,1440)` | `union` | `PixelRect(-2560,0,4480,1440)` | REQ-001.9, .10 |
| 5 | `` `union of empty list fails`() `` | `emptyList()` | `union` | `assertFailsWith<IllegalArgumentException>` | — |
| 6 | `` `negative width fails`() `` | — | `PixelRect(0,0,-1,5)` | `assertFailsWith<IllegalArgumentException>` | — |

`VirtualDesktopTest` (A = `MonitorInfo("\\\\.\\DISPLAY1", PixelRect(0,0,1920,1080), PixelRect(0,0,1920,1040), true)`,
B = `MonitorInfo("\\\\.\\DISPLAY2", PixelRect(-2560,0,2560,1440), PixelRect(-2560,0,2560,1400), false)`):

| # | Метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `` `bounds is union of monitors`() `` | `[A, B]` | `bounds` | `PixelRect(-2560,0,4480,1440)` | US-002 AC-1 |
| 2 | `` `primary is monitor flagged primary`() `` | `[B, A]` | `primary` | `A` | REQ-001.8 |
| 3 | `` `primary falls back to first`() `` | `[B, A.copy(isPrimary=false)]` | `primary` | `B` | — |
| 4 | `` `monitorAt finds monitor with negative coordinates`() `` | `[A, B]` | `monitorAt(PixelPoint(-1, 0))` | `B` | REQ-001.10 |
| 5 | `` `monitorAt returns null in gap`() `` | `[A, B]` | `monitorAt(PixelPoint(100, 1200))` | `null` | US-002 AC-2 |
| 6 | `` `portrait monitor bounds`() `` | `[A, MonitorInfo("\\\\.\\DISPLAY3", PixelRect(1920,-500,1080,1920), PixelRect(1920,-500,1080,1880), false)]` | `bounds` | `PixelRect(0,-500,3000,1920)` | REQ-001.10 |

`SelectionGeometryTest`:

| # | Метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `` `rect from reverse drag`() `` | a=(1900,500), b=(1700,300) | `rectFromPoints` | `PixelRect(1700,300,201,201)` | US-003 (обратное направление) |
| 2 | `` `single click is 1x1 and cancelled`() `` | a=b=(5,5) | `rectFromPoints` → `isCancelled` | `PixelRect(5,5,1,1)`, `true` | REQ-001.18 |
| 3 | `` `2x2 is not cancelled`() `` | a=(0,0), b=(1,1) | то же | `PixelRect(0,0,2,2)`, `false` | REQ-001.18 |
| 4 | `` `1 pixel high is cancelled`() `` | a=(0,0), b=(100,0) | то же | `true` | REQ-001.18 |
| 5 | `` `toFrameRect shifts by negative origin`() `` | rect `(-100,10,50,20)`, origin `(-2560,0)` | `toFrameRect` | `PixelRect(2460,10,50,20)` | REQ-001.19 |

Моков нет.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.domain.*"` | 17 тестов пройдены |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная проверка не нужна.

## 13. Критерии готовности (Definition of Done)

- [x] Все шаги раздела 7 выполнены.
- [x] Сигнатуры совпадают с разделом 6.
- [x] Все случаи раздела 9 обработаны.
- [x] Все тесты раздела 11 написаны и проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.
- [x] В пакете `domain` нет импортов `java.awt.*` (кроме `java.awt.image.BufferedImage`), `javax.swing`, `com.sun.jna`.

## 14. Чего НЕ делать

- Не добавлять зависимости.
- Не использовать `java.awt.Rectangle`/`Point` вместо своих классов.
- Не реализовывать захват, интерфейсы `capture` (TASK-006…010).

## 15. Ловушки и подсказки

- В Kotlin строка `"\\\\.\\DISPLAY1"` даёт текст `\\.\DISPLAY1`.
- `width` = разность **плюс один** только в `rectFromPoints`: точки включительные. В `intersect`/`union` границы
  исключительные, `+1` там нет.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-24 | Review | Реализовано по задаче без отклонений. TASK-001 переведена в Done по указанию пользователя. `gradlew build` — BUILD SUCCESSFUL; 17 тестов domain + 2 AppInfo пройдены. |

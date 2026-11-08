# TASK-025: Реализовать состояние выделения нескольких областей и интерфейс `MultiRegionSelector`

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-003 §5, §7.1 |
| Требования | REQ-002.4–2.11, 2.13–2.15, 2.17; US-008 AC-2–AC-6, AC-8 |
| ADR | ADR-005 |
| Зависит от | TASK-024 |
| Блокирует | TASK-026 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать чистый (без AWT/Swing) класс `MultiSelectionState`, который хранит набор регионов оверлея и решает, что
происходит при нажатии/перетаскивании/отпускании мыши, удалении и подтверждении. Объявить интерфейс оверлея
`MultiRegionSelector` и его параметры `MultiRegionRequest` в пакете `capture`. Окна — в TASK-026.

## 2. Контекст

Оверлей показывает замороженный кадр. Пользователь перетаскиванием левой кнопки добавляет прямоугольники (регионы).
Регионы не должны пересекаться (касание по стороне допустимо), их число ограничено, выделение меньше 2×2 физических
пикселей игнорируется. Правая кнопка внутри региона удаляет его. Координаты — физические пиксели стола; точка мыши —
пиксель, прямоугольник по двум точкам строит существующая `SelectionGeometry.rectFromPoints(a, b)` (оба угла
включительно: от (10,10) до (12,12) → `PixelRect(10,10,3,3)`).

**Выдержки из требований (дословно):**

> **REQ-002.4** … После отпускания кнопки оверлей **не закрывается**: регион добавляется в набор и остаётся видимым, можно выделять следующий.
>
> **REQ-002.5** Выделенные регионы должны оставаться визуально отмеченными на оверлее … и пронумерованными в порядке добавления.
>
> **REQ-002.6** Во время выделения система должна показывать размер текущего региона в физических пикселях … и счётчик «k / N» (выделено / лимит).
>
> **REQ-002.7** Регионы не должны пересекаться: никакой физический пиксель не может принадлежать двум регионам. Касание по границе (общая сторона без общих пикселей) допускается.
>
> **REQ-002.8** Если отпущенное выделение пересекается хотя бы с одним существующим регионом, система должна не добавлять его и показать на оверлее подсказку «Области не должны пересекаться» (≈ 2 с). Существующие регионы не изменяются. Во время перетаскивания пересечение должно подсвечиваться (например, красной рамкой).
>
> **REQ-002.9** Начало перетаскивания внутри существующего региона не создаёт новый регион (это пересечение по REQ-002.8).
>
> **REQ-002.10** Выделение меньше 2×2 физических пикселей игнорируется (не добавляется, оверлей остаётся открытым).
>
> **REQ-002.11** … Когда лимит достигнут, новое выделение не начинается, и система показывает подсказку «Достигнут лимит: N областей. Enter — собрать снимок».
>
> **REQ-002.13** Клавиша «Удалить последнюю область» … удаляет последний добавленный регион. Если регионов нет — ничего не происходит.
>
> **REQ-002.14** Щелчок правой кнопкой мыши внутри существующего региона удаляет этот регион. Номера остальных регионов перенумеровываются по порядку.
>
> **REQ-002.15** Щелчок правой кнопкой вне регионов ничего не делает.
>
> **US-008 AC-4** Given регион 1 = (0,0)–(100,100) When пользователь выделяет (100,0)–(200,100) Then регион добавляется.

## 3. Предусловия

- TASK-024: `settings/OverlayKeymap.kt` — `OverlayKeymap`.
- Существуют: `domain/PixelRect.kt` (`contains(PixelPoint)`, `intersect(PixelRect): PixelRect?`),
  `domain/PixelPoint.kt` (`PixelPoint(x, y)`), `domain/SelectionGeometry.kt` (`rectFromPoints`, `MIN_SIZE = 2`,
  `isCancelled(rect)` = `width < 2 || height < 2`), `ui/SelectionState.kt` (образец стиля), `capture/RegionSelector.kt`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/MultiRegionRequest.kt` | Создать | `MultiRegionRequest` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/MultiRegionSelector.kt` | Создать | `MultiRegionSelector` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/MultiSelectionState.kt` | Создать | `MultiSelectionState`, `PressResult`, `ReleaseResult` |
| 4 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/MultiSelectionStateTest.kt` | Создать | Тесты раздела 11 |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.settings.OverlayKeymap

/** Параметры оверлея «Несколько областей», снимок настроек на момент нажатия горячей клавиши. maxRegions в 2..50. */
internal data class MultiRegionRequest(val maxRegions: Int, val keymap: OverlayKeymap)
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.capture

/** Оверлей выбора нескольких областей. select вызывается на EDT; onResult вызывается на EDT ровно один раз. */
internal interface MultiRegionSelector {
    /**
     * frame — кадр всего стола (пиксель (0,0) = desktop.bounds.left/top).
     * onResult(регионы в координатах стола в порядке добавления, не пустой список) или onResult(null) при отмене/ошибке.
     */
    fun select(
        frame: java.awt.image.BufferedImage,
        desktop: by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop,
        request: MultiRegionRequest,
        onResult: (List<by.sparkyanvil.fastcowscreenshot.domain.PixelRect>?) -> Unit,
    )
}
```

(В файле используй обычные `import`, полные имена выше — только для ясности.)

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/** Результат нажатия левой кнопки. */
internal enum class PressResult { STARTED, LIMIT_REACHED, INSIDE_REGION }

/** Результат отпускания левой кнопки. */
internal enum class ReleaseResult { ADDED, TOO_SMALL, INTERSECTS, NO_DRAG }

/**
 * Набор регионов оверлея «Несколько областей». Только EDT, не потокобезопасен.
 * @param maxRegions лимит, >= 1 (@throws IllegalArgumentException иначе).
 */
internal class MultiSelectionState(val maxRegions: Int) {
    /** Регионы в порядке добавления (номер на экране = индекс + 1). Возвращает неизменяемую копию. */
    val regions: List<PixelRect>

    /** Текущий перетаскиваемый прямоугольник или null, если перетаскивания нет. */
    val dragRect: PixelRect?

    /** true, если идёт перетаскивание. */
    val isDragging: Boolean

    /** true, если dragRect != null и пересекается хотя бы с одним регионом. */
    val dragIntersects: Boolean

    /** regions.size >= maxRegions. */
    val isFull: Boolean

    /** Порядок проверок: isFull → LIMIT_REACHED; точка внутри региона → INSIDE_REGION; иначе начать перетаскивание → STARTED. */
    fun press(point: PixelPoint): PressResult

    /** Обновляет второй угол; без перетаскивания игнорируется. */
    fun drag(point: PixelPoint)

    /**
     * Завершает перетаскивание (состояние перетаскивания сбрасывается при любом результате, кроме NO_DRAG).
     * Порядок: нет перетаскивания → NO_DRAG; SelectionGeometry.isCancelled(rect) → TOO_SMALL; пересечение → INTERSECTS;
     * иначе регион добавляется → ADDED.
     */
    fun release(point: PixelPoint): ReleaseResult

    /** Удаляет последний регион. false, если регионов нет. */
    fun removeLast(): Boolean

    /** Удаляет регион, содержащий точку. false, если такого нет. Во время перетаскивания — ничего, false. */
    fun removeAt(point: PixelPoint): Boolean

    /** Прерывает перетаскивание без добавления. */
    fun cancelDrag()

    /** "k / N", например "3 / 10". */
    fun counterLabel(): String

    /** "201×201" (× — U+00D7) для dragRect или null. */
    fun sizeLabel(): String?
}
```

`PixelRect`, `PixelPoint`, `SelectionGeometry` — из `by.sparkyanvil.fastcowscreenshot.domain`.

## 7. Пошаговая реализация

### Шаг 1. `MultiRegionRequest.kt`, `MultiRegionSelector.kt`
- По разделу 6. **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 2. `MultiSelectionState.kt`
- Внутреннее состояние: `private val items = mutableListOf<PixelRect>()`, `private var anchor: PixelPoint? = null`,
  `private var current: PixelPoint? = null`. `regions` = `items.toList()`. `dragRect` =
  `SelectionGeometry.rectFromPoints(anchor, current)`, если оба не null. Логика — раздел 8.
- **Результат:** компиляция.

### Шаг 3. Тесты
- **Результат:** `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.MultiSelectionStateTest"` проходит.

## 8. Алгоритм и логика

```
press(p):
  if (isFull) return LIMIT_REACHED
  if (items.any { it.contains(p) }) return INSIDE_REGION
  anchor = p; current = p; return STARTED

release(p):
  if (anchor == null) return NO_DRAG
  current = p; rect = dragRect; anchor = null; current = null
  if (SelectionGeometry.isCancelled(rect)) return TOO_SMALL
  if (items.any { it.intersect(rect) != null }) return INTERSECTS
  items += rect; return ADDED

removeAt(p): if (isDragging) return false; idx = items.indexOfFirst { it.contains(p) }; если -1 → false; иначе removeAt(idx), true
```

**Пример расчёта (US-008 AC-4):** регион 1 = `PixelRect(0,0,100,100)` получен из `press(0,0)`, `release(99,99)`.
Затем `press(100,0)` → STARTED (точка (100,0) не внутри: `right` исключительный), `release(199,99)` →
rect `PixelRect(100,0,100,100)`, `intersect` → null → ADDED, `counterLabel()` = `"2 / 10"`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение |
|---|---|---|
| 1 | `MultiSelectionState(0)` | `IllegalArgumentException("maxRegions must be positive: 0")` |
| 2 | `drag` без `press` | игнорируется |
| 3 | Повторный `press` во время перетаскивания | начинает новое перетаскивание с новой точки (якорь заменяется), проверки как обычно |
| 4 | `release` при лимите (перетаскивание не начиналось) | NO_DRAG |
| 5 | Перетаскивание началось вне регионов и закончилось внутри другого | INTERSECTS |
| 6 | `removeLast` без регионов | false, ничего |

Логирование в этом классе не используется.

## 10. Константы и тексты

| Что | Значение |
|---|---|
| Формат счётчика | `"%d / %d"` (пробелы вокруг `/`) |
| Формат размера | `"${w}\u00D7${h}"` |

## 11. Тесты

Файл: `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/MultiSelectionStateTest.kt`. `p(x,y)` = `PixelPoint(x,y)`.
Helper `add(s, x1, y1, x2, y2)` = `s.press(p(x1,y1)); s.drag(p(x2,y2)); s.release(p(x2,y2))`.

| # | Метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `` `adds regions and keeps order`() `` | `State(10)` | add (0,0,9,9), (20,0,29,9), (40,0,49,9) | regions = `[r(0,0,10,10), r(20,0,10,10), r(40,0,10,10)]`, `counterLabel()` = `"3 / 10"` | 2.4, 2.5, AC-2 |
| 2 | `` `touching region is added`() `` | регион add(0,0,99,99) | add(100,0,199,99) | ADDED, 2 региона | 2.7, AC-4 |
| 3 | `` `intersecting region is rejected`() `` | регион add(0,0,99,99) | press(150,150), release(99,99) | INTERSECTS, 1 регион без изменений | 2.8, AC-3 |
| 4 | `` `drag intersects flag`() `` | регион add(0,0,99,99) | press(150,150), drag(120,120) → флаг; drag(50,50) → флаг | false, затем true | 2.8 |
| 5 | `` `press inside region does not start drag`() `` | регион add(0,0,99,99) | press(50,50) | INSIDE_REGION, `isDragging` false | 2.9 |
| 6 | `` `tiny selection is ignored`() `` | пусто | press(10,10), release(11,10) | TOO_SMALL, регионов 0, `isDragging` false | 2.10 |
| 7 | `` `two by two is accepted`() `` | пусто | press(10,10), release(11,11) | ADDED, `r(10,10,2,2)` | 2.10 |
| 8 | `` `limit blocks new drag and frees after removal`() `` | `State(2)`, 2 региона | press(500,500) → ; removeLast(); press(500,500) | LIMIT_REACHED, `isFull` true; затем STARTED | 2.11, AC-5 |
| 9 | `` `remove last removes newest`() `` | 3 региона из теста 1 | removeLast() | true, остались первые два | 2.13, AC-6 |
| 10 | `` `remove last on empty does nothing`() `` | пусто | removeLast() | false | 2.13 |
| 11 | `` `remove at removes containing region and renumbers`() `` | 3 региона из теста 1 | removeAt(p(5,5)) | true; regions = `[r(20,0,10,10), r(40,0,10,10)]` | 2.14, AC-6 |
| 12 | `` `remove at outside does nothing`() `` | 3 региона | removeAt(p(15,5)) | false, 3 региона | 2.15 |
| 13 | `` `remove at during drag does nothing`() `` | 1 регион; press(500,500) | removeAt(p(5,5)) | false, 1 регион | §8 |
| 14 | `` `release without press`() `` | пусто | release(p(1,1)) | NO_DRAG | §9 №4 |
| 15 | `` `size label while dragging`() `` | press(0,0), drag(200,200) | sizeLabel() | `"201×201"`; после release — null | 2.6 |
| 16 | `` `cancel drag discards selection`() `` | press(0,0), drag(50,50) | cancelDrag(); release(p(50,50)) | `dragRect` null после cancelDrag; release → NO_DRAG; регионов 0 | §6 |
| 17 | `` `regions list is a copy`() `` | 1 регион | `val r = s.regions`; add ещё один | `r.size == 1` | §6 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.MultiSelectionStateTest"` | 17 тестов проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

## 13. Критерии готовности (Definition of Done)

- [x] Сигнатуры совпадают с разделом 6; `MultiSelectionState.kt` не импортирует `java.awt` и `javax.swing`.
- [x] 17 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не создавать окна и не реализовывать `MultiRegionSelector` (TASK-026).
- Не менять `SelectionState.kt`, `RegionSelector.kt`, `RegionOverlay.kt`.
- Не показывать сообщения — класс только возвращает `PressResult`/`ReleaseResult`.

## 15. Ловушки и подсказки

- `PixelRect.contains` использует исключительные `right`/`bottom`: точка (100,0) не внутри `r(0,0,100,100)`.
- `rectFromPoints` включает оба угла: размер = `|dx| + 1`. Не прибавляй 1 повторно.
- В `release` сбрасывай якорь **до** проверок, иначе после TOO_SMALL оверлей «залипнет» в перетаскивании.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-11-08 | Review | Реализованы MultiRegionRequest, MultiRegionSelector, MultiSelectionState и 17 тестов. build — BUILD SUCCESSFUL. Отклонений нет. |

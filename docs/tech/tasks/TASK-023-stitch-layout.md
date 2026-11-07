# TASK-023: Реализовать раскладку склейки регионов `StitchLayout` («сжатие пустот»)

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-003 §8 |
| Требования | REQ-002.20, 2.22, 2.23, 2.24, N6; US-009 AC-1–AC-5 |
| ADR | ADR-005 |
| Зависит от | — |
| Блокирует | TASK-027 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать чистую функцию, которая по списку непересекающихся прямоугольников (физические пиксели стола) вычисляет размер
итогового изображения и позицию каждого прямоугольника на нём. Пиксели в этой задаче не копируются.

## 2. Контекст

Режим «Несколько областей» собирает в одно изображение только содержимое выделенных регионов. Пустые столбцы и строки
между регионами удаляются целиком, взаимное расположение сохраняется, регионы не масштабируются. Это делается
независимо по оси X и по оси Y («сжатие координат»).

**Выдержки из требований (дословно):**

> **REQ-002.20** Система должна собирать итоговое изображение только из содержимого регионов по алгоритму «сжатие пустот»: (1) берётся ограничивающий прямоугольник всех регионов; (2) удаляются все вертикальные полосы (столбцы пикселей), не пересекающие ни один регион; (3) удаляются все горизонтальные полосы (строки), не пересекающие ни один регион; (4) каждый регион переносится без масштабирования, его новая позиция — исходная минус ширина/высота удалённых полос левее/выше него.
>
> **REQ-002.22** Склейка должна сохранять взаимный порядок: если регион A целиком левее (выше) региона B на экране, он остаётся левее (выше) и на изображении. Регионы на результате не пересекаются, не масштабируются, отступов между ними нет.
>
> **REQ-002.23** Результат не зависит от порядка выделения регионов, только от их положения.
>
> **REQ-002.24** При одном регионе результат идентичен снимку режима «Область» с тем же прямоугольником.
>
> **REQ-002.N6** Алгоритм склейки покрыт модульными тестами: 1 регион, касающиеся регионы, регионы «по диагонали», в одну строку, в один столбец, отрицательные координаты, 10 регионов, независимость от порядка добавления.
>
> **US-009 AC-1** Given регионы A = (0,0)–(100,50) и B = (500,300)–(700,400) … Then результат 300×150: A в (0,0), B в (100,50).
>
> **US-009 AC-2** Given A = (0,0)–(100,100) и B = (300,20)–(400,60) … Then результат 200×100: A в (0,0), B в (100,20).

Нотация «(x1,y1)–(x2,y2)» = `PixelRect(x1, y1, x2 − x1, y2 − y1)`, правая/нижняя граница исключительная.

## 3. Предусловия

- В проекте уже существует `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/domain/PixelRect.kt` —
  `PixelRect(left, top, width, height)` со свойствами `right`, `bottom`, `isEmpty`, методом `intersect(other): PixelRect?`
  (null, если общих пикселей нет; касание по стороне → null).
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/domain/StitchLayout.kt` | Создать | `StitchPlacement`, `StitchLayout` |
| 2 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/domain/StitchLayoutTest.kt` | Создать | Тесты раздела 11 |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.domain

/**
 * Положение одного региона на склеенном изображении.
 * source — регион в координатах стола (физические пиксели); targetLeft/targetTop — левый верхний угол на результате.
 */
internal data class StitchPlacement(val source: PixelRect, val targetLeft: Int, val targetTop: Int)

/**
 * Раскладка склейки «сжатие пустот» (REQ-002.20). Неизменяема. Потокобезопасна.
 * placements отсортированы по (source.top, source.left).
 */
internal data class StitchLayout(val width: Int, val height: Int, val placements: List<StitchPlacement>) {
    companion object {
        /**
         * @param regions непустой список непустых попарно непересекающихся прямоугольников, любой порядок.
         * @throws IllegalArgumentException regions пуст ("regions must not be empty"); есть пустой прямоугольник
         *   ("empty region: <rect>"); два прямоугольника пересекаются ("regions intersect: <a> and <b>").
         */
        fun compute(regions: List<PixelRect>): StitchLayout = TODO()
    }
}
```

Вспомогательные функции — `private` top-level в этом же файле, имена: `mergeIntervals`, `mapCoordinate`.

## 7. Пошаговая реализация

### Шаг 1. Контракты
- **Файл:** `StitchLayout.kt`. Объявить классы раздела 6, тело `compute` — по разделу 8.
- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 2. Тесты
- **Файл:** `StitchLayoutTest.kt`. Все тесты раздела 11.
- **Результат:** `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.domain.StitchLayoutTest"` проходит.

## 8. Алгоритм и логика

```
compute(regions):
  require(regions.isNotEmpty())
  для каждого r: require(!r.isEmpty)
  для каждой пары i<j: require(regions[i].intersect(regions[j]) == null)
  xs = mergeIntervals(regions.map { it.left to it.right })
  ys = mergeIntervals(regions.map { it.top to it.bottom })
  width  = сумма (end - start) по xs
  height = сумма (end - start) по ys
  placements = regions
      .sortedWith(compareBy({ it.top }, { it.left }))
      .map { StitchPlacement(it, mapCoordinate(xs, it.left), mapCoordinate(ys, it.top)) }
  return StitchLayout(width, height, placements)

mergeIntervals(list: List<Pair<Int,Int>>): List<Triple<Int,Int,Int>>   // (start, endExclusive, offset)
  sorted = list.sortedBy { it.first }
  результат пуст; curStart = sorted[0].first; curEnd = sorted[0].second
  для каждого (s, e) из sorted, начиная со второго:
      если s <= curEnd: curEnd = max(curEnd, e)          // перекрытие или касание — один отрезок
      иначе: добавить (curStart, curEnd); curStart = s; curEnd = e
  добавить (curStart, curEnd)
  offset: первый 0, каждый следующий = offset предыдущего + (end - start) предыдущего

mapCoordinate(segs, c): найти seg, где seg.start <= c < seg.end; вернуть seg.offset + (c - seg.start)
  (такой seg всегда существует: c — начало одного из исходных отрезков)
```

**Пример расчёта:** A = `PixelRect(0, 0, 100, 50)`, B = `PixelRect(500, 300, 200, 100)` →
xs = (0,100,0), (500,700,100) → width 300; ys = (0,50,0), (300,400,50) → height 150 →
placements = [A→(0,0), B→(100,50)].

Пример 2 (отрицательные координаты): A = `PixelRect(-1920, 100, 100, 50)`, B = `PixelRect(0, 0, 200, 100)` →
xs = (−1920,−1820,0), (0,200,100) → width 300; ys: [0,100) и [100,150) сливаются в (0,150,0) → height 150 →
placements (сортировка по top): [B→(100,0), A→(0,100)].

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | Пустой список | `IllegalArgumentException("regions must not be empty")` | — | исключение |
| 2 | Прямоугольник с width 0 или height 0 | `IllegalArgumentException("empty region: $r")` | — | исключение |
| 3 | Два прямоугольника пересекаются | `IllegalArgumentException("regions intersect: $a and $b")` | — | исключение |
| 4 | Касание по стороне | допустимо, вплотную | — | см. тест 2 |

Логирование в этом классе не используется.

## 10. Константы и тексты

Н/П — констант и текстов UI нет.

## 11. Тесты

Файл: `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/domain/StitchLayoutTest.kt`. `r(l,t,w,h)` = `PixelRect(l,t,w,h)`.
`pos(layout, rect)` — private helper теста: `Pair(targetLeft, targetTop)` placement с `source == rect`.

| # | Метод | Given | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `single region is placed at origin`() `` | `[r(30,40,50,60)]` | 50×60; позиция (0,0) | 2.24 |
| 2 | `` `touching regions stay adjacent`() `` | `r(0,0,100,100)`, `r(100,0,100,100)` | 200×100; (0,0) и (100,0) | 2.7, 2.22 |
| 3 | `` `diagonal regions collapse gaps`() `` | `r(0,0,100,50)`, `r(500,300,200,100)` | 300×150; (0,0) и (100,50) | US-009 AC-1 |
| 4 | `` `regions in a row keep vertical offset`() `` | `r(0,0,100,100)`, `r(300,20,100,40)` | 200×100; (0,0) и (100,20) | US-009 AC-2 |
| 5 | `` `regions in a column keep horizontal offset`() `` | `r(0,0,100,100)`, `r(30,500,40,50)` | 100×150; (0,0) и (30,100) | N6 |
| 6 | `` `negative coordinates are supported`() `` | `r(-1920,100,100,50)`, `r(0,0,200,100)` | 300×150; A (0,100), B (100,0); `placements[0].source == r(0,0,200,100)` | N6 |
| 7 | `` `overlapping projections are not removed`() `` | `r(0,0,300,10)`, `r(100,50,10,10)`, `r(250,100,10,10)` | 300×30; (0,0), (100,10), (250,20) | REQ-002 §6 |
| 8 | `` `ten regions in a grid`() `` | 10 регионов `r(i*200, (i%2)*500, 50, 40)`, i=0..9 | 500×80; регион i → (i*50, (i%2)*40) | N6 |
| 9 | `` `result does not depend on input order`() `` | список теста 8 и он же `.reversed()` и `.shuffled(Random(42))` | все три `StitchLayout` равны (`assertEquals`) | 2.23, US-009 AC-4 |
| 10 | `` `order is preserved left to right`() `` | `r(0,0,10,10)`, `r(1000,5,10,10)`, `r(50,500,10,10)` | targetLeft: 0, 20, 10 соответственно; targetLeft(r(0,0..)) < targetLeft(r(50,..)) < targetLeft(r(1000,..)) | 2.22 |
| 11 | `` `empty list is rejected`() `` | `emptyList()` | `assertFailsWith<IllegalArgumentException>` | §9 №1 |
| 12 | `` `empty region is rejected`() `` | `[r(0,0,0,10)]` | `assertFailsWith<IllegalArgumentException>` | §9 №2 |
| 13 | `` `intersecting regions are rejected`() `` | `r(0,0,10,10)`, `r(9,9,10,10)` | `assertFailsWith<IllegalArgumentException>` | §9 №3 |

Проверка теста 8: X-отрезки по 50 px без касаний (шаг 200) → 10·50 = 500; Y: [0,40) и [500,540) → 80.
Проверка теста 10: X-отрезки [0,10),[50,60),[1000,1010) → offset 0, 10, 20.

Моки: не нужны. `Random` — `kotlin.random.Random`.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.domain.StitchLayoutTest"` | 13 тестов проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная проверка: Н/П.

## 13. Критерии готовности (Definition of Done)

- [ ] Все шаги раздела 7 выполнены.
- [ ] Сигнатуры совпадают с разделом 6.
- [ ] Случаи раздела 9 обработаны.
- [ ] 13 тестов раздела 11 написаны и проходят.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не копировать пиксели и не импортировать `java.awt` — это TASK-027.
- Не изменять `PixelRect.kt`.
- Не добавлять зависимости.

## 15. Ловушки и подсказки

- При слиянии отрезков используй условие `s <= curEnd` (касающиеся отрезки сливаются в один), как в разделе 8.
- Проверка пересечения — через `PixelRect.intersect` (касание → null), а не через сравнение `right >= left`.
- Сортировка `placements` обязательна, иначе тест 9 упадёт.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

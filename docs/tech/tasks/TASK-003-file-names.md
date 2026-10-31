# TASK-003: Реализовать имена файлов снимков, нормализацию «.png» и сокращение пути многоточием

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | S (≤ 1 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.28, .29, .38, .39; US-005 AC-1, AC-2; US-006 AC-2, AC-6 |
| ADR | — |
| Зависит от | TASK-001 |
| Блокирует | TASK-009, TASK-011, TASK-012 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Три чистые утилиты:
- `ScreenshotFileNames` — имя файла по времени захвата и с суффиксом при коллизии;
- `SaveAsFileName` — дописывание `.png` к имени из диалога «Сохранить как»;
- `TextEllipsis` — сокращение длинного пути многоточием в середине (для уведомления).

## 2. Контекст

Имя автосохранения строится по шаблону из локального времени захвата. Если файл уже есть, перебираются суффиксы
`_1`, `_2`… Сам перебор с проверкой диска делается в TASK-009, здесь — только построение строки. В «Сохранить как»
пользователь может ввести имя без расширения или с другим расширением — файл всегда PNG.

**Выдержки из требований (дословно):**

> **REQ-001.28** Система должна давать файлу имя по шаблону `Screenshot_yyyy-MM-dd_HH-mm-ss.png`, где время — локальное время момента захвата.
>
> **REQ-001.29** Если файл с таким именем уже существует, система должна добавить к имени суффикс `_1`, `_2`, … (`Screenshot_2026-10-18_20-00-48_1.png`). Существующие файлы не должны перезаписываться.
>
> **REQ-001.39** Файл всегда сохраняется в формате PNG. Если имя, введённое пользователем, не заканчивается на `.png` (без учёта регистра), система должна дописать `.png`: `login-error` → `login-error.png`, `login.jpg` → `login.jpg.png`, `v1.2` → `v1.2.png`.
>
> **US-005 (заметки)** строка с путём к папке (длинный путь сокращается многоточием в середине).

## 3. Предусловия

- TASK-001 выполнена. `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

`M = src/main/kotlin/by/sparkyanvil/fastcowscreenshot/domain/`, `T = src/test/kotlin/by/sparkyanvil/fastcowscreenshot/domain/`.

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `M/ScreenshotFileNames.kt` | Создать | `ScreenshotFileNames` |
| 2 | `M/SaveAsFileName.kt` | Создать | `SaveAsFileName` |
| 3 | `M/TextEllipsis.kt` | Создать | `TextEllipsis` |
| 4 | `T/ScreenshotFileNamesTest.kt`, `T/SaveAsFileNameTest.kt`, `T/TextEllipsisTest.kt` | Создать | Тесты |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.domain

import java.nio.file.Path
import java.time.LocalDateTime

/** Имена файлов автосохранения. Без состояния, потокобезопасен. */
internal object ScreenshotFileNames {
    const val PREFIX: String = "Screenshot_"
    const val EXTENSION: String = ".png"
    const val MAX_SUFFIX: Int = 9999
    const val TIMESTAMP_PATTERN: String = "yyyy-MM-dd_HH-mm-ss"

    /** "Screenshot_2026-10-18_20-00-48" (без расширения). */
    fun baseName(capturedAt: LocalDateTime): String = TODO()

    /**
     * index = 0 → "Screenshot_…48.png"; index = n ≥ 1 → "Screenshot_…48_n.png".
     * @throws IllegalArgumentException если index !in 0..MAX_SUFFIX.
     */
    fun candidate(capturedAt: LocalDateTime, index: Int): String = TODO()
}

/** Нормализация имени «Сохранить как» (REQ-001.39). */
internal object SaveAsFileName {
    /** Дописывает ".png", если name не оканчивается на ".png" без учёта регистра. @throws IllegalArgumentException если name.isEmpty(). */
    fun normalizeName(name: String): String = TODO()

    /** То же для полного пути: меняется только последний элемент (path.resolveSibling). */
    fun normalize(path: Path): Path = TODO()
}

/** Сокращение строк. */
internal object TextEllipsis {
    const val ELLIPSIS: String = "…" // U+2026

    /**
     * Если text.length <= maxChars — text без изменений. Иначе head + "…" + tail,
     * где head = (maxChars - 1) / 2 первых символов, tail = maxChars - 1 - head последних. Длина результата = maxChars.
     * @throws IllegalArgumentException если maxChars < 5.
     */
    fun middle(text: String, maxChars: Int): String = TODO()
}
```

## 7. Пошаговая реализация

### Шаг 1. ScreenshotFileNames
- Форматтер — `DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN)` в `private val` объекта.
- `baseName` = `PREFIX + formatter.format(capturedAt)`.
- `candidate` = `baseName + EXTENSION` для 0, `baseName + "_" + index + EXTENSION` для остальных.

### Шаг 2. SaveAsFileName
- `normalizeName`: `if (name.endsWith(EXTENSION, ignoreCase = true)) name else name + EXTENSION`
  (`EXTENSION` = `ScreenshotFileNames.EXTENSION`).
- `normalize(path)`: `val file = path.fileName.toString()`; результат — `path.resolveSibling(normalizeName(file))`.

### Шаг 3. TextEllipsis — по KDoc.

### Шаг 4. Тесты (раздел 11) → `.\gradlew.bat test` проходит.

## 8. Алгоритм и логика

**Пример:** `capturedAt = LocalDateTime.of(2026, 10, 18, 20, 0, 48)` → `baseName` = `Screenshot_2026-10-18_20-00-48`,
`candidate(…, 0)` = `Screenshot_2026-10-18_20-00-48.png`, `candidate(…, 1)` = `Screenshot_2026-10-18_20-00-48_1.png`.

`middle("abcdefghij", 5)` → head = 2 → `"ab"`, tail = 2 → `"ij"` → `"ab…ij"`.
`middle("abcdefghij", 6)` → head = 2, tail = 3 → `"ab…hij"`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | `candidate(…, -1)` или `10000` | `IllegalArgumentException` | — | исключение |
| 2 | `normalizeName("")` | `IllegalArgumentException` | — | исключение |
| 3 | `normalizeName("shot.PNG")` | без изменений | — | `shot.PNG` |
| 4 | `middle(text, 4)` | `IllegalArgumentException` | — | исключение |

## 10. Константы и тексты

Константы — в разделе 6. Текстов UI нет.

## 11. Тесты

| # | Класс / метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `ScreenshotFileNamesTest` / `` `base name uses local timestamp`() `` | 2026-10-18 20:00:48 | `baseName` | `"Screenshot_2026-10-18_20-00-48"` | REQ-001.28 |
| 2 | `` `index 0 has no suffix`() `` | то же | `candidate(t, 0)` | `"Screenshot_2026-10-18_20-00-48.png"` | US-005 AC-1 |
| 3 | `` `index 1 adds suffix`() `` | то же | `candidate(t, 1)` | `"Screenshot_2026-10-18_20-00-48_1.png"` | US-005 AC-2 |
| 4 | `` `pads single digit time parts`() `` | 2026-01-02 03:04:05 | `baseName` | `"Screenshot_2026-01-02_03-04-05"` | REQ-001.28 |
| 5 | `` `rejects index out of range`() `` | — | `candidate(t, 10000)` | `IllegalArgumentException` | — |
| 6 | `SaveAsFileNameTest` / `` `appends png to plain name`() `` | `"login-error"` | `normalizeName` | `"login-error.png"` | REQ-001.39 |
| 7 | `` `appends png to other extension`() `` | `"login.jpg"` | `normalizeName` | `"login.jpg.png"` | US-006 AC-6 |
| 8 | `` `appends png to dotted name`() `` | `"v1.2"` | `normalizeName` | `"v1.2.png"` | REQ-001.39 |
| 9 | `` `keeps uppercase PNG`() `` | `"shot.PNG"` | `normalizeName` | `"shot.PNG"` | US-006 граничный случай |
| 10 | `` `normalizes last path element only`() `` | `Path.of("D:\\Projects\\Bug-42\\login-error")` | `normalize` | `Path.of("D:\\Projects\\Bug-42\\login-error.png")` | US-006 AC-2 |
| 11 | `TextEllipsisTest` / `` `short text unchanged`() `` | `"abc"`, 5 | `middle` | `"abc"` | — |
| 12 | `` `odd budget`() `` | `"abcdefghij"`, 5 | `middle` | `"ab…ij"` | US-005 |
| 13 | `` `even budget`() `` | `"abcdefghij"`, 6 | `middle` | `"ab…hij"` | US-005 |
| 14 | `` `rejects too small budget`() `` | `"abcdefghij"`, 4 | `middle` | `IllegalArgumentException` | — |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.domain.*"` | все тесты пакета проходят (включая TASK-002, если она сделана) |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–4 выполнены, сигнатуры — как в разделе 6.
- [x] 14 тестов раздела 11 проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не обращаться к файловой системе (проверка существования — TASK-009).
- Не обрезать пробелы в именах и не заменять символы.

## 15. Ловушки и подсказки

- В шаблоне `HH` (24 часа), а не `hh`; `mm` — минуты, `MM` — месяц.
- `Path.of("D:\\…")` на Windows работает и в тестах; тесты запускаются только на Windows.
- `…` — один символ U+2026, не три точки.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-24 | Review | Реализовано по задаче без отклонений. TASK-002 переведена в Done по указанию пользователя. `gradlew build` — BUILD SUCCESSFUL; 14 тестов TASK-003 пройдены (всего 33). |

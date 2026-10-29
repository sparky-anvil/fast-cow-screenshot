# TASK-009: Реализовать атомарную запись PNG, автосохранение и определение папки «Снимки экрана»

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.27–.32, .44, .46, .49, .50, N4; US-005 AC-1–AC-4, AC-8; US-007 AC-1, AC-4 |
| ADR | ADR-002, ADR-003 |
| Зависит от | TASK-003, TASK-004, TASK-005 |
| Блокирует | TASK-010, TASK-012, TASK-014 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать пакет `storage`:
- `PngFileWriter` — кодирует PNG во временный файл в той же папке и переименовывает его в итоговое имя;
- `AutoSaver` — создаёт папку, подбирает свободное имя и сохраняет;
- `IoErrorMessages` — понятная пользователю причина ошибки;
- `DirectoryValidator` — проверка права записи в папку.

Плюс `KnownFolders` (WinAPI) — начальная директория по умолчанию.

## 2. Контекст

- **Атомарность (REQ-001.32).** PNG пишется во временный файл `.fcs-*.tmp` в целевой папке. После успешной записи
  он переименовывается в итоговое имя. Если запись оборвалась — временный файл удаляется, итогового файла нет.
- `Files.move(tmp, target)` **без опций** на одном томе — атомарное переименование. Если `target` уже существует,
  бросается `FileAlreadyExistsException`: так существующий файл не перезаписывается (REQ-001.29). Для «Сохранить как»
  с подтверждённой перезаписью — `REPLACE_EXISTING, ATOMIC_MOVE`.
- Скорость (N4 ≤ 1,5 с для 4K): `ImageWriteParam.compressionMode = MODE_EXPLICIT`, `compressionQuality = 0.5f`
  (deflate-уровень 4), `ImageIO.setUseCache(false)`.
- Known Folder `FOLDERID_Screenshots` = `{B7BEDE81-DF94-4682-A7D8-57A52620B86F}`,
  `FOLDERID_Pictures` = `{33E28130-4E1E-4676-835A-98395C3BC3BB}`. Получаются через
  `com.sun.jna.platform.win32.Shell32Util.getKnownFolderPath(Guid.GUID)`. Это учитывает перенаправление в OneDrive.
  Если папка не определена, бросается `Win32Exception`.
- Java NIO на Windows работает через UTF-16 API, поэтому кириллица, пробелы, UNC-пути и длинные пути поддерживаются
  без специальных действий (REQ-001.49–.51).

**Выдержки из требований (дословно):**

> **REQ-001.29** Если файл с таким именем уже существует, система должна добавить к имени суффикс `_1`, `_2`, … Существующие файлы не должны перезаписываться.
>
> **REQ-001.30** Если директории по умолчанию не существует, система должна создать её вместе с промежуточными директориями.
>
> **REQ-001.31** Если записать файл невозможно (нет прав, диск заполнен или отключён, путь недоступен), система должна показать уведомление с причиной и предложить «Сохранить как». Снимок должен оставаться в буфере обмена и в памяти как последний снимок.
>
> **REQ-001.32** Запись файла должна быть атомарной: при сбое во время записи в директории не должен оставаться повреждённый или неполный PNG-файл с итоговым именем.
>
> **REQ-001.44** При первом запуске система должна устанавливать директорию по умолчанию в системную папку «Снимки экрана» пользователя (Known Folder `FOLDERID_Screenshots`…). Если папка недоступна — в `%USERPROFILE%\Pictures`.
>
> **REQ-001.46** При выборе новой директории система должна проверять, что в неё можно записывать.

## 3. Предусловия

- TASK-003: `ScreenshotFileNames.candidate`, `ScreenshotFileNames.MAX_SUFFIX`.
- TASK-004: `DefaultDirectoryProvider`.
- TASK-005: JNA подключена (используется только `jna-platform` `Shell32Util`).
- `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

`S = src/main/kotlin/by/sparkyanvil/fastcowscreenshot/storage/`, `TS` — то же в `src/test/kotlin`.

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `S/SaveResult.kt` | Создать | sealed |
| 2 | `S/ScreenshotSaver.kt` | Создать | интерфейс |
| 3 | `S/PngFileWriter.kt` | Создать | класс |
| 4 | `S/AutoSaver.kt` | Создать | класс |
| 5 | `S/IoErrorMessages.kt` | Создать | object |
| 6 | `S/DirectoryValidator.kt` | Создать | object |
| 7 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/KnownFolders.kt` | Создать | класс |
| 8 | `TS/PngFileWriterTest.kt`, `TS/AutoSaverTest.kt`, `TS/IoErrorMessagesTest.kt`, `TS/DirectoryValidatorTest.kt` | Создать | unit |
| 9 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/KnownFoldersTest.kt` | Создать | integration |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.storage

/** Результат сохранения. reason — текст для пользователя (на русском). */
internal sealed interface SaveResult {
    data class Saved(val file: java.nio.file.Path) : SaveResult
    data class Failed(val directory: java.nio.file.Path, val reason: String) : SaveResult
}

/** Автосохранение. Вызывать на fcs-save. Не бросает исключений. */
internal interface ScreenshotSaver {
    fun save(image: java.awt.image.BufferedImage, capturedAt: java.time.LocalDateTime, directory: java.nio.file.Path): SaveResult
}

/** Атомарная запись PNG. Потокобезопасен (без состояния). */
internal class PngFileWriter {
    /**
     * @param replaceExisting false — если target существует, бросает FileAlreadyExistsException, target не меняется;
     *                        true — заменяет target атомарно.
     * @throws java.io.IOException при любой ошибке; временный файл в этом случае удалён.
     */
    fun writeAtomically(image: java.awt.image.BufferedImage, target: java.nio.file.Path, replaceExisting: Boolean): Unit = TODO()

    companion object {
        const val COMPRESSION_QUALITY: Float = 0.5f
        const val TEMP_PREFIX: String = ".fcs-"
        const val TEMP_SUFFIX: String = ".tmp"
    }
}

internal class AutoSaver(private val writer: PngFileWriter = PngFileWriter()) : ScreenshotSaver

/** Причина ошибки ввода-вывода для пользователя. */
internal object IoErrorMessages {
    const val ACCESS_DENIED: String = "Нет прав на запись в папку"
    const val DISK_FULL: String = "Недостаточно места на диске"
    const val UNAVAILABLE: String = "Папка недоступна"
    const val TOO_MANY_FILES: String = "Слишком много файлов с одинаковым именем"
    /** Раздел 8.3. */
    fun describe(e: java.io.IOException): String = TODO()
}

internal object DirectoryValidator {
    /** true, если dir — существующая папка и в ней удалось создать и удалить временный файл. Не бросает. */
    fun isWritable(dir: java.nio.file.Path): Boolean = TODO()
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** Начальная директория по умолчанию (REQ-001.44). Вызывать с любого потока. */
internal class KnownFolders : DefaultDirectoryProvider {
    override fun initialDirectory(): java.nio.file.Path = TODO()
    companion object {
        const val FOLDERID_SCREENSHOTS: String = "{B7BEDE81-DF94-4682-A7D8-57A52620B86F}"
        const val FOLDERID_PICTURES: String = "{33E28130-4E1E-4676-835A-98395C3BC3BB}"
    }
}
```

## 7. Пошаговая реализация

1. `SaveResult`, `ScreenshotSaver`, `IoErrorMessages` + тест.
2. `PngFileWriter` + тест (8.1).
3. `AutoSaver` + тест (8.2).
4. `DirectoryValidator` + тест.
5. `KnownFolders` + тест (8.4).

## 8. Алгоритм и логика

### 8.1. PngFileWriter.writeAtomically

1. `dir = target.parent`; `tmp = Files.createTempFile(dir, TEMP_PREFIX, TEMP_SUFFIX)`.
2. `try`:
   - `writer = ImageIO.getImageWritersByFormatName("png").next()`; `param = writer.defaultWriteParam`;
     `if (param.canWriteCompressed()) { param.compressionMode = ImageWriteParam.MODE_EXPLICIT; param.compressionQuality = COMPRESSION_QUALITY }`;
   - `ImageIO.createImageOutputStream(tmp.toFile()).use { out -> writer.output = out; writer.write(null, IIOImage(image, null, null), param) }`;
     `writer.dispose()`;
   - `if (replaceExisting) Files.move(tmp, target, REPLACE_EXISTING, ATOMIC_MOVE) else Files.move(tmp, target)`.
3. `catch (e: IOException) { Files.deleteIfExists(tmp); throw e }`.
4. В `init` объекта-компаньона или в конструкторе: `ImageIO.setUseCache(false)`.

### 8.2. AutoSaver.save

```
start = nanoTime
try {
  Files.createDirectories(directory)
  for (index in 0..MAX_SUFFIX) {
    target = directory.resolve(ScreenshotFileNames.candidate(capturedAt, index))
    if (Files.exists(target)) continue
    try { writer.writeAtomically(image, target, replaceExisting = false) }
    catch (e: FileAlreadyExistsException) { continue }
    log.info("Saved {} in {} ms", target, ms); return Saved(target)
  }
  log.error("Auto-save failed in {}: too many files", directory); return Failed(directory, TOO_MANY_FILES)
} catch (e: IOException) {
  log.error("Auto-save failed in {}: {}", directory, e.toString()); return Failed(directory, IoErrorMessages.describe(e))
}
```

### 8.3. IoErrorMessages.describe

1. `e is AccessDeniedException` → `ACCESS_DENIED`.
2. `text = (e as? FileSystemException)?.reason ?: e.message ?: ""`.
3. `text.contains("not enough space", ignoreCase = true)` → `DISK_FULL`.
4. `e is NoSuchFileException` или `text` содержит (без учёта регистра) `"not ready"`, `"network"` или `"cannot find the path"` → `UNAVAILABLE`.
5. Иначе `"Ошибка записи: $text"`; если `text` пустой — `"Ошибка записи: ${e.javaClass.simpleName}"`.

### 8.4. KnownFolders.initialDirectory

1. `try { return Path.of(Shell32Util.getKnownFolderPath(Guid.GUID.fromString(FOLDERID_SCREENSHOTS))) } catch (e: Win32Exception) { log.warn("FOLDERID_Screenshots unavailable: {}", e.message) }`.
2. Так же для `FOLDERID_PICTURES`.
3. `return Path.of(System.getenv("USERPROFILE") ?: System.getProperty("user.home"), "Pictures")`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | Папки нет | Создаётся с промежуточными | — | `Saved` |
| 2 | Нет прав | `Failed(dir, "Нет прав на запись в папку")` | `ERROR "Auto-save failed in …"` | — |
| 3 | Диск заполнен во время записи | tmp удалён, итогового файла нет | `ERROR` | `Failed(…, "Недостаточно места на диске")` |
| 4 | Отключённый диск / сеть | `Failed(…, "Папка недоступна")` | `ERROR` | — |
| 5 | Файл появился между `exists` и `move` | следующий индекс | — | `Saved(…_N.png)` |
| 6 | Все 10 000 имён заняты | `Failed(…, TOO_MANY_FILES)` | `ERROR` | — |

## 10. Константы и тексты

Тексты причин (дословно): `"Нет прав на запись в папку"`, `"Недостаточно места на диске"`, `"Папка недоступна"`,
`"Слишком много файлов с одинаковым именем"`, `"Ошибка записи: <текст>"`.

## 11. Тесты

`image` — `BufferedImage(40, 30, TYPE_INT_RGB)`, пиксель (5,5) = `0x336699`; `t = LocalDateTime.of(2026,10,18,20,0,48)`;
`@TempDir tempDir`.

| # | Класс / метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `PngFileWriterTest` / `` `writes readable png`() `` | — | `writeAtomically(image, tempDir/"a.png", false)` | `ImageIO.read` → 40×30, пиксель (5,5) `and 0xFFFFFF == 0x336699`; в папке нет `*.tmp` | REQ-001.27 |
| 2 | `` `does not overwrite without flag`() `` | `a.png` с байтами `"old"` | то же | `FileAlreadyExistsException`; содержимое `"old"`; нет `*.tmp` | REQ-001.29, .32 |
| 3 | `` `replaces with flag`() `` | `a.png` с `"old"` | `…, true` | `ImageIO.read` успешен | REQ-001.40 |
| 4 | `AutoSaverTest` / `` `creates missing cyrillic directory`() `` | `dir = tempDir/"Изображения"/"Снимки экрана"` (нет) | `save(image, t, dir)` | `Saved(dir/"Screenshot_2026-10-18_20-00-48.png")`, файл есть | REQ-001.30, .49, US-005 AC-1, AC-3 |
| 5 | `` `adds suffix on collision`() `` | в `dir` есть `Screenshot_2026-10-18_20-00-48.png` с `"x"` | `save` | `Saved(…_1.png)`; старый файл = `"x"` | US-005 AC-2 |
| 6 | `` `race on move picks next index`() `` | `writer = mockk<PngFileWriter>()`: при `target.fileName == "…48.png"` бросает `FileAlreadyExistsException`, иначе `just Runs` | `save` | `Saved(…_1.png)` | REQ-001.29 |
| 7 | `` `access denied gives failed result`() `` | writer бросает `AccessDeniedException("x")` | `save` | `Failed(dir, "Нет прав на запись в папку")` | REQ-001.31 |
| 8 | `IoErrorMessagesTest` / `` `maps known errors`() `` | `AccessDeniedException("p")`; `FileSystemException("p", null, "There is not enough space on the disk")`; `NoSuchFileException("p")`; `FileSystemException("p", null, "The device is not ready")`; `IOException("boom")` | `describe` | `ACCESS_DENIED`, `DISK_FULL`, `UNAVAILABLE`, `UNAVAILABLE`, `"Ошибка записи: boom"` | REQ-001.31 |
| 9 | `DirectoryValidatorTest` / `` `existing temp dir is writable`() `` | `tempDir` | `isWritable` | `true`, папка пуста после вызова | REQ-001.46 |
| 10 | `` `missing dir is not writable`() `` | `tempDir/"nope"` | `isWritable` | `false` | REQ-001.46 |
| 11 | `` `file is not writable dir`() `` | `tempDir/"f.txt"` (файл) | `isWritable` | `false` | — |
| 12 | `KnownFoldersTest` / `` `returns absolute path`() `` `@EnabledOnOs(OS.WINDOWS)` | — | `KnownFolders().initialDirectory()` | `isAbsolute == true` | REQ-001.44 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.storage.*"` | 11 тестов |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.platform.windows.KnownFoldersTest"` | 1 тест |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (пользователь): в журнал записать путь, который вернул тест 12 (вывести в лог `INFO` в тесте). Он должен
совпадать с папкой, куда сохраняет Win+PrtSc.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–5 выполнены; контракты — как в разделе 6.
- [x] Ни в одной ветке ошибки не остаётся `*.tmp`.
- [x] 12 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не писать PNG сразу в итоговый файл.
- Не использовать `File.renameTo`, `java.io.File` для логики (кроме `tmp.toFile()` для ImageIO).
- Не определять путь через `user.home + "\\Pictures\\Screenshots"` в обход Known Folders.

## 15. Ловушки и подсказки

- `Files.exists` + `move` — не атомарная пара. Поэтому `FileAlreadyExistsException` из `move` обязательно ловится
  отдельно (тест 6).
- `FileAlreadyExistsException` — подкласс `FileSystemException` → `IOException`. Внутренний `catch` должен стоять
  **до** внешнего `catch (IOException)`. Это обеспечивает вложенный `try` в 8.2.
- Временный файл — в **той же** папке: перенос между томами не атомарен.
- MockK: `every { writer.writeAtomically(any(), match { it.fileName.toString().endsWith("48.png") }, false) } throws FileAlreadyExistsException("x")`.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-29 | Review | Реализованы SaveResult, ScreenshotSaver, PngFileWriter, AutoSaver, IoErrorMessages, DirectoryValidator, KnownFolders и 12 тестов (storage - 11, KnownFolders - 1), все пройдены; ``gradlew build`` - BUILD SUCCESSFUL. Ручная проверка: KnownFolders вернул ``C:\Users\<пользователь>\Pictures\Screenshots``, пользователь подтвердил совпадение с папкой Win+PrtSc. TASK-003 по решению пользователя переведена в Done. Отклонений нет. |

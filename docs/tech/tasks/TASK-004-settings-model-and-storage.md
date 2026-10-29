# TASK-004: Реализовать модель настроек, горячие клавиши и JSON-хранилище

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.4, .35, .44, .45, .47, .52, .53, .55; US-007 AC-1, AC-3, AC-5 |
| ADR | ADR-003 |
| Зависит от | TASK-002 |
| Блокирует | TASK-007, TASK-009, TASK-010, TASK-014 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать в пакете `settings`:
- модель настроек `AppSettings` (сериализуемую в JSON);
- представление горячей клавиши `HotkeyBinding` (разбор и форматирование строк вида `Ctrl+Shift+1`);
- `SettingsRepository` — чтение и атомарную запись `settings.json`;
- `SettingsStore` — текущее значение в памяти с уведомлением подписчиков.

## 2. Контекст

- Файл настроек — `%APPDATA%\fast-cow-screenshot\settings.json`, UTF-8 (ADR-003).
- Начальная директория по умолчанию — системная папка «Снимки экрана». Её путь определяет WinAPI (TASK-009), поэтому
  репозиторий получает его через интерфейс `DefaultDirectoryProvider`. В тестах подставляется лямбда.
- Горячая клавиша хранится строкой (`"Ctrl+Shift+1"`). `HotkeyBinding.keyCode` — **виртуальный код клавиши Windows**:
  `'0'..'9'` = `0x30..0x39`, `'A'..'Z'` = `0x41..0x5A`, `F1..F12` = `0x70..0x7B`.
- Модификаторы `RegisterHotKey`: `MOD_ALT = 0x0001`, `MOD_CONTROL = 0x0002`, `MOD_SHIFT = 0x0004`,
  `MOD_NOREPEAT = 0x4000` (удержание клавиши не порождает повторов).
- Допущение TDD-001 §17: допустимы только сочетания с Ctrl и/или Alt.

**Выдержки из требований (дословно):**

> **REQ-001.52** Горячие клавиши по умолчанию: `Ctrl+Shift+1` — «Монитор под курсором», `Ctrl+Shift+2` — «Все мониторы», `Ctrl+Shift+3` — «Область».
>
> **REQ-001.4** Система должна позволять переназначить горячую клавишу каждого режима в настройках. Новое значение должно сохраняться между перезапусками.
>
> **REQ-001.45** Система должна сохранять выбранную директорию по умолчанию между перезапусками приложения.
>
> **REQ-001.47** Если сохранённая директория недоступна при запуске (например, отключён съёмный или сетевой диск), система не должна молча менять настройку.
>
> **REQ-001.53** … флажок «Включать указатель мыши в снимок» (по умолчанию выключен) … Значение сохраняется между перезапусками.
>
> **REQ-001.55** Система должна запоминать последнюю папку «Сохранить как…» между перезапусками приложения. Эта папка хранится отдельно от директории по умолчанию.
>
> **US-007 (граничный случай)** Файл настроек повреждён или удалён — применяется значение из AC-1.

## 3. Предусловия

- TASK-002 выполнена: есть `by.sparkyanvil.fastcowscreenshot.domain.CaptureMode`.
- `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

`M = src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/`, `T = src/test/kotlin/by/sparkyanvil/fastcowscreenshot/settings/`.

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `M/HotkeyAction.kt` | Создать | enum |
| 2 | `M/HotkeyBinding.kt` | Создать | data class + companion |
| 3 | `M/HotkeySettings.kt` | Создать | `@Serializable` |
| 4 | `M/AppSettings.kt` | Создать | `@Serializable` |
| 5 | `M/DefaultDirectoryProvider.kt` | Создать | fun interface |
| 6 | `M/SettingsRepository.kt` | Создать | чтение/запись |
| 7 | `M/SettingsStore.kt` | Создать | значение в памяти |
| 8 | `T/HotkeyBindingTest.kt`, `T/SettingsRepositoryTest.kt`, `T/SettingsStoreTest.kt` | Создать | тесты |
| 9 | `build.gradle.kts` (корень) | Изменить | только блок `tasks.test { }`: добавить флаги JVM для MockK (шаг 0, CLR-001) |

**Файлы, которые изменять нельзя:** все, кроме перечисленных. В `build.gradle.kts` нельзя менять ничего, кроме блока
`tasks.test { }`; список `appJvmArgs` не трогать (он же используется для запуска приложения).

## 5. Зависимости

Новых нет (`kotlinx-serialization-json` и плагин serialization подключены в TASK-001). Флаги тестовой JVM — шаг 0.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.settings

/** Действие горячей клавиши. displayName — текст для UI. */
internal enum class HotkeyAction(val displayName: String, val mode: CaptureMode) {
    MONITOR_UNDER_CURSOR("Монитор под курсором", CaptureMode.MONITOR_UNDER_CURSOR),
    ALL_MONITORS("Все мониторы", CaptureMode.ALL_MONITORS),
    REGION("Область", CaptureMode.REGION),
}

/**
 * Сочетание клавиш. keyCode — виртуальный код Windows.
 * init: require(ctrl || alt) { "Ctrl or Alt is required" }; require(isSupportedKey(keyCode)) { "Unsupported key: $keyCode" }.
 */
internal data class HotkeyBinding(val ctrl: Boolean, val alt: Boolean, val shift: Boolean, val keyCode: Int) {
    /** "Ctrl+Alt+Shift+K": порядок Ctrl, Alt, Shift, клавиша; только включённые модификаторы. */
    fun format(): String = TODO()
    /** Битовая маска для RegisterHotKey: MOD_* включённых модификаторов OR MOD_NOREPEAT. */
    val winModifiers: Int get() = TODO()

    companion object {
        const val MOD_ALT: Int = 0x0001
        const val MOD_CONTROL: Int = 0x0002
        const val MOD_SHIFT: Int = 0x0004
        const val MOD_NOREPEAT: Int = 0x4000
        /** 0x30..0x39, 0x41..0x5A, 0x70..0x7B. */
        fun isSupportedKey(keyCode: Int): Boolean = TODO()
        /** "0".."9", "A".."Z", "F1".."F12"; null для неподдерживаемых. */
        fun keyName(keyCode: Int): String? = TODO()
        /** Разбор строки; null, если строка некорректна (раздел 8). Никогда не бросает исключений. */
        fun parse(text: String): HotkeyBinding? = TODO()
        /** Значения по умолчанию REQ-001.52: Ctrl+Shift+1 / 2 / 3. */
        fun defaultFor(action: HotkeyAction): HotkeyBinding = TODO()
    }
}

@Serializable
internal data class HotkeySettings(
    val monitorUnderCursor: String = "Ctrl+Shift+1",
    val allMonitors: String = "Ctrl+Shift+2",
    val region: String = "Ctrl+Shift+3",
) {
    fun get(action: HotkeyAction): String = TODO()
    fun with(action: HotkeyAction, text: String): HotkeySettings = TODO() // copy(...) нужного поля
}

@Serializable
internal data class AppSettings(
    val version: Int = CURRENT_VERSION,
    val defaultDirectory: String,
    val lastSaveAsDirectory: String? = null,
    val includeCursor: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val hotkeys: HotkeySettings = HotkeySettings(),
) {
    /** HotkeyBinding.parse(hotkeys.get(action)) ?: HotkeyBinding.defaultFor(action). */
    fun hotkeyBinding(action: HotkeyAction): HotkeyBinding = TODO()

    companion object { const val CURRENT_VERSION: Int = 1 }
}

/** Источник начальной директории по умолчанию (REQ-001.44). Реализация на WinAPI — TASK-009. */
internal fun interface DefaultDirectoryProvider {
    fun initialDirectory(): java.nio.file.Path
}

/** Чтение/запись settings.json. Не потокобезопасен — используется только из SettingsStore. */
internal class SettingsRepository(
    private val file: java.nio.file.Path,
    private val defaults: DefaultDirectoryProvider,
) {
    /** Раздел 8 «load». Никогда не бросает исключений. */
    fun load(): AppSettings = TODO()
    /** Атомарная запись. @throws java.io.IOException при ошибке записи. */
    fun save(settings: AppSettings): Unit = TODO()
    /** AppSettings(defaultDirectory = defaults.initialDirectory().toString()). */
    fun defaultSettings(): AppSettings = TODO()

    companion object {
        const val FILE_NAME: String = "settings.json"
        const val APP_DIR_NAME: String = "fast-cow-screenshot"
        /** Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), APP_DIR_NAME, FILE_NAME). */
        fun defaultFile(): java.nio.file.Path = TODO()
    }
}

/** Текущие настройки в памяти. Потокобезопасен: current — @Volatile, update — synchronized. */
internal class SettingsStore(private val repository: SettingsRepository) {
    /** Значение, загруженное repository.load() в init, далее — последнее после update. */
    val current: AppSettings get() = TODO()
    /**
     * new = transform(current). Если new == current → true, ничего не делать.
     * Иначе: repository.save(new); current = new; вызвать слушателей с new (в текущем потоке); вернуть true.
     * При IOException из save: log ERROR "Failed to save settings: {}" (e.message); current = new; вызвать слушателей; вернуть false.
     */
    fun update(transform: (AppSettings) -> AppSettings): Boolean = TODO()
    /** Слушатели хранятся в CopyOnWriteArrayList. */
    fun addListener(listener: (AppSettings) -> Unit): Unit = TODO()
}
```

Импорт `CaptureMode`: `by.sparkyanvil.fastcowscreenshot.domain.CaptureMode`. `@Serializable` — `kotlinx.serialization.Serializable`.

## 7. Пошаговая реализация

0. `build.gradle.kts`: блок `tasks.test` заменить целиком на эталонный фрагмент (CLR-001, CLR-002). Строку
   `jvmArgs("-Djdk.attach.allowAttachSelf=true", "-XX:+EnableDynamicAgentLoading")`, если она уже есть, удалить.
   ```kotlin
   tasks.test {
       useJUnitPlatform()
       jvmArgs(appJvmArgs)
       // MockK: агент ByteBuddy подключается при старте JVM (-javaagent), а не self-attach,
       // который на JDK 25 не работает и ломается на путях с кириллицей (CLR-001, CLR-002).
       doFirst {
           val agentJar = classpath.first { it.name.startsWith("byte-buddy-agent") }
           jvmArgs("-javaagent:${agentJar.absolutePath}", "-Xshare:off")
       }
   }
   ```
   `byte-buddy-agent` приходит в тестовый classpath транзитивно через `io.mockk:mockk` — новую зависимость
   не добавлять. `-Xshare:off` убирает предупреждение JVM `Sharing is only supported for boot loader classes`.
   Результат: `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.settings.SettingsStoreTest"` → 3 теста
   пройдены (проверено Tech на машине с профилем `C:\Users\Александр`).
1. `HotkeyAction.kt`, `HotkeyBinding.kt` — по разделам 6 и 8. Компиляция проходит.
2. `HotkeySettings.kt`, `AppSettings.kt`, `DefaultDirectoryProvider.kt`.
3. `SettingsRepository.kt`: `private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }`.
4. `SettingsStore.kt`, логгер `LoggerFactory.getLogger(SettingsStore::class.java)`.
5. Тесты раздела 11 → `.\gradlew.bat test` проходит.

## 8. Алгоритм и логика

**parse(text):**
1. `parts = text.split('+').map { it.trim() }`. Если какая-то часть пустая → `null`.
2. Последняя часть — клавиша: `keyCodeOf(last.uppercase())`: одиночный символ `0-9` → его код (`'5'.code` = 0x35);
   `A-Z` → его код; `F1`..`F12` → `0x70 + (n - 1)`; иначе → `null`.
3. Остальные части — модификаторы без учёта регистра: `ctrl`, `alt`, `shift`. Неизвестный модификатор или повтор
   модификатора → `null`.
4. Если нет ни Ctrl, ни Alt → `null`. Иначе — `HotkeyBinding(...)`.

Примеры: `"Ctrl+Shift+1"` → `(true,false,true,0x31)`; `" ctrl + shift + f5 "` → `(true,false,true,0x74)`;
`"Shift+1"` → `null`; `"Ctrl+Shift"` → `null`; `"Ctrl+Ctrl+1"` → `null`; `"Ctrl+Enter"` → `null`; `""` → `null`.

**winModifiers:** `(ctrl?2:0) | (alt?1:0) | (shift?4:0) | 0x4000`. Для `Ctrl+Shift+1` → `0x4006`.

**format:** `HotkeyBinding(true,true,true,0x41).format()` → `"Ctrl+Alt+Shift+A"`; `HotkeyBinding(false,true,false,0x7B)` → `"Alt+F12"`.

**load():**
1. Если файла нет → `s = defaultSettings()`; `save(s)` в `try` (`IOException` → `WARN "Failed to write initial settings: {}"`); вернуть `s`.
2. Иначе прочитать `Files.readString(file, UTF_8)` и `json.decodeFromString<AppSettings>(text)`.
   - `IOException` → `WARN "Failed to read settings, defaults used: {}"`, вернуть `defaultSettings()`.
   - `SerializationException` или `IllegalArgumentException` → `WARN "Settings file is corrupted, defaults used"`, вернуть `defaultSettings()`. Файл **не перезаписывать** (перезапишется при следующем `save`).
3. Существование `defaultDirectory` на диске **не проверять** (REQ-001.47).

**save(s):** `Files.createDirectories(file.parent)`; `tmp = file.resolveSibling("$FILE_NAME.tmp")`;
`Files.writeString(tmp, json.encodeToString(s), UTF_8)`;
`Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | Нет файла | По умолчанию + запись | — | файл создан |
| 2 | Не JSON / нет поля `defaultDirectory` | По умолчанию, файл не трогать | `WARN "Settings file is corrupted, defaults used"` | — |
| 3 | Неизвестные поля | Игнорируются | — | — |
| 4 | Строка клавиши некорректна | `hotkeyBinding` вернёт значение по умолчанию | — | — |
| 5 | Ошибка записи в `update` | В памяти новое значение, `false` | `ERROR "Failed to save settings: {}"` | UI покажет ошибку (TASK-014) |
| 6 | Путь с кириллицей | Сохраняется и читается без искажений (UTF-8) | — | — |

## 10. Константы и тексты

Константы — в разделе 6. Тексты UI: `HotkeyAction.displayName` — `"Монитор под курсором"`, `"Все мониторы"`, `"Область"`.

## 11. Тесты

Для репозитория: `@TempDir lateinit var tempDir: Path`, `file = tempDir.resolve("cfg").resolve("settings.json")`,
`provider = DefaultDirectoryProvider { Path.of("C:\\Users\\Александр\\Pictures\\Screenshots") }`.

| # | Класс / метод | Given | When | Then | Покрывает |
|---|---|---|---|---|---|
| 1 | `HotkeyBindingTest` / `` `parses default binding`() `` | `"Ctrl+Shift+1"` | `parse` | `HotkeyBinding(true,false,true,0x31)` | REQ-001.52 |
| 2 | `` `parse is case and space insensitive`() `` | `" ctrl + shift + f5 "` | `parse` | `(true,false,true,0x74)` | REQ-001.4 |
| 3 | `` `parse rejects invalid`() `` | `"Shift+1"`, `"Ctrl+Shift"`, `"Ctrl+Ctrl+1"`, `"Ctrl+Enter"`, `""` | `parse` | все `null` | — |
| 4 | `` `format round trip`() `` | `HotkeyBinding(true,true,true,0x41)` | `format`, затем `parse` | `"Ctrl+Alt+Shift+A"`, равно исходному | REQ-001.4 |
| 5 | `` `win modifiers include norepeat`() `` | `Ctrl+Shift+1` | `winModifiers` | `0x4006` | REQ-001.2 |
| 6 | `` `constructor rejects binding without ctrl or alt`() `` | — | `HotkeyBinding(false,false,true,0x31)` | `IllegalArgumentException` | — |
| 7 | `` `defaults per action`() `` | — | `defaultFor(REGION).format()` | `"Ctrl+Shift+3"` | REQ-001.52 |
| 8 | `SettingsRepositoryTest` / `` `first load creates file with defaults`() `` | файла нет | `load()` | `defaultDirectory == "C:\\Users\\Александр\\Pictures\\Screenshots"`, `includeCursor == false`, `autoSaveEnabled == true`, `hotkeys == HotkeySettings()`, файл существует | US-007 AC-1, REQ-001.22 |
| 9 | `` `save and load round trip with cyrillic`() `` | `save(defaults.copy(defaultDirectory="D:\\Скриншоты", lastSaveAsDirectory="D:\\Projects\\Bug-42", includeCursor=true))` | `load()` | равно сохранённому | US-007 AC-3, REQ-001.55, .53 |
| 10 | `` `corrupted file gives defaults and is kept`() `` | в файле `"{not json"` | `load()` | равно `defaultSettings()`; содержимое файла по-прежнему `"{not json"` | US-007 |
| 11 | `` `unknown keys ignored`() `` | JSON по TDD-001 §11 + `"extra": 5` | `load()` | значения из файла | — |
| 12 | `` `missing directory on disk is not reset`() `` | сохранено `defaultDirectory = "Z:\\offline"` (диска нет) | `load()` | `"Z:\\offline"` | REQ-001.47 |
| 13 | `` `invalid hotkey text falls back to default`() `` | `AppSettings(defaultDirectory="x", hotkeys=HotkeySettings(region="bad"))` | `hotkeyBinding(REGION)` | `defaultFor(REGION)` | — |
| 14 | `SettingsStoreTest` / `` `update saves and notifies`() `` | store на репозитории из tempDir, слушатель пишет в список | `update { it.copy(includeCursor = true) }` | `true`; `current.includeCursor`; слушатель вызван 1 раз; новый `SettingsRepository(file, provider).load().includeCursor == true` | REQ-001.53 |
| 15 | `` `update with same value does nothing`() `` | то же | `update { it }` | `true`, слушатель не вызван | — |
| 16 | `` `update keeps value in memory when save fails`() `` | `repo = mockk<SettingsRepository>()`: `load()` → `AppSettings(defaultDirectory="x")`, `save(any())` бросает `IOException("disk")` | `update { it.copy(autoSaveEnabled=false) }` | `false`; `current.autoSaveEnabled == false` | REQ-001.35 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.settings.*"` | 16 тестов пройдены |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 0–5 выполнены; сигнатуры — как в разделе 6.
- [x] Все случаи раздела 9 обработаны.
- [x] 16 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не вызывать WinAPI и не определять Known Folder (TASK-009).
- Не регистрировать горячие клавиши (TASK-007).
- Не добавлять поля в `AppSettings` сверх раздела 6.

## 15. Ловушки и подсказки

- `encodeDefaults = true` нужен, чтобы в файл попадали все поля, включая значения по умолчанию.
- `SettingsRepository` — обычный (не `open`) класс. MockK мокает final-классы через inline-агент ByteBuddy, который
  на JDK 25 надёжно работает только при подключении через `-javaagent` (шаг 0). Без него тест №16 падает с
  `Could not self-attach to current VM`, а с флагом `-Djdk.attach.allowAttachSelf=true` — с
  `Error opening zip file or JAR manifest missing` (кириллица в пути `~\.gradle`). Флаги self-attach не использовать.
- `SerializationException` — `kotlinx.serialization.SerializationException`. `MissingFieldException` — его подкласс.
- `Files.move(..., ATOMIC_MOVE)` с `REPLACE_EXISTING` на NTFS атомарно заменяет файл.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-29 | In Progress | Начата реализация. |
| 2026-10-29 | Blocked | Шаги 1–5 выполнены, 48 из 49 тестов проходят. Тест №16 (MockK) падает: self-attach на JDK 25 → CLR-001 (Tech). |
| 2026-10-29 | In Progress | CLR-001 закрыт: флаги из шага 0 добавлены в tasks.test. |
| 2026-10-29 | Blocked | Тест №16 всё равно падает: агент не открывается из-за кириллицы в пути ~/.gradle (48/49 тестов зелёные) → CLR-002 (Tech). |
| 2026-10-29 | In Progress | CLR-002 закрыт, возобновлено. |
| 2026-10-29 | Review | Шаг 0 применён (-javaagent). `.\gradlew.bat build` — SUCCESS, 16 тестов settings (7+6+3), 0 failed. Ручных проверок нет. Отклонений нет. |

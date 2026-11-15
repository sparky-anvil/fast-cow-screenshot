# TASK-031: Добавить настройки редактора (`openInEditor`, цвет и толщина карандаша) и флажок в окне настроек

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-004 §5, §10, §11 |
| Требования | REQ-003.1, REQ-003.4, REQ-003.17, REQ-003.18, REQ-003.19; US-011 AC-1, US-012 AC-4 |
| ADR | ADR-006 |
| Зависит от | — |
| Блокирует | TASK-036, TASK-037 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Добавить в модель настроек флажок «Открывать снимок в редакторе» и настройки карандаша (цвет из палитры, толщина),
сохранить их в `settings.json` и вывести флажок в окно настроек. Сам редактор в этой задаче не делается.

## 2. Контекст

Настройки — `AppSettings` (`@Serializable data class`), читаются и пишутся `SettingsRepository` в
`%APPDATA%\fast-cow-screenshot\settings.json`. JSON настроен с `ignoreUnknownKeys = true` и `encodeDefaults = true`:
если в старом файле нет нового поля, берётся значение по умолчанию, при следующей записи поле появится. Изменения
делаются через `SettingsStore.update { it.copy(...) }`, который возвращает `false`, если запись не удалась.

`SettingsController` — логика окна настроек; каждый сеттер возвращает текст ошибки или `null`
(`ERR_SAVE = "Не удалось сохранить настройки"` — уже есть). Окно настроек `SettingsWindow` (Compose) в разделе общих
настроек показывает флажки «Включать указатель мыши в снимок» и «Автоматически сохранять снимки…».

Цвет хранится строкой `#RRGGBB` (как `MultiRegionSettings.fillColor`); для разбора и форматирования уже есть
`MultiRegionSettings.parseHexColor(text): Int?` и `MultiRegionSettings.formatHexColor(rgb): String`.

**Выдержки из требований (дословно):**

> **REQ-003.1** Система должна предоставлять в настройках флажок «Открывать снимок в редакторе» (по умолчанию выключен). Значение сохраняется между перезапусками.
>
> **REQ-003.4** Изменение флажка применяется со следующего захвата. Уже открытый редактор не закрывается.
>
> **REQ-003.17** Толщина задаётся в физических пикселях изображения и не зависит от масштаба просмотра. Варианты: 2, 4, 8, 16 px. По умолчанию 4 px.
>
> **REQ-003.18** Цвет выбирается из палитры: красный `#FF0000` (по умолчанию), жёлтый `#FFD800`, зелёный `#00C853`, синий `#2979FF`, чёрный `#000000`, белый `#FFFFFF`. Штрихи непрозрачные.
>
> **REQ-003.19** Система должна запоминать последние выбранные цвет и толщину между открытиями редактора и между перезапусками приложения.
>
> **US-011, заметка UI:** Флажок — в общем разделе настроек рядом с «Включать указатель мыши в снимок».

## 3. Предусловия

- В проекте уже существуют:
  - `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/AppSettings.kt` — поля `version`, `defaultDirectory`,
    `lastSaveAsDirectory`, `includeCursor`, `autoSaveEnabled`, `hotkeys`, `multiRegion`;
  - `settings/MultiRegionSettings.kt` — `companion` с `parseHexColor`, `formatHexColor`;
  - `ui/SettingsController.kt` — `setIncludeCursor(value: Boolean): String?`, `setAutoSave(value: Boolean): String?`,
    константа `ERR_SAVE`;
  - `ui/SettingsWindow.kt` — `Row { Checkbox(checked = s.includeCursor, …); Text("Включать указатель мыши в снимок") }`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/EditorSettings.kt` | Создать | `EditorSettings` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/AppSettings.kt` | Изменить | поля `openInEditor`, `editor` в конце |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsController.kt` | Изменить | метод `setOpenInEditor` |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsWindow.kt` | Изменить | строка с флажком |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/settings/EditorSettingsTest.kt` | Создать | тесты 1–6 |
| 6 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/settings/SettingsRepositoryTest.kt` | Изменить | тесты 7–8 |
| 7 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsControllerTest.kt` | Изменить | тест 9 |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.settings

import kotlinx.serialization.Serializable

/**
 * Настройки карандаша редактора (REQ-003.17–19). Хранятся как есть; неверные значения заменяются по умолчанию
 * только при чтении через penRgb()/effectivePenWidth().
 */
@Serializable
internal data class EditorSettings(
    val penColor: String = DEFAULT_PEN_COLOR, // "#RRGGBB", одно из PALETTE
    val penWidth: Int = DEFAULT_PEN_WIDTH,    // px изображения, одно из PEN_WIDTHS
) {
    /** RGB цвета 0xRRGGBB, если penColor разбирается и входит в PALETTE_RGB; иначе DEFAULT_PEN_RGB. */
    fun penRgb(): Int = TODO()

    /** penWidth, если входит в PEN_WIDTHS; иначе DEFAULT_PEN_WIDTH. */
    fun effectivePenWidth(): Int = TODO()

    companion object {
        const val DEFAULT_PEN_COLOR: String = "#FF0000"
        const val DEFAULT_PEN_RGB: Int = 0xFF0000
        const val DEFAULT_PEN_WIDTH: Int = 4

        /** Палитра в порядке показа на панели (REQ-003.18). */
        val PALETTE: List<String> = listOf("#FF0000", "#FFD800", "#00C853", "#2979FF", "#000000", "#FFFFFF")

        /** PALETTE в виде 0xRRGGBB, тот же порядок. */
        val PALETTE_RGB: List<Int> = listOf(0xFF0000, 0xFFD800, 0x00C853, 0x2979FF, 0x000000, 0xFFFFFF)

        /** Толщины в порядке показа (REQ-003.17). */
        val PEN_WIDTHS: List<Int> = listOf(2, 4, 8, 16)
    }
}
```

`AppSettings` — два поля **после** `multiRegion` (порядок важен для читаемости JSON):

```kotlin
internal data class AppSettings(
    val version: Int = CURRENT_VERSION,
    val defaultDirectory: String,
    val lastSaveAsDirectory: String? = null,
    val includeCursor: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val hotkeys: HotkeySettings = HotkeySettings(),
    val multiRegion: MultiRegionSettings = MultiRegionSettings(),
    val openInEditor: Boolean = false,          // REQ-003.1
    val editor: EditorSettings = EditorSettings(), // REQ-003.19
)
```

`SettingsController`:

```kotlin
/** Флажок «Открывать снимок в редакторе» (REQ-003.1). Ошибка (ERR_SAVE) или null. */
fun setOpenInEditor(value: Boolean): String?
```

**Используемые существующие контракты:**

```kotlin
// settings/MultiRegionSettings.kt, companion object
fun parseHexColor(text: String): Int?   // "#RRGGBB" → 0xRRGGBB, иначе null
fun formatHexColor(rgb: Int): String    // 0xRRGGBB → "#RRGGBB" (верхний регистр)
// settings/SettingsStore.kt
val current: AppSettings
fun update(transform: (AppSettings) -> AppSettings): Boolean
```

## 7. Пошаговая реализация

### Шаг 1. `EditorSettings.kt`
- Создать файл по разделу 6. `penRgb()`:
  `MultiRegionSettings.parseHexColor(penColor)?.takeIf { it in PALETTE_RGB } ?: DEFAULT_PEN_RGB`.
  `effectivePenWidth()`: `if (penWidth in PEN_WIDTHS) penWidth else DEFAULT_PEN_WIDTH`.
- **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 2. `AppSettings.kt`
- Добавить два поля в конец списка параметров (раздел 6), KDoc класса не менять. `CURRENT_VERSION` не менять.
- **Результат:** компиляция проходит.

### Шаг 3. `SettingsController.kt`
- Сразу после `setAutoSave` добавить:

```kotlin
/** Флажок «Открывать снимок в редакторе» (REQ-003.1). Ошибка (ERR_SAVE) или null. */
fun setOpenInEditor(value: Boolean): String? =
    if (store.update { it.copy(openInEditor = value) }) null else ERR_SAVE
```

### Шаг 4. `SettingsWindow.kt`
- Сразу **после** блока `Row { Checkbox(checked = s.includeCursor, …); Text("Включать указатель мыши в снимок") }`
  и **до** строки с автосохранением вставить:

```kotlin
Row {
    Checkbox(checked = s.openInEditor, onCheckedChange = { generalError = controller.setOpenInEditor(it) })
    Text("Открывать снимок в редакторе")
}
```

- Оформление точно как у соседних строк (тот же `Row` без дополнительных модификаторов).
- **Результат:** компиляция проходит.

### Шаг 5. Тесты — раздел 11. **Результат:** `.\gradlew.bat build` проходит.

## 8. Алгоритм и логика

Н/П — логика в разделе 7.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | В старом `settings.json` нет `openInEditor` / `editor` | значения по умолчанию `false` / `EditorSettings()` | — | флажок выключен |
| 2 | `penColor = "#123456"` (не из палитры) или `"red"` | `penRgb() == 0xFF0000` | — | — |
| 3 | `penWidth = 5` | `effectivePenWidth() == 4` | — | — |
| 4 | `penColor = "#ffd800"` (нижний регистр) | `penRgb() == 0xFFD800` | — | — |
| 5 | Запись настроек не удалась | `setOpenInEditor` → `ERR_SAVE` | существующий WARN `SettingsStore` | `"Не удалось сохранить настройки"` под общим разделом |

## 10. Константы и тексты

| Имя | Тип | Значение | Где |
|---|---|---|---|
| `DEFAULT_PEN_COLOR` | `String` | `"#FF0000"` | `EditorSettings.kt` |
| `DEFAULT_PEN_RGB` | `Int` | `0xFF0000` | `EditorSettings.kt` |
| `DEFAULT_PEN_WIDTH` | `Int` | `4` | `EditorSettings.kt` |
| `PALETTE` | `List<String>` | `"#FF0000", "#FFD800", "#00C853", "#2979FF", "#000000", "#FFFFFF"` | `EditorSettings.kt` |
| `PALETTE_RGB` | `List<Int>` | `0xFF0000, 0xFFD800, 0x00C853, 0x2979FF, 0x000000, 0xFFFFFF` | `EditorSettings.kt` |
| `PEN_WIDTHS` | `List<Int>` | `2, 4, 8, 16` | `EditorSettings.kt` |

Тексты UI: `"Открывать снимок в редакторе"`.

## 11. Тесты

`EditorSettingsTest.kt` (пакет `by.sparkyanvil.fastcowscreenshot.settings`):

| # | Метод | Given / When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `defaults are red and 4 px`() `` | `EditorSettings()` | `penColor == "#FF0000"`, `penRgb() == 0xFF0000`, `effectivePenWidth() == 4` | REQ-003.17, 3.18 |
| 2 | `` `palette color is parsed`() `` | `EditorSettings(penColor = "#2979FF")` | `penRgb() == 0x2979FF` | 3.18 |
| 3 | `` `lowercase palette color is parsed`() `` | `"#ffd800"` | `0xFFD800` | §9 №4 |
| 4 | `` `color outside palette falls back to red`() `` | `"#123456"`, затем `"red"` | оба `0xFF0000` | §9 №2 |
| 5 | `` `unsupported width falls back to 4`() `` | `penWidth = 5`, затем `0` | оба `4`; `penWidth = 16` → `16` | §9 №3 |
| 6 | `` `palette lists match`() `` | `PALETTE.map { MultiRegionSettings.parseHexColor(it) }` | равно `PALETTE_RGB`; размер 6 | 3.18 |

`SettingsRepositoryTest.kt` — добавить:

| # | Метод | Given | Then | Покрывает |
|---|---|---|---|---|
| 7 | `` `old file without editor fields gives defaults`() `` | файл из теста `unknown keys ignored` (тот же JSON без `openInEditor`/`editor`) | `loaded.openInEditor == false`; `loaded.editor == EditorSettings()` | §9 №1 |
| 8 | `` `editor settings round trip`() `` | `repo.defaultSettings().copy(openInEditor = true, editor = EditorSettings("#FFD800", 2))`, `save`, `load` | загруженное равно сохранённому | REQ-003.1, 3.19, US-012 AC-4 |

`SettingsControllerTest.kt` — добавить:

| # | Метод | When | Then | Покрывает |
|---|---|---|---|---|
| 9 | `` `set open in editor saves flag`() `` | `controller().setOpenInEditor(true)` | возвращает `null`; `store.current.openInEditor == true`; затем `setOpenInEditor(false)` → `store.current.openInEditor == false` | REQ-003.1 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.settings.*"` | Все тесты проходят |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.SettingsControllerTest"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (`.\gradlew.bat run`):
1. Трей → «Настройки…» → под «Включать указатель мыши в снимок» есть флажок «Открывать снимок в редакторе», выключен.
2. Включить флажок, закрыть окно, «Выход» в трее, запустить снова → флажок включён; в `settings.json` есть
   `"openInEditor": true` и блок `"editor": { "penColor": "#FF0000", "penWidth": 4 }`.
3. Выключить флажок обратно (пока нет TASK-036 и TASK-038, флажок на захват не влияет).

## 13. Критерии готовности (Definition of Done)

- [x] Сигнатуры совпадают с разделом 6; поля `AppSettings` — последними.
- [x] Тесты 1–9 написаны и проходят; старые тесты проходят без изменений.
- [x] Ручная проверка 1–3 выполнена.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не повышать `AppSettings.CURRENT_VERSION` и не писать миграции.
- Не добавлять в окно настроек выбор цвета/толщины карандаша (они выбираются в редакторе).
- Не трогать `CaptureCoordinator` (TASK-036).

## 15. Ловушки и подсказки

- `EditorSettings` обязательно с `@Serializable`, иначе `AppSettings` не скомпилируется (плагин сериализации требует
  сериализуемые типы полей).
- `PALETTE_RGB` — константы без альфа-канала (`0xFF0000`, а не `0xFFFF0000`).

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-11-15 | Review | Реализованы шаги 1–5, тесты 1–9. `gradlew build` — BUILD SUCCESSFUL. Ручная проверка 1–3 пройдена пользователем. Отклонений нет. |

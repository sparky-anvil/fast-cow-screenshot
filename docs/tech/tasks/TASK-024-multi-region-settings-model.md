# TASK-024: Добавить настройки режима «Несколько областей» и модель клавиш оверлея

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-003 §5, §10, §11 |
| Требования | REQ-002.11, 2.13, 2.16, 2.18, 2.21, 2.27, 2.28; US-010 AC-1, AC-2 |
| ADR | ADR-003, ADR-005 |
| Зависит от | — |
| Блокирует | TASK-025, TASK-027, TASK-028 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Добавить в пакет `settings` модель локальных клавиш оверлея (`OverlayKey`, `OverlayAction`, `KeySlot`,
`OverlayKeymap`) и сохраняемые настройки режима (`MultiRegionSettings`), подключить их к `AppSettings`.
UI и использование настроек — в следующих задачах.

## 2. Контекст

Оверлей «Несколько областей» управляется **локальными** клавишами: они действуют, только пока оверлей открыт, и не
регистрируются в Windows. В отличие от глобальных `HotkeyBinding` (обязателен Ctrl/Alt, коды Windows), клавиша
оверлея может быть без модификаторов (`Enter`, `Esc`) и хранится в кодах AWT `java.awt.event.KeyEvent.VK_*`.
`NumPad Enter` имеет тот же код `VK_ENTER` (10), но другое «расположение» — поэтому флаг `numpad`.

У каждого из трёх действий два слота: основное (обязательно) и дополнительное (может быть пустым = «не назначено»).
Настройки хранятся в `settings.json` как строки; при некорректных значениях в файле берутся значения по умолчанию.

**Выдержки из требований (дословно):**

> **REQ-002.13** Клавиша «Удалить последнюю область» (по умолчанию `Backspace`, дополнительно `Ctrl+Z`) удаляет последний добавленный регион.
>
> **REQ-002.16** Клавиша «Собрать снимок» (по умолчанию `Enter`, дополнительно `NumPad Enter`) закрывает оверлей и запускает склейку.
>
> **REQ-002.18** Клавиша «Отмена» (по умолчанию `Esc`) закрывает оверлей без создания снимка.
>
> **REQ-002.21** Пиксели итогового изображения, не принадлежащие ни одному региону, система должна заливать непрозрачным цветом из настройки «Цвет заливки» (по умолчанию `#808080`).
>
> **REQ-002.27** … «Максимум областей» (целое 2–50, по умолчанию 10); «Цвет заливки» (выбор цвета и ввод HEX `#RRGGBB`, по умолчанию `#808080`). Значения сохраняются между перезапусками.
>
> **REQ-002.28** Клавиши оверлея (REQ-002.13, 16, 18) — локальные (действуют только при открытом оверлее), не регистрируются глобально. Каждое действие допускает основное и дополнительное сочетание.
>
> **US-010 AC-1** … «Собрать» `Enter`/`NumPad Enter`, «Удалить последнюю» `Backspace`/`Ctrl+Z`, «Отмена» `Esc`, лимит 10, цвет `#808080`.

## 3. Предусловия

- Существуют: `settings/AppSettings.kt` (`@Serializable data class AppSettings(version, defaultDirectory,
  lastSaveAsDirectory, includeCursor, autoSaveEnabled, hotkeys)`), `settings/SettingsRepository.kt`
  (`Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }`), `settings/HotkeyBinding.kt`
  (образец стиля `parse`/`format`).
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/OverlayAction.kt` | Создать | `enum OverlayAction` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/KeySlot.kt` | Создать | `enum KeySlot` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/OverlayKey.kt` | Создать | `OverlayKey` |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/OverlayKeymap.kt` | Создать | `OverlayKeymap` |
| 5 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/MultiRegionSettings.kt` | Создать | `MultiRegionSettings` |
| 6 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/settings/AppSettings.kt` | Изменить | поле `multiRegion` последним параметром |
| 7 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/settings/OverlayKeyTest.kt` | Создать | тесты 1–12 |
| 8 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/settings/OverlayKeymapTest.kt` | Создать | тесты 13–16 |
| 9 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/settings/MultiRegionSettingsTest.kt` | Создать | тесты 17–27 |

**Файлы, которые изменять нельзя:** все остальные, в том числе `HotkeySettings.kt`, `HotkeyAction.kt`,
`HotkeyBinding.kt`, `SettingsRepository.kt` (это TASK-027).

## 5. Зависимости

Новых зависимостей нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.settings

/** Действие оверлея «Несколько областей». displayName — текст для UI. */
internal enum class OverlayAction(val displayName: String) {
    CONFIRM("Собрать снимок"),
    REMOVE_LAST("Удалить последнюю область"),
    CANCEL("Отмена"),
}

/** Слот сочетания действия: основное или дополнительное. */
internal enum class KeySlot { PRIMARY, SECONDARY }

/**
 * Локальная клавиша оверлея. keyCode — java.awt.event.KeyEvent.VK_* (java.awt не импортировать: константы ниже).
 * numpad = true допустим только для VK_ENTER (клавиша Enter цифрового блока).
 * @throws IllegalArgumentException если !isSupportedKey(keyCode) или numpad && keyCode != VK_ENTER.
 */
internal data class OverlayKey(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val keyCode: Int,
    val numpad: Boolean = false,
) {
    /** "Ctrl+Alt+Shift+<имя>": порядок Ctrl, Alt, Shift, имя клавиши keyName(keyCode, numpad). */
    fun format(): String = TODO()

    /** true, если все пять значений равны полям. Не бросает (для неподдерживаемых кодов — false). */
    fun matches(ctrl: Boolean, alt: Boolean, shift: Boolean, keyCode: Int, numpad: Boolean): Boolean = TODO()

    companion object {
        const val VK_BACK_SPACE: Int = 8
        const val VK_ENTER: Int = 10
        const val VK_ESCAPE: Int = 27
        const val VK_SPACE: Int = 32
        const val VK_DELETE: Int = 127

        /** 0x30..0x39, 0x41..0x5A, 0x70..0x7B, VK_BACK_SPACE, VK_ENTER, VK_ESCAPE, VK_SPACE, VK_DELETE. */
        fun isSupportedKey(keyCode: Int): Boolean = TODO()

        /** Имя клавиши (таблица раздела 10) или null для неподдерживаемых. numpad учитывается только для VK_ENTER. */
        fun keyName(keyCode: Int, numpad: Boolean): String? = TODO()

        /**
         * Разбор строки format(). Пустая строка или строка из пробелов → null. Регистр не важен ("ctrl+z", "numpad enter").
         * Модификаторы — "Ctrl", "Alt", "Shift", каждый не более одного раза. Никогда не бросает.
         */
        fun parse(text: String): OverlayKey? = TODO()
    }
}

/** Соответствие нажатия действию. Неизменяем. */
internal data class OverlayKeymap(
    val confirm: List<OverlayKey>,
    val removeLast: List<OverlayKey>,
    val cancel: List<OverlayKey>,
) {
    /** Список клавиш действия. */
    fun keys(action: OverlayAction): List<OverlayKey> = TODO()

    /**
     * Первое действие в порядке CONFIRM, REMOVE_LAST, CANCEL, у которого есть клавиша с matches(...) == true; иначе null.
     */
    fun actionFor(ctrl: Boolean, alt: Boolean, shift: Boolean, keyCode: Int, numpad: Boolean): OverlayAction? = TODO()

    /** keys(action).firstOrNull()?.format() ?: "—" (длинное тире U+2014). */
    fun primaryName(action: OverlayAction): String = TODO()
}

/** Настройки режима «Несколько областей» (сохраняются в settings.json). */
@kotlinx.serialization.Serializable
internal data class MultiRegionSettings(
    val maxRegions: Int = DEFAULT_MAX_REGIONS,
    val fillColor: String = DEFAULT_FILL_COLOR,
    val confirmKey: String = "Enter",
    val confirmKeyAlt: String = "NumPad Enter",
    val removeLastKey: String = "Backspace",
    val removeLastKeyAlt: String = "Ctrl+Z",
    val cancelKey: String = "Esc",
    val cancelKeyAlt: String = "",
) {
    /** maxRegions.coerceIn(MIN_REGIONS, MAX_REGIONS). */
    fun effectiveMaxRegions(): Int = TODO()

    /** parseHexColor(fillColor) ?: DEFAULT_FILL_RGB. */
    fun fillRgb(): Int = TODO()

    /** Строка слота: CONFIRM/PRIMARY → confirmKey, CONFIRM/SECONDARY → confirmKeyAlt и т. п. для всех 6 сочетаний. */
    fun key(action: OverlayAction, slot: KeySlot): String = TODO()

    /** Копия с заменённой строкой слота. */
    fun withKey(action: OverlayAction, slot: KeySlot, text: String): MultiRegionSettings = TODO()

    /**
     * Клавиша слота: OverlayKey.parse(key(action, slot)).
     * Если результат null и slot == PRIMARY → OverlayKey.parse(defaultKey(action, PRIMARY)) (никогда не null).
     * Если результат null и slot == SECONDARY → null («не назначено»).
     */
    fun resolvedKey(action: OverlayAction, slot: KeySlot): OverlayKey? = TODO()

    /** OverlayKeymap: для каждого действия listOfNotNull(resolvedKey(PRIMARY), resolvedKey(SECONDARY)). */
    fun keymap(): OverlayKeymap = TODO()

    companion object {
        const val MIN_REGIONS: Int = 2
        const val MAX_REGIONS: Int = 50
        const val DEFAULT_MAX_REGIONS: Int = 10
        const val DEFAULT_FILL_COLOR: String = "#808080"
        const val DEFAULT_FILL_RGB: Int = 0x808080

        /** Значение по умолчанию слота из MultiRegionSettings() (например, CANCEL/SECONDARY → ""). */
        fun defaultKey(action: OverlayAction, slot: KeySlot): String = TODO()

        /** "#RRGGBB" (ровно 7 символов, hex в любом регистре) → 0xRRGGBB; иначе null. Без trim. Не бросает. */
        fun parseHexColor(text: String): Int? = TODO()

        /** 0xRRGGBB → "#RRGGBB" заглавными: "#%06X".format(rgb and 0xFFFFFF). */
        fun formatHexColor(rgb: Int): String = TODO()
    }
}
```

Изменение `AppSettings` (добавить последним параметром конструктора, остальное не трогать):

```kotlin
    val hotkeys: HotkeySettings = HotkeySettings(),
    val multiRegion: MultiRegionSettings = MultiRegionSettings(),
```

## 7. Пошаговая реализация

### Шаг 1. `OverlayAction.kt`, `KeySlot.kt`
- Объявить enum по разделу 6 (KDoc обязателен). **Результат:** `.\gradlew.bat compileKotlin` проходит.

### Шаг 2. `OverlayKey.kt`
- Реализовать по разделам 6, 8. **Результат:** компиляция.

### Шаг 3. `OverlayKeymap.kt`
- Реализовать. **Результат:** компиляция.

### Шаг 4. `MultiRegionSettings.kt`
- Реализовать. Аннотация — `import kotlinx.serialization.Serializable` + `@Serializable`. **Результат:** компиляция.

### Шаг 5. `AppSettings.kt`
- Добавить поле `multiRegion` (раздел 6). `CURRENT_VERSION` не менять. **Результат:** `.\gradlew.bat build` проходит
  (существующие тесты не ломаются: поле имеет значение по умолчанию).

### Шаг 6. Тесты
- Три тестовых файла по разделу 11. **Результат:** все тесты проходят.

## 8. Алгоритм и логика

**`keyName(keyCode, numpad)`**:
- `0x30..0x39`, `0x41..0x5A` → `keyCode.toChar().toString()` ("0".."9", "A".."Z");
- `0x70..0x7B` → `"F${keyCode - 0x70 + 1}"`;
- `VK_ENTER` → `if (numpad) "NumPad Enter" else "Enter"`;
- `VK_BACK_SPACE` → `"Backspace"`, `VK_ESCAPE` → `"Esc"`, `VK_SPACE` → `"Space"`, `VK_DELETE` → `"Delete"`;
- иначе `null`.

**`parse(text)`**:
1. `parts = text.split('+').map { it.trim() }`. Если `text.isBlank()` или любая часть пустая → `null`.
2. Имя клавиши = `parts.last()`; сравнить без учёта регистра (`equals(…, ignoreCase = true)`) со списком:
   `"Enter"`→(VK_ENTER,false), `"NumPad Enter"`→(VK_ENTER,true), `"Backspace"`, `"Esc"`, `"Space"`, `"Delete"`;
   одна буква/цифра → `uppercase()[0].code`, если в диапазонах; `"F1".."F12"` → `0x70 + n − 1`. Нет совпадения → `null`.
3. Модификаторы `parts.dropLast(1)`: `"ctrl"`, `"alt"`, `"shift"` (lowercase); повтор или неизвестное слово → `null`.
4. Вернуть `OverlayKey(ctrl, alt, shift, keyCode, numpad)`.

**`format()`**: `buildList { if (ctrl) add("Ctrl"); if (alt) add("Alt"); if (shift) add("Shift"); add(keyName(keyCode, numpad).orEmpty()) }.joinToString("+")`.

**Примеры:** `parse("Ctrl+Z")` = `OverlayKey(true,false,false,0x5A)`; `parse("numpad enter")` =
`OverlayKey(false,false,false,10,true)`; `OverlayKey(false,false,true,0x41).format()` = `"Shift+A"`;
`parseHexColor("#ff00Aa")` = `0xFF00AA`; `formatHexColor(0xFF00AA)` = `"#FF00AA"`.

**`parseHexColor`**: `Regex("^#[0-9A-Fa-f]{6}$")`; совпало → `text.substring(1).toInt(16)`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Результат |
|---|---|---|---|
| 1 | `parse("")`, `parse("  ")`, `parse("Ctrl+")`, `parse("Ctrl+Ctrl+Z")`, `parse("Win+Z")`, `parse("Tab")` | null | — |
| 2 | `OverlayKey(false,false,false,9)` (Tab) | `IllegalArgumentException("Unsupported key: 9")` | — |
| 3 | `OverlayKey(false,false,false,0x41,numpad=true)` | `IllegalArgumentException("numpad is allowed only for Enter")` | — |
| 4 | В JSON `maxRegions = 99` / `1` | `effectiveMaxRegions()` = 50 / 2; поле не переписывается | — |
| 5 | В JSON `fillColor = "red"` | `fillRgb()` = `0x808080` | — |
| 6 | В JSON `confirmKey = "???"` | `resolvedKey(CONFIRM, PRIMARY)` = Enter | — |
| 7 | В JSON `cancelKeyAlt = "???"` | `resolvedKey(CANCEL, SECONDARY)` = null | — |
| 8 | В JSON нет объекта `multiRegion` | значения по умолчанию | — |

## 10. Константы и тексты

| Имя клавиши (`keyName`) | keyCode | numpad |
|---|---|---|
| `"Enter"` | 10 | false |
| `"NumPad Enter"` | 10 | true |
| `"Backspace"` | 8 | false |
| `"Esc"` | 27 | false |
| `"Space"` | 32 | false |
| `"Delete"` | 127 | false |
| `"0"`–`"9"`, `"A"`–`"Z"` | 0x30–0x39, 0x41–0x5A | false |
| `"F1"`–`"F12"` | 0x70–0x7B | false |

Тексты `OverlayAction.displayName`: `"Собрать снимок"`, `"Удалить последнюю область"`, `"Отмена"`.
Остальные константы — в разделе 6.

## 11. Тесты

`OverlayKeyTest.kt`:

| # | Метод | Given / When | Then |
|---|---|---|---|
| 1 | `` `formats modifiers in fixed order`() `` | `OverlayKey(true,true,true,0x5A).format()` | `"Ctrl+Alt+Shift+Z"` |
| 2 | `` `formats special keys`() `` | VK 10, 10+numpad, 8, 27, 32, 127, 0x70, 0x7B, 0x30 | `"Enter"`, `"NumPad Enter"`, `"Backspace"`, `"Esc"`, `"Space"`, `"Delete"`, `"F1"`, `"F12"`, `"0"` |
| 3 | `` `parses default keys`() `` | `parse` строк `"Enter"`, `"NumPad Enter"`, `"Backspace"`, `"Ctrl+Z"`, `"Esc"` | `OverlayKey(false,false,false,10)`, `(…,10,true)`, `(…,8)`, `(true,false,false,0x5A)`, `(…,27)` |
| 4 | `` `parse is case insensitive`() `` | `parse("ctrl+shift+numpad enter")` | `OverlayKey(true,false,true,10,true)` |
| 5 | `` `parse and format round trip`() `` | для каждого поддерживаемого keyCode × модификаторы (8 комбинаций) | `parse(k.format()) == k`; плюс NumPad Enter |
| 6 | `` `parse rejects invalid strings`() `` | строки §9 №1 | все `null` |
| 7 | `` `parse trims spaces around parts`() `` | `parse(" Ctrl + Z ")` | `OverlayKey(true,false,false,0x5A)` |
| 8 | `` `unsupported key code throws`() `` | `OverlayKey(false,false,false,9)` | `assertFailsWith<IllegalArgumentException>` |
| 9 | `` `numpad only for enter`() `` | `OverlayKey(false,false,false,0x41,true)` | `assertFailsWith<IllegalArgumentException>` |
| 10 | `` `matches requires exact modifiers`() `` | `Ctrl+Z`.matches(true,false,false,0x5A,false) / (true,false,true,0x5A,false) | true / false |
| 11 | `` `enter does not match numpad enter`() `` | `Enter`.matches(false,false,false,10,true) | false |
| 12 | `` `matches unsupported code returns false`() `` | `Enter`.matches(false,false,false,9,false) | false, без исключения |

`OverlayKeymapTest.kt` (keymap = `MultiRegionSettings().keymap()`):

| # | Метод | When | Then |
|---|---|---|---|
| 13 | `` `default keymap maps keys to actions`() `` | `actionFor` для Enter, NumPad Enter, Backspace, Ctrl+Z, Esc | CONFIRM, CONFIRM, REMOVE_LAST, REMOVE_LAST, CANCEL |
| 14 | `` `unknown key returns null`() `` | `actionFor(false,false,false,0x41,false)`; `actionFor(false,false,false,0x5A,false)` (Z без Ctrl) | null, null |
| 15 | `` `first action wins on duplicate`() `` | `OverlayKeymap(listOf(esc), emptyList(), listOf(esc))`, нажатие Esc | CONFIRM |
| 16 | `` `primary name uses first key or dash`() `` | default `primaryName(CONFIRM)`; `OverlayKeymap(emptyList(), …).primaryName(CONFIRM)` | `"Enter"`, `"—"` |

`MultiRegionSettingsTest.kt`:

| # | Метод | Given / When | Then |
|---|---|---|---|
| 17 | `` `defaults match requirements`() `` | `MultiRegionSettings()` | maxRegions 10, fillColor `"#808080"`, `keymap().keys(CONFIRM)` = [Enter, NumPad Enter], REMOVE_LAST = [Backspace, Ctrl+Z], CANCEL = [Esc] |
| 18 | `` `max regions is clamped`() `` | maxRegions 1 / 99 / 25 | 2 / 50 / 25 |
| 19 | `` `fill rgb falls back to default`() `` | fillColor `"red"` / `"#FFFFFF"` | `0x808080` / `0xFFFFFF` |
| 20 | `` `parse hex color`() `` | `"#ff00Aa"`, `"#GGG"`, `"808080"`, `"#8080800"`, `" #808080"` | `0xFF00AA`, null, null, null, null |
| 21 | `` `format hex color`() `` | `formatHexColor(0xFF00AA)`, `formatHexColor(0)` | `"#FF00AA"`, `"#000000"` |
| 22 | `` `key and withKey cover all slots`() `` | для всех 6 (action, slot): `withKey(a, s, "F5").key(a, s)` | `"F5"`; остальные 5 слотов не изменились |
| 23 | `` `invalid primary falls back to default`() `` | `copy(confirmKey = "???")` | `resolvedKey(CONFIRM, PRIMARY)` = Enter |
| 24 | `` `invalid or empty secondary is unassigned`() `` | `copy(cancelKeyAlt = "???")`; default `cancelKeyAlt` | оба `resolvedKey(CANCEL, SECONDARY)` = null; `keymap().keys(CANCEL)` = [Esc] |
| 25 | `` `default key per slot`() `` | `defaultKey(REMOVE_LAST, SECONDARY)`, `defaultKey(CANCEL, SECONDARY)` | `"Ctrl+Z"`, `""` |
| 26 | `` `settings round trip through repository`() `` | `@TempDir`; `SettingsRepository(file, { tempDir })`; save `AppSettings(defaultDirectory="C:\\s", multiRegion = MultiRegionSettings(maxRegions = 7, fillColor = "#FFFFFF", cancelKeyAlt = "Delete"))`; load | загруженное == сохранённому |
| 27 | `` `missing multiRegion object uses defaults`() `` | записать в файл `{"defaultDirectory":"C:\\s"}`; load | `multiRegion == MultiRegionSettings()` |

`DefaultDirectoryProvider` — fun interface (см. `SettingsControllerTest`: `DefaultDirectoryProvider { path }`).

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.settings.*"` | Все тесты пакета проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная проверка: Н/П.

## 13. Критерии готовности (Definition of Done)

- [ ] Шаги раздела 7 выполнены; сигнатуры совпадают с разделом 6.
- [ ] 27 тестов раздела 11 написаны и проходят; старые тесты не изменены и проходят.
- [ ] `settings` не импортирует `java.awt`.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не менять `HotkeyBinding`, `HotkeySettings`, `HotkeyAction`, `CaptureMode` (TASK-027).
- Не повышать `AppSettings.CURRENT_VERSION`.
- Не валидировать значения при загрузке и не переписывать файл — только «эффективные» геттеры раздела 6.
- Не добавлять UI.

## 15. Ловушки и подсказки

- `"NumPad Enter"` содержит пробел — разбор по `'+'` его не разбивает; не используй `split(' ')`.
- Буква `"z"` в нижнем регистре: `uppercase()` перед взятием кода, иначе получишь 0x7A (это F11!).
- `matches` не должен создавать `OverlayKey` из аргументов (конструктор бросает) — сравнивай поля.
- Порядок параметров `AppSettings` важен для позиционных вызовов в существующем коде — новое поле строго последним.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

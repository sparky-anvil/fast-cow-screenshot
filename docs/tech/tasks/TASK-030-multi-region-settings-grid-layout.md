# TASK-030: Переделать раскладку раздела «Несколько областей» в таблицу с общими столбцами

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | S (до 1 ч) |
| Технический дизайн | TDD-003 §10 |
| Требования | REQ-002.27, 2.28, 2.30; US-010 AC-1, AC-6 |
| ADR | ADR-001, ADR-005 |
| Зависит от | TASK-028 |
| Блокирует | TASK-029 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Исправить внешний вид раздела «Несколько областей» окна настроек: все строки раздела выстроить в таблицу с тремя
общими столбцами фиксированной ширины («Действие» | «Основная» | «Дополнительная») и расширить окно, чтобы строки
не переносились. Логика (`SettingsController`, `OverlayKeyRecorder`, обработка клавиш) не меняется.

## 2. Контекст

TASK-028 добавила раздел, но строка клавиши оверлея (подпись 200dp + 2 × (120dp + кнопка «Изменить») + «Очистить»
≈ 760dp) не помещается в окно шириной 600: кнопки переносятся и съезжают, строки лимита и цвета выровнены по-другому.
Пользователь при ручной проверке TASK-028 выбрал вариант «таблица в несколько столбцов» (журнал TASK-028).

Окно — Compose Desktop (Material, **не** Material3), файл `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsWindow.kt`,
функция `SettingsContent`. В ней уже есть локальные состояния (их не менять): `recordingAction` (глобальная клавиша,
`HotkeyAction?`), `hotkeyError`, `multiError`, `overlayTarget: Pair<OverlayAction, KeySlot>?`, `maxText`, `colorText`,
`anyRecording`. Обработчик `onPreviewKeyEvent` не меняется.

**Выдержки из требований (дословно):**

> **REQ-002.27** Система должна предоставлять в настройках раздел «Несколько областей» с параметрами: горячая клавиша запуска; клавиши «Собрать снимок», «Удалить последнюю область», «Отмена»; «Максимум областей» (целое 2–50, по умолчанию 10); «Цвет заливки» (выбор цвета и ввод HEX `#RRGGBB`, по умолчанию `#808080`).
>
> **REQ-002.28** … Каждое действие допускает основное и дополнительное сочетание.
>
> **REQ-002.30** Система должна предоставлять кнопку «Сбросить» для раздела, возвращающую значения по умолчанию.

## 3. Предусловия

- TASK-028 выполнена: в `SettingsWindow.kt` есть раздел «Несколько областей» (код от строки
  `Text("Несколько областей", fontWeight = FontWeight.Bold)` до `multiError?.let { Text(it, color = ERROR_COLOR) }`),
  private-функция `HotkeyRow`, `HEIGHT = 760`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsWindow.kt` | Изменить | `WIDTH`, `HEIGHT`, константы столбцов, `CellButton`, тело раздела «Несколько областей» |

**Файлы, которые изменять нельзя:** все остальные, в том числе `SettingsController.kt`, `OverlayKeyRecorder.kt`,
тесты. В `SettingsWindow.kt` не менять: класс `SettingsWindow` (кроме `WIDTH`/`HEIGHT`), обработчик
`onPreviewKeyEvent`, разделы «Директория по умолчанию», флажки, «Горячие клавиши», функцию `HotkeyRow`.

## 5. Зависимости

Новых зависимостей нет. Новые импорты (все из уже подключённого Compose):
`androidx.compose.foundation.layout.PaddingValues`, `androidx.compose.foundation.layout.Spacer`,
`androidx.compose.ui.Alignment`, `androidx.compose.ui.text.style.TextOverflow`.

## 6. Контракты

Добавить в `SettingsWindow.kt` рядом с `ERROR_COLOR` (private top-level):

```kotlin
private val COL_LABEL = 200.dp      // столбец «Действие»
private val COL_PRIMARY = 200.dp    // столбец «Основная»
private val COL_SECONDARY = 296.dp  // столбец «Дополнительная»
private val CELL_TEXT = 104.dp      // значение клавиши / поле ввода внутри ячейки
private val CELL_BUTTON = 96.dp     // кнопка внутри ячейки
private val SWATCH = 24.dp          // образец цвета
```

Новая private-функция в конце файла:

```kotlin
/** Кнопка ячейки таблицы фиксированной ширины CELL_BUTTON. filled = true — Button, иначе OutlinedButton. */
@Composable
private fun CellButton(text: String, enabled: Boolean = true, filled: Boolean = false, onClick: () -> Unit)
```

Эталонный фрагмент `CellButton`:

```kotlin
val modifier = Modifier.width(CELL_BUTTON)
val padding = PaddingValues(horizontal = 4.dp)
if (filled) {
    Button(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = padding) { Text(text, maxLines = 1) }
} else {
    OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, contentPadding = padding) { Text(text, maxLines = 1) }
}
```

`SettingsWindow.companion`: `WIDTH = 760`, `HEIGHT = 800`.

## 7. Пошаговая реализация

### Шаг 1. Размер окна и константы
- `WIDTH: Int = 760`, `HEIGHT: Int = 800`; константы раздела 6; функция `CellButton`; импорты раздела 5.
- **Результат:** `.\gradlew.bat compileKotlin` проходит (неиспользуемые константы допустимы до шага 2).

### Шаг 2. Тело раздела
- Заменить весь код от `Text("Несколько областей", fontWeight = FontWeight.Bold)` до
  `multiError?.let { Text(it, color = ERROR_COLOR) }` включительно на раскладку раздела 8. Строки «Горячие клавиши»
  выше и `hotkeyError?.let { … }` / `generalError?.let { … }` не трогать.
- **Результат:** `.\gradlew.bat build` проходит.

### Шаг 3. Ручная проверка — раздел 12.

## 8. Алгоритм и логика

Раскладка раздела сверху вниз. «Строка» = `Row(verticalAlignment = Alignment.CenterVertically)`. «Ячейка X» =
`Row(Modifier.width(X), verticalAlignment = Alignment.CenterVertically) { … }`. «Значение» =
`Text(<текст>, Modifier.width(CELL_TEXT), maxLines = 1, overflow = TextOverflow.Ellipsis)`.

1. `Text("Несколько областей", fontWeight = FontWeight.Bold)` (как было).
2. Строка-заголовок: `Text("Действие", Modifier.width(COL_LABEL), color = Color.Gray)`,
   `Text("Основная", Modifier.width(COL_PRIMARY), color = Color.Gray)`,
   `Text("Дополнительная", Modifier.width(COL_SECONDARY), color = Color.Gray)`.
3. Строка глобальной клавиши:
   - `Text("Горячая клавиша запуска", Modifier.width(COL_LABEL))`;
   - ячейка `COL_PRIMARY`: значение `s.hotkeyBinding(HotkeyAction.MULTI_REGION).format()`;
     `CellButton("Изменить", enabled = !anyRecording) { recordingAction = HotkeyAction.MULTI_REGION; hotkeyError = null; controller.beginHotkeyRecording() }`;
   - если `recordingAction == HotkeyAction.MULTI_REGION` —
     `Text("Нажмите сочетание… (Esc — отмена)", Modifier.width(COL_SECONDARY))`.
   Вызов `HotkeyRow(HotkeyAction.MULTI_REGION, …)` из раздела удалить (функция `HotkeyRow` остаётся для списка «Горячие клавиши»).
4. Для каждого `a in OverlayAction.entries` — строка:
   - `Text(a.displayName, Modifier.width(COL_LABEL))`;
   - ячейка `COL_PRIMARY`: значение `label(a, KeySlot.PRIMARY)`, затем `slotButton(a, KeySlot.PRIMARY)`;
   - ячейка `COL_SECONDARY`: значение `label(a, KeySlot.SECONDARY)`, затем `slotButton(a, KeySlot.SECONDARY)`, затем
     `CellButton("Очистить", enabled = !anyRecording && s.multiRegion.key(a, KeySlot.SECONDARY).isNotEmpty()) { multiError = controller.applyOverlayKey(a, KeySlot.SECONDARY, null) }`.

   Здесь (код писать прямо в цикле, отдельные функции не создавать):
   - `label(a, slot)` = `"Нажмите…"`, если `overlayTarget == a to slot`; иначе `s.multiRegion.key(a, slot).ifEmpty { "—" }`;
   - `slotButton(a, slot)` = если `overlayTarget == a to slot` → `CellButton("Отменить") { overlayTarget = null }`;
     иначе `CellButton("Изменить", enabled = !anyRecording) { multiError = null; overlayTarget = a to slot }`.
5. Строка лимита: `Text("Максимум областей (2–50)", Modifier.width(COL_LABEL))`; ячейка `COL_PRIMARY`:
   `OutlinedTextField(value = maxText, onValueChange = { maxText = it }, singleLine = true, modifier = Modifier.width(CELL_TEXT))`,
   `CellButton("Применить", filled = true) { multiError = controller.setMaxRegions(maxText) }`.
6. Строка цвета: `Text("Цвет заливки", Modifier.width(COL_LABEL))`; ячейка `COL_PRIMARY`:
   `OutlinedTextField(value = colorText, onValueChange = { colorText = it }, singleLine = true, modifier = Modifier.width(CELL_TEXT))`,
   `CellButton("Применить", filled = true) { multiError = controller.setFillColor(colorText) }`; ячейка `COL_SECONDARY`:
   - текущий цвет `Box(Modifier.size(SWATCH).background(Color(0xFF000000 or s.multiRegion.fillRgb().toLong())).border(2.dp, Color.Black))`;
   - `Spacer(Modifier.width(16.dp))`;
   - для каждого `hex in SettingsController.FILL_PALETTE`: `val rgb = MultiRegionSettings.parseHexColor(hex) ?: continue`,
     `Box(Modifier.padding(2.dp).size(SWATCH).background(Color(0xFF000000 or rgb.toLong())).border(1.dp, Color.Gray).clickable { multiError = controller.setFillColor(hex) })`.
7. Строка сброса: `Spacer(Modifier.width(COL_LABEL))`,
   `OutlinedButton(enabled = !anyRecording, onClick = { overlayTarget = null; multiError = controller.resetMultiRegion() }) { Text("Сбросить") }`.
8. `multiError?.let { Text(it, color = ERROR_COLOR) }`.

**Проверка ширины:** `COL_LABEL + COL_PRIMARY + COL_SECONDARY` = 200 + 200 + 296 = 696dp; + 32dp отступов `Column`
= 728dp ≤ `WIDTH − 16` = 744 (conventions §9). Ячейка «Основная»: 104 + 96 = 200 = `COL_PRIMARY`.
Ячейка «Дополнительная»: 104 + 96 + 96 = 296 = `COL_SECONDARY`. Палитра: 24 + 16 + 8 × (24 + 4) = 264 ≤ 296.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение |
|---|---|---|
| 1 | Длинное значение клавиши (`Ctrl+Alt+Shift+F12`) | обрезается многоточием в ячейке шириной `CELL_TEXT`, строка не переносится |
| 2 | Ошибка записи глобальной клавиши раздела | как раньше — `hotkeyError` выводится под списком «Горячие клавиши» |
| 3 | Масштаб Windows 150 % | размеры в dp масштабируются вместе с окном, раскладка та же |

## 10. Константы и тексты

| Что | Значение |
|---|---|
| `SettingsWindow.WIDTH` / `HEIGHT` | `760` / `800` |
| Столбцы | `200.dp` / `200.dp` / `296.dp`; `CELL_TEXT = 104.dp`, `CELL_BUTTON = 96.dp`, `SWATCH = 24.dp` |

Тексты UI (дословно), новые: `"Действие"`, `"Основная"`, `"Дополнительная"`, `"Горячая клавиша запуска"`, `"Нажмите…"`.
Остальные — без изменений: `"Несколько областей"`, `"Изменить"`, `"Отменить"`, `"Очистить"`, `"—"`,
`"Максимум областей (2–50)"`, `"Применить"`, `"Цвет заливки"`, `"Сбросить"`, `"Нажмите сочетание… (Esc — отмена)"`.
Текст `"Нажмите клавишу…"` (TASK-028) больше не используется.

## 11. Тесты

Н/П — меняется только раскладка Compose, логика покрыта тестами TASK-028; автотестов UI в проекте нет.
Существующие тесты должны проходить без изменений.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (`.\gradlew.bat run`, «Настройки» в трее; масштаб 100 % и, если есть, 150 %):
1. Окно открывается 760×800; раздел «Несколько областей» — таблица: заголовки «Действие | Основная | Дополнительная»
   над столбцами; ни одна строка раздела не переносится и не выходит за правый край.
2. Кнопки «Изменить» в столбцах «Основная» и «Дополнительная» стоят строго друг под другом во всех строках; поля
   лимита и цвета — под значениями клавиш столбца «Основная», кнопки «Применить» — под кнопками «Изменить».
3. «Изменить» у «Отмена» (основная) → значение «Нажмите…», кнопка «Отменить»; `Delete` → `Delete`.
4. «Изменить» у «Горячая клавиша запуска» → в столбце «Дополнительная» `"Нажмите сочетание… (Esc — отмена)"`; `Esc` → отмена.
5. Клик по белому образцу палитры → поле `#FFFFFF`, образец текущего цвета белый.
6. «Сбросить» → значения по умолчанию (Ctrl+Shift+4; Enter / NumPad Enter; Backspace / Ctrl+Z; Esc / —; 10; #808080).

Результаты записать в журнал.

## 13. Критерии готовности (Definition of Done)

- [x] Константы, `CellButton`, размеры окна — по разделу 6; раскладка — по разделу 8; тексты — по разделу 10.
- [x] Изменён только `SettingsWindow.kt`; логика и обработчики клавиш не изменены.
- [x] Ручная проверка 1–6 выполнена, результаты в журнале.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не менять `SettingsController`, `OverlayKeyRecorder`, обработчик `onPreviewKeyEvent`, состояния `remember`.
- Не менять раздел «Горячие клавиши» и функцию `HotkeyRow`.
- Не использовать `LazyColumn`, `LazyVerticalGrid`, Material3, `weight(...)` — только фиксированные ширины.
- Не уменьшать шрифты и не добавлять подсказки (tooltip).

## 15. Ловушки и подсказки

- `Button`/`OutlinedButton` Material по умолчанию имеют `contentPadding` 16dp по горизонтали и `minWidth` 64dp —
  без `contentPadding = PaddingValues(horizontal = 4.dp)` текст «Применить» в 96dp переносится.
- Без `verticalAlignment = Alignment.CenterVertically` поле ввода (высота 56dp) и кнопки (36dp) окажутся
  выровнены по верху.
- `Color(Long)` ожидает ARGB: `0xFF000000 or rgb.toLong()`.
- `continue` внутри `for` в `@Composable`-лямбде `Row` допустим (так же, как в TASK-028).

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-11-08 | Review | Раздел «Несколько областей» переделан в таблицу (столбцы 200/200/296dp, CellButton, окно 760×800). Только SettingsWindow.kt. build — BUILD SUCCESSFUL. Ручная проверка 1–6 выполнена пользователем: «Работает как описано». Примечание: на старте TASK-028 была в Review (не Done) — по указанию пользователя и записи Tech в журнале TASK-028 задача взята в работу. Отклонений нет. |

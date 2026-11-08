# TASK-028: Добавить раздел настроек «Несколько областей»

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-003 §3, §10 |
| Требования | REQ-002.2, 2.27–2.31; US-010 AC-1–AC-6 |
| ADR | ADR-001, ADR-005 |
| Зависит от | TASK-027 |
| Блокирует | TASK-029 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

В окне настроек (Compose) добавить раздел «Несколько областей»: глобальная клавиша запуска, клавиши оверлея
(основная и дополнительная для трёх действий), «Максимум областей», «Цвет заливки» (палитра + HEX), кнопка «Сбросить».
Логику изменений — в `SettingsController` (unit-тесты), запись клавиш оверлея — чистая функция `OverlayKeyRecorder`.

## 2. Контекст

Окно настроек (`ui/SettingsWindow.kt`, функция `SettingsContent`) применяет каждое изменение сразу через
`SettingsController` и показывает текст ошибки красным под разделом; «Сохранить» отдельной кнопкой нет. Так же
работает новый раздел: «при сохранении» в US-010 означает момент применения изменения; при ошибке значение
не меняется.

Глобальная клавиша `MULTI_REGION` уже есть в `HotkeyAction.entries` (TASK-027) и сейчас показывается в общем списке
«Горячие клавиши». Её строка переезжает в новый раздел; запись и проверка конфликтов — существующие
`beginHotkeyRecording` / `applyHotkey` (сообщение `"Сочетание %s уже используется для режима «%s»"` — это US-010 AC-4).

Клавиши оверлея — `OverlayKey` (TASK-024): могут быть без модификаторов, поэтому `Esc` при записи — обычная клавиша,
а запись отменяется кнопкой «Отменить». Глобальные клавиши при записи клавиши оверлея **не** снимаются.

**Выдержки из требований (дословно):**

> **REQ-002.27** Система должна предоставлять в настройках раздел «Несколько областей» с параметрами: горячая клавиша запуска; клавиши «Собрать снимок», «Удалить последнюю область», «Отмена»; «Максимум областей» (целое 2–50, по умолчанию 10); «Цвет заливки» (выбор цвета и ввод HEX `#RRGGBB`, по умолчанию `#808080`). Значения сохраняются между перезапусками.
>
> **REQ-002.28** … Каждое действие допускает основное и дополнительное сочетание.
>
> **REQ-002.29** Система должна запрещать назначать одно сочетание двум действиям оверлея и сообщать о конфликте при сохранении. Для глобальной клавиши запуска — также конфликт с клавишами режимов REQ-001.52.
>
> **REQ-002.30** Система должна предоставлять кнопку «Сбросить» для раздела, возвращающую значения по умолчанию.
>
> **REQ-002.31** Изменение лимита не влияет на уже открытый оверлей; применяется со следующего запуска режима.
>
> **US-010 AC-3** Given пользователь назначает `Enter` и на «Собрать», и на «Отмена» When сохраняет настройки Then сохранение отклоняется с сообщением о конфликте.
>
> **US-010 AC-5** Given пользователь вводит лимит 0 / 51 / «abc» или цвет `#GGG` When сохраняет Then значение отклоняется с подсказкой о допустимом диапазоне (2–50, `#RRGGBB`).
>
> **US-010 AC-6** Given параметры изменены When пользователь нажимает «Сбросить» в разделе Then восстановлены значения AC-1.

## 3. Предусловия

- TASK-024: `OverlayKey`, `OverlayAction`, `KeySlot`, `MultiRegionSettings` (`key`, `withKey`, `resolvedKey`,
  `parseHexColor`, `formatHexColor`, `MIN_REGIONS`, `MAX_REGIONS`), `AppSettings.multiRegion`.
- TASK-027: `HotkeyAction.MULTI_REGION`, `HotkeyBinding.defaultFor(MULTI_REGION)` = `Ctrl+Shift+4`.
- Существуют: `ui/SettingsController.kt` (`applyHotkey`, `registerAll`, `ERR_SAVE`), `ui/SettingsWindow.kt`,
  `ui/HotkeyRecorder.kt` (`isModifierKey(awtKeyCode)`), `ui/RecordingStep.kt` (образец sealed-иерархии),
  `src/test/.../ui/SettingsControllerTest.kt` (фикстура `controller()`, `store`, мок `hotkeys`).
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/OverlayRecordingStep.kt` | Создать | sealed `OverlayRecordingStep` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/OverlayKeyRecorder.kt` | Создать | `OverlayKeyRecorder` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsController.kt` | Изменить | 4 метода, константы |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsWindow.kt` | Изменить | раздел, `HEIGHT = 760` |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/OverlayKeyRecorderTest.kt` | Создать | тесты 1–5 |
| 6 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsControllerTest.kt` | Изменить | тесты 6–20 |

**Файлы, которые изменять нельзя:** все остальные.

## 5. Зависимости

Новых зависимостей нет (`androidx.compose.foundation.*`, `androidx.compose.material.*` уже подключены).

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/** Решение по нажатию при записи клавиши оверлея. */
internal sealed interface OverlayRecordingStep {
    /** Нажат только модификатор — ждать. */
    data object Wait : OverlayRecordingStep
    /** Клавиша не поддерживается. */
    data object Invalid : OverlayRecordingStep
    /** Записать клавишу. */
    data class Apply(val key: OverlayKey) : OverlayRecordingStep
}

/** Преобразование нажатия AWT в OverlayKey. Без состояния. */
internal object OverlayKeyRecorder {
    /**
     * Порядок: HotkeyRecorder.isModifierKey(awtKeyCode) → Wait; !OverlayKey.isSupportedKey(awtKeyCode) → Invalid;
     * иначе Apply(OverlayKey(ctrl, alt, shift, awtKeyCode, numpad && awtKeyCode == OverlayKey.VK_ENTER)). Не бросает.
     */
    fun onKeyDown(ctrl: Boolean, alt: Boolean, shift: Boolean, awtKeyCode: Int, numpad: Boolean): OverlayRecordingStep = TODO()
}
```

Добавить в `SettingsController` (существующие методы не менять):

```kotlin
    /**
     * Назначает клавишу оверлея. key == null — очистить слот (только SECONDARY).
     * @return null — успех; ERR_PRIMARY_REQUIRED; ERR_OVERLAY_DUPLICATE_FORMAT; ERR_SAVE.
     */
    fun applyOverlayKey(action: OverlayAction, slot: KeySlot, key: OverlayKey?): String?

    /** text.trim() → Int в MIN_REGIONS..MAX_REGIONS → сохранить. Иначе ERR_MAX_REGIONS. null — успех; ERR_SAVE. */
    fun setMaxRegions(text: String): String?

    /** parseHexColor(text.trim()) → сохранить formatHexColor(rgb). Иначе ERR_FILL_COLOR. null — успех; ERR_SAVE. */
    fun setFillColor(text: String): String?

    /** multiRegion = MultiRegionSettings(); глобальная клавиша MULTI_REGION = Ctrl+Shift+4. Первая ошибка или null. */
    fun resetMultiRegion(): String?
```

Константы в `companion object` `SettingsController`:

```kotlin
        const val ERR_PRIMARY_REQUIRED: String = "Основное сочетание обязательно"
        const val ERR_OVERLAY_DUPLICATE_FORMAT: String = "Сочетание %s уже назначено действию «%s»"
        const val ERR_OVERLAY_INVALID: String =
            "Клавиша не поддерживается. Допустимы A–Z, 0–9, F1–F12, Enter, NumPad Enter, Backspace, Esc, Delete, Space"
        const val ERR_MAX_REGIONS: String = "Максимум областей — целое число от 2 до 50"
        const val ERR_FILL_COLOR: String = "Цвет — в формате #RRGGBB, например #808080"
        val FILL_PALETTE: List<String> =
            listOf("#808080", "#FFFFFF", "#000000", "#C0C0C0", "#FF00FF", "#2E86DE", "#27AE60", "#F1C40F")
```

## 7. Пошаговая реализация

### Шаг 1. `OverlayRecordingStep.kt`, `OverlayKeyRecorder.kt` + тесты 1–5
- **Результат:** `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.OverlayKeyRecorderTest"` проходит.

### Шаг 2. `SettingsController` — методы раздела 8 + тесты 6–20
- **Результат:** `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.SettingsControllerTest"` проходит.

### Шаг 3. `SettingsWindow` — вынести строку горячей клавиши
- Тело цикла `for (action in HotkeyAction.entries) { Row { … } }` вынести в
  `@Composable private fun HotkeyRow(action: HotkeyAction, s: AppSettings, recording: MutableState<HotkeyAction?>, enabled: Boolean, onStart: () -> Unit)`
  без изменения внешнего вида; кнопка «Изменить» получает `enabled = enabled`; `onClick` = `onStart()`.
- Цикл: `for (action in HotkeyAction.entries.filter { it != HotkeyAction.MULTI_REGION })`.
- `onStart` = прежнее тело `onClick` (`recordingAction = action; hotkeyError = null; controller.beginHotkeyRecording()`).
- **Результат:** компиляция; внешний вид общего списка не изменился, в нём 3 строки.

### Шаг 4. `SettingsWindow` — состояние раздела
В `SettingsContent` добавить:
```kotlin
var multiError by remember { mutableStateOf<String?>(null) }
var overlayTarget by remember { mutableStateOf<Pair<OverlayAction, KeySlot>?>(null) }
var maxText by remember(s.multiRegion.maxRegions) { mutableStateOf(s.multiRegion.maxRegions.toString()) }
var colorText by remember(s.multiRegion.fillColor) { mutableStateOf(s.multiRegion.fillColor) }
val anyRecording = recordingAction != null || overlayTarget != null
```
`LaunchedEffect(recordingAction, overlayTarget) { if (recordingAction != null || overlayTarget != null) focusRequester.requestFocus() }`
(заменяет существующий `LaunchedEffect(recordingAction)`).

### Шаг 5. `SettingsWindow` — обработка клавиш
В начале лямбды `onPreviewKeyEvent`, до существующего кода:
```kotlin
val target = overlayTarget
if (target != null) {
    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
    when (val step = OverlayKeyRecorder.onKeyDown(
        e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.key.nativeKeyCode, e.key == Key.NumPadEnter,
    )) {
        OverlayRecordingStep.Wait -> return@onPreviewKeyEvent true
        OverlayRecordingStep.Invalid -> multiError = SettingsController.ERR_OVERLAY_INVALID
        is OverlayRecordingStep.Apply -> multiError = controller.applyOverlayKey(target.first, target.second, step.key)
    }
    overlayTarget = null
    return@onPreviewKeyEvent true
}
```

### Шаг 6. `SettingsWindow` — раздел
После `generalError?.let { … }` в `Column` — элементы раздела 10 «Раскладка раздела» сверху вниз.
`HEIGHT` = `760`.
- **Результат:** `.\gradlew.bat build` проходит; ручная проверка раздела 12.

## 8. Алгоритм и логика

```
applyOverlayKey(action, slot, key):
  m = store.current.multiRegion
  if (key == null):
      if (slot == PRIMARY) return ERR_PRIMARY_REQUIRED
  else:
      для a in OverlayAction.entries, sl in KeySlot.entries, (a, sl) != (action, slot):
          if (m.resolvedKey(a, sl) == key) return ERR_OVERLAY_DUPLICATE_FORMAT.format(key.format(), a.displayName)
  text = key?.format() ?: ""
  return if (store.update { it.copy(multiRegion = it.multiRegion.withKey(action, slot, text)) }) null else ERR_SAVE

setMaxRegions(text):
  n = text.trim().toIntOrNull()
  if (n == null || n !in MultiRegionSettings.MIN_REGIONS..MultiRegionSettings.MAX_REGIONS) return ERR_MAX_REGIONS
  return if (store.update { it.copy(multiRegion = it.multiRegion.copy(maxRegions = n)) }) null else ERR_SAVE

setFillColor(text):
  rgb = MultiRegionSettings.parseHexColor(text.trim()) ?: return ERR_FILL_COLOR
  hex = MultiRegionSettings.formatHexColor(rgb)
  return if (store.update { it.copy(multiRegion = it.multiRegion.copy(fillColor = hex)) }) null else ERR_SAVE

resetMultiRegion():
  saved = store.update { it.copy(multiRegion = MultiRegionSettings()) }
  def = HotkeyBinding.defaultFor(HotkeyAction.MULTI_REGION)
  hotkeyErr = if (store.current.hotkeyBinding(HotkeyAction.MULTI_REGION) != def) applyHotkey(HotkeyAction.MULTI_REGION, def) else null
  return hotkeyErr ?: (if (saved) null else ERR_SAVE)
```

**Пример (US-010 AC-3):** по умолчанию CONFIRM/PRIMARY = Enter. `applyOverlayKey(CANCEL, PRIMARY, Enter)` →
находит `(CONFIRM, PRIMARY)` → `"Сочетание Enter уже назначено действию «Собрать снимок»"`; `cancelKey` остаётся `"Esc"`.

Повторное назначение той же клавиши в тот же слот (`applyOverlayKey(CONFIRM, PRIMARY, Enter)`) — не конфликт, успех.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Текст под разделом |
|---|---|---|---|
| 1 | Клавиша оверлея уже у другого действия/слота | не сохранять | `"Сочетание Enter уже назначено действию «Собрать снимок»"` |
| 2 | Очистка основного слота | не сохранять | `"Основное сочетание обязательно"` |
| 3 | Неподдерживаемая клавиша при записи (Tab, стрелки, NumPad 0) | запись завершена, ничего не сохранено | `ERR_OVERLAY_INVALID` |
| 4 | Лимит `0`, `51`, `abc`, пусто | не сохранять | `"Максимум областей — целое число от 2 до 50"` |
| 5 | Цвет `#GGG`, `808080`, `#80808` | не сохранять | `"Цвет — в формате #RRGGBB, например #808080"` |
| 6 | Глобальная клавиша `Ctrl+Shift+3` для «Несколько областей» | существующий `applyHotkey` | `"Сочетание Ctrl+Shift+3 уже используется для режима «Область»"` |
| 7 | Глобальная клавиша занята другим приложением | существующий `applyHotkey` | `"Сочетание … занято другим приложением"` |
| 8 | Ошибка записи файла | значение в памяти обновлено (`SettingsStore`) | `"Не удалось сохранить настройки"` |
| 9 | Сброс, когда `Ctrl+Shift+4` у другого режима | раздел сброшен, клавиша не изменена | `"Сочетание Ctrl+Shift+4 уже используется для режима «…»"` |
| 10 | Окно закрыто во время записи клавиши оверлея | состояние Compose теряется, ничего не сохранено | — |

Логирование в новых методах не требуется (`SettingsStore` логирует ошибки записи сам).

## 10. Константы и тексты

**Раскладка раздела** (Compose, сверху вниз; ширины — как у существующих строк):

1. `Text("Несколько областей", fontWeight = FontWeight.Bold)`.
2. `HotkeyRow(HotkeyAction.MULTI_REGION, s, recording, enabled = !anyRecording, onStart = { recordingAction = HotkeyAction.MULTI_REGION; hotkeyError = null; controller.beginHotkeyRecording() })`.
   Ошибки этой строки выводит существующий `hotkeyError?.let { … }` на прежнем месте; новый вывод не добавлять.
3. Для каждого `a in OverlayAction.entries` — `Row`:
   - `Text(a.displayName, Modifier.width(200.dp))`;
   - для `slot in KeySlot.entries`: `Text(label, Modifier.width(120.dp))`, где `label` =
     `"Нажмите клавишу…"`, если `overlayTarget == a to slot`; иначе `s.multiRegion.key(a, slot)` или `"—"`, если пусто;
     затем кнопка: если запись этого слота идёт — `OutlinedButton(onClick = { overlayTarget = null }) { Text("Отменить") }`;
     иначе `OutlinedButton(enabled = !anyRecording, onClick = { multiError = null; overlayTarget = a to slot }) { Text("Изменить") }`;
   - после слота `SECONDARY` — `OutlinedButton(enabled = !anyRecording && s.multiRegion.key(a, KeySlot.SECONDARY).isNotEmpty(), onClick = { multiError = controller.applyOverlayKey(a, KeySlot.SECONDARY, null) }) { Text("Очистить") }`.
4. `Row`: `Text("Максимум областей (2–50)", Modifier.width(200.dp))`,
   `OutlinedTextField(value = maxText, onValueChange = { maxText = it }, singleLine = true, modifier = Modifier.width(100.dp))`,
   `Button(onClick = { multiError = controller.setMaxRegions(maxText) }) { Text("Применить") }`.
5. `Row`: `Text("Цвет заливки", Modifier.width(200.dp))`, образец текущего цвета
   `Box(Modifier.size(24.dp).background(Color(0xFF000000 or s.multiRegion.fillRgb().toLong())).border(1.dp, Color.Gray))`,
   `OutlinedTextField(value = colorText, onValueChange = { colorText = it }, singleLine = true, modifier = Modifier.width(120.dp))`,
   `Button(onClick = { multiError = controller.setFillColor(colorText) }) { Text("Применить") }`.
6. `Row`: для каждого `hex in SettingsController.FILL_PALETTE`: `val rgb = MultiRegionSettings.parseHexColor(hex) ?: continue`, затем
   `Box(Modifier.padding(2.dp).size(24.dp).background(Color(0xFF000000 or rgb.toLong())).border(1.dp, Color.Gray).clickable { multiError = controller.setFillColor(hex) })`.
7. `OutlinedButton(enabled = !anyRecording, onClick = { overlayTarget = null; multiError = controller.resetMultiRegion() }) { Text("Сбросить") }`.
8. `multiError?.let { Text(it, color = ERROR_COLOR) }`.

Импорты Compose: `androidx.compose.foundation.background`, `androidx.compose.foundation.border`,
`androidx.compose.foundation.clickable`, `androidx.compose.foundation.layout.size`, `androidx.compose.ui.input.key.Key`.

Тексты UI (дословно): `"Несколько областей"`, `"Нажмите клавишу…"`, `"—"`, `"Изменить"`, `"Отменить"`, `"Очистить"`,
`"Максимум областей (2–50)"`, `"Применить"`, `"Цвет заливки"`, `"Сбросить"`; ошибки — раздел 6.

## 11. Тесты

`OverlayKeyRecorderTest.kt`:

| # | Метод | When | Then |
|---|---|---|---|
| 1 | `` `modifier key waits`() `` | `onKeyDown(true,false,false, KeyEvent.VK_CONTROL, false)` | `Wait` |
| 2 | `` `escape is a regular key`() `` | `onKeyDown(false,false,false, 27, false)` | `Apply(OverlayKey(false,false,false,27))` |
| 3 | `` `numpad enter is recorded`() `` | `onKeyDown(false,false,false, 10, true)` | `Apply(OverlayKey(false,false,false,10,true))` |
| 4 | `` `ctrl z is recorded`() `` | `onKeyDown(true,false,false, 0x5A, false)` | `Apply(OverlayKey(true,false,false,0x5A))` |
| 5 | `` `unsupported key is invalid`() `` | `onKeyDown(false,false,false, KeyEvent.VK_TAB, false)`; `(…, KeyEvent.VK_NUMPAD0, true)` | `Invalid`, `Invalid` |

`SettingsControllerTest.kt` (существующая фикстура; `enter` = `OverlayKey(false,false,false,10)`):

| # | Метод | Given / When | Then | Покрывает |
|---|---|---|---|---|
| 6 | `` `overlay key conflict is rejected`() `` | `applyOverlayKey(CANCEL, PRIMARY, enter)` | `"Сочетание Enter уже назначено действию «Собрать снимок»"`; `store.current.multiRegion.cancelKey == "Esc"` | AC-3 |
| 7 | `` `overlay key conflict with secondary slot`() `` | `applyOverlayKey(CANCEL, SECONDARY, Ctrl+Z)` | `"Сочетание Ctrl+Z уже назначено действию «Удалить последнюю область»"` | 2.29 |
| 8 | `` `overlay key is saved`() `` | `applyOverlayKey(CANCEL, SECONDARY, OverlayKey(false,false,false,0x51))` | null; `cancelKeyAlt == "Q"`; после нового `SettingsStore` на том же файле — `"Q"` | AC-2 |
| 9 | `` `same key in same slot is not a conflict`() `` | `applyOverlayKey(CONFIRM, PRIMARY, enter)` | null | §8 |
| 10 | `` `swapping keys is possible via free key`() `` | CANCEL/PRIMARY ← F5; CONFIRM/PRIMARY ← Esc; CANCEL/PRIMARY ← Enter | все null; confirmKey `"Esc"`, cancelKey `"Enter"` | REQ-002 §6 |
| 11 | `` `secondary can be cleared`() `` | `applyOverlayKey(REMOVE_LAST, SECONDARY, null)` | null; `removeLastKeyAlt == ""` | 2.28 |
| 12 | `` `primary cannot be cleared`() `` | `applyOverlayKey(CONFIRM, PRIMARY, null)` | `"Основное сочетание обязательно"`; confirmKey `"Enter"` | 2.28 |
| 13 | `` `max regions validation`() `` | `setMaxRegions("0")`, `("51")`, `("abc")`, `("")` | все `"Максимум областей — целое число от 2 до 50"`; maxRegions 10 | AC-5 |
| 14 | `` `max regions saved`() `` | `setMaxRegions(" 25 ")` | null; maxRegions 25 | 2.27 |
| 15 | `` `fill color validation`() `` | `setFillColor("#GGG")`, `("808080")` | `"Цвет — в формате #RRGGBB, например #808080"`; fillColor `"#808080"` | AC-5 |
| 16 | `` `fill color normalized to upper case`() `` | `setFillColor("#ff00aa")` | null; fillColor `"#FF00AA"` | 2.27 |
| 17 | `` `multi region hotkey conflicts with region mode`() `` | `applyHotkey(MULTI_REGION, HotkeyBinding(true,false,true,'3'.code))` | `"Сочетание Ctrl+Shift+3 уже используется для режима «Область»"` | AC-4 |
| 18 | `` `reset restores defaults`() `` | изменить maxRegions 25, fillColor `#FFFFFF`, cancelKey F5, глобальную `MULTI_REGION` → `Ctrl+Alt+M`; `resetMultiRegion()` | null; `multiRegion == MultiRegionSettings()`; `hotkeyBinding(MULTI_REGION).format() == "Ctrl+Shift+4"`; `verify { hotkeys.register(MULTI_REGION, defaultFor(MULTI_REGION)) }` | AC-6 |
| 19 | `` `reset keeps hotkey when default is taken`() `` | `every { hotkeys.register(HotkeyAction.MULTI_REGION, HotkeyBinding.defaultFor(HotkeyAction.MULTI_REGION)) } returns false`; глобальная `MULTI_REGION` = `Ctrl+Alt+M`; reset | `"Сочетание Ctrl+Shift+4 занято другим приложением"`; `multiRegion == MultiRegionSettings()` | §9 №9 |
| 20 | `` `register all includes multi region`() `` | `registerAll()` | `verify { hotkeys.register(HotkeyAction.MULTI_REGION, any()) }` | 2.1 |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.*"` | Все тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (`.\gradlew.bat run`, «Настройки» в трее):
1. Удалить `%APPDATA%\fast-cow-screenshot\settings.json`, запустить → раздел «Несколько областей»: `Ctrl+Shift+4`;
   «Собрать снимок» `Enter` / `NumPad Enter`; «Удалить последнюю область» `Backspace` / `Ctrl+Z`; «Отмена» `Esc` / `—`;
   лимит `10`; цвет `#808080`, образец серый (US-010 AC-1). В общем списке «Горячие клавиши» — 3 строки.
2. «Отмена» → «Изменить» → `Enter` → красный текст конфликта; значение `Esc` (AC-3).
3. «Отмена», дополнительное → «Изменить» → `Delete` → `Delete`. «Очистить» → `—`.
4. Лимит `abc` → «Применить» → ошибка; `3` → «Применить» → без ошибки.
5. Цвет `#GGG` → ошибка; клик по белому образцу палитры → поле и образец `#FFFFFF`.
6. Запуск режима → «Изменить» → `Ctrl+Shift+3` → `"Сочетание Ctrl+Shift+3 уже используется для режима «Область»"` (AC-4).
7. Закрыть и перезапустить приложение → значения шагов 3–5 сохранились (AC-2).
8. `Ctrl+Shift+4`: при лимите 3 четвёртое выделение не начинается, подсказка `"Достигнут лимит: 3 областей. Enter — собрать снимок"`;
   `Delete` (дополнительная «Отмена») закрывает оверлей; склейка «по диагонали» — пустоты белые.
9. «Сбросить» → значения шага 1 (AC-6).

## 13. Критерии готовности (Definition of Done)

- [x] Сигнатуры и константы совпадают с разделом 6; тексты — с разделом 10.
- [x] Тесты 1–20 написаны и проходят; старые тесты проходят.
- [ ] Ручная проверка 1–9 выполнена, результаты в журнале. (выполнена, но есть замечание по раскладке — см. журнал)
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не менять `HotkeyRecorder.kt`, `RecordingStep.kt`, `applyHotkey`, `registerAll`.
- Не снимать глобальные клавиши при записи клавиши оверлея.
- Не добавлять кнопку «Сохранить» и диалоги выбора цвета (`JColorChooser` запрещён ADR-005).
- Не использовать Material3.

## 15. Ловушки и подсказки

- `KeyEvent.nativeKeyEvent` не приводить к AWT (conventions §9, CLR-003); `NumPad Enter` — `e.key == Key.NumPadEnter`.
- Возвращай `true` из `onPreviewKeyEvent` при записи клавиши оверлея, иначе `Enter`/`Space` нажмёт сфокусированную кнопку.
- `remember(s.multiRegion.maxRegions) { … }` — ключ нужен, чтобы поле обновилось после «Сбросить».
- `Color(Long)` в Compose ожидает ARGB: `0xFF000000 or rgb.toLong()`, иначе цвет будет прозрачным.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-11-08 | Review | Реализованы OverlayRecordingStep, OverlayKeyRecorder, 4 метода и константы SettingsController, раздел «Несколько областей» и HotkeyRow в SettingsWindow (HEIGHT=760), тесты 1–20. build — BUILD SUCCESSFUL (OverlayKeyRecorderTest 5, SettingsControllerTest 25). Ручная проверка: пользователь прошёл шаги 1–9, замечание по раскладке (не функциональное): расположение новых кнопок неудачно — нужно либо всё в один столбец с более широким окном, либо везде несколько столбцов. Раскладка задана разделом 10 задачи, поэтому изменение не вносилось; предложение к Tech: уточнить раскладку/ширину окна (отдельная задача или правка TASK-028). |
| 2026-11-08 | Review | Tech: замечание по раскладке принято; пользователь выбрал «таблица в несколько столбцов». Исправление вынесено в TASK-030 (только SettingsWindow.kt). Функциональность TASK-028 принимается как есть; пункт DoD о ручной проверке закрывается после TASK-030. |

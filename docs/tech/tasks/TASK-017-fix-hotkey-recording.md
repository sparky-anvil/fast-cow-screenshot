# TASK-017: Исправить запись сочетания клавиш в окне настроек (CLR-003)

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | S (до 1 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.4; US-007 (окно настроек) |
| ADR | ADR-001 |
| Зависит от | TASK-014 |
| Блокирует | TASK-015 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Кнопка «Изменить» в окне настроек должна записывать новое сочетание, а Esc — отменять запись. Сейчас обработчик
падает с `ClassCastException` (CLR-003). Кроме того, после любой отмены записи глобальные клавиши должны быть
зарегистрированы заново, в том числе если окно закрыли крестиком во время записи.

## 2. Контекст

- Сейчас `ui/SettingsWindow.kt`, строка 107: `val k = e.nativeKeyEvent as KeyEvent`. В Compose 1.12.2 `nativeKeyEvent` —
  это `androidx.compose.ui.input.key.InternalKeyEvent`, а не `java.awt.event.KeyEvent`, поэтому приведение падает.
  Исключение уходит в обработчик EDT. `beginHotkeyRecording()` уже снял все глобальные клавиши, а `registerAll()` не
  вызывается, поэтому горячие клавиши перестают работать.
- Правильный способ — публичный API Compose без приведений:
  - `e.key.nativeKeyCode` (`androidx.compose.ui.input.key.nativeKeyCode`) — AWT-код `java.awt.event.KeyEvent.VK_*`
    (проверено по исходнику `Key.desktop.kt`: `Key(nativeKeyCode)` упаковывает именно AWT VK-код);
  - `e.isCtrlPressed`, `e.isAltPressed`, `e.isShiftPressed` (`androidx.compose.ui.input.key.*`).
- Решение «что делать с нажатием» выносится в чистую функцию `HotkeyRecorder.onKeyDown`, она покрывается unit-тестами.
  Composable только вызывает её и выполняет результат.
- Состояние `recording` переносится из `SettingsContent` в `SettingsWindow`. Так окно при закрытии может отменить запись.

**Выдержки из требований (дословно):**

> **REQ-001.4** Система должна позволять переназначить горячую клавишу каждого режима в настройках. Новое значение должно сохраняться между перезапусками.

## 3. Предусловия

- TASK-014 выполнена: существуют `ui/HotkeyRecorder.kt`, `ui/SettingsController.kt`, `ui/SettingsWindow.kt`,
  `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/HotkeyRecorderTest.kt`.
- `.\gradlew.bat build` зелёный до начала работы.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/RecordingStep.kt` | Создать | sealed interface |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/HotkeyRecorder.kt` | Изменить | добавить `onKeyDown` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/SettingsWindow.kt` | Изменить | обработчик клавиш, состояние `recording`, слушатель закрытия |
| 4 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/HotkeyRecorderTest.kt` | Изменить | добавить 5 тестов |

**Файлы, которые изменять нельзя:** все, кроме перечисленных. В частности — `SettingsController.kt` и
`app/FastCowScreenshotApp.kt`. Если файлы TASK-015 не закоммичены, их не трогать.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

/** Что сделать с нажатием клавиши во время записи сочетания. */
internal sealed interface RecordingStep {
    /** Esc: отменить запись. */
    data object Cancel : RecordingStep
    /** Нажат только модификатор: ждать следующую клавишу. */
    data object Wait : RecordingStep
    /** Сочетание недопустимо: показать ERR_INVALID_COMBO и отменить запись. */
    data object Invalid : RecordingStep
    /** Применить сочетание. */
    data class Apply(val binding: HotkeyBinding) : RecordingStep
}
```

```kotlin
// Добавить в internal object HotkeyRecorder (существующие функции не менять):
/**
 * Решение по нажатию во время записи. awtKeyCode — java.awt.event.KeyEvent.VK_*. Не бросает.
 * Порядок проверок: VK_ESCAPE → Cancel; isModifierKey → Wait; toBinding == null → Invalid; иначе Apply.
 */
fun onKeyDown(ctrl: Boolean, alt: Boolean, shift: Boolean, awtKeyCode: Int): RecordingStep
```

```kotlin
// SettingsWindow.kt — новая сигнатура composable (было: SettingsContent(s, controller)):
@Composable
internal fun SettingsContent(
    s: AppSettings,
    controller: SettingsController,
    recording: androidx.compose.runtime.MutableState<HotkeyAction?>,
)
```

Публичный контракт `SettingsWindow` (`show()`, `dispose()`, константы) не меняется.

## 7. Пошаговая реализация

1. Создать `RecordingStep.kt` по разделу 6 (`package by.sparkyanvil.fastcowscreenshot.ui`, импорт
   `by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding`). → `.\gradlew.bat compileKotlin` зелёный.
2. В `HotkeyRecorder` добавить `onKeyDown` (раздел 8.1). Добавить тесты раздела 11. →
   `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.HotkeyRecorderTest"`: 10 тестов.
3. В `SettingsWindow.kt` выполнить изменения раздела 8.2. → `.\gradlew.bat build` зелёный.
4. Ручная проверка раздела 12.

## 8. Алгоритм и логика

### 8.1. HotkeyRecorder.onKeyDown

```
if (awtKeyCode == KeyEvent.VK_ESCAPE) return RecordingStep.Cancel
if (isModifierKey(awtKeyCode)) return RecordingStep.Wait
val b = toBinding(ctrl, alt, shift, awtKeyCode) ?: return RecordingStep.Invalid
return RecordingStep.Apply(b)
```

### 8.2. Изменения SettingsWindow.kt

1. В классе `SettingsWindow` добавить поле
   `private val recording = mutableStateOf<HotkeyAction?>(null)`.
2. В `show()`, внутри `ComposeWindow().apply { … }`:
   - `setContent { SettingsContent(settingsState.value, controller, recording) }`;
   - после `defaultCloseOperation = …` добавить
     `addWindowListener(object : java.awt.event.WindowAdapter() { override fun windowClosing(e: java.awt.event.WindowEvent) { cancelRecording() } })`.
3. Добавить в класс приватный метод:
   ```
   private fun cancelRecording() {
       if (recording.value != null) { controller.cancelHotkeyRecording(); recording.value = null }
   }
   ```
   В `dispose()` первой строкой вызвать `cancelRecording()`.
4. В `SettingsContent` удалить строку `var recording by remember { mutableStateOf<HotkeyAction?>(null) }`. Вместо неё:
   `var recordingAction by recording` (делегат `MutableState`; импорты `getValue`/`setValue` уже есть).
   Все прежние обращения к `recording` внутри `SettingsContent` заменить на `recordingAction`
   (в `LaunchedEffect(recordingAction)`, в тексте строки клавиши, в `onClick` кнопки «Изменить»).
5. Тело `onPreviewKeyEvent` заменить на (Эталонный фрагмент):
   ```kotlin
   .onPreviewKeyEvent { e ->
       val action = recordingAction
       if (action == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
       when (val step = HotkeyRecorder.onKeyDown(e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.key.nativeKeyCode)) {
           RecordingStep.Wait -> return@onPreviewKeyEvent true
           RecordingStep.Cancel -> controller.cancelHotkeyRecording()
           RecordingStep.Invalid -> {
               hotkeyError = SettingsController.ERR_INVALID_COMBO
               controller.cancelHotkeyRecording()
           }
           is RecordingStep.Apply -> hotkeyError = controller.applyHotkey(action, step.binding)
       }
       recordingAction = null
       true
   }
   ```
6. Импорты: удалить `import java.awt.event.KeyEvent`. Добавить `androidx.compose.ui.input.key.key`,
   `androidx.compose.ui.input.key.nativeKeyCode`, `androidx.compose.ui.input.key.isCtrlPressed`,
   `androidx.compose.ui.input.key.isAltPressed`, `androidx.compose.ui.input.key.isShiftPressed`,
   `androidx.compose.runtime.MutableState`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | Esc во время записи | `cancelHotkeyRecording()` → клавиши зарегистрированы заново | — | строка снова показывает прежнее сочетание |
| 2 | Нажат только Ctrl/Shift/Alt/Win | ждать следующую клавишу | — | текст «Нажмите сочетание… (Esc — отмена)» остаётся |
| 3 | `Shift+A`, `Ctrl+Enter` | `cancelHotkeyRecording()` | — | `ERR_INVALID_COMBO` красным |
| 4 | Окно закрыто крестиком во время записи | `cancelHotkeyRecording()`, `recording = null` | — | горячие клавиши работают |
| 5 | Окно не во время записи закрыто | ничего | — | — |

## 10. Константы и тексты

Новых нет. Используются существующие `SettingsController.ERR_INVALID_COMBO` и тексты TASK-014.

## 11. Тесты

Добавить в `HotkeyRecorderTest` (5 существующих остаются):

| # | Метод | When | Then |
|---|---|---|---|
| 6 | `` `escape cancels recording`() `` | `onKeyDown(false,false,false, KeyEvent.VK_ESCAPE)` | `RecordingStep.Cancel` |
| 7 | `` `escape with ctrl still cancels`() `` | `onKeyDown(true,false,false, KeyEvent.VK_ESCAPE)` | `RecordingStep.Cancel` |
| 8 | `` `modifier only waits`() `` | `onKeyDown(true,false,false, KeyEvent.VK_CONTROL)` | `RecordingStep.Wait` |
| 9 | `` `invalid combination`() `` | `onKeyDown(false,false,true, KeyEvent.VK_A)` | `RecordingStep.Invalid` |
| 10 | `` `ctrl alt r applies`() `` | `onKeyDown(true,true,false, KeyEvent.VK_R)` | `RecordingStep.Apply(HotkeyBinding(true,true,false,0x52))` |

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.HotkeyRecorderTest"` | 10 тестов, 0 failed |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная проверка выполняется пользователем: `.\gradlew.bat run` в отдельном окне. Dev передаёт шаги через `question`.

1. Трей → «Настройки…» → «Изменить» у «Область» → нажать `Ctrl+Alt+R` → в строке `Ctrl+Alt+R`; `Ctrl+Alt+R` запускает
   оверлей, `Ctrl+Shift+3` — нет.
2. «Изменить» → Esc → прежнее сочетание; `Ctrl+Shift+1` работает.
3. «Изменить» → `Shift+A` → красный текст `Сочетание должно содержать Ctrl или Alt и клавишу A–Z, 0–9 или F1–F12`; клавиши работают.
4. «Изменить» → закрыть окно крестиком → `Ctrl+Shift+1` работает.
5. Вернуть `Ctrl+Shift+3` для «Область». В `%LOCALAPPDATA%\fast-cow-screenshot\logs\app.log` нет `ClassCastException`.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–4 выполнены; контракты — как в разделе 6.
- [x] 10 тестов `HotkeyRecorderTest` проходят, `.\gradlew.bat build` зелёный.
- [x] В `SettingsWindow.kt` нет `nativeKeyEvent` и `java.awt.event.KeyEvent`.
- [x] Ручные шаги 1–5 записаны в журнал.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать `e.nativeKeyEvent` и любые приведения `as` к AWT-событиям.
- Не менять `SettingsController`, `HotkeyBinding`, существующие функции `HotkeyRecorder`.
- Не ловить исключения в обработчике клавиш (`try/catch`): после исправления обработчик не бросает.
- Не трогать меню трея (замечание о пункте «Выход» за панелью задач — известное ограничение, см. TDD-001 §15).

## 15. Ловушки и подсказки

- `nativeKeyCode` — extension-свойство `Key` (`androidx.compose.ui.input.key.nativeKeyCode`); без импорта не разрешится.
- `isCtrlPressed`/`isAltPressed`/`isShiftPressed` — extension-свойства `androidx.compose.ui.input.key.KeyEvent`
  (Compose), не путать с `java.awt.event.KeyEvent`.
- AltGr на русской/немецкой раскладке приходит как `Ctrl+Alt` — это допустимо, `toBinding` его примет.
- `recording` — один `MutableState` на всё окно. Не создавать второй через `remember`.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-31 | Review | Шаги 1–4 выполнены. `.\gradlew.bat build` успешен, `HotkeyRecorderTest` — 10 тестов. В `SettingsWindow.kt` нет `nativeKeyEvent` и `java.awt.event.KeyEvent`. Ручная проверка (пользователь): шаги 1, 2, 4, 5 — работают; шаг 3 — логика верна, но красный текст `ERR_INVALID_COMBO` расположен под списком и виден только при прокрутке/растягивании окна 600×460. Предложение Tech: уменьшить высоту содержимого или увеличить `HEIGHT` окна, чтобы ошибка была видна сразу. |

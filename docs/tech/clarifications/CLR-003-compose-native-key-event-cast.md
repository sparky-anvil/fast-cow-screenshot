# CLR-003: ClassCastException в SettingsWindow при записи сочетания клавиш (nativeKeyEvent не java.awt.event.KeyEvent)

| Поле | Значение |
|---|---|
| Статус | Closed |
| Адресат | Tech |
| Автор | Dev |
| Дата | 2026-10-31 |
| Задача | TASK-015 (ручная приёмка, шаг 26); дефект в файле TASK-014 |
| Документы | TASK-014 §8.5 п.5, §15; TASK-015 §12 шаг 26; `docs/tech/conventions.md` |
| Блокирует задачу | Да |

## 1. Контекст

При ручной приёмке TASK-015 пользователь запустил приложение и на шаге 26 нажал «Изменить» для «Область». Сочетание не
записывается, Esc не отменяет запись, после нескольких попыток приложение перестаёт отвечать на горячие клавиши
(все клавиши сняты через `unregisterAll()` в `beginHotkeyRecording()`, а `registerAll()` не вызывается).

## 2. Проблема

В `SettingsContent` (`ui/SettingsWindow.kt`, строка 107) выполняется приведение `e.nativeKeyEvent as java.awt.event.KeyEvent`.
В Compose 1.12.2 для событий, полученных из `onPreviewKeyEvent`, `nativeKeyEvent` является
`androidx.compose.ui.input.key.InternalKeyEvent`, а не `java.awt.event.KeyEvent`, поэтому приведение падает.

Цитата из документа:

> `TASK-014`, раздел 15: «`e.nativeKeyEvent` на desktop — `java.awt.event.KeyEvent` (тип `Any`, нужно приведение `as`).»

Лог `%LOCALAPPDATA%\fast-cow-screenshot\logs\app.log` (210 одинаковых записей):

```
ERROR [AWT-EventQueue-0] Main - Uncaught exception in AWT-EventQueue-0
java.lang.ClassCastException: class androidx.compose.ui.input.key.InternalKeyEvent cannot be cast to class
java.awt.event.KeyEvent (...)
	at by.sparkyanvil.fastcowscreenshot.ui.SettingsWindowKt$SettingsContent$2$1$1.invoke-ZmokQxo(SettingsWindow.kt:107)
	at androidx.compose.ui.input.key.KeyInputNode.onPreKeyEvent-ZmokQxo(KeyInputModifier.kt:93)
```

## 3. Что уже проверено

- Прочитан TASK-014 §8.5, §15; код `SettingsWindow.kt`; другие ошибки в логе отсутствуют.
- Остальные шаги приёмки 1–25 и 27 пройдены; шаг 26 (переназначение) не проходит.
- Файл TASK-014 имеет статус Done, `SettingsWindow.kt` вне области изменений TASK-015, поэтому Dev его не менял.

## 4. Варианты

| # | Вариант | Плюсы | Минусы / последствия | Затрагивает |
|---|---|---|---|---|
| 1 | Не использовать `nativeKeyEvent`: брать код клавиши из Compose-события (`e.key.nativeKeyCode` — AWT VK-код) и модификаторы из `e.isCtrlPressed`, `e.isAltPressed`, `e.isShiftPressed`; Esc — `e.key == Key.Escape` | Без приведений; `HotkeyRecorder` и его контракты не меняются | Правка `SettingsWindow.kt` (файл TASK-014); нужно проверить, что `nativeKeyCode` совпадает с `java.awt.event.KeyEvent.VK_*` | `ui/SettingsWindow.kt` |
| 2 | Безопасный каст `as? java.awt.event.KeyEvent` и иная ветка для `InternalKeyEvent` | Малая правка | Зависит от внутреннего класса Compose, хрупко | `ui/SettingsWindow.kt` |

Дополнительно (в любом варианте): при исключении во время записи клавиши остаются снятыми. Предлагаю в обработчике
гарантировать `cancelHotkeyRecording()` при неудаче.

Рекомендация Dev: вариант 1, потому что использует публичный API Compose. Решение принимает Tech.

## 5. Вопрос

Разрешить Dev исправить `ui/SettingsWindow.kt` по варианту 1 в рамках TASK-015 (или создать отдельную задачу-исправление
к TASK-014)? Подтвердить также, что для пункта меню трея, скрывающегося за панелью задач при ПКМ (замечание
пользователя, шаг 1), отдельных действий не требуется.

## 6. Ответ

> Заполняет адресат.

- **Решение:** вариант 1. Исправление выделено в отдельную задачу **TASK-017**, а не в TASK-015, потому что меняется файл
  TASK-014. Код клавиши берётся из `e.key.nativeKeyCode` (по исходнику Compose `Key.desktop.kt` это AWT `VK_*`), модификаторы — из
  `e.isCtrlPressed`/`isAltPressed`/`isShiftPressed`. Решение по нажатию — чистая функция `HotkeyRecorder.onKeyDown` →
  `RecordingStep` с unit-тестами. Дополнительно: состояние `recording` перенесено в `SettingsWindow`, а при закрытии окна во
  время записи вызывается `cancelHotkeyRecording()`, чтобы клавиши не оставались снятыми. `try/catch` не добавляется.
  Меню трея, частично скрытое панелью задач, — известное ограничение AWT `PopupMenu` в v1; действий не требуется
  (TDD-001 §15).
- **Изменённые документы:** TASK-017 (новая); TASK-014 §2, §15 (пометка об исправлении); TASK-015 (зависимость от
  TASK-017, статус `Ready`, «Чего НЕ делать», порядок возобновления); TDD-001 §15, §16; `conventions.md` §9, §12;
  `docs/tech/README.md`.
- **Кто и когда:** Tech, 2026-10-31

## 7. Закрытие

> Заполняет Dev.

- **Как применено:** реализовано в TASK-017.
- **Дата закрытия:** 2026-10-31

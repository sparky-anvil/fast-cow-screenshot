# TASK-015: Собрать приложение: трей, запуск, регистрация клавиш, выход; приёмочная ручная проверка

| Поле | Значение |
|---|---|
| Статус | Review |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.1–.3, .37, .42, N9, N11; US-001 – US-007 (приёмка) |
| ADR | ADR-001, ADR-002 |
| Зависит от | TASK-011, TASK-012, TASK-013, TASK-014, TASK-017 |
| Блокирует | TASK-016 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Связать все компоненты в работающее приложение `FastCowScreenshotApp`:
- значок и меню в трее;
- регистрация горячих клавиш с уведомлением о конфликтах;
- корректный выход.

Затем провести полную ручную приёмку по US-001 – US-007.

## 2. Контекст

- `main()` создаёт `FastCowScreenshotApp` и вызывает `start()`. После возврата из `main` JVM продолжает работать: значок в трее
  держит живыми потоки AWT. Завершение — только через пункт «Выход» → `System.exit(0)`.
- Компоненты и потоки — TDD-001 §4, `conventions.md` §6. Свойства `FastCowScreenshotApp` инициализируются в порядке объявления.
  Лямбда `onSaveAs` в `NotificationWindow` ссылается на объявленное ниже свойство `saveAsAction`. Это допустимо:
  лямбда вызывается позже, когда все свойства уже инициализированы.
- Меню трея — AWT `PopupMenu` (нативное меню Windows). Значок рисуется программно (`TrayIconImage`) — бинарных
  ресурсов нет.

**Выдержки из требований (дословно):**

> **REQ-001 §7** приложение … работает в фоне и имеет значок в системном трее (через него доступны «Сохранить как…», «Настройки», «Выход»).
>
> **REQ-001.3** Если горячую клавишу не удалось зарегистрировать (её уже занял другой процесс), система должна при запуске показать уведомление с названием конфликтующей комбинации. Остальные режимы должны продолжать работать.
>
> **REQ-001.37** Система должна предоставлять действие «Сохранить как…» для последнего снимка: из уведомления о захвате (пока оно видно) и из меню значка в системном трее (всегда, пока есть последний снимок).
>
> **REQ-001.42** Если снимка ещё нет (в текущем сеансе не было ни одного захвата), пункт «Сохранить как…» должен быть недоступен.

## 3. Предусловия

Выполнены TASK-001 – TASK-014; существуют `AppThreads`, `SettingsStore`, `SettingsRepository`, `KnownFolders`,
`NativeMessageLoop`, `WindowsHotkeyService`, `LastScreenshotHolder`, `ExplorerLauncher`, `NotificationWindow`,
`SaveAsAction`, `WindowsSaveDialog`, `WindowsMessageBox`, `PngFileWriter`, `CaptureCoordinator`, `WindowsMonitorProvider`,
`GdiScreenCapturer`, `WindowsCursorService`, `WindowsClipboardWriter`, `AutoSaver`, `RegionOverlay`, `SettingsController`,
`WindowsFolderDialog`, `DirectoryValidator`, `SettingsWindow`, `DpiAwareness`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/TrayIconImage.kt` | Создать | object |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/TrayController.kt` | Создать | класс |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/app/FastCowScreenshotApp.kt` | Создать | класс |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/Main.kt` | Изменить | тело `main()` |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/app/HotkeyConflictMessageTest.kt` | Создать | unit |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.ui

internal object TrayIconImage {
    /** 32×32 TYPE_INT_ARGB: скруглённый квадрат #2E86DE (радиус 8), белая буква "F", шрифт Font.SANS_SERIF BOLD 22, по центру. */
    fun create(): java.awt.image.BufferedImage = TODO()
}

/** Трей. Методы — на EDT. */
internal class TrayController(
    private val lastScreenshot: LastScreenshotHolder,
    private val onSaveAs: () -> Unit,
    private val onSettings: () -> Unit,
    private val onExit: () -> Unit,
) {
    /** @throws IllegalStateException("System tray is not supported") если SystemTray.isSupported() == false. */
    fun install(): Unit = TODO()
    fun remove(): Unit = TODO()
    companion object {
        const val TOOLTIP: String = "fast-cow-screenshot"
        const val ITEM_SAVE_AS: String = "Сохранить как…"
        const val ITEM_SETTINGS: String = "Настройки…"
        const val ITEM_EXIT: String = "Выход"
    }
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.app

internal class FastCowScreenshotApp {
    /** Запуск (раздел 8.2). Вызывать на main-потоке. */
    fun start(): Unit = TODO()
    /** Выход (раздел 8.3). Вызывать на EDT. */
    fun exit(): Unit = TODO()

    companion object {
        const val HOTKEYS_UNAVAILABLE_TITLE: String = "Горячие клавиши недоступны"
        /** Строка: "<сочетание> («<режим>») — занято другим приложением". */
        fun conflictLine(binding: HotkeyBinding, action: HotkeyAction): String = TODO()
    }
}
```

## 7. Пошаговая реализация

1. `TrayIconImage`, `TrayController` (8.1).
2. `FastCowScreenshotApp` (8.2, 8.3) + `HotkeyConflictMessageTest`.
3. `Main.kt`: `Thread.setDefaultUncaughtExceptionHandler { t, e -> log.error("Uncaught exception in {}", t.name, e) }`;
   прежняя строка лога запуска; `FastCowScreenshotApp().start()`.
4. `.\gradlew.bat build`, затем ручная приёмка раздела 12.

## 8. Алгоритм и логика

### 8.1. TrayController.install

```
if (!SystemTray.isSupported()) throw IllegalStateException("System tray is not supported")
saveAs = MenuItem(ITEM_SAVE_AS).apply { isEnabled = lastScreenshot.get() != null; addActionListener { onSaveAs() } }
settings = MenuItem(ITEM_SETTINGS).apply { addActionListener { onSettings() } }
exit = MenuItem(ITEM_EXIT).apply { addActionListener { onExit() } }
menu = PopupMenu().apply { add(saveAs); add(settings); addSeparator(); add(exit) }
icon = TrayIcon(TrayIconImage.create(), TOOLTIP, menu).apply { isImageAutoSize = true; addActionListener { onSettings() } }
SystemTray.getSystemTray().add(icon)
lastScreenshot.addListener { saveAs.isEnabled = true }   // слушатель уже вызывается на EDT
```

### 8.2. FastCowScreenshotApp: свойства и start()

Свойства (в этом порядке):
```
threads = AppThreads()
store = SettingsStore(SettingsRepository(SettingsRepository.defaultFile(), KnownFolders()))
loop = NativeMessageLoop()
hotkeys = WindowsHotkeyService(loop)
last = LastScreenshotHolder(threads.ui)
explorer = ExplorerLauncher()
notifier = NotificationWindow(onSaveAs = { saveAsAction.run() }, explorer, threads.dialog)
saveAsAction = SaveAsAction(last, store, { dir, name -> WindowsSaveDialog().show(dir, name) },
                            { WindowsMessageBox().confirmOverwrite(it) }, PngFileWriter(), notifier, threads.dialog, threads.ui)
coordinator = CaptureCoordinator(WindowsMonitorProvider(), GdiScreenCapturer(), WindowsCursorService(),
                                 WindowsClipboardWriter({ loop.messageWindow }), AutoSaver(), RegionOverlay(), notifier, last,
                                 { store.current }, { LocalDateTime.now() }, threads.capture, threads.save, threads.ui)
controller = SettingsController(store, hotkeys, KnownFolders(), { WindowsFolderDialog().show() },
                                DirectoryValidator::isWritable, { explorer.openFolder(it) }, threads.dialog, threads.ui)
settingsWindow = SettingsWindow(controller, store)
tray = TrayController(last, { saveAsAction.run() }, { settingsWindow.show() }, { exit() })
```
`start()`:
1. Если `!System.getProperty("os.name").startsWith("Windows")` → `ERROR "Unsupported OS: {}"`, `exitProcess(1)`.
2. `SwingUtilities.invokeAndWait { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); a = DpiAwareness.currentThreadAwareness(); if (a == 2) INFO "EDT DPI awareness: {}" else WARN "EDT DPI awareness: {}" }`.
3. `try { loop.start() } catch (e: WinApiException) { log.error("Native message loop failed", e); exitProcess(1) }`;
   `hotkeys.start { action -> coordinator.trigger(action.mode) }`.
4. `failed = controller.registerAll()`. Если не пуст → `threads.ui.execute { notifier.showMessage(HOTKEYS_UNAVAILABLE_TITLE, failed.map { conflictLine(store.current.hotkeyBinding(it), it) }) }`.
5. `SwingUtilities.invokeAndWait { tray.install() }`. При `IllegalStateException` → `ERROR`, `exitProcess(1)`.
6. `INFO "Application started, settings: {}"` (путь файла настроек).

`conflictLine(b, a)` = `"${b.format()} («${a.displayName}») — занято другим приложением"`.

### 8.3. exit()

`INFO "Exiting"`; по очереди, каждый шаг в своём `try { } catch (e: Exception) { log.warn("Exit step failed: {}", e.toString()) }`:
`hotkeys.unregisterAll()`, `loop.stop()`, `notifier.dispose()`, `settingsWindow.dispose()`, `tray.remove()`,
`threads.shutdown(5000)`. Затем `exitProcess(0)`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | Трей не поддерживается | выход с кодом 1 | `ERROR` | — |
| 2 | 1–3 клавиши заняты | остальные работают | `WARN` | `"Горячие клавиши недоступны"` + строки, например `"Ctrl+Shift+1 («Монитор под курсором») — занято другим приложением"` |
| 3 | `loop.start()` бросил `WinApiException` | выход с кодом 1 | `ERROR "Native message loop failed"` | — |
| 4 | Сохранение идёт в момент выхода | ждём до 5 с | — | — |

## 10. Константы и тексты

Тексты (дословно): `"fast-cow-screenshot"` (подсказка значка), `"Сохранить как…"`, `"Настройки…"`, `"Выход"`,
`"Горячие клавиши недоступны"`, `"<сочетание> («<режим>») — занято другим приложением"`. Цвет значка `#2E86DE`.

## 11. Тесты

`HotkeyConflictMessageTest`:

| # | Метод | When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `` `conflict line names combination and mode`() `` | `conflictLine(HotkeyBinding.defaultFor(MONITOR_UNDER_CURSOR), MONITOR_UNDER_CURSOR)` | `"Ctrl+Shift+1 («Монитор под курсором») — занято другим приложением"` | REQ-001.3 |

## 12. Проверка

**Автоматическая:** `.\gradlew.bat build` → `BUILD SUCCESSFUL` (все тесты проекта).

**Ручная (выполняет пользователь).** Dev даёт пользователю этот список через `question` частями по 5–7 шагов и
записывает результат каждого шага в журнал. Приложение запускает пользователь: `.\gradlew.bat run` в отдельном окне PowerShell.

1. Запуск → в трее значок «F». Меню: «Сохранить как…» неактивен, «Настройки…», «Выход» (US-006 AC-8).
2. Фокус в браузере, `Ctrl+Shift+1` → справа внизу основного монитора уведомление «Снимок сохранён», имя файла
   `Screenshot_…png`, папка; фокус остался в браузере (US-001 AC-3, US-005 AC-6).
3. Paint → Ctrl+V → размер изображения = разрешение монитора из параметров дисплея (US-001 AC-1, AC-2).
4. (≥ 2 мониторов) курсор на втором мониторе, `Ctrl+Shift+1` → снят второй монитор (US-001 AC-1).
5. Уведомление исчезает примерно через 3 с (REQ-001.54).
6. Два снимка подряд быстрее 3 с → второе уведомление заменяет первое (US-005 AC-7).
7. Клик по уведомлению → Проводник с выделенным файлом (REQ-001.34).
8. Снимок, сделанный пока видно уведомление, не содержит уведомления (US-001 AC-6).
9. Трей → «Сохранить как…» активен → диалог Windows: папка по умолчанию, имя по шаблону, фильтр `PNG (*.png)` (US-006 AC-1).
10. Ввести `login-error` в другой папке → создан `login-error.png`; автосохранённый файл на месте (US-006 AC-2).
11. Новый снимок → «Сохранить как…» → диалог в последней папке; после перезапуска приложения — тоже (US-006 AC-3, AC-4).
12. Ввести `login.jpg` → `login.jpg.png`; ввести имя существующего файла → вопрос о замене; «Нет» → файл не изменён (US-006 AC-6, AC-7).
13. Кнопка «Сохранить как…» в уведомлении открывает тот же диалог (REQ-001.37).
14. `Ctrl+Shift+2` → одно изображение всех мониторов, зоны вне мониторов серые (US-002 AC-1, AC-2).
15. Открыть выпадающее меню любого приложения, `Ctrl+Shift+3` → оверлей с неподвижным кадром, меню видно; в логе
    `Overlay shown in N ms`, N ≤ 250 (US-003 AC-1, N3).
16. Протянуть выделение через границу мониторов → видна подпись `Ш×В`; отпустить → оверлей закрыт; Ctrl+V в Paint —
    ровно выделенная область, без затемнения и рамки (US-003 AC-2, AC-3, AC-4, AC-7).
17. Выделение снизу-справа вверх-влево работает.
18. Скопировать текст, `Ctrl+Shift+3`, Esc → оверлей закрыт, Ctrl+V вставляет текст; то же с правой кнопкой (US-003 AC-5, US-004 AC-5).
19. Одиночный клик в оверлее → отмена (US-003 AC-6).
20. Во время открытого оверлея нажать `Ctrl+Shift+1` → ничего не происходит (REQ-001.5).
21. Трей → «Настройки…» → директория по умолчанию = папка «Снимки экрана» (US-007 AC-1).
22. «Обзор…» → `D:\Скриншоты` (создать заранее) → путь отображается, следующий снимок сохраняется туда, после перезапуска путь тот же (US-007 AC-2, AC-3).
23. «Обзор…» → `C:\Windows\System32` → ошибка, прежнее значение (US-007 AC-4).
24. «Сбросить» → исходная папка (US-007 AC-6); «Открыть папку» → Проводник.
25. Включить «Включать указатель мыши в снимок» → на снимке `Ctrl+Shift+1` виден указатель; в режиме «Область» при
    выделении без точки указателя — указателя нет (US-001 AC-8, US-003 AC-8).
26. «Изменить» для «Область» → нажать `Ctrl+Alt+R` → отображается `Ctrl+Alt+R`, работает, `Ctrl+Shift+3` больше не работает;
    после перезапуска сохраняется (REQ-001.4). Вернуть `Ctrl+Shift+3`.
27. Снять флажок автосохранения → после снимка уведомление «Снимок скопирован в буфер обмена», файл не создан (REQ-001.35). Вернуть флажок.
28. Запустить второй экземпляр (`.\gradlew.bat run` во втором окне) → у второго уведомление «Горячие клавиши недоступны»
    с тремя строками; закрыть его через «Выход» (REQ-001.3, US-001 AC-4).
29. Вставить снимок в Word, Telegram Desktop, Chrome/Edge (US-004 AC-1).
30. Директория по умолчанию на USB-флешке → извлечь флешку → снимок → «Не удалось сохранить снимок», причина,
    «Сохранить как…»; в буфере — снимок (US-005 AC-4, US-004 AC-2).
31. Трей → «Выход» → процесс завершился; Paint → Ctrl+V вставляет последний снимок (US-004 AC-4).

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–4 выполнены; контракты — как в разделе 6.
- [x] `.\gradlew.bat build` → `BUILD SUCCESSFUL`.
- [x] Результаты ручных шагов 1–31 записаны в журнал. Каждый провал оформлен CLR к Tech с номером шага и логом
      `%LOCALAPPDATA%\fast-cow-screenshot\logs\app.log`.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не исправлять найденные при приёмке ошибки в файлах других задач без CLR.
- Не добавлять автозапуск, запрет второго экземпляра, пункты меню сверх раздела 10.
- Не использовать Compose `Tray`/`application {}`.
- Не менять `ui/SettingsWindow.kt`: запись сочетаний исправляется в TASK-017 (CLR-003).
- Пункт «Выход» меню трея при ПКМ частично перекрывается панелью задач — известное ограничение AWT `PopupMenu`
  (TDD-001 §15), не дефект; CLR по нему не оформлять, в журнале отметить «известное ограничение».

**Возобновление после CLR-003:** код шагов 1–4 уже написан (см. журнал), повторно не писать. После `Done` у TASK-017:
`.\gradlew.bat build`, затем ручные шаги 26 и 28–31 раздела 12 (шаги 1–25, 27 уже пройдены), затем `Review`.

## 15. Ловушки и подсказки

- `exitProcess` — `kotlin.system.exitProcess`.
- `SwingUtilities.invokeAndWait` нельзя вызывать с EDT — `start()` вызывается с main-потока.
- Если значок не виден — он может быть в скрытой области трея (стрелка «^»). Это не ошибка.
- Слушатели `LastScreenshotHolder` уже вызываются через `threads.ui`, отдельный `invokeLater` не нужен.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-31 | Blocked | Шаги 1–4 выполнены, `.\gradlew.bat build` успешен, `HotkeyConflictMessageTest` проходит. Для `notifier` и `saveAsAction` добавлены явные типы (рекурсивный вывод типов). Приёмка: шаги 1–25, 27 — пользователь: «Работает» (замечание к шагу 1: пункт «Выход» в меню трея при ПКМ скрывается за панелью задач — AWT PopupMenu, вне задачи); шаг 26 не проходит: ClassCastException в SettingsWindow.kt:107 → [CLR-003](../clarifications/CLR-003-compose-native-key-event-cast.md) (Tech). Шаги 28–31 не выполнялись. |
| 2026-10-31 | In Progress | CLR-003 закрыт, TASK-017 в Done; возобновлена. |
| 2026-10-31 | Review | `.\gradlew.bat build` успешен. Ручная приёмка (пользователь): шаги 1–25, 27 — «Работает»; после TASK-017 шаги 26, 28–31 — «Работает как описано». Известное ограничение: пункт «Выход» меню трея частично за панелью задач (AWT PopupMenu), не дефект. Отклонение: явные типы у `notifier` и `saveAsAction` (рекурсивный вывод типов). |

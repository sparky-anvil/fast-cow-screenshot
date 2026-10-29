# TASK-007: Реализовать нативный цикл сообщений и глобальные горячие клавиши (RegisterHotKey)

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | M (1–3 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.2, .3, .4, .52; US-001 AC-3, AC-4 |
| ADR | ADR-002 |
| Зависит от | TASK-004, TASK-005 |
| Блокирует | TASK-008, TASK-014, TASK-015 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать поток `fcs-hotkeys` с циклом сообщений Windows (`NativeMessageLoop`). Он принимает `WM_HOTKEY`, выполняет
задачи других потоков (`invoke`) и владеет скрытым message-only окном — оно нужно буферу обмена в TASK-008.
Поверх цикла — `WindowsHotkeyService`: регистрирует, переназначает и снимает глобальные горячие клавиши.

## 2. Контекст

- `RegisterHotKey(null, id, modifiers, vk)` привязывает клавишу к **очереди сообщений потока, который её вызвал**.
  `WM_HOTKEY` (`wParam = id`) придёт только этому потоку. `UnregisterHotKey` тоже должен вызываться на нём. Поэтому
  все вызовы проходят через `NativeMessageLoop.invoke`.
- Очередь сообщений потока создаётся при первом вызове `PeekMessage`. Пока её нет, `PostThreadMessage` не работает —
  поэтому `PeekMessage(PM_NOREMOVE)` вызывается в самом начале потока.
- Если комбинация занята другим процессом (или этим же для другого id), `RegisterHotKey` возвращает `false`
  (GetLastError = 1409). Так определяется конфликт (REQ-001.3).
- Message-only окно: `CreateWindowEx(0, "STATIC", "fcs-message-window", 0, 0, 0, 0, 0, HWND_MESSAGE, null, null, null)`.
  Класс `STATIC` предопределён в Windows, регистрировать его не нужно. Сообщения, отправленные окну через `SendMessage`
  (например `WM_DESTROYCLIPBOARD`), `GetMessageW` обрабатывает сам.
- Id горячей клавиши = `action.ordinal + 1` (1, 2, 3).

**Выдержки из требований (дословно):**

> **REQ-001.2** Система должна запускать каждый режим своей глобальной горячей клавишей. Клавиша должна срабатывать, когда приложение работает в фоне, свёрнуто или не имеет фокуса ввода.
>
> **REQ-001.3** Если горячую клавишу не удалось зарегистрировать (её уже занял другой процесс), система должна при запуске показать уведомление с названием конфликтующей комбинации. Остальные режимы должны продолжать работать.
>
> **REQ-001.4** Система должна позволять переназначить горячую клавишу каждого режима в настройках.

## 3. Предусловия

- TASK-004: `HotkeyAction`, `HotkeyBinding` (`winModifiers`, `keyCode`, `format()`) в `…fastcowscreenshot.settings`.
- TASK-005: `User32Api`, `Kernel32Api`, `WinConst`, `WinApiException` в `…platform.windows(.jna)`.
- `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/capture/HotkeyService.kt` | Создать | интерфейс |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/NativeMessageLoop.kt` | Создать | класс |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/WindowsHotkeyService.kt` | Создать | класс |
| 4 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/NativeMessageLoopTest.kt` | Создать | тесты |
| 5 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/WindowsHotkeyServiceTest.kt` | Создать | тесты |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

```kotlin
package by.sparkyanvil.fastcowscreenshot.capture

/** Глобальные горячие клавиши. Потокобезопасен. */
internal interface HotkeyService {
    /** Устанавливает обработчик. onHotkey вызывается на потоке fcs-hotkeys и должен быстро возвращать управление. */
    fun start(onHotkey: (HotkeyAction) -> Unit)
    /**
     * Регистрирует binding для action, заменяя прежнюю регистрацию этого action.
     * @return true — зарегистрировано; false — занято (прежняя регистрация action, если была, восстановлена).
     */
    fun register(action: HotkeyAction, binding: HotkeyBinding): Boolean
    /** Снимает все регистрации. */
    fun unregisterAll()
}
```

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** Поток fcs-hotkeys с циклом GetMessageW. Потокобезопасен. */
internal class NativeMessageLoop(
    private val user32: User32Api = User32Api.INSTANCE,
    private val kernel32: Kernel32Api = Kernel32Api.INSTANCE,
) {
    /** Обработчик WM_HOTKEY (аргумент — id). Вызывается на fcs-hotkeys. */
    @Volatile var onHotkey: ((Int) -> Unit)? = null

    /**
     * Запускает daemon-поток "fcs-hotkeys" и ждёт готовности до START_TIMEOUT_MS.
     * @throws WinApiException если окно не создано или поток не стартовал за таймаут.
     * @throws IllegalStateException при повторном вызове.
     */
    fun start(): Unit = TODO()

    /** HWND message-only окна. @throws IllegalStateException до start(). */
    val messageWindow: HWND get() = TODO()

    /**
     * Выполняет block на fcs-hotkeys и возвращает результат. На самом fcs-hotkeys выполняет сразу.
     * Ждёт до INVOKE_TIMEOUT_MS. Исключение из block пробрасывается вызывающему (cause из ExecutionException).
     * @throws java.util.concurrent.TimeoutException при превышении таймаута.
     */
    fun <T> invoke(block: () -> T): T = TODO()

    /** PostThreadMessage(WM_QUIT), join до STOP_TIMEOUT_MS. Повторный вызов — без действия. */
    fun stop(): Unit = TODO()

    companion object {
        const val THREAD_NAME: String = "fcs-hotkeys"
        const val WM_APP_INVOKE: Int = WinConst.WM_APP + 1
        const val START_TIMEOUT_MS: Long = 5000
        const val INVOKE_TIMEOUT_MS: Long = 5000
        const val STOP_TIMEOUT_MS: Long = 2000
    }
}

/** Реализация HotkeyService на RegisterHotKey. */
internal class WindowsHotkeyService(
    private val loop: NativeMessageLoop,
    private val user32: User32Api = User32Api.INSTANCE,
) : HotkeyService
```

## 7. Пошаговая реализация

1. `HotkeyService.kt`.
2. `NativeMessageLoop.kt` — раздел 8.1. Тесты `NativeMessageLoopTest` проходят.
3. `WindowsHotkeyService.kt` — раздел 8.2. Тесты `WindowsHotkeyServiceTest` проходят.

## 8. Алгоритм и логика

### 8.1. NativeMessageLoop

Поля: `tasks = ConcurrentLinkedQueue<FutureTask<*>>()`, `@Volatile threadId = 0`, `@Volatile hwnd: HWND? = null`,
`ready = CountDownLatch(1)`, `@Volatile startError: String? = null`, `thread: Thread?`.

Тело потока:
1. `threadId = kernel32.GetCurrentThreadId()`; `msg = WinUser.MSG()`; `user32.PeekMessage(msg, null, 0, 0, PM_NOREMOVE)`.
2. `hwnd = user32.CreateWindowEx(0, "STATIC", "fcs-message-window", 0, 0, 0, 0, 0, HWND(Pointer.createConstant(HWND_MESSAGE_VALUE.toLong())), null, null, null)`.
   Если `null` → `startError = WinApiException.lastError("CreateWindowEx").message`, `ready.countDown()`, выйти.
3. `ready.countDown()`; `INFO "Native message loop started"`.
4. Цикл: `while (true) { r = user32.GetMessage(msg, null, 0, 0); if (r == 0) break; if (r == -1) { ERROR "GetMessage failed, GetLastError={}"; break } … }`:
   - `msg.message == WM_HOTKEY` → `try { onHotkey?.invoke(msg.wParam.toInt()) } catch (e: Exception) { ERROR "Hotkey handler failed", e }`;
   - `msg.message == WM_APP_INVOKE` → `while (true) { t = tasks.poll() ?: break; t.run() }`.
5. После цикла: `user32.DestroyWindow(hwnd)`; `INFO "Native message loop stopped"`.

`start()`: создать `Thread(body, THREAD_NAME)`, `isDaemon = true`, `start()`; `ready.await(START_TIMEOUT_MS, ms)` false →
`throw WinApiException("Native message loop did not start")`; `startError != null` → `throw WinApiException(startError)`.

`invoke(block)`: если `Thread.currentThread() === thread` → `return block()`. Иначе `task = FutureTask(block)`,
`tasks.add(task)`; если `!PostThreadMessage(threadId, WM_APP_INVOKE, WPARAM(0), LPARAM(0))` →
`throw WinApiException.lastError("PostThreadMessage")`. `try { return task.get(INVOKE_TIMEOUT_MS, ms) } catch (e: ExecutionException) { throw e.cause ?: e }`.

`stop()`: `PostThreadMessage(threadId, WM_QUIT, WPARAM(0), LPARAM(0))`; `thread.join(STOP_TIMEOUT_MS)`.

### 8.2. WindowsHotkeyService

- `registered = mutableMapOf<HotkeyAction, HotkeyBinding>()` — читается и меняется **только** внутри `loop.invoke`.
- `start(onHotkey)`: `loop.onHotkey = { id -> HotkeyAction.entries.getOrNull(id - 1)?.let(onHotkey) }`.
- `register(action, binding)` = `loop.invoke { … }`:
  1. `id = action.ordinal + 1`; `previous = registered[action]`.
  2. Если `previous != null` → `user32.UnregisterHotKey(null, id)`; `registered.remove(action)`.
  3. `if (user32.RegisterHotKey(null, id, binding.winModifiers, binding.keyCode))` → `registered[action] = binding`;
     `INFO "Hotkey registered: {} = {}"` (`action`, `binding.format()`); `true`.
  4. Иначе `WARN "RegisterHotKey failed for {}: {}, GetLastError={}"`; если `previous != null` и
     `RegisterHotKey(null, id, previous.winModifiers, previous.keyCode)` → `registered[action] = previous`; `false`.
- `unregisterAll()` = `loop.invoke { registered.keys.forEach { UnregisterHotKey(null, it.ordinal + 1) }; registered.clear() }`.

**Пример:** `register(REGION, Ctrl+Shift+3)` → `RegisterHotKey(null, 3, 0x4006, 0x33)`.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | Сочетание занято другим процессом | `false`, прежняя регистрация восстановлена | `WARN "RegisterHotKey failed for …"` | уведомление показывает TASK-015 |
| 2 | То же сочетание у другого action | `false` (Windows отказывает) | `WARN` | — |
| 3 | Исключение в `onHotkey` | Цикл продолжает работу | `ERROR "Hotkey handler failed"` | — |
| 4 | `invoke` после `stop()` | `PostThreadMessage` false → `WinApiException` | — | — |
| 5 | Повторный `start()` | `IllegalStateException("already started")` | — | — |

## 10. Константы и тексты

Константы — в разделе 6. Текстов UI нет.

## 11. Тесты

Все тесты — `@EnabledOnOs(OS.WINDOWS)`. Каждый тест создаёт свой `NativeMessageLoop`, вызывает `start()`, а в конце
(`try/finally`) — `stop()`.

| # | Класс / метод | When | Then | Покрывает |
|---|---|---|---|---|
| 1 | `NativeMessageLoopTest` / `` `invoke runs on loop thread`() `` | `invoke { Thread.currentThread().name }` | `"fcs-hotkeys"` | — |
| 2 | `` `invoke propagates exception`() `` | `invoke { throw IllegalStateException("x") }` | `assertFailsWith<IllegalStateException>`, message `"x"` | — |
| 3 | `` `message window is created`() `` | `messageWindow` | не бросает, `Pointer.nativeValue(messageWindow.pointer) != 0L` | — |
| 4 | `` `stop terminates thread`() `` | `stop()` | после этого `invoke { 1 }` бросает исключение (`assertFails`) | — |
| 5 | `WindowsHotkeyServiceTest` / `` `registers rare binding`() `` | `register(REGION, HotkeyBinding(true,true,true,0x7B))` (Ctrl+Alt+Shift+F12) | `true`; затем `unregisterAll()` | REQ-001.2 |
| 6 | `` `same binding for second action fails and keeps first`() `` | `register(REGION, b)` → `true`; `register(ALL_MONITORS, b)` | `false`; повторный `register(REGION, b)` → `true` (своё переназначение на то же значение работает) | REQ-001.3 |
| 7 | `` `reassign replaces previous binding`() `` | `register(REGION, Ctrl+Alt+Shift+F12)`, затем `register(REGION, Ctrl+Alt+Shift+F11 = 0x7A)` | оба `true`; `register(ALL_MONITORS, Ctrl+Alt+Shift+F12)` → `true` (старое освобождено) | REQ-001.4 |

`unregisterAll()` в `finally` каждого теста `WindowsHotkeyServiceTest`.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.platform.windows.NativeMessageLoopTest"` | 4 теста |
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsHotkeyServiceTest"` | 3 теста |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

Ручная проверка нажатий — в TASK-015.

## 13. Критерии готовности (Definition of Done)

- [x] Шаги 1–3 выполнены; контракты — как в разделе 6.
- [x] `RegisterHotKey`/`UnregisterHotKey` вызываются только на `fcs-hotkeys`.
- [x] 7 тестов проходят.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать JNativeHook и низкоуровневые хуки клавиатуры.
- Не регистрировать клавиши по умолчанию в этой задаче (это делает TASK-015).
- Не реализовывать буфер обмена (TASK-008).

## 15. Ловушки и подсказки

- Тест 6: если на машине Ctrl+Alt+Shift+F12 занят другим приложением, тест 5 упадёт. Это проблема окружения —
  сообщить пользователю (CLR), не менять сочетание в тесте.
- `msg.wParam` — `WPARAM` (`IntegerType`): `.toInt()`.
- `GetMessage` возвращает `Int` (может быть `-1`), не `Boolean`.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-29 | Review | Реализованы HotkeyService, NativeMessageLoop, WindowsHotkeyService и 7 тестов (4 + 3), все пройдены; `gradlew build` - BUILD SUCCESSFUL. По указанию пользователя перед началом TASK-006 и TASK-004 переведены в Done. Отклонений нет. Ручная проверка нажатий - в TASK-015. |

# TASK-005: Объявить JNA-маппинги WinAPI, утилиты DPI awareness и HWND окон

| Поле | Значение |
|---|---|
| Статус | Done |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-001 |
| Требования | REQ-001.7, .19, .20, .21 (основа для TASK-006…015) |
| ADR | ADR-002 |
| Зависит от | TASK-001 |
| Блокирует | TASK-006, TASK-007, TASK-008, TASK-009, TASK-011, TASK-012, TASK-013, TASK-014 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Объявить в одном месте все функции, структуры и константы WinAPI, которые нужны проекту. Добавить три утилиты:
исключение `WinApiException`, `DpiAwareness` (физические координаты на потоке) и `WindowHandles` (HWND AWT-окна
и исключение окна из захвата). Логики захвата в задаче нет — только «провода».

## 2. Контекст

- JNA вызывает функции DLL Windows через Kotlin-интерфейс. Интерфейс загружается так:
  `Native.load("user32", User32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)`. `DEFAULT_OPTIONS` сам ищет
  `…W`-версию функции (например, `GetMonitorInfo` → `GetMonitorInfoW`), а если её нет — берёт имя как есть.
  Поэтому имена объявляются **без** `W`.
- **DPI awareness.** Если поток не «per-monitor aware», Windows подменяет координаты логическими (масштабированными).
  Захват должен идти в физических пикселях, поэтому поток захвата переводится в контекст
  `DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2` (значение-handle `-4`).
- `SetWindowDisplayAffinity(hwnd, WDA_EXCLUDEFROMCAPTURE = 0x11)` (Windows 10 2004+) делает окно невидимым для
  `BitBlt`. Так собственные окна не попадают в снимок (REQ-001.21).
- Все структуры x64. Размеры проверяются тестами (раздел 11).

**Выдержки из требований (дословно):**

> **REQ-001.20** Снимок должен совпадать с содержимым экрана пиксель в пиксель, без масштабирования и сглаживания, при любых коэффициентах масштабирования Windows от 100 % до 300 %, включая смешанные конфигурации.
>
> **REQ-001.21** Окна самого приложения (оверлей выделения, уведомления, окно настроек) не должны попадать в снимок.

## 3. Предусловия

- TASK-001 выполнена (JNA 5.19.1 подключена).
- `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

`J = src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/jna/`,
`P = src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/`,
`T = src/test/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/`.

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `J/WinConst.kt` | Создать | `object WinConst` — константы раздела 10 |
| 2 | `J/CURSORINFO.kt` | Создать | структура |
| 3 | `J/OPENFILENAME.kt` | Создать | структура |
| 4 | `J/BROWSEINFO.kt` | Создать | структура |
| 5 | `J/User32Api.kt` | Создать | интерфейс |
| 6 | `J/Gdi32Api.kt` | Создать | интерфейс |
| 7 | `J/Kernel32Api.kt` | Создать | интерфейс |
| 8 | `J/Shell32Api.kt` | Создать | интерфейс |
| 9 | `J/Comdlg32Api.kt` | Создать | интерфейс |
| 10 | `P/WinApiException.kt` | Создать | исключение |
| 11 | `P/DpiAwareness.kt` | Создать | утилита |
| 12 | `P/WindowHandles.kt` | Создать | утилита |
| 13 | `T/jna/StructureSizeTest.kt`, `T/DpiAwarenessTest.kt` | Создать | тесты |

**Файлы, которые изменять нельзя:** все, кроме перечисленных.

## 5. Зависимости

Новых нет.

## 6. Контракты

Общие импорты для `jna`: `com.sun.jna.*`, `com.sun.jna.platform.win32.WinDef.*`, `com.sun.jna.platform.win32.WinNT`,
`com.sun.jna.platform.win32.WinUser`, `com.sun.jna.platform.win32.WinGDI`, `com.sun.jna.ptr.PointerByReference`,
`com.sun.jna.win32.StdCallLibrary`, `com.sun.jna.win32.W32APIOptions`.

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

internal interface User32Api : StdCallLibrary {
    fun GetDC(hWnd: HWND?): HDC?
    fun ReleaseDC(hWnd: HWND?, hDC: HDC): Int
    fun EnumDisplayMonitors(hdc: HDC?, lprcClip: RECT?, lpfnEnum: WinUser.MONITORENUMPROC, dwData: LPARAM?): Boolean
    fun GetMonitorInfo(hMonitor: WinUser.HMONITOR, lpmi: WinUser.MONITORINFOEX): Boolean
    fun MonitorFromPoint(pt: POINT.ByValue, dwFlags: Int): WinUser.HMONITOR?
    fun MonitorFromWindow(hWnd: HWND, dwFlags: Int): WinUser.HMONITOR?
    fun GetCursorPos(lpPoint: POINT): Boolean
    fun GetCursorInfo(pci: CURSORINFO): Boolean
    fun CopyIcon(hIcon: HICON): HICON?
    fun DestroyIcon(hIcon: HICON): Boolean
    fun GetIconInfo(hIcon: HICON, piconinfo: WinGDI.ICONINFO): Boolean
    fun DrawIconEx(hdc: HDC, xLeft: Int, yTop: Int, hIcon: HICON, cxWidth: Int, cyWidth: Int,
                   istepIfAniCur: Int, hbrFlickerFreeDraw: Pointer?, diFlags: Int): Boolean
    fun RegisterHotKey(hWnd: HWND?, id: Int, fsModifiers: Int, vk: Int): Boolean
    fun UnregisterHotKey(hWnd: HWND?, id: Int): Boolean
    fun GetMessage(lpMsg: WinUser.MSG, hWnd: HWND?, wMsgFilterMin: Int, wMsgFilterMax: Int): Int
    fun PeekMessage(lpMsg: WinUser.MSG, hWnd: HWND?, wMsgFilterMin: Int, wMsgFilterMax: Int, wRemoveMsg: Int): Boolean
    fun PostThreadMessage(idThread: Int, msg: Int, wParam: WPARAM, lParam: LPARAM): Boolean
    fun CreateWindowEx(dwExStyle: Int, lpClassName: String, lpWindowName: String, dwStyle: Int,
                       x: Int, y: Int, nWidth: Int, nHeight: Int, hWndParent: HWND?, hMenu: Pointer?,
                       hInstance: Pointer?, lpParam: Pointer?): HWND?
    fun DestroyWindow(hWnd: HWND): Boolean
    fun OpenClipboard(hWndNewOwner: HWND?): Boolean
    fun EmptyClipboard(): Boolean
    fun SetClipboardData(uFormat: Int, hMem: Pointer): Pointer?
    fun CloseClipboard(): Boolean
    fun SetThreadDpiAwarenessContext(dpiContext: Pointer): Pointer?
    fun GetThreadDpiAwarenessContext(): Pointer?
    fun GetAwarenessFromDpiAwarenessContext(value: Pointer): Int
    fun SetWindowPos(hWnd: HWND, hWndInsertAfter: HWND?, x: Int, y: Int, cx: Int, cy: Int, uFlags: Int): Boolean
    fun GetWindowRect(hWnd: HWND, lpRect: RECT): Boolean
    fun SetWindowDisplayAffinity(hWnd: HWND, dwAffinity: Int): Boolean
    fun MessageBox(hWnd: HWND?, lpText: String, lpCaption: String, uType: Int): Int

    companion object {
        val INSTANCE: User32Api = Native.load("user32", User32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

internal interface Gdi32Api : StdCallLibrary {
    fun CreateCompatibleDC(hdc: HDC?): HDC?
    fun DeleteDC(hdc: HDC): Boolean
    fun CreateDIBSection(hdc: HDC?, pbmi: WinGDI.BITMAPINFO, usage: Int, ppvBits: PointerByReference,
                         hSection: Pointer?, offset: Int): HBITMAP?
    fun SelectObject(hdc: HDC, h: WinNT.HANDLE): WinNT.HANDLE?
    fun DeleteObject(ho: WinNT.HANDLE): Boolean
    fun BitBlt(hdc: HDC, x: Int, y: Int, cx: Int, cy: Int, hdcSrc: HDC, x1: Int, y1: Int, rop: Int): Boolean

    companion object {
        val INSTANCE: Gdi32Api = Native.load("gdi32", Gdi32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

internal interface Kernel32Api : StdCallLibrary {
    fun GlobalAlloc(uFlags: Int, dwBytes: Long): Pointer?
    fun GlobalLock(hMem: Pointer): Pointer?
    fun GlobalUnlock(hMem: Pointer): Boolean
    fun GlobalFree(hMem: Pointer): Pointer?
    fun GetCurrentThreadId(): Int

    companion object {
        val INSTANCE: Kernel32Api = Native.load("kernel32", Kernel32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

internal interface Shell32Api : StdCallLibrary {
    fun ILCreateFromPath(pszPath: String): Pointer?
    fun ILFree(pidl: Pointer)
    fun SHOpenFolderAndSelectItems(pidlFolder: Pointer, cidl: Int, apidl: Pointer?, dwFlags: Int): WinNT.HRESULT
    fun SHBrowseForFolder(lpbi: BROWSEINFO): Pointer?
    fun SHGetPathFromIDList(pidl: Pointer, pszPath: CharArray): Boolean

    companion object {
        val INSTANCE: Shell32Api = Native.load("shell32", Shell32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}

internal interface Comdlg32Api : StdCallLibrary {
    fun GetSaveFileName(lpofn: OPENFILENAME): Boolean
    fun CommDlgExtendedError(): Int

    companion object {
        val INSTANCE: Comdlg32Api = Native.load("comdlg32", Comdlg32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}
```

Структуры (эталонный фрагмент — оформить три класса так же):

```kotlin
@Structure.FieldOrder("cbSize", "flags", "hCursor", "ptScreenPos")
internal class CURSORINFO : Structure() {
    @JvmField var cbSize: Int = 0
    @JvmField var flags: Int = 0
    @JvmField var hCursor: HICON? = null
    @JvmField var ptScreenPos: POINT = POINT()
    init { cbSize = size() }
}
```

`OPENFILENAME` — поля в этом порядке и с этими типами (все `@JvmField var`, начальные значения `0`/`null`, в `init`
`lStructSize = size()`):
`lStructSize: Int`, `hwndOwner: HWND?`, `hInstance: Pointer?`, `lpstrFilter: Pointer?`, `lpstrCustomFilter: Pointer?`,
`nMaxCustFilter: Int`, `nFilterIndex: Int`, `lpstrFile: Pointer?`, `nMaxFile: Int`, `lpstrFileTitle: Pointer?`,
`nMaxFileTitle: Int`, `lpstrInitialDir: WString?`, `lpstrTitle: WString?`, `Flags: Int`, `nFileOffset: Short`,
`nFileExtension: Short`, `lpstrDefExt: WString?`, `lCustData: Pointer?`, `lpfnHook: Pointer?`, `lpTemplateName: Pointer?`,
`pvReserved: Pointer?`, `dwReserved: Int`, `FlagsEx: Int`.

`BROWSEINFO` — `hwndOwner: HWND?`, `pidlRoot: Pointer?`, `pszDisplayName: Pointer?`, `lpszTitle: WString?`, `ulFlags: Int`,
`lpfn: Pointer?`, `lParam: Pointer?`, `iImage: Int`.

```kotlin
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** Сбой WinAPI-вызова. */
internal class WinApiException(message: String) : RuntimeException(message) {
    companion object {
        /** WinApiException("$function failed, GetLastError=${Native.getLastError()}"). Вызывать сразу после неудачного вызова. */
        fun lastError(function: String): WinApiException = TODO()
    }
}

/** DPI awareness текущего потока. Потокобезопасен (действует на вызывающий поток). */
internal object DpiAwareness {
    /** DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = (HANDLE)-4. */
    val PER_MONITOR_AWARE_V2: Pointer = Pointer.createConstant(-4L)
    const val AWARENESS_PER_MONITOR: Int = 2
    /**
     * SetThreadDpiAwarenessContext(PER_MONITOR_AWARE_V2). true — успех.
     * При null: log WARN "SetThreadDpiAwarenessContext failed, GetLastError={}", вернуть false.
     */
    fun enterPerMonitorV2(): Boolean = TODO()
    /** GetAwarenessFromDpiAwarenessContext(GetThreadDpiAwarenessContext()); -1, если контекст null. */
    fun currentThreadAwareness(): Int = TODO()
}

/** HWND AWT-окон. Вызывать на EDT. */
internal object WindowHandles {
    /** require(window.isDisplayable) { "window is not displayable" }; HWND(Native.getWindowPointer(window)). */
    fun hwndOf(window: java.awt.Window): HWND = TODO()
    /**
     * SetWindowDisplayAffinity(hwndOf(window), WinConst.WDA_EXCLUDEFROMCAPTURE). Возвращает результат.
     * При false: log WARN "SetWindowDisplayAffinity failed, GetLastError={}".
     */
    fun excludeFromCapture(window: java.awt.Window): Boolean = TODO()
}
```

## 7. Пошаговая реализация

1. `WinConst.kt` — все константы раздела 10 как `const val` (тип `Int`).
2. Структуры `CURSORINFO`, `OPENFILENAME`, `BROWSEINFO`. Компиляция проходит.
3. Пять интерфейсов — посимвольно по разделу 6.
4. `WinApiException`, `DpiAwareness`, `WindowHandles`.
5. Тесты раздела 11 → `.\gradlew.bat test` проходит.

## 8. Алгоритм и логика

Н/П — только объявления и однострочные обёртки по KDoc.

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Результат |
|---|---|---|---|---|
| 1 | `SetThreadDpiAwarenessContext` вернул null | `false` | `WARN "SetThreadDpiAwarenessContext failed, GetLastError={}"` | вызывающий решает сам |
| 2 | `hwndOf` у окна без peer | `IllegalArgumentException("window is not displayable")` | — | — |
| 3 | `SetWindowDisplayAffinity` false (Windows < 2004) | `false` | `WARN …` | окно может попасть в снимок |
| 4 | Функция не найдена в DLL (опечатка) | `UnsatisfiedLinkError` при первом вызове | — | исправить имя по разделу 6 |

## 10. Константы и тексты

`object WinConst` (все `const val …: Int`):

| Имя | Значение | | Имя | Значение |
|---|---|---|---|---|
| `MONITOR_DEFAULTTONULL` | `0x0` | | `MONITOR_DEFAULTTOPRIMARY` | `0x1` |
| `MONITOR_DEFAULTTONEAREST` | `0x2` | | `MONITORINFOF_PRIMARY` | `0x1` |
| `SRCCOPY` | `0x00CC0020` | | `CAPTUREBLT` | `0x40000000` |
| `BI_RGB` | `0` | | `DIB_RGB_COLORS` | `0` |
| `CURSOR_SHOWING` | `0x1` | | `DI_NORMAL` | `0x3` |
| `WM_HOTKEY` | `0x0312` | | `WM_QUIT` | `0x0012` |
| `WM_APP` | `0x8000` | | `PM_NOREMOVE` | `0x0` |
| `CF_DIB` | `8` | | `GMEM_MOVEABLE` | `0x0002` |
| `SWP_NOACTIVATE` | `0x0010` | | `SWP_SHOWWINDOW` | `0x0040` |
| `HWND_TOPMOST_VALUE` | `-1` | | `HWND_MESSAGE_VALUE` | `-3` |
| `WDA_EXCLUDEFROMCAPTURE` | `0x11` | | `MB_YESNO` | `0x4` |
| `MB_ICONWARNING` | `0x30` | | `MB_SETFOREGROUND` | `0x10000` |
| `MB_TOPMOST` | `0x40000` | | `IDYES` | `6` |
| `OFN_HIDEREADONLY` | `0x4` | | `OFN_NOCHANGEDIR` | `0x8` |
| `OFN_PATHMUSTEXIST` | `0x800` | | `OFN_EXPLORER` | `0x80000` |
| `BIF_RETURNONLYFSDIRS` | `0x1` | | `BIF_NEWDIALOGSTYLE` | `0x40` |
| `MAX_PATH` | `260` | | `SAVE_DIALOG_BUFFER_CHARS` | `32768` |
| `COINIT_APARTMENTTHREADED` | `0x2` | 2026-10-29 | Review | Реализованы WinConst, 3 структуры, 5 JNA-интерфейсов, WinApiException, DpiAwareness, WindowHandles и тесты (6 шт.). `gradlew test --tests ...windows.*` - 6 пройдено; `gradlew build` - BUILD SUCCESSFUL. Отклонений нет. Ручная проверка не требуется. Незакоммиченные изменения в `.idea/` не относятся к задаче и не затронуты. |

HWND-константы получаются как `HWND(Pointer.createConstant(WinConst.HWND_TOPMOST_VALUE.toLong()))`.

## 11. Тесты

| # | Класс / метод | Given | When | Then |
|---|---|---|---|---|
| 1 | `jna.StructureSizeTest` / `` `cursorinfo is 24 bytes`() `` | — | `CURSORINFO().size()` и `.cbSize` | `24`, `24` |
| 2 | `` `openfilename is 152 bytes`() `` | — | `OPENFILENAME().size()` | `152` |
| 3 | `` `browseinfo is 64 bytes`() `` | — | `BROWSEINFO().size()` | `64` |
| 4 | `DpiAwarenessTest` / `` `thread enters per monitor v2`() `` `@EnabledOnOs(OS.WINDOWS)` | новый `Thread` (результаты через `AtomicReference`, `join(5000)`) | `enterPerMonitorV2()`, затем `currentThreadAwareness()` | `true`, `2` |
| 5 | `` `kernel32 returns thread id`() `` `@EnabledOnOs(OS.WINDOWS)` | — | `Kernel32Api.INSTANCE.GetCurrentThreadId()` | `!= 0` |
| 6 | `` `user32 cursor pos call succeeds`() `` `@EnabledOnOs(OS.WINDOWS)` | — | `User32Api.INSTANCE.GetCursorPos(POINT())` | `true` |

Тест 2 и 3 — в пакете `…platform.windows.jna` (`T/jna/`).

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.platform.windows.*"` | 6 тестов пройдены |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

## 13. Критерии готовности (Definition of Done)

- [x] Все объявления раздела 6 есть, имена функций — как в WinAPI.
- [x] Размеры структур — 24 / 152 / 64.
- [x] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не использовать `com.sun.jna.platform.win32.User32.INSTANCE` / `GDI32.INSTANCE` вместо своих интерфейсов.
- Не добавлять функции сверх раздела 6.
- Не реализовывать захват, клавиши, буфер.

## 15. Ловушки и подсказки

- Поля структур в Kotlin обязательно `@JvmField var` — иначе JNA их не видит («has unknown or zero size»).
- Имя поля `Flags` в `OPENFILENAME` с заглавной буквы — так в `@Structure.FieldOrder`.
- `POINT.ByValue` — вложенный класс `com.sun.jna.platform.win32.WinDef.POINT.ByValue`.
- В JNA `Pointer.createConstant(-4L)` создаёт указатель-значение без памяти — для «псевдо-handle» это и нужно.
- `Native.getLastError()` нужно читать **сразу** после неудачного вызова, до других JNA-вызовов на этом потоке.

## 16. Журнал выполнения

> Заполняет Dev.

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| 2026-10-29 | Review | Реализованы WinConst, 3 структуры, 5 JNA-интерфейсов, WinApiException, DpiAwareness, WindowHandles и тесты (6 шт.). `gradlew test --tests ...windows.*` - 6 пройдено; `gradlew build` - BUILD SUCCESSFUL. Отклонений нет. Ручная проверка не требуется. Незакоммиченные изменения в `.idea/` не относятся к задаче и не затронуты. |

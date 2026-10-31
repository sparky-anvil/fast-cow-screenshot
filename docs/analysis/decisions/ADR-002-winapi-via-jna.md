# ADR-002: Нативный слой Windows через JNA: GDI-захват, RegisterHotKey, буфер обмена WinAPI, нативные диалоги

| Поле | Значение |
|---|---|
| Статус | Accepted |
| Дата | 2026-10-18 |
| Автор | Tech |
| Связанные | REQ-001 (.2, .3, .7, .9–.12, .19–.26, .34, .38, .43, .44), RES-001 |

## Контекст

RES-001 показал, что AWT `Robot` при HiDPI работает в логических координатах и масштабирует снимок
(нарушение REQ-001.20). Глобальных горячих клавиш в AWT нет. Буфер обмена AWT публикует несколько форматов
(DIB, PNG, JFIF) с синхронным кодированием, и время такой публикации на 4K непредсказуемо (REQ-001.N1).

## Рассмотренные варианты

1. **JNA 5.19.1 (`jna` + `jna-platform`)** — маппинги WinAPI на Kotlin без C-кода.
2. **JNI** — свой C/C++-код и сборка нативной DLL.
3. **Panama FFM (JDK 22+)** — стандартный API, но многословный; нет готовых типов WinAPI.
4. **Только AWT** (`Robot`, `Clipboard`, `FileDialog`).

## Сравнение

| Критерий | JNA | JNI | Panama | AWT |
|---|---|---|---|---|
| Точность HiDPI | Да (физические пиксели) | Да | Да | Нет |
| Сложность | Низкая | Высокая (C-тулчейн) | Средняя | Низкая |
| Готовые типы WinAPI | Да (`jna-platform`) | Нет | Нет | — |
| Производительность одиночных вызовов | Достаточная | Лучшая | Лучшая | — |

## Решение

JNA 5.19.1. Весь WinAPI-код находится в пакете `by.sparkyanvil.fastcowscreenshot.platform.windows`.

- Захват: `EnumDisplayMonitors` / `GetMonitorInfoW` / `MonitorFromPoint` + GDI `BitBlt(SRCCOPY | CAPTUREBLT)` в 32-битную
  DIB-секцию. Поток захвата работает в контексте `DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2`
  (`SetThreadDpiAwarenessContext`).
- Указатель мыши: `GetCursorInfo` + `CopyIcon` + `GetIconInfo` + `DrawIconEx`.
- Горячие клавиши: `RegisterHotKey` на отдельном потоке с циклом `GetMessageW`.
- Буфер обмена: `OpenClipboard` / `EmptyClipboard` / `SetClipboardData(CF_DIB)`, 24 бит, без отложенного рендеринга —
  данные остаются после выхода (REQ-001.26). Владелец — message-only окно на потоке цикла сообщений.
- Окна приложения исключаются из захвата: `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)` (REQ-001.21).
- «Показать в папке»: `SHOpenFolderAndSelectItems`. Known Folders: `SHGetKnownFolderPath`
  (`Shell32Util.getKnownFolderPath`).
- Диалоги: `GetSaveFileNameW` (фильтр `PNG (*.png)`, предзаполненное имя), `SHBrowseForFolderW`
  (`BIF_NEWDIALOGSTYLE`), `MessageBoxW` (подтверждение перезаписи).

## Последствия

- **Положительные:** физические пиксели при любом DPI; конфликт горячей клавиши определяется сразу (REQ-001.3);
  предсказуемое время записи в буфер.
- **Отрицательные / риски:** код только для Windows; ручное освобождение GDI-ресурсов; DRM-контент и exclusive
  fullscreen дают чёрный кадр; при HDR цвета искажаются (ограничение REQ-001 §5); `SHBrowseForFolderW` не поддерживает
  пути длиннее 260 символов (REQ-001.51 — Could, выполняется частично).

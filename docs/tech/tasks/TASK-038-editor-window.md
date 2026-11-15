# TASK-038: Создать окно редактора `EditorWindow` (панель, холст, клавиши, завершение) и подключить его

| Поле | Значение |
|---|---|
| Статус | Ready |
| Приоритет | Must |
| Оценка | L (3–6 ч) |
| Технический дизайн | TDD-004 §3, §4, §6, §7, §10, §12, §13 |
| Требования | REQ-003.7 – 3.13, 3.16, 3.18, 3.20, 3.22, 3.37 – 3.40, 3.45, N1, N6; US-011 AC-2 – AC-7, AC-9, US-013 AC-11, US-014 AC-1 – AC-5 |
| ADR | ADR-006 |
| Зависит от | TASK-037 |
| Блокирует | TASK-039 |

> Перед началом прочитай `docs/tech/conventions.md`. Всё, что не сказано в этой задаче, делается по соглашениям оттуда.
> Если что-то в задаче противоречит соглашениям или коду — не угадывай: поставь статус `Blocked` и опиши проблему в «Журнале выполнения».

## 1. Цель

Создать настоящее окно редактора и заменить им временный `PassThroughEditor`: `ComposeWindow`, развёрнутое на
мониторе под курсором, с панелью инструментов, холстом (снимок, фрагменты, заливка, штрихи, полосы прокрутки),
строкой состояния, подсказками, клавишами, колесом мыши, подтверждением закрытия и выходом из трея с
подтверждением. Рисование и перетаскивание мышью — следующая задача (TASK-039): здесь холст принимает только колесо.

## 2. Контекст

Вся логика — в `EditorController` (TASK-037). Окно:
- создаёт контроллер и передаёт ему лямбды (`showHint`, `confirmDiscard`, `closeWindow`, `onCanvasChanged`,
  `onChromeChanged`, `onPenChanged`);
- рисует состояние контроллера в composable-функциях (`EditorContent.kt`);
- передаёт контроллеру события: размер холста, колесо, кнопки, клавиши (через `EditorKeymap`).

Перерисовка: два счётчика `mutableStateOf(0)` — `canvasTick` (читается **только** внутри блока рисования `Canvas`,
поэтому меняется только картинка холста) и `chromeTick` (читается в панели и строке состояния). Контроллер
увеличивает их через `onCanvasChanged` / `onChromeChanged`.

Снимок для Skia: `EditorImages.toImageBitmap(image)` — один раз при открытии, своё преобразование без лишнего
`IntArray` (экономия ~100 МБ heap на 3 × 4K, REQ-003.N6).

Размещение: `ComposeWindow` создаётся с размером 1280×800, становится displayable (`addNotify()`), затем
`EditorWindowPlacer.moveToWorkArea` переносит его (WinAPI `SetWindowPos`, физические пиксели) в рабочую область
монитора `request.workArea`, затем `extendedState = MAXIMIZED_BOTH` и показ — Windows разворачивает окно на том
мониторе, где оно находится.

Правило conventions §9: кнопки и клавиши вызывают контроллер **отложенно** (`SwingUtilities.invokeLater`), потому что
действие может открыть модальный `JOptionPane` или закрыть окно; закрытие окна (`dispose`) тоже отложенное.

**Выдержки из требований (дословно):**

> **REQ-003.7** Редактор открывается обычным окном с заголовком, значком на панели задач и стандартными кнопками окна. Окно развёрнуто на мониторе, где был курсор в момент нажатия горячей клавиши, и получает фокус ввода.
>
> **REQ-003.8** Окно содержит: холст со снимком; панель инструментов («Перемещение» — только для режима «Несколько областей», «Карандаш», цвет, толщина, «Склеить», «Отменить», «Повторить», масштаб); кнопки «Готово», «Копировать», «Сохранить как…», «Отмена»; размер итогового изображения в физических пикселях (Ш×В).
>
> **REQ-003.9** При открытии снимок вписывается в холст целиком: масштаб `min(100 %, по ширине, по высоте)`. …
>
> **REQ-003.10** Масштаб меняется сочетанием `Ctrl` + колесо мыши (относительно точки под курсором), `Ctrl+0` — вписать в окно, `Ctrl+1` — 100 %. Диапазон — от 10 % до 800 %. Текущий масштаб показывается в процентах. Если снимок больше холста, есть прокрутка (колесо — по вертикали, `Shift` + колесо — по горизонтали).
>
> **REQ-003.11** Масштаб и прокрутка влияют только на отображение. …
>
> **REQ-003.12** Окно редактора не должно попадать в снимки (REQ-001.21).
>
> **REQ-003.13** … При нажатии такой клавиши окно редактора выводится на передний план и показывается подсказка «Завершите редактирование снимка».
>
> **REQ-003.40** «Отмена» (кнопка, `Esc`, закрытие окна) закрывает редактор без результата. … Иначе система спрашивает «Закрыть без сохранения? Изменения будут потеряны.» с вариантами «Закрыть» и «Вернуться».
>
> **REQ-003.45** При выходе из приложения через трей с открытым редактором действует правило REQ-003.40: если есть несохранённые изменения, система спрашивает подтверждение.
>
> **REQ-003 §5 HiDPI:** При масштабе просмотра 100 % один пиксель снимка равен одному физическому пикселю экрана, а не логическому (DIP).
>
> **REQ-003.N1** Редактор со снимком появляется ≤ 500 мс после захвата … для снимка 3840×2160 и ≤ 800 мс для 3 × 4K.
>
> **US-013 AC-11** Given снимок режима «Область» или «Монитор под курсором» When редактор открыт Then инструмента «Перемещение» и кнопки «Склеить» нет.

## 3. Предусловия

- TASK-037: `ui/EditorController.kt` (все методы и константы `MSG_*`, `HINT_MS`), `ui/DragPreview.kt`.
- TASK-035: `ui/EditorKeymap.kt`, `ui/EditorKeyAction.kt`, `editor/Viewport.kt`.
- TASK-036: `capture/ScreenshotEditor.kt`, `capture/EditorRequest.kt`, `capture/EditorHost.kt`,
  `app/PassThroughEditor.kt` (удаляется этой задачей).
- Существуют: `platform/windows/WindowHandles.kt` (`hwndOf(window)`, `excludeFromCapture(window)`),
  `platform/windows/jna/User32Api.kt` (`SetWindowPos(hWnd, hWndInsertAfter: HWND?, x, y, cx, cy, uFlags): Boolean`),
  `platform/windows/jna/WinConst.kt` (`SWP_NOACTIVATE = 0x0010`), `ui/TrayIconImage.kt` (`create(): BufferedImage`),
  `settings/SettingsStore.kt` (`current`, `update`), `settings/EditorSettings.kt`.
- Проверка перед началом: `.\gradlew.bat build` → `BUILD SUCCESSFUL`.

## 4. Область изменений

| # | Файл | Действие | Что именно |
|---|---|---|---|
| 1 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/jna/WinConst.kt` | Изменить | `SWP_NOZORDER` |
| 2 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/platform/windows/EditorWindowPlacer.kt` | Создать | `EditorWindowPlacer` |
| 3 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorImages.kt` | Создать | `EditorImages` |
| 4 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorContent.kt` | Создать | composable-функции окна |
| 5 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorWindow.kt` | Создать | `EditorWindow` |
| 6 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/app/FastCowScreenshotApp.kt` | Изменить | `editorWindow`, `exit()` |
| 7 | `src/main/kotlin/by/sparkyanvil/fastcowscreenshot/app/PassThroughEditor.kt` | Удалить | — |
| 8 | `src/test/kotlin/by/sparkyanvil/fastcowscreenshot/ui/EditorImagesTest.kt` | Создать | тесты 1–2 |

**Файлы, которые изменять нельзя:** все остальные (в том числе `EditorController.kt` — при нехватке API оформи CLR).

## 5. Зависимости

Новых зависимостей нет. Skia (`org.jetbrains.skia.*`) уже приходит с Compose Desktop; по conventions §3 её можно
использовать **только** в `ui/EditorImages.kt`.

## 6. Контракты

```kotlin
// platform/windows/jna/WinConst.kt — добавить рядом с SWP_NOACTIVATE
const val SWP_NOZORDER: Int = 0x0004
```

```kotlin
// platform/windows/EditorWindowPlacer.kt
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** Перенос окна редактора на нужный монитор до показа (TDD-004 §3). Вызывать на EDT. */
internal object EditorWindowPlacer {
    /**
     * SetWindowPos(hwnd, null, area.left + area.width / 8, area.top + area.height / 8,
     *              area.width * 3 / 4, area.height * 3 / 4, SWP_NOZORDER or SWP_NOACTIVATE).
     * window должен быть displayable (после addNotify()).
     * @return true — успех; false — ошибка (WARN "Editor placement failed, GetLastError={}").
     */
    fun moveToWorkArea(window: java.awt.Window, area: PixelRect): Boolean = TODO()
}
```

```kotlin
// ui/EditorImages.kt
package by.sparkyanvil.fastcowscreenshot.ui

/** Преобразование снимка для холста Compose. */
internal object EditorImages {
    /**
     * TYPE_INT_RGB → ImageBitmap (Skia BGRA_8888, OPAQUE). Один промежуточный ByteArray размером w*h*4.
     * @throws IllegalArgumentException если image не TYPE_INT_RGB ("image must be TYPE_INT_RGB").
     * @throws IllegalStateException если Skia не приняла пиксели ("installPixels failed").
     */
    fun toImageBitmap(image: BufferedImage): ImageBitmap = TODO()
}
```

```kotlin
// ui/EditorWindow.kt
package by.sparkyanvil.fastcowscreenshot.ui

/** Окно редактора (TDD-004). Все методы — на EDT. Одновременно открыт не более одного редактора. */
internal class EditorWindow(private val store: SettingsStore) : ScreenshotEditor {
    /** @throws IllegalStateException если редактор уже открыт ("Editor already open"); любые ошибки создания окна. */
    override fun open(request: EditorRequest, host: EditorHost)

    override fun bringToFront()

    /** Для выхода из трея (REQ-003.45): true — редактора нет или он закрыт; false — пользователь выбрал «Вернуться». */
    fun confirmCloseForExit(): Boolean

    companion object {
        const val TITLE: String = "Редактор снимка — fast-cow-screenshot"
        const val INITIAL_WIDTH: Int = 1280
        const val INITIAL_HEIGHT: Int = 800
        const val CONFIRM_TITLE: String = "Редактор снимка"
        const val CONFIRM_TEXT: String = "Закрыть без сохранения? Изменения будут потеряны."
        const val CONFIRM_CLOSE: String = "Закрыть"
        const val CONFIRM_RETURN: String = "Вернуться"
    }
}
```

```kotlin
// ui/EditorContent.kt — composable-функции (internal только EditorContent, остальные private)
@Composable
internal fun EditorContent(
    controller: EditorController,
    bitmap: ImageBitmap,
    canvasTick: State<Int>,
    chromeTick: State<Int>,
    hint: State<String?>,
)
```

**Используемые существующие контракты:** `EditorController` (TASK-037, раздел 6 той задачи), `EditorKeymap.actionFor`,
`WindowHandles.hwndOf/excludeFromCapture`, `User32Api.INSTANCE.SetWindowPos`, `TrayIconImage.create()`,
`SettingsStore.current/update`.

## 7. Пошаговая реализация

### Шаг 1. `WinConst.SWP_NOZORDER` и `EditorWindowPlacer`
- По разделу 6. Реализация — по образцу `OverlayWindowPlacer` (`WindowHandles.hwndOf`, `User32Api.INSTANCE`), без
  `HWND_TOPMOST`: второй аргумент `null`. При `false` — `log.warn("Editor placement failed, GetLastError={}",
  Native.getLastError())`.
- **Результат:** компиляция.

### Шаг 2. `EditorImages`
- По эталонному фрагменту §8. **Результат:** компиляция.

### Шаг 3. `EditorContent.kt`
- По разделу 8 «Раскладка» и «Холст». **Результат:** компиляция.

### Шаг 4. `EditorWindow.kt`
- По разделу 8 «Окно». **Результат:** компиляция.

### Шаг 5. Подключение
- `FastCowScreenshotApp`: добавить свойство `private val editorWindow = EditorWindow(store)` **перед**
  `private val coordinator`; в вызове `CaptureCoordinator(...)` заменить `PassThroughEditor(),` на `editorWindow,`.
- В начале `exit()` (до `log.info("Exiting")`):

```kotlin
if (!editorWindow.confirmCloseForExit()) {
    log.info("Exit cancelled: editor has unsaved changes")
    return
}
```

- Удалить файл `app/PassThroughEditor.kt`.
- **Результат:** `.\gradlew.bat build` проходит.

### Шаг 6. Тест `EditorImagesTest` — раздел 11. Шаг 7. Ручная проверка — раздел 12.

## 8. Алгоритм и логика

### Эталонный фрагмент `EditorImages.toImageBitmap`

```kotlin
require(image.type == BufferedImage.TYPE_INT_RGB && image.raster.dataBuffer is DataBufferInt) { "image must be TYPE_INT_RGB" }
val w = image.width
val h = image.height
val src = (image.raster.dataBuffer as DataBufferInt).data
val bytes = ByteArray(w * h * 4)
val ints = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
for (i in 0 until w * h) ints.put(i, src[i] or OPAQUE_ALPHA)        // 0x00RRGGBB → байты BB GG RR FF
val bitmap = Bitmap()
check(bitmap.installPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE), bytes, w * 4)) { "installPixels failed" }
bitmap.setImmutable()
return bitmap.asComposeImageBitmap()
```

`private const val OPAQUE_ALPHA: Int = 0xFF000000.toInt()`. Импорты: `org.jetbrains.skia.Bitmap`,
`org.jetbrains.skia.ColorAlphaType`, `org.jetbrains.skia.ColorType`, `org.jetbrains.skia.ImageInfo`,
`androidx.compose.ui.graphics.ImageBitmap`, `androidx.compose.ui.graphics.asComposeImageBitmap`,
`java.nio.ByteBuffer`, `java.nio.ByteOrder`.

### Окно (`EditorWindow`)

Приватное состояние: `private var session: Session? = null`, где

```kotlin
private class Session(
    val window: ComposeWindow,
    val controller: EditorController,
    val hint: MutableState<String?>,
    val hintTimer: Timer,           // javax.swing.Timer
)
```

`open(request, host)`:

```
check(session == null) { "Editor already open" }
bitmap = EditorImages.toImageBitmap(request.image)
canvasTick = mutableStateOf(0); chromeTick = mutableStateOf(0); hint = mutableStateOf<String?>(null)
hintTimer = Timer(EditorController.HINT_MS) { hint.value = null }.apply { isRepeats = false }
window = ComposeWindow()
controller = EditorController(
    request, host, store.current.editor,
    onPenChanged = { pen -> store.update { it.copy(editor = pen) } },
    showHint = { text -> hint.value = text; hintTimer.restart() },
    confirmDiscard = { confirmDiscard(window) },
    closeWindow = { closeWindow(window, hintTimer) },
    onCanvasChanged = { canvasTick.value++ },
    onChromeChanged = { chromeTick.value++ },
)
session = Session(window, controller, hint, hintTimer)
try {
    window.title = TITLE
    window.iconImage = TrayIconImage.create()
    window.defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
    window.setSize(INITIAL_WIDTH, INITIAL_HEIGHT)
    window.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent) { SwingUtilities.invokeLater { controller.requestCancel() } }
    })
    window.setContent(onPreviewKeyEvent = { e -> onKey(e, controller) }) {
        EditorContent(controller, bitmap, canvasTick, chromeTick, hint)
    }
    window.addNotify()
    EditorWindowPlacer.moveToWorkArea(window, request.workArea)
    window.extendedState = Frame.MAXIMIZED_BOTH
    window.isVisible = true
    WindowHandles.excludeFromCapture(window)
    window.toFront()
    window.requestFocus()
    log.info("Editor shown in {} ms", (System.nanoTime() - request.startNs) / 1_000_000)
} catch (e: Exception) {
    session = null; hintTimer.stop(); window.dispose(); throw e
}
```

```
private fun onKey(e: KeyEvent, controller: EditorController): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val action = EditorKeymap.actionFor(e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.key.nativeKeyCode) ?: return false
    SwingUtilities.invokeLater { controller.onKey(action) }
    return true
}

private fun confirmDiscard(parent: ComposeWindow): Boolean {
    val options = arrayOf<Any>(CONFIRM_CLOSE, CONFIRM_RETURN)
    val choice = JOptionPane.showOptionDialog(parent, CONFIRM_TEXT, CONFIRM_TITLE, JOptionPane.DEFAULT_OPTION,
        JOptionPane.WARNING_MESSAGE, null, options, CONFIRM_RETURN)
    return choice == 0                       // Esc / крестик диалога = CLOSED_OPTION (-1) = «Вернуться»
}

private fun closeWindow(window: ComposeWindow, timer: Timer) {
    session = null
    SwingUtilities.invokeLater { timer.stop(); window.isVisible = false; window.dispose() }
}

bringToFront():
    s = session ?: return
    if ((s.window.extendedState and Frame.ICONIFIED) != 0) s.window.extendedState = Frame.MAXIMIZED_BOTH
    s.window.toFront(); s.window.requestFocus()
    s.hint.value = EditorController.MSG_FINISH_EDITING; s.hintTimer.restart()

confirmCloseForExit():
    s = session ?: return true
    s.window.toFront()
    return s.controller.closeForExit()
```

`KeyEvent` здесь — `androidx.compose.ui.input.key.KeyEvent`; `isCtrlPressed`, `isAltPressed`, `isShiftPressed`,
`key`, `nativeKeyCode`, `type` — из `androidx.compose.ui.input.key`.

### Раскладка (`EditorContent.kt`)

```
EditorContent:
MaterialTheme {
  Column(Modifier.fillMaxSize().background(PANEL_BG)) {
    EditorToolbar(controller, chromeTick)
    Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(CANVAS_BG)) {
      EditorCanvas(controller, bitmap, canvasTick)
      hint.value?.let { HintBanner(it, Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) }
    }
    EditorStatusBar(controller, chromeTick)
  }
}

EditorToolbar(controller, chromeTick):   chromeTick.value  // подписка на изменения
  Row(Modifier.fillMaxWidth().height(48.dp).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    s = controller.state
    if (controller.isMultiRegion) ToolButton("Перемещение", s.tool == MOVE, s.canMove, 112.dp) { controller.selectTool(MOVE) }
    ToolButton("Карандаш", s.tool == PENCIL, true, 112.dp) { controller.selectTool(PENCIL) }
    Spacer(Modifier.width(12.dp))
    for (rgb in EditorSettings.PALETTE_RGB) ColorSwatch(rgb, controller.penRgb == rgb) { controller.selectColor(rgb) }
    Spacer(Modifier.width(12.dp))
    for (w in EditorSettings.PEN_WIDTHS) ToolButton("$w", controller.penWidth == w, true, 44.dp) { controller.selectWidth(w) }
    Spacer(Modifier.width(12.dp))
    if (controller.isMultiRegion) ToolButton("Склеить", false, s.canMove, 104.dp) { controller.stitch() }
    ToolButton("Отменить", false, s.canUndo, 104.dp) { controller.undo() }
    ToolButton("Повторить", false, s.canRedo, 104.dp) { controller.redo() }
    Spacer(Modifier.width(12.dp))
    ToolButton("Вписать", false, true, 88.dp) { controller.zoomFit() }
    ToolButton("100 %", false, true, 72.dp) { controller.zoom100() }
  }

EditorStatusBar(controller, chromeTick):   chromeTick.value
  Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(controller.statusText, color = Color.White, fontSize = 13.sp, maxLines = 1)
    if (controller.showMoveBlockedHint)
      Text(EditorController.MSG_MOVE_BLOCKED, Modifier.weight(1f), color = WARN_TEXT, fontSize = 13.sp,
           maxLines = 1, overflow = TextOverflow.Ellipsis)
    else Spacer(Modifier.weight(1f))
    ToolButton("Отмена", false, true, 96.dp) { controller.requestCancel() }
    ToolButton("Сохранить как…", false, true, 140.dp) { controller.saveAs() }
    ToolButton("Копировать", false, true, 112.dp) { controller.copy() }
    ToolButton("Готово", true, true, 104.dp) { controller.done() }
  }

ToolButton(text, selected, enabled, width, onClick):
  modifier = Modifier.width(width).height(36.dp).focusProperties { canFocus = false }
  padding = PaddingValues(horizontal = 4.dp)
  click = { SwingUtilities.invokeLater { onClick() } }
  if (selected) Button(click, modifier, enabled = enabled, contentPadding = padding) { Text(text, fontSize = 13.sp, maxLines = 1) }
  else OutlinedButton(click, modifier, enabled = enabled, contentPadding = padding) { Text(text, fontSize = 13.sp, maxLines = 1) }

ColorSwatch(rgb, selected, onClick):
  Box(Modifier.size(28.dp)
      .border(if (selected) 3.dp else 1.dp, if (selected) SELECTED_BORDER else SWATCH_BORDER, RoundedCornerShape(4.dp))
      .padding(4.dp)
      .background(Color(OPAQUE or rgb))
      .focusProperties { canFocus = false }
      .clickable { SwingUtilities.invokeLater { onClick() } })

HintBanner(text, modifier):
  Box(modifier.background(HINT_BG, RoundedCornerShape(6.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
    Text(text, color = Color.White, fontSize = 14.sp)
  }
```

### Холст (`EditorCanvas`)

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EditorCanvas(controller: EditorController, bitmap: ImageBitmap, canvasTick: State<Int>) {
    Canvas(
        Modifier.fillMaxSize()
            .onSizeChanged { controller.onCanvasSize(it.width, it.height) }
            .onPointerEvent(PointerEventType.Scroll) { e ->
                val c = e.changes.first()
                controller.onWheel(c.position.x, c.position.y, c.scrollDelta.x, c.scrollDelta.y, e.keyboardModifiers.isCtrlPressed)
            },
    ) {
        canvasTick.value
        drawDocument(controller, bitmap)
    }
}
```

`drawDocument` (private `DrawScope`-расширение), все координаты в px холста:

```
v = controller.viewport; z = v.zoom.toFloat(); ox = v.offsetX.toFloat(); oy = v.offsetY.toFloat()
st = controller.state; drag = controller.drag
quality = if (z < 1f) FilterQuality.Medium else FilterQuality.None
1. Снимок:
   if (drag == null && st.positions == st.initialPositions):
       drawImage(bitmap, IntOffset.Zero, IntSize(bitmap.width, bitmap.height),
                 IntOffset(ox.roundToInt(), oy.roundToInt()),
                 IntSize((bitmap.width * z).roundToInt(), (bitmap.height * z).roundToInt()), filterQuality = quality)
   else:
       b = controller.displayBounds
       drawRect(Color(OPAQUE or st.fillRgb), Offset(ox + b.left * z, oy + b.top * z), Size(b.width * z, b.height * z))
       для i в st.sources.indices, кроме drag?.index: drawFragment(st.sources[i], st.positions[i])
       если drag != null: drawFragment(st.sources[drag.index], drag.position)
     где drawFragment(src, pos) = drawImage(bitmap, IntOffset(src.left, src.top), IntSize(src.width, src.height),
             IntOffset((ox + pos.x * z).roundToInt(), (oy + pos.y * z).roundToInt()),
             IntSize((src.width * z).roundToInt(), (src.height * z).roundToInt()), filterQuality = quality)
2. Штрихи (отсечение по изображению):
   clipRect(ox, oy, ox + st.width * z, oy + st.height * z) {
       withTransform({ translate(ox, oy); scale(z, z, pivot = Offset.Zero) }) {
           st.strokes.forEach { drawEditorStroke(it) }
           controller.liveStroke?.let { drawEditorStroke(it) }
       }
   }
   drawEditorStroke(s): color = Color(OPAQUE or s.rgb)
       1 точка → drawCircle(color, radius = s.width / 2f, center = Offset(p.x, p.y))
       иначе   → path = Path(); moveTo(первая); lineTo(остальные)
                 drawPath(path, color, style = DrawStroke(width = s.width.toFloat(), cap = StrokeCap.Round, join = StrokeJoin.Round))
3. Полосы прокрутки (только показ):
   cw = size.width; ch = size.height; contentW = st.width * z; contentH = st.height * z
   if (contentW > cw) drawRect(SCROLLBAR, Offset(-ox / contentW * cw, ch - 6f), Size(cw * cw / contentW, 6f))
   if (contentH > ch) drawRect(SCROLLBAR, Offset(cw - 6f, -oy / contentH * ch), Size(6f, ch * ch / contentH))
```

`import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke` (conventions §9).

## 9. Ошибки и граничные случаи

| # | Ситуация | Поведение | Лог | Пользователь |
|---|---|---|---|---|
| 1 | `open` при уже открытом редакторе | `IllegalStateException("Editor already open")` → координатор отдаёт снимок обычным конвейером | `ERROR "Editor failed to open"` (координатор) | обычное уведомление |
| 2 | Ошибка преобразования / создания окна | `session = null`, `dispose`, исключение наружу | как №1 | как №1 |
| 3 | `SetWindowPos` не удался | окно разворачивается там, где создано | `WARN "Editor placement failed, GetLastError=N"` | окно на другом мониторе |
| 4 | `excludeFromCapture` вернул false | продолжить | существующий WARN `WindowHandles` | — |
| 5 | Крестик окна | как «Отмена» (`requestCancel` отложенно) | — | вопрос, если есть изменения |
| 6 | Окно свёрнуто, нажата клавиша захвата | развернуть, вперёд, подсказка | — | `"Завершите редактирование снимка"` |
| 7 | Выход из трея, «Вернуться» | выход отменён | `INFO "Exit cancelled: editor has unsaved changes"` | редактор открыт |
| 8 | Запись цвета/толщины в настройки не удалась | игнорировать (выбор действует до закрытия) | существующий WARN `SettingsStore` | — |

## 10. Константы и тексты

| Имя | Значение | Где |
|---|---|---|
| `TITLE` | `"Редактор снимка — fast-cow-screenshot"` | `EditorWindow` |
| `INITIAL_WIDTH` / `INITIAL_HEIGHT` | `1280` / `800` | `EditorWindow` |
| `CONFIRM_TITLE` | `"Редактор снимка"` | `EditorWindow` |
| `CONFIRM_TEXT` | `"Закрыть без сохранения? Изменения будут потеряны."` | `EditorWindow` |
| `CONFIRM_CLOSE` / `CONFIRM_RETURN` | `"Закрыть"` / `"Вернуться"` | `EditorWindow` |
| `SWP_NOZORDER` | `0x0004` | `WinConst` |
| `OPAQUE` | `0xFF000000.toInt()` (private const в `EditorContent.kt`) | `EditorContent.kt` |
| `PANEL_BG` | `Color(0xFF3C3F41)` | `EditorContent.kt` |
| `CANVAS_BG` | `Color(0xFF2B2B2B)` | `EditorContent.kt` |
| `HINT_BG` | `Color(0xE6202020)` | `EditorContent.kt` |
| `WARN_TEXT` | `Color(0xFFFFCC80)` | `EditorContent.kt` |
| `SELECTED_BORDER` / `SWATCH_BORDER` | `Color(0xFF64B5F6)` / `Color(0xFF888888)` | `EditorContent.kt` |
| `SCROLLBAR` | `Color(0x99FFFFFF)` | `EditorContent.kt` |

Тексты кнопок (дословно): `"Перемещение"`, `"Карандаш"`, `"2"`, `"4"`, `"8"`, `"16"`, `"Склеить"`, `"Отменить"`,
`"Повторить"`, `"Вписать"`, `"100 %"`, `"Отмена"`, `"Сохранить как…"` (символ `…` U+2026), `"Копировать"`, `"Готово"`.

## 11. Тесты

`EditorImagesTest.kt` (класс помечен `@EnabledOnOs(OS.WINDOWS)`):

| # | Метод | Given | Then |
|---|---|---|---|
| 1 | `` `converts pixels exactly`() `` | `BufferedImage(3, 2, TYPE_INT_RGB)`, пиксели по строкам `0xFF0000, 0x00FF00, 0x0000FF / 0x123456, 0xFFFFFF, 0x000000` | `bitmap.width == 3`, `height == 2`; `val map = bitmap.toPixelMap()`; для каждого `(x, y)` `map[x, y].toArgb() == (0xFF000000.toInt() or rgb)` |
| 2 | `` `rejects non rgb image`() `` | `BufferedImage(2, 2, TYPE_INT_ARGB)` | `assertFailsWith<IllegalArgumentException>` |

`toPixelMap` — `androidx.compose.ui.graphics.toPixelMap`, `toArgb` — `androidx.compose.ui.graphics.toArgb`.

## 12. Проверка

| Команда | Ожидаемый результат |
|---|---|
| `.\gradlew.bat test --tests "by.sparkyanvil.fastcowscreenshot.ui.EditorImagesTest"` | Тесты проходят |
| `.\gradlew.bat build` | `BUILD SUCCESSFUL` |

**Ручная** (`.\gradlew.bat run`, флажок «Открывать снимок в редакторе» включён; лучше 2 монитора с разным масштабом):
1. Курсор на втором мониторе → `Ctrl+Shift+1` → окно «Редактор снимка — fast-cow-screenshot» развёрнуто на втором
   мониторе, значок в панели задач, кнопки окна; клавиатурный фокус в окне (сразу работает `Ctrl+0`). В логе
   `Editor shown in N ms`.
2. Панель: «Карандаш», 6 цветов (красный выделен), «2 4 8 16» (4 выделено), «Отменить», «Повторить» (недоступны),
   «Вписать», «100 %»; **нет** «Перемещение» и «Склеить». Внизу `"<Ш> × <В> px · <N> %"` и кнопки «Отмена»,
   «Сохранить как…», «Копировать», «Готово».
3. `Ctrl+Shift+2` при 2+ мониторах → снимок вписан целиком, масштаб < 100 %. `Ctrl+1` → `100 %`; на мониторе 150/200 %
   картинка 1:1 физическим пикселям (мелкий текст как на исходном экране, не увеличен). `Ctrl+0` → вписан.
4. `Ctrl` + колесо → масштаб меняется вокруг точки под курсором, не меньше 10 % и не больше 800 %. При масштабе
   больше окна колесо прокручивает по вертикали, `Shift` + колесо — по горизонтали; полосы прокрутки видны у краёв.
5. Пока редактор открыт, `Ctrl+Shift+3` → область не выделяется, окно редактора выходит вперёд, сверху на ~2 с
   подсказка «Завершите редактирование снимка». Свернуть окно, нажать `Ctrl+Shift+1` → окно разворачивается.
6. Сделать снимок из редактора: `Win+Shift+S` (или `PrintScreen`) — окна редактора на снимке нет (чёрная область или
   то, что под ним, в зависимости от системы).
7. `Ctrl+C` → уведомление «Скопировано в буфер обмена», вставка в Paint — снимок; редактор открыт. `Esc` → закрылся
   без вопроса, новых файлов нет.
8. Новый снимок → `Ctrl+Shift+S` → диалог «Сохранить как»; сохранить `C:\Temp\bug.png` → файл есть, редактор открыт;
   буфер не изменился. Нажать крестик окна → закрылось без вопроса.
9. Новый снимок → `Ctrl+Enter` → окно закрыто, снимок в буфере, файл `Screenshot_<время захвата>.png` в папке по
   умолчанию, уведомление. В логе `Captured … in N ms`. Повторить с «Готово» (кнопка) и `Ctrl+S`.
10. `Ctrl+Shift+4` → 2 области → `Enter` → редактор: на панели есть «Перемещение» (выделено) и «Склеить»; снимок —
    склеенные фрагменты, пустоты цвета заливки.
11. Открыть редактор и выбрать «Выход» в трее → приложение закрылось без вопроса (изменений нет).
12. 10 раз подряд открыть и закрыть редактор (`Ctrl+Shift+2`, `Esc`) → приложение работает, в логе нет ошибок.

## 13. Критерии готовности (Definition of Done)

- [ ] Сигнатуры и константы совпадают с разделами 6 и 10; `PassThroughEditor.kt` удалён.
- [ ] Все кнопки и клавиши вызывают контроллер через `SwingUtilities.invokeLater`.
- [ ] `canvasTick.value` читается только внутри блока рисования `Canvas`.
- [ ] Тесты 1–2 проходят; ручная проверка 1–12 выполнена и записана в журнал.
- [ ] Выполнен общий DoD из `docs/tech/conventions.md`.

## 14. Чего НЕ делать

- Не обрабатывать нажатие/движение/отпускание мыши на холсте, не рисовать рамки и круг карандаша (TASK-039).
- Не использовать `BufferedImage.toComposeImageBitmap()` (лишние 100 МБ heap).
- Не использовать Compose `AlertDialog`/`Dialog` — подтверждение только `JOptionPane`.
- Не переиспользовать окно между открытиями (каждый раз новое, `dispose` при закрытии).
- Не делать окно `alwaysOnTop`.

## 15. Ловушки и подсказки

- Обработчик Compose (клик, клавиша) не должен синхронно открывать модальный диалог или вызывать `dispose()` окна —
  поэтому `invokeLater` в `ToolButton`, `ColorSwatch`, `onKey` и `closeWindow`.
- `focusProperties { canFocus = false }` на кнопках: иначе после клика фокус уходит на кнопку и `Space`/`Enter`
  повторно нажимают её; клавиши редактора всё равно работают (они ловятся в `onPreviewKeyEvent` окна).
- `Modifier.focusProperties` должен стоять **перед** `clickable` в цепочке модификаторов.
- `Color(0x99FFFFFF)` — конструктор `Color(Long)`; `Color(OPAQUE or rgb)` — конструктор `Color(Int)`.
- `scrollDelta` в Compose Desktop = `preciseWheelRotation` (1.0 на щелчок); при `Shift` значение приходит в `x`.
- Позиции указателя в Compose Desktop — логические координаты AWT × масштаб монитора; на 200 % шаг — 2 физических
  пикселя. Это ожидаемо (ADR-006).
- `window.addNotify()` нужен до `EditorWindowPlacer` — без него `WindowHandles.hwndOf` бросит
  `"window is not displayable"`.
- Если в ручной проверке 1 окно открывается не на мониторе под курсором — не подбирай обходной путь: `Blocked` + CLR
  с конфигурацией мониторов и строками лога.
- `Native.getLastError()` вызывай сразу после неудачного `SetWindowPos`.

## 16. Журнал выполнения

| Дата | Статус | Что сделано / отклонения от задачи / проблемы |
|---|---|---|
| | | |

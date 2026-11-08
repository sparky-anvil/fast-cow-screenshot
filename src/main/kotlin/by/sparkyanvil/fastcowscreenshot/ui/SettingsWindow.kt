package by.sparkyanvil.fastcowscreenshot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowHandles
import by.sparkyanvil.fastcowscreenshot.settings.AppSettings
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.KeySlot
import by.sparkyanvil.fastcowscreenshot.settings.MultiRegionSettings
import by.sparkyanvil.fastcowscreenshot.settings.OverlayAction
import by.sparkyanvil.fastcowscreenshot.settings.SettingsStore
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/** Окно настроек. show() — на EDT. */
internal class SettingsWindow(private val controller: SettingsController, private val store: SettingsStore) {
    private val settingsState = mutableStateOf(store.current)
    private val recording = mutableStateOf<HotkeyAction?>(null)
    private var window: ComposeWindow? = null

    init {
        store.addListener { s -> SwingUtilities.invokeLater { settingsState.value = s } }
    }

    /** Создаёт окно при первом вызове, далее — toFront(). */
    fun show() {
        val existing = window
        if (existing != null) {
            existing.isVisible = true
            existing.toFront()
            return
        }
        val w = ComposeWindow().apply {
            title = TITLE
            setSize(WIDTH, HEIGHT)
            defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            addWindowListener(object : WindowAdapter() {
                override fun windowClosing(e: WindowEvent) {
                    cancelRecording()
                }
            })
            setLocationRelativeTo(null)
            setContent { SettingsContent(settingsState.value, controller, recording) }
        }
        window = w
        w.isVisible = true
        WindowHandles.excludeFromCapture(w)
    }

    /** Закрывает и освобождает окно. */
    fun dispose() {
        cancelRecording()
        window?.dispose()
        window = null
    }

    private fun cancelRecording() {
        if (recording.value != null) {
            controller.cancelHotkeyRecording()
            recording.value = null
        }
    }

    companion object {
        const val TITLE: String = "Настройки — fast-cow-screenshot"
        const val WIDTH: Int = 600
        const val HEIGHT: Int = 760
    }
}

private val ERROR_COLOR = Color(0xFFD32F2F)

/** Содержимое окна настроек. */
@Composable
internal fun SettingsContent(
    s: AppSettings,
    controller: SettingsController,
    recording: MutableState<HotkeyAction?>,
) {
    var dirError by remember { mutableStateOf<String?>(null) }
    var hotkeyError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }
    var recordingAction by recording
    var multiError by remember { mutableStateOf<String?>(null) }
    var overlayTarget by remember { mutableStateOf<Pair<OverlayAction, KeySlot>?>(null) }
    var maxText by remember(s.multiRegion.maxRegions) { mutableStateOf(s.multiRegion.maxRegions.toString()) }
    var colorText by remember(s.multiRegion.fillColor) { mutableStateOf(s.multiRegion.fillColor) }
    val anyRecording = recordingAction != null || overlayTarget != null
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(recordingAction, overlayTarget) {
        if (recordingAction != null || overlayTarget != null) focusRequester.requestFocus()
    }

    MaterialTheme {
        Box(
            Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { e ->
                    val target = overlayTarget
                    if (target != null) {
                        if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent true
                        when (
                            val step = OverlayKeyRecorder.onKeyDown(
                                e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.key.nativeKeyCode, e.key == Key.NumPadEnter,
                            )
                        ) {
                            OverlayRecordingStep.Wait -> return@onPreviewKeyEvent true
                            OverlayRecordingStep.Invalid -> multiError = SettingsController.ERR_OVERLAY_INVALID
                            is OverlayRecordingStep.Apply ->
                                multiError = controller.applyOverlayKey(target.first, target.second, step.key)
                        }
                        overlayTarget = null
                        return@onPreviewKeyEvent true
                    }
                    val action = recordingAction
                    if (action == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val step = HotkeyRecorder.onKeyDown(
                        e.isCtrlPressed, e.isAltPressed, e.isShiftPressed, e.key.nativeKeyCode,
                    )
                    when (step) {
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
                },
        ) {
            Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text("Директория по умолчанию", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = s.defaultDirectory,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    Button(onClick = { controller.browseDirectory { dirError = it } }) { Text("Обзор…") }
                    OutlinedButton(onClick = { controller.openDirectory() }) { Text("Открыть папку") }
                    OutlinedButton(onClick = { dirError = controller.resetDirectory() }) { Text("Сбросить") }
                }
                dirError?.let { Text(it, color = ERROR_COLOR) }

                Row {
                    Checkbox(checked = s.includeCursor, onCheckedChange = { generalError = controller.setIncludeCursor(it) })
                    Text("Включать указатель мыши в снимок")
                }
                Row {
                    Checkbox(checked = s.autoSaveEnabled, onCheckedChange = { generalError = controller.setAutoSave(it) })
                    Text("Автоматически сохранять снимки в директорию по умолчанию")
                }

                Text("Горячие клавиши", fontWeight = FontWeight.Bold)
                for (action in HotkeyAction.entries.filter { it != HotkeyAction.MULTI_REGION }) {
                    HotkeyRow(action, s, recording, enabled = true) {
                        recordingAction = action
                        hotkeyError = null
                        controller.beginHotkeyRecording()
                    }
                }
                hotkeyError?.let { Text(it, color = ERROR_COLOR) }
                generalError?.let { Text(it, color = ERROR_COLOR) }

                Text("Несколько областей", fontWeight = FontWeight.Bold)
                HotkeyRow(HotkeyAction.MULTI_REGION, s, recording, enabled = !anyRecording) {
                    recordingAction = HotkeyAction.MULTI_REGION
                    hotkeyError = null
                    controller.beginHotkeyRecording()
                }
                for (a in OverlayAction.entries) {
                    Row {
                        Text(a.displayName, Modifier.width(200.dp))
                        for (slot in KeySlot.entries) {
                            val label = when {
                                overlayTarget == a to slot -> "Нажмите клавишу…"
                                else -> s.multiRegion.key(a, slot).ifEmpty { "—" }
                            }
                            Text(label, Modifier.width(120.dp))
                            if (overlayTarget == a to slot) {
                                OutlinedButton(onClick = { overlayTarget = null }) { Text("Отменить") }
                            } else {
                                OutlinedButton(
                                    enabled = !anyRecording,
                                    onClick = {
                                        multiError = null
                                        overlayTarget = a to slot
                                    },
                                ) { Text("Изменить") }
                            }
                        }
                        OutlinedButton(
                            enabled = !anyRecording && s.multiRegion.key(a, KeySlot.SECONDARY).isNotEmpty(),
                            onClick = { multiError = controller.applyOverlayKey(a, KeySlot.SECONDARY, null) },
                        ) { Text("Очистить") }
                    }
                }
                Row {
                    Text("Максимум областей (2–50)", Modifier.width(200.dp))
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it },
                        singleLine = true,
                        modifier = Modifier.width(100.dp),
                    )
                    Button(onClick = { multiError = controller.setMaxRegions(maxText) }) { Text("Применить") }
                }
                Row {
                    Text("Цвет заливки", Modifier.width(200.dp))
                    Box(
                        Modifier.size(24.dp)
                            .background(Color(0xFF000000 or s.multiRegion.fillRgb().toLong()))
                            .border(1.dp, Color.Gray),
                    )
                    OutlinedTextField(
                        value = colorText,
                        onValueChange = { colorText = it },
                        singleLine = true,
                        modifier = Modifier.width(120.dp),
                    )
                    Button(onClick = { multiError = controller.setFillColor(colorText) }) { Text("Применить") }
                }
                Row {
                    for (hex in SettingsController.FILL_PALETTE) {
                        val rgb = MultiRegionSettings.parseHexColor(hex) ?: continue
                        Box(
                            Modifier.padding(2.dp).size(24.dp)
                                .background(Color(0xFF000000 or rgb.toLong()))
                                .border(1.dp, Color.Gray)
                                .clickable { multiError = controller.setFillColor(hex) },
                        )
                    }
                }
                OutlinedButton(
                    enabled = !anyRecording,
                    onClick = {
                        overlayTarget = null
                        multiError = controller.resetMultiRegion()
                    },
                ) { Text("Сбросить") }
                multiError?.let { Text(it, color = ERROR_COLOR) }
            }
        }
    }
}

@Composable
private fun HotkeyRow(
    action: HotkeyAction,
    s: AppSettings,
    recording: MutableState<HotkeyAction?>,
    enabled: Boolean,
    onStart: () -> Unit,
) {
    Row {
        Text(action.displayName, Modifier.width(200.dp))
        Text(
            if (recording.value == action) "Нажмите сочетание… (Esc — отмена)" else s.hotkeyBinding(action).format(),
            Modifier.width(240.dp),
        )
        OutlinedButton(enabled = enabled, onClick = { onStart() }) { Text("Изменить") }
    }
}

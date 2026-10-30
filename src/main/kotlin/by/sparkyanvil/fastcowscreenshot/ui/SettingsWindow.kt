package by.sparkyanvil.fastcowscreenshot.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowHandles
import by.sparkyanvil.fastcowscreenshot.settings.AppSettings
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.SettingsStore
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import javax.swing.WindowConstants

/** Окно настроек. show() — на EDT. */
internal class SettingsWindow(private val controller: SettingsController, private val store: SettingsStore) {
    private val settingsState = mutableStateOf(store.current)
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
            setLocationRelativeTo(null)
            setContent { SettingsContent(settingsState.value, controller) }
        }
        window = w
        w.isVisible = true
        WindowHandles.excludeFromCapture(w)
    }

    /** Закрывает и освобождает окно. */
    fun dispose() {
        window?.dispose()
        window = null
    }

    companion object {
        const val TITLE: String = "Настройки — fast-cow-screenshot"
        const val WIDTH: Int = 600
        const val HEIGHT: Int = 460
    }
}

private val ERROR_COLOR = Color(0xFFD32F2F)

/** Содержимое окна настроек. */
@Composable
internal fun SettingsContent(s: AppSettings, controller: SettingsController) {
    var dirError by remember { mutableStateOf<String?>(null) }
    var hotkeyError by remember { mutableStateOf<String?>(null) }
    var generalError by remember { mutableStateOf<String?>(null) }
    var recording by remember { mutableStateOf<HotkeyAction?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(recording) {
        if (recording != null) focusRequester.requestFocus()
    }

    MaterialTheme {
        Box(
            Modifier
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { e ->
                    val action = recording
                    if (action == null || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val k = e.nativeKeyEvent as KeyEvent
                    if (k.keyCode == KeyEvent.VK_ESCAPE) {
                        controller.cancelHotkeyRecording()
                        recording = null
                        return@onPreviewKeyEvent true
                    }
                    if (HotkeyRecorder.isModifierKey(k.keyCode)) return@onPreviewKeyEvent true
                    val b = HotkeyRecorder.toBinding(k.isControlDown, k.isAltDown, k.isShiftDown, k.keyCode)
                    if (b == null) {
                        hotkeyError = SettingsController.ERR_INVALID_COMBO
                        controller.cancelHotkeyRecording()
                    } else {
                        hotkeyError = controller.applyHotkey(action, b)
                    }
                    recording = null
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
                for (action in HotkeyAction.entries) {
                    Row {
                        Text(action.displayName, Modifier.width(200.dp))
                        Text(
                            if (recording == action) "Нажмите сочетание… (Esc — отмена)" else s.hotkeyBinding(action).format(),
                            Modifier.width(240.dp),
                        )
                        OutlinedButton(onClick = {
                            recording = action
                            hotkeyError = null
                            controller.beginHotkeyRecording()
                        }) { Text("Изменить") }
                    }
                }
                hotkeyError?.let { Text(it, color = ERROR_COLOR) }
                generalError?.let { Text(it, color = ERROR_COLOR) }
            }
        }
    }
}

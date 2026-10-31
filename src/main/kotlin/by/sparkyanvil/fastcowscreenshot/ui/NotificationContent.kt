package by.sparkyanvil.fastcowscreenshot.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Содержимое окна уведомления. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NotificationContent(model: NotificationModel, onClick: () -> Unit, onSaveAs: () -> Unit) {
    MaterialTheme {
        Surface(color = Color(0xFF2B2B2B), modifier = Modifier.fillMaxSize().clickable(onClick = onClick)) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    model.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                val tooltipText = model.folderTooltip
                model.lines.forEachIndexed { i, line ->
                    if (tooltipText != null && i == model.lines.lastIndex) {
                        TooltipArea(
                            tooltip = {
                                Surface(color = Color(0xFF444444)) {
                                    Text(
                                        tooltipText,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(6.dp),
                                    )
                                }
                            },
                        ) {
                            Text(line, color = Color(0xFFDDDDDD), fontSize = 12.sp, maxLines = 1)
                        }
                    } else {
                        Text(line, color = Color(0xFFDDDDDD), fontSize = 12.sp, maxLines = 1)
                    }
                }
                if (model.showSaveAs) {
                    TextButton(onClick = onSaveAs) {
                        Text(NotificationWindow.SAVE_AS_TEXT, color = Color(0xFF6CB4FF))
                    }
                }
            }
        }
    }
}

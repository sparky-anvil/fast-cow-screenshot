package by.sparkyanvil.fastcowscreenshot.domain

import java.awt.image.BufferedImage
import java.time.LocalDateTime

/**
 * Готовый снимок. image — TYPE_INT_RGB; capturedAt — локальное время нажатия клавиши.
 * Изображение после создания Screenshot не изменяется.
 */
internal data class Screenshot(
    val image: BufferedImage,
    val capturedAt: LocalDateTime,
    val mode: CaptureMode,
)
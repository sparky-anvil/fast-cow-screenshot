package by.sparkyanvil.fastcowscreenshot.capture

/** Запись изображения в буфер обмена. Вызывать на fcs-capture. Не бросает исключений. */
internal interface ClipboardWriter {
    fun write(image: java.awt.image.BufferedImage): ClipboardResult
}
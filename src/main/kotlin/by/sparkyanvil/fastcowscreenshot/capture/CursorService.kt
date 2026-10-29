package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint

/** Указатель мыши. Вызывать на fcs-capture. */
internal interface CursorService {
    /** null — указатель скрыт или ошибка API (WARN в лог). Не бросает. */
    fun snapshot(): CursorSnapshot?

    /**
     * Рисует указатель на image (in-place). imageOrigin — координаты стола пикселя (0,0) изображения.
     * Ошибки WinAPI → WARN, изображение не меняется. Не бросает.
     */
    fun paint(image: java.awt.image.BufferedImage, cursor: CursorSnapshot, imageOrigin: PixelPoint)
}
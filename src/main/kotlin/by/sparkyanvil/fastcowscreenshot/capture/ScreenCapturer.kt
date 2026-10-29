package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop

/** Захват пикселей. Вызывать на fcs-capture. */
internal interface ScreenCapturer {
    /**
     * Изображение TYPE_INT_RGB размера area.width × area.height; пиксель (0,0) = точка (area.left, area.top) стола.
     * Пиксели вне desktop.monitors = GAP_FILL_RGB. @throws CaptureException при сбое WinAPI.
     * @throws IllegalArgumentException если area.isEmpty.
     */
    fun capture(area: PixelRect, desktop: VirtualDesktop): java.awt.image.BufferedImage
}
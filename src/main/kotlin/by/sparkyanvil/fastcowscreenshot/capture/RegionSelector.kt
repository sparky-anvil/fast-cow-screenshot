package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import java.awt.image.BufferedImage

/** Оверлей выбора области. select вызывается на EDT; onResult вызывается на EDT ровно один раз. */
internal interface RegionSelector {
    /** frame — кадр всего стола (пиксель (0,0) = desktop.bounds.left/top). onResult(rect в координатах стола) или onResult(null) при отмене. */
    fun select(frame: BufferedImage, desktop: VirtualDesktop, onResult: (PixelRect?) -> Unit)
}
package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import java.awt.image.BufferedImage

/** Оверлей выбора нескольких областей. select вызывается на EDT; onResult вызывается на EDT ровно один раз. */
internal interface MultiRegionSelector {
    /**
     * frame — кадр всего стола (пиксель (0,0) = desktop.bounds.left/top).
     * onResult(регионы в координатах стола в порядке добавления, не пустой список) или onResult(null) при отмене/ошибке.
     */
    fun select(
        frame: BufferedImage,
        desktop: VirtualDesktop,
        request: MultiRegionRequest,
        onResult: (List<PixelRect>?) -> Unit,
    )
}
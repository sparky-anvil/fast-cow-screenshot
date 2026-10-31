package by.sparkyanvil.fastcowscreenshot.domain

/**
 * Монитор. deviceName — имя устройства Windows ("\\.\DISPLAY1"), bounds — вся площадь,
 * workArea — площадь без панели задач. Всё в физических пикселях.
 */
internal data class MonitorInfo(
    val deviceName: String,
    val bounds: PixelRect,
    val workArea: PixelRect,
    val isPrimary: Boolean,
)
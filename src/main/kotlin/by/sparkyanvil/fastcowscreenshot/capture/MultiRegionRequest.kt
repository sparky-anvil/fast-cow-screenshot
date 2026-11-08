package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.settings.OverlayKeymap

/** Параметры оверлея «Несколько областей», снимок настроек на момент нажатия горячей клавиши. maxRegions в 2..50. */
internal data class MultiRegionRequest(val maxRegions: Int, val keymap: OverlayKeymap)
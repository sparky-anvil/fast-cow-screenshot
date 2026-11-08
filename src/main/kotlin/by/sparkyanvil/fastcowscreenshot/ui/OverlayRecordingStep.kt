package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.settings.OverlayKey

/** Решение по нажатию при записи клавиши оверлея. */
internal sealed interface OverlayRecordingStep {
    /** Нажат только модификатор — ждать. */
    data object Wait : OverlayRecordingStep

    /** Клавиша не поддерживается. */
    data object Invalid : OverlayRecordingStep

    /** Записать клавишу. */
    data class Apply(val key: OverlayKey) : OverlayRecordingStep
}
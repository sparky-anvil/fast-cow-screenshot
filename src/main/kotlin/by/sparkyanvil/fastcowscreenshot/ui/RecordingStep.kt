package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding

/** Что сделать с нажатием клавиши во время записи сочетания. */
internal sealed interface RecordingStep {
    /** Esc: отменить запись. */
    data object Cancel : RecordingStep

    /** Нажат только модификатор: ждать следующую клавишу. */
    data object Wait : RecordingStep

    /** Сочетание недопустимо: показать ERR_INVALID_COMBO и отменить запись. */
    data object Invalid : RecordingStep

    /** Применить сочетание. */
    data class Apply(val binding: HotkeyBinding) : RecordingStep
}

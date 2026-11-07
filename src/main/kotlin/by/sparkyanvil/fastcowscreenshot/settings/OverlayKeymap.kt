package by.sparkyanvil.fastcowscreenshot.settings

/** Соответствие нажатия действию. Неизменяем. */
internal data class OverlayKeymap(
    val confirm: List<OverlayKey>,
    val removeLast: List<OverlayKey>,
    val cancel: List<OverlayKey>,
) {
    /** Список клавиш действия. */
    fun keys(action: OverlayAction): List<OverlayKey> = when (action) {
        OverlayAction.CONFIRM -> confirm
        OverlayAction.REMOVE_LAST -> removeLast
        OverlayAction.CANCEL -> cancel
    }

    /**
     * Первое действие в порядке CONFIRM, REMOVE_LAST, CANCEL, у которого есть клавиша с matches(...) == true; иначе null.
     */
    fun actionFor(ctrl: Boolean, alt: Boolean, shift: Boolean, keyCode: Int, numpad: Boolean): OverlayAction? =
        OverlayAction.entries.firstOrNull { action ->
            keys(action).any { it.matches(ctrl, alt, shift, keyCode, numpad) }
        }

    /** keys(action).firstOrNull()?.format() ?: "—" (длинное тире U+2014). */
    fun primaryName(action: OverlayAction): String = keys(action).firstOrNull()?.format() ?: "\u2014"
}
package by.sparkyanvil.fastcowscreenshot.settings

/** Действие оверлея «Несколько областей». displayName — текст для UI. */
internal enum class OverlayAction(val displayName: String) {
    CONFIRM("Собрать снимок"),
    REMOVE_LAST("Удалить последнюю область"),
    CANCEL("Отмена"),
}
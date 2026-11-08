package by.sparkyanvil.fastcowscreenshot.settings

import kotlinx.serialization.Serializable

/** Настройки режима «Несколько областей» (сохраняются в settings.json). */
@Serializable
internal data class MultiRegionSettings(
    val maxRegions: Int = DEFAULT_MAX_REGIONS,
    val fillColor: String = DEFAULT_FILL_COLOR,
    val confirmKey: String = "Enter",
    val confirmKeyAlt: String = "NumPad Enter",
    val removeLastKey: String = "Backspace",
    val removeLastKeyAlt: String = "Ctrl+Z",
    val cancelKey: String = "Esc",
    val cancelKeyAlt: String = "",
) {
    /** maxRegions.coerceIn(MIN_REGIONS, MAX_REGIONS). */
    fun effectiveMaxRegions(): Int = maxRegions.coerceIn(MIN_REGIONS, MAX_REGIONS)

    /** parseHexColor(fillColor) ?: DEFAULT_FILL_RGB. */
    fun fillRgb(): Int = parseHexColor(fillColor) ?: DEFAULT_FILL_RGB

    /** Строка слота: CONFIRM/PRIMARY → confirmKey, CONFIRM/SECONDARY → confirmKeyAlt и т. п. для всех 6 сочетаний. */
    fun key(action: OverlayAction, slot: KeySlot): String = when (action) {
        OverlayAction.CONFIRM -> if (slot == KeySlot.PRIMARY) confirmKey else confirmKeyAlt
        OverlayAction.REMOVE_LAST -> if (slot == KeySlot.PRIMARY) removeLastKey else removeLastKeyAlt
        OverlayAction.CANCEL -> if (slot == KeySlot.PRIMARY) cancelKey else cancelKeyAlt
    }

    /** Копия с заменённой строкой слота. */
    fun withKey(action: OverlayAction, slot: KeySlot, text: String): MultiRegionSettings = when (action) {
        OverlayAction.CONFIRM ->
            if (slot == KeySlot.PRIMARY) copy(confirmKey = text) else copy(confirmKeyAlt = text)
        OverlayAction.REMOVE_LAST ->
            if (slot == KeySlot.PRIMARY) copy(removeLastKey = text) else copy(removeLastKeyAlt = text)
        OverlayAction.CANCEL ->
            if (slot == KeySlot.PRIMARY) copy(cancelKey = text) else copy(cancelKeyAlt = text)
    }

    /**
     * Клавиша слота: OverlayKey.parse(key(action, slot)).
     * Если результат null и slot == PRIMARY → OverlayKey.parse(defaultKey(action, PRIMARY)) (никогда не null).
     * Если результат null и slot == SECONDARY → null («не назначено»).
     */
    fun resolvedKey(action: OverlayAction, slot: KeySlot): OverlayKey? {
        val parsed = OverlayKey.parse(key(action, slot))
        if (parsed != null || slot == KeySlot.SECONDARY) return parsed
        return OverlayKey.parse(defaultKey(action, KeySlot.PRIMARY))
    }

    /** OverlayKeymap: для каждого действия listOfNotNull(resolvedKey(PRIMARY), resolvedKey(SECONDARY)). */
    fun keymap(): OverlayKeymap {
        fun keysOf(action: OverlayAction) =
            listOfNotNull(resolvedKey(action, KeySlot.PRIMARY), resolvedKey(action, KeySlot.SECONDARY))
        return OverlayKeymap(
            confirm = keysOf(OverlayAction.CONFIRM),
            removeLast = keysOf(OverlayAction.REMOVE_LAST),
            cancel = keysOf(OverlayAction.CANCEL),
        )
    }

    companion object {
        const val MIN_REGIONS: Int = 2
        const val MAX_REGIONS: Int = 50
        const val DEFAULT_MAX_REGIONS: Int = 10
        const val DEFAULT_FILL_COLOR: String = "#808080"
        const val DEFAULT_FILL_RGB: Int = 0x808080

        private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")

        /** Значение по умолчанию слота из MultiRegionSettings() (например, CANCEL/SECONDARY → ""). */
        fun defaultKey(action: OverlayAction, slot: KeySlot): String = MultiRegionSettings().key(action, slot)

        /** "#RRGGBB" (ровно 7 символов, hex в любом регистре) → 0xRRGGBB; иначе null. Без trim. Не бросает. */
        fun parseHexColor(text: String): Int? =
            if (HEX_COLOR.matches(text)) text.substring(1).toInt(16) else null

        /** 0xRRGGBB → "#RRGGBB" заглавными: "#%06X".format(rgb and 0xFFFFFF). */
        fun formatHexColor(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)
    }
}
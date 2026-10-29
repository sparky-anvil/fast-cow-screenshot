package by.sparkyanvil.fastcowscreenshot.settings

/**
 * Сочетание клавиш. keyCode — виртуальный код клавиши Windows.
 * Допустимы только сочетания с Ctrl и/или Alt.
 */
internal data class HotkeyBinding(val ctrl: Boolean, val alt: Boolean, val shift: Boolean, val keyCode: Int) {
    init {
        require(ctrl || alt) { "Ctrl or Alt is required" }
        require(isSupportedKey(keyCode)) { "Unsupported key: $keyCode" }
    }

    /** "Ctrl+Alt+Shift+K": порядок Ctrl, Alt, Shift, клавиша; только включённые модификаторы. */
    fun format(): String = buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        add(keyName(keyCode).orEmpty())
    }.joinToString("+")

    /** Битовая маска для RegisterHotKey: MOD_* включённых модификаторов OR MOD_NOREPEAT. */
    val winModifiers: Int
        get() = (if (ctrl) MOD_CONTROL else 0) or
            (if (alt) MOD_ALT else 0) or
            (if (shift) MOD_SHIFT else 0) or
            MOD_NOREPEAT

    companion object {
        const val MOD_ALT: Int = 0x0001
        const val MOD_CONTROL: Int = 0x0002
        const val MOD_SHIFT: Int = 0x0004
        const val MOD_NOREPEAT: Int = 0x4000

        private val DIGITS = 0x30..0x39
        private val LETTERS = 0x41..0x5A
        private val FUNCTION_KEYS = 0x70..0x7B

        /** 0x30..0x39, 0x41..0x5A, 0x70..0x7B. */
        fun isSupportedKey(keyCode: Int): Boolean =
            keyCode in DIGITS || keyCode in LETTERS || keyCode in FUNCTION_KEYS

        /** "0".."9", "A".."Z", "F1".."F12"; null для неподдерживаемых. */
        fun keyName(keyCode: Int): String? = when (keyCode) {
            in DIGITS, in LETTERS -> keyCode.toChar().toString()
            in FUNCTION_KEYS -> "F${keyCode - FUNCTION_KEYS.first + 1}"
            else -> null
        }

        /** Разбор строки; null, если строка некорректна. Никогда не бросает исключений. */
        fun parse(text: String): HotkeyBinding? {
            val parts = text.split('+').map { it.trim() }
            if (parts.any { it.isEmpty() }) return null
            val keyCode = keyCodeOf(parts.last().uppercase()) ?: return null
            var ctrl = false
            var alt = false
            var shift = false
            for (modifier in parts.dropLast(1)) {
                when (modifier.lowercase()) {
                    "ctrl" -> if (ctrl) return null else ctrl = true
                    "alt" -> if (alt) return null else alt = true
                    "shift" -> if (shift) return null else shift = true
                    else -> return null
                }
            }
            if (!ctrl && !alt) return null
            return HotkeyBinding(ctrl, alt, shift, keyCode)
        }

        /** Значения по умолчанию REQ-001.52: Ctrl+Shift+1 / 2 / 3. */
        fun defaultFor(action: HotkeyAction): HotkeyBinding = HotkeyBinding(
            ctrl = true,
            alt = false,
            shift = true,
            keyCode = when (action) {
                HotkeyAction.MONITOR_UNDER_CURSOR -> '1'.code
                HotkeyAction.ALL_MONITORS -> '2'.code
                HotkeyAction.REGION -> '3'.code
            },
        )

        private fun keyCodeOf(name: String): Int? {
            if (name.length == 1) {
                val code = name[0].code
                return if (code in DIGITS || code in LETTERS) code else null
            }
            if (name.startsWith("F")) {
                val n = name.substring(1).takeIf { s -> s.all { it in '0'..'9' } }?.toIntOrNull() ?: return null
                return if (n in 1..12) FUNCTION_KEYS.first + n - 1 else null
            }
            return null
        }
    }
}

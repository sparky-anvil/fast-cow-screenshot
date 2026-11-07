package by.sparkyanvil.fastcowscreenshot.settings

/**
 * Локальная клавиша оверлея. keyCode — java.awt.event.KeyEvent.VK_* (java.awt не импортировать: константы ниже).
 * numpad = true допустим только для VK_ENTER (клавиша Enter цифрового блока).
 * @throws IllegalArgumentException если !isSupportedKey(keyCode) или numpad && keyCode != VK_ENTER.
 */
internal data class OverlayKey(
    val ctrl: Boolean,
    val alt: Boolean,
    val shift: Boolean,
    val keyCode: Int,
    val numpad: Boolean = false,
) {
    init {
        require(isSupportedKey(keyCode)) { "Unsupported key: $keyCode" }
        require(!numpad || keyCode == VK_ENTER) { "numpad is allowed only for Enter" }
    }

    /** "Ctrl+Alt+Shift+<имя>": порядок Ctrl, Alt, Shift, имя клавиши keyName(keyCode, numpad). */
    fun format(): String = buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        add(keyName(keyCode, numpad).orEmpty())
    }.joinToString("+")

    /** true, если все пять значений равны полям. Не бросает (для неподдерживаемых кодов — false). */
    fun matches(ctrl: Boolean, alt: Boolean, shift: Boolean, keyCode: Int, numpad: Boolean): Boolean =
        this.ctrl == ctrl && this.alt == alt && this.shift == shift &&
            this.keyCode == keyCode && this.numpad == numpad

    companion object {
        const val VK_BACK_SPACE: Int = 8
        const val VK_ENTER: Int = 10
        const val VK_ESCAPE: Int = 27
        const val VK_SPACE: Int = 32
        const val VK_DELETE: Int = 127

        private val DIGITS = 0x30..0x39
        private val LETTERS = 0x41..0x5A
        private val FUNCTION_KEYS = 0x70..0x7B

        /** 0x30..0x39, 0x41..0x5A, 0x70..0x7B, VK_BACK_SPACE, VK_ENTER, VK_ESCAPE, VK_SPACE, VK_DELETE. */
        fun isSupportedKey(keyCode: Int): Boolean =
            keyCode in DIGITS || keyCode in LETTERS || keyCode in FUNCTION_KEYS ||
                keyCode == VK_BACK_SPACE || keyCode == VK_ENTER || keyCode == VK_ESCAPE ||
                keyCode == VK_SPACE || keyCode == VK_DELETE

        /** Имя клавиши (таблица раздела 10) или null для неподдерживаемых. numpad учитывается только для VK_ENTER. */
        fun keyName(keyCode: Int, numpad: Boolean): String? = when {
            keyCode in DIGITS || keyCode in LETTERS -> keyCode.toChar().toString()
            keyCode in FUNCTION_KEYS -> "F${keyCode - 0x70 + 1}"
            keyCode == VK_ENTER -> if (numpad) "NumPad Enter" else "Enter"
            keyCode == VK_BACK_SPACE -> "Backspace"
            keyCode == VK_ESCAPE -> "Esc"
            keyCode == VK_SPACE -> "Space"
            keyCode == VK_DELETE -> "Delete"
            else -> null
        }

        /**
         * Разбор строки format(). Пустая строка или строка из пробелов → null. Регистр не важен ("ctrl+z", "numpad enter").
         * Модификаторы — "Ctrl", "Alt", "Shift", каждый не более одного раза. Никогда не бросает.
         */
        fun parse(text: String): OverlayKey? {
            if (text.isBlank()) return null
            val parts = text.split('+').map { it.trim() }
            if (parts.any { it.isEmpty() }) return null
            val (keyCode, numpad) = parseKeyName(parts.last()) ?: return null
            var ctrl = false
            var alt = false
            var shift = false
            for (mod in parts.dropLast(1)) {
                when (mod.lowercase()) {
                    "ctrl" -> if (ctrl) return null else ctrl = true
                    "alt" -> if (alt) return null else alt = true
                    "shift" -> if (shift) return null else shift = true
                    else -> return null
                }
            }
            return OverlayKey(ctrl, alt, shift, keyCode, numpad)
        }

        private fun parseKeyName(name: String): Pair<Int, Boolean>? {
            val special = when {
                name.equals("Enter", ignoreCase = true) -> VK_ENTER to false
                name.equals("NumPad Enter", ignoreCase = true) -> VK_ENTER to true
                name.equals("Backspace", ignoreCase = true) -> VK_BACK_SPACE to false
                name.equals("Esc", ignoreCase = true) -> VK_ESCAPE to false
                name.equals("Space", ignoreCase = true) -> VK_SPACE to false
                name.equals("Delete", ignoreCase = true) -> VK_DELETE to false
                else -> null
            }
            if (special != null) return special
            if (name.length == 1) {
                val code = name.uppercase()[0].code
                return if (code in DIGITS || code in LETTERS) code to false else null
            }
            if (name.length in 2..3 && (name[0] == 'F' || name[0] == 'f')) {
                val n = name.substring(1).toIntOrNull()
                if (n != null && n in 1..12 && name.substring(1).all { it.isDigit() }) return (0x70 + n - 1) to false
            }
            return null
        }
    }
}
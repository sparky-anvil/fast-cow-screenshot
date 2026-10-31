package by.sparkyanvil.fastcowscreenshot.domain

/** Сокращение строк. */
internal object TextEllipsis {
    const val ELLIPSIS: String = "…" // U+2026

    private const val MIN_CHARS: Int = 5

    /**
     * Если text.length <= maxChars — text без изменений. Иначе head + "…" + tail,
     * где head = (maxChars - 1) / 2 первых символов, tail = maxChars - 1 - head последних.
     * @throws IllegalArgumentException если maxChars < 5.
     */
    fun middle(text: String, maxChars: Int): String {
        require(maxChars >= MIN_CHARS) { "maxChars must be >= $MIN_CHARS: $maxChars" }
        if (text.length <= maxChars) return text
        val head = (maxChars - 1) / 2
        val tail = maxChars - 1 - head
        return text.take(head) + ELLIPSIS + text.takeLast(tail)
    }
}
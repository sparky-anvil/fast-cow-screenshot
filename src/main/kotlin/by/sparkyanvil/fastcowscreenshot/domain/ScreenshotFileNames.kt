package by.sparkyanvil.fastcowscreenshot.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Имена файлов автосохранения. Без состояния, потокобезопасен. */
internal object ScreenshotFileNames {
    const val PREFIX: String = "Screenshot_"
    const val EXTENSION: String = ".png"
    const val MAX_SUFFIX: Int = 9999
    const val TIMESTAMP_PATTERN: String = "yyyy-MM-dd_HH-mm-ss"

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern(TIMESTAMP_PATTERN)

    /** "Screenshot_2026-10-18_20-00-48" (без расширения). */
    fun baseName(capturedAt: LocalDateTime): String = PREFIX + formatter.format(capturedAt)

    /**
     * index = 0 -> "Screenshot_...48.png"; index = n >= 1 -> "Screenshot_...48_n.png".
     * @throws IllegalArgumentException если index !in 0..MAX_SUFFIX.
     */
    fun candidate(capturedAt: LocalDateTime, index: Int): String {
        require(index in 0..MAX_SUFFIX) { "index must be in 0..$MAX_SUFFIX: $index" }
        val base = baseName(capturedAt)
        return if (index == 0) base + EXTENSION else base + "_" + index + EXTENSION
    }
}
package by.sparkyanvil.fastcowscreenshot.platform.windows

/** Повтор действия с паузами. */
internal object ClipboardRetry {
    const val MAX_ATTEMPTS: Int = 8
    const val DELAY_MS: Long = 100

    /**
     * Вызывает action(1), action(2)… до первого не-null результата или до maxAttempts.
     * Между попытками — sleep(delayMs); после последней паузы нет. Возвращает результат или null.
     */
    fun <T : Any> attempt(
        maxAttempts: Int = MAX_ATTEMPTS,
        delayMs: Long = DELAY_MS,
        sleep: (Long) -> Unit = { Thread.sleep(it) },
        action: (attempt: Int) -> T?,
    ): T? {
        for (n in 1..maxAttempts) {
            val result = action(n)
            if (result != null) return result
            if (n < maxAttempts) sleep(delayMs)
        }
        return null
    }
}
package by.sparkyanvil.fastcowscreenshot.capture

/** Ожидаемый сбой захвата (WinAPI, нет мониторов). */
internal class CaptureException(message: String, cause: Throwable? = null) : Exception(message, cause)
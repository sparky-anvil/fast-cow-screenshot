package by.sparkyanvil.fastcowscreenshot.platform.windows

import com.sun.jna.Native

/** Сбой WinAPI-вызова. */
internal class WinApiException(message: String) : RuntimeException(message) {
    companion object {
        /** Создаёт исключение с кодом GetLastError. Вызывать сразу после неудачного вызова. */
        fun lastError(function: String): WinApiException =
            WinApiException("$function failed, GetLastError=${Native.getLastError()}")
    }
}
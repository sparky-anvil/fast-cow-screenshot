package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import com.sun.jna.Native
import com.sun.jna.Pointer
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(DpiAwareness::class.java)

/** DPI awareness текущего потока. Потокобезопасен (действует на вызывающий поток). */
internal object DpiAwareness {
    /** DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = (HANDLE)-4. */
    val PER_MONITOR_AWARE_V2: Pointer = Pointer.createConstant(-4L)

    /** Значение DPI_AWARENESS_PER_MONITOR_AWARE. */
    const val AWARENESS_PER_MONITOR: Int = 2

    /** Переводит текущий поток в Per-Monitor V2. `true` — успех. */
    fun enterPerMonitorV2(): Boolean {
        val previous: Pointer? = User32Api.INSTANCE.SetThreadDpiAwarenessContext(PER_MONITOR_AWARE_V2)
        if (previous == null) {
            log.warn("SetThreadDpiAwarenessContext failed, GetLastError={}", Native.getLastError())
            return false
        }
        return true
    }

    /** DPI awareness текущего потока; -1, если контекст получить не удалось. */
    fun currentThreadAwareness(): Int {
        val context: Pointer = User32Api.INSTANCE.GetThreadDpiAwarenessContext() ?: return -1
        return User32Api.INSTANCE.GetAwarenessFromDpiAwarenessContext(context)
    }
}
package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.Kernel32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import com.sun.jna.platform.win32.WinDef.POINT
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DpiAwarenessTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `thread enters per monitor v2`() {
        val entered = AtomicReference<Boolean?>()
        val awareness = AtomicReference<Int?>()
        val thread = Thread {
            entered.set(DpiAwareness.enterPerMonitorV2())
            awareness.set(DpiAwareness.currentThreadAwareness())
        }
        thread.start()
        thread.join(5000)
        assertEquals(true, entered.get())
        assertEquals(DpiAwareness.AWARENESS_PER_MONITOR, awareness.get())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `kernel32 returns thread id`() {
        assertNotEquals(0, Kernel32Api.INSTANCE.GetCurrentThreadId())
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `user32 cursor pos call succeeds`() {
        assertTrue(User32Api.INSTANCE.GetCursorPos(POINT()))
    }
}
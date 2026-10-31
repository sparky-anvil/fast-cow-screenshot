package by.sparkyanvil.fastcowscreenshot.platform.windows

import com.sun.jna.Pointer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFails
import kotlin.test.assertNotEquals

@EnabledOnOs(OS.WINDOWS)
class NativeMessageLoopTest {
    private fun withLoop(block: (NativeMessageLoop) -> Unit) {
        val loop = NativeMessageLoop()
        loop.start()
        try {
            block(loop)
        } finally {
            loop.stop()
        }
    }

    @Test
    fun `invoke runs on loop thread`() = withLoop { loop ->
        assertEquals("fcs-hotkeys", loop.invoke { Thread.currentThread().name })
    }

    @Test
    fun `invoke propagates exception`() = withLoop { loop ->
        val e = assertFailsWith<IllegalStateException> { loop.invoke { throw IllegalStateException("x") } }
        assertEquals("x", e.message)
    }

    @Test
    fun `message window is created`() = withLoop { loop ->
        assertNotEquals(0L, Pointer.nativeValue(loop.messageWindow.pointer))
    }

    @Test
    fun `stop terminates thread`() {
        val loop = NativeMessageLoop()
        loop.start()
        loop.stop()
        assertFails { loop.invoke { 1 } }
    }
}
package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class GdiCaptureIntegrationTest {
    /** Выполняет блок в новом потоке с Per-Monitor V2; пробрасывает исключение блока. */
    private fun <T : Any> onCaptureThread(block: () -> T): T {
        val result = AtomicReference<T?>()
        val error = AtomicReference<Throwable?>()
        val thread = Thread {
            DpiAwareness.enterPerMonitorV2()
            try {
                result.set(block())
            } catch (e: Exception) {
                error.set(e)
            }
        }
        thread.start()
        thread.join(10000)
        error.get()?.let { throw it }
        return result.get() ?: error("capture thread produced no result")
    }

    @Test
    fun `desktop has exactly one primary monitor`() {
        val count = onCaptureThread { WindowsMonitorProvider().desktop().monitors.count { it.isPrimary } }
        assertEquals(1, count)
    }

    @Test
    fun `primary capture has monitor size`() {
        val size = onCaptureThread {
            val d = WindowsMonitorProvider().desktop()
            val image = GdiScreenCapturer().capture(d.primary.bounds, d)
            listOf(image.width, image.height, d.primary.bounds.width, d.primary.bounds.height)
        }
        assertEquals(size[2], size[0])
        assertEquals(size[3], size[1])
    }

    @Test
    fun `writes manual check images`() {
        val dir = File("build/manual-check").apply { mkdirs() }
        onCaptureThread {
            val d = WindowsMonitorProvider().desktop()
            val capturer = GdiScreenCapturer()
            ImageIO.write(capturer.capture(d.primary.bounds, d), "png", File(dir, "primary.png"))
            ImageIO.write(capturer.capture(d.bounds, d), "png", File(dir, "all-monitors.png"))
            val image = capturer.capture(d.bounds, d)
            val service = WindowsCursorService()
            service.snapshot()?.use { service.paint(image, it, PixelPoint(d.bounds.left, d.bounds.top)) }
            ImageIO.write(image, "png", File(dir, "all-monitors-cursor.png"))
            true
        }
        listOf("primary.png", "all-monitors.png", "all-monitors-cursor.png").forEach {
            assertTrue(File(dir, it).exists(), "$it must exist")
        }
    }

    @Test
    fun `cursor snapshot can be closed twice`() {
        onCaptureThread {
            val snapshot = WindowsCursorService().snapshot()
            snapshot?.use { }
            snapshot?.close()
            true
        }
    }
}
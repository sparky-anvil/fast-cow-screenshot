package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.CaptureMode
import by.sparkyanvil.fastcowscreenshot.domain.Screenshot
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.time.LocalDateTime
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LastScreenshotHolderTest {
    @Test
    fun `set notifies listeners via executor`() {
        val holder = LastScreenshotHolder(Executor { it.run() })
        val received = mutableListOf<Screenshot>()
        holder.addListener { received.add(it) }
        val s = Screenshot(BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), LocalDateTime.now(), CaptureMode.REGION)
        holder.set(s)
        assertEquals(listOf(s), received)
        assertEquals(s, holder.get())
    }

    @Test
    fun `initially empty`() {
        assertNull(LastScreenshotHolder(Executor { it.run() }).get())
    }
}
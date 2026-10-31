package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.capture.ClipboardResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WindowsClipboardWriterTest {
    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `written image can be read back`() {
        val loop = NativeMessageLoop()
        loop.start()
        try {
            val image = BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB)
            image.setRGB(10, 10, 0x336699)
            val result = WindowsClipboardWriter({ loop.messageWindow }).write(image)
            assertIs<ClipboardResult.Success>(result)

            val read = Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.imageFlavor) as Image
            val copy = BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB)
            val g = copy.createGraphics()
            g.drawImage(read, 0, 0, null)
            g.dispose()
            assertEquals(300, read.getWidth(null))
            assertEquals(200, read.getHeight(null))
            assertEquals(0x336699, copy.getRGB(10, 10) and 0xFFFFFF)
        } finally {
            loop.stop()
        }
    }
}
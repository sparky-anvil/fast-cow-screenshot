package by.sparkyanvil.fastcowscreenshot.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PngFileWriterTest {
    @TempDir
    lateinit var tempDir: Path

    private val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB).apply { setRGB(5, 5, 0x336699) }

    private fun tmpFiles(): List<Path> =
        Files.list(tempDir).use { s -> s.filter { it.fileName.toString().endsWith(".tmp") }.toList() }

    @Test
    fun `writes readable png`() {
        val target = tempDir.resolve("a.png")
        PngFileWriter().writeAtomically(image, target, false)
        val read = assertNotNull(ImageIO.read(target.toFile()))
        assertEquals(40, read.width)
        assertEquals(30, read.height)
        assertEquals(0x336699, read.getRGB(5, 5) and 0xFFFFFF)
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun `does not overwrite without flag`() {
        val target = tempDir.resolve("a.png")
        Files.writeString(target, "old")
        assertFailsWith<FileAlreadyExistsException> { PngFileWriter().writeAtomically(image, target, false) }
        assertEquals("old", Files.readString(target))
        assertTrue(tmpFiles().isEmpty())
    }

    @Test
    fun `replaces with flag`() {
        val target = tempDir.resolve("a.png")
        Files.writeString(target, "old")
        PngFileWriter().writeAtomically(image, target, true)
        assertNotNull(ImageIO.read(target.toFile()))
    }
}
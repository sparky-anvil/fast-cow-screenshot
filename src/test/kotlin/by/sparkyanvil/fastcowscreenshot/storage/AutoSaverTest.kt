package by.sparkyanvil.fastcowscreenshot.storage

import io.mockk.just
import io.mockk.Runs
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutoSaverTest {
    @TempDir
    lateinit var tempDir: Path

    private val image = BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB).apply { setRGB(5, 5, 0x336699) }
    private val t = LocalDateTime.of(2026, 10, 18, 20, 0, 48)
    private val baseName = "Screenshot_2026-10-18_20-00-48"

    @Test
    fun `creates missing cyrillic directory`() {
        val dir = tempDir.resolve("Изображения").resolve("Снимки экрана")
        val result = AutoSaver().save(image, t, dir)
        val expected = dir.resolve("$baseName.png")
        assertEquals(SaveResult.Saved(expected), result)
        assertTrue(Files.exists(expected))
    }

    @Test
    fun `adds suffix on collision`() {
        Files.writeString(tempDir.resolve("$baseName.png"), "x")
        val result = AutoSaver().save(image, t, tempDir)
        assertEquals(SaveResult.Saved(tempDir.resolve("${baseName}_1.png")), result)
        assertEquals("x", Files.readString(tempDir.resolve("$baseName.png")))
    }

    @Test
    fun `race on move picks next index`() {
        val writer = mockk<PngFileWriter>()
        every {
            writer.writeAtomically(any(), match { it.fileName.toString().endsWith("48.png") }, false)
        } throws FileAlreadyExistsException("x")
        every {
            writer.writeAtomically(any(), match { !it.fileName.toString().endsWith("48.png") }, false)
        } just Runs
        val result = AutoSaver(writer).save(image, t, tempDir)
        assertEquals(SaveResult.Saved(tempDir.resolve("${baseName}_1.png")), result)
    }

    @Test
    fun `access denied gives failed result`() {
        val writer = mockk<PngFileWriter>()
        every { writer.writeAtomically(any(), any(), any()) } throws AccessDeniedException("x")
        val result = AutoSaver(writer).save(image, t, tempDir)
        assertEquals(SaveResult.Failed(tempDir, "Нет прав на запись в папку"), result)
    }
}
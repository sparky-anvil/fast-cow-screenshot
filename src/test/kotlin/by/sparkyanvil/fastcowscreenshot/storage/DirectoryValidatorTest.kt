package by.sparkyanvil.fastcowscreenshot.storage

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectoryValidatorTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `existing temp dir is writable`() {
        assertTrue(DirectoryValidator.isWritable(tempDir))
        Files.list(tempDir).use { assertTrue(it.findAny().isEmpty) }
    }

    @Test
    fun `missing dir is not writable`() {
        assertFalse(DirectoryValidator.isWritable(tempDir.resolve("nope")))
    }

    @Test
    fun `file is not writable dir`() {
        val file = tempDir.resolve("f.txt")
        Files.writeString(file, "x")
        assertFalse(DirectoryValidator.isWritable(file))
    }
}
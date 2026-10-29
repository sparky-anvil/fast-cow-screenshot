package by.sparkyanvil.fastcowscreenshot.storage

import org.junit.jupiter.api.Test
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException
import kotlin.test.assertEquals

class IoErrorMessagesTest {
    @Test
    fun `maps known errors`() {
        assertEquals(IoErrorMessages.ACCESS_DENIED, IoErrorMessages.describe(AccessDeniedException("p")))
        assertEquals(
            IoErrorMessages.DISK_FULL,
            IoErrorMessages.describe(FileSystemException("p", null, "There is not enough space on the disk")),
        )
        assertEquals(IoErrorMessages.UNAVAILABLE, IoErrorMessages.describe(NoSuchFileException("p")))
        assertEquals(
            IoErrorMessages.UNAVAILABLE,
            IoErrorMessages.describe(FileSystemException("p", null, "The device is not ready")),
        )
        assertEquals("Ошибка записи: boom", IoErrorMessages.describe(IOException("boom")))
    }
}
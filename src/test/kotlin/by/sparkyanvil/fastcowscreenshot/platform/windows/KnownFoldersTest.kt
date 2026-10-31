package by.sparkyanvil.fastcowscreenshot.platform.windows

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.slf4j.LoggerFactory
import kotlin.test.assertTrue

class KnownFoldersTest {
    private val log = LoggerFactory.getLogger(KnownFoldersTest::class.java)

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `returns absolute path`() {
        val dir = KnownFolders().initialDirectory()
        log.info("Initial directory: {}", dir)
        assertTrue(dir.isAbsolute)
    }
}
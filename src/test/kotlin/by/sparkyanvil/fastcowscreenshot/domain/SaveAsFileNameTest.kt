package by.sparkyanvil.fastcowscreenshot.domain

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertEquals

class SaveAsFileNameTest {
    @Test
    fun `appends png to plain name`() {
        assertEquals("login-error.png", SaveAsFileName.normalizeName("login-error"))
    }

    @Test
    fun `appends png to other extension`() {
        assertEquals("login.jpg.png", SaveAsFileName.normalizeName("login.jpg"))
    }

    @Test
    fun `appends png to dotted name`() {
        assertEquals("v1.2.png", SaveAsFileName.normalizeName("v1.2"))
    }

    @Test
    fun `keeps uppercase PNG`() {
        assertEquals("shot.PNG", SaveAsFileName.normalizeName("shot.PNG"))
    }

    @Test
    fun `normalizes last path element only`() {
        assertEquals(
            Path.of("D:\\Projects\\Bug-42\\login-error.png"),
            SaveAsFileName.normalize(Path.of("D:\\Projects\\Bug-42\\login-error")),
        )
    }
}
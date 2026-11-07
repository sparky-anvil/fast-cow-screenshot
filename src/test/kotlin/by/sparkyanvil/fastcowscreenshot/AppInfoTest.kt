package by.sparkyanvil.fastcowscreenshot

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppInfoTest {
    private val missingMessage = "app-info.properties: version is missing"

    @Test
    fun `app name is fast-cow-screenshot`() {
        assertEquals("fast-cow-screenshot", AppInfo.NAME)
    }

    @Test
    fun `version from resources is semver with optional snapshot`() {
        assertTrue(Regex("""^\d+\.\d+\.\d+(-SNAPSHOT)?$""").matches(AppInfo.VERSION), AppInfo.VERSION)
    }

    @Test
    fun `parses version`() {
        assertEquals("0.0.1", AppInfo.parseVersion("version=0.0.1\n".byteInputStream()))
    }

    @Test
    fun `trims version value`() {
        assertEquals("0.0.2-SNAPSHOT", AppInfo.parseVersion("version = 0.0.2-SNAPSHOT   \n".byteInputStream()))
    }

    @Test
    fun `fails when resource is missing`() {
        val e = assertFailsWith<IllegalStateException> { AppInfo.parseVersion(null) }
        assertEquals(missingMessage, e.message)
    }

    @Test
    fun `fails when version key is absent`() {
        val e = assertFailsWith<IllegalStateException> { AppInfo.parseVersion("name=x\n".byteInputStream()) }
        assertEquals(missingMessage, e.message)
    }

    @Test
    fun `fails when version is blank`() {
        assertFailsWith<IllegalStateException> { AppInfo.parseVersion("version=   \n".byteInputStream()) }
    }

    @Test
    fun `fails when placeholder was not expanded`() {
        assertFailsWith<IllegalStateException> { AppInfo.parseVersion("version=\${version}\n".byteInputStream()) }
    }
}
package by.sparkyanvil.fastcowscreenshot

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AppInfoTest {
    @Test
    fun `app name is fast-cow-screenshot`() {
        assertEquals("fast-cow-screenshot", AppInfo.NAME)
    }

    @Test
    fun `version is 1_0_0`() {
        assertEquals("1.0.0", AppInfo.VERSION)
    }
}
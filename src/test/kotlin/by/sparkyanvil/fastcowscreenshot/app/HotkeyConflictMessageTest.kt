package by.sparkyanvil.fastcowscreenshot.app

import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class HotkeyConflictMessageTest {
    @Test
    fun `conflict line names combination and mode`() {
        val action = HotkeyAction.MONITOR_UNDER_CURSOR
        assertEquals(
            "Ctrl+Shift+1 («Монитор под курсором») — занято другим приложением",
            FastCowScreenshotApp.conflictLine(HotkeyBinding.defaultFor(action), action),
        )
    }
}

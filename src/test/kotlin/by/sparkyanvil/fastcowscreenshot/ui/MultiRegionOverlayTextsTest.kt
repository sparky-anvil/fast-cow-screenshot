package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.settings.MultiRegionSettings
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MultiRegionOverlayTextsTest {
    @Test
    fun `keys hint uses default primary keys`() {
        assertEquals(
            "Enter — собрать · Backspace — удалить последнюю · Esc — отмена",
            MultiRegionOverlay.keysHint(MultiRegionSettings().keymap()),
        )
    }

    @Test
    fun `keys hint follows reassigned keys`() {
        val keymap = MultiRegionSettings(confirmKey = "Space", cancelKey = "Ctrl+Q").keymap()
        assertEquals(
            "Space — собрать · Backspace — удалить последнюю · Ctrl+Q — отмена",
            MultiRegionOverlay.keysHint(keymap),
        )
    }

    @Test
    fun `limit message`() {
        assertEquals(
            "Достигнут лимит: 10 областей. Enter — собрать снимок",
            MultiRegionOverlay.limitMessage(10, MultiRegionSettings().keymap()),
        )
    }
}
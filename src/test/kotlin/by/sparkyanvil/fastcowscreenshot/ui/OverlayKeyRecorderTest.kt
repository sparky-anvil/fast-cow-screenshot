package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.settings.OverlayKey
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import kotlin.test.assertEquals

class OverlayKeyRecorderTest {
    @Test
    fun `modifier key waits`() {
        assertEquals(
            OverlayRecordingStep.Wait,
            OverlayKeyRecorder.onKeyDown(true, false, false, KeyEvent.VK_CONTROL, false),
        )
    }

    @Test
    fun `escape is a regular key`() {
        assertEquals(
            OverlayRecordingStep.Apply(OverlayKey(false, false, false, 27)),
            OverlayKeyRecorder.onKeyDown(false, false, false, 27, false),
        )
    }

    @Test
    fun `numpad enter is recorded`() {
        assertEquals(
            OverlayRecordingStep.Apply(OverlayKey(false, false, false, 10, true)),
            OverlayKeyRecorder.onKeyDown(false, false, false, 10, true),
        )
    }

    @Test
    fun `ctrl z is recorded`() {
        assertEquals(
            OverlayRecordingStep.Apply(OverlayKey(true, false, false, 0x5A)),
            OverlayKeyRecorder.onKeyDown(true, false, false, 0x5A, false),
        )
    }

    @Test
    fun `unsupported key is invalid`() {
        assertEquals(
            OverlayRecordingStep.Invalid,
            OverlayKeyRecorder.onKeyDown(false, false, false, KeyEvent.VK_TAB, false),
        )
        assertEquals(
            OverlayRecordingStep.Invalid,
            OverlayKeyRecorder.onKeyDown(false, false, false, KeyEvent.VK_NUMPAD0, true),
        )
    }
}
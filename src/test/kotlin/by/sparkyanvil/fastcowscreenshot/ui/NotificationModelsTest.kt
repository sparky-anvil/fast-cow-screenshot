package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.CaptureOutcome
import by.sparkyanvil.fastcowscreenshot.capture.ClipboardResult
import by.sparkyanvil.fastcowscreenshot.domain.CaptureMode
import by.sparkyanvil.fastcowscreenshot.domain.Screenshot
import by.sparkyanvil.fastcowscreenshot.storage.SaveResult
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationModelsTest {
    private val f = Path.of("C:\\Users\\Александр\\Pictures\\Screenshots\\Screenshot_2026-10-18_20-00-48.png")
    private val shot = Screenshot(
        BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB),
        LocalDateTime.of(2026, 10, 18, 20, 0, 48),
        CaptureMode.ALL_MONITORS,
    )

    private fun outcome(clipboard: ClipboardResult, save: SaveResult?) = CaptureOutcome(shot, clipboard, save)

    @Test
    fun `saved shows file name and folder`() {
        val m = NotificationModels.fromOutcome(outcome(ClipboardResult.Success, SaveResult.Saved(f)))
        assertEquals("Снимок сохранён", m.title)
        assertEquals(
            listOf("Screenshot_2026-10-18_20-00-48.png", "C:\\Users\\Александр\\Pictures\\Screenshots"),
            m.lines,
        )
        assertEquals(f, m.fileToReveal)
        assertTrue(m.showSaveAs)
        assertNull(m.folderTooltip)
    }

    @Test
    fun `long folder is shortened with tooltip`() {
        val file = Path.of("D:\\" + "a".repeat(60) + "\\x.png")
        val m = NotificationModels.fromOutcome(outcome(ClipboardResult.Success, SaveResult.Saved(file)))
        assertEquals(48, m.lines[1].length)
        assertTrue(m.lines[1].contains("…"))
        assertEquals(file.parent.toString(), m.folderTooltip)
    }

    @Test
    fun `autosave disabled`() {
        val m = NotificationModels.fromOutcome(outcome(ClipboardResult.Success, null))
        assertEquals("Снимок скопирован в буфер обмена", m.title)
        assertTrue(m.lines.isEmpty())
        assertTrue(m.showSaveAs)
    }

    @Test
    fun `save failed offers save as`() {
        val m = NotificationModels.fromOutcome(
            outcome(ClipboardResult.Success, SaveResult.Failed(Path.of("E:\\shots"), "Папка недоступна")),
        )
        assertEquals("Не удалось сохранить снимок", m.title)
        assertEquals(listOf("Папка недоступна", "E:\\shots"), m.lines)
        assertNull(m.fileToReveal)
        assertTrue(m.showSaveAs)
    }

    @Test
    fun `clipboard failed but saved`() {
        val m = NotificationModels.fromOutcome(outcome(ClipboardResult.Failure("busy"), SaveResult.Saved(f)))
        assertEquals("Не удалось скопировать в буфер обмена", m.title)
        assertEquals(f, m.fileToReveal)
    }

    @Test
    fun `both failed`() {
        val m = NotificationModels.fromOutcome(
            outcome(ClipboardResult.Failure("busy"), SaveResult.Failed(Path.of(""), "Папка недоступна")),
        )
        assertEquals(NotificationModels.TITLE_BOTH_FAILED, m.title)
        assertEquals(listOf("Папка недоступна"), m.lines)
    }

    @Test
    fun `message has no actions`() {
        val m = NotificationModels.message("Не удалось сделать снимок", listOf("x"))
        assertNull(m.fileToReveal)
        assertTrue(!m.showSaveAs)
    }
}

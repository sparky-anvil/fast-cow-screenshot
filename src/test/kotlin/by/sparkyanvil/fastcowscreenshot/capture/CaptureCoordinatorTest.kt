package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.CaptureMode
import by.sparkyanvil.fastcowscreenshot.domain.MonitorInfo
import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import by.sparkyanvil.fastcowscreenshot.settings.AppSettings
import by.sparkyanvil.fastcowscreenshot.storage.SaveResult
import by.sparkyanvil.fastcowscreenshot.storage.ScreenshotSaver
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.Executor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CaptureCoordinatorTest {
    private val a = MonitorInfo("\\\\.\\DISPLAY1", PixelRect(0, 0, 200, 100), PixelRect(0, 0, 200, 90), true)
    private val b = MonitorInfo("\\\\.\\DISPLAY2", PixelRect(-100, 0, 100, 100), PixelRect(-100, 0, 100, 90), false)
    private val desktop = VirtualDesktop(listOf(a, b))
    private val time = LocalDateTime.of(2026, 10, 18, 20, 0, 48)
    private val direct = Executor { it.run() }

    private class FakeCapturer : ScreenCapturer {
        var lastArea: PixelRect? = null
        var calls = 0
        override fun capture(area: PixelRect, desktop: VirtualDesktop): BufferedImage {
            lastArea = area
            calls++
            val image = BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB)
            for (y in 0 until area.height) for (x in 0 until area.width) {
                image.setRGB(x, y, ((area.left + x + 1000) shl 8) or (area.top + y))
            }
            return image
        }
    }

    private class FakeSelector : RegionSelector {
        var nextRect: PixelRect? = null
        override fun select(frame: BufferedImage, desktop: VirtualDesktop, onResult: (PixelRect?) -> Unit) =
            onResult(nextRect)
    }

    private val capturer = FakeCapturer()
    private val selector = FakeSelector()
    private val monitors = mockk<MonitorProvider> {
        every { desktop() } returns desktop
        every { monitorUnderCursor(desktop) } returns b
    }
    private val clipboard = mockk<ClipboardWriter> { every { write(any()) } returns ClipboardResult.Success }
    private val saver = mockk<ScreenshotSaver> {
        every { save(any(), any(), any()) } returns SaveResult.Saved(Path.of("C:\\s\\x.png"))
    }
    private val notifier = mockk<CaptureNotifier>(relaxed = true)
    private val cursor = mockk<CursorService>(relaxed = true)
    private val last = LastScreenshotHolder(direct)

    private fun coordinator(
        settings: AppSettings = AppSettings(defaultDirectory = "C:\\s"),
        captureExecutor: Executor = direct,
    ) = CaptureCoordinator(
        monitors, capturer, cursor, clipboard, saver, selector, notifier, last,
        { settings }, { time }, captureExecutor, direct, direct,
    )

    private fun snapAt(x: Int, y: Int): CursorSnapshot = mockk(relaxed = true) {
        every { hotspot } returns PixelPoint(x, y)
    }

    @Test
    fun `monitor mode captures monitor under cursor`() {
        val outcome = slot<CaptureOutcome>()
        every { notifier.showOutcome(capture(outcome)) } returns Unit
        assertTrue(coordinator().trigger(CaptureMode.MONITOR_UNDER_CURSOR))
        assertEquals(b.bounds, capturer.lastArea)
        verify(exactly = 1) { clipboard.write(any()) }
        assertEquals(100, last.get()?.image?.width)
        verify { saver.save(any(), time, Path.of("C:\\s")) }
        assertEquals(ClipboardResult.Success, outcome.captured.clipboard)
        assertIs<SaveResult.Saved>(outcome.captured.save)
    }

    @Test
    fun `all monitors captures desktop bounds`() {
        coordinator().trigger(CaptureMode.ALL_MONITORS)
        assertEquals(PixelRect(-100, 0, 300, 100), capturer.lastArea)
    }

    @Test
    fun `second trigger is ignored while busy`() {
        val queue = ArrayDeque<Runnable>()
        val c = coordinator(captureExecutor = Executor { queue.add(it) })
        assertTrue(c.trigger(CaptureMode.ALL_MONITORS))
        assertFalse(c.trigger(CaptureMode.ALL_MONITORS))
        while (queue.isNotEmpty()) queue.removeFirst().run()
        assertEquals(1, capturer.calls)
        assertTrue(c.trigger(CaptureMode.ALL_MONITORS))
    }

    @Test
    fun `save runs even if clipboard fails`() {
        every { clipboard.write(any()) } returns ClipboardResult.Failure("busy")
        val outcome = slot<CaptureOutcome>()
        every { notifier.showOutcome(capture(outcome)) } returns Unit
        coordinator().trigger(CaptureMode.ALL_MONITORS)
        verify { saver.save(any(), any(), any()) }
        assertIs<ClipboardResult.Failure>(outcome.captured.clipboard)
    }

    @Test
    fun `save failure keeps last screenshot`() {
        every { saver.save(any(), any(), any()) } returns SaveResult.Failed(Path.of("C:\\s"), "Папка недоступна")
        val outcome = slot<CaptureOutcome>()
        every { notifier.showOutcome(capture(outcome)) } returns Unit
        coordinator().trigger(CaptureMode.ALL_MONITORS)
        assertNotNull(last.get())
        verify { clipboard.write(any()) }
        assertIs<SaveResult.Failed>(outcome.captured.save)
    }

    @Test
    fun `autosave disabled skips saver`() {
        val outcome = slot<CaptureOutcome>()
        every { notifier.showOutcome(capture(outcome)) } returns Unit
        coordinator(AppSettings(defaultDirectory = "C:\\s", autoSaveEnabled = false)).trigger(CaptureMode.ALL_MONITORS)
        verify(exactly = 0) { saver.save(any(), any(), any()) }
        assertNull(outcome.captured.save)
    }

    @Test
    fun `cursor not requested when disabled`() {
        coordinator(AppSettings(defaultDirectory = "C:\\s", includeCursor = false)).trigger(CaptureMode.ALL_MONITORS)
        verify(exactly = 0) { cursor.snapshot() }
    }

    @Test
    fun `cursor painted and closed when inside`() {
        val snap = snapAt(-50, 10)
        every { cursor.snapshot() } returns snap
        coordinator(AppSettings(defaultDirectory = "C:\\s", includeCursor = true)).trigger(CaptureMode.ALL_MONITORS)
        verify { cursor.paint(any(), snap, PixelPoint(-100, 0)) }
        verify(exactly = 1) { snap.close() }
    }

    private fun assertNothingTouched(c: CaptureCoordinator) {
        verify(exactly = 0) { clipboard.write(any()) }
        verify(exactly = 0) { saver.save(any(), any(), any()) }
        verify(exactly = 0) { notifier.showOutcome(any()) }
        verify(exactly = 0) { notifier.showMessage(any(), any()) }
        assertFalse(c.isBusy)
        assertNull(last.get())
    }

    @Test
    fun `region cancel touches nothing`() {
        selector.nextRect = null
        val c = coordinator()
        c.trigger(CaptureMode.REGION)
        assertNothingTouched(c)
    }

    @Test
    fun `tiny region is cancel`() {
        selector.nextRect = PixelRect(5, 5, 1, 1)
        val c = coordinator()
        c.trigger(CaptureMode.REGION)
        assertNothingTouched(c)
    }

    @Test
    fun `region crops frame pixels`() {
        selector.nextRect = PixelRect(-50, 10, 20, 5)
        val written = slot<BufferedImage>()
        every { clipboard.write(capture(written)) } returns ClipboardResult.Success
        coordinator().trigger(CaptureMode.REGION)
        assertEquals(20, written.captured.width)
        assertEquals(5, written.captured.height)
        assertEquals(((-50 + 1000) shl 8) or 10, written.captured.getRGB(0, 0) and 0xFFFFFF)
    }

    @Test
    fun `region cursor outside selection is not painted`() {
        val snap = snapAt(150, 50)
        every { cursor.snapshot() } returns snap
        selector.nextRect = PixelRect(-50, 10, 20, 5)
        coordinator(AppSettings(defaultDirectory = "C:\\s", includeCursor = true)).trigger(CaptureMode.REGION)
        verify(exactly = 0) { cursor.paint(any(), any(), any()) }
        verify(exactly = 1) { snap.close() }
    }

    @Test
    fun `capture failure shows message and frees busy`() {
        every { monitors.desktop() } throws CaptureException("No monitors found")
        val c = coordinator()
        c.trigger(CaptureMode.ALL_MONITORS)
        verify { notifier.showMessage("Не удалось сделать снимок", listOf("No monitors found")) }
        assertFalse(c.isBusy)
    }
}
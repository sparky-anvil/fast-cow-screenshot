package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.CaptureMode
import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.Screenshot
import by.sparkyanvil.fastcowscreenshot.domain.SelectionGeometry
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import by.sparkyanvil.fastcowscreenshot.settings.AppSettings
import by.sparkyanvil.fastcowscreenshot.storage.IoErrorMessages
import by.sparkyanvil.fastcowscreenshot.storage.SaveResult
import by.sparkyanvil.fastcowscreenshot.storage.ScreenshotSaver
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger(CaptureCoordinator::class.java)

/** Сценарии захвата (TDD-001 §6). trigger потокобезопасен. */
internal class CaptureCoordinator(
    private val monitors: MonitorProvider,
    private val capturer: ScreenCapturer,
    private val cursor: CursorService,
    private val clipboard: ClipboardWriter,
    private val saver: ScreenshotSaver,
    private val regionSelector: RegionSelector,
    private val notifier: CaptureNotifier,
    private val lastScreenshot: LastScreenshotHolder,
    private val settings: () -> AppSettings,
    private val clock: () -> LocalDateTime,
    private val captureExecutor: Executor,
    private val saveExecutor: Executor,
    private val uiExecutor: Executor,
) {
    private val busy = AtomicBoolean(false)

    val isBusy: Boolean get() = busy.get()

    /** true — захват запущен; false — проигнорирован (уже идёт). Не бросает. */
    fun trigger(mode: CaptureMode): Boolean {
        if (!busy.compareAndSet(false, true)) {
            log.debug("Hotkey ignored: capture in progress")
            return false
        }
        val capturedAt = clock()
        val s = settings()
        val start = System.nanoTime()
        try {
            captureExecutor.execute { runCapture(mode, capturedAt, s, start) }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
            return false
        }
        return true
    }

    private fun runCapture(mode: CaptureMode, capturedAt: LocalDateTime, s: AppSettings, start: Long) {
        val snap = if (s.includeCursor) cursor.snapshot() else null
        var handedOver = false
        try {
            val desktop = monitors.desktop()
            when (mode) {
                CaptureMode.MONITOR_UNDER_CURSOR -> {
                    val area = monitors.monitorUnderCursor(desktop).bounds
                    finish(captureArea(area, desktop, snap), mode, capturedAt, s, start)
                }
                CaptureMode.ALL_MONITORS -> {
                    finish(captureArea(desktop.bounds, desktop, snap), mode, capturedAt, s, start)
                }
                CaptureMode.REGION -> {
                    val frame = capturer.capture(desktop.bounds, desktop)
                    handedOver = true
                    uiExecutor.execute {
                        regionSelector.select(frame, desktop) { rect ->
                            onRegion(rect, frame, desktop, snap, mode, capturedAt, s, start)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            fail(e)
        } finally {
            if (!handedOver) snap?.close()
        }
    }

    private fun captureArea(area: PixelRect, desktop: VirtualDesktop, snap: CursorSnapshot?): BufferedImage {
        val image = capturer.capture(area, desktop)
        if (snap != null && area.contains(snap.hotspot)) {
            cursor.paint(image, snap, PixelPoint(area.left, area.top))
        }
        return image
    }

    private fun onRegion(
        rect: PixelRect?,
        frame: BufferedImage,
        desktop: VirtualDesktop,
        snap: CursorSnapshot?,
        mode: CaptureMode,
        capturedAt: LocalDateTime,
        s: AppSettings,
        start: Long,
    ) {
        try {
            captureExecutor.execute {
                try {
                    val clipped = rect?.intersect(desktop.bounds)
                    if (clipped == null || SelectionGeometry.isCancelled(clipped)) {
                        log.info("Region capture cancelled")
                        busy.set(false)
                        return@execute
                    }
                    val fr = SelectionGeometry.toFrameRect(clipped, PixelPoint(desktop.bounds.left, desktop.bounds.top))
                    val image = crop(frame, fr)
                    if (snap != null && clipped.contains(snap.hotspot)) {
                        cursor.paint(image, snap, PixelPoint(clipped.left, clipped.top))
                    }
                    finish(image, mode, capturedAt, s, start)
                } catch (e: Exception) {
                    fail(e)
                } finally {
                    snap?.close()
                }
            }
        } catch (e: RejectedExecutionException) {
            busy.set(false)
            snap?.close()
        }
    }

    private fun crop(frame: BufferedImage, fr: PixelRect): BufferedImage {
        val out = BufferedImage(fr.width, fr.height, BufferedImage.TYPE_INT_RGB)
        val src = (frame.raster.dataBuffer as DataBufferInt).data
        val dst = (out.raster.dataBuffer as DataBufferInt).data
        for (row in 0 until fr.height) {
            System.arraycopy(src, (fr.top + row) * frame.width + fr.left, dst, row * fr.width, fr.width)
        }
        return out
    }

    private fun finish(image: BufferedImage, mode: CaptureMode, capturedAt: LocalDateTime, s: AppSettings, start: Long) {
        val shot = Screenshot(image, capturedAt, mode)
        val clip = clipboard.write(image)
        lastScreenshot.set(shot)
        log.info("Captured {} {}x{} in {} ms", mode, image.width, image.height, (System.nanoTime() - start) / 1_000_000)
        busy.set(false)
        saveExecutor.execute {
            val save: SaveResult? = if (!s.autoSaveEnabled) {
                null
            } else {
                try {
                    saver.save(image, capturedAt, Path.of(s.defaultDirectory))
                } catch (e: Exception) {
                    log.error("Auto-save failed", e)
                    SaveResult.Failed(Path.of(""), IoErrorMessages.UNAVAILABLE)
                }
            }
            uiExecutor.execute { notifier.showOutcome(CaptureOutcome(shot, clip, save)) }
        }
    }

    private fun fail(e: Exception) {
        log.error("Capture failed", e)
        busy.set(false)
        uiExecutor.execute { notifier.showMessage(CAPTURE_FAILED_TITLE, listOf(e.message ?: e.javaClass.simpleName)) }
    }

    companion object {
        const val CAPTURE_FAILED_TITLE: String = "Не удалось сделать снимок"
    }
}
package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.capture.CaptureException
import by.sparkyanvil.fastcowscreenshot.capture.FrameAssembler
import by.sparkyanvil.fastcowscreenshot.capture.ScreenCapturer
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.Gdi32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.ptr.PointerByReference
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage

private val log = LoggerFactory.getLogger(GdiScreenCapturer::class.java)

/** Захват прямоугольника виртуального стола одним BitBlt через DIB-секцию. */
internal class GdiScreenCapturer(
    private val user32: User32Api = User32Api.INSTANCE,
    private val gdi32: Gdi32Api = Gdi32Api.INSTANCE,
) : ScreenCapturer {
    override fun capture(area: PixelRect, desktop: VirtualDesktop): BufferedImage {
        require(!area.isEmpty) { "area is empty" }
        val start = System.nanoTime()
        val screenDc = user32.GetDC(null) ?: throw failure("GetDC")
        try {
            val memDc = gdi32.CreateCompatibleDC(screenDc) ?: throw failure("CreateCompatibleDC")
            try {
                val bmi = WinGDI.BITMAPINFO()
                val h = bmi.bmiHeader
                h.biSize = h.size()
                h.biWidth = area.width
                h.biHeight = -area.height
                h.biPlanes = 1
                h.biBitCount = 32
                h.biCompression = WinConst.BI_RGB
                val bitsRef = PointerByReference()
                val dib = gdi32.CreateDIBSection(screenDc, bmi, WinConst.DIB_RGB_COLORS, bitsRef, null, 0)
                    ?: throw failure("CreateDIBSection")
                try {
                    val old = gdi32.SelectObject(memDc, dib)
                    try {
                        val rop = WinConst.SRCCOPY or WinConst.CAPTUREBLT
                        if (!gdi32.BitBlt(memDc, 0, 0, area.width, area.height, screenDc, area.left, area.top, rop)) {
                            throw failure("BitBlt")
                        }
                        val bits = bitsRef.value
                        val image = FrameAssembler.assemble(area, FrameAssembler.coveredRects(area, desktop)) { s, data, count ->
                            bits.read(s * 4L, data, s, count)
                        }
                        log.debug(
                            "BitBlt {}x{} at ({},{}) in {} ms",
                            area.width, area.height, area.left, area.top, (System.nanoTime() - start) / 1_000_000,
                        )
                        return image
                    } finally {
                        if (old != null) gdi32.SelectObject(memDc, old)
                    }
                } finally {
                    gdi32.DeleteObject(dib)
                }
            } finally {
                gdi32.DeleteDC(memDc)
            }
        } finally {
            user32.ReleaseDC(null, screenDc)
        }
    }

    private fun failure(function: String): CaptureException {
        val cause = WinApiException.lastError(function)
        return CaptureException(cause.message ?: "$function failed", cause)
    }
}
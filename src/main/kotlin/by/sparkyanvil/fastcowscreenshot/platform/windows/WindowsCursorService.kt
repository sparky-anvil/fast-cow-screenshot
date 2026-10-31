package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.capture.CursorService
import by.sparkyanvil.fastcowscreenshot.capture.CursorSnapshot
import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.CURSORINFO
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.Gdi32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.ptr.PointerByReference
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger(WindowsCursorService::class.java)

/** Указатель мыши: снимок формы (CopyIcon) и отрисовка через DrawIconEx. */
internal class WindowsCursorService(
    private val user32: User32Api = User32Api.INSTANCE,
    private val gdi32: Gdi32Api = Gdi32Api.INSTANCE,
) : CursorService {
    companion object {
        const val CURSOR_PATCH_SIZE: Int = 256
    }

    override fun snapshot(): CursorSnapshot? {
        val ci = CURSORINFO()
        if (!user32.GetCursorInfo(ci)) {
            log.warn("GetCursorInfo failed, GetLastError={}", Native.getLastError())
            return null
        }
        val cursor: HICON = ci.hCursor ?: return null
        if ((ci.flags and WinConst.CURSOR_SHOWING) == 0) return null
        val icon: HICON? = user32.CopyIcon(cursor)
        if (icon == null) {
            log.warn("CopyIcon failed, GetLastError={}", Native.getLastError())
            return null
        }
        val ii = WinGDI.ICONINFO()
        if (!user32.GetIconInfo(icon, ii)) {
            log.warn("GetIconInfo failed, GetLastError={}", Native.getLastError())
            user32.DestroyIcon(icon)
            return null
        }
        ii.hbmMask?.let { gdi32.DeleteObject(it) }
        ii.hbmColor?.let { gdi32.DeleteObject(it) }
        return WindowsCursorSnapshot(
            PixelPoint(ci.ptScreenPos.x, ci.ptScreenPos.y), icon, ii.xHotspot, ii.yHotspot, user32,
        )
    }

    override fun paint(image: BufferedImage, cursor: CursorSnapshot, imageOrigin: PixelPoint) {
        val snap = cursor as? WindowsCursorSnapshot ?: throw IllegalArgumentException("Unsupported CursorSnapshot")
        val drawX = snap.hotspot.x - snap.xHotspot - imageOrigin.x
        val drawY = snap.hotspot.y - snap.yHotspot - imageOrigin.y
        val patch = PixelRect(0, 0, image.width, image.height)
            .intersect(PixelRect(drawX, drawY, CURSOR_PATCH_SIZE, CURSOR_PATCH_SIZE)) ?: return
        val data: IntArray = (image.raster.dataBuffer as DataBufferInt).data

        val memDc = gdi32.CreateCompatibleDC(null)
        if (memDc == null) {
            log.warn("CreateCompatibleDC failed, GetLastError={}", Native.getLastError())
            return
        }
        try {
            val bmi = WinGDI.BITMAPINFO()
            val h = bmi.bmiHeader
            h.biSize = h.size()
            h.biWidth = patch.width
            h.biHeight = -patch.height
            h.biPlanes = 1
            h.biBitCount = 32
            h.biCompression = WinConst.BI_RGB
            val bitsRef = PointerByReference()
            val dib = gdi32.CreateDIBSection(null, bmi, WinConst.DIB_RGB_COLORS, bitsRef, null, 0)
            if (dib == null) {
                log.warn("CreateDIBSection failed, GetLastError={}", Native.getLastError())
                return
            }
            try {
                val old = gdi32.SelectObject(memDc, dib)
                try {
                    val bits = bitsRef.value
                    for (row in 0 until patch.height) {
                        val index = (patch.top + row) * image.width + patch.left
                        bits.write(row.toLong() * patch.width * 4L, data, index, patch.width)
                    }
                    val ok = user32.DrawIconEx(
                        memDc, drawX - patch.left, drawY - patch.top, snap.icon, 0, 0, 0, null, WinConst.DI_NORMAL,
                    )
                    if (!ok) {
                        log.warn("DrawIconEx failed, GetLastError={}", Native.getLastError())
                        return
                    }
                    for (row in 0 until patch.height) {
                        val index = (patch.top + row) * image.width + patch.left
                        bits.read(row.toLong() * patch.width * 4L, data, index, patch.width)
                    }
                } finally {
                    if (old != null) gdi32.SelectObject(memDc, old)
                }
            } finally {
                gdi32.DeleteObject(dib)
            }
        } finally {
            gdi32.DeleteDC(memDc)
        }
    }
}

private class WindowsCursorSnapshot(
    override val hotspot: PixelPoint,
    val icon: HICON,
    val xHotspot: Int,
    val yHotspot: Int,
    private val user32: User32Api,
) : CursorSnapshot {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            user32.DestroyIcon(icon)
        }
    }
}
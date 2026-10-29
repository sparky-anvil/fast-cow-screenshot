package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.capture.ClipboardResult
import by.sparkyanvil.fastcowscreenshot.capture.ClipboardWriter
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.Kernel32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage

private val log = LoggerFactory.getLogger(WindowsClipboardWriter::class.java)

/** CF_DIB через WinAPI. Потокобезопасен на уровне ОС (буфер открывается и закрывается в одном вызове). */
internal class WindowsClipboardWriter(
    private val ownerWindow: () -> HWND,
    private val user32: User32Api = User32Api.INSTANCE,
    private val kernel32: Kernel32Api = Kernel32Api.INSTANCE,
) : ClipboardWriter {
    override fun write(image: BufferedImage): ClipboardResult {
        var hMem: Pointer? = null
        var owned = true
        try {
            val start = System.nanoTime()
            val size = DibEncoder.totalSize(image.width, image.height)
            hMem = kernel32.GlobalAlloc(WinConst.GMEM_MOVEABLE, size)
                ?: return fail(lastErrorText("GlobalAlloc"))
            val ptr: Pointer = kernel32.GlobalLock(hMem) ?: return fail(lastErrorText("GlobalLock"))
            try {
                DibEncoder.write(image) { off, buf, len -> ptr.write(off, buf, 0, len) }
            } finally {
                kernel32.GlobalUnlock(hMem)
            }
            val owner = ownerWindow()
            val opened = ClipboardRetry.attempt { n ->
                if (user32.OpenClipboard(owner)) {
                    true
                } else {
                    log.warn("Clipboard busy, attempt {}/{}", n, ClipboardRetry.MAX_ATTEMPTS)
                    null
                }
            }
            if (opened == null) return fail("clipboard is busy")
            try {
                if (!user32.EmptyClipboard()) return fail(lastErrorText("EmptyClipboard"))
                if (user32.SetClipboardData(WinConst.CF_DIB, hMem) == null) {
                    return fail(lastErrorText("SetClipboardData"))
                }
                owned = false
            } finally {
                user32.CloseClipboard()
            }
            log.debug(
                "Clipboard DIB {}x{} written in {} ms",
                image.width, image.height, (System.nanoTime() - start) / 1_000_000,
            )
            return ClipboardResult.Success
        } catch (e: Exception) {
            return fail(e.message ?: e.javaClass.simpleName)
        } finally {
            if (owned && hMem != null) kernel32.GlobalFree(hMem)
        }
    }

    private fun lastErrorText(function: String): String =
        WinApiException.lastError(function).message ?: "$function failed"

    private fun fail(reason: String): ClipboardResult {
        log.error("Clipboard write failed: {}", reason)
        return ClipboardResult.Failure(reason)
    }
}
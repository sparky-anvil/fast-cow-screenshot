package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.capture.CaptureException
import by.sparkyanvil.fastcowscreenshot.capture.MonitorProvider
import by.sparkyanvil.fastcowscreenshot.domain.MonitorInfo
import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HDC
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinUser
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WindowsMonitorProvider::class.java)

/** Перечисление мониторов через EnumDisplayMonitors; координаты физические. */
internal class WindowsMonitorProvider(private val user32: User32Api = User32Api.INSTANCE) : MonitorProvider {
    override fun desktop(): VirtualDesktop {
        val list = mutableListOf<MonitorInfo>()
        val callback = object : WinUser.MONITORENUMPROC {
            override fun apply(hMonitor: WinUser.HMONITOR?, hdc: HDC?, rect: RECT?, data: LPARAM?): Int {
                if (hMonitor == null) return 1
                val info = WinUser.MONITORINFOEX()
                if (user32.GetMonitorInfo(hMonitor, info)) {
                    list.add(
                        MonitorInfo(
                            Native.toString(info.szDevice),
                            rc(info.rcMonitor),
                            rc(info.rcWork),
                            (info.dwFlags and WinConst.MONITORINFOF_PRIMARY) != 0,
                        ),
                    )
                } else {
                    log.warn("GetMonitorInfo failed, GetLastError={}", Native.getLastError())
                }
                return 1
            }
        }
        if (!user32.EnumDisplayMonitors(null, null, callback, null)) {
            throw CaptureException(WinApiException.lastError("EnumDisplayMonitors").message ?: "EnumDisplayMonitors failed")
        }
        if (list.isEmpty()) throw CaptureException("No monitors found")
        list.forEach { log.debug("Monitor {} bounds={} primary={}", it.deviceName, it.bounds, it.isPrimary) }
        return VirtualDesktop(list)
    }

    override fun monitorUnderCursor(desktop: VirtualDesktop): MonitorInfo {
        val p = POINT()
        if (!user32.GetCursorPos(p)) {
            log.warn("GetCursorPos failed, GetLastError={}", Native.getLastError())
            return desktop.primary
        }
        val point = PixelPoint(p.x, p.y)
        val handle: WinUser.HMONITOR = user32.MonitorFromPoint(POINT.ByValue(p.x, p.y), WinConst.MONITOR_DEFAULTTOPRIMARY)
            ?: return desktop.primary
        val info = WinUser.MONITORINFOEX()
        if (!user32.GetMonitorInfo(handle, info)) {
            return desktop.monitorAt(point) ?: desktop.primary
        }
        val name = Native.toString(info.szDevice)
        return desktop.monitors.firstOrNull { it.deviceName == name } ?: desktop.monitorAt(point) ?: desktop.primary
    }

    private fun rc(r: RECT): PixelRect = PixelRect(r.left, r.top, r.right - r.left, r.bottom - r.top)
}
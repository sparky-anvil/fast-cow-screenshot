package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.Kernel32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser
import org.slf4j.LoggerFactory
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val log = LoggerFactory.getLogger(NativeMessageLoop::class.java)

/** Поток fcs-hotkeys с циклом GetMessageW. Потокобезопасен. */
internal class NativeMessageLoop(
    private val user32: User32Api = User32Api.INSTANCE,
    private val kernel32: Kernel32Api = Kernel32Api.INSTANCE,
) {
    /** Обработчик WM_HOTKEY (аргумент — id). Вызывается на fcs-hotkeys. */
    @Volatile
    var onHotkey: ((Int) -> Unit)? = null

    private val tasks = ConcurrentLinkedQueue<FutureTask<*>>()
    private val ready = CountDownLatch(1)
    private val started = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)

    @Volatile
    private var threadId: Int = 0

    @Volatile
    private var hwnd: HWND? = null

    @Volatile
    private var startError: String? = null

    @Volatile
    private var thread: Thread? = null

    /**
     * Запускает daemon-поток "fcs-hotkeys" и ждёт готовности до START_TIMEOUT_MS.
     * @throws WinApiException если окно не создано или поток не стартовал за таймаут.
     * @throws IllegalStateException при повторном вызове.
     */
    fun start() {
        check(started.compareAndSet(false, true)) { "already started" }
        val t = Thread({ run() }, THREAD_NAME)
        t.isDaemon = true
        thread = t
        t.start()
        if (!ready.await(START_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            throw WinApiException("Native message loop did not start")
        }
        startError?.let { throw WinApiException(it) }
    }

    /** HWND message-only окна. @throws IllegalStateException до start(). */
    val messageWindow: HWND
        get() = hwnd ?: throw IllegalStateException("message loop is not started")

    /**
     * Выполняет block на fcs-hotkeys и возвращает результат. На самом fcs-hotkeys выполняет сразу.
     * Ждёт до INVOKE_TIMEOUT_MS. Исключение из block пробрасывается вызывающему (cause из ExecutionException).
     * @throws java.util.concurrent.TimeoutException при превышении таймаута.
     */
    fun <T> invoke(block: () -> T): T {
        if (Thread.currentThread() === thread) return block()
        val task = FutureTask(Callable { block() })
        tasks.add(task)
        if (!user32.PostThreadMessage(threadId, WM_APP_INVOKE, WPARAM(0), LPARAM(0))) {
            tasks.remove(task)
            throw WinApiException.lastError("PostThreadMessage")
        }
        try {
            return task.get(INVOKE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** PostThreadMessage(WM_QUIT), join до STOP_TIMEOUT_MS. Повторный вызов — без действия. */
    fun stop() {
        val t = thread ?: return
        if (!stopped.compareAndSet(false, true)) return
        user32.PostThreadMessage(threadId, WinConst.WM_QUIT, WPARAM(0), LPARAM(0))
        t.join(STOP_TIMEOUT_MS)
    }

    private fun run() {
        threadId = kernel32.GetCurrentThreadId()
        val msg = WinUser.MSG()
        user32.PeekMessage(msg, null, 0, 0, WinConst.PM_NOREMOVE)
        val window: HWND? = user32.CreateWindowEx(
            0, "STATIC", "fcs-message-window", 0, 0, 0, 0, 0,
            HWND(Pointer.createConstant(WinConst.HWND_MESSAGE_VALUE.toLong())), null, null, null,
        )
        if (window == null) {
            startError = WinApiException.lastError("CreateWindowEx").message
            ready.countDown()
            return
        }
        hwnd = window
        ready.countDown()
        log.info("Native message loop started")
        while (true) {
            val r = user32.GetMessage(msg, null, 0, 0)
            if (r == 0) break
            if (r == -1) {
                log.error("GetMessage failed, GetLastError={}", Native.getLastError())
                break
            }
            when (msg.message) {
                WinConst.WM_HOTKEY -> try {
                    onHotkey?.invoke(msg.wParam.toInt())
                } catch (e: Exception) {
                    log.error("Hotkey handler failed", e)
                }
                WM_APP_INVOKE -> while (true) {
                    val task = tasks.poll() ?: break
                    task.run()
                }
            }
        }
        user32.DestroyWindow(window)
        log.info("Native message loop stopped")
    }

    companion object {
        const val THREAD_NAME: String = "fcs-hotkeys"
        const val WM_APP_INVOKE: Int = WinConst.WM_APP + 1
        const val START_TIMEOUT_MS: Long = 5000
        const val INVOKE_TIMEOUT_MS: Long = 5000
        const val STOP_TIMEOUT_MS: Long = 2000
    }
}
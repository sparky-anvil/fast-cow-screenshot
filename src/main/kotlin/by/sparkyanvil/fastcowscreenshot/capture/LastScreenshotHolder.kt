package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.Screenshot
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

/** Последний снимок. Потокобезопасен. */
internal class LastScreenshotHolder(private val uiExecutor: Executor) {
    private val current = AtomicReference<Screenshot?>(null)
    private val listeners = CopyOnWriteArrayList<(Screenshot) -> Unit>()

    fun get(): Screenshot? = current.get()

    /** Сохраняет снимок и вызывает каждого слушателя через uiExecutor.execute { listener(screenshot) }. */
    fun set(screenshot: Screenshot) {
        current.set(screenshot)
        listeners.forEach { listener -> uiExecutor.execute { listener(screenshot) } }
    }

    fun addListener(listener: (Screenshot) -> Unit) {
        listeners.add(listener)
    }
}
package by.sparkyanvil.fastcowscreenshot.settings

import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Текущие настройки в памяти. Потокобезопасен: current — @Volatile, update — synchronized. */
internal class SettingsStore(private val repository: SettingsRepository) {
    private val listeners = CopyOnWriteArrayList<(AppSettings) -> Unit>()

    @Volatile
    private var value: AppSettings = repository.load()

    /** Значение, загруженное repository.load() в init, далее — последнее после update. */
    val current: AppSettings get() = value

    /**
     * Применяет transform к текущим настройкам, сохраняет и уведомляет слушателей.
     * Возвращает false, если запись на диск не удалась (значение в памяти при этом обновляется).
     */
    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings): Boolean {
        val new = transform(value)
        if (new == value) return true
        val saved = try {
            repository.save(new)
            true
        } catch (e: IOException) {
            log.error("Failed to save settings: {}", e.message)
            false
        }
        value = new
        listeners.forEach { it(new) }
        return saved
    }

    /** Добавляет слушателя изменений настроек. */
    fun addListener(listener: (AppSettings) -> Unit) {
        listeners.add(listener)
    }

    private companion object {
        val log = LoggerFactory.getLogger(SettingsStore::class.java)
    }
}

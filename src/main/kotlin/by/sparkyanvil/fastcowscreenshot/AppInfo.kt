package by.sparkyanvil.fastcowscreenshot

import java.io.InputStream
import java.util.Properties

private const val VERSION_MISSING_MESSAGE = "app-info.properties: version is missing"

/** Сведения о приложении. Потокобезопасен: значения неизменяемы, инициализируются один раз при загрузке объекта. */
internal object AppInfo {
    const val NAME: String = "fast-cow-screenshot"

    /** Путь ресурса с версией в classpath. */
    const val VERSION_RESOURCE: String = "/app-info.properties"

    /** Версия приложения из [VERSION_RESOURCE], например `0.0.1` или `0.0.2-SNAPSHOT`. */
    val VERSION: String = parseVersion(AppInfo::class.java.getResourceAsStream(VERSION_RESOURCE))

    /**
     * Читает свойство `version` из потока в формате `java.util.Properties` и закрывает поток.
     * @param input поток ресурса; `null`, если ресурс не найден
     * @return значение `version` без пробелов по краям
     * @throws IllegalStateException с сообщением [VERSION_MISSING_MESSAGE], если [input] равен `null`, свойства нет,
     *   оно пустое после trim или содержит `${` (подстановка Gradle не выполнилась)
     */
    internal fun parseVersion(input: InputStream?): String {
        if (input == null) throw IllegalStateException(VERSION_MISSING_MESSAGE)
        val props = Properties()
        input.use { props.load(it) }
        val value = props.getProperty("version")?.trim()
        if (value == null || value.isEmpty() || value.contains("\${")) {
            throw IllegalStateException(VERSION_MISSING_MESSAGE)
        }
        return value
    }
}
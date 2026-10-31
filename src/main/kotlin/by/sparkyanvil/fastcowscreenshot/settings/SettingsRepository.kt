package by.sparkyanvil.fastcowscreenshot.settings

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Чтение/запись settings.json. Не потокобезопасен — используется только из SettingsStore. */
internal class SettingsRepository(
    private val file: Path,
    private val defaults: DefaultDirectoryProvider,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Загружает настройки; при отсутствии или повреждении файла возвращает значения по умолчанию. */
    fun load(): AppSettings {
        if (!Files.exists(file)) {
            val settings = defaultSettings()
            try {
                save(settings)
            } catch (e: IOException) {
                log.warn("Failed to write initial settings: {}", e.message)
            }
            return settings
        }
        return try {
            json.decodeFromString<AppSettings>(Files.readString(file, UTF_8))
        } catch (e: IOException) {
            log.warn("Failed to read settings, defaults used: {}", e.message)
            defaultSettings()
        } catch (e: SerializationException) {
            log.warn("Settings file is corrupted, defaults used")
            defaultSettings()
        } catch (e: IllegalArgumentException) {
            log.warn("Settings file is corrupted, defaults used")
            defaultSettings()
        }
    }

    /** Атомарная запись. @throws IOException при ошибке записи. */
    fun save(settings: AppSettings) {
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling("$FILE_NAME.tmp")
        Files.writeString(tmp, json.encodeToString(settings), UTF_8)
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    /** Настройки по умолчанию с начальной директорией от провайдера. */
    fun defaultSettings(): AppSettings = AppSettings(defaultDirectory = defaults.initialDirectory().toString())

    companion object {
        const val FILE_NAME: String = "settings.json"
        const val APP_DIR_NAME: String = "fast-cow-screenshot"

        private val log = LoggerFactory.getLogger(SettingsRepository::class.java)

        /** Файл настроек в %APPDATA% (или в домашней директории, если переменной нет). */
        fun defaultFile(): Path =
            Path.of(System.getenv("APPDATA") ?: System.getProperty("user.home"), APP_DIR_NAME, FILE_NAME)
    }
}

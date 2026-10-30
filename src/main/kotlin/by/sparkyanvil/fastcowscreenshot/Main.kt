package by.sparkyanvil.fastcowscreenshot

import by.sparkyanvil.fastcowscreenshot.app.FastCowScreenshotApp
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("by.sparkyanvil.fastcowscreenshot.Main")

/** Точка входа. */
fun main() {
    Thread.setDefaultUncaughtExceptionHandler { t, e -> log.error("Uncaught exception in {}", t.name, e) }
    log.info("{} {} started, java {}", AppInfo.NAME, AppInfo.VERSION, System.getProperty("java.version"))
    FastCowScreenshotApp().start()
}

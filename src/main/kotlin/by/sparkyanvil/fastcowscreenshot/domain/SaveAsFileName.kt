package by.sparkyanvil.fastcowscreenshot.domain

import java.nio.file.Path

/** Нормализация имени «Сохранить как» (REQ-001.39). */
internal object SaveAsFileName {
    /**
     * Дописывает ".png", если name не оканчивается на ".png" без учёта регистра.
     * @throws IllegalArgumentException если name.isEmpty().
     */
    fun normalizeName(name: String): String {
        require(name.isNotEmpty()) { "name must not be empty" }
        val extension = ScreenshotFileNames.EXTENSION
        return if (name.endsWith(extension, ignoreCase = true)) name else name + extension
    }

    /** То же для полного пути: меняется только последний элемент. */
    fun normalize(path: Path): Path {
        val file = path.fileName.toString()
        return path.resolveSibling(normalizeName(file))
    }
}
package by.sparkyanvil.fastcowscreenshot.storage

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Проверка права записи в папку. */
internal object DirectoryValidator {
    /** true, если dir — существующая папка и в ней удалось создать и удалить временный файл. Не бросает. */
    fun isWritable(dir: Path): Boolean {
        if (!Files.isDirectory(dir)) return false
        return try {
            val tmp = Files.createTempFile(dir, PngFileWriter.TEMP_PREFIX, PngFileWriter.TEMP_SUFFIX)
            Files.deleteIfExists(tmp)
            true
        } catch (e: IOException) {
            false
        }
    }
}
package by.sparkyanvil.fastcowscreenshot.storage

import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/** Атомарная запись PNG. Потокобезопасен (без состояния). */
internal class PngFileWriter {
    init {
        ImageIO.setUseCache(false)
    }

    /**
     * @param replaceExisting false — если target существует, бросает FileAlreadyExistsException, target не меняется;
     *                        true — заменяет target атомарно.
     * @throws java.io.IOException при любой ошибке; временный файл в этом случае удалён.
     */
    fun writeAtomically(image: BufferedImage, target: Path, replaceExisting: Boolean) {
        val dir: Path = target.parent
        val tmp = Files.createTempFile(dir, TEMP_PREFIX, TEMP_SUFFIX)
        try {
            val writer = ImageIO.getImageWritersByFormatName("png").next()
            try {
                val param = writer.defaultWriteParam
                if (param.canWriteCompressed()) {
                    param.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    param.compressionQuality = COMPRESSION_QUALITY
                }
                ImageIO.createImageOutputStream(tmp.toFile()).use { out ->
                    writer.output = out
                    writer.write(null, IIOImage(image, null, null), param)
                }
            } finally {
                writer.dispose()
            }
            if (replaceExisting) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } else {
                Files.move(tmp, target)
            }
        } catch (e: IOException) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    companion object {
        const val COMPRESSION_QUALITY: Float = 0.5f
        const val TEMP_PREFIX: String = ".fcs-"
        const val TEMP_SUFFIX: String = ".tmp"
    }
}
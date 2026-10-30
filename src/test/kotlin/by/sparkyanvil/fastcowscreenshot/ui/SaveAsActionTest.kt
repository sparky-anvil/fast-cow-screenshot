package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.CaptureNotifier
import by.sparkyanvil.fastcowscreenshot.capture.LastScreenshotHolder
import by.sparkyanvil.fastcowscreenshot.domain.CaptureMode
import by.sparkyanvil.fastcowscreenshot.domain.Screenshot
import by.sparkyanvil.fastcowscreenshot.settings.DefaultDirectoryProvider
import by.sparkyanvil.fastcowscreenshot.settings.SettingsRepository
import by.sparkyanvil.fastcowscreenshot.settings.SettingsStore
import by.sparkyanvil.fastcowscreenshot.storage.PngFileWriter
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SaveAsActionTest {
    @TempDir
    lateinit var tempDir: Path

    private val direct = Executor { it.run() }
    private val holder = LastScreenshotHolder(direct)
    private val notifier: CaptureNotifier = mockk(relaxed = true)
    private val dialogCalls = mutableListOf<Pair<Path, String>>()
    private var dialogResult: Path? = null
    private var confirmResult = true
    private lateinit var store: SettingsStore
    private lateinit var action: SaveAsAction
    private val capturedAt = LocalDateTime.of(2026, 10, 18, 20, 0, 48)

    private fun shot(w: Int, h: Int) =
        Screenshot(BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), capturedAt, CaptureMode.ALL_MONITORS)

    @BeforeEach
    fun setUp() {
        Files.createDirectories(tempDir.resolve("default"))
        val defaults = DefaultDirectoryProvider { tempDir.resolve("default") }
        store = SettingsStore(SettingsRepository(tempDir.resolve("settings.json"), defaults))
        action = SaveAsAction(
            holder, store,
            { dir, name ->
                dialogCalls.add(dir to name)
                dialogResult
            },
            { confirmResult }, PngFileWriter(), notifier, direct, direct,
        )
    }

    @Test
    fun `no screenshot does nothing`() {
        action.run()
        assertTrue(dialogCalls.isEmpty())
    }

    @Test
    fun `first open uses default dir and template name`() {
        holder.set(shot(40, 30))
        action.run()
        assertEquals(listOf(tempDir.resolve("default") to "Screenshot_2026-10-18_20-00-48.png"), dialogCalls)
    }

    @Test
    fun `saves normalized png and remembers folder`() {
        holder.set(shot(40, 30))
        val dir = Files.createDirectories(tempDir.resolve("Bug-42"))
        dialogResult = dir.resolve("login-error")
        val defaultBefore = store.current.defaultDirectory
        action.run()
        val img = ImageIO.read(dir.resolve("login-error.png").toFile())
        assertEquals(40, img.width)
        assertEquals(30, img.height)
        assertEquals(dir.toString(), store.current.lastSaveAsDirectory)
        assertEquals(defaultBefore, store.current.defaultDirectory)
    }

    @Test
    fun `reopens in last folder`() {
        holder.set(shot(40, 30))
        val dir = Files.createDirectories(tempDir.resolve("Bug-42"))
        dialogResult = dir.resolve("login-error")
        action.run()
        action.run()
        assertEquals(dir, dialogCalls[1].first)
    }

    @Test
    fun `missing last folder falls back to default`() {
        holder.set(shot(40, 30))
        store.update { it.copy(lastSaveAsDirectory = tempDir.resolve("gone").toString()) }
        action.run()
        assertEquals(tempDir.resolve("default"), dialogCalls[0].first)
    }

    @Test
    fun `jpg name gets png appended`() {
        holder.set(shot(40, 30))
        dialogResult = tempDir.resolve("default").resolve("login.jpg")
        action.run()
        assertTrue(Files.exists(tempDir.resolve("default").resolve("login.jpg.png")))
    }

    @Test
    fun `declined overwrite keeps file`() {
        holder.set(shot(40, 30))
        val x = tempDir.resolve("default").resolve("x.png")
        x.writeText("old")
        dialogResult = x
        confirmResult = false
        action.run()
        assertEquals("old", x.readText())
        assertNull(store.current.lastSaveAsDirectory)
    }

    @Test
    fun `accepted overwrite replaces file`() {
        holder.set(shot(40, 30))
        val x = tempDir.resolve("default").resolve("x.png")
        x.writeText("old")
        dialogResult = x
        confirmResult = true
        action.run()
        val img = ImageIO.read(x.toFile())
        assertEquals(40, img.width)
        assertEquals(30, img.height)
    }

    @Test
    fun `cancel changes nothing`() {
        holder.set(shot(40, 30))
        dialogResult = null
        action.run()
        Files.list(tempDir.resolve("default")).use { assertEquals(0, it.count()) }
        assertNull(store.current.lastSaveAsDirectory)
    }

    @Test
    fun `uses latest screenshot`() {
        holder.set(shot(40, 30))
        holder.set(shot(80, 60))
        dialogResult = tempDir.resolve("default").resolve("a")
        action.run()
        val img = ImageIO.read(tempDir.resolve("default").resolve("a.png").toFile())
        assertEquals(80, img.width)
        assertEquals(60, img.height)
    }

    @Test
    fun `write failure shows notification`() {
        holder.set(shot(40, 30))
        dialogResult = tempDir.resolve("missing").resolve("x")
        action.run()
        verify {
            notifier.showMessage(
                "Не удалось сохранить снимок",
                listOf("Папка недоступна", tempDir.resolve("missing").toString()),
            )
        }
    }
}
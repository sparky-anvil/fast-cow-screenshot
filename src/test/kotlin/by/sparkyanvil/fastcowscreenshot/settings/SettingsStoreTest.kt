package by.sparkyanvil.fastcowscreenshot.settings

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsStoreTest {
    @TempDir
    lateinit var tempDir: Path

    private val provider = DefaultDirectoryProvider { Path.of("C:\\Shots") }
    private val file: Path get() = tempDir.resolve("cfg").resolve("settings.json")

    @Test
    fun `update saves and notifies`() {
        val store = SettingsStore(SettingsRepository(file, provider))
        val received = mutableListOf<AppSettings>()
        store.addListener { received.add(it) }

        assertTrue(store.update { it.copy(includeCursor = true) })

        assertTrue(store.current.includeCursor)
        assertEquals(1, received.size)
        assertTrue(SettingsRepository(file, provider).load().includeCursor)
    }

    @Test
    fun `update with same value does nothing`() {
        val store = SettingsStore(SettingsRepository(file, provider))
        var calls = 0
        store.addListener { calls++ }

        assertTrue(store.update { it })
        assertEquals(0, calls)
    }

    @Test
    fun `update keeps value in memory when save fails`() {
        val repo = mockk<SettingsRepository>()
        every { repo.load() } returns AppSettings(defaultDirectory = "x")
        every { repo.save(any()) } throws IOException("disk")
        val store = SettingsStore(repo)

        assertFalse(store.update { it.copy(autoSaveEnabled = false) })
        assertFalse(store.current.autoSaveEnabled)
    }
}

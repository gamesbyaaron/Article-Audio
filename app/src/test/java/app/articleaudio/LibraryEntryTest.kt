package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Properties

class LibraryEntryTest {
    private val entry = LibraryEntry(
        id = "Alan Turing", title = "Alan Turing", name = "Turing = genius", fetchedAt = 1000L, openedAt = 2000L,
        chars = 90_000, position = 50, sentences = 201, audioPositionMs = 65_000, hasAudio = true,
    )

    @Test
    fun survivesSavingAndLoading() =
        assertEquals(entry, LibraryEntry.fromProperties("Alan Turing", entry.toProperties(), hasAudio = true))

    @Test
    fun percentRead() {
        assertEquals(25, entry.percent)
        assertEquals(0, entry.copy(position = 0).percent)
        assertEquals(100, entry.copy(position = 200).percent)
        assertEquals(0, entry.copy(sentences = 0).percent)
    }

    @Test
    fun missingDetailsFallBack() {
        val p = Properties().apply { setProperty("title", "Mount Everest") }
        val loaded = LibraryEntry.fromProperties("Mount Everest", p, hasAudio = false)!!
        assertEquals("Mount Everest", loaded.name)
        assertEquals(0, loaded.position)
    }

    @Test
    fun unusableDetailsAreIgnored() = assertNull(LibraryEntry.fromProperties("x", Properties(), hasAudio = false))
}

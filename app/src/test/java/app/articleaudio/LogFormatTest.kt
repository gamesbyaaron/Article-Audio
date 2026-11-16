package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFormatTest {
    @Test
    fun lineContainsLevelAndMessage() {
        val line = LogFormat.line(0L, "INFO ", "hello")
        assertTrue(line.endsWith("INFO  hello"))
    }

    @Test
    fun lineIncludesStackTraceForErrors() {
        val line = LogFormat.line(0L, "ERROR", "boom", IllegalStateException("bad state"))
        assertTrue(line.contains("ERROR boom\n"))
        assertTrue(line.contains("java.lang.IllegalStateException: bad state"))
    }

    @Test
    fun trimKeepsShortTextUnchanged() {
        assertEquals("a\nb\n", LogFormat.trimToTail("a\nb\n", 100))
    }

    @Test
    fun trimKeepsNewestWholeLines() {
        val text = "first line\nsecond line\nthird line\n"
        assertEquals("third line\n", LogFormat.trimToTail(text, 15))
    }
}

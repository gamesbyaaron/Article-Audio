package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueStateTest {
    @Test
    fun playNextGoesFirstAndAddGoesLast() {
        val q = QueueState()
        q.add("a", current = "x")
        q.add("b", current = "x")
        q.playNext("c", current = "x")
        assertEquals(listOf("c", "a", "b"), q.upNext)
    }

    @Test
    fun noDuplicatesAndNotTheCurrentArticle() {
        val q = QueueState(listOf("a", "b"))
        q.add("a", current = "x")
        q.add("x", current = "x")
        q.playNext("b", current = "x")
        assertEquals(listOf("b", "a"), q.upNext)
    }

    @Test
    fun nextAndPreviousWalkTheLine() {
        val q = QueueState(listOf("b", "c"))
        assertEquals("b", q.takeNext(current = "a"))
        assertEquals("c", q.takeNext(current = "b"))
        assertNull(q.takeNext(current = "c"))
        assertEquals("b", q.takePrevious(current = "c"))
        assertEquals(listOf("c"), q.upNext)
        assertEquals("a", q.takePrevious(current = "b"))
        assertEquals(listOf("b", "c"), q.upNext)
        assertNull(q.takePrevious(current = "a"))
    }

    @Test
    fun reorderAndRemove() {
        val q = QueueState(listOf("a", "b", "c"))
        q.move(2, 0)
        assertEquals(listOf("c", "a", "b"), q.upNext)
        q.remove(1)
        assertEquals(listOf("c", "b"), q.upNext)
    }

    @Test
    fun forgetRemovesEverywhere() {
        val q = QueueState(listOf("a", "b"), listOf("b", "c"))
        q.forget("b")
        assertEquals(listOf("a"), q.upNext)
        assertEquals(listOf("c"), q.history)
    }

    @Test
    fun survivesSaving() {
        val q = QueueState(listOf("Alan Turing", "Mount Everest"), listOf("Rome"))
        val back = QueueState.parse(q.serialize())
        assertEquals(q.upNext, back.upNext)
        assertEquals(q.history, back.history)
        assertEquals(emptyList<String>(), QueueState.parse("").upNext)
    }
}

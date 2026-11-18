package app.articleaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicPickerTest {
    private val vietnam = "Vietnam War" to listOf(
        "Category:Vietnam War", "Category:Cold War conflicts", "Category:Wars involving the United States",
        "Category:Articles with short description", "Category:1955 in Vietnam", "Category:Proxy wars",
    )
    private val korea = "Korean War" to listOf(
        "Category:Korean War", "Category:Cold War conflicts", "Category:Wars involving the United States",
        "Category:Use dmy dates from March 2024", "Category:Proxy wars",
    )
    private val turing = "Alan Turing" to listOf(
        "Category:1912 births", "Category:English mathematicians", "Category:Computer scientists",
        "Category:Living people", "Category:Alumni of King's College, Cambridge",
    )

    @Test
    fun sharedTopicsComeFirst() {
        val picked = TopicPicker.pick(listOf(turing, vietnam, korea)).map { it.label }
        // The three categories both wars share, shortest (broadest) name first among equals.
        assertEquals(setOf("Cold War conflicts", "Wars involving the United States", "Proxy wars"), picked.take(3).toSet())
        assertEquals("Proxy wars", picked.first())
    }

    @Test
    fun thenOneTopicPerArticle() {
        val picked = TopicPicker.pick(listOf(turing, vietnam, korea)).map { it.label }
        assertTrue(picked.toString(), "Computer scientists" in picked)
    }

    @Test
    fun anArticlesOwnCategoryIsNotSuggested() {
        val picked = TopicPicker.pick(listOf(vietnam, korea)).map { it.label }
        assertTrue("Vietnam War" !in picked && "Korean War" !in picked)
    }

    @Test
    fun maintenanceAndDateCategoriesAreDropped() {
        assertNull(TopicPicker.clean("Category:Articles with short description"))
        assertNull(TopicPicker.clean("Category:1912 births"))
        assertNull(TopicPicker.clean("Category:1955 in Vietnam"))
        assertNull(TopicPicker.clean("Category:Use dmy dates from March 2024"))
        assertNull(TopicPicker.clean("Category:Living people"))
        assertEquals("Proxy wars", TopicPicker.clean("Category:Proxy wars"))
    }

    @Test
    fun nearDuplicatesAreSkipped() {
        val a = "A" to listOf("Category:Ancient Rome", "Category:Ancient Rome in art")
        val b = "B" to listOf("Category:Ancient Rome", "Category:Ancient Rome in art")
        assertEquals(listOf("Ancient Rome"), TopicPicker.pick(listOf(a, b)).map { it.label })
    }

    @Test
    fun respectsTheLimit() = assertEquals(2, TopicPicker.pick(listOf(turing, vietnam, korea), max = 2).size)

    @Test
    fun nothingHeardNothingSuggested() = assertEquals(emptyList<TopicPicker.Picked>(), TopicPicker.pick(emptyList()))
}

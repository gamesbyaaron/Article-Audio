package app.articleaudio

/**
 * What plays after the current article ("up next") and what played before it ("history"),
 * as library article ids. Pure Kotlin, unit tested.
 */
class QueueState(upNext: List<String> = emptyList(), history: List<String> = emptyList()) {
    val upNext: MutableList<String> = upNext.toMutableList()
    val history: MutableList<String> = history.toMutableList()

    /** Puts [id] first in line, moving it if it is already queued. */
    fun playNext(id: String, current: String?) {
        if (id == current) return
        upNext.remove(id)
        upNext.add(0, id)
    }

    /** Adds [id] to the end of the line unless it is already there or playing. */
    fun add(id: String, current: String?) {
        if (id == current || id in upNext) return
        upNext.add(id)
    }

    fun remove(index: Int) {
        if (index in upNext.indices) upNext.removeAt(index)
    }

    fun move(from: Int, to: Int) {
        if (from !in upNext.indices || to !in upNext.indices) return
        upNext.add(to, upNext.removeAt(from))
    }

    /** Replaces what comes next, for example when a collection starts. */
    fun replace(ids: List<String>) {
        upNext.clear()
        upNext.addAll(ids)
    }

    /** The next article to play, remembering [current] so Previous can return to it. */
    fun takeNext(current: String?): String? {
        if (upNext.isEmpty()) return null
        val next = upNext.removeAt(0)
        if (current != null) remember(current)
        return next
    }

    /** The article played before, putting [current] back at the front of the line. */
    fun takePrevious(current: String?): String? {
        if (history.isEmpty()) return null
        val previous = history.removeAt(history.lastIndex)
        if (current != null && current != previous) {
            upNext.remove(current)
            upNext.add(0, current)
        }
        return previous
    }

    /** Removes an article everywhere, for example after it is deleted from the library. */
    fun forget(id: String) {
        upNext.removeAll { it == id }
        history.removeAll { it == id }
    }

    private fun remember(id: String) {
        history.remove(id)
        history.add(id)
        while (history.size > 30) history.removeAt(0)
    }

    fun serialize(): String = "next\t" + upNext.joinToString("\t") + "\nhistory\t" + history.joinToString("\t")

    companion object {
        fun parse(text: String): QueueState {
            val lines = text.lines().associate { line ->
                val parts = line.split('\t')
                parts.first() to parts.drop(1).filter { it.isNotEmpty() }
            }
            return QueueState(lines["next"].orEmpty(), lines["history"].orEmpty())
        }
    }
}

package app.articleaudio

import java.util.Properties

/** One saved article as shown in the library. Pure Kotlin, unit tested. */
data class LibraryEntry(
    val id: String,
    /** The Wikipedia title, used for fetching and attribution. */
    val title: String,
    /** The name shown in the library; the owner can change it. */
    val name: String,
    val fetchedAt: Long,
    val openedAt: Long,
    val chars: Int,
    /** Sentence to resume live reading from, and how many sentences there are. */
    val position: Int,
    val sentences: Int,
    /** Place to resume the downloaded audio from, in milliseconds. */
    val audioPositionMs: Int,
    val hasAudio: Boolean = false,
    /** Credit line for the article's picture; empty if it has none. */
    val imageCredit: String = "",
    /** True once the app has looked for a picture, so it does not keep asking. */
    val imageChecked: Boolean = false,
    /** True once the article has been listened to the end. */
    val finished: Boolean = false,
    /** Wikipedia's short description, such as "2024 video game". */
    val description: String = "",
    /** Kept on purpose with the Save button (or by downloading it). */
    val saved: Boolean = false,
    /** Listened to, so shown under History until history is cleared. */
    val inHistory: Boolean = false,
) {
    /** Which generic icon to show when there is no free picture. */
    val category: String get() = Category.of(description, title)

    val percent: Int get() = if (sentences <= 1) 0 else (position * 100 / (sentences - 1)).coerceIn(0, 100)

    fun toProperties(): Properties = Properties().apply {
        setProperty("title", title)
        setProperty("name", name)
        setProperty("fetchedAt", fetchedAt.toString())
        setProperty("openedAt", openedAt.toString())
        setProperty("chars", chars.toString())
        setProperty("position", position.toString())
        setProperty("sentences", sentences.toString())
        setProperty("audioPositionMs", audioPositionMs.toString())
        setProperty("imageCredit", imageCredit)
        setProperty("imageChecked", imageChecked.toString())
        setProperty("finished", finished.toString())
        setProperty("description", description)
        setProperty("saved", saved.toString())
        setProperty("inHistory", inHistory.toString())
    }

    companion object {
        /** Null if the saved details are unusable. */
        fun fromProperties(id: String, p: Properties, hasAudio: Boolean): LibraryEntry? {
            val title = p.getProperty("title") ?: return null
            fun long(key: String) = p.getProperty(key)?.toLongOrNull() ?: 0L
            return LibraryEntry(
                id = id,
                title = title,
                name = p.getProperty("name") ?: title,
                fetchedAt = long("fetchedAt"),
                openedAt = long("openedAt"),
                chars = long("chars").toInt(),
                position = long("position").toInt(),
                sentences = long("sentences").toInt(),
                audioPositionMs = long("audioPositionMs").toInt(),
                hasAudio = hasAudio,
                imageCredit = p.getProperty("imageCredit") ?: "",
                imageChecked = p.getProperty("imageChecked") == "true",
                finished = p.getProperty("finished") == "true",
                description = p.getProperty("description") ?: "",
                // Articles from before Saved and History existed: all were listened to, and
                // downloaded ones count as saved.
                saved = p.getProperty("saved")?.toBoolean() ?: hasAudio,
                inHistory = p.getProperty("inHistory")?.toBoolean() ?: true,
            )
        }
    }
}

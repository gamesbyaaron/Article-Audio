package app.articleaudio

/** Readable names for the speech engine's voice codes. Pure Kotlin, unit tested. */
object VoiceNames {
    // Google's speech engine names voices like "en-us-x-iom-local". Android does not say whether
    // a voice is male or female, so this table is a best guess from the known codes.
    private val FEMALE = setOf(
        "sfg", "iob", "iog", "tpc", "tpf", "gba", "gbc", "gbg", "fis", "aua", "auc", "ahp", "cxx", "enc", "tfn",
    )
    private val MALE = setOf("iol", "iom", "tpd", "gbb", "gbd", "rjs", "aub", "aud", "ene", "end")
    private val code = Regex("-x-([a-z]+)")

    fun gender(voiceName: String): String? = when (code.find(voiceName)?.groupValues?.get(1)) {
        in FEMALE -> "female"
        in MALE -> "male"
        else -> null
    }

    /** "English (United States) · Voice 3 · male". */
    fun label(accent: String, number: Int, voiceName: String): String =
        listOfNotNull(accent, "Voice $number", gender(voiceName)).joinToString(" · ")
}

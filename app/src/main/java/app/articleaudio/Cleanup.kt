package app.articleaudio

/** One rewrite made by the cleanup rules, shown on the Changes screen. */
data class Change(val rule: String, val before: String, val after: String)

/**
 * Rewrites written notation into text that sounds natural when spoken. It changes notation,
 * never meaning, and leaves anything ambiguous alone. Pure Kotlin, one unit test per rule.
 */
object Cleanup {
    class Result(val text: String, val changes: List<Change>)

    fun apply(input: String): Result {
        val changes = mutableListOf<Change>()
        // Wikipedia puts non-breaking and thin spaces between numbers and units.
        var text = input.replace(Regex("[\\u00A0\\u2009\\u202F]"), " ")
        text = removeMaths(text, changes)
        for (rule in RULES) text = rule.apply(text, changes)
        return Result(tidy(text), changes)
    }

    /**
     * [replace] returns the new text for a match, or null to leave it. Rules that need to look
     * at the words before the match use [context] instead, which also gets the whole text.
     */
    private class Rule(
        val name: String,
        pattern: String,
        val context: ((MatchResult, String) -> String?)? = null,
        val replace: (MatchResult) -> String? = { null },
    ) {
        val regex = Regex(pattern)

        fun apply(text: String, changes: MutableList<Change>): String {
            val out = StringBuilder()
            var last = 0
            for (m in regex.findAll(text)) {
                val replacement = (if (context != null) context.invoke(m, text) else replace(m)) ?: continue
                if (replacement == m.value) continue
                out.append(text, last, m.range.first).append(replacement)
                last = m.range.last + 1
                changes += Change(name, m.value.trim(), replacement.trim().ifEmpty { "(removed)" })
            }
            if (last == 0) return text
            return out.append(text, last, text.length).toString()
        }
    }

    // ---- Building blocks -------------------------------------------------------------------

    private const val MONTH =
        "(?:January|February|March|April|May|June|July|August|September|October|November|December)"
    private const val ERA = "(?: (?:BCE|BC|AD|CE))"
    private const val YEAR = "(?:(?:1\\d{3}|20\\d{2})$ERA?|\\d{1,4}$ERA)"
    private const val DATE =
        "(?:circa )?(?:\\d{1,2} $MONTH $YEAR|$MONTH \\d{1,2}, $YEAR|$MONTH $YEAR|$YEAR)"
    private const val NUM = "\\d[\\d,]*(?:\\.\\d+)?"
    private const val UNIT = "km/h|km2|km²|m2|m²|sq mi|sq ft|mph|km|mm|cm|kg|lbs?|mi|ft|°C|°F|m|g|in"

    /** A parenthetical that is only a unit conversion, such as "(3.1 mi)" or "(6–9 mi; 5 nmi)". */
    private val conversion = Regex(
        "^(?:about |approximately |~)?(?:minus |[−-])?$NUM(?:\\s?[–-]\\s?(?:minus |[−-])?$NUM)?\\s?" +
            "(?:km/h|km2|km²|m2|m²|sq mi|sq ft|sq yd|mph|km|mm|cm|kg|lbs?|mi|ft|°C|°F|K|m|g|in|oz|yd|ha|acres?|nmi|kn|st)" +
            "(?![A-Za-z])"
    )

    /** Singular and plural spoken names. */
    private val UNIT_NAMES = mapOf(
        "km" to ("kilometer" to "kilometers"),
        "m" to ("meter" to "meters"),
        "cm" to ("centimeter" to "centimeters"),
        "mm" to ("millimeter" to "millimeters"),
        "mi" to ("mile" to "miles"),
        "ft" to ("foot" to "feet"),
        "in" to ("inch" to "inches"),
        "kg" to ("kilogram" to "kilograms"),
        "g" to ("gram" to "grams"),
        "lb" to ("pound" to "pounds"),
        "lbs" to ("pound" to "pounds"),
        "mph" to ("mile per hour" to "miles per hour"),
        "km/h" to ("kilometer per hour" to "kilometers per hour"),
        "°C" to ("degree Celsius" to "degrees Celsius"),
        "°F" to ("degree Fahrenheit" to "degrees Fahrenheit"),
        "km2" to ("square kilometer" to "square kilometers"),
        "km²" to ("square kilometer" to "square kilometers"),
        "m2" to ("square meter" to "square meters"),
        "m²" to ("square meter" to "square meters"),
        "sq mi" to ("square mile" to "square miles"),
        "sq ft" to ("square foot" to "square feet"),
    )

    private val ipaInSlashes = Regex("/[^/]*[^\\x00-\\x7F/][^/]*/")
    private val ipaInBrackets = Regex("\\[[^\\]]*[^\\x00-\\x7F][^\\]]*\\]")
    private val monthAndYearOnly = Regex("^$MONTH $YEAR$")

    private val romanNumeral = Regex("^[IVXLCDM]+$")

    /**
     * True if [token] looks like an abbreviation of the words just before it: mostly capitals,
     * not a Roman numeral, its first letter starts one of those words and the rest of its
     * capitals follow in order. "London (UK)" and "the film (PG)" fail this and are left alone.
     */
    private fun isAbbreviationOf(before: String, token: String): Boolean {
        val capitals = token.filter { it.isUpperCase() }
        if (capitals.length < 2 || capitals.length * 2 < token.count { it.isLetter() }) return false
        if (romanNumeral.matches(token)) return false
        val words = before.trim().split(Regex("\\s+")).takeLast(capitals.length + 3)
        val wanted = capitals.lowercase()
        for (i in words.indices) {
            if (!words[i].trimStart('(', '"', '“', '\'').startsWith(wanted[0], ignoreCase = true)) continue
            val rest = words.drop(i).joinToString(" ").lowercase()
            var position = 0
            val found = wanted.all { letter ->
                val at = rest.indexOf(letter, position)
                position = at + 1
                at >= 0
            }
            if (found) return true
        }
        return false
    }

    /** Turns " (inner)" into ", inner," so the voice pauses as it would for brackets. */
    private fun commas(inner: String, following: String): String =
        if (following.isEmpty()) ", $inner," else ", $inner$following"

    // ---- The rules, applied in this order --------------------------------------------------

    private val RULES = listOf(
        Rule(
            "Citation marker",
            "\\[(?:\\d{1,3}|[a-z]|note \\d+|nb \\d+|citation needed|clarification needed|better source needed|when\\?|who\\?)\\]",
        ) { "" },

        // "(/ˈæləmoʊ/ AL-ə-moh)" goes entirely; "(/.../; 23 June 1912 – ...)" keeps the dates.
        Rule("Pronunciation guide", "( ?)\\(([^()]*)\\)") { m ->
            val inner = m.groupValues[2]
            if (!ipaInSlashes.containsMatchIn(inner) && !ipaInBrackets.containsMatchIn(inner)) return@Rule null
            val kept = inner.split(';')
                .filterNot { ipaInSlashes.containsMatchIn(it) || ipaInBrackets.containsMatchIn(it) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (kept.isEmpty()) "" else m.groupValues[1] + "(" + kept.joinToString("; ") + ")"
        },

        // Wikipedia's plain text leaves "(; 23 June 1912 ...)" or "()" where it stripped something.
        Rule("Leftover punctuation", "\\(\\s*[;,:]+\\s*") { "(" },
        Rule("Leftover punctuation", "\\s*[;,:]+\\s*\\)") { ")" },
        Rule("Empty brackets", " ?\\(\\s*\\)") { "" },

        // "World Health Organization (WHO)" -> "World Health Organization, abbreviated to WHO,".
        Rule(
            "Abbreviation in brackets",
            " ?\\(([A-Za-z][A-Za-z0-9&.-]{1,9})\\)([.,;:!?)]?)",
            context = { m, text ->
                val token = m.groupValues[1]
                if (isAbbreviationOf(text.substring(0, m.range.first), token)) {
                    commas("abbreviated to $token", m.groupValues[2])
                } else {
                    null
                }
            },
        ),

        Rule("Minus sign", "(?<![\\w.])−(?=\\d)") { "minus " },

        Rule("Circa", "\\b(?:c|ca)\\.\\s*(?=\\d)") { "circa " },

        // "1964–1968", "23 June 1912 – 7 June 1954", "2001–present".
        Rule("Date range", "(?<![\\d–-])($DATE)\\s?[–—-]\\s?($DATE|present)(?![\\d–-])") { m ->
            "${m.groupValues[1]} to ${m.groupValues[2]}"
        },
        // "1964–68" (en dash only; hyphens here are too often something else).
        Rule("Date range", "(?<![\\d–-])((?:1\\d{3}|20\\d{2}))–(\\d{2})(?![\\d–-])") { m ->
            "${m.groupValues[1]} to ${m.groupValues[2]}"
        },
        // "12–15 March 1966" and "March 12–15, 1966".
        Rule("Date range", "(?<![\\d–-])(\\d{1,2})[–-](\\d{1,2}) ($MONTH)\\b") { m ->
            "${m.groupValues[1]} to ${m.groupValues[2]} ${m.groupValues[3]}"
        },
        Rule("Date range", "\\b($MONTH) (\\d{1,2})[–-](\\d{1,2})(?![\\d–-])") { m ->
            "${m.groupValues[1]} ${m.groupValues[2]} to ${m.groupValues[3]}"
        },
        // "23 June – 7 July 1954" and "June–July 1954".
        Rule("Date range", "\\b((?:\\d{1,2} )?$MONTH)\\s?[–—]\\s?((?:\\d{1,2} )?$MONTH)\\b") { m ->
            "${m.groupValues[1]} to ${m.groupValues[2]}"
        },

        // "5 km (3.1 mi)" -> "5 kilometers"; "10–15 km" -> "10 to 15 kilometers".
        Rule(
            "Unit",
            "(?<![\\w.,$£€–-])($NUM)(?:\\s?[–-]\\s?($NUM))?\\s?($UNIT)(?![\\w/²])(?: \\(([^()]*)\\))?",
        ) { m ->
            val first = m.groupValues[1]
            val second = m.groupValues[2]
            val unit = m.groupValues[3]
            val bracket = m.groups[4]?.value
            val isConversion = bracket != null && conversion.containsMatchIn(bracket)
            // "in" is nearly always the ordinary word; only trust it when a conversion follows.
            if (unit == "in" && !isConversion) return@Rule null
            val (singular, plural) = UNIT_NAMES.getValue(unit)
            val name = if (second.isEmpty() && first == "1") singular else plural
            val number = if (second.isEmpty()) first else "$first to $second"
            val rest = if (bracket != null && !isConversion) " ($bracket)" else ""
            "$number $name$rest"
        },

        Rule("Abbreviation", "\\b[Nn]os\\.\\s?(?=\\d)") { "numbers " },
        Rule("Abbreviation", "\\b[Nn]o\\.\\s?(?=\\d)") { "number " },
        Rule("Abbreviation", "\\be\\.g\\.") { "for example" },
        Rule("Abbreviation", "\\bi\\.e\\.") { "that is" },
        Rule("Abbreviation", "\\bvs\\.?(?=\\s)") { "versus" },
        Rule("Abbreviation", "\\bapprox\\.") { "approximately" },

        // "Smith & Jones" but not "R&D" or "AT&T".
        Rule("Ampersand", "(?<=\\S) & (?=\\S)") { " and " },

        Rule("Born", " ?\\((?:born\\s+|b\\.\\s*(?=\\d|circa ))([^()]{1,80})\\)([.,;:!?)]?)") { m ->
            commas("born ${m.groupValues[1]}", m.groupValues[2])
        },
        Rule("Died", " ?\\((?:died\\s+|d\\.\\s*(?=\\d|circa ))([^()]{1,80})\\)([.,;:!?)]?)") { m ->
            commas("died ${m.groupValues[1]}", m.groupValues[2])
        },
        Rule("Reigned", " ?\\(r\\.\\s*(?=\\d|circa )([^()]{1,80})\\)([.,;:!?)]?)") { m ->
            commas("reigned ${m.groupValues[1]}", m.groupValues[2])
        },
        // "(1920 to 1987)" -> ", 1920 to 1987,"; "(April 1964 to May 1964)" -> "from April 1964 to May 1964".
        Rule("Dates in brackets", "( ?)\\(($DATE) to ($DATE|present)\\)([.,;:!?)]?)") { m ->
            val from = m.groupValues[2]
            val to = m.groupValues[3]
            if (monthAndYearOnly.matches(from) && monthAndYearOnly.matches(to)) {
                "${m.groupValues[1]}from $from to $to${m.groupValues[4]}"
            } else {
                commas("$from to $to", m.groupValues[4])
            }
        },
    )

    /** Removes LaTeX such as "{\displaystyle E=mc^{2}}", which has nested braces. */
    private fun removeMaths(text: String, changes: MutableList<Change>): String {
        val marker = "{\\displaystyle"
        var start = text.indexOf(marker)
        if (start < 0) return text
        val out = StringBuilder()
        var last = 0
        while (start >= 0) {
            var depth = 0
            var end = start
            while (end < text.length) {
                if (text[end] == '{') depth++
                if (text[end] == '}' && --depth == 0) break
                end++
            }
            if (end >= text.length) break // unbalanced: leave the rest alone
            out.append(text, last, start)
            val removed = text.substring(start, end + 1)
            changes += Change("Maths markup", if (removed.length > 60) removed.take(57) + "..." else removed, "(removed)")
            last = end + 1
            start = text.indexOf(marker, last)
        }
        return out.append(text, last, text.length).toString()
    }

    private fun tidy(text: String): String = text
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex(" +([,.;:!?)])"), "$1")
        .replace(Regex("\\( +"), "(")
        .replace(Regex(",{2,}"), ",")
        .replace(Regex(",([.;:!?])"), "$1")
        .trim()
}

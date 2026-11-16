package app.articleaudio

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Pure formatting for debug log lines, kept free of Android code so it can be unit tested. */
object LogFormat {
    fun line(timeMillis: Long, level: String, message: String, error: Throwable? = null): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timeMillis))
        val base = "$time $level $message"
        return if (error == null) base else base + "\n" + error.stackTraceToString().trimEnd()
    }

    /** Keeps the newest part of the log when it grows past [maxChars], cutting at a line break. */
    fun trimToTail(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text
        val tail = text.substring(text.length - maxChars)
        val firstBreak = tail.indexOf('\n')
        return if (firstBreak >= 0) tail.substring(firstBreak + 1) else tail
    }
}

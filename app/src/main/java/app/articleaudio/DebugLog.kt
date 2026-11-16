package app.articleaudio

import android.content.Context
import android.util.Log
import java.io.File

/** In-app replacement for logcat: everything goes to a file the Log screen can show. */
object DebugLog {
    private const val MAX_CHARS = 300_000
    private var file: File? = null

    fun init(context: Context) {
        file = File(context.filesDir, "debug.log")
    }

    fun i(message: String) = write("INFO ", message, null)

    fun e(message: String, error: Throwable? = null) = write("ERROR", message, error)

    @Synchronized
    private fun write(level: String, message: String, error: Throwable?) {
        val line = LogFormat.line(System.currentTimeMillis(), level, message, error)
        Log.println(if (level == "ERROR") Log.ERROR else Log.INFO, "ArticleAudio", line)
        val f = file ?: return
        try {
            f.appendText(line + "\n")
            if (f.length() > MAX_CHARS * 2) {
                f.writeText(LogFormat.trimToTail(f.readText(), MAX_CHARS))
            }
        } catch (t: Throwable) {
            Log.e("ArticleAudio", "Could not write debug log", t)
        }
    }

    @Synchronized
    fun readAll(): String = try {
        file?.takeIf { it.exists() }?.readText().orEmpty()
    } catch (t: Throwable) {
        "Could not read log: $t"
    }

    @Synchronized
    fun clear() {
        file?.writeText("")
    }
}

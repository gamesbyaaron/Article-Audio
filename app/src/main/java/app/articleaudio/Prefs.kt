package app.articleaudio

import android.content.Context
import android.content.SharedPreferences

/** Settings that are remembered between launches. */
object Prefs {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    /** Name of the chosen voice, or null for the phone's default. */
    var voiceName: String?
        get() = prefs.getString("voice", null)
        set(value) = prefs.edit().putString("voice", value).apply()

    /** Reading speed; 1.0 is normal. */
    var speed: Float
        get() = prefs.getFloat("speed", 1f)
        set(value) = prefs.edit().putFloat("speed", value).apply()

    /** Whether the notification permission has been asked for, so it is only asked once. */
    var askedForNotifications: Boolean
        get() = prefs.getBoolean("askedForNotifications", false)
        set(value) = prefs.edit().putBoolean("askedForNotifications", value).apply()
}

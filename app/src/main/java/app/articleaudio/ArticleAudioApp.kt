package app.articleaudio

import android.app.Application
import android.os.Build

class ArticleAudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLog.init(this)

        // Record any crash before Android closes the app; it shows on the Log screen next launch.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            DebugLog.e("CRASH on thread ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }

        Prefs.init(this)
        Library.init(this)
        Ambient.init()
        Reader.init(this)
        Exporter.init(this)

        DebugLog.i(
            "App started: version ${BuildConfig.VERSION_NAME}, " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }
}

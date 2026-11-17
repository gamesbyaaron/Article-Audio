package app.articleaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps a download going with the screen off or the app in the background, and shows its
 * progress with a Cancel button. The work itself is done by [Exporter].
 */
class ExportService : Service() {
    private lateinit var wakeLock: PowerManager.WakeLock
    private var shownPercent = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArticleAudio:download")
        wakeLock.acquire(6 * 60 * 60 * 1000L)
        Exporter.onChanged = ::refresh
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires this promptly after every startForegroundService call.
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(PROGRESS_ID, progressNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(PROGRESS_ID, progressNotification())
            }
        } catch (t: Throwable) {
            DebugLog.e("Could not keep the download in the foreground; it may stop when the screen is off", t)
        }
        if (intent?.action == ACTION_CANCEL) Exporter.cancel()
        refresh()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Exporter.onChanged = null
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun refresh() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Exporter.running) {
            val percent = (Exporter.progress * 100).toInt()
            if (percent != shownPercent) {
                shownPercent = percent
                manager.notify(PROGRESS_ID, progressNotification())
            }
            return
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        val text = when {
            Exporter.savedAs.isNotEmpty() -> "Audio ready: saved to ${Exporter.savedAs}"
            Exporter.error.isNotEmpty() -> Exporter.error
            else -> null // cancelled
        }
        if (text != null) {
            manager.notify(
                RESULT_ID,
                Notification.Builder(this, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(Exporter.title)
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(openApp())
                    .setAutoCancel(true)
                    .build()
            )
        }
        stopSelf()
    }

    private fun progressNotification(): Notification {
        val percent = (Exporter.progress * 100).toInt()
        val cancel = PendingIntent.getForegroundService(
            this, 1, Intent(this, ExportService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Making audio: ${Exporter.title}")
            .setContentText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel), "Cancel", cancel,
                ).build()
            )
            .build()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        private const val CHANNEL = "downloads"
        private const val PROGRESS_ID = 2
        private const val RESULT_ID = 3
        private const val ACTION_CANCEL = "app.articleaudio.CANCEL_DOWNLOAD"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, ExportService::class.java))
            } catch (t: Throwable) {
                DebugLog.e("Could not keep the download in the foreground; it may stop when the screen is off", t)
            }
        }
    }
}

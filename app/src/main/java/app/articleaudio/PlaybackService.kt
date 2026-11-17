package app.articleaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Keeps reading going with the screen off or the app in the background, and puts play, pause
 * and skip controls in the notification and on the lock screen. The reading itself is done by
 * [Reader]; this service only tells Android about it. It runs from Play until Stop or the end
 * of the article.
 */
class PlaybackService : Service() {
    private lateinit var session: MediaSession
    private lateinit var wakeLock: PowerManager.WakeLock

    private val headphonesUnplugged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Reader.playing) {
                DebugLog.i("Headphones disconnected: pausing")
                Reader.pause()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Reading", NotificationManager.IMPORTANCE_LOW)
        )
        session = MediaSession(this, "ArticleAudio")
        session.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = Reader.play()
            override fun onPause() = Reader.pause()
            override fun onSkipToNext() = Reader.skip(1)
            override fun onSkipToPrevious() = Reader.skip(-1)
            override fun onStop() = Reader.stop()
        })
        session.isActive = true
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ArticleAudio:reading")
        ContextCompat.registerReceiver(
            this,
            headphonesUnplugged,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        Reader.onPlaybackChanged = ::refresh
        DebugLog.i("Background playback started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires this promptly after every startForegroundService call.
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        } catch (t: Throwable) {
            DebugLog.e("Could not start background playback; reading may stop when the screen is off", t)
        }
        when (intent?.action) {
            ACTION_TOGGLE -> if (Reader.playing) Reader.pause() else Reader.play()
            ACTION_NEXT -> Reader.skip(1)
            ACTION_PREVIOUS -> Reader.skip(-1)
            ACTION_STOP -> Reader.stop()
        }
        refresh()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Reader.onPlaybackChanged = null
        unregisterReceiver(headphonesUnplugged)
        if (wakeLock.isHeld) wakeLock.release()
        session.release()
        DebugLog.i("Background playback ended")
        super.onDestroy()
    }

    /** Brings the lock-screen controls and notification in line with [Reader]. */
    private fun refresh() {
        if (!Reader.active) {
            if (wakeLock.isHeld) wakeLock.release()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        val playing = Reader.playing
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, Reader.article?.title ?: "Article Audio")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "From Wikipedia")
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP
                )
                .setState(
                    if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    PlaybackState.PLAYBACK_POSITION_UNKNOWN,
                    if (playing) Reader.speed else 0f,
                )
                .build()
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
        // Keep the processor awake while reading so the next paragraph is always queued in time.
        if (playing && !wakeLock.isHeld) wakeLock.acquire(4 * 60 * 60 * 1000L)
        if (!playing && wakeLock.isHeld) wakeLock.release()
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val playing = Reader.playing
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(Reader.article?.title ?: "Article Audio")
            .setContentText("From Wikipedia")
            .setContentIntent(openApp)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .addAction(action(android.R.drawable.ic_media_previous, "Back", ACTION_PREVIOUS, 1))
            .addAction(
                if (playing) action(android.R.drawable.ic_media_pause, "Pause", ACTION_TOGGLE, 2)
                else action(android.R.drawable.ic_media_play, "Play", ACTION_TOGGLE, 2)
            )
            .addAction(action(android.R.drawable.ic_media_next, "Next", ACTION_NEXT, 3))
            .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP, 4))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun action(icon: Int, title: String, action: String, requestCode: Int): Notification.Action {
        val intent = PendingIntent.getForegroundService(
            this, requestCode, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(Icon.createWithResource(this, icon), title, intent).build()
    }

    companion object {
        private const val CHANNEL = "reading"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "app.articleaudio.TOGGLE"
        private const val ACTION_NEXT = "app.articleaudio.NEXT"
        private const val ACTION_PREVIOUS = "app.articleaudio.PREVIOUS"
        private const val ACTION_STOP = "app.articleaudio.STOP"

        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, PlaybackService::class.java))
            } catch (t: Throwable) {
                DebugLog.e("Could not start background playback; reading may stop when the screen is off", t)
            }
        }
    }
}

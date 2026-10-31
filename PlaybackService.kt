package com.rhythm.music

import android.app.*
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PlaybackService : Service() {
    companion object {
        const val PLAY = "com.rhythm.music.PLAY"
        const val TOGGLE = "com.rhythm.music.TOGGLE"
        const val NEXT = "com.rhythm.music.NEXT"
        const val PREVIOUS = "com.rhythm.music.PREVIOUS"
        const val SEEK = "com.rhythm.music.SEEK"
        const val STATE = "com.rhythm.music.STATE"
        const val CHANNEL = "rhythm_playback"
        const val NOTIFICATION_ID = 401
    }

    private var player: MediaPlayer? = null
    private var urls = arrayListOf<String>()
    private var names = arrayListOf<String>()
    private var artists = arrayListOf<String>()
    private var index = 0
    private var title = "Nothing playing"
    private var artist = "Choose a track"

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Rhythm playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PLAY -> {
                urls = intent.getStringArrayListExtra("urls") ?: arrayListOf()
                names = intent.getStringArrayListExtra("names") ?: arrayListOf()
                artists = intent.getStringArrayListExtra("artists") ?: arrayListOf()
                index = intent.getIntExtra("index", 0)
                startForeground(NOTIFICATION_ID, notification())
                playCurrent()
            }
            TOGGLE -> {
                startForeground(NOTIFICATION_ID, notification())
                try {
                    if (player == null) playCurrent()
                    else if (player!!.isPlaying) player!!.pause() else player!!.start()
                } catch (_: Exception) { }
                publish()
            }
            NEXT -> {
                if (index < urls.lastIndex) index++
                startForeground(NOTIFICATION_ID, notification())
                playCurrent()
            }
            PREVIOUS -> {
                if (index > 0) index--
                startForeground(NOTIFICATION_ID, notification())
                playCurrent()
            }
            SEEK -> {
                try {
                    val p = player ?: return START_NOT_STICKY
                    val permille = intent.getIntExtra("position", 0).coerceIn(0, 1000)
                    p.seekTo((p.duration.toLong() * permille / 1000).toInt())
                    publish()
                } catch (_: Exception) { }
            }
        }
        return START_NOT_STICKY
    }

    private fun playCurrent() {
        if (urls.isEmpty() || index !in urls.indices) { publish(); return }
        title = names.getOrNull(index) ?: "Unknown track"
        artist = artists.getOrNull(index) ?: "Unknown artist"
        try {
            player?.release()
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setOnPreparedListener { mp ->
                    try { mp.start(); publish() } catch (_: Exception) { publish() }
                }
                setOnCompletionListener {
                    if (index < urls.lastIndex) { index++; playCurrent() }
                    else publish()
                }
                setOnErrorListener { _, _, _ -> publish(); true }
                setDataSource(urls[index])
                prepareAsync()
            }
            publish()
        } catch (_: Exception) {
            player?.release(); player = null; publish()
        }
    }

    private fun publish() {
        val p = player
        val playing = try { p?.isPlaying == true } catch (_: Exception) { false }
        val duration = try { p?.duration ?: 0 } catch (_: Exception) { 0 }
        val position = try { p?.currentPosition ?: 0 } catch (_: Exception) { 0 }
        sendBroadcast(Intent(STATE).setPackage(packageName).apply {
            putExtra("title", title); putExtra("artist", artist)
            putExtra("playing", playing); putExtra("duration", duration)
            putExtra("position", position)
        })
        try { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification()) }
        catch (_: Exception) { }
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        fun action(label: String, command: String, code: Int): NotificationCompat.Action {
            val icon = when (command) {
                PREVIOUS -> android.R.drawable.ic_media_previous
                NEXT -> android.R.drawable.ic_media_next
                else -> if (player?.isPlaying == true) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            }
            val pi = PendingIntent.getService(this, code,
                Intent(this, PlaybackService::class.java).setAction(command),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            return NotificationCompat.Action(icon, label, pi)
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title).setContentText(artist)
            .setContentIntent(open).setOnlyAlertOnce(true)
            .setOngoing(player?.isPlaying == true)
            .addAction(action("Previous", PREVIOUS, 2))
            .addAction(action("Play/Pause", TOGGLE, 3))
            .addAction(action("Next", NEXT, 4)).build()
    }

    override fun onDestroy() { player?.release(); player = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}

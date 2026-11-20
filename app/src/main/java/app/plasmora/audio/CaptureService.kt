package app.plasmora.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.plasmora.MainActivity
import app.plasmora.R

/**
 * Foreground service that listens to (a) audio played by other apps such as YouTube Music,
 * using Android's AudioPlaybackCapture, and/or (b) the microphone as a backup.
 */
class CaptureService : Service() {
    companion object {
        const val ACTION_START = "app.plasmora.START"
        const val ACTION_STOP = "app.plasmora.STOP"
        const val EXTRA_CAPTURE = "capture"
        const val EXTRA_MIC = "mic"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "plasmora_capture"
        private const val NOTIF_ID = 42
        private const val SR = 48000

        fun stop(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, CaptureService::class.java).setAction(ACTION_STOP))
            } catch (_: Throwable) {
                // App is in the background: the service cannot be started just to stop it.
                try { ctx.stopService(Intent(ctx, CaptureService::class.java)) } catch (_: Throwable) { }
            }
        }
    }

    @Volatile private var running = false
    private var projection: MediaProjection? = null
    private var captureRecord: AudioRecord? = null
    private var micRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var micThread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == ACTION_STOP) {
            shutdown(); stopSelf(); return START_NOT_STICKY
        }
        shutdown()
        val wantCapture = intent.getBooleanExtra(EXTRA_CAPTURE, false) && Build.VERSION.SDK_INT >= 29
        val wantMic = intent.getBooleanExtra(EXTRA_MIC, true)

        createChannel()
        var type = 0
        if (wantCapture && Build.VERSION.SDK_INT >= 29) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (wantMic && Build.VERSION.SDK_INT >= 30) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
        } catch (t: Throwable) {
            AudioHub.setRunning(false, false, "Could not start audio service: ${t.message}")
            stopSelf(); return START_NOT_STICKY
        }

        running = true
        AudioHub.markCaptureStart()
        var captureOk = false
        var message = ""

        if (wantCapture) {
            try {
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                @Suppress("DEPRECATION")
                val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
                val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val mp = mpm.getMediaProjection(code, data!!) ?: throw IllegalStateException("No media projection")
                mp.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() { Handler(Looper.getMainLooper()).post { shutdown(); stopSelf() } }
                }, Handler(Looper.getMainLooper()))
                projection = mp
                val cfg = AudioPlaybackCaptureConfiguration.Builder(mp)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()
                val fmt = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SR)
                    .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                    .build()
                val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
                @Suppress("MissingPermission")
                val rec = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(cfg)
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(maxOf(minBuf * 2, 16384))
                    .build()
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    captureRecord = rec
                    captureOk = true
                } else {
                    rec.release(); message = "System-audio capture could not start."
                }
            } catch (t: Throwable) {
                message = "System-audio capture failed: ${t.message}"
            }
        }

        var micOk = false
        if (wantMic) {
            try {
                val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val unprocessed = Build.VERSION.SDK_INT >= 24 &&
                    am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
                val src = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.MIC
                val minBuf = AudioRecord.getMinBufferSize(SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                @Suppress("MissingPermission")
                val rec = AudioRecord(src, SR, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf * 2, 16384))
                if (rec.state == AudioRecord.STATE_INITIALIZED) { micRecord = rec; micOk = true } else rec.release()
            } catch (t: Throwable) {
                message += " Microphone failed: ${t.message}"
            }
        }

        AudioHub.setRunning(captureOk, micOk, message.trim())
        if (!captureOk && !micOk) { shutdown(); stopSelf(); return START_NOT_STICKY }

        captureRecord?.let { rec ->
            captureThread = Thread({ readLoop(rec, 2, true) }, "plasmora-capture").also { it.start() }
        }
        micRecord?.let { rec ->
            micThread = Thread({ readLoop(rec, 1, false) }, "plasmora-mic").also { it.start() }
        }
        return START_NOT_STICKY
    }

    private fun readLoop(rec: AudioRecord, channels: Int, isCapture: Boolean) {
        val frames = 1024
        val buf = ShortArray(frames * channels)
        val l = FloatArray(frames); val r = FloatArray(frames)
        try {
            rec.startRecording()
            while (running) {
                val got = rec.read(buf, 0, buf.size)
                if (got <= 0) { if (got < 0) break else continue }
                val n = got / channels
                if (channels == 2) {
                    for (i in 0 until n) { l[i] = buf[2 * i] / 32768f; r[i] = buf[2 * i + 1] / 32768f }
                } else {
                    for (i in 0 until n) { val v = buf[i] / 32768f; l[i] = v; r[i] = v }
                }
                if (isCapture) AudioHub.feedCapture(l, r, n) else AudioHub.feedMic(l, r, n)
            }
        } catch (_: Throwable) {
        } finally {
            try { rec.stop() } catch (_: Throwable) { }
            try { rec.release() } catch (_: Throwable) { }
        }
    }

    private fun shutdown() {
        running = false
        try { captureThread?.join(300) } catch (_: Throwable) { }
        try { micThread?.join(300) } catch (_: Throwable) { }
        captureThread = null; micThread = null
        captureRecord = null; micRecord = null
        try { projection?.stop() } catch (_: Throwable) { }
        projection = null
        AudioHub.setRunning(false, false, "")
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Audio listening", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Plasmora is listening")
            .setContentText("Visualizing the music that is playing")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }
}

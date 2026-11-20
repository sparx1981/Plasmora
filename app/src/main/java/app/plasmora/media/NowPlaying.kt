package app.plasmora.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.plasmora.MainActivity
import app.plasmora.R
import app.plasmora.SettingsStore
import app.plasmora.audio.AudioHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/** True while the main screen is visible. */
object AppState {
    @Volatile var foreground = false
}

/** Needed only so Android lets us read other apps' media sessions (song, artist, art, controls). */
class MediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() { NowPlaying.attach(applicationContext) }
    override fun onListenerDisconnected() { NowPlaying.detach() }
}

data class NowPlayingState(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val pkg: String = "",
    val hasAccess: Boolean = false,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val posStamp: Long = 0L,      // SystemClock.elapsedRealtime() when positionMs was reported
    val speed: Float = 1f
)

/** Latest album art, shared with every renderer (app + live wallpaper). */
object ArtBridge {
    @Volatile var bitmap: Bitmap? = null
    @Volatile var palette: FloatArray? = null
    val version = AtomicInteger(0)

    fun set(b: Bitmap?) {
        if (b == null) { bitmap = null; palette = null; version.incrementAndGet(); return }
        val small = runCatching { fit(b, 512) }.getOrNull()
        bitmap = small
        palette = small?.let { runCatching { ArtPalette.extract(it) }.getOrNull() }
        version.incrementAndGet()
    }

    private fun fit(b: Bitmap, maxDim: Int): Bitmap {
        val soft = if (b.config == Bitmap.Config.ARGB_8888 && !(Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE)) b
        else b.copy(Bitmap.Config.ARGB_8888, false)
        val m = max(soft.width, soft.height)
        if (m <= maxDim) return soft
        val k = maxDim.toFloat() / m
        return Bitmap.createScaledBitmap(soft, max(1, (soft.width * k).toInt()), max(1, (soft.height * k).toInt()), true)
    }
}

object ArtPalette {
    /** One bright, saturated colour (ARGB) from the cosine palette, for tinting the player controls. */
    fun accent(p: FloatArray): Int {
        var best = 0; var bestScore = -1f
        val hsv = FloatArray(3)
        for (k in 0 until 6) {
            val t = k / 6f
            fun ch(i: Int) = (p[i] + p[3 + i] * kotlin.math.cos(6.28318f * (p[6 + i] * t + p[9 + i]))).coerceIn(0f, 1f)
            val c = Color.rgb((ch(0) * 255).toInt(), (ch(1) * 255).toInt(), (ch(2) * 255).toInt())
            Color.colorToHSV(c, hsv)
            val score = hsv[1] * (0.4f + hsv[2])
            if (score > bestScore) { bestScore = score; best = c }
        }
        Color.colorToHSV(best, hsv)
        hsv[1] = hsv[1].coerceAtLeast(0.45f); hsv[2] = hsv[2].coerceIn(0.8f, 1f)
        return Color.HSVToColor(hsv)
    }

    /** Finds three representative colours and fits the cosine palette a + b*cos(2π(t + d)) through them. */
    fun extract(bmp: Bitmap): FloatArray {
        val n = 24
        val s = Bitmap.createScaledBitmap(bmp, n, n, true)
        val px = IntArray(n * n)
        s.getPixels(px, 0, n, 0, 0, n, n)
        val hsv = FloatArray(3)
        val rgb = Array(px.size) { FloatArray(3) }
        val w = FloatArray(px.size)
        for (i in px.indices) {
            val c = px[i]
            Color.colorToHSV(c, hsv)
            rgb[i][0] = Color.red(c) / 255f; rgb[i][1] = Color.green(c) / 255f; rgb[i][2] = Color.blue(c) / 255f
            w[i] = 0.05f + hsv[1] * hsv[2]
        }
        fun d2(a: FloatArray, b: FloatArray): Float { val x = a[0] - b[0]; val y = a[1] - b[1]; val z = a[2] - b[2]; return x * x + y * y + z * z }
        val cen = Array(3) { FloatArray(3) }
        var best = 0
        for (i in px.indices) if (w[i] > w[best]) best = i
        cen[0] = rgb[best].copyOf()
        var bi = 0; var bv = -1f
        for (i in px.indices) { val v = d2(rgb[i], cen[0]) * w[i]; if (v > bv) { bv = v; bi = i } }
        cen[1] = rgb[bi].copyOf()
        bi = 0; bv = -1f
        for (i in px.indices) { val v = minOf(d2(rgb[i], cen[0]), d2(rgb[i], cen[1])) * w[i]; if (v > bv) { bv = v; bi = i } }
        cen[2] = rgb[bi].copyOf()
        repeat(6) {
            val sum = Array(3) { FloatArray(3) }; val ws = FloatArray(3)
            for (i in px.indices) {
                var k = 0; var kd = d2(rgb[i], cen[0])
                for (j in 1..2) { val dd = d2(rgb[i], cen[j]); if (dd < kd) { kd = dd; k = j } }
                for (c in 0..2) sum[k][c] += rgb[i][c] * w[i]
                ws[k] += w[i]
            }
            for (k in 0..2) if (ws[k] > 1e-4f) for (c in 0..2) cen[k][c] = sum[k][c] / ws[k]
        }
        // dark -> bright, and make the colours lively
        val keys = cen.sortedBy { 0.2126f * it[0] + 0.7152f * it[1] + 0.0722f * it[2] }.map { c ->
            Color.RGBToHSV((c[0] * 255).toInt(), (c[1] * 255).toInt(), (c[2] * 255).toInt(), hsv)
            hsv[1] = (hsv[1] * 1.25f + 0.05f).coerceAtMost(1f)
            hsv[2] = hsv[2].coerceIn(0.32f, 1f)
            val o = Color.HSVToColor(hsv)
            floatArrayOf(Color.red(o) / 255f, Color.green(o) / 255f, Color.blue(o) / 255f)
        }
        val out = FloatArray(12)
        val r3 = sqrt(3f)
        for (ch in 0..2) {
            val y0 = keys[0][ch]; val y1 = keys[1][ch]; val y2 = keys[2][ch]
            val a = (y0 + y1 + y2) / 3f
            val aa = (2f * y0 - y1 - y2) / 3f
            val bb = (y2 - y1) / r3
            val amp = max(hypot(aa, bb), 0.28f)
            val d = if (abs(aa) + abs(bb) < 1e-4f) 0f else atan2(bb, aa) / 6.2831853f
            out[ch] = a.coerceIn(0.15f, 0.85f)
            out[3 + ch] = amp
            out[6 + ch] = 1f
            out[9 + ch] = d
        }
        return out
    }
}

object NowPlaying {
    private const val YTM = "com.google.android.apps.youtube.music"
    private const val OFFER_CHANNEL = "plasmora_offer"
    private const val OFFER_ID = 43

    private val _state = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> get() = _state

    private val main = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var msm: MediaSessionManager? = null
    private var listenerComponent: ComponentName? = null
    private var controller: MediaController? = null
    private var lastArtKey = ""
    private var lastOfferNs = 0L
    private var wasPlaying = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> main.post { choose(list) } }

    private val cb = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) { refresh() }
        override fun onPlaybackStateChanged(state: PlaybackState?) { refresh() }
        override fun onSessionDestroyed() { main.post { reselect() } }
    }

    fun hasAccess(ctx: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Call from the activity's onResume: refreshes the permission flag and connects if allowed. */
    fun onResume(ctx: Context) {
        val ok = hasAccess(ctx)
        if (_state.value.hasAccess != ok) _state.value = _state.value.copy(hasAccess = ok)
        if (ok) attach(ctx.applicationContext)
    }

    fun attach(ctx: Context) {
        appCtx = ctx.applicationContext
        try {
            val m = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val comp = ComponentName(ctx, MediaListenerService::class.java)
            if (msm == null) {
                m.addOnActiveSessionsChangedListener(sessionsListener, comp, main)
                msm = m; listenerComponent = comp
            }
            _state.value = _state.value.copy(hasAccess = true)
            choose(m.getActiveSessions(comp))
        } catch (_: SecurityException) {
            msm = null
            _state.value = _state.value.copy(hasAccess = false)
        } catch (_: Throwable) { }
    }

    fun detach() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Throwable) { }
        msm = null
        controller?.unregisterCallback(cb); controller = null
        _state.value = NowPlayingState(hasAccess = false)
    }

    private fun reselect() {
        val m = msm ?: return
        val comp = listenerComponent ?: return
        try { choose(m.getActiveSessions(comp)) } catch (_: Throwable) { }
    }

    private fun choose(list: List<MediaController>?) {
        val l = list ?: emptyList()
        fun playing(c: MediaController) = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val yt = l.firstOrNull { it.packageName == YTM }
        val pick = when {
            yt != null && playing(yt) -> yt
            else -> l.firstOrNull { playing(it) } ?: yt ?: l.firstOrNull()
        }
        if (pick?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(cb)
            controller = pick
            pick?.registerCallback(cb, main)
        }
        refresh()
    }

    private fun refresh() {
        val c = controller
        if (c == null) {
            _state.value = NowPlayingState(hasAccess = _state.value.hasAccess)
            wasPlaying = false
            return
        }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val key = "$title|$artist|$album|${bmp?.width}x${bmp?.height}|${bmp?.generationId}"
        if (key != lastArtKey) { lastArtKey = key; ArtBridge.set(bmp) }
        val playing = c.playbackState?.state == PlaybackState.STATE_PLAYING
        val prev = _state.value
        val thumb = ArtBridge.bitmap
        val ps = c.playbackState
        val dur = (md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        val pos = (ps?.position ?: 0L).coerceAtLeast(0L)
        val stamp = ps?.lastPositionUpdateTime ?: 0L
        val spd = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f
        if (prev.title != title || prev.artist != artist || prev.playing != playing || prev.pkg != c.packageName || prev.art !== thumb ||
            prev.album != album || prev.durationMs != dur || prev.positionMs != pos || prev.posStamp != stamp) {
            _state.value = NowPlayingState(title, artist, album, thumb, playing, c.packageName, prev.hasAccess, dur, pos, stamp, spd)
        }
        if (playing && !wasPlaying) maybeOffer(c.packageName)
        wasPlaying = playing
    }

    // ---------------------------------------------------------------- controls
    fun play() { controller?.transportControls?.play() }
    fun pause() { controller?.transportControls?.pause() }
    fun toggle() { if (_state.value.playing) pause() else play() }
    fun next() { controller?.transportControls?.skipToNext() }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms.coerceAtLeast(0L)) }

    /** Brings the app that is playing to the front (its own player screen when it offers one). */
    fun openPlayer(ctx: Context) {
        val c = controller ?: return
        val pkg = c.packageName
        // 1) the app's launcher entry: reliable from a foreground app, resumes the running task
        try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                ctx.startActivity(i)
                return
            }
        } catch (_: Throwable) { }
        // 2) the player screen the app registered with its media session
        try {
            val pi = c.sessionActivity ?: return
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                val o = android.app.ActivityOptions.makeBasic()
                o.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(ctx, 0, null, null, null, null, o.toBundle())
            } else pi.send()
        } catch (_: Throwable) { }
    }
    fun prev() { controller?.transportControls?.skipToPrevious() }

    // ---------------------------------------------------------------- "start visualizer" offer
    private fun maybeOffer(pkg: String) {
        val ctx = appCtx ?: return
        if (!SettingsStore.value.offerStart || AppState.foreground) return
        val st = AudioHub.status.value
        if (st.captureRunning || st.micRunning) return
        val now = System.nanoTime()
        if (lastOfferNs != 0L && now - lastOfferNs < 90_000_000_000L) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        lastOfferNs = now
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(OFFER_CHANNEL, "Visualizer suggestions", NotificationManager.IMPORTANCE_LOW))
        }
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_START, true)
        val pi = PendingIntent.getActivity(ctx, 7, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val s = _state.value
        val n = NotificationCompat.Builder(ctx, OFFER_CHANNEL)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Start Plasmora visualizer")
            .setContentText(if (s.title.isNotEmpty()) "${s.title} — ${s.artist}" else "Music is playing")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .addAction(0, "Start visualizer", pi)
            .build()
        try { nm.notify(OFFER_ID, n) } catch (_: Throwable) { }
    }

    fun cancelOffer(ctx: Context) {
        try { (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(OFFER_ID) } catch (_: Throwable) { }
    }
}

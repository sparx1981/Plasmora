package app.plasmora.audio

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class LocalState(
    val hasList: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val index: Int = 0,
    val count: Int = 0
)

/** Plays local audio files and taps the decoded PCM so the visuals react to it. */
class LocalPlayer(private val ctx: Context) {
    private val _state = MutableStateFlow(LocalState())
    val state: StateFlow<LocalState> get() = _state
    private val handler = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null

    private val poll = object : Runnable {
        override fun run() {
            publish()
            handler.postDelayed(this, 250)
        }
    }

    /** Copies samples into the analyzer without altering the audio. */
    private class TapProcessor : BaseAudioProcessor() {
        private var channels = 2
        private var float = false
        private var l = FloatArray(4096)
        private var r = FloatArray(4096)

        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
                throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
            channels = inputAudioFormat.channelCount
            float = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
            AudioHub.analyzer.sampleRate = inputAudioFormat.sampleRate
            return inputAudioFormat
        }

        override fun queueInput(inputBuffer: ByteBuffer) {
            val size = inputBuffer.remaining()
            if (size == 0) return
            val bytesPer = if (float) 4 else 2
            val frames = size / (bytesPer * channels)
            val dup = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
            var done = 0
            while (done < frames) {
                val n = minOf(l.size, frames - done)
                for (i in 0 until n) {
                    val base = dup.position() + ((done + i) * channels) * bytesPer
                    val a: Float; val b: Float
                    if (float) {
                        a = dup.getFloat(base)
                        b = if (channels > 1) dup.getFloat(base + 4) else a
                    } else {
                        a = dup.getShort(base) / 32768f
                        b = if (channels > 1) dup.getShort(base + 2) / 32768f else a
                    }
                    l[i] = a; r[i] = b
                }
                AudioHub.analyzer.push(l, r, n)
                done += n
            }
            val out = replaceOutputBuffer(size)
            out.put(inputBuffer)
            out.flip()
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val tap = TapProcessor()
        val factory = object : DefaultRenderersFactory(ctx) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(false)
                .setAudioProcessors(arrayOf(tap))
                .build()
        }
        val p = ExoPlayer.Builder(ctx, factory)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
        p.repeatMode = Player.REPEAT_MODE_ALL
        p.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish()
        })
        player = p
        handler.post(poll)
        return p
    }

    private fun nameOf(uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
            }
        } catch (_: Throwable) { }
        return uri.lastPathSegment ?: "Track"
    }

    fun load(uris: List<Uri>, autoplay: Boolean = true) {
        if (uris.isEmpty()) return
        val p = ensurePlayer()
        AudioHub.stopDemo()
        AudioHub.analyzer.delayMs = 0
        val items = uris.map { u ->
            MediaItem.Builder().setUri(u)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(nameOf(u)).build()).build()
        }
        p.setMediaItems(items)
        p.prepare()
        if (autoplay) p.play()
        publish()
    }

    fun setSyncMs(ms: Int) { if (player != null) AudioHub.analyzer.delayMs = ms }

    fun activate(syncMs: Int) {
        AudioHub.stopDemo()
        AudioHub.analyzer.delayMs = syncMs
        AudioHub.setActive("Local file")
    }

    fun toggle() { player?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { player?.seekToNextMediaItem() }
    fun prev() {
        player?.let { if (it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
    }
    fun seekTo(ms: Long) { player?.seekTo(ms) }
    fun pause() { player?.pause() }
    fun resume() { player?.play() }

    fun release() {
        handler.removeCallbacks(poll)
        player?.release(); player = null
    }

    private fun publish() {
        val p = player ?: return
        val md = p.mediaMetadata
        _state.value = LocalState(
            hasList = p.mediaItemCount > 0,
            title = md.title?.toString() ?: "",
            artist = md.artist?.toString() ?: "",
            playing = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = if (p.duration == C.TIME_UNSET) 0 else p.duration,
            index = p.currentMediaItemIndex,
            count = p.mediaItemCount
        )
    }
}

package app.plasmora.gl

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicInteger

data class VisInfo(
    val presetName: String = "",
    val presetIndex: Int = 0,
    val paletteName: String = "",
    val fps: Float = 0f,
    val simW: Int = 0,
    val simH: Int = 0,
    val hdr: Boolean = true,
    val gpu: String = "",
    val palColors: IntArray = IntArray(0)
)

/** Thread-safe mailbox between the UI and the GL thread. */
object VisController {
    val nav = AtomicInteger(0)            // +1 next, -1 previous (accumulated)
    val select = AtomicInteger(-1)        // jump to preset index
    val randomRequests = AtomicInteger(0)
    val paletteRandom = AtomicInteger(0)
    val pinned = java.util.concurrent.atomic.AtomicBoolean(false)       // current song has a remembered effect: hold it
    val paletteOverride = AtomicInteger(-1)                              // >=0 forces a palette (remembered song)
    val leaderIdx = AtomicInteger(-1)      // main screen's current effect, followed by an external display
    val leaderPal = AtomicInteger(-1)
    val thumbReq = AtomicInteger(-1)       // capture the current picture as the preview of this effect
    val screenshot = java.util.concurrent.atomic.AtomicBoolean(false)
    /** Lyric line to draw inside the visual (empty = none). Set by the UI, read by the GL thread. */
    @Volatile var lyricText: String = ""
    @Volatile var lyricPos: Int = 1
    @Volatile var lyricBack: Float = 0.6f
    @Volatile var beatLevel: Float = 0f
    @Volatile var bpm: Float = 0f

    /** Remote / keyboard keys forwarded by the activity (Android TV). */
    val keys = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 16)

    private val _info = MutableStateFlow(VisInfo())
    val info: StateFlow<VisInfo> get() = _info
    fun publish(i: VisInfo) { _info.value = i }

    fun next() { nav.incrementAndGet() }
    fun prev() { nav.decrementAndGet() }
    fun random() { randomRequests.incrementAndGet() }
    fun select(i: Int) { select.set(i) }
    fun takeScreenshot() { screenshot.set(true) }
}

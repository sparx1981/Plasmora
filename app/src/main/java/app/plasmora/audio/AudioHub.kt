package app.plasmora.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

data class AudioStatus(
    /** What the visuals are actually listening to right now. */
    val active: String = "Idle",
    val message: String = "",
    val captureRunning: Boolean = false,
    val micRunning: Boolean = false,
    val captureSilent: Boolean = false
)

/** Singleton bridge between audio sources (service, player, demo) and the renderer. */
object AudioHub {
    val analyzer = AudioAnalyzer()

    private val _status = MutableStateFlow(AudioStatus())
    val status: StateFlow<AudioStatus> get() = _status

    @Volatile private var captureActiveNs = 0L
    @Volatile private var captureSeen = false
    private var demo: DemoThread? = null

    fun setActive(label: String, message: String = _status.value.message) {
        if (_status.value.active != label || _status.value.message != message) {
            _status.value = _status.value.copy(active = label, message = message)
        }
    }

    fun setRunning(capture: Boolean, mic: Boolean, message: String = "") {
        _status.value = _status.value.copy(captureRunning = capture, micRunning = mic, message = message,
            active = if (!capture && !mic && _status.value.active in listOf("System audio", "Microphone")) "Idle" else _status.value.active)
    }

    fun feedCapture(l: FloatArray, r: FloatArray, n: Int) {
        var e = 0f
        for (i in 0 until n) { val v = l[i] + r[i]; e += v * v }
        val now = System.nanoTime()
        if (e / n > 1e-9f) {
            captureActiveNs = now
            captureSeen = true
        }
        val live = now - captureActiveNs < 1_500_000_000L
        if (live) {
            analyzer.sampleRate = 48000
            analyzer.delayMs = 0
            analyzer.push(l, r, n)
            setActive("System audio")
            if (_status.value.captureSilent) _status.value = _status.value.copy(captureSilent = false)
        } else {
            // Capture is delivering silence (paused, or the app blocks capture).
            // With no microphone running, feed that silence through so the visuals calm down.
            if (!_status.value.micRunning) {
                analyzer.sampleRate = 48000
                analyzer.delayMs = 0
                analyzer.push(l, r, n)
                setActive("System audio — waiting for music")
            }
            if (!_status.value.captureSilent && captureSeen.not() && now - captureStartNs > 4_000_000_000L) {
                _status.value = _status.value.copy(captureSilent = true)
            }
        }
    }

    @Volatile private var captureStartNs = System.nanoTime()
    fun markCaptureStart() { captureStartNs = System.nanoTime(); captureActiveNs = 0L; captureSeen = false }

    /** Mic audio is used whenever system-audio capture is not delivering sound. */
    fun feedMic(l: FloatArray, r: FloatArray, n: Int) {
        val now = System.nanoTime()
        if (now - captureActiveNs > 1_500_000_000L) {
            analyzer.sampleRate = 48000
            analyzer.delayMs = 0
            analyzer.push(l, r, n)
            setActive("Microphone")
        }
    }

    fun startDemo() {
        stopDemo()
        analyzer.clear()
        analyzer.delayMs = 0
        demo = DemoThread().also { it.start() }
        setActive("Demo", "Synthetic test music")
    }

    fun stopDemo() { demo?.run = false; demo = null }

    private class DemoThread : Thread("plasmora-demo") {
        @Volatile var run = true
        override fun run() {
            val sr = 48000
            analyzer.sampleRate = sr
            val chunk = 1024
            val l = FloatArray(chunk); val r = FloatArray(chunk)
            val bpm = 124.0; val beatLen = 60.0 / bpm
            val startNs = System.nanoTime()
            var produced = 0L
            var seed = 22222
            var prevN = 0f
            val bassNotes = doubleArrayOf(55.0, 55.0, 82.41, 73.42, 55.0, 65.41, 82.41, 98.0)
            val arp = intArrayOf(0, 3, 7, 12, 7, 3, 10, 15)
            while (run) {
                for (i in 0 until chunk) {
                    val t = (produced + i).toDouble() / sr
                    val tb = t % beatLen
                    val beatIdx = (t / beatLen).toLong()
                    // kick
                    var kick = 0.0
                    if (tb < 0.4) {
                        val ph = 2 * PI * (42.0 * tb + 95.0 / 38.0 * (1 - exp(-38.0 * tb)))
                        kick = sin(ph) * exp(-tb * 11.0) * 0.85
                    }
                    // off-beat hat
                    val tOff = (t + beatLen * 0.5) % beatLen
                    seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
                    val nz = (seed and 0xFFFF) / 32768f - 1f
                    val hat = ((nz - prevN) * exp(-tOff * 55.0) * 0.28).also { prevN = nz }
                    // bass with side-chain
                    val bn = bassNotes[((beatIdx / 1) % bassNotes.size).toInt()]
                    val side = 1.0 - exp(-tb * 14.0)
                    val bass = (sin(2 * PI * bn * t) + 0.4 * sin(4 * PI * bn * t) + 0.2 * sin(6 * PI * bn * t)) * 0.28 * side
                    // pad
                    val trem = 0.75 + 0.25 * sin(2 * PI * 0.2 * t)
                    val pad = (sin(2 * PI * 220.0 * t) + sin(2 * PI * 261.63 * t) + sin(2 * PI * 329.63 * t) + 0.7 * sin(2 * PI * 440.0 * t)) * 0.045 * trem
                    // arp (16th notes)
                    val sixt = beatLen / 4
                    val step = (t / sixt).toLong()
                    val ts = t % sixt
                    val note = arp[(step % arp.size).toInt()]
                    val f = 440.0 * Math.pow(2.0, note / 12.0)
                    val arpS = (sin(2 * PI * f * t) + 0.3 * sin(4 * PI * f * t)) * exp(-ts * 16.0) * 0.12
                    val pan = if (step % 2L == 0L) 0.7 else 0.3
                    val mono = kick + bass + pad
                    l[i] = (mono + hat * 0.8 + arpS * (1 - pan) * 1.4).toFloat()
                    r[i] = (mono + hat * 1.2 + arpS * pan * 1.4).toFloat()
                }
                analyzer.push(l, r, chunk)
                produced += chunk
                val targetNs = startNs + produced * 1_000_000_000L / sr
                val waitMs = (targetNs - System.nanoTime()) / 1_000_000L
                if (waitMs > 0) try { sleep(waitMs) } catch (_: InterruptedException) { }
            }
        }
    }
}

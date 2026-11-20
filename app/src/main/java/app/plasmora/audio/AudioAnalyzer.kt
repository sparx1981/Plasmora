package app.plasmora.audio

import app.plasmora.Settings
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Everything the renderer needs to know about the music this frame. */
class AudioFrame {
    @JvmField var bass = 0f       // ~1.0 on average, spikes on kicks
    @JvmField var mid = 0f
    @JvmField var treb = 0f
    @JvmField var bassAtt = 0f    // slower, smoother versions
    @JvmField var midAtt = 0f
    @JvmField var trebAtt = 0f
    @JvmField var beat = 0f       // 1.0 on a detected beat, decays to 0
    @JvmField var beatHit = false // true only on the frame a beat is detected
    @JvmField var level = 0f      // overall loudness 0..~1
    @JvmField var silent = true
    // ---- stem-style analysis (approximate separation by frequency + transient behaviour)
    @JvmField var kick = 0f        // low-end thump, ~1 average, spikes on kicks
    @JvmField var snare = 0f       // mid/high transient energy
    @JvmField var vocal = 0f       // tonal energy in the voice range (0..~2)
    @JvmField var melody = 0f      // strength of the dominant pitched line (0..1)
    @JvmField var pitch = 0f       // dominant pitch class 0..1 (C..B), smoothed
    @JvmField var kickHit = false
    @JvmField var snareHit = false
    @JvmField var flux = 0f        // spectral flux (how fast the sound is changing)
    @JvmField var bright = 0f      // spectral centroid, 0 dark .. 1 bright
    @JvmField var bpm = 0f         // running tempo estimate (0 if unknown)
    @JvmField var section = 0f     // slow loudness (~8 s) for structure detection
    @JvmField var buildUp = 0f     // >0 while energy is rising steadily
    /** 1024 x 4 floats: L wave, R wave, spectrum, peak-hold spectrum. */
    @JvmField val tex = FloatArray(AudioAnalyzer.TEX_W * 4)

    fun copyFrom(o: AudioFrame) {
        bass = o.bass; mid = o.mid; treb = o.treb; bassAtt = o.bassAtt; midAtt = o.midAtt; trebAtt = o.trebAtt
        beat = o.beat; beatHit = o.beatHit; level = o.level; silent = o.silent
        kick = o.kick; snare = o.snare; vocal = o.vocal; melody = o.melody; pitch = o.pitch
        kickHit = o.kickHit; snareHit = o.snareHit; flux = o.flux; bright = o.bright; bpm = o.bpm
        section = o.section; buildUp = o.buildUp
        System.arraycopy(o.tex, 0, tex, 0, tex.size)
    }
}

/**
 * Thread-safe PCM ring buffer + analysis (FFT, bands, beat detection, AGC).
 * Audio threads call [push]; the GL thread calls [analyze] once per frame.
 */
class AudioAnalyzer {
    companion object {
        const val RING = 1 shl 17
        const val FFT_N = 4096
        const val TEX_W = 1024
    }

    private val lock = Any()
    private val ringL = FloatArray(RING)
    private val ringR = FloatArray(RING)
    private var writePos = 0L

    @Volatile var sampleRate = 48000
    @Volatile var delayMs = 0
    @Volatile var lastPushNs = 0L

    fun push(l: FloatArray, r: FloatArray, n: Int) {
        synchronized(lock) {
            var w = writePos
            val mask = (RING - 1).toLong()
            for (i in 0 until n) {
                val k = (w and mask).toInt()
                ringL[k] = l[i]; ringR[k] = r[i]
                w++
            }
            writePos = w
        }
        lastPushNs = System.nanoTime()
    }

    fun clear() {
        synchronized(lock) {
            java.util.Arrays.fill(ringL, 0f); java.util.Arrays.fill(ringR, 0f)
        }
    }

    // ---------------------------------------------------------------- analysis state
    private val bufL = FloatArray(FFT_N)
    private val bufR = FloatArray(FFT_N)
    private val re = FloatArray(FFT_N)
    private val im = FloatArray(FFT_N)
    private val mag = FloatArray(FFT_N / 2)
    private val hann = FloatArray(FFT_N) { (0.5 - 0.5 * cos(2.0 * PI * it / (FFT_N - 1))).toFloat() }
    private val cosT = FloatArray(FFT_N / 2) { cos(2.0 * PI * it / FFT_N).toFloat() }
    private val sinT = FloatArray(FFT_N / 2) { sin(2.0 * PI * it / FFT_N).toFloat() }
    private val rev = IntArray(FFT_N).also { r ->
        val bits = Integer.numberOfTrailingZeros(FFT_N)
        for (i in 0 until FFT_N) r[i] = Integer.reverse(i) ushr (32 - bits)
    }

    private val spec = FloatArray(TEX_W)
    private val peak = FloatArray(TEX_W)
    private var mapSr = 0
    private val mapLo = IntArray(TEX_W)
    private val mapHi = IntArray(TEX_W)
    private val mapFrac = FloatArray(TEX_W)
    private val mapTilt = FloatArray(TEX_W)

    private var env = 0f            // slow loudness envelope for AGC
    private var agcGain = 1f
    private var silentTime = 0f

    private var bassAvg = 0.02f; private var midAvg = 0.02f; private var trebAvg = 0.01f
    private var bassS = 1f; private var midS = 1f; private var trebS = 1f
    private var bassA = 1f; private var midA = 1f; private var trebA = 1f
    private var beatMean = 0.02f; private var beatVar = 0f; private var prevBassE = 0f
    private var sinceBeat = 1f
    private var beatPulse = 0f

    // stem state
    private var kickAvg = 0.02f; private var kickS = 1f
    private var snareAvg = 0.02f; private var snareS = 1f
    private var vocS = 0f; private var melS = 0f; private var pitchS = 0f
    private var snareMean = 0.01f; private var snareVar = 0f; private var sinceSnare = 1f
    private var fluxS = 0f; private var brightS = 0.3f
    private val prevMag = FloatArray(FFT_N / 2)
    private var sectionE = 0f; private var sectionPrev = 0f; private var buildS = 0f; private var buildTimer = 0f
    private val beatTimes = FloatArray(16); private var beatCount = 0; private var clock = 0f; private var bpmS = 0f

    private fun buildMap(sr: Int) {
        mapSr = sr
        val binHz = sr.toFloat() / FFT_N
        val fMin = 28f
        val fMax = min(18000f, sr * 0.5f * 0.96f)
        val ratio = fMax / fMin
        for (k in 0 until TEX_W) {
            val f0 = fMin * Math.pow(ratio.toDouble(), k.toDouble() / TEX_W).toFloat()
            val f1 = fMin * Math.pow(ratio.toDouble(), (k + 1.0) / TEX_W).toFloat()
            val c = sqrt(f0 * f1)
            val b0 = f0 / binHz; val b1 = f1 / binHz; val bc = c / binHz
            mapLo[k] = max(1, floor(b0).toInt())
            mapHi[k] = min(FFT_N / 2 - 1, max(mapLo[k], floor(b1).toInt()))
            mapFrac[k] = bc - floor(bc)
            if (b1 - b0 < 1f) { mapLo[k] = min(FFT_N / 2 - 2, floor(bc).toInt()); mapHi[k] = mapLo[k] }
            // music rolls off roughly 4.5 dB per octave; tilt so highs are visible
            mapTilt[k] = 4.5f * log2(c / 1000f)
        }
    }

    private fun fft() {
        val n = FFT_N
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val half = size shr 1
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + half) {
                    val wr = cosT[k]; val wi = -sinT[k]
                    val b = j + half
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[j] - tr; im[b] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += step
                }
                i += size
            }
            size = size shl 1
        }
    }

    private fun smoothStep(dt: Float, tau: Float) = 1f - exp(-dt / tau)
    private fun prevMagSnare(k: Int): Float = prevMag[k]

    // ---- several renderers (app, wallpaper, external display) can share one analysis
    private val shared = AudioFrame()
    private var sharedNs = 0L
    private var beatSeq = 0; private var snareSeq = 0
    private val seenBeat = HashMap<Int, Int>(); private val seenSnare = HashMap<Int, Int>()

    @Synchronized fun analyzeFor(consumer: Int, dt: Float, s: Settings, out: AudioFrame) {
        val now = System.nanoTime()
        if (sharedNs == 0L || now - sharedNs > 6_000_000L) {
            val d = if (sharedNs == 0L) dt else ((now - sharedNs) / 1e9f).coerceIn(0.002f, 0.1f)
            analyze(d, s, shared)
            sharedNs = now
            if (shared.beatHit) beatSeq++
            if (shared.snareHit) snareSeq++
        }
        out.copyFrom(shared)
        val sb = seenBeat[consumer] ?: beatSeq; val ss = seenSnare[consumer] ?: snareSeq
        out.beatHit = beatSeq != sb
        out.kickHit = out.beatHit
        out.snareHit = snareSeq != ss
        seenBeat[consumer] = beatSeq; seenSnare[consumer] = snareSeq
    }

    fun analyze(dt: Float, s: Settings, out: AudioFrame) {
        val sr = sampleRate
        if (sr != mapSr) buildMap(sr)
        val delay = (delayMs.toLong() * sr / 1000L)
        var have = true
        synchronized(lock) {
            val end = writePos - delay
            if (end < FFT_N) {
                have = false
            } else {
                val mask = RING - 1
                var idx = (end - FFT_N).toInt()
                for (i in 0 until FFT_N) {
                    val k = (idx + i) and mask
                    bufL[i] = ringL[k]; bufR[i] = ringR[k]
                }
            }
        }
        if (!have) { java.util.Arrays.fill(bufL, 0f); java.util.Arrays.fill(bufR, 0f) }

        // ---- loudness + AGC (on the most recent ~43 ms)
        var e = 0.0
        for (i in FFT_N - 2048 until FFT_N) { val m = (bufL[i] + bufR[i]) * 0.5f; e += (m * m).toDouble() }
        val rms = sqrt(e / 2048.0).toFloat()
        env += (rms - env) * smoothStep(dt, if (rms > env) 0.25f else 3.0f)
        val target = if (env > 0.0015f) (0.18f / env).coerceIn(0.6f, 10f) else 1f
        agcGain += (target - agcGain) * smoothStep(dt, 1.5f)
        val g = (if (s.agc) agcGain else 1f)

        silentTime = if (rms < 1e-4f) silentTime + dt else 0f
        out.silent = silentTime > 1.0f
        out.level = (rms * g * 2.2f).coerceIn(0f, 1.5f)

        // ---- waveform with a rising zero-crossing trigger so the scope is stable
        val base = FFT_N - 2048
        var start = base
        for (i in base + 1 until base + 1024) {
            val a = bufL[i - 1] + bufR[i - 1]; val b = bufL[i] + bufR[i]
            if (a < 0f && b >= 0f) { start = i; break }
        }
        val tex = out.tex
        for (i in 0 until TEX_W) {
            tex[i] = (bufL[start + i] * g * 0.8f).coerceIn(-1.5f, 1.5f)
            tex[TEX_W + i] = (bufR[start + i] * g * 0.8f).coerceIn(-1.5f, 1.5f)
        }

        // ---- spectrum
        for (i in 0 until FFT_N) { re[i] = (bufL[i] + bufR[i]) * 0.5f * hann[i]; im[i] = 0f }
        fft()
        val norm = 4f / FFT_N * g
        for (k in 0 until FFT_N / 2) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k]) * norm

        val attack = 1f - exp(-dt / 0.012f)
        val release = 1f - exp(-dt / (0.08f + 0.25f * s.smoothing))
        val peakFall = dt * (0.35f + 0.5f * (1f - s.smoothing))
        for (k in 0 until TEX_W) {
            val lo = mapLo[k]; val hi = mapHi[k]
            val v = if (hi > lo) {
                var m = 0f
                for (j in lo..hi) if (mag[j] > m) m = mag[j]
                m
            } else {
                mag[lo] + (mag[min(lo + 1, FFT_N / 2 - 1)] - mag[lo]) * mapFrac[k]
            }
            val db = 20f * log10(v + 1e-7f) + mapTilt[k]
            val nv = ((db + 78f) / 62f).coerceIn(0f, 1.3f)
            val cur = spec[k]
            spec[k] = if (nv > cur) cur + (nv - cur) * attack.coerceAtLeast(0.55f) else cur + (nv - cur) * release
            peak[k] = max(spec[k], peak[k] - peakFall)
            tex[2 * TEX_W + k] = spec[k]
            tex[3 * TEX_W + k] = peak[k]
        }

        // ---- bands
        val binHz = sr.toFloat() / FFT_N
        fun band(f0: Float, f1: Float): Float {
            val a = max(1, (f0 / binHz).toInt()); val b = min(FFT_N / 2 - 1, (f1 / binHz).toInt())
            var sum = 0f
            for (k in a..b) sum += mag[k] * mag[k]
            return sqrt(sum)
        }
        val bassNow = band(25f, 160f)
        val midNow = band(160f, 2200f)
        val trebNow = band(2200f, 15000f)

        val avgTau = 3.0f
        bassAvg += (bassNow - bassAvg) * smoothStep(dt, avgTau)
        midAvg += (midNow - midAvg) * smoothStep(dt, avgTau)
        trebAvg += (trebNow - trebAvg) * smoothStep(dt, avgTau)
        fun rel(x: Float, avg: Float, gain: Float): Float = (x / max(avg, 0.012f) * gain * s.gain).coerceIn(0f, 5f)
        val rb = rel(bassNow, bassAvg, s.bassGain)
        val rm = rel(midNow, midAvg, s.midGain)
        val rt = rel(trebNow, trebAvg, s.trebGain)
        val fast = smoothStep(dt, 0.03f + 0.10f * s.smoothing)
        val slow = smoothStep(dt, 0.25f + 0.5f * s.smoothing)
        bassS += (rb - bassS) * fast; midS += (rm - midS) * fast; trebS += (rt - trebS) * fast
        bassA += (rb - bassA) * slow; midA += (rm - midA) * slow; trebA += (rt - trebA) * slow
        out.bass = bassS; out.mid = midS; out.treb = trebS
        out.bassAtt = bassA; out.midAtt = midA; out.trebAtt = trebA

        // ---- beat detection (adaptive threshold on low-frequency energy)
        val eNow = band(30f, 140f)
        val m0 = beatMean
        beatMean += (eNow - beatMean) * smoothStep(dt, 0.9f)
        beatVar += ((eNow - m0) * (eNow - m0) - beatVar) * smoothStep(dt, 0.9f)
        val k = 2.3f - 1.7f * s.beatSens
        val thr = beatMean + k * sqrt(beatVar) + 0.004f
        sinceBeat += dt
        out.beatHit = false
        if (eNow > thr && eNow > prevBassE && sinceBeat > 0.17f && !out.silent) {
            out.beatHit = true
            sinceBeat = 0f
            beatPulse = 1f
        }
        prevBassE = eNow
        beatPulse = max(0f, beatPulse - dt * 4.2f)
        out.beat = beatPulse

        // ---- tempo from recent beat intervals (median, folded into 70..180 BPM)
        clock += dt
        if (out.beatHit) {
            for (i in 0 until beatTimes.size - 1) beatTimes[i] = beatTimes[i + 1]
            beatTimes[beatTimes.size - 1] = clock
            beatCount = min(beatCount + 1, beatTimes.size)
            if (beatCount >= 6) {
                val iv = ArrayList<Float>()
                for (i in beatTimes.size - beatCount + 1 until beatTimes.size) iv.add(beatTimes[i] - beatTimes[i - 1])
                iv.sort()
                val med = max(0.2f, iv[iv.size / 2])
                var sum = 0f; var cnt = 0
                for (x in iv) if (x > med * 0.85f && x < med * 1.15f) { sum += x; cnt++ }
                val per = if (cnt > 0) sum / cnt else med
                var b = 60f / max(0.2f, per)
                while (b < 70f) b *= 2f
                while (b > 180f) b *= 0.5f
                bpmS = if (bpmS == 0f) b else bpmS + (b - bpmS) * 0.15f
            }
        }
        if (out.silent) { beatCount = 0; bpmS = 0f }
        out.bpm = bpmS

        // ---- kick: tight low band
        val kickNow = band(35f, 110f)
        kickAvg += (kickNow - kickAvg) * smoothStep(dt, 2.5f)
        val rk = (kickNow / max(kickAvg, 0.012f) * s.gain * s.bassGain).coerceIn(0f, 5f)
        kickS += (rk - kickS) * smoothStep(dt, 0.025f)
        out.kick = kickS
        out.kickHit = out.beatHit

        // ---- spectral flux + centroid
        var fl = 0f; var cs = 0f; var ws = 0f
        val hiBin = min(FFT_N / 2 - 1, (9000f / binHz).toInt())
        for (kk in 2..hiBin) {
            val d = mag[kk] - prevMag[kk]
            if (d > 0f) fl += d
            val w = mag[kk] * mag[kk]
            cs += w * kk; ws += w
        }
        fluxS += (fl * 6f - fluxS) * smoothStep(dt, 0.06f)
        out.flux = fluxS.coerceIn(0f, 4f)
        val cen = if (ws > 1e-9f) (cs / ws) * binHz else 500f
        val cn = ((log2(max(cen, 80f) / 80f)) / 5f).coerceIn(0f, 1f)
        brightS += (cn - brightS) * smoothStep(dt, 0.4f)
        out.bright = brightS

        // ---- snare: broadband transient (150-400 Hz body + 2-8 kHz crack), via positive flux
        var sf = 0f
        val a1 = max(1, (150f / binHz).toInt()); val b1 = (400f / binHz).toInt()
        val a2 = (2000f / binHz).toInt(); val b2 = min(FFT_N / 2 - 1, (8000f / binHz).toInt())
        for (kk in a1..b1) sf += max(0f, mag[kk] - prevMagSnare(kk))
        for (kk in a2..b2) sf += max(0f, mag[kk] - prevMagSnare(kk))
        snareAvg += (sf - snareAvg) * smoothStep(dt, 2.0f)
        val rs = (sf / max(snareAvg, 0.004f) * 0.6f * s.gain).coerceIn(0f, 5f)
        snareS += (rs - snareS) * smoothStep(dt, 0.03f)
        out.snare = snareS
        val m1 = snareMean
        snareMean += (sf - snareMean) * smoothStep(dt, 0.8f)
        snareVar += ((sf - m1) * (sf - m1) - snareVar) * smoothStep(dt, 0.8f)
        sinceSnare += dt
        out.snareHit = false
        if (sf > snareMean + 2.2f * sqrt(snareVar) + 0.003f && sinceSnare > 0.12f && !out.silent && !out.beatHit) {
            out.snareHit = true; sinceSnare = 0f
        }

        for (kk in 0 until FFT_N / 2) prevMag[kk] = mag[kk]

        // ---- vocal: voice-range energy weighted by tonalness (peaky spectrum = pitched)
        val va = max(1, (300f / binHz).toInt()); val vb = (3400f / binHz).toInt()
        var vsum = 0f; var vmax = 0f; var vmaxK = va
        for (kk in va..vb) { val m = mag[kk]; vsum += m; if (m > vmax) { vmax = m; vmaxK = kk } }
        val vmean = vsum / (vb - va + 1)
        val tonal = ((vmax / max(vmean, 1e-6f)) / 14f).coerceIn(0f, 1f)
        val vEn = band(300f, 3400f)
        val vr = (vEn / max(midAvg, 0.012f) * tonal * 1.4f * s.gain * s.midGain).coerceIn(0f, 3f)
        vocS += (vr - vocS) * smoothStep(dt, 0.12f)
        out.vocal = vocS

        // ---- melody: dominant pitched line 200..2000 Hz -> pitch class
        val ma = max(1, (200f / binHz).toInt()); val mb = (2000f / binHz).toInt()
        var mm = 0f; var mk = ma; var msum = 0f
        for (kk in ma..mb) { val m = mag[kk]; msum += m; if (m > mm) { mm = m; mk = kk } }
        val peaky = (mm / max(msum / (mb - ma + 1), 1e-6f) / 18f).coerceIn(0f, 1f)
        melS += (peaky - melS) * smoothStep(dt, 0.15f)
        out.melody = melS
        if (peaky > 0.35f && mm * g > 0.002f) {
            val hz = mk * binHz
            val semis = 12f * log2(hz / 261.63f)
            val pc = ((semis % 12f) + 12f) % 12f / 12f
            var d = pc - pitchS
            if (d > 0.5f) d -= 1f; if (d < -0.5f) d += 1f
            pitchS = ((pitchS + d * smoothStep(dt, 0.25f)) % 1f + 1f) % 1f
        }
        out.pitch = pitchS

        // ---- song structure: slow loudness and build-up detection
        sectionE += (out.level - sectionE) * smoothStep(dt, 8f)
        val slowE = sectionE
        buildTimer += dt
        if (buildTimer > 0.5f) {
            val rise = slowE - sectionPrev
            buildS += ((if (rise > 0.004f) 1f else 0f) - buildS) * 0.25f
            sectionPrev = slowE; buildTimer = 0f
        }
        out.section = slowE
        out.buildUp = buildS
    }
}

package app.plasmora.gl

import android.content.Context
import android.opengl.GLES20
import app.plasmora.ui.ThumbStore
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.Log
import app.plasmora.Settings
import app.plasmora.SettingsStore
import app.plasmora.SourceMode
import app.plasmora.audio.AudioAnalyzer
import app.plasmora.audio.AudioFrame
import app.plasmora.audio.AudioHub
import app.plasmora.media.ArtBridge
import android.graphics.Bitmap
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.tan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class VisualizerRenderer(
    private val ctx: Context,
    private val wallpaper: Boolean = false,
    /** Mirrors the main screen's effect and palette (used on an external display). */
    private val follower: Boolean = false,
    /** Low-power dim mode (screensaver / always-on). */
    private val dim: Boolean = false
) : GLSurfaceView.Renderer {
    private val consumerId = nextConsumer.getAndIncrement()
    private companion object {
        val nextConsumer = java.util.concurrent.atomic.AtomicInteger(1)
        const val TAG = "Plasmora"
        const val POINTS = 1024
        const val MAX_LAYERS = 4
        const val RCOLS = 256
        const val RROWS = 40
        const val RSTEP = 1f / 20f
        const val LUTN = 24
    }

    // ------------------------------------------------------------------ GL helpers
    private class Prog(val id: Int) {
        private val locs = HashMap<String, Int>()
        fun loc(n: String): Int = locs.getOrPut(n) { GLES30.glGetUniformLocation(id, n) }
    }

    private class Target(var tex: Int = 0, var fbo: Int = 0, var w: Int = 0, var h: Int = 0)

    private fun readAsset(name: String) = ctx.assets.open("shaders/$name").bufferedReader().use { it.readText() }

    private fun shader(type: Int, src: String, name: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            Log.e(TAG, "Shader $name failed: $log")
            throw RuntimeException("Shader $name failed: $log")
        }
        return s
    }

    private fun program(vs: String, fs: String): Prog {
        val v = shader(GLES30.GL_VERTEX_SHADER, readAsset(vs), vs)
        val f = shader(GLES30.GL_FRAGMENT_SHADER, readAsset(fs), fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v); GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Link $vs/$fs failed: " + GLES30.glGetProgramInfoLog(p))
        GLES30.glDeleteShader(v); GLES30.glDeleteShader(f)
        return Prog(p)
    }

    private fun makeTarget(w: Int, h: Int, half: Boolean, wrap: Int): Target? {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        val tex = ids[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        if (half) GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA16F, w, h, 0, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, null)
        else GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLES30.glGenFramebuffers(1, ids, 0)
        val fbo = ids[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
        val st = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (st != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
            GLES30.glDeleteTextures(1, intArrayOf(tex), 0)
            return null
        }
        GLES30.glViewport(0, 0, w, h)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        return Target(tex, fbo, w, h)
    }

    private fun free(t: Target?) {
        t ?: return
        GLES30.glDeleteFramebuffers(1, intArrayOf(t.fbo), 0)
        GLES30.glDeleteTextures(1, intArrayOf(t.tex), 0)
    }

    // ------------------------------------------------------------------ state
    private lateinit var warpP: Prog
    private lateinit var shapeP: Prog
    private lateinit var downP: Prog
    private lateinit var lyricP: Prog
    private var lyricTex = 0
    private var lyricShown = ""
    private var lyricAlpha = 0f
    private var lyricHas = false
    private lateinit var upP: Prog
    private lateinit var finalP: Prog
    private lateinit var barsP: Prog
    private lateinit var ridgeP: Prog
    private lateinit var tunnelP: Prog
    private lateinit var partP: Prog
    private lateinit var terrainP: Prog
    private lateinit var postP: Prog
    private lateinit var taaP: Prog
    private lateinit var gradeP: Prog
    private lateinit var sphereP: Prog
    private lateinit var fluidP: Prog
    private lateinit var marchP: Prog
    private lateinit var bar3dP: Prog
    private lateinit var wave3dP: Prog
    private lateinit var surfP: Prog
    private lateinit var hexP: Prog
    private lateinit var framesP: Prog
    private lateinit var mazeP: Prog
    private lateinit var fireP: Prog
    private lateinit var vortexP: Prog
    private lateinit var tfloorP: Prog
    private lateinit var rad3dP: Prog
    private lateinit var pulseP: Prog
    private lateinit var polyP: Prog
    private lateinit var smokeP: Prog
    private lateinit var spikeP: Prog
    private lateinit var sbarP: Prog
    private lateinit var bloomP: Prog
    private lateinit var trailP: Prog
    private lateinit var sringP: Prog
    private lateinit var dfloorP: Prog
    private lateinit var ltunP: Prog
    private lateinit var rbarsP: Prog
    private lateinit var dbarsP: Prog
    private lateinit var horiP: Prog
    private lateinit var glassP: Prog
    private var ridgeTex = 0
    private val ridgeHist = FloatArray(RCOLS * RROWS)
    private val ridgeBuf: FloatBuffer = ByteBuffer.allocateDirect(RCOLS * RROWS * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var ridgeAcc = 0f
    private var curAspect = 1f
    private var curH = 1000f
    private var slowLevel = 0.3f
    private var fastLevel = 0.3f
    private var quietFor = 0f
    private var dropKick = 0f
    private var dropHit = false
    private var vao = 0
    private var audioTex = 0
    private var blackTex = 0
    private var hdrSupported = false
    private var maxTex = 4096
    private var gpuName = ""

    // album art
    private var artTex = 0
    private var artVer = -1
    private var artAspectImg = 1f
    private var artMix = 0f
    private var hasArt = false

    // power
    private var powerSave = false
    private var psTimer = 99f
    private var realTime = 0f
    private var palName = ""

    private var screenW = 1
    private var screenH = 1
    private val sim = arrayOfNulls<Target>(2)
    private var cur = 0
    private val bloom = ArrayList<Target>()

    // cinematic post chain
    private var postT: Target? = null
    private val histT = arrayOfNulls<Target>(2)
    private var histIdx = 0
    private var histValid = false
    private var postKey = ""
    private var taaFrame = 0
    private var lutTex = 0
    private val lutPal = FloatArray(12) { -9f }
    private var lutTick = 0
    private val lutBuf: ByteBuffer = ByteBuffer.allocateDirect(LUTN * LUTN * LUTN * 3)
    private val shake = FloatArray(4)
    private val shakeV = FloatArray(4)
    private val shakePrev = FloatArray(4)
    private val camCur = FloatArray(4)
    private val camPrev = FloatArray(4)
    private var parPX = 0f; private var parPY = 0f
    // hints collected from the layers drawn this frame
    private var fxDofW = 0f; private var fxDofCamH = 1.3f; private var fxDofPitch = 0.45f; private var fxDofFocus = 3f
    private var fxSunW = 0f; private var fxSunY = 0.5f
    private var fxReflW = 0f; private var fxReflY = 0.3f
    private var simKey = ""
    private var curDt = 1f / 60f
    private var effectiveSettings: Settings = SettingsStore.value
    private var fluidKey = ""
    private val fluidTargets = arrayOfNulls<Target>(8)   // velA velB prsA prsB div curl dyeA dyeB
    private var fluidVelIdx = 0; private var fluidPrsIdx = 0; private var fluidDyeIdx = 0
    private var fluidClock = 0f
    private var fluidSparkle = 0f
    private var simIsHalf = true

    private val audioBuf: FloatBuffer = ByteBuffer.allocateDirect(AudioAnalyzer.TEX_W * 4 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val frame = AudioFrame()

    // timing
    private var lastNs = 0L
    private var time = 0f
    private var mt = 0f
    private var warpFlow = 0f
    private var warpRip = 0f
    private var fpsAcc = 0f
    private var fpsCount = 0
    private var fpsShown = 0f
    private var idleMix = 0f

    // presets
    private var curIdx = 0
    private var prevIdx = 0
    private var trans = 1f
    private var presetTimer = 0f
    private var beatsThisPreset = 0
    private var initialised = false
    private val pCur = FloatArray(P.N)
    private val pPrev = FloatArray(P.N)
    private val pMix = FloatArray(P.N)
    private val layer = LayerState()

    // palette
    private val palCur = FloatArray(12)
    private var palTarget = 0
    private var autoPaletteIdx = 0
    private var palInit = false

    // shockwaves
    private val ringR = FloatArray(6)
    private val ringLife = FloatArray(6)
    private var ringNext = 0

    private fun hasExt(name: String): Boolean {
        val s = GLES30.glGetString(GLES30.GL_EXTENSIONS) ?: return false
        return s.contains(name)
    }

    // ------------------------------------------------------------------ GLSurfaceView.Renderer
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        warpP = program("fullscreen.vert", "warp.frag")
        shapeP = program("shape.vert", "shape.frag")
        downP = program("fullscreen.vert", "down.frag")
        lyricP = program("fullscreen.vert", "lyric.frag")
        lyricTex = 0; lyricShown = ""; lyricAlpha = 0f; lyricHas = false
        upP = program("fullscreen.vert", "up.frag")
        finalP = program("fullscreen.vert", "final.frag")
        barsP = program("bars.vert", "bars.frag")
        ridgeP = program("ridge.vert", "ridge.frag")
        tunnelP = program("fullscreen.vert", "tunnel.frag")
        partP = program("particles.vert", "particles.frag")
        terrainP = program("fullscreen.vert", "terrain.frag")
        postP = program("fullscreen.vert", "post.frag")
        taaP = program("fullscreen.vert", "taa.frag")
        gradeP = program("fullscreen.vert", "grade.frag")
        sphereP = program("fullscreen.vert", "sphere.frag")
        fluidP = program("fullscreen.vert", "fluid.frag")
        marchP = program("fullscreen.vert", "march.frag")
        bar3dP = program("bar3d.vert", "bar3d.frag")
        wave3dP = program("wave3d.vert", "wave3d.frag")
        hexP = program("fullscreen.vert", "hex.frag")
        framesP = program("fullscreen.vert", "frames.frag")
        mazeP = program("fullscreen.vert", "maze.frag")
        fireP = program("fullscreen.vert", "firewave.frag")
        vortexP = program("fullscreen.vert", "vortex.frag")
        tfloorP = program("fullscreen.vert", "tilefloor.frag")
        rad3dP = program("rad3d.vert", "rad3d.frag")
        pulseP = program("fullscreen.vert", "pulse.frag")
        polyP = program("fullscreen.vert", "polytunnel.frag")
        smokeP = program("fullscreen.vert", "smoke.frag")
        spikeP = program("spike3d.vert", "spike3d.frag")
        sbarP = program("fullscreen.vert", "smokebars.frag")
        bloomP = program("fullscreen.vert", "smokebloom.frag")
        trailP = program("trails3d.vert", "trails3d.frag")
        sringP = program("fullscreen.vert", "smokering.frag")
        dfloorP = program("fullscreen.vert", "dotfloor.frag")
        ltunP = program("fullscreen.vert", "ledtunnel.frag")
        rbarsP = program("fullscreen.vert", "ringbars.frag")
        dbarsP = program("fullscreen.vert", "dotbars.frag")
        horiP = program("fullscreen.vert", "horizon.frag")
        glassP = program("glass3d.vert", "glass3d.frag")
        surfP = program("surf.vert", "surf.frag")
        // GL objects from a lost context are gone; forget the fluid targets so they are rebuilt
        fluidKey = ""
        fluidTargets.fill(null)

        val v = IntArray(1)
        GLES30.glGenVertexArrays(1, v, 0); vao = v[0]
        GLES30.glBindVertexArray(vao)
        GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, v, 0); maxTex = v[0]
        hdrSupported = hasExt("GL_EXT_color_buffer_float") || hasExt("GL_EXT_color_buffer_half_float")
        gpuName = (GLES30.glGetString(GLES30.GL_RENDERER) ?: "")

        // audio data texture (R16F, 1024x4)
        GLES30.glGenTextures(1, v, 0); audioTex = v[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, audioTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, AudioAnalyzer.TEX_W, 4, 0, GLES30.GL_RED, GLES30.GL_FLOAT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // ridge-line history texture (R16F, 256 x RROWS) on unit 3
        GLES30.glGenTextures(1, v, 0); ridgeTex = v[0]
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ridgeTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, RCOLS, RROWS, 0, GLES30.GL_RED, GLES30.GL_FLOAT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        java.util.Arrays.fill(ridgeHist, 0f)
        ridgeBuf.clear(); ridgeBuf.put(ridgeHist); ridgeBuf.position(0)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, RCOLS, RROWS, GLES30.GL_RED, GLES30.GL_FLOAT, ridgeBuf)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

        GLES30.glGenTextures(1, v, 0); blackTex = v[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, blackTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()))
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)

        // album-art texture on unit 4 (starts as a 1x1 black pixel)
        GLES30.glGenTextures(1, v, 0); artTex = v[0]
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, artTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
            ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()))
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        artVer = -1; hasArt = false

        // 3D colour-grading LUT on unit 7
        GLES30.glGenTextures(1, v, 0); lutTex = v[0]
        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        GLES30.glTexImage3D(GLES30.GL_TEXTURE_3D, 0, GLES30.GL_RGB8, LUTN, LUTN, LUTN, 0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        java.util.Arrays.fill(lutPal, -9f)
        postT = null; histT[0] = null; histT[1] = null; postKey = ""; histValid = false

        // size dependent resources get rebuilt on demand
        sim[0] = null; sim[1] = null; bloom.clear(); simKey = ""
        lastNs = 0L
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        screenW = max(1, width); screenH = max(1, height)
    }

    private fun ensureBuffers(s: Settings) {
        var scale = s.renderScale.toDouble()
        var w = screenW * scale
        var h = screenH * scale
        val maxPix = 8_300_000.0
        if (w * h > maxPix) { val k = sqrt(maxPix / (w * h)); w *= k; h *= k }
        val lim = min(maxTex, 8192).toDouble()
        if (w > lim) { h *= lim / w; w = lim }
        if (h > lim) { w *= lim / h; h = lim }
        val iw = max(64, w.toInt()); val ih = max(64, h.toInt())
        val half = s.hdr && hdrSupported
        val levels = when (s.bloomQuality) { 0 -> 0; 1 -> 4; else -> 6 }
        val key = "$iw x $ih $half $levels"
        if (key == simKey && sim[0] != null) return

        free(sim[0]); free(sim[1]); bloom.forEach { free(it) }; bloom.clear()
        var useHalf = half
        var a = makeTarget(iw, ih, useHalf, GLES20.GL_MIRRORED_REPEAT)
        var b = if (a != null) makeTarget(iw, ih, useHalf, GLES20.GL_MIRRORED_REPEAT) else null
        if ((a == null || b == null) && useHalf) {
            free(a); free(b); useHalf = false
            a = makeTarget(iw, ih, false, GLES20.GL_MIRRORED_REPEAT)
            b = makeTarget(iw, ih, false, GLES20.GL_MIRRORED_REPEAT)
        }
        sim[0] = a; sim[1] = b; simIsHalf = useHalf
        var bw = iw; var bh = ih
        for (i in 0 until levels) {
            bw = max(2, bw / 2); bh = max(2, bh / 2)
            if (bw < 4 || bh < 4) break
            val t = makeTarget(bw, bh, useHalf, GLES20.GL_CLAMP_TO_EDGE) ?: break
            bloom.add(t)
        }
        simKey = key
        cur = 0
    }

    /** Screen-resolution targets for the cinematic chain (composite, TAA history). */
    private fun ensurePost(half: Boolean, wantHist: Boolean) {
        val key = "$screenW x $screenH $half $wantHist"
        if (key == postKey) return
        free(postT); free(histT[0]); free(histT[1])
        postT = null; histT[0] = null; histT[1] = null
        var useHalf = half
        fun mk() = makeTarget(screenW, screenH, useHalf, GLES20.GL_CLAMP_TO_EDGE)
        val need = if (wantHist) 3 else 1
        var list = ArrayList<Target>()
        for (n in 0 until need) { val t = mk() ?: break; list.add(t) }
        if (list.size < need && useHalf) {
            list.forEach { free(it) }; list = ArrayList(); useHalf = false
            for (n in 0 until need) { val t = mk() ?: break; list.add(t) }
        }
        if (list.isEmpty()) { postKey = key; return }
        postT = list[0]
        if (list.size == 3) { histT[0] = list[1]; histT[1] = list[2] } else list.drop(1).forEach { free(it) }
        histIdx = 0; histValid = false; postKey = key
    }

    private fun smooth01(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    // ------------------------------------------------------------------ frame
    override fun onDrawFrame(gl: GL10?) {
        val s = effective(SettingsStore.value)
        effectiveSettings = s

        // frame limiter
        if (s.maxFps > 0 && lastNs != 0L) {
            val minNs = 1_000_000_000L / s.maxFps
            val wait = lastNs + minNs - System.nanoTime() - 1_500_000L
            if (wait > 1_000_000L) try { Thread.sleep(wait / 1_000_000L) } catch (_: InterruptedException) { }
        }
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 1f / 60f else ((now - lastNs) / 1e9f).coerceIn(0.002f, 0.1f)
        lastNs = now
        val dtS = dt * s.speed
        val dt60 = dtS * 60f
        val dt60i = dt60 * s.intensity
        time += dtS
        realTime += dt
        psTimer += dt
        if (psTimer > 3f) {
            psTimer = 0f
            powerSave = try { (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode } catch (_: Throwable) { false }
        }
        syncArt()
        val artTarget = if (s.artBg && hasArt) 1f else 0f
        artMix += (artTarget - artMix) * (1f - exp(-dt / 0.6f))

        ensureBuffers(s)
        if (s.fxEnabled) ensurePost(simIsHalf, s.taa)
        val fxOn = s.fxEnabled && postT != null
        val srcT = sim[cur] ?: return
        val dstT = sim[1 - cur] ?: return

        // ---- audio
        if (s.source == SourceMode.LOCAL) AudioHub.analyzer.delayMs = s.syncMs
        else if (s.source != SourceMode.DEMO) AudioHub.analyzer.delayMs = 0
        AudioHub.analyzer.analyzeFor(consumerId, dt, s, frame)
        detectDrop(dt)
        val idleTarget = if (frame.silent) 1f else 0f
        idleMix += (idleTarget - idleMix) * (1f - exp(-dt / 0.8f))
        if (idleMix > 0.01f) addIdleSignal(idleMix)
        audioBuf.clear(); audioBuf.put(frame.tex); audioBuf.position(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, audioTex)
        GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, AudioAnalyzer.TEX_W, 4, GLES30.GL_RED, GLES30.GL_FLOAT, audioBuf)

        updateRidge(dt)

        mt += dtS * (0.55f + 0.45f * min(frame.bassAtt, 2.5f) + 0.25f * min(frame.midAtt, 2.5f))

        updatePresets(dt, s)
        updatePalette(dt, s)
        updateShake(dt, if (fxOn) s.shake else 0f)
        run {
            val par = if (fxOn) s.parallax else 0f
            val px = ParallaxSensor.x * par * 0.09f; val py = ParallaxSensor.y * par * 0.09f
            camCur[0] = shake[0] + px; camCur[1] = shake[1] - py; camCur[2] = shake[2]; camCur[3] = shake[3]
            camPrev[0] = shakePrev[0] + parPX; camPrev[1] = shakePrev[1] - parPY; camPrev[2] = shakePrev[2]; camPrev[3] = shakePrev[3]
            parPX = px; parPY = py
        }
        if (fxOn && s.grading > 0.001f) updateLut()

        // ---- mix warp parameters
        val k = smooth01(trans)
        P.defaults(pCur); Presets.all[curIdx].warp(frame, time, mt, pCur)
        if (trans < 1f) {
            P.defaults(pPrev); Presets.all[prevIdx].warp(frame, time, mt, pPrev)
            for (i in 0 until P.N) pMix[i] = pPrev[i] + (pCur[i] - pPrev[i]) * k
            pMix[P.KAL] = if (k < 0.5f) pPrev[P.KAL] else pCur[P.KAL]
        } else System.arraycopy(pCur, 0, pMix, 0, P.N)

        // ---- pass 1: warp the previous frame
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dstT.fbo)
        GLES30.glViewport(0, 0, dstT.w, dstT.h)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(warpP.id)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcT.tex)
        val aspect = screenW.toFloat() / screenH
        warpFlow += pMix[P.FLOWT] * dt60
        warpRip += pMix[P.RIPP] * dt60
        val soft = pMix[P.SOFT]
        val softEff = if (soft >= 0f) 1f - (1f - soft).pow(dt60) else soft * dt60
        GLES30.glUniform1i(warpP.loc("uPrev"), 0)
        GLES30.glUniform2f(warpP.loc("uTexel"), 1f / dstT.w, 1f / dstT.h)
        GLES30.glUniform1f(warpP.loc("uAspect"), aspect)
        GLES30.glUniform1f(warpP.loc("uZoom"), pMix[P.ZOOM].pow(dt60))
        GLES30.glUniform1f(warpP.loc("uRot"), pMix[P.ROT] * dt60)
        GLES30.glUniform1f(warpP.loc("uSwirl"), pMix[P.SWIRL] * dt60)
        GLES30.glUniform1f(warpP.loc("uSwirlR"), pMix[P.SWIRL_R])
        GLES30.glUniform2f(warpP.loc("uTrans"), pMix[P.TX] * dt60, pMix[P.TY] * dt60)
        GLES30.glUniform1f(warpP.loc("uRipA"), pMix[P.RIPA])
        GLES30.glUniform1f(warpP.loc("uRipF"), pMix[P.RIPF])
        GLES30.glUniform1f(warpP.loc("uRipP"), warpRip)
        GLES30.glUniform1f(warpP.loc("uRipR"), pMix[P.RIPR])
        GLES30.glUniform1f(warpP.loc("uFlowA"), pMix[P.FLOWA] * dt60)
        GLES30.glUniform1f(warpP.loc("uFlowS"), pMix[P.FLOWS])
        GLES30.glUniform1f(warpP.loc("uFlowT"), warpFlow)
        GLES30.glUniform1f(warpP.loc("uKal"), pMix[P.KAL])
        GLES30.glUniform1f(warpP.loc("uTwist"), pMix[P.TWIST] * dt60)
        GLES30.glUniform1f(warpP.loc("uDecay"), pMix[P.DECAY].pow(dt60))
        GLES30.glUniform3f(warpP.loc("uTint"), pMix[P.TR].pow(dt60), pMix[P.TG].pow(dt60), pMix[P.TB].pow(dt60))
        GLES30.glUniform1f(warpP.loc("uHue"), pMix[P.HUE] * dt60)
        GLES30.glUniform1f(warpP.loc("uSoft"), softEff)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

        // ---- pass 2: draw the waveforms on top (additive)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        curAspect = aspect
        curH = dstT.h.toFloat()
        GLES30.glUseProgram(barsP.id)
        GLES30.glUniform1i(barsP.loc("uAudio"), 1)
        GLES30.glUniform1f(barsP.loc("uAspect"), aspect)
        uploadPalette(barsP, s)
        GLES30.glUseProgram(ridgeP.id)
        GLES30.glUniform1i(ridgeP.loc("uHist"), 3)
        GLES30.glUniform1f(ridgeP.loc("uAspect"), aspect)
        GLES30.glUniform1i(ridgeP.loc("uRows"), RROWS)
        uploadPalette(ridgeP, s)
        GLES30.glUseProgram(tunnelP.id)
        GLES30.glUniform1i(tunnelP.loc("uAudio"), 1)
        GLES30.glUniform1f(tunnelP.loc("uAspect"), aspect)
        GLES30.glUniform1f(tunnelP.loc("uBass"), min(frame.bassAtt, 2f) * 0.5f)
        GLES30.glUniform1f(tunnelP.loc("uBeat"), frame.beat)
        uploadPalette(tunnelP, s)
        GLES30.glUseProgram(partP.id)
        GLES30.glUniform1i(partP.loc("uAudio"), 1)
        GLES30.glUniform1f(partP.loc("uAspect"), aspect)
        GLES30.glUniform1f(partP.loc("uBass"), min(frame.bassAtt, 2f) * 0.5f)
        GLES30.glUniform1f(partP.loc("uBeat"), frame.beat)
        uploadPalette(partP, s)
        GLES30.glUseProgram(terrainP.id)
        GLES30.glUniform1i(terrainP.loc("uAudio"), 1)
        GLES30.glUniform1i(terrainP.loc("uHist"), 3)
        GLES30.glUniform1f(terrainP.loc("uAspect"), aspect)
        GLES30.glUniform1f(terrainP.loc("uPix"), 1f / curH)
        GLES30.glUniform1f(terrainP.loc("uFrac"), (ridgeAcc / RSTEP).coerceIn(0f, 1f))
        GLES30.glUniform1f(terrainP.loc("uBass"), min(frame.bassAtt, 2f) * 0.5f)
        GLES30.glUniform1f(terrainP.loc("uBeat"), frame.beat)
        uploadPalette(terrainP, s)
        GLES30.glUseProgram(sphereP.id)
        GLES30.glUniform1i(sphereP.loc("uAudio"), 1)
        GLES30.glUniform1f(sphereP.loc("uAspect"), aspect)
        GLES30.glUniform1f(sphereP.loc("uPix"), 1f / curH)
        GLES30.glUniform1f(sphereP.loc("uBass"), min(frame.bassAtt, 2f) * 0.5f)
        GLES30.glUniform1f(sphereP.loc("uBeat"), frame.beat)
        uploadPalette(sphereP, s)
        for (pp in arrayOf(bar3dP, wave3dP, surfP, hexP, framesP, mazeP, fireP, vortexP, tfloorP, rad3dP, pulseP, polyP, smokeP, spikeP, sbarP, bloomP, trailP, sringP, dfloorP, ltunP, rbarsP, dbarsP, horiP, glassP)) {
            GLES30.glUseProgram(pp.id)
            GLES30.glUniform1i(pp.loc("uHist"), 3)
            GLES30.glUniform1i(pp.loc("uAudio"), 1)
            GLES30.glUniform1f(pp.loc("uAspect"), aspect)
            GLES30.glUniform1f(pp.loc("uBass"), min(frame.bassAtt, 2f) * 0.5f)
            GLES30.glUniform1f(pp.loc("uBeat"), frame.beat)
            GLES30.glUniform1f(pp.loc("uKick"), min(frame.kick, 3f) * 0.5f)
            uploadPalette(pp, s)
        }
        curDt = dtS
        for (pp in arrayOf(partP, marchP)) {
            GLES30.glUseProgram(pp.id)
            GLES30.glUniform1i(pp.loc("uAudio"), 1)
            GLES30.glUniform1f(pp.loc("uAspect"), aspect)
            GLES30.glUniform1f(pp.loc("uBass"), min(frame.bassAtt, 2f) * 0.5f)
            GLES30.glUniform1f(pp.loc("uBeat"), frame.beat)
            GLES30.glUniform1f(pp.loc("uKick"), min(frame.kick, 3f) * 0.5f)
            GLES30.glUniform1f(pp.loc("uSnare"), min(frame.snare, 3f) * 0.5f)
            GLES30.glUniform1f(pp.loc("uVocal"), min(frame.vocal, 2f) * 0.6f)
            GLES30.glUniform1f(pp.loc("uPitch"), frame.pitch)
            GLES30.glUniform1f(pp.loc("uBright"), frame.bright)
            uploadPalette(pp, s)
        }
        GLES30.glUseProgram(marchP.id)
        GLES30.glUniform1f(marchP.loc("uPix"), 1f / curH)
        GLES30.glUniform1f(marchP.loc("uMelody"), frame.melody)
        GLES30.glUseProgram(shapeP.id)
        GLES30.glUniform1i(shapeP.loc("uAudio"), 1)
        GLES30.glUniform1f(shapeP.loc("uAspect"), aspect)
        GLES30.glUniform1f(shapeP.loc("uTime"), time)
        GLES30.glUniform1i(shapeP.loc("uN"), POINTS)
        uploadPalette(shapeP, s)
        fxDofW = 0f; fxSunW = 0f; fxReflW = 0f
        if (trans < 1f) drawPreset(prevIdx, 1f - k, dt60i)
        drawPreset(curIdx, if (trans < 1f) k else 1f, dt60i)
        updateAndDrawRings(dtS, dt60i, aspect)
        GLES30.glDisable(GLES30.GL_BLEND)

        cur = 1 - cur
        val result = sim[cur]!!

        // ---- bloom (dual-filter pyramid)
        var haveBloom = false
        val bloomNeeded = s.bloomAmount > 0.001f || (fxOn && (s.streaks + s.flare + s.halation + s.godRays) > 0.001f)
        if (bloom.isNotEmpty() && bloomNeeded) {
            haveBloom = true
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glUseProgram(downP.id)
            GLES30.glUniform1i(downP.loc("uSrc"), 0)
            var srcTex = result.tex; var sw = result.w; var sh = result.h
            for (i in bloom.indices) {
                val t = bloom[i]
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, t.fbo)
                GLES30.glViewport(0, 0, t.w, t.h)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
                GLES30.glUniform2f(downP.loc("uTexel"), 1f / sw, 1f / sh)
                GLES30.glUniform1f(downP.loc("uThreshold"), if (i == 0) 0.45f else -1f)
                GLES30.glUniform1f(downP.loc("uKnee"), 0.35f)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
                srcTex = t.tex; sw = t.w; sh = t.h
            }
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            GLES30.glUseProgram(upP.id)
            GLES30.glUniform1i(upP.loc("uSrc"), 0)
            for (i in bloom.size - 2 downTo 0) {
                val small = bloom[i + 1]; val big = bloom[i]
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, big.fbo)
                GLES30.glViewport(0, 0, big.w, big.h)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, small.tex)
                GLES30.glUniform2f(upP.loc("uTexel"), 1f / small.w, 1f / small.h)
                GLES30.glUniform1f(upP.loc("uWeight"), 0.85f)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            }
            GLES30.glDisable(GLES30.GL_BLEND)
        }

        // ---- final composite to the screen
        if (fxOn) {
            drawCinematic(s, result, haveBloom, dt60)
        } else {
        histValid = false
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenW, screenH)
        GLES30.glUseProgram(finalP.id)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, result.tex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (haveBloom) bloom[0].tex else blackTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, artTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glUniform1i(finalP.loc("uScene"), 0)
        GLES30.glUniform1i(finalP.loc("uBloom"), 2)
        val ss = result.w.toFloat() / screenW
        if (ss > 1.05f) GLES30.glUniform2f(finalP.loc("uSSOff"), 0.25f * ss / result.w, 0.25f * ss / result.h)
        else GLES30.glUniform2f(finalP.loc("uSSOff"), 0f, 0f)
        GLES30.glUniform1f(finalP.loc("uBloomAmt"), if (haveBloom) s.bloomAmount * 0.9f else 0f)
        GLES30.glUniform1f(finalP.loc("uExposure"), s.exposure * (1f + 0.10f * frame.beat + 0.35f * dropKick))
        GLES30.glUniform1f(finalP.loc("uSat"), s.saturation)
        GLES30.glUniform1f(finalP.loc("uGrad"), s.gradientMap)
        GLES30.glUniform1f(finalP.loc("uVig"), s.vignette)
        GLES30.glUniform1f(finalP.loc("uChroma"), s.chroma)
        GLES30.glUniform1f(finalP.loc("uGrain"), s.grain)
        GLES30.glUniform1f(finalP.loc("uTime"), realTime)
        GLES30.glUniform1i(finalP.loc("uArt"), 4)
        GLES30.glUniform1f(finalP.loc("uArtAmt"), s.artAmount * artMix)
        GLES30.glUniform1f(finalP.loc("uArtLod"), s.artBlur * 5f)
        GLES30.glUniform1f(finalP.loc("uArtAspect"), artAspectImg)
        GLES30.glUniform1f(finalP.loc("uAspect"), screenW.toFloat() / screenH)
        GLES30.glUniform1f(finalP.loc("uPhase"), time * 0.01f * s.paletteSpeed)
        uploadPalette(finalP, s)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }

        // Lyrics go straight to the screen after all feedback, bloom and grading: they never spin with the visual or echo.
        if (!wallpaper && !follower) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, screenW, screenH)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            drawLyrics(s, dtS, screenW.toFloat() / screenH)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        if (!wallpaper && !follower && !dim && VisController.screenshot.getAndSet(false)) grabScreen()
        if (!wallpaper && !follower && !dim) {
            val ti = VisController.thumbReq.get()
            if (ti >= 0) {
                if (ti == curIdx && trans >= 1f) { VisController.thumbReq.set(-1); grabThumb(ti) }
                else if (ti != curIdx && trans >= 1f && presetTimer > 8f) VisController.thumbReq.set(-1)   // stale request
            }
        }

        // ---- stats
        fpsAcc += dt; fpsCount++
        if (fpsAcc >= 0.5f && !wallpaper && !follower && !dim) {
            fpsShown = fpsCount / fpsAcc; fpsAcc = 0f; fpsCount = 0
            VisController.publish(VisInfo(Presets.all[curIdx].name, curIdx, palName,
                fpsShown, result.w, result.h, simIsHalf, gpuName, palSwatches()))
        }
    }

    // ------------------------------------------------------------------ pieces
    /** Battery saver and wallpaper mode lower the cost of rendering. */
    private fun effective(raw: Settings): Settings {
        var s = raw
        val cap30 = { f: Int -> if (f in 1..30) f else 30 }
        if (raw.autoBattery && powerSave) {
            s = s.copy(maxFps = cap30(s.maxFps), renderScale = min(s.renderScale, 0.75f), bloomQuality = min(s.bloomQuality, 1))
        }
        if (wallpaper) {
            s = s.copy(maxFps = cap30(s.maxFps), renderScale = min(s.renderScale, 1f), fxEnabled = false)
        }
        if (raw.autoBattery && powerSave) s = s.copy(fxEnabled = false)
        if (dim || raw.dimMode) {
            s = s.copy(maxFps = cap30(s.maxFps), renderScale = min(s.renderScale, 0.75f), fxEnabled = false,
                exposure = s.exposure * raw.dimLevel.coerceIn(0.1f, 1f), bloomQuality = min(s.bloomQuality, 1))
        }
        return s
    }

    /** Uploads new album art to the GPU when the app has a newer image. */
    private fun syncArt() {
        val v = ArtBridge.version.get()
        if (v == artVer) return
        artVer = v
        val b = ArtBridge.bitmap
        if (b == null || b.isRecycled) { hasArt = false; return }
        try {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, artTex)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, b, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            artAspectImg = b.width.toFloat() / max(1, b.height)
            hasArt = true
        } catch (_: Throwable) { hasArt = false }
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun grabScreen() {
        val w = screenW; val h = screenH
        val bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bb)
        val app = ctx.applicationContext
        Thread {
            val msg = try {
                val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bb.rewind(); raw.copyPixelsFromBuffer(bb)
                val m = android.graphics.Matrix().apply { preScale(1f, -1f) }
                val bmp = Bitmap.createBitmap(raw, 0, 0, w, h, m, false)
                saveShot(app, bmp)
            } catch (e: Throwable) { "Couldn't save screenshot" }
            Handler(Looper.getMainLooper()).post { Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
        }.start()
    }

    /** Small copy of the current picture for the effect browser's preview grid. */
    private fun grabThumb(idx: Int) {
        val w = screenW; val h = screenH
        val bb = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glReadPixels(0, 0, w, h, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bb)
        val app = ctx.applicationContext
        Thread {
            try {
                val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bb.rewind(); raw.copyPixelsFromBuffer(bb)
                // crop the middle to 3:2 landscape (flipped, GL rows are bottom-up), then shrink
                val targetH = (w * 2f / 3f).toInt().coerceAtMost(h)
                val y0 = ((h - targetH) / 2).coerceAtLeast(0)
                val m = android.graphics.Matrix().apply { preScale(1f, -1f) }
                val crop = Bitmap.createBitmap(raw, 0, y0, w, targetH, m, false)
                val tw = 320; val th = (tw * targetH / w.toFloat()).toInt().coerceAtLeast(1)
                ThumbStore.put(app, idx, Bitmap.createScaledBitmap(crop, tw, th, true))
            } catch (_: Throwable) { }
        }.start()
    }

    private fun saveShot(app: Context, bmp: Bitmap): String {
        val name = "Plasmora_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date()) + ".png"
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val cv = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Plasmora")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = app.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return "Couldn't save screenshot"
            app.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cv.clear(); cv.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            app.contentResolver.update(uri, cv, null, null)
            return "Saved to Pictures/Plasmora"
        }
        val dir = app.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: return "Couldn't save screenshot"
        dir.mkdirs()
        java.io.File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return "Saved to app Pictures folder"
    }

    private fun addIdleSignal(m: Float) {
        val t = frame.tex
        val w = AudioAnalyzer.TEX_W
        for (i in 0 until w) {
            val x = i / w.toFloat()
            val v = (0.10f * sin(6.2831853f * (x * 2f + time * 0.15f)) + 0.06f * sin(6.2831853f * (x * 5f - time * 0.3f)) +
                0.03f * sin(6.2831853f * (x * 11f + time * 0.5f))) * m
            t[i] += v
            t[w + i] += v * 0.9f
            val sp = (0.22f + 0.12f * sin(6.2831853f * (x * 3f + time * 0.2f)) + 0.06f * sin(6.2831853f * (x * 9f - time * 0.35f))) * m
            t[2 * w + i] = max(t[2 * w + i], sp)
            t[3 * w + i] = max(t[3 * w + i], sp)
        }
    }

    private fun updatePresets(dt: Float, s: Settings) {
        val n = Presets.all.size
        if (!initialised) {
            curIdx = s.lastPreset.coerceIn(0, n - 1); prevIdx = curIdx; trans = 1f; initialised = true
        }
        if (trans < 1f) trans = min(1f, trans + dt / max(0.3f, s.transitionSec))
        presetTimer += dt
        if (frame.beatHit) beatsThisPreset++

        dirUpdate(dt, s)
        if (follower) {
            val li = VisController.leaderIdx.get()
            if (li in 0 until n && li != curIdx) {
                prevIdx = curIdx; curIdx = li; trans = 0f; presetTimer = 0f; beatsThisPreset = 0
                for (i in ringLife.indices) ringLife[i] = 0f
            }
            return
        }
        var target = -1
        val sel = VisController.select.getAndSet(-1)
        if (sel >= 0) target = sel.coerceIn(0, n - 1)
        val nav = VisController.nav.getAndSet(0)
        if (target < 0 && nav != 0) {
            val pl = pool(n, s)
            if (pl.size <= 1) { if (pl.isNotEmpty() && pl[0] != curIdx) target = pl[0] }
            else {
                // step through the pool (all presets, or only favourites when that switch is on)
                var pos = pl.indexOfFirst { it >= curIdx }.let { if (it < 0) 0 else it }
                if (pl[pos] != curIdx) { if (nav > 0) pos = (pos - 1 + pl.size) % pl.size }   // current not in pool
                target = pl[(((pos + nav) % pl.size) + pl.size) % pl.size]
            }
        }
        if (VisController.randomRequests.getAndSet(0) > 0 && target < 0) target = randomOther(n, s)
        if (target < 0 && s.autoChange && !VisController.pinned.get()) {
            val due = presetTimer > s.autoChangeSec
            if (s.director) {
                target = directorPick(n, s, due)
            } else if (s.smartChange) {
                // change on a big moment (a drop after a quiet passage), not just on any beat
                val early = dropHit && presetTimer > s.autoChangeSec * 0.4f
                val late = due && (dropHit || presetTimer > s.autoChangeSec + 12f)
                if (early || late) target = autoNext(n, s)
            } else if (due && (frame.beatHit || presetTimer > s.autoChangeSec + 6f)) {
                target = autoNext(n, s)
            }
        }
        if (target >= 0 && target != curIdx) {
            prevIdx = curIdx; curIdx = target; trans = 0f; presetTimer = 0f; beatsThisPreset = 0
            if (s.autoPalette) {
                val sug = Presets.all[curIdx].palette?.let { nm -> Palettes.all.indexOfFirst { it.name == nm } } ?: -1
                autoPaletteIdx = if (sug >= 0 && Random.nextInt(3) != 0) sug else Random.nextInt(Palettes.all.size)
            }
            if (!wallpaper) SettingsStore.update { it.copy(lastPreset = curIdx) }
            for (i in ringLife.indices) ringLife[i] = 0f
        }
    }


    // ------------------------------------------------------------------ auto-director
    private var songBeats = 0
    private var dirSecAvg = 0f
    private var dirSecBase = 0f
    private var dirSectionJump = false
    private var dirPhrase = false
    private var dirBreakdown = false

    private fun dirUpdate(dt: Float, s: Settings) {
        dirPhrase = false; dirSectionJump = false
        if (frame.silent) { songBeats = 0; dirSecAvg = 0f; dirSecBase = 0f; return }
        if (frame.beatHit) {
            songBeats++
            if (songBeats % 16 == 0) dirPhrase = true        // every four bars
        }
        // section change: slow loudness moves a lot against its own long-term average
        dirSecAvg += (frame.section - dirSecAvg) * (1f - exp(-dt / 20f))
        if (dirSecBase == 0f) dirSecBase = max(frame.section, 0.02f)
        val rel = frame.section / max(dirSecAvg, 0.02f)
        dirBreakdown = rel < 0.7f
        if (presetTimer > 6f && (rel > 1.35f || rel < 0.6f) && frame.beatHit) {
            dirSectionJump = true
            dirSecAvg = frame.section        // reset so one change fires per section
        }
    }

    private fun energyClass(idx: Int): Int {
        return when (PresetCategories.of(Presets.all[idx])) {
            "Smoke & Fluid", "Waves & Ribbons", "Classic" -> 0
            "Raymarched 3D", "3D Terrain & Shapes" -> 1
            "Particles" -> 1
            else -> 2                       // bars, tunnels
        }
    }

    /** Picks a switch moment (drop, section change, phrase boundary) and an effect that matches the energy. */
    private fun directorPick(n: Int, s: Settings, due: Boolean): Int {
        val minDwell = max(8f, s.autoChangeSec * 0.5f)
        if (presetTimer < 6f) return -1
        val dropNow = dropHit && presetTimer > 6f
        val moment = dropNow || (dirSectionJump && presetTimer > minDwell * 0.6f) ||
            (dirPhrase && presetTimer > minDwell && (due || presetTimer > minDwell * 1.4f)) ||
            (due && presetTimer > s.autoChangeSec + 14f)
        if (!moment) return -1
        val wanted = when {
            dropNow || frame.buildUp > 0.6f -> 2
            dirBreakdown -> 0
            frame.section > 0.45f -> 2
            frame.section < 0.18f -> 0
            else -> 1
        }
        val pl = pool(n, s).filter { it != curIdx }
        if (pl.isEmpty()) return -1
        val match = pl.filter { energyClass(it) == wanted }
        val near = pl.filter { kotlin.math.abs(energyClass(it) - wanted) <= 1 }
        val from = if (match.isNotEmpty() && Random.nextInt(4) != 0) match else if (near.isNotEmpty()) near else pl
        return from[Random.nextInt(from.size)]
    }

    /** A "drop": loud energy returning with a bass hit after a quiet passage. */
    private fun detectDrop(dt: Float) {
        dropHit = false
        dropKick *= exp(-dt / 0.45f)
        if (frame.silent) { quietFor = 0f; return }
        val lv = frame.level
        fastLevel += (lv - fastLevel) * (1f - exp(-dt / 0.25f))
        slowLevel += (lv - slowLevel) * (1f - exp(-dt / 7f))
        if (fastLevel < slowLevel * 0.6f) quietFor += dt else if (fastLevel > slowLevel * 0.85f) {
            if (quietFor > 1.2f && (frame.beatHit || frame.bass > 1.5f)) {
                dropHit = true
                dropKick = 1f
            }
            quietFor = 0f
        }
    }

    private fun pool(n: Int, s: Settings): List<Int> {
        if (s.favOnly) {
            val f = s.favourites.filter { it in 0 until n }.sorted()
            if (f.isNotEmpty()) return f
        }
        if (s.skipDisliked) {
            val ok = (0 until n).filter { it !in s.dislikes }
            if (ok.isNotEmpty()) return ok
        }
        return (0 until n).toList()
    }

    private fun autoNext(n: Int, s: Settings): Int {
        val pl = pool(n, s)
        if (pl.size == 1) return pl[0]
        if (s.shuffle) {
            val c = pl.filter { it != curIdx }
            return c[Random.nextInt(c.size)]
        }
        return pl.firstOrNull { it > curIdx } ?: pl[0]
    }

    private fun randomOther(n: Int, s: Settings): Int {
        val pl = pool(n, s).filter { it != curIdx }
        return if (pl.isEmpty()) curIdx else pl[Random.nextInt(pl.size)]
    }

    private fun updatePalette(dt: Float, s: Settings) {
        val ov = if (follower) VisController.leaderPal.get() else VisController.paletteOverride.get()
        palTarget = if (ov >= 0) ov.coerceIn(0, Palettes.all.size - 1)
            else if (s.autoPalette) autoPaletteIdx else s.palette.coerceIn(0, Palettes.all.size - 1)
        val art = ArtBridge.palette
        val useArt = s.artPalette && art != null
        val tv = if (useArt) art!! else Palettes.all[palTarget].v
        palName = if (useArt) "Album art" else Palettes.all[palTarget].name
        if (!follower && !wallpaper && !dim) { VisController.leaderPal.set(palTarget); VisController.leaderIdx.set(curIdx) }
        if (!palInit) { System.arraycopy(tv, 0, palCur, 0, 12); palInit = true }
        val rate = 1f - exp(-dt / max(0.2f, s.transitionSec * 0.45f))
        for (i in 0 until 12) palCur[i] += (tv[i] - palCur[i]) * rate
    }

    // ------------------------------------------------------------------ lyrics drawn inside the visual
    private fun palSwatches(): IntArray {
        val out = IntArray(3)
        for (k in 0 until 3) {
            val t = k * 0.33f
            fun ch(i: Int): Int {
                val v = palCur[i] + palCur[3 + i] * cos(6.28318f * (palCur[6 + i] * t + palCur[9 + i]))
                return (0.25f + 0.75f * v.coerceIn(0f, 1f)).times(255f).toInt().coerceIn(0, 255)
            }
            out[k] = (0xFF shl 24) or (ch(0) shl 16) or (ch(1) shl 8) or ch(2)
        }
        return out
    }

    private fun buildLyricBitmap(text: String): Bitmap {
        val w = 1024; val h = 256
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        tp.color = android.graphics.Color.WHITE
        tp.typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        var size = 96f
        var layout: android.text.StaticLayout
        while (true) {
            tp.textSize = size
            layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, tp, w - 40)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).build()
            if (layout.height <= h - 16 || size <= 28f) break
            size -= 6f
        }
        c.translate(20f, (h - layout.height) / 2f)
        layout.draw(c)
        return bmp
    }

    private fun drawLyrics(s: Settings, dt: Float, aspect: Float) {
        VisController.beatLevel = min(frame.beat, 1.5f)
        VisController.bpm = frame.bpm
        val want = VisController.lyricText
        if (want != lyricShown) {
            if (want.isEmpty()) { lyricHas = false }
            else {
                if (lyricTex == 0) {
                    val v = IntArray(1); GLES30.glGenTextures(1, v, 0); lyricTex = v[0]
                }
                val bmp = buildLyricBitmap(want)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lyricTex)
                GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                bmp.recycle()
                lyricHas = true; lyricAlpha = 0f
            }
            lyricShown = want
        }
        lyricAlpha = if (lyricHas) min(1f, lyricAlpha + dt * 3.5f) else max(0f, lyricAlpha - dt * 4f)
        if (lyricAlpha <= 0.002f || lyricTex == 0) return
        val hw = min(0.88f, 0.22f * 4f / max(aspect, 0.01f))
        val hh = hw / 4f * aspect
        val cy = when (VisController.lyricPos) { 0 -> 0.62f; 2 -> -0.5f; else -> 0.02f }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glUseProgram(lyricP.id)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, lyricTex)
        GLES30.glUniform1i(lyricP.loc("uTex"), 5)
        GLES30.glUniform4f(lyricP.loc("uRect"), 0f, cy, hw, hh)
        GLES30.glUniform1f(lyricP.loc("uAlpha"), lyricAlpha)
        GLES30.glUniform1f(lyricP.loc("uBeat"), min(frame.beat, 1.5f))
        GLES30.glUniform1f(lyricP.loc("uTime"), time)
        uploadPalette(lyricP, s)
        GLES30.glUniform1f(lyricP.loc("uBack"), VisController.lyricBack)
        if (VisController.lyricBack > 0.01f) {   // soft dark halo so the text stays readable on bright scenes
            GLES30.glBlendFunc(GLES30.GL_ZERO, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glUniform1i(lyricP.loc("uPass"), 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        }
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glUniform1i(lyricP.loc("uPass"), 1)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun uploadPalette(p: Prog, s: Settings) {
        val shift = s.hue + time * s.paletteSpeed * 0.02f
        GLES30.glUniform3f(p.loc("pa"), palCur[0], palCur[1], palCur[2])
        GLES30.glUniform3f(p.loc("pb"), palCur[3], palCur[4], palCur[5])
        GLES30.glUniform3f(p.loc("pc"), palCur[6], palCur[7], palCur[8])
        GLES30.glUniform3f(p.loc("pd"), palCur[9] + shift, palCur[10] + shift, palCur[11] + shift)
    }

    private fun drawPreset(idx: Int, weight: Float, dt60: Float) {
        if (weight <= 0.001f) return
        val preset = Presets.all[idx]
        val n = min(MAX_LAYERS, preset.layers.size)
        for (i in 0 until n) {
            layer.reset()
            preset.layers[i](frame, time, mt, layer)
            drawLayer(layer, weight, dt60)
        }
    }

    private fun updateRidge(dt: Float) {
        ridgeAcc += dt
        var pushed = false
        var guard = 0
        while (ridgeAcc >= RSTEP && guard++ < 6) {
            System.arraycopy(ridgeHist, 0, ridgeHist, RCOLS, (RROWS - 1) * RCOLS)
            val t = frame.tex
            for (c in 0 until RCOLS) {
                val b = 2 * AudioAnalyzer.TEX_W + c * 4
                var m = t[b]
                for (j in 1 until 4) if (t[b + j] > m) m = t[b + j]
                ridgeHist[c] = m
            }
            ridgeAcc -= RSTEP
            pushed = true
        }
        if (ridgeAcc > RSTEP) ridgeAcc = 0f
        if (pushed) {
            ridgeBuf.clear(); ridgeBuf.put(ridgeHist); ridgeBuf.position(0)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ridgeTex)
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, 0, RCOLS, RROWS, GLES30.GL_RED, GLES30.GL_FLOAT, ridgeBuf)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        }
    }

    /** Bars (style 0) or filled curve (style 1), with optional peak caps. */
    private fun drawBars(l: LayerState, weight: Float, dt60: Float, style: Int) {
        val p = barsP
        GLES30.glUseProgram(p.id)
        val aspectFactor = if (curAspect < 1f) 0.6f + 0.4f * curAspect else 1f
        val n = if (style == 0) max(8, (l.count * aspectFactor).toInt()) else l.count
        GLES30.glUniform1i(p.loc("uStyle"), style)
        GLES30.glUniform1i(p.loc("uBars"), n)
        GLES30.glUniform1i(p.loc("uMirror"), l.mirror)
        GLES30.glUniform1f(p.loc("uBase"), l.base)
        GLES30.glUniform1f(p.loc("uHeight"), l.height)
        GLES30.glUniform1f(p.loc("uHalfW"), curAspect * l.scale)
        GLES30.glUniform1f(p.loc("uGap"), l.gap)
        GLES30.glUniform1f(p.loc("uPow"), l.power)
        GLES30.glUniform1f(p.loc("uCapH"), l.capH)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uHueLevel"), l.hueLevel)
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.tilt)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1i(p.loc("uSym"), l.p2.toInt())
        GLES30.glUniform1f(p.loc("uSeg"), l.p3)
        GLES30.glUniform1f(p.loc("uSegGap"), l.segGap)
        GLES30.glUniform1f(p.loc("uReflect"), l.reflect)
        GLES30.glUniform1f(p.loc("uFill"), l.core)
        GLES30.glUniform1f(p.loc("uEdgeW"), l.p1)
        GLES30.glUniform1f(p.loc("uYHue"), if (l.mode == 10) l.aux else 0f)
        val halves = if (l.mirror == 0) 1 else 2
        GLES30.glUniform1i(p.loc("uCap"), 0)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight * dt60)
        if (style == 3) {
            GLES30.glUniform1i(p.loc("uBars"), l.count)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, l.count)
        } else if (style == 2) {
            GLES30.glUniform1i(p.loc("uBars"), l.count)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, l.count)
            if (l.caps) {
                GLES30.glUniform1i(p.loc("uCap"), 1)
                GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * 2.2f * weight * dt60)
                GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, l.count)
            }
        } else if (style == 0) {
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, n * halves)
            if (l.caps) {
                GLES30.glUniform1i(p.loc("uCap"), 1)
                GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * 2.2f * weight * dt60)
                GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 4, n * halves)
            }
        } else {
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 2 * n, halves)
        }
    }

    /** Full-screen 3D tunnel. */
    private fun drawTunnel(l: LayerState, weight: Float, dt60: Float) {
        val p = tunnelP
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1f(p.loc("uSides"), l.p1)
        GLES30.glUniform1f(p.loc("uRibs"), l.p2)
        GLES30.glUniform1f(p.loc("uTravel"), l.p3)
        GLES30.glUniform1f(p.loc("uTwist"), l.amp)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight * dt60)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    /** Procedural GPU particles (no CPU state): galaxy, starfield or fountain ring. */
    private fun drawParticles(l: LayerState, weight: Float, dt60: Float) {
        val p = partP
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1i(p.loc("uMode"), l.p1.toInt())
        GLES30.glUniform1f(p.loc("uTravel"), l.p3)
        GLES30.glUniform1f(p.loc("uSize"), l.width * curH)
        GLES30.glUniform1f(p.loc("uScale"), l.scale)
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uArms"), l.p2)
        GLES30.glUniform1f(p.loc("uSwirl"), l.amp)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight * dt60)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, l.count)
    }

    // ------------------------------------------------------------------ cinematic chain
    private fun halton(index: Int, base: Int): Float {
        var f = 1f; var r = 0f; var i = index
        while (i > 0) { f /= base; r += f * (i % base); i /= base }
        return r
    }

    /** Beat-driven camera shake: an underdamped spring kicked on every beat (offset, roll and a small punch-in). */
    private fun updateShake(dt: Float, amt: Float) {
        System.arraycopy(shake, 0, shakePrev, 0, 4)
        if (amt > 0.001f && frame.beatHit && !frame.silent) {
            val str = (0.6f + 0.4f * min(frame.bassAtt, 2f)).coerceIn(0.5f, 1.4f) * amt
            val a = Random.nextFloat() * 6.2831853f
            shakeV[0] += cos(a) * 0.40f * str
            shakeV[1] += sin(a) * 0.40f * str
            shakeV[2] += (if (Random.nextBoolean()) 1f else -1f) * 0.70f * str
            shakeV[3] += 0.80f * str
        }
        val h = min(dt, 0.05f) / 2f
        for (n in 0 until 2) for (i in 0 until 4) {
            val acc = -220f * shake[i] - 14f * shakeV[i]
            shakeV[i] += acc * h
            shake[i] += shakeV[i] * h
        }
        shake[0] = shake[0].coerceIn(-0.02f, 0.02f); shake[1] = shake[1].coerceIn(-0.02f, 0.02f)
        shake[2] = shake[2].coerceIn(-0.04f, 0.04f); shake[3] = shake[3].coerceIn(-0.03f, 0.03f)
        if (amt <= 0.001f) { for (i in 0 until 4) { shake[i] *= 0.8f; shakeV[i] = 0f } }
    }

    /** Rebuilds the 3D colour-grade LUT from the current palette (split-toned shadows and highlights, soft S-curve). */
    private fun updateLut() {
        var diff = 0f
        for (i in 0 until 12) diff = max(diff, kotlin.math.abs(palCur[i] - lutPal[i]))
        if (diff < 0.004f) return
        if ((lutTick++ and 1) == 1 && lutPal[0] > -5f) return      // throttle while the palette is fading
        System.arraycopy(palCur, 0, lutPal, 0, 12)
        fun pl(t: Float, c: Int) = (palCur[c] + palCur[3 + c] * cos(6.2831853f * (palCur[6 + c] * t + palCur[9 + c]))).coerceIn(0f, 1f)
        val shR = pl(0.12f, 0); val shG = pl(0.12f, 1); val shB = pl(0.12f, 2)
        val hiR = pl(0.62f, 0); val hiG = pl(0.62f, 1); val hiB = pl(0.62f, 2)
        val shL = 0.2126f * shR + 0.7152f * shG + 0.0722f * shB
        val hiL = 0.2126f * hiR + 0.7152f * hiG + 0.0722f * hiB
        val sc = floatArrayOf(shR - shL, shG - shL, shB - shL)
        val hc = floatArrayOf(hiR - hiL, hiG - hiL, hiB - hiL)
        val n = LUTN
        lutBuf.clear()
        val o = FloatArray(3)
        for (b in 0 until n) for (g in 0 until n) for (r in 0 until n) {
            o[0] = r / (n - 1f); o[1] = g / (n - 1f); o[2] = b / (n - 1f)
            val lum = 0.2126f * o[0] + 0.7152f * o[1] + 0.0722f * o[2]
            val sw = (1f - lum) * (1f - lum) * 0.07f
            val hw = lum * lum * 0.14f
            for (c in 0 until 3) {
                var v = (o[c] + sc[c] * sw + hc[c] * hw).coerceIn(0f, 1f)
                v += (v * v * (3f - 2f * v) - v) * 0.45f
                o[c] = v
            }
            val l2 = 0.2126f * o[0] + 0.7152f * o[1] + 0.0722f * o[2]
            for (c in 0 until 3) {
                val v = (l2 + (o[c] - l2) * 1.08f).coerceIn(0f, 1f)
                lutBuf.put((v * 255f + 0.5f).toInt().toByte())
            }
        }
        lutBuf.position(0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE7)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
        GLES30.glTexSubImage3D(GLES30.GL_TEXTURE_3D, 0, 0, 0, 0, n, n, n, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, lutBuf)
        GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 4)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    private fun setU4(p: Prog, n: String, a: FloatArray) = GLES30.glUniform4f(p.loc(n), a[0], a[1], a[2], a[3])

    /** Composite -> TAA resolve -> grade/sharpen to the screen. */
    private fun drawCinematic(s: Settings, result: Target, haveBloom: Boolean, dt60: Float) {
        val post = postT ?: return
        val aspect = screenW.toFloat() / screenH
        val beat = frame.beat
        val nb = bloom.size
        val midT = if (haveBloom && nb > 0) bloom[min(1, nb - 1)].tex else blackTex
        val wideT = if (haveBloom && nb > 0) bloom[min(2, nb - 1)].tex else blackTex

        val taaOn = s.taa && histT[0] != null && histT[1] != null
        var jx = 0f; var jy = 0f
        if (taaOn) {
            taaFrame = (taaFrame + 1) and 7
            jx = (halton(taaFrame + 1, 2) - 0.5f) * 0.9f / screenW
            jy = (halton(taaFrame + 1, 3) - 0.5f) * 0.9f / screenH
        }

        // ---- A: composite (HDR)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, post.fbo)
        GLES30.glViewport(0, 0, post.w, post.h)
        GLES30.glUseProgram(postP.id)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, result.tex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (haveBloom) bloom[0].tex else blackTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, artTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE5); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, midT)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE6); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, wideT)
        val p = postP
        GLES30.glUniform1i(p.loc("uScene"), 0); GLES30.glUniform1i(p.loc("uBloom"), 2); GLES30.glUniform1i(p.loc("uBloomMid"), 5)
        GLES30.glUniform1i(p.loc("uBloomWide"), 6); GLES30.glUniform1i(p.loc("uArt"), 4)
        val ss = result.w.toFloat() / screenW
        if (ss > 1.05f) GLES30.glUniform2f(p.loc("uSSOff"), 0.25f * ss / result.w, 0.25f * ss / result.h)
        else GLES30.glUniform2f(p.loc("uSSOff"), 0f, 0f)
        GLES30.glUniform2f(p.loc("uJitter"), jx, jy)
        GLES30.glUniform1f(p.loc("uBloomAmt"), if (haveBloom) s.bloomAmount * 0.9f else 0f)
        GLES30.glUniform1f(p.loc("uChroma"), s.chroma)
        GLES30.glUniform1f(p.loc("uAspect"), aspect)
        GLES30.glUniform1f(p.loc("uTime"), realTime)
        GLES30.glUniform1f(p.loc("uBeat"), beat)
        GLES30.glUniform1f(p.loc("uPhase"), time * 0.01f * s.paletteSpeed)
        setU4(p, "uShake", camCur); setU4(p, "uShakePrev", camPrev)
        val wz = (pMix[P.ZOOM].pow(dt60) - 1f).coerceIn(-0.05f, 0.05f) * 0.5f
        val wr = (pMix[P.ROT] * dt60).coerceIn(-0.05f, 0.05f) * 0.5f
        GLES30.glUniform2f(p.loc("uWarpMV"), wz, wr)
        GLES30.glUniform1f(p.loc("uMB"), s.motionBlur)
        GLES30.glUniform1f(p.loc("uDof"), s.dof * fxDofW)
        GLES30.glUniform1f(p.loc("uDofCamH"), fxDofCamH)
        GLES30.glUniform1f(p.loc("uDofPitch"), fxDofPitch)
        GLES30.glUniform1f(p.loc("uDofFocus"), fxDofFocus)
        GLES30.glUniform2f(p.loc("uSun"), 0.5f, fxSunY)
        GLES30.glUniform1f(p.loc("uGod"), if (haveBloom) s.godRays * fxSunW else 0f)
        GLES30.glUniform1f(p.loc("uRefl"), s.reflections * fxReflW)
        GLES30.glUniform1f(p.loc("uReflY"), fxReflY)
        GLES30.glUniform1f(p.loc("uReflRip"), 0.0015f)
        GLES30.glUniform1f(p.loc("uStreak"), if (haveBloom) s.streaks else 0f)
        GLES30.glUniform1f(p.loc("uFlare"), if (haveBloom) s.flare else 0f)
        GLES30.glUniform1f(p.loc("uHalo"), if (haveBloom) s.halation else 0f)
        GLES30.glUniform1f(p.loc("uArtAmt"), s.artAmount * artMix)
        GLES30.glUniform1f(p.loc("uArtLod"), s.artBlur * 5f)
        GLES30.glUniform1f(p.loc("uArtAspect"), artAspectImg)
        uploadPalette(p, s)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)

        // ---- B: temporal resolve
        var srcTex = post.tex
        if (s.taa && histT[0] != null && histT[1] != null) {
            val wi = histIdx; val ri = 1 - histIdx
            val dst = histT[wi]!!; val hin = histT[ri]!!
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dst.fbo)
            GLES30.glViewport(0, 0, dst.w, dst.h)
            GLES30.glUseProgram(taaP.id)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, post.tex)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE8); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, hin.tex)
            val q = taaP
            GLES30.glUniform1i(q.loc("uCur"), 0); GLES30.glUniform1i(q.loc("uHist"), 8)
            GLES30.glUniform2f(q.loc("uTexel"), 1f / dst.w, 1f / dst.h)
            setU4(q, "uShake", camCur); setU4(q, "uShakePrev", camPrev)
            GLES30.glUniform1f(q.loc("uAspect"), aspect)
            GLES30.glUniform1f(q.loc("uAlpha"), if (histValid) 0.8f else 0f)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            histIdx = ri; histValid = true
            srcTex = dst.tex
        } else histValid = false

        // ---- C: grade, sharpen and output
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, screenW, screenH)
        GLES30.glUseProgram(gradeP.id)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE7); GLES30.glBindTexture(GLES30.GL_TEXTURE_3D, lutTex)
        val g = gradeP
        GLES30.glUniform1i(g.loc("uTex"), 0); GLES30.glUniform1i(g.loc("uLut"), 7)
        GLES30.glUniform2f(g.loc("uTexel"), 1f / screenW, 1f / screenH)
        GLES30.glUniform1f(g.loc("uLutAmt"), s.grading)
        GLES30.glUniform1f(g.loc("uLutN"), LUTN.toFloat())
        GLES30.glUniform1f(g.loc("uSharp"), s.sharpen)
        GLES30.glUniform1f(g.loc("uExposure"), s.exposure * (1f + 0.10f * beat + 0.35f * dropKick))
        GLES30.glUniform1f(g.loc("uSat"), s.saturation)
        GLES30.glUniform1f(g.loc("uGrad"), s.gradientMap)
        GLES30.glUniform1f(g.loc("uVig"), s.vignette)
        GLES30.glUniform1f(g.loc("uGrain"), s.grain)
        GLES30.glUniform1f(g.loc("uTime"), realTime)
        GLES30.glUniform1f(g.loc("uPhase"), time * 0.01f * s.paletteSpeed)
        GLES30.glUniform1f(g.loc("uAspect"), aspect)
        uploadPalette(g, s)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /** Raymarched heightfield: scrolling mesh / synthwave valley / ripple plane. */
    private fun drawTerrain(l: LayerState, weight: Float, dt60: Float) {
        // effect hints: analytic ground depth for depth of field, sun + horizon for rays and reflection
        val mode = l.p1.toInt()
        val fit = min(curAspect, 1f)
        if (weight >= fxDofW) {
            fxDofW = weight; fxDofCamH = l.base; fxDofPitch = l.height
            fxDofFocus = if (mode == 1) 2.8f else if (l.height > 0.1f) l.base / sin(l.height) else 3.2f
        }
        if (mode == 1) {
            val hz = 0.5f + fit * 1.15f * tan(l.height)
            if (weight >= fxSunW) { fxSunW = weight; fxSunY = hz + fit * 0.2f }
            if (weight >= fxReflW) { fxReflW = weight; fxReflY = hz }
        }
        val p = terrainP
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1i(p.loc("uMode"), l.p1.toInt())
        GLES30.glUniform1i(p.loc("uStyle"), l.p2.toInt())
        GLES30.glUniform1f(p.loc("uTravel"), l.p3)
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uCells"), l.count.toFloat() / 10f)
        GLES30.glUniform1f(p.loc("uLineW"), l.width)
        GLES30.glUniform1f(p.loc("uCamH"), l.base)
        GLES30.glUniform1f(p.loc("uPitch"), l.height)
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uHueLevel"), l.hueLevel)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight * dt60)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    /** Spectrum-displaced wire sphere. */
    private fun drawSphere(l: LayerState, weight: Float, dt60: Float) {
        val fit = min(curAspect, 1f)
        if (weight >= fxReflW) { fxReflW = weight; fxReflY = 0.5f + l.cy * fit - l.scale * fit * 1.06f }
        val p = sphereP
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uScale"), l.scale)
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uTime"), l.p3)
        GLES30.glUniform1f(p.loc("uLon"), l.count.toFloat())
        GLES30.glUniform1f(p.loc("uLineW"), l.width)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight * dt60)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }


    // ------------------------------------------------------------------ raymarched scenes
    /** Mandelbulb / metaballs / crystal cathedral, lit by the palette and driven by the stems. */
    private fun drawMarch(l: LayerState, weight: Float, dt60: Float) {
        val p = marchP
        val fit = min(curAspect, 1f)
        if (weight >= fxReflW && l.p1.toInt() == 1) { fxReflW = weight; fxReflY = 0.5f + l.cy * fit - l.scale * fit * 1.0f }
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1i(p.loc("uScene"), l.p1.toInt())
        GLES30.glUniform1i(p.loc("uSteps"), l.count.coerceIn(16, 160))
        GLES30.glUniform1f(p.loc("uScale"), l.scale)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uTime"), l.p3)
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight * dt60)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    // ------------------------------------------------------------------ fluid simulation
    private fun ensureFluid() {
        val aspect = curAspect
        val base = 176
        val vw = if (aspect >= 1f) (base * aspect).toInt() else base
        val vh = if (aspect >= 1f) base else (base / aspect).toInt()
        val key = "$vw x $vh"
        if (key == fluidKey && fluidTargets[0] != null) return
        for (i in fluidTargets.indices) { free(fluidTargets[i]); fluidTargets[i] = null }
        val ok = GLES20.GL_CLAMP_TO_EDGE
        fluidTargets[0] = makeTarget(vw, vh, true, ok); fluidTargets[1] = makeTarget(vw, vh, true, ok)
        fluidTargets[2] = makeTarget(vw, vh, true, ok); fluidTargets[3] = makeTarget(vw, vh, true, ok)
        fluidTargets[4] = makeTarget(vw, vh, true, ok); fluidTargets[5] = makeTarget(vw, vh, true, ok)
        fluidTargets[6] = makeTarget(vw * 2, vh * 2, true, ok); fluidTargets[7] = makeTarget(vw * 2, vh * 2, true, ok)
        fluidKey = if (fluidTargets.all { it != null }) key else ""
        fluidVelIdx = 0; fluidPrsIdx = 0; fluidDyeIdx = 0
    }

    private fun palRgb(t: Float, out: FloatArray) {
        for (c in 0..2) out[c] = (palCur[c] + palCur[3 + c] * cos(6.2831853f * (palCur[6 + c] * t + palCur[9 + c]))).coerceIn(0f, 2f)
    }

    private val spPos = FloatArray(32); private val spVel = FloatArray(32); private val spCol = FloatArray(32)
    private var spN = 0
    private val tmpRgb = FloatArray(3)
    private fun addSplat(x: Float, y: Float, r: Float, vx: Float, vy: Float, hue: Float, amt: Float) {
        if (spN >= 8) return
        palRgb(hue, tmpRgb)
        val i = spN * 4
        spPos[i] = x; spPos[i + 1] = y; spPos[i + 2] = r; spPos[i + 3] = 0f
        spVel[i] = vx; spVel[i + 1] = vy; spVel[i + 2] = 0f; spVel[i + 3] = 0f
        spCol[i] = tmpRgb[0]; spCol[i + 1] = tmpRgb[1]; spCol[i + 2] = tmpRgb[2]; spCol[i + 3] = amt
        spN++
    }

    private fun fluidPass(pass: Int, dst: Target, src: Target?, vel: Target?, aux: Target?) {
        val p = fluidP
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, dst.fbo)
        GLES30.glViewport(0, 0, dst.w, dst.h)
        GLES30.glUniform1i(p.loc("uPass"), pass)
        GLES30.glUniform2f(p.loc("uTexel"), 1f / dst.w, 1f / dst.h)
        if (src != null) { GLES30.glActiveTexture(GLES30.GL_TEXTURE9); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, src.tex) }
        if (vel != null) { GLES30.glActiveTexture(GLES30.GL_TEXTURE10); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, vel.tex) }
        if (aux != null) { GLES30.glActiveTexture(GLES30.GL_TEXTURE11); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, aux.tex) }
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    /**
     * Stable-fluids smoke/ink. The stems steer it: kick = radial burst, snare = side jets,
     * vocal = orbiting swirl emitter, bass = rising plume, treble = sparkle droplets, pitch = hue.
     */
    private fun drawFluid(l: LayerState, weight: Float, dt60: Float) {
        ensureFluid()
        if (fluidKey.isEmpty()) return
        val fb = IntArray(1); GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, fb, 0)
        val vp = IntArray(4); GLES30.glGetIntegerv(GLES30.GL_VIEWPORT, vp, 0)
        val p = fluidP
        val T = fluidTargets
        val dt = curDt.coerceIn(0.004f, 0.033f)
        fluidClock += dt
        val a = frame
        val aspect = curAspect
        val str = l.amp
        val hue0 = l.phase + a.pitch * 0.35f

        // ---- gather emitters
        spN = 0
        val vs = 70f * str
        if (a.beatHit || a.kickHit) {
            val n = 5
            val ph = fluidClock * 0.7f
            for (i in 0 until n) {
                val ang = ph + i * 6.2831853f / n
                addSplat(0.5f + 0.06f * cos(ang) / max(aspect, 1f), 0.5f + 0.06f * sin(ang) * max(aspect, 1f).coerceAtMost(1f) + 0.0f,
                    0.0016f + 0.0012f * a.kick.coerceAtMost(2f), cos(ang) * vs * 2.4f, sin(ang) * vs * 2.4f, hue0 + i * 0.07f, 0.22f)
            }
        }
        if (a.snareHit) {
            val y = 0.35f + 0.3f * ((fluidClock * 1.7f) % 1f)
            addSplat(0.04f, y, 0.0009f, vs * 3.2f, (0.5f - y) * vs, hue0 + 0.33f, 0.25f)
            addSplat(0.96f, y, 0.0009f, -vs * 3.2f, (0.5f - y) * vs, hue0 + 0.38f, 0.25f)
        }
        run {
            val ang = fluidClock * 0.9f
            val rad = 0.2f + 0.08f * sin(fluidClock * 0.43f)
            val x = 0.5f + rad * cos(ang) / max(aspect, 1f)
            val y = 0.5f + rad * sin(ang) * min(aspect, 1f).let { if (aspect < 1f) 1f else 1f }
            val v = a.vocal.coerceAtMost(2f)
            if (v > 0.12f) addSplat(x, y, 0.0012f + 0.0008f * v, -sin(ang) * vs * 0.9f * v, cos(ang) * vs * 0.9f * v, hue0 + 0.15f, 0.05f * v)
        }
        run {
            val b = (a.bassAtt - 0.8f).coerceIn(0f, 1.5f)
            if (b > 0.05f) addSplat(0.5f + 0.1f * sin(fluidClock * 0.6f), 0.06f, 0.0016f, 0f, vs * 1.1f * b, hue0 + 0.6f, 0.03f * b)
        }
        fluidSparkle += (a.treb - 0.9f).coerceAtLeast(0f) * dt * 14f
        while (fluidSparkle > 1f && spN < 8) {
            fluidSparkle -= 1f
            val h = (fluidClock * 91.7f) % 1f; val h2 = (fluidClock * 37.3f + 0.31f) % 1f
            addSplat(0.1f + 0.8f * h, 0.15f + 0.7f * h2, 0.0004f, (h2 - 0.5f) * vs, (h - 0.5f) * vs, hue0 + 0.8f * h, 0.30f)
        }
        if (fluidSparkle > 3f) fluidSparkle = 0f

        // ---- run the solver
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1i(p.loc("uSrc"), 9); GLES30.glUniform1i(p.loc("uVel"), 10); GLES30.glUniform1i(p.loc("uAux"), 11)
        GLES30.glUniform1f(p.loc("uDt"), dt)
        GLES30.glUniform1f(p.loc("uAspect"), aspect)
        val v0 = T[fluidVelIdx]!!; val v1 = T[1 - fluidVelIdx]!!
        GLES30.glUniform2f(p.loc("uVelTexel"), 1f / v0.w, 1f / v0.h)
        // emitters into velocity
        GLES30.glUniform1i(p.loc("uSplatMode"), 0); GLES30.glUniform1i(p.loc("uCount"), spN)
        GLES30.glUniform4fv(p.loc("uSplat[0]"), 8, spPos, 0); GLES30.glUniform4fv(p.loc("uSplatVel[0]"), 8, spVel, 0)
        GLES30.glUniform4fv(p.loc("uSplatCol[0]"), 8, spCol, 0)
        if (spN > 0) { fluidPass(6, v1, v0, null, null); fluidVelIdx = 1 - fluidVelIdx }
        var vel = T[fluidVelIdx]!!; var vel2 = T[1 - fluidVelIdx]!!
        // vorticity confinement
        fluidPass(4, T[5]!!, null, vel, null)
        GLES30.glUniform1f(p.loc("uVort"), 24f * l.p2.coerceAtLeast(0.2f))
        fluidPass(5, vel2, vel, null, T[5]!!); fluidVelIdx = 1 - fluidVelIdx
        vel = T[fluidVelIdx]!!; vel2 = T[1 - fluidVelIdx]!!
        // self-advection
        GLES30.glUniform1f(p.loc("uDiss"), exp(-0.45f * dt))
        fluidPass(0, vel2, vel, vel, null); fluidVelIdx = 1 - fluidVelIdx
        vel = T[fluidVelIdx]!!; vel2 = T[1 - fluidVelIdx]!!
        // pressure projection
        fluidPass(1, T[4]!!, null, vel, null)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, T[2]!!.fbo); GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        fluidPrsIdx = 0
        for (i in 0 until 16) {
            val ps = T[2 + fluidPrsIdx]!!; val pd = T[2 + 1 - fluidPrsIdx]!!
            fluidPass(2, pd, ps, null, T[4]!!); fluidPrsIdx = 1 - fluidPrsIdx
        }
        fluidPass(3, vel2, null, vel, T[2 + fluidPrsIdx]!!); fluidVelIdx = 1 - fluidVelIdx
        vel = T[fluidVelIdx]!!
        // dye: emit, advect, fade
        var d0 = T[6 + fluidDyeIdx]!!; var d1 = T[6 + 1 - fluidDyeIdx]!!
        GLES30.glUniform1i(p.loc("uSplatMode"), 1)
        if (spN > 0) { fluidPass(6, d1, d0, null, null); fluidDyeIdx = 1 - fluidDyeIdx }
        d0 = T[6 + fluidDyeIdx]!!; d1 = T[6 + 1 - fluidDyeIdx]!!
        GLES30.glUniform1f(p.loc("uDiss"), exp(-l.p3.coerceAtLeast(0.2f) * dt))
        fluidPass(0, d1, d0, vel, null); fluidDyeIdx = 1 - fluidDyeIdx
        val dye = T[6 + fluidDyeIdx]!!

        // ---- composite into the scene
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fb[0])
        GLES30.glViewport(vp[0], vp[1], vp[2], vp[3])
        GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        GLES30.glUniform2f(p.loc("uTexel"), 1f / dye.w, 1f / dye.h)
        GLES30.glUniform1i(p.loc("uPass"), 7)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE9); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dye.tex)
        uploadPalette(p, effectiveSettings)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uShade"), l.hueLevel * 40f)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight * dt60)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    }

    /** Scrolling ridge-line spectrum with hidden-line removal. */
    private fun drawRidge(l: LayerState, weight: Float) {
        val p = ridgeP
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1f(p.loc("uFrac"), (ridgeAcc / RSTEP).coerceIn(0f, 1f))
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uWidth"), l.width)
        GLES30.glUniform1f(p.loc("uTop"), l.p1)
        GLES30.glUniform1f(p.loc("uBot"), l.p2)
        GLES30.glUniform1f(p.loc("uSpan"), curAspect * l.scale)
        GLES30.glUniform1f(p.loc("uFar"), l.p3)
        GLES30.glUniform1f(p.loc("uDrop"), 0.14f)
        GLES30.glUniform1f(p.loc("uAlpha"), weight)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity)
        GLES30.glUniform1f(p.loc("uDepthHue"), l.hueLevel)
        GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
        for (row in RROWS - 1 downTo 0) {
            GLES30.glUniform1i(p.loc("uRow"), row)
            for (pass in 0..1) {
                GLES30.glUniform1i(p.loc("uPass"), pass)
                GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 1024)
            }
        }
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
    }

    /** Camera "fit": narrow scenes keep their size in portrait, wide ones are allowed to fill the screen. */
    private fun fit3d(wide: Boolean): Float {
        val f = min(curAspect, 1f)
        val z = effectiveSettings.visZoom.coerceIn(0.4f, 2f)
        return (if (wide) f.pow(0.55f) else f) * z
    }

    /** glBars-style 3D grid of bars: columns = frequency, rows = the last two seconds. */
    private fun drawBars3d(l: LayerState, weight: Float) {
        val p = bar3dP
        GLES30.glUseProgram(p.id)
        val nx = l.count.coerceIn(6, 48)
        val nz = (if (l.p1 > 0f) l.p1.toInt() else 14).coerceIn(4, 24)
        GLES30.glUniform1f(p.loc("uFit"), if (curAspect < 1f) curAspect else 0.72f)
        GLES30.glUniform1f(p.loc("uTime"), time)
        GLES30.glUniform1f(p.loc("uFrac"), (ridgeAcc / RSTEP).coerceIn(0f, 1f))
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uDist"), if (l.dist > 0f) l.dist else 4.2f)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uFocal"), 2.0f)
        GLES30.glUniform1f(p.loc("uSpanX"), l.scale)
        GLES30.glUniform1f(p.loc("uSpanZ"), if (l.p3 > 0f) l.p3 else 1.2f)
        GLES30.glUniform1f(p.loc("uHeight"), l.amp)
        GLES30.glUniform1f(p.loc("uGap"), l.gap)
        GLES30.glUniform1f(p.loc("uPow"), l.power)
        GLES30.glUniform1f(p.loc("uFarFade"), 0.8f)
        GLES30.glUniform1i(p.loc("uNX"), nx)
        GLES30.glUniform1i(p.loc("uNZ"), nz)
        GLES30.glUniform1i(p.loc("uRows"), RROWS)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uHueLevel"), l.hueLevel)
        GLES30.glUniform1f(p.loc("uAlpha"), weight)
        GLES30.glUniform1f(p.loc("uReflect"), l.reflect)
        GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
        if (l.reflect > 0.01f) {
            GLES30.glUniform1i(p.loc("uPass"), 1)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, nx * nz)
        }
        GLES30.glUniform1i(p.loc("uPass"), 0)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, nx * nz)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
    }

    /** Neon ribbon bundle (p1 = 0) or dotted wave landscape (p1 = 1). */
    private fun drawWave3d(l: LayerState, weight: Float) {
        val p = wave3dP
        GLES30.glUseProgram(p.id)
        val dots = l.p1 >= 1f
        val beads = dots && l.aux > 0.5f
        GLES30.glUniform1i(p.loc("uKind"), if (beads) 2 else if (dots) 1 else 0)
        GLES30.glUniform1f(p.loc("uFit"), fit3d(true))
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uDist"), if (l.dist > 0f) l.dist else 3.0f)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uFocal"), 1.7f)
        GLES30.glUniform1f(p.loc("uSpan"), l.scale * max(1f, curAspect * 0.8f))
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uWidth"), l.width)
        GLES30.glUniform1f(p.loc("uTwist"), l.p2)
        GLES30.glUniform1f(p.loc("uRibW"), l.core)
        GLES30.glUniform1f(p.loc("uTime"), time)
        GLES30.glUniform1f(p.loc("uTravel"), l.p3)
        GLES30.glUniform1f(p.loc("uFrac"), (ridgeAcc / RSTEP).coerceIn(0f, 1f))
        GLES30.glUniform1f(p.loc("uPow"), l.power)
        GLES30.glUniform1f(p.loc("uSize"), l.width * (curH / 1080f))
        GLES30.glUniform1f(p.loc("uFar"), 5.2f)
        GLES30.glUniform1f(p.loc("uIntensity"), if (beads) l.intensity else l.intensity * weight)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uHueLevel"), l.hueLevel)
        GLES30.glUniform1f(p.loc("uAlpha"), if (beads) weight else 1f)
        if (beads) GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
        if (dots) {
            val nx = l.count.coerceIn(if (beads) 16 else 80, 900)
            val nz = if (l.p2 > 0f) l.p2.toInt().coerceIn(8, 80) else 38
            GLES30.glUniform1i(p.loc("uLines"), nx)
            GLES30.glUniform1i(p.loc("uRowsN"), nz)
            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, nx * nz)
            if (beads) GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
        } else {
            val nl = l.count.coerceIn(8, 120)
            GLES30.glUniform1i(p.loc("uLines"), nl)
            GLES30.glUniform1i(p.loc("uRowsN"), 1)
            GLES30.glUniform1f(p.loc("uTwist"), if (l.p2 > 0f) l.p2 else 1.1f)
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 2 * 480, nl)
        }
    }

    /** Wireframe surfaces: mountains (p1 = 0, p2 = style) or a radial plane (p1 = 1). */
    private fun drawSurf(l: LayerState, weight: Float) {
        val p = surfP
        GLES30.glUseProgram(p.id)
        val radial = l.p1 >= 1f
        GLES30.glUniform1i(p.loc("uKind"), if (radial) 1 else 0)
        GLES30.glUniform1i(p.loc("uStyle"), l.p2.toInt())
        GLES30.glUniform1i(p.loc("uRowsN"), if (radial) 64 else 110)
        GLES30.glUniform1i(p.loc("uColsN"), if (radial) 192 else 170)
        GLES30.glUniform1f(p.loc("uFit"), fit3d(!radial))
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uDist"), if (l.dist > 0f) l.dist else 3.3f)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uFocal"), 1.7f)
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uCells"), l.count.toFloat() / 10f)
        GLES30.glUniform1f(p.loc("uSpokes"), if (l.aux > 0f) l.aux else 56f)
        GLES30.glUniform1f(p.loc("uTravel"), l.p3)
        GLES30.glUniform1f(p.loc("uFrac"), (ridgeAcc / RSTEP).coerceIn(0f, 1f))
        GLES30.glUniform1f(p.loc("uPow"), l.power)
        GLES30.glUniform1f(p.loc("uSpanX"), l.scale)
        GLES30.glUniform1f(p.loc("uFar"), 8f)
        GLES30.glUniform1f(p.loc("uInner"), l.scale * 0.07f)
        GLES30.glUniform1f(p.loc("uOuter"), l.scale)
        GLES30.glUniform1f(p.loc("uLineW"), l.width * (curH / 1080f).coerceAtLeast(0.6f))
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uHueLevel"), l.hueLevel)
        GLES30.glUniform1f(p.loc("uAlpha"), weight)
        GLES30.glUniform1f(p.loc("uTri"), if (!radial && l.aux > 0.5f) 1f else 0f)
        if (!radial) GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
        else GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight)
        val rows = if (radial) 64 else 110
        val cols = if (radial) 192 else 170
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLE_STRIP, 0, 2 * (cols + 1), rows)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
    }

    /** Generic fullscreen fragment look: every shader declares only the uniforms it needs. */
    private fun drawFull(p: Prog, l: LayerState, weight: Float, dt60: Float) {
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1f(p.loc("uTime"), time)
        GLES30.glUniform1f(p.loc("uZoom"), 1.12f / (min(curAspect, 1f).pow(0.75f) * effectiveSettings.visZoom.coerceIn(0.4f, 2f)))
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uScale"), l.scale)
        GLES30.glUniform1f(p.loc("uWidth"), l.width)
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uP1"), l.p1)
        GLES30.glUniform1f(p.loc("uP2"), l.p2)
        GLES30.glUniform1f(p.loc("uP3"), l.p3)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uCore"), l.core)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight * dt60)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }

    /** Glass bar ring: count bars per ring, p1 rings, scale radius, amp height, width bar fill. */
    private fun drawGlass(l: LayerState, weight: Float) {
        val p = glassP
        GLES30.glUseProgram(p.id)
        val n = l.count.coerceIn(12, 80)
        val rings = l.p1.toInt().coerceIn(1, 3)
        GLES30.glUniform1i(p.loc("uN"), n)
        GLES30.glUniform1i(p.loc("uRings"), rings)
        GLES30.glUniform1f(p.loc("uFit"), fit3d(true))
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uDist"), if (l.dist > 0f) l.dist else 3.2f)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uFocal"), 1.7f)
        GLES30.glUniform1f(p.loc("uR"), l.scale)
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uPow"), l.power)
        GLES30.glUniform1f(p.loc("uBW"), l.width)
        GLES30.glUniform1f(p.loc("uSpin"), l.p3)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uHueLevel"), l.hueLevel)
        GLES30.glUniform1f(p.loc("uCore"), l.core)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 36, n * rings * 2 + 120)
    }

    /** Neon light trails: p1 kind (0 circuit, 1 wire bundle, 2 ribbon), count paths, p2 segments, p3 strands. */
    private fun drawTrails(l: LayerState, weight: Float) {
        val p = trailP
        GLES30.glUseProgram(p.id)
        GLES30.glUniform1i(p.loc("uN"), l.count.coerceIn(1, 80))
        GLES30.glUniform1i(p.loc("uSeg"), l.p2.toInt().coerceIn(2, 40))
        GLES30.glUniform1i(p.loc("uStr"), l.p3.toInt().coerceIn(1, 10))
        GLES30.glUniform1i(p.loc("uKind"), l.p1.toInt())
        GLES30.glUniform1f(p.loc("uFit"), fit3d(true) * 0.85f)
        GLES30.glUniform1f(p.loc("uTime"), time)
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uDist"), if (l.dist > 0f) l.dist else 3.0f)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uFocal"), 1.7f)
        GLES30.glUniform1f(p.loc("uSpeed"), l.power)
        GLES30.glUniform1f(p.loc("uSize"), l.scale)
        GLES30.glUniform1f(p.loc("uWidth"), l.width)
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uGap"), l.gap)
        GLES30.glUniform1f(p.loc("uTumble"), 0f)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uCore"), l.core)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, l.count.coerceIn(1, 80) * l.p2.toInt().coerceIn(2, 40) * l.p3.toInt().coerceIn(1, 10))
    }

    /** Field of sharp spectrum spikes: l.count columns, l.p2 rows, l.scale span, l.amp height. */
    private fun drawSpike3d(l: LayerState, weight: Float) {
        val p = spikeP
        GLES30.glUseProgram(p.id)
        val nx = l.count.coerceIn(12, 90)
        val nz = l.p2.toInt().coerceIn(8, 60)
        GLES30.glUniform1i(p.loc("uNX"), nx)
        GLES30.glUniform1i(p.loc("uNZ"), nz)
        GLES30.glUniform1f(p.loc("uFit"), fit3d(true))
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uDist"), if (l.dist > 0f) l.dist else 3.0f)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uFocal"), 1.7f)
        GLES30.glUniform1f(p.loc("uSpan"), l.scale)
        GLES30.glUniform1f(p.loc("uFar"), 3.4f)
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uPow"), l.power)
        GLES30.glUniform1f(p.loc("uTravel"), l.p3)
        GLES30.glUniform1f(p.loc("uFrac"), (l.p3 * 3f) % 1f)
        GLES30.glUniform1f(p.loc("uSharp"), l.width)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uHueLevel"), l.hueLevel)
        GLES30.glBlendFuncSeparate(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA, GLES30.GL_ZERO, GLES30.GL_ONE)
        GLES30.glUniform1f(p.loc("uWeightDummy"), weight)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 3, nx * nz)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
    }

    /** 3D radial spectrum disc: l.count spokes, l.scale radius, l.amp spike height. */
    private fun drawRad3d(l: LayerState, weight: Float) {
        val p = rad3dP
        GLES30.glUseProgram(p.id)
        val n = l.count.coerceIn(24, 240)
        GLES30.glUniform1i(p.loc("uN"), n)
        GLES30.glUniform1f(p.loc("uFit"), fit3d(true))
        GLES30.glUniform1f(p.loc("uRot"), l.rot)
        GLES30.glUniform1f(p.loc("uTilt"), l.height)
        GLES30.glUniform1f(p.loc("uDist"), if (l.dist > 0f) l.dist else 3.2f)
        GLES30.glUniform1f(p.loc("uCy"), l.cy)
        GLES30.glUniform1f(p.loc("uFocal"), 1.7f)
        GLES30.glUniform1f(p.loc("uR"), l.scale)
        GLES30.glUniform1f(p.loc("uAmp"), l.amp)
        GLES30.glUniform1f(p.loc("uPow"), l.power)
        GLES30.glUniform1f(p.loc("uSpin"), l.p3)
        GLES30.glUniform1f(p.loc("uInner"), l.p1)
        GLES30.glUniform1f(p.loc("uSpikeW"), l.width)
        GLES30.glUniform1f(p.loc("uFloorW"), l.p2)
        GLES30.glUniform1f(p.loc("uIntensity"), l.intensity * weight)
        GLES30.glUniform1f(p.loc("uPhase"), l.phase)
        GLES30.glUniform1f(p.loc("uSpread"), l.spread)
        GLES30.glUniform1f(p.loc("uHueLevel"), l.hueLevel)
        GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 12, n)
    }

    private fun drawLayer(l: LayerState, weight: Float, dt60: Float) {
        val par = effectiveSettings.parallax
        if (par > 0.01f) {
            val px = ParallaxSensor.x * par; val py = ParallaxSensor.y * par
            when (l.mode) {
                17 -> { l.rot += px * 0.9f; l.height += py * 0.5f }
                18, 20 -> { l.rot += px * 2.2f; l.height += py * 1.6f }
                21, 22 -> { l.rot += px * 0.6f; l.height += py * 0.5f }
                23 -> { l.rot += px * (if (l.p1 >= 1f) 0.6f else 0.2f); l.height += py * 0.5f }
                30, 34, 36, 44 -> { l.rot += px * 0.6f; l.height += py * 0.5f }
                25, 29 -> { l.rot += px * 0.05f; l.cy += py * 0.04f }
                14 -> if (l.p1 >= 3f) { l.rot += px * 2.2f; l.height += py * 1.6f }
            }
        }
        when (l.mode) {
            10 -> { drawBars(l, weight, dt60, 0); return }
            13 -> { drawTunnel(l, weight, dt60); return }
            14 -> { drawParticles(l, weight, dt60); return }
            15 -> { drawBars(l, weight, dt60, 2); return }
            11 -> { drawBars(l, weight, dt60, 1); return }
            12 -> { drawRidge(l, weight); return }
            16 -> { drawBars(l, weight, dt60, 3); return }
            17 -> { drawTerrain(l, weight, dt60); return }
            18 -> { drawSphere(l, weight, dt60); return }
            19 -> { drawFluid(l, weight, dt60); return }
            20 -> { drawMarch(l, weight, dt60); return }
            21 -> { drawBars3d(l, weight); return }
            22 -> { drawWave3d(l, weight); return }
            23 -> { drawSurf(l, weight); return }
            24 -> { drawFull(hexP, l, weight, dt60); return }
            25 -> { drawFull(framesP, l, weight, dt60); return }
            26 -> { drawFull(mazeP, l, weight, dt60); return }
            27 -> { drawFull(fireP, l, weight, dt60); return }
            28 -> { drawFull(vortexP, l, weight, dt60); return }
            29 -> { drawFull(tfloorP, l, weight, dt60); return }
            30 -> { drawRad3d(l, weight); return }
            31 -> { drawFull(pulseP, l, weight, dt60); return }
            32 -> { drawFull(polyP, l, weight, dt60); return }
            33 -> { drawFull(smokeP, l, weight, dt60); return }
            34 -> { drawSpike3d(l, weight); return }
            35 -> { drawFull(sbarP, l, weight, dt60); return }
            36 -> { drawTrails(l, weight); return }
            37 -> { drawFull(bloomP, l, weight, dt60); return }
            38 -> { drawFull(sringP, l, weight, dt60); return }
            39 -> { drawFull(dfloorP, l, weight, dt60); return }
            40 -> { drawFull(ltunP, l, weight, dt60); return }
            41 -> { drawFull(rbarsP, l, weight, dt60); return }
            42 -> { drawFull(dbarsP, l, weight, dt60); return }
            43 -> { drawFull(horiP, l, weight, dt60); return }
            44 -> { drawGlass(l, weight); return }
        }
        GLES30.glUseProgram(shapeP.id)
        GLES30.glUniform1i(shapeP.loc("uMode"), l.mode)
        GLES30.glUniform1f(shapeP.loc("uAmp"), l.amp)
        GLES30.glUniform1f(shapeP.loc("uScale"), l.scale)
        GLES30.glUniform1f(shapeP.loc("uWidth"), l.width)
        GLES30.glUniform1f(shapeP.loc("uRot"), l.rot)
        GLES30.glUniform1f(shapeP.loc("uP1"), l.p1)
        GLES30.glUniform1f(shapeP.loc("uP2"), l.p2)
        GLES30.glUniform1f(shapeP.loc("uP3"), l.p3)
        GLES30.glUniform2f(shapeP.loc("uCenter"), l.cx, l.cy)
        GLES30.glUniform1f(shapeP.loc("uIntensity"), l.intensity * weight * dt60)
        GLES30.glUniform1f(shapeP.loc("uPhase"), l.phase)
        GLES30.glUniform1f(shapeP.loc("uSpread"), l.spread)
        GLES30.glUniform1f(shapeP.loc("uCore"), l.core)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 2 * POINTS)
    }

    private fun updateAndDrawRings(dt: Float, dt60: Float, aspect: Float) {
        val preset = Presets.all[curIdx]
        if (preset.shock && frame.beatHit && trans > 0.3f) {
            ringR[ringNext] = 0.04f; ringLife[ringNext] = 1f; ringNext = (ringNext + 1) % ringR.size
        }
        for (i in ringR.indices) {
            if (ringLife[i] <= 0f) continue
            ringR[i] += dt * (0.5f + 0.35f * frame.bassAtt)
            ringLife[i] -= dt * 0.85f
            if (ringLife[i] <= 0f) continue
            layer.reset()
            layer.mode = 9; layer.p1 = 5f; layer.scale = ringR[i]
            layer.width = 0.006f + 0.016f * ringLife[i]
            layer.intensity = 0.22f * ringLife[i] * ringLife[i]
            layer.phase = time * 0.1f + i * 0.17f; layer.spread = 0.5f
            drawLayer(layer, 1f, dt60)
        }
    }
}

package app.plasmora.media

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.view.Choreographer
import android.view.View
import android.view.WindowManager
import app.plasmora.SettingsStore
import app.plasmora.audio.AudioAnalyzer
import app.plasmora.audio.AudioFrame
import app.plasmora.audio.AudioHub
import app.plasmora.gl.Palettes
import app.plasmora.gl.VisController
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min

/**
 * Lights the screen edges (glow and/or spectrum bars) on top of other apps while music plays.
 * Needs the "display over other apps" permission and a running audio source (capture keeps the process alive).
 */
object EdgeOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private var appCtx: Context? = null
    private var view: EdgeView? = null
    private var started = false

    fun canDraw(ctx: Context) = AndroidSettings.canDrawOverlays(ctx)

    /** Idempotent: starts a light watcher that shows the overlay only while it is wanted. */
    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        if (started) return
        started = true
        handler.post(object : Runnable {
            override fun run() { evaluate(); handler.postDelayed(this, 600) }
        })
    }

    private fun evaluate() {
        val c = appCtx ?: return
        val want = SettingsStore.value.edgeOverlay && canDraw(c) && !AppState.foreground
        if (want && view == null) add(c) else if (!want && view != null) remove(c)
    }

    private fun add(c: Context) {
        try {
            val wm = c.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = EdgeView(c)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            )
            wm.addView(v, lp)
            view = v
        } catch (_: Throwable) { view = null }
    }

    private fun remove(c: Context) {
        try { (c.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) } catch (_: Throwable) { }
        view?.stop(); view = null
    }

    private class EdgeView(ctx: Context) : View(ctx), Choreographer.FrameCallback {
        private val frame = AudioFrame()
        private val consumer = 9000 + System.identityHashCode(this) % 1000
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var last = 0L
        private var running = true
        private var shader: LinearGradient? = null
        private val rect = RectF()
        private val rgb = FloatArray(3)
        private var level = 0f
        private var pulse = 0f

        init { Choreographer.getInstance().postFrameCallback(this) }
        fun stop() { running = false }

        override fun doFrame(ns: Long) {
            if (!running) return
            val dt = if (last == 0L) 0.033f else ((ns - last) / 1e9f).coerceIn(0.005f, 0.2f)
            if (ns - last > 30_000_000L) {   // ~30 fps is plenty for an overlay
                last = ns
                AudioHub.analyzer.analyzeFor(consumer, dt, SettingsStore.value, frame)
                level += (frame.level - level) * 0.35f
                pulse = if (frame.beatHit) 1f else pulse * 0.88f
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }

        private fun color(t: Float): Int {
            val s = SettingsStore.value
            val idx = (VisController.leaderPal.get().takeIf { it >= 0 } ?: s.palette).coerceIn(0, Palettes.all.size - 1)
            val v = Palettes.all[idx].v
            for (c in 0..2) rgb[c] = (v[c] + v[3 + c] * cos(2f * PI.toFloat() * (v[6 + c] * t + v[9 + c]))).coerceIn(0f, 1f)
            return Color.rgb((rgb[0] * 255).toInt(), (rgb[1] * 255).toInt(), (rgb[2] * 255).toInt())
        }

        override fun onDraw(canvas: Canvas) {
            val s = SettingsStore.value
            if (frame.silent && level < 0.02f) return
            val w = width.toFloat(); val h = height.toFloat()
            val base = min(w, h) * (0.012f + 0.05f * s.edgeSize)
            val thick = base * (1f + 1.2f * level + 0.9f * pulse)
            val a = (70 + 150 * level.coerceAtMost(1f) + 35 * pulse).toInt().coerceIn(0, 255)
            val tint = color(frame.pitch + 0.15f * frame.bright)
            if (s.edgeStyle == 0 || s.edgeStyle == 2) drawGlow(canvas, w, h, thick, tint, a)
            if (s.edgeStyle == 1 || s.edgeStyle == 2) drawBars(canvas, w, h, base * 2.2f, s.edgeSize)
        }

        private fun drawGlow(c: Canvas, w: Float, h: Float, th: Float, tint: Int, a: Int) {
            if (shader == null || shader!!.hashCode() == 0) shader = LinearGradient(0f, 0f, 1f, 0f, Color.WHITE, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            paint.shader = shader
            paint.colorFilter = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
            paint.alpha = a
            // left edge: gradient runs left->right, scaled to the thickness
            c.save(); c.scale(th, 1f); rect.set(0f, 0f, 1f, h); c.drawRect(rect, paint); c.restore()
            c.save(); c.translate(w, 0f); c.scale(-th, 1f); rect.set(0f, 0f, 1f, h); c.drawRect(rect, paint); c.restore()
            c.save(); c.rotate(90f); c.scale(th, 1f); rect.set(0f, -w, 1f, 0f); c.drawRect(rect, paint); c.restore()
            c.save(); c.translate(0f, h); c.rotate(-90f); c.scale(th, 1f); rect.set(0f, 0f, 1f, w); c.drawRect(rect, paint); c.restore()
            paint.shader = null; paint.colorFilter = null
        }

        private fun drawBars(c: Canvas, w: Float, h: Float, maxH: Float, size: Float) {
            val n = 48
            val bw = w / n
            val tex = frame.tex
            for (i in 0 until n) {
                val k = (i.toFloat() / n * 0.85f * AudioAnalyzer.TEX_W).toInt().coerceIn(0, AudioAnalyzer.TEX_W - 1)
                val v = abs(tex[2 * AudioAnalyzer.TEX_W + k]).coerceIn(0f, 1.2f)
                val bh = (maxH * (0.15f + 3.2f * v * (0.4f + size))).coerceAtMost(h * 0.12f)
                barPaint.color = color(i.toFloat() / n * 0.8f + frame.pitch * 0.2f)
                barPaint.alpha = (90 + 140 * v).toInt().coerceIn(0, 255)
                rect.set(i * bw + bw * 0.15f, h - bh, (i + 1) * bw - bw * 0.15f, h)
                c.drawRoundRect(rect, bw * 0.3f, bw * 0.3f, barPaint)
            }
        }
    }
}

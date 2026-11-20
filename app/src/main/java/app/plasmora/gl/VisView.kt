package app.plasmora.gl

import android.content.Context
import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Surface

/** OpenGL ES 3 surface that hosts the visualizer. */
class VisView(context: Context) : GLSurfaceView(context) {
    val visRenderer = VisualizerRenderer(context)

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        preserveEGLContextOnPause = true
        setRenderer(visRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Hint the display to run at a high refresh rate when the user asked for it. */
    fun applyFrameRate(fps: Int) {
        if (Build.VERSION.SDK_INT >= 30) {
            val s = holder.surface
            if (s != null && s.isValid) {
                try {
                    s.setFrameRate(if (fps <= 0) 120f else fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
                } catch (_: Throwable) { }
            }
        }
    }

    override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, w: Int, h: Int) {
        super.surfaceChanged(holder, format, w, h)
        applyFrameRate(app.plasmora.SettingsStore.value.maxFps)
    }
}

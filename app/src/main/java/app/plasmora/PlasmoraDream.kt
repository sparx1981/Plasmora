package app.plasmora

import android.opengl.GLSurfaceView
import android.service.dreams.DreamService
import app.plasmora.audio.AudioHub
import app.plasmora.gl.VisualizerRenderer
import app.plasmora.media.AppState

/** Screensaver ("Daydream"): dimmed, low-power visuals while the phone or tablet charges or docks. */
class PlasmoraDream : DreamService() {
    private var view: GLSurfaceView? = null
    private var startedDemo = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        SettingsStore.init(applicationContext)
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        val v = GLSurfaceView(this)
        v.setEGLContextClientVersion(3)
        v.setEGLConfigChooser(8, 8, 8, 8, 0, 0)
        v.preserveEGLContextOnPause = true
        v.setRenderer(VisualizerRenderer(applicationContext, wallpaper = true, dim = true))
        v.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        view = v
        setContentView(v)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        if (AudioHub.status.value.active == "Idle") { AudioHub.startDemo(); startedDemo = true }
        view?.onResume()
    }

    override fun onDreamingStopped() {
        view?.onPause()
        if (startedDemo && !AppState.foreground) { AudioHub.stopDemo(); startedDemo = false }
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        view = null
        super.onDetachedFromWindow()
    }
}

package app.plasmora

import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import app.plasmora.audio.AudioHub
import app.plasmora.gl.VisualizerRenderer
import app.plasmora.media.AppState

/** Live wallpaper: the same renderer, running on the home/lock screen at a battery-friendly frame rate. */
class PlasmoraWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = GLEngine()

    inner class GLEngine : Engine() {
        private var view: WallView? = null
        private var startedDemo = false

        inner class WallView : GLSurfaceView(this@PlasmoraWallpaper) {
            override fun getHolder(): SurfaceHolder = surfaceHolder
            fun destroy() { super.onDetachedFromWindow() }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            SettingsStore.init(applicationContext)
            val v = WallView()
            v.setEGLContextClientVersion(3)
            v.setEGLConfigChooser(8, 8, 8, 8, 0, 0)
            v.preserveEGLContextOnPause = true
            v.setRenderer(VisualizerRenderer(applicationContext, wallpaper = true))
            v.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            view = v
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            val v = view ?: return
            if (visible) {
                if (AudioHub.status.value.active == "Idle") { AudioHub.startDemo(); startedDemo = true }
                v.onResume()
                app.plasmora.gl.ParallaxSensor.start(applicationContext)
            } else {
                v.onPause()
                app.plasmora.gl.ParallaxSensor.stop()
                if (startedDemo && !AppState.foreground) { AudioHub.stopDemo(); startedDemo = false }
            }
        }

        override fun onDestroy() {
            view?.destroy(); view = null
            if (startedDemo && !AppState.foreground) AudioHub.stopDemo()
            super.onDestroy()
        }
    }
}

package app.plasmora

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.plasmora.audio.AudioHub
import app.plasmora.audio.CaptureService
import app.plasmora.audio.LocalPlayer
import app.plasmora.gl.VisView
import app.plasmora.media.AppState
import app.plasmora.media.NowPlaying
import app.plasmora.ui.PlasmoraApp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    lateinit var visView: VisView
    lateinit var localPlayer: LocalPlayer
    val inPip = MutableStateFlow(false)
    /** Set when the user taps the tile / notification asking us to start listening. */
    val startRequest = MutableStateFlow(false)
    val resumeTick = MutableStateFlow(0)

    companion object {
        const val EXTRA_START = "app.plasmora.EXTRA_START"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        app.plasmora.media.SongMemory.init(this)
        app.plasmora.media.Lyrics.init(this)
        localPlayer = LocalPlayer(applicationContext)
        visView = VisView(this)
        app.plasmora.media.EdgeOverlay.start(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        // Show something alive immediately; the user then picks a real source.
        if (AudioHub.status.value.active == "Idle") AudioHub.startDemo()

        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val showSplash = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(true) }
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                PlasmoraApp(this@MainActivity)
                if (showSplash.value) app.plasmora.ui.SplashScreen { showSplash.value = false }
            }
        }
    }

    private var returnJob: Job? = null

    /**
     * Called when the user taps the mic button. Once the assistant has got a track playing (a new song, or playback that
     * wasn't running), bring Plasmora back to the front. Gives up after 90 s. Android may refuse to bring an app forward
     * from the background; if so nothing happens and the user simply returns themselves.
     */
    fun armReturnAfterAssistant() {
        returnJob?.cancel()
        val before = NowPlaying.state.value
        returnJob = lifecycleScope.launch {
            val started = withTimeoutOrNull(90_000) {
                NowPlaying.state.first { it.playing && it.title.isNotEmpty() && (!before.playing || it.title != before.title) }
            }
            if (started != null) { delay(1500); bringToFront() }
        }
    }

    private fun bringToFront() {
        try { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)) } catch (_: Throwable) { }
        try { (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).moveTaskToFront(taskId, android.app.ActivityManager.MOVE_TASK_WITH_HOME) } catch (_: Throwable) { }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_START, false) == true) {
            i.removeExtra(EXTRA_START)
            val st = AudioHub.status.value
            if (!st.captureRunning && !st.micRunning) startRequest.value = true
            NowPlaying.cancelOffer(this)
        }
    }

    override fun onStart() {
        super.onStart()
        AppState.foreground = true
    }

    private fun hideSystemBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) updatePipParams()
        if (hasFocus && !inPip.value) hideSystemBars()
    }

    private var parOn = false

    val isTv: Boolean by lazy {
        (getSystemService(UI_MODE_SERVICE) as android.app.UiModeManager).currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        when (keyCode) {
            android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
            android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN,
            android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER,
            android.view.KeyEvent.KEYCODE_MENU, android.view.KeyEvent.KEYCODE_PAGE_UP, android.view.KeyEvent.KEYCODE_PAGE_DOWN,
            android.view.KeyEvent.KEYCODE_CHANNEL_UP, android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (event.repeatCount == 0) app.plasmora.gl.VisController.keys.tryEmit(keyCode)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        visView.onResume()
        resumeTick.value++
        if (!parOn) { parOn = true; app.plasmora.gl.ParallaxSensor.start(this) }
        NowPlaying.onResume(this)
        updatePipParams()
    }

    override fun onPause() {
        if (!isInPictureInPictureMode && !isInMultiWindowMode) visView.onPause()
        super.onPause()
    }

    override fun onStop() {
        AppState.foreground = false
        visView.onPause()
        if (parOn) { parOn = false; app.plasmora.gl.ParallaxSensor.stop() }
        super.onStop()
    }

    fun canPip(): Boolean {
        val st = AudioHub.status.value
        return Build.VERSION.SDK_INT >= 26 && !isInMultiWindowMode && SettingsStore.value.autoPip && (st.captureRunning || st.micRunning)
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= 26 && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            try {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build())
            } catch (_: Throwable) { }
        }
    }

    /** Android 12+: auto-enter PiP only on a real Home gesture, so split-screen is not hijacked. */
    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                setPictureInPictureParams(PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(9, 16)).setAutoEnterEnabled(canPip()).build())
            } catch (_: Throwable) { }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < 31 && canPip() && !isInMultiWindowMode) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) visView.onResume() else hideSystemBars()
    }

    override fun onDestroy() {
        if (isFinishing) {
            if (!SettingsStore.value.keepListening) CaptureService.stop(this)
            AudioHub.stopDemo()
            localPlayer.release()
        }
        super.onDestroy()
    }
}

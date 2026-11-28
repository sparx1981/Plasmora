package app.plasmora.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.view.WindowManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.plasmora.media.NowPlaying
import app.plasmora.media.NowPlayingState
import app.plasmora.media.SongMemory
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import app.plasmora.gl.Presets
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.plasmora.MainActivity
import app.plasmora.Settings
import app.plasmora.SettingsStore
import app.plasmora.SourceMode
import app.plasmora.audio.AudioHub
import app.plasmora.audio.CaptureService
import app.plasmora.gl.VisController
import kotlin.math.abs

private val Accent = Color(0xFF7CF4FF)
private val Accent2 = Color(0xFFFF5FC8)
private val LogoPurple = Color(0xFFB14CFF)
private val LogoGradient = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFB14CFF)))

@Composable
fun PlasmoraApp(activity: MainActivity) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            // colours from the Plasmora logo: cyan, violet and pink on deep indigo
            primary = LogoPurple, onPrimary = Color.White,
            secondary = Color(0xFF00E5FF), onSecondary = Color(0xFF00212A),
            tertiary = Color(0xFFFF4F9A),
            primaryContainer = Color(0xFF3A1B66), onPrimaryContainer = Color(0xFFEBDDFF),
            secondaryContainer = Color(0xFF3A1B66), onSecondaryContainer = Color(0xFFF1E6FF),
            background = Color.Black,
            surface = Color(0xFF120A24), onSurface = Color(0xFFEDE7FF),
            surfaceVariant = Color(0xFF2A1B47), onSurfaceVariant = Color(0xFFCFC2EE),
            outline = Color(0xFF6B4FA0)
        )
    ) {
        val seen by SettingsStore.flow.collectAsState()
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            Root(activity)
            if (!seen.onboarded) Onboarding { SettingsStore.update { it.copy(onboarded = true) } }
        }
    }
}

@Composable
private fun Root(activity: MainActivity) {
    val ctx = LocalContext.current
    val settings by SettingsStore.flow.collectAsState()
    val audio by AudioHub.status.collectAsState()
    val info by VisController.info.collectAsState()
    val local by activity.localPlayer.state.collectAsState()
    val inPip by activity.inPip.collectAsState()
    val np by NowPlaying.state.collectAsState()
    val startReq by activity.startRequest.collectAsState()
    var trackToast by remember { mutableStateOf("") }
    var songPinned by remember { mutableStateOf(false) }

    var uiVisible by remember { mutableStateOf(true) }
    var showSettings by remember { mutableStateOf(false) }
    var showSourcePrompt by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var pendingMode by remember { mutableStateOf<SourceMode?>(null) }
    var projMic by remember { mutableStateOf(true) }

    // Auto-hide the controls a few seconds after the last touch.
    var lastTouch by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(uiVisible, showSettings, lastTouch) {
        if (uiVisible && !showSettings) {
            kotlinx.coroutines.delay(6000)
            uiVisible = false
        }
    }
    // Opened with no audio linked: offer the sources once (after giving auto-start a moment)
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        val st = AudioHub.status.value
        val s = SettingsStore.value
        val linked = st.captureRunning || st.micRunning ||
            s.source == SourceMode.DEMO || (s.source == SourceMode.LOCAL && activity.localPlayer.state.value.hasList)
        if (!linked && !activity.inPip.value && !showSettings) showSourcePrompt = true
    }
    // Build the effect previews automatically on first use (and for any new effects) so nobody has to.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(7000)
        while (activity.inPip.value || showSettings) kotlinx.coroutines.delay(3000)
        if (ThumbStore.count(ctx) < Presets.all.size) PreviewJob.start(ctx, true, 2600L)
    }
    LaunchedEffect(settings.maxFps) { activity.visView.applyFrameRate(settings.maxFps) }
    // dim mode: very low screen brightness for an always-on, OLED-friendly display
    LaunchedEffect(settings.dimMode) {
        val lp = activity.window.attributes
        lp.screenBrightness = if (settings.dimMode) 0.03f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
    }
    // remote control / keyboard (Android TV): left/right change effect, up = random, OK or down = controls, menu = settings
    LaunchedEffect(Unit) {
        VisController.keys.collect { code ->
            if (showSettings) return@collect
            lastTouch = System.nanoTime()
            when (code) {
                android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_CHANNEL_DOWN, android.view.KeyEvent.KEYCODE_PAGE_DOWN -> { VisController.prev(); uiVisible = true }
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_CHANNEL_UP, android.view.KeyEvent.KEYCODE_PAGE_UP -> { VisController.next(); uiVisible = true }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> { VisController.random(); uiVisible = true }
                android.view.KeyEvent.KEYCODE_MENU -> showSettings = true
                else -> uiVisible = !uiVisible
            }
        }
    }

    fun startService(capture: Boolean, mic: Boolean, resultCode: Int = 0, data: Intent? = null) {
        val i = Intent(ctx, CaptureService::class.java)
            .setAction(CaptureService.ACTION_START)
            .putExtra(CaptureService.EXTRA_CAPTURE, capture)
            .putExtra(CaptureService.EXTRA_MIC, mic)
        if (capture) i.putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode).putExtra(CaptureService.EXTRA_DATA, data)
        ContextCompat.startForegroundService(ctx, i)
    }

    val projLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            AudioHub.stopDemo()
            startService(true, projMic, r.resultCode, r.data)
            note = ""
        } else if (projMic) {
            AudioHub.stopDemo()
            startService(false, true)
            note = "Screen-capture permission was declined — using the microphone instead."
        } else {
            note = "Screen-capture permission was declined, so there is nothing to listen to yet. Tap the chip to try again."
        }
    }

    fun proceed(mode: SourceMode) {
        when (mode) {
            SourceMode.AUTO, SourceMode.SYSTEM -> {
                projMic = mode == SourceMode.AUTO
                if (Build.VERSION.SDK_INT >= 29) {
                    val mpm = ctx.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projLauncher.launch(mpm.createScreenCaptureIntent())
                } else if (mode == SourceMode.AUTO) {
                    AudioHub.stopDemo(); startService(false, true)
                    note = "Android 10+ is needed to capture other apps. Using the microphone."
                } else {
                    note = "Capturing other apps needs Android 10 or newer."
                }
            }
            SourceMode.MIC -> { AudioHub.stopDemo(); startService(false, true); note = "" }
            else -> {}
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val mode = pendingMode
        pendingMode = null
        if (mode != null) {
            if (res[Manifest.permission.RECORD_AUDIO] == true || ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) proceed(mode)
            else note = "Microphone permission is required to listen to audio."
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { u ->
                try { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Throwable) { }
            }
            CaptureService.stop(ctx)
            activity.localPlayer.load(uris)
            AudioHub.setActive("Local file")
            SettingsStore.update { it.copy(source = SourceMode.LOCAL) }
            note = ""
        }
    }

    fun selectSource(mode: SourceMode) {
        SettingsStore.update { it.copy(source = mode) }
        note = ""
        when (mode) {
            SourceMode.AUTO, SourceMode.SYSTEM, SourceMode.MIC -> {
                activity.localPlayer.pause()
                val need = ArrayList<String>()
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.RECORD_AUDIO
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need += Manifest.permission.POST_NOTIFICATIONS
                if (need.isNotEmpty()) { pendingMode = mode; permLauncher.launch(need.toTypedArray()) } else proceed(mode)
            }
            SourceMode.LOCAL -> {
                CaptureService.stop(ctx)
                activity.localPlayer.activate(SettingsStore.value.syncMs)
                if (!activity.localPlayer.state.value.hasList) pickLauncher.launch(arrayOf("audio/*"))
                else activity.localPlayer.resume()
            }
            SourceMode.DEMO -> {
                CaptureService.stop(ctx)
                activity.localPlayer.pause()
                AudioHub.startDemo()
            }
        }
    }

    // One-tap start from the Quick Settings tile / notification.
    LaunchedEffect(startReq) {
        if (startReq) {
            activity.startRequest.value = false
            val m = SettingsStore.value.source
            selectSource(if (m == SourceMode.AUTO || m == SourceMode.SYSTEM || m == SourceMode.MIC) m else SourceMode.AUTO)
        }
    }

    // Re-opened with the audio input dead (service gone, or connected but deaf while music plays): fix or ask.
    val resumeTick by activity.resumeTick.collectAsState()
    var reconnect by remember { mutableStateOf(false) }
    LaunchedEffect(resumeTick) {
        if (resumeTick == 0) return@LaunchedEffect
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        for (wait in longArrayOf(5000L, 6000L)) {
            kotlinx.coroutines.delay(wait)
            val src = SettingsStore.value.source
            if (src != SourceMode.AUTO && src != SourceMode.SYSTEM && src != SourceMode.MIC) return@LaunchedEffect
            if (activity.inPip.value) return@LaunchedEffect
            val st = AudioHub.status.value
            val live = st.active == "System audio" || st.active == "Microphone"
            if (live || !am.isMusicActive) return@LaunchedEffect
        }
        if (SettingsStore.value.source == SourceMode.MIC) selectSource(SourceMode.MIC) else reconnect = true
    }
    if (reconnect) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { reconnect = false },
            title = { Text("Audio input lost") },
            text = { Text("Music is playing but Plasmora can't hear it. Reconnect the audio input to carry on.") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { reconnect = false; selectSource(SettingsStore.value.source) }) { Text("Reconnect") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { reconnect = false }) { Text("Not now") } }
        )
    }

    // Per-song memory: when a pinned song starts, restore its effect and hold it.
    LaunchedEffect(np.title, np.artist, settings.songMemory) {
        val e = if (settings.songMemory) SongMemory.get(np.title, np.artist) else null
        if (e != null) {
            VisController.select(e.preset)
            VisController.paletteOverride.set(e.palette)
            VisController.pinned.set(true)
            songPinned = true
        } else {
            VisController.paletteOverride.set(-1)
            VisController.pinned.set(false)
            songPinned = false
        }
    }

    // Brief "now playing" banner when the track changes.
    LaunchedEffect(np.title, np.artist) {
        if (SettingsStore.value.trackToast && np.title.isNotEmpty()) {
            trackToast = if (np.artist.isNotEmpty()) "${np.title} — ${np.artist}" else np.title
            kotlinx.coroutines.delay(4500)
            trackToast = ""
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val pj by PreviewJob.progress.collectAsState()
        if (pj.running) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 48.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(20.dp))
                    .clickable { PreviewJob.cancel() }.padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Preparing previews ${pj.done}/${pj.total}  \u2022  tap to skip", color = Color.White, fontSize = 12.sp)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                (activity.visView.parent as? ViewGroup)?.removeView(activity.visView)
                activity.visView
            }
        )

        // gesture layer
        Box(
            Modifier.fillMaxSize()
                .pointerInput(showSettings) {
                    detectTapGestures(
                        onTap = { uiVisible = !uiVisible; lastTouch = System.nanoTime() },
                        onDoubleTap = { VisController.random() }
                    )
                }
                .pointerInput(showSettings) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (abs(total) > 140f) { if (total < 0) VisController.next() else VisController.prev() }
                        }
                    ) { _, d -> total += d }
                }
        )

        if (settings.lyricsMode != 0 && np.title.isNotEmpty()) LyricsOverlay(np, settings, Modifier.align(when (settings.lyricsPos) { 0 -> Alignment.TopCenter; 2 -> Alignment.BottomCenter; else -> Alignment.Center }))

        if (!inPip) {
            AnimatedVisibility(uiVisible, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    TopBar(info.presetName, info.paletteName, onSettings = { showSettings = true },
                        showPin = settings.songMemory && np.title.isNotEmpty(), pinned = songPinned,
                        onPin = {
                            if (songPinned) {
                                SongMemory.remove(np.title, np.artist)
                                VisController.paletteOverride.set(-1); VisController.pinned.set(false); songPinned = false
                            } else {
                                val ov = VisController.paletteOverride.get()
                                val pal = if (ov >= 0) ov else if (settings.autoPalette) -1 else settings.palette
                                SongMemory.put(np.title, np.artist, info.presetIndex, pal)
                                VisController.pinned.set(true); songPinned = true
                            }
                            lastTouch = System.nanoTime()
                        },
                        isFav = info.presetIndex in settings.favourites,
                        isDis = info.presetIndex in settings.dislikes,
                        onFav = {
                            val i = info.presetIndex
                            SettingsStore.update { it.copy(favourites = if (i in it.favourites) it.favourites - i else it.favourites + i, dislikes = it.dislikes - i) }
                            lastTouch = System.nanoTime()
                        },
                        onDis = {
                            val i = info.presetIndex
                            SettingsStore.update { it.copy(dislikes = if (i in it.dislikes) it.dislikes - i else it.dislikes + i, favourites = it.favourites - i) }
                            lastTouch = System.nanoTime()
                        },
                        onShot = { VisController.takeScreenshot(); lastTouch = System.nanoTime() },
                        onPip = { if (Build.VERSION.SDK_INT >= 26) activity.enterPip() },
                        onPrev = { VisController.prev(); lastTouch = System.nanoTime() },
                        onNext = { VisController.next(); lastTouch = System.nanoTime() },
                        modifier = Modifier.align(Alignment.TopCenter))
                }
            }
            AnimatedVisibility(uiVisible || settings.alwaysShowControls, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    BottomPanel(
                        settings = settings, active = audio.active, status = audio, note = note,
                        np = np,
                        onEnableAccess = {
                            try { ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Throwable) { }
                        },
                        localState = local, onSource = { selectSource(it); lastTouch = System.nanoTime() },
                        onPick = { pickLauncher.launch(arrayOf("audio/*")) },
                        onToggle = { activity.localPlayer.toggle() }, onNext = { activity.localPlayer.next() },
                        onPrev = { activity.localPlayer.prev() }, onSeek = { activity.localPlayer.seekTo(it) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
            AnimatedVisibility(
                trackToast.isNotEmpty(), enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 110.dp)
            ) {
                Text(
                    trackToast, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (settings.voiceAssistantButton) {
                IconButton(
                    onClick = { launchVoiceAssistant(ctx); lastTouch = System.nanoTime() },
                    modifier = Modifier.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                        .padding(end = 16.dp, top = 76.dp).size(40.dp)
                        .clip(RoundedCornerShape(20.dp)).background(Color(0x99000000))
                ) { Icon(ThumbIcons.Mic, "Voice assistant", tint = Color.White, modifier = Modifier.size(22.dp)) }
            }
            if (settings.showFps) {
                Text(
                    "%.0f fps  %dx%d%s".format(info.fps, info.simW, info.simH, if (info.hdr) "  HDR" else ""),
                    color = Color(0xCCFFFFFF), fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 12.dp, top = 64.dp)
                )
            }
        }

        if (showSourcePrompt) {
            app.plasmora.ui.SourcePrompt(settings.source, onSource = { selectSource(it); showSourcePrompt = false; lastTouch = System.nanoTime() }, onDismiss = { showSourcePrompt = false })
        }
        if (showSettings) {
            SettingsSheet(settings = settings, info = info, onSource = { selectSource(it); lastTouch = System.nanoTime() }, onDismiss = { showSettings = false; lastTouch = System.nanoTime() },
                onSelectPreset = { VisController.select(it) })
        }
    }
}

@Composable
private fun TopBar(
    preset: String, palette: String, showPin: Boolean, pinned: Boolean, onPin: () -> Unit,
    isFav: Boolean, isDis: Boolean, onFav: () -> Unit, onDis: () -> Unit, onShot: () -> Unit,
    onSettings: () -> Unit, onPip: () -> Unit,
    onPrev: () -> Unit, onNext: () -> Unit, modifier: Modifier
) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout)).padding(start = 8.dp, end = 8.dp, top = 22.dp, bottom = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrev) { Text("‹", fontSize = 28.sp, color = Color.White) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(preset, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(palette, color = Color(0xB3FFFFFF), fontSize = 11.sp)
            }
            TextButton(onClick = onNext) { Text("›", fontSize = 28.sp, color = Color.White) }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onFav) {
                Icon(ThumbIcons.Up, "Like", tint = if (isFav) Color(0xFF3DA9FF) else Color.White, modifier = Modifier.size(18.dp))
                Text(if (isFav) "  Liked" else "", color = Color(0xFF3DA9FF), fontSize = 13.sp)
            }
            TextButton(onClick = onDis) {
                Icon(ThumbIcons.Down, "Dislike", tint = if (isDis) Color(0xFFFF6B6B) else Color.White, modifier = Modifier.size(18.dp))
                Text(if (isDis) "  Disliked" else "", color = Color(0xFFFF6B6B), fontSize = 13.sp)
            }
            if (showPin) TextButton(onClick = onPin) {
                Text(if (pinned) "📌 Pinned to song" else "📌 Pin to song", color = if (pinned) Color(0xFF7CF4FF) else Color.White, fontSize = 13.sp)
            }
            TextButton(onClick = onShot) { Text("Screenshot", color = Color.White, fontSize = 13.sp) }
            if (Build.VERSION.SDK_INT >= 26) TextButton(onClick = onPip) { Text("PiP", color = Color.White, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun BottomPanel(
    settings: Settings, active: String, status: app.plasmora.audio.AudioStatus, note: String,
    np: NowPlayingState, onEnableAccess: () -> Unit,
    localState: app.plasmora.audio.LocalState,
    onSource: (SourceMode) -> Unit, onPick: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit,
    onPrev: () -> Unit, onSeek: (Long) -> Unit, modifier: Modifier
) {
    Column(
        modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
            .navigationBarsPadding().padding(horizontal = 12.dp).padding(top = 28.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (settings.source == SourceMode.LOCAL) {
            PlayerCard(localState, onPick, onToggle, onNext, onPrev, onSeek, settings.playerOpacity)
        } else if (np.hasAccess) {
            if (np.title.isNotEmpty()) NowPlayingCard(np, settings)
        } else {
            TextButton(onClick = onEnableAccess, modifier = Modifier.fillMaxWidth()) {
                Text("Enable song info & play/pause/skip controls", color = Accent, fontSize = 13.sp)
            }
        }

        val msg = when {
            note.isNotEmpty() -> note
            status.message.isNotEmpty() -> status.message
            settings.source == SourceMode.SYSTEM && status.captureRunning && active.startsWith("System audio —") ->
                if (status.captureSilent) "No audio yet — press play in your music app. (Microphone is off.)"
                else "Waiting for music · microphone is off"
            settings.source == SourceMode.AUTO && status.captureRunning && active == "Microphone" ->
                "No sound from system audio — listening through the microphone"
            active == "Demo" && settings.source != SourceMode.DEMO -> "Showing demo music — pick a source below to listen to yours"
            else -> ""
        }
        if (msg.isNotEmpty()) Text(msg, color = Color(0xE6FFFFFF), fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NowPlayingCard(np: NowPlayingState, settings: Settings) {
    val ctx = LocalContext.current
    val artDp = when (settings.artSize) { 0 -> 0; 2 -> 88; 3 -> 200; else -> 52 }
    val big = settings.artSize == 3
    val openApp = if (settings.artOpensApp) Modifier.clickable { NowPlaying.openPlayer(ctx) } else Modifier
    @Composable fun ArtBox(size: Int) {
        val m = Modifier.height(size.dp).width(size.dp).clip(RoundedCornerShape(if (size > 100) 16.dp else 10.dp)).then(openApp)
        val art = np.art
        if (art != null) Image(art.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = m)
        else Box(m.background(Color(0xFF241B38)))
    }
    val artAccent = remember(np.art, settings.artPalette) {
        val pal = app.plasmora.media.ArtBridge.palette
        if (settings.artPalette && pal != null) Color(app.plasmora.media.ArtPalette.accent(pal)) else null
    }
    val barColour = artAccent ?: LogoPurple
    val playColour = artAccent ?: Accent
    @Composable fun Transport() {
        IconButton(onClick = { NowPlaying.prev() }) { TransportIcon(0) }
        IconButton(onClick = { NowPlaying.toggle() }, modifier = Modifier.height(44.dp).width(44.dp).clip(RoundedCornerShape(22.dp)).then(if (artAccent != null) Modifier.background(playColour) else Modifier.background(LogoGradient))) {
            TransportIcon(if (np.playing) 2 else 1, Color.Black)
        }
        IconButton(onClick = { NowPlaying.next() }) { TransportIcon(3) }
    }
    val bar = settings.progressStyle != 0 && np.durationMs > 0
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF14101F).copy(alpha = settings.playerOpacity)).padding(10.dp)) {
        if (big) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ArtBox(artDp)
                Spacer(Modifier.height(8.dp))
                Text(np.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(np.artist.ifEmpty { np.album }, color = Color(0xB3FFFFFF), fontSize = 12.sp, maxLines = 1)
            }
            if (bar) ProgressBar(np, settings.progressStyle, barColour)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { Transport() }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artDp > 0) { ArtBox(artDp); Spacer(Modifier.width(12.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(np.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(np.artist.ifEmpty { np.album }, color = Color(0xB3FFFFFF), fontSize = 12.sp, maxLines = 1)
                }
                Transport()
            }
            if (bar) ProgressBar(np, settings.progressStyle, barColour)
        }
    }
}

/** Seekable progress bar in several looks. Position keeps ticking between player updates. */
@Composable
private fun ProgressBar(np: NowPlayingState, style: Int, accent: Color) {
    val ctx = LocalContext.current
    val settingsNow by SettingsStore.flow.collectAsState()
    val genre by app.plasmora.media.Genre.state.collectAsState()
    if (style == 3 && settingsNow.dancerGenre && np.artist.isNotBlank()) {
        LaunchedEffect(np.artist) { app.plasmora.media.Genre.load(ctx.applicationContext, np.artist) }
    }
    val genreStyle = if (style == 3 && settingsNow.dancerGenre && genre.key == app.plasmora.media.Genre.keyOf(np.artist)) genre.style else -1
    // the dancer that suits the artist's genre, or a different one per track when the genre is unknown
    val dancer = remember(np.title, genreStyle) { if (genreStyle >= 0) genreStyle else DancerPick.forTitle(np.title) }
    val moves = remember(dancer) { DancerMoves(dancer * 7 + 3) }
    var energy by remember { mutableStateOf(0.5f) }
    var avgBeat by remember { mutableStateOf(0.25f) }
    var now by remember { mutableStateOf(0L) }
    var phase by remember { mutableStateOf(0f) }
    var dance by remember { mutableStateOf(0f) }
    LaunchedEffect(np.playing) {
        var lastT = 0L; var tempo = 100f
        while (true) {
            androidx.compose.runtime.withFrameNanos { t ->
                now = android.os.SystemClock.elapsedRealtime(); phase = t / 1e9f
                val dt = if (lastT == 0L) 0f else ((t - lastT) / 1e9f).coerceIn(0f, 0.1f); lastT = t
                // dance tempo follows the track's detected BPM (one bounce per beat), eased so it never lurches
                val target = VisController.bpm.takeIf { it in 50f..200f } ?: 100f
                tempo += (target - tempo) * (dt * 1.5f).coerceAtMost(1f)
                if (np.playing) dance += dt * 3.14159f * tempo / 60f
                // energy of the music: how hard the beats hit (slow average) plus how fast the tempo is
                avgBeat += (VisController.beatLevel.coerceIn(0f, 1f) - avgBeat) * (dt / 8f).coerceAtMost(1f)
                energy = 0.5f * (avgBeat * 2f).coerceIn(0f, 1f) + 0.5f * ((tempo - 70f) / 90f).coerceIn(0f, 1f)
            }
            if (!np.playing) kotlinx.coroutines.delay(250)
        }
    }
    val live = if (np.playing) np.positionMs + ((now - np.posStamp).coerceAtLeast(0) * np.speed).toLong() else np.positionMs
    var drag by remember { mutableStateOf<Float?>(null) }
    val dur = np.durationMs.coerceAtLeast(1)
    val f = drag ?: (live.toFloat() / dur).coerceIn(0f, 1f)
    val acc = accent
    val dim = Color(0x33FFFFFF)
    Column {
        androidx.compose.foundation.Canvas(
            Modifier.fillMaxWidth().height(if (style == 3) 50.dp else 34.dp)
                .pointerInput(dur) { detectTapGestures { o -> NowPlaying.seekTo((o.x / size.width * dur).toLong().coerceIn(0, dur)) } }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = { drag?.let { NowPlaying.seekTo((it * dur).toLong()) }; drag = null },
                        onDragCancel = { drag = null }
                    ) { ch, _ -> drag = (ch.position.x / size.width).coerceIn(0f, 1f) }
                }
        ) {
            val w = size.width; val h = size.height; val cy = h / 2f; val x = w * f
            val pad = 8.dp.toPx()
            val bt = if (np.playing) 1f else 0.25f
            val beat = (0.5f + 0.5f * kotlin.math.sin(phase * 7f)) * bt
            when (style) {
                1 -> {
                    drawLine(dim, Offset(0f, cy), Offset(w, cy), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(acc, Offset(0f, cy), Offset(x, cy), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(Color.White, 6.dp.toPx(), Offset(x.coerceIn(pad, w - pad), cy))
                }
                2 -> {
                    val amp = 6.dp.toPx() * (if (np.playing) 1f else 0.35f)
                    val path = Path()
                    var px = 0f; path.moveTo(0f, cy)
                    while (px < x) { path.lineTo(px, cy + amp * kotlin.math.sin(px / 9.dp.toPx() * 1.4f - phase * 5f)); px += 3f }
                    drawPath(path, acc, style = androidx.compose.ui.graphics.drawscope.Stroke(3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    drawLine(dim, Offset(x, cy), Offset(w, cy), 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(Color.White, 5.dp.toPx(), Offset(x, cy + amp * kotlin.math.sin(x / 9.dp.toPx() * 1.4f - phase * 5f)))
                }
                3 -> {
                    if (moves.style != dancer || !moves.ready) moves.reset(dancer, energy)
                    moves.update(dance / 6.28318f, energy)
                    drawDancer(f, w, h, phase, dance, np.playing, VisController.beatLevel, acc, dim, dancer, moves)
                }
                4 -> {
                    val n = 44; val gap = w / n; val bw = gap * 0.55f
                    for (i in 0 until n) {
                        val on = (i + 0.5f) / n <= f
                        val a = 0.5f + 0.5f * kotlin.math.sin(phase * 6f + i * 0.9f) * kotlin.math.sin(phase * 3.1f - i * 0.4f)
                        val bh = if (on) (5.dp.toPx() + (h - 8.dp.toPx()) * a * bt) else 4.dp.toPx()
                        drawLine(if (on) acc else dim, Offset(gap * (i + 0.5f), cy + bh / 2), Offset(gap * (i + 0.5f), cy - bh / 2), bw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    }
                }
                5 -> {
                    drawLine(dim, Offset(0f, cy), Offset(w, cy), 2.dp.toPx())
                    val glow = 6.dp.toPx() + 7.dp.toPx() * beat
                    for (k in 4 downTo 1) drawLine(acc.copy(alpha = 0.10f), Offset(0f, cy), Offset(x, cy), glow * k / 2.2f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.95f), Offset(0f, cy), Offset(x, cy), 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(acc.copy(alpha = 0.35f), 8.dp.toPx() + 4.dp.toPx() * beat, Offset(x, cy))
                    drawCircle(Color.White, 3.dp.toPx(), Offset(x, cy))
                }
                else -> {
                    drawLine(dim, Offset(0f, cy), Offset(w, cy), 2.dp.toPx())
                    drawLine(Brush.horizontalGradient(listOf(Color.Transparent, acc), 0f, x.coerceAtLeast(1f)), Offset(0f, cy), Offset(x, cy), 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    val oy = cy + 2.dp.toPx() * kotlin.math.sin(phase * 4f) * bt
                    drawCircle(acc.copy(alpha = 0.25f), 13.dp.toPx() + 3.dp.toPx() * beat, Offset(x, oy))
                    drawCircle(acc.copy(alpha = 0.6f), 8.dp.toPx(), Offset(x, oy))
                    drawCircle(Color.White, 4.5f.dp.toPx(), Offset(x - 1.dp.toPx(), oy - 1.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(fmtTime((f * dur).toLong()), color = Color(0xB3FFFFFF), fontSize = 10.sp)
            Text(fmtTime(np.durationMs), color = Color(0xB3FFFFFF), fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlayerCard(
    st: app.plasmora.audio.LocalState, onPick: () -> Unit, onToggle: () -> Unit,
    onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Long) -> Unit, opacity: Float = 0.8f
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF14101F).copy(alpha = opacity)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (st.hasList) st.title.ifEmpty { "Track ${st.index + 1}" } else "No music loaded",
                    color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(if (st.hasList) "${st.index + 1} / ${st.count}" else "Pick songs from your device",
                    color = Color(0xB3FFFFFF), fontSize = 11.sp)
            }
            TextButton(onClick = onPick) { Text(if (st.hasList) "Change" else "Choose files") }
        }
        if (st.hasList) {
            var drag by remember { mutableStateOf<Float?>(null) }
            val dur = st.durationMs.coerceAtLeast(1)
            Slider(
                value = drag ?: (st.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { onSeek((it * dur).toLong()) }; drag = null },
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmtTime(st.positionMs), color = Color(0xB3FFFFFF), fontSize = 11.sp)
                Text(fmtTime(st.durationMs), color = Color(0xB3FFFFFF), fontSize = 11.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { TransportIcon(0) }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onToggle, modifier = Modifier.height(56.dp).width(56.dp).clip(RoundedCornerShape(28.dp)).background(LogoGradient)) {
                    TransportIcon(if (st.playing) 2 else 1, Color.Black)
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = onNext) { TransportIcon(3) }
            }
        }
    }
}

/** kind: 0 = previous, 1 = play, 2 = pause, 3 = next */
@Composable
private fun TransportIcon(kind: Int, tint: Color = Color.White) {
    androidx.compose.foundation.Canvas(Modifier.height(24.dp).width(24.dp)) {
        val w = size.width; val h = size.height
        when (kind) {
            1 -> drawPath(Path().apply { moveTo(w * 0.28f, h * 0.15f); lineTo(w * 0.85f, h * 0.5f); lineTo(w * 0.28f, h * 0.85f); close() }, tint)
            2 -> {
                drawRect(tint, Offset(w * 0.22f, h * 0.15f), androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.7f))
                drawRect(tint, Offset(w * 0.58f, h * 0.15f), androidx.compose.ui.geometry.Size(w * 0.2f, h * 0.7f))
            }
            3 -> {
                drawPath(Path().apply { moveTo(w * 0.12f, h * 0.2f); lineTo(w * 0.68f, h * 0.5f); lineTo(w * 0.12f, h * 0.8f); close() }, tint)
                drawRect(tint, Offset(w * 0.74f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.6f))
            }
            else -> {
                drawPath(Path().apply { moveTo(w * 0.88f, h * 0.2f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.88f, h * 0.8f); close() }, tint)
                drawRect(tint, Offset(w * 0.12f, h * 0.2f), androidx.compose.ui.geometry.Size(w * 0.14f, h * 0.6f))
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}


@Composable
private fun LyricsOverlay(np: NowPlayingState, settings: Settings, modifier: Modifier) {
    val ctx = LocalContext.current
    val mode = settings.lyricsMode
    val style = settings.lyricsStyle
    val info by VisController.info.collectAsState()
    LaunchedEffect(np.title, np.artist) {
        app.plasmora.media.Lyrics.init(ctx)
        app.plasmora.media.Lyrics.load(np.title, np.artist, np.album, np.durationMs)
    }
    val ly by app.plasmora.media.Lyrics.state.collectAsState()
    var now by remember { mutableStateOf(0L) }
    var beat by remember { mutableStateOf(0f) }
    LaunchedEffect(style) {
        while (true) {
            now = android.os.SystemClock.elapsedRealtime()
            if (style == 2 || style == 3) {
                androidx.compose.runtime.withFrameNanos { _ -> beat += (VisController.beatLevel.coerceIn(0f, 1f) - beat) * 0.35f }
            } else kotlinx.coroutines.delay(200)
        }
    }
    val pos = if (np.playing) np.positionMs + ((now - np.posStamp).coerceAtLeast(0) * np.speed).toLong() else np.positionMs
    // Plain (unsynced) lyrics cannot follow the song, so only synced lyrics are shown.
    val idx = if (ly.synced) ly.lines.indexOfLast { it.timeMs <= pos + 150 } else -1
    val cur = ly.lines.getOrNull(idx)
    val text = if (cur == null) "" else if (mode == 1 && !cur.isKey) "" else cur.text

    // "Inside the visual": hand the line to the renderer, draw nothing here
    VisController.lyricPos = settings.lyricsPos
    VisController.lyricBack = settings.lyricsBack
    androidx.compose.runtime.SideEffect { VisController.lyricText = if (style == 5) text else "" }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { VisController.lyricText = "" } }
    if (style == 5 || text.isEmpty()) return

    val pal = info.palColors.map { Color(it) }.ifEmpty { listOf(Color.White, Color.White, Color.White) }
    val c0 = pal[0]; val c1 = pal.getOrElse(1) { c0 }; val c2 = pal.getOrElse(2) { c0 }
    val center = androidx.compose.ui.text.style.TextAlign.Center
    val padding = when (settings.lyricsPos) { 0 -> Modifier.statusBarsPadding().padding(top = 70.dp); 2 -> Modifier.padding(bottom = 230.dp); else -> Modifier }
    val next = if (mode == 2) ly.lines.getOrNull(idx + 1)?.text else null
    val shadowPlain = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(0f, 2f), 10f)
    val st: androidx.compose.ui.text.TextStyle = when (style) {
        0 -> androidx.compose.ui.text.TextStyle(color = Color.White, shadow = shadowPlain)
        1 -> androidx.compose.ui.text.TextStyle(color = Color.White, shadow = androidx.compose.ui.graphics.Shadow(c0, Offset.Zero, 36f))
        2 -> androidx.compose.ui.text.TextStyle(color = Color(0xFFFFFFFF).copy(alpha = 0.92f),
            shadow = androidx.compose.ui.graphics.Shadow(c1.copy(alpha = 0.55f + 0.45f * beat), Offset.Zero, 28f + 36f * beat))
        3 -> androidx.compose.ui.text.TextStyle(color = Color.White, shadow = androidx.compose.ui.graphics.Shadow(c1, Offset.Zero, 24f))
        else -> androidx.compose.ui.text.TextStyle(brush = Brush.horizontalGradient(listOf(c0, c1, c2)), shadow = shadowPlain)
    }
    val scale = if (style == 3) 1f + 0.14f * beat else 1f
    Box(modifier.fillMaxWidth().then(padding).padding(horizontal = 20.dp), contentAlignment = Alignment.Center) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black.copy(alpha = 0.7f * settings.lyricsBack)).padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, style = st, fontSize = if (mode == 1) 28.sp else 24.sp, fontWeight = FontWeight.Bold, textAlign = center,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
        if (next != null) Text(next, color = Color(0x99FFFFFF), fontSize = 16.sp, textAlign = center,
            style = androidx.compose.ui.text.TextStyle(shadow = shadowPlain))
    }
    }
}


private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDancer(
    f: Float, w: Float, h: Float, phase: Float, dance: Float, playing: Boolean, beat: Float, acc: Color, dim: Color, style: Int,
    moves: DancerMoves
) {
    val u = 1.15.dp.toPx()
    val gy = h - 5.dp.toPx()
    val cap = androidx.compose.ui.graphics.StrokeCap.Round
    drawLine(dim, Offset(0f, gy), Offset(w, gy), 2.dp.toPx(), cap = cap)
    drawLine(acc, Offset(0f, gy), Offset(w * f, gy), 2.dp.toPx(), cap = cap)

    val p = dance
    val blend = moves.blend
    val a = DancerPoses.pose(style, moves.cur, p); val b = DancerPoses.pose(style, moves.next, p)
    val q = FloatArray(16) { a[it] + (b[it] - a[it]) * blend }
    val beatPop = if (playing) beat.coerceIn(0f, 1f) * 0.12f else 0f
    val col = Color(DANCER_COLOURS[style])
    val r = DancerPoses.skeleton(q, u)
    val headR = 3.2f * u
    var lowest = -1e9f
    for (i in listOf(5, 6, 12, 13)) lowest = kotlin.math.max(lowest, r[i].y)
    lowest = kotlin.math.max(lowest, r[14].y + headR)
    val fx = (w * f).coerceIn(14.dp.toPx(), w - 14.dp.toPx()) + q[13] * u
    val dy = gy - lowest - q[12] * 6f * u - q[0] * 2f * u * (1f + beatPop * 4f)
    fun P(i: Int) = Offset(fx + r[i].x, dy + r[i].y)
    val lw = 2.1f * u
    fun seg(i: Int, j: Int) = drawLine(col, P(i), P(j), lw, cap = cap)

    drawOval(Color(0x44000000), Offset(fx - 7 * u, gy - 1.2f * u), androidx.compose.ui.geometry.Size(14 * u, 2.4f * u))
    seg(0, 1); seg(0, 2); seg(1, 3); seg(3, 5); seg(2, 4); seg(4, 6)      // pelvis + legs
    seg(0, 7); seg(8, 9)                                                  // spine + shoulders
    seg(8, 10); seg(10, 12); seg(9, 11); seg(11, 13)                      // arms
    drawCircle(col, 1.3f * u, P(12)); drawCircle(col, 1.3f * u, P(13))
    when (style) {
        2 -> drawRect(col, Offset(P(14).x - 2.9f * u, P(14).y - 2.9f * u), androidx.compose.ui.geometry.Size(5.8f * u, 5.8f * u))
        3 -> { drawCircle(col, headR, P(14)); drawCircle(col, 1.7f * u, P(14) + Offset(0f, -headR * 0.9f)) }
        4 -> { drawCircle(col, headR, P(14))
            val s0 = if (playing) kotlin.math.abs(kotlin.math.sin(phase * 5f)) else 0.3f
            drawCircle(Color.White.copy(alpha = 0.4f + 0.6f * s0), 1.1f * u, P(14) + Offset(-5f * u, -5f * u))
            drawCircle(Color.White.copy(alpha = 1f - 0.6f * s0), 0.9f * u, P(14) + Offset(5.5f * u, -3.5f * u)) }
        5 -> { drawCircle(col, headR, P(14))   // long hair flying with the head-bang
            for (d in -1..1) drawLine(col, P(14) + Offset(d * headR * 0.6f, -headR * 0.5f),
                P(14) + Offset(d * headR * 0.9f - q[10] * u * 0.8f, headR * 1.8f), 1.2f * u, cap = cap) }
        6 -> { drawCircle(col, headR, P(14))   // flower in the hair
            drawCircle(Color(0xFFFF4081), 1.5f * u, P(14) + Offset(-headR * 0.8f, -headR * 0.8f))
            drawCircle(Color.White, 0.6f * u, P(14) + Offset(-headR * 0.8f, -headR * 0.8f)) }
        7 -> { drawCircle(col, headR, P(14))   // cowboy hat
            val hc = P(14)
            drawLine(col, hc + Offset(-headR * 1.9f, -headR * 0.7f), hc + Offset(headR * 1.9f, -headR * 0.7f), 1.3f * u, cap = cap)
            drawRect(col, Offset(hc.x - headR * 0.8f, hc.y - headR * 1.7f), androidx.compose.ui.geometry.Size(headR * 1.6f, headR * 1.0f)) }
        else -> drawCircle(col, headR, P(14))
    }
}

// ---- eight dancers, each with its own look and its own eight moves (see DancerPoses)
object DancerPick {
    private var lastTitle = ""
    private var idx = 0
    /** A different dancer for every new track (same one while the same track plays) when the genre is unknown. */
    fun forTitle(t: String): Int {
        if (t != lastTitle) { idx = (idx + 1 + (kotlin.math.abs(t.hashCode()) % 7)) % DancerPoses.STYLES; lastTitle = t }
        return idx
    }
}

private val DANCER_COLOURS = longArrayOf(0xFFFFFFFF, 0xFFFF9A3C, 0xFF7CF4FF, 0xFFFF8FD0, 0xFFFFD54F, 0xFFFF5252, 0xFF69F0AE, 0xFFB388FF)

/**
 * Opens the user's default voice assistant the same way its hardware button does (ACTION_ASSIST), so Gemini shows its
 * small overlay at the bottom of the screen. The default assistant is read from system settings and addressed directly,
 * so Android never shows a "Complete action using" chooser. Falls back to the system assistant settings.
 */
private fun launchVoiceAssistant(ctx: android.content.Context) {
    val pm = ctx.packageManager
    fun go(i: Intent): Boolean = try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Throwable) { false }
    // package of the default assistant (e.g. Google/Gemini), from "package/service"
    val pkg = try {
        android.provider.Settings.Secure.getString(ctx.contentResolver, "assistant")?.let { android.content.ComponentName.unflattenFromString(it)?.packageName }
    } catch (_: Throwable) { null }
    if (pkg != null) {
        for (action in listOf(Intent.ACTION_ASSIST, Intent.ACTION_VOICE_COMMAND, "android.intent.action.VOICE_ASSIST")) {
            val probe = Intent(action).setPackage(pkg)
            val ri = try { pm.queryIntentActivities(probe, 0).firstOrNull() } catch (_: Throwable) { null }
            // address the first matching activity explicitly so no chooser can appear
            val intent = if (ri != null) Intent(action).setClassName(ri.activityInfo.packageName, ri.activityInfo.name) else probe
            if (go(intent)) return
        }
    }
    // default assistant unknown: plain assist request (may show a chooser if several assistants exist)
    if (go(Intent(Intent.ACTION_ASSIST))) return
    if (go(Intent(Intent.ACTION_VOICE_COMMAND))) return
    if (!go(Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS))) go(Intent(android.provider.Settings.ACTION_SETTINGS))
}

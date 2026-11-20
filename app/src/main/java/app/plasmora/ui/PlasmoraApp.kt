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
    val dancer = remember(np.title) { DancerPick.forTitle(np.title) }
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
                3 -> drawDancer(f, w, h, phase, dance, np.playing, VisController.beatLevel, acc, dim, dancer)
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


// ---------------------------------------------------------------- dancer
// Pose vector: 0 bob, 1 lean, 2 armR, 3 elbowR, 4 armL, 5 elbowL, 6 thighR, 7 kneeR, 8 thighL, 9 kneeL,
//              10 head tilt, 11 shoulder width scale, 12 hop (up), 13 sway x
private const val NMOVES = 7
private fun dancePose(move: Int, p: Float): FloatArray {
    val s = kotlin.math.sin(p); val c = kotlin.math.cos(p)
    val k = 0.5f + 0.5f * s
    val o = FloatArray(14); o[11] = 1f
    when (move) {
        0 -> { // groove: bouncing knees, swinging bent arms
            o[0] = 0.15f * kotlin.math.abs(s); o[1] = 0.08f * s; o[2] = 0.55f + 0.5f * s; o[3] = 1.1f
            o[4] = -(0.55f - 0.5f * s); o[5] = -1.1f
            o[6] = 0.12f + 0.1f * s; o[7] = 0.5f * k; o[8] = -(0.12f - 0.1f * s); o[9] = -0.5f * (1f - k)
            o[10] = 0.15f * s; o[13] = 1.2f * s
        }
        1 -> { // disco point
            val g = 0.5f + 0.5f * kotlin.math.sin(p * 0.5f)
            o[2] = 0.4f + 2.3f * g; o[3] = 0.2f; o[4] = -(0.5f + 2.2f * (1f - g)); o[5] = -0.3f
            o[1] = 0.2f * (g - 0.5f); o[6] = 0.25f * g; o[7] = 0.7f * g; o[8] = -0.15f * (1f - g); o[9] = -0.3f * (1f - g)
            o[0] = 0.12f * kotlin.math.abs(s); o[10] = 0.3f * (g - 0.5f); o[13] = 2f * (g - 0.5f)
        }
        2 -> { // jumping jacks
            o[2] = 0.25f + 2.6f * k; o[3] = 0.1f; o[4] = -o[2]; o[5] = -0.1f
            o[6] = 0.06f + 0.4f * k; o[7] = 0.1f; o[8] = -o[6]; o[9] = -0.1f
            o[12] = 0.9f * k * k; o[0] = 0.1f
        }
        3 -> { // hands up, waving
            o[2] = 2.55f + 0.35f * s; o[3] = 0.5f * kotlin.math.sin(p + 1f)
            o[4] = -(2.55f + 0.35f * kotlin.math.sin(p + 2.2f)); o[5] = -0.5f * kotlin.math.sin(p + 2.5f)
            o[1] = 0.15f * s; o[6] = 0.1f; o[7] = 0.4f * k; o[8] = -0.1f; o[9] = -0.4f * (1f - k)
            o[0] = 0.1f * (1f - kotlin.math.abs(c)); o[13] = 2f * s; o[10] = 0.25f * s
        }
        4 -> { // run on the spot
            val r = p * 1.5f; val rs = kotlin.math.sin(r); val rc = kotlin.math.cos(r)
            o[6] = 0.85f * rs; o[7] = 0.9f * (0.5f - 0.5f * rc).coerceAtLeast(0f); o[8] = -0.85f * rs; o[9] = -0.9f * (0.5f + 0.5f * rc)
            o[2] = -0.9f * rs; o[3] = 1.3f; o[4] = 0.9f * rs; o[5] = -1.3f
            o[1] = 0.25f; o[12] = 0.5f * kotlin.math.abs(rs)
        }
        5 -> { // spin: body turns edge-on and back, arms flung out
            val t = p * 0.6f
            o[11] = kotlin.math.cos(t)
            o[2] = 1.55f + 0.2f * s; o[3] = 0.15f; o[4] = -(1.55f + 0.2f * kotlin.math.sin(p + 1f)); o[5] = -0.15f
            o[6] = 0.15f * kotlin.math.sin(t); o[7] = 0.2f; o[8] = -0.1f; o[9] = -0.5f
            o[12] = 0.5f * kotlin.math.abs(kotlin.math.sin(t)); o[10] = 0.2f * kotlin.math.sin(t)
        }
        else -> { // sway: slow side-to-side with a hip pop
            val q = p * 0.5f; val qs = kotlin.math.sin(q)
            o[13] = 3f * qs; o[1] = 0.18f * qs; o[0] = 0.2f * kotlin.math.abs(qs)
            o[2] = 1.3f + 0.7f * qs; o[3] = 0.9f; o[4] = -(1.3f - 0.7f * qs); o[5] = -0.9f
            o[6] = 0.2f * qs; o[7] = 0.55f * (0.5f + 0.5f * qs); o[8] = 0.2f * qs; o[9] = -0.55f * (0.5f - 0.5f * qs)
            o[10] = 0.3f * qs
        }
    }
    return o
}

private fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

// ---- five dancers, each with its own look and its own four moves
object DancerPick {
    private var lastTitle = ""
    private var idx = 0
    /** A different dancer for every new track (same one while the same track plays). */
    fun forTitle(t: String): Int {
        if (t != lastTitle) { idx = (idx + 1 + (kotlin.math.abs(t.hashCode()) % 4)) % 5; lastTitle = t }
        return idx
    }
}

private val DANCER_COLOURS = longArrayOf(0xFFFFFFFF, 0xFFFF9A3C, 0xFF7CF4FF, 0xFFFF8FD0, 0xFFFFD54F)

/** Extra poses for dancers 1..4 (dancer 0 uses dancePose). o[14] = whole-body rotation. */
private fun dancePose2(style: Int, move: Int, p: Float): FloatArray {
    val s = kotlin.math.sin(p); val c = kotlin.math.cos(p)
    val k = 0.5f + 0.5f * s
    val o = FloatArray(16); o[11] = 1f
    val PI = 3.14159f
    when (style) {
        1 -> when (move) { // breaker
            0 -> { o[0] = 0.2f * kotlin.math.abs(s); o[1] = 0.1f * s; o[2] = 0.9f + 0.5f * s; o[3] = 1.2f; o[4] = -(0.9f - 0.5f * s); o[5] = -1.2f
                o[6] = 0.35f; o[7] = 0.7f * k; o[8] = -0.35f; o[9] = -0.7f * (1f - k); o[13] = 1.5f * s }
            1 -> { o[14] = PI + 0.22f * kotlin.math.sin(p * 0.5f); o[2] = PI - 0.1f; o[4] = -(PI - 0.1f)
                o[6] = 0.25f + 0.8f * k; o[7] = 0.1f; o[8] = -(0.25f + 0.8f * (1f - k)); o[9] = -0.1f; o[1] = 0.05f * s }
            2 -> { o[14] = p * 0.9f; o[2] = 1.4f; o[4] = -1.4f; o[3] = 0.1f; o[5] = -0.1f
                o[6] = 0.7f; o[8] = -0.7f; o[7] = 0.1f; o[9] = -0.1f }
            else -> { o[12] = 1.1f * k * k; o[2] = 2.7f; o[4] = -2.7f; o[3] = 0.3f; o[5] = -0.3f
                o[6] = 0.4f + 0.8f * k; o[7] = 1.1f * k; o[8] = -(0.4f + 0.8f * k); o[9] = -1.1f * k }
        }
        2 -> { // robot: snapping, square angles
            val st = kotlin.math.floor(p / 1.5708f).toInt() and 3
            val sn = if (st == 0 || st == 3) 1f else -1f
            when (move) {
                0 -> { val aa = floatArrayOf(1.57f, 3.14f, 1.57f, 0.2f)[st]; val bb = floatArrayOf(0.2f, 1.57f, -1.57f, 1.57f)[st]
                    o[2] = aa; o[3] = bb; o[4] = -floatArrayOf(0.2f, 1.57f, 3.14f, 1.57f)[st]; o[5] = -floatArrayOf(1.57f, 0.2f, 1.57f, -1.57f)[st]; o[10] = 0.3f * sn }
                1 -> { o[6] = if (st and 1 == 0) 0.4f else 0.05f; o[8] = if (st and 1 == 0) -0.05f else -0.4f
                    o[2] = if (st and 1 == 0) -0.5f else 0.5f; o[3] = 1.57f; o[4] = if (st and 1 == 0) -0.5f else 0.5f; o[5] = -1.57f; o[0] = if (st and 1 == 0) 0.3f else 0f }
                2 -> { o[2] = if (st < 2) 3.0f else 0.4f; o[4] = if (st < 2) -0.4f else -3.0f; o[3] = 0f; o[5] = 0f; o[10] = if (st < 2) 0.3f else -0.3f }
                else -> { o[2] = 1.57f; o[3] = if (st and 1 == 0) 1.57f else 0f; o[4] = -1.57f; o[5] = if (st and 1 == 0) 0f else -1.57f
                    o[10] = sn * 0.35f; o[1] = sn * 0.1f; o[6] = 0.15f; o[8] = -0.15f }
            }
        }
        3 -> { // ballerina: on tiptoe
            o[12] = 0.3f
            when (move) {
                0 -> { o[2] = 2.2f + 0.5f * s; o[3] = 0.4f; o[4] = -(2.2f - 0.5f * s); o[5] = -0.4f; o[1] = 0.1f * s; o[13] = 2f * s; o[10] = 0.25f * s; o[6] = 0.05f; o[8] = -0.05f }
                1 -> { o[11] = kotlin.math.cos(p * 0.8f); o[2] = 2.7f; o[3] = 1.0f; o[4] = -2.7f; o[5] = -1.0f
                    o[6] = 0f; o[8] = -0.45f; o[9] = 1.7f; o[12] = 0.5f }
                2 -> { o[1] = 0.45f + 0.05f * s; o[2] = 1.5f; o[3] = 0.1f; o[4] = -2.7f; o[5] = -0.1f
                    o[6] = 0f; o[8] = -1.5f - 0.1f * s; o[9] = 0f; o[10] = -0.3f }
                else -> { o[12] = 1.3f * kotlin.math.abs(s); o[2] = 2.8f; o[4] = -2.8f; o[3] = 0.2f; o[5] = -0.2f
                    o[6] = 1.3f; o[7] = 0f; o[8] = -1.3f; o[9] = 0f }
            }
        }
        else -> { // disco
            when (move) {
                0 -> { val g = 0.5f + 0.5f * kotlin.math.sin(p * 0.5f)
                    o[2] = 0.5f + 2.2f * g; o[3] = 0.15f; o[4] = -(0.5f + 2.2f * (1f - g)); o[5] = -0.15f
                    o[1] = 0.2f * (g - 0.5f); o[6] = 0.3f * g; o[7] = 0.5f * g; o[8] = -0.3f * (1f - g); o[9] = -0.5f * (1f - g); o[13] = 2f * (g - 0.5f) }
                1 -> { o[13] = 2.2f * kotlin.math.sin(p * 2f); o[0] = 0.18f * kotlin.math.abs(kotlin.math.sin(p * 2f)); o[2] = 0.6f; o[3] = 2.2f; o[4] = -0.6f; o[5] = -2.2f
                    o[6] = 0.25f; o[7] = 0.9f; o[8] = -0.25f; o[9] = -0.9f; o[1] = 0.12f * kotlin.math.sin(p * 2f) }
                2 -> { val g = smooth(0.5f + 0.6f * kotlin.math.sin(p * 0.5f))
                    o[2] = 2.4f * g + 0.4f; o[3] = 0.1f; o[4] = -0.7f; o[5] = -2.0f; o[6] = 0.5f * g; o[7] = 0.2f; o[8] = -0.1f; o[9] = -0.2f
                    o[1] = 0.25f * (g - 0.5f); o[13] = 3f * (g - 0.5f) }
                else -> { o[2] = p; o[3] = 0.3f; o[4] = -(p + 3.14f); o[5] = -0.3f; o[6] = 0.15f; o[7] = 0.5f * k; o[8] = -0.15f; o[9] = -0.5f * (1f - k); o[0] = 0.15f * kotlin.math.abs(s); o[1] = 0.05f * s }
            }
        }
    }
    return o
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDancer(
    f: Float, w: Float, h: Float, phase: Float, dance: Float, playing: Boolean, beat: Float, acc: Color, dim: Color, style: Int
) {
    val u = 1.15.dp.toPx()
    val gy = h - 5.dp.toPx()
    val cap = androidx.compose.ui.graphics.StrokeCap.Round
    drawLine(dim, Offset(0f, gy), Offset(w, gy), 2.dp.toPx(), cap = cap)
    drawLine(acc, Offset(0f, gy), Offset(w * f, gy), 2.dp.toPx(), cap = cap)

    val cyc = dance / 6.28318f
    val segC = 4f                                  // a move lasts four cycles (eight beats)
    val si = (cyc / segC).toInt(); val fr = (cyc / segC) - si
    val blend = smooth((fr - 0.9f) / 0.1f)
    val nm = if (style == 0) 8 else 4
    val order = if (style == 0) intArrayOf(0, 1, 4, 2, 6, 3, 0, 5) else intArrayOf(0, 1, 2, 3)
    val m0 = order[si % order.size]; val m1 = order[(si + 1) % order.size]
    val p = dance
    fun pose(m: Int): FloatArray = if (style == 0) dancePose(m, p).copyOf(16) else dancePose2(style, m, p)
    val a = pose(m0); val b = pose(m1)
    val q = FloatArray(16) { a[it] + (b[it] - a[it]) * blend }
    val beatPop = if (playing) beat.coerceIn(0f, 1f) * 0.12f else 0f
    val col = Color(DANCER_COLOURS[style])

    fun dir(ang: Float) = Offset(kotlin.math.sin(ang), kotlin.math.cos(ang))
    val thigh = 6.2f * u; val shin = 6.2f * u; val torso = 9f * u; val upper = 5f * u; val fore = 5f * u
    val sw = q[11]
    val up = Offset(kotlin.math.sin(q[1]), -kotlin.math.cos(q[1]))
    val shoulder = up * torso
    // joints in local space, hip at the origin
    val hipR = Offset(1.6f * u * sw, 0f); val hipL = Offset(-1.6f * u * sw, 0f)
    val kneeR = hipR + dir(q[6]) * thigh; val footR = kneeR + dir(q[6] + q[7]) * shin
    val kneeL = hipL + dir(q[8]) * thigh; val footL = kneeL + dir(q[8] + q[9]) * shin
    val shR = shoulder + Offset(2.4f * u * sw, 0.6f * u); val shL = shoulder + Offset(-2.4f * u * sw, 0.6f * u)
    val elR = shR + dir(q[2]) * upper; val haR = elR + dir(q[2] + q[3]) * fore
    val elL = shL + dir(q[4]) * upper; val haL = elL + dir(q[4] + q[5]) * fore
    val headC = shoulder + up * (3.6f * u) + Offset(q[10] * 1.5f * u, 0f)
    val all = arrayOf(Offset.Zero, hipR, hipL, kneeR, kneeL, footR, footL, shoulder, shR, shL, elR, elL, haR, haL, headC)
    // rotate the whole body about the middle of the torso (cartwheels, handstands, spins)
    val piv = shoulder * 0.5f
    val cs = kotlin.math.cos(q[14]); val sn = kotlin.math.sin(q[14])
    fun rot(o: Offset): Offset { val x = o.x - piv.x; val y = o.y - piv.y; return Offset(cs * x - sn * y + piv.x, sn * x + cs * y + piv.y) }
    val r = all.map { rot(it) }
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
        else -> drawCircle(col, headR, P(14))
    }
}

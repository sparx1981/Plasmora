package app.plasmora.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.ComponentName
import android.content.Intent
import app.plasmora.PlasmoraWallpaper
import app.plasmora.media.NowPlaying
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.plasmora.Settings
import app.plasmora.SettingsStore
import app.plasmora.gl.Palettes
import app.plasmora.gl.PresetCategories
import app.plasmora.gl.Presets
import app.plasmora.gl.VisInfo

private val LogoCyan = Color(0xFF00E5FF)
private val LogoViolet = Color(0xFFB14CFF)
private val LogoPink = Color(0xFFFF4F9A)
private val LogoGradientH = Brush.horizontalGradient(listOf(LogoCyan, LogoViolet, LogoPink))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(settings: Settings, info: VisInfo, onSource: (app.plasmora.SourceMode) -> Unit, onDismiss: () -> Unit, onSelectPreset: (Int) -> Unit) {
    var allowHide by remember { mutableStateOf(false) }
    // Gestures and scrim taps can never hide the sheet; only the handle pull (below) flips allowHide.
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != androidx.compose.material3.SheetValue.Hidden || allowHide })
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val closeNow: () -> Unit = { allowHide = true; scope.launch { state.hide(); onDismiss() } }
    androidx.activity.compose.BackHandler(onBack = closeNow)
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Source", "Visuals", "Music", "Colour", "Effects", "Quality", "Audio", "App")
    val maxH = (LocalConfiguration.current.screenHeightDp * 0.62f).coerceIn(200f, 520f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = state, containerColor = Color.Transparent,
        dragHandle = {
            // Tall touch strip: the handle is the only place a downward pull closes the sheet.
            val closePx = with(androidx.compose.ui.platform.LocalDensity.current) { 36.dp.toPx() }
            Box(Modifier.fillMaxWidth().height(40.dp).pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy; if (total > closePx) { total = Float.MIN_VALUE; closeNow() } }
                )
            }, contentAlignment = Alignment.Center) {
                Box(Modifier.width(44.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(LogoGradientH))
            }
        }
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding()
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B0E38), Color(0xFF0B0620))))
                    drawRect(Brush.radialGradient(listOf(LogoCyan.copy(alpha = 0.16f), Color.Transparent), Offset(0f, 0f), size.width * 0.8f))
                    drawRect(Brush.radialGradient(listOf(LogoPink.copy(alpha = 0.13f), Color.Transparent), Offset(size.width, size.height * 0.15f), size.width * 0.7f))
                }
        ) {
            ScrollableTabRow(
                selectedTabIndex = tab, containerColor = Color.Transparent, edgePadding = 8.dp, divider = {},
                indicator = { pos ->
                    Box(with(androidx.compose.material3.TabRowDefaults) { Modifier.tabIndicatorOffset(pos[tab]) }.padding(horizontal = 14.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(LogoGradientH))
                }
            ) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        selectedContentColor = Color.White, unselectedContentColor = Color(0xFF9D8CC4),
                        text = { Text(t, fontWeight = if (tab == i) FontWeight.SemiBold else FontWeight.Normal) })
                }
            }
            // Scrolling the settings must never drag the sheet down: swallow whatever the list can't use.
            val keepSheet = remember {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource) = available
                    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity) = available
                }
            }
            Column(
                Modifier.fillMaxWidth().height(maxH).nestedScroll(keepSheet).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (tab) {
                    0 -> SourceTab(settings, onSource)
                    1 -> VisualsTab(settings, info, onSelectPreset)
                    2 -> MusicTab(settings)
                    3 -> ColourTab(settings)
                    4 -> EffectsTab(settings)
                    5 -> QualityTab(settings, info)
                    6 -> AudioTab(settings)
                    else -> AppTab(settings)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ tabs

/** Shown when the app opens without any audio linked: pick where the music comes from. */
@Composable
fun SourcePrompt(current: app.plasmora.SourceMode, onSource: (app.plasmora.SourceMode) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .drawBehind {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B0E38), Color(0xFF0B0620))))
                    drawRect(Brush.radialGradient(listOf(LogoCyan.copy(alpha = 0.16f), Color.Transparent), Offset(0f, 0f), size.width * 0.8f))
                    drawRect(Brush.radialGradient(listOf(LogoPink.copy(alpha = 0.13f), Color.Transparent), Offset(size.width, size.height * 0.2f), size.width * 0.7f))
                }
                .border(1.dp, Brush.linearGradient(listOf(LogoCyan.copy(alpha = 0.5f), LogoViolet.copy(alpha = 0.4f), LogoPink.copy(alpha = 0.4f))), RoundedCornerShape(28.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Choose a sound source", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                style = androidx.compose.ui.text.TextStyle(brush = LogoGradientH))
            Text("Nothing is linked yet. Pick where the music comes from.", fontSize = 13.sp, color = Color(0xFFCFC2EE))
            Spacer(Modifier.height(4.dp))
            app.plasmora.SourceMode.values().forEach { m ->
                val sel = current == m
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(if (sel) LogoViolet.copy(alpha = 0.35f) else Color(0x1AFFFFFF))
                        .border(1.dp, if (sel) LogoViolet else Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                        .clickable { onSource(m) }.padding(horizontal = 16.dp, vertical = 14.dp)
                ) { Text(m.label, color = Color.White, fontSize = 15.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Not now", color = Color(0xFFCFC2EE)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceTab(s: Settings, onSource: (app.plasmora.SourceMode) -> Unit) {
    Heading("Sound source")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        app.plasmora.SourceMode.values().forEach { m ->
            FilterChip(selected = s.source == m, onClick = { onSource(m) }, label = { Text(m.label, fontSize = 13.sp) })
        }
    }
    val np by NowPlaying.state.collectAsState()
    if (!np.hasAccess) NotificationAccessPrompt()
    SwitchRow("Always show player controls", s.alwaysShowControls,
        if (np.hasAccess) null else "Needs notification access to show anything", enabled = np.hasAccess) { v ->
        SettingsStore.update { it.copy(alwaysShowControls = v) }
    }
}

@Composable
private fun NotificationAccessPrompt() {
    val ctx = LocalContext.current
    Hint("Allow notification access so Plasmora can show the song and control playback.")
    Button(onClick = {
        try { ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Throwable) { }
    }) { Text("Allow notification access") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VisualsTab(s: Settings, info: VisInfo, onSelectPreset: (Int) -> Unit) {
    Heading("Changing effects")
    SwitchRow("Change effect automatically", s.autoChange) { v -> SettingsStore.update { it.copy(autoChange = v) } }
    if (s.autoChange) SwitchRow("Auto-director", s.director, "Changes effects on drops and section changes") { v -> SettingsStore.update { it.copy(director = v) } }
    if (s.autoChange) SwitchRow("Smart changes", s.smartChange) { v -> SettingsStore.update { it.copy(smartChange = v) } }
    if (s.autoChange) {
        SliderRow("Change every", s.autoChangeSec.toFloat(), 10f..180f, { v -> SettingsStore.update { it.copy(autoChangeSec = v.toInt()) } }) { "${it.toInt()} s" }
        SwitchRow("Shuffle order", s.shuffle) { v -> SettingsStore.update { it.copy(shuffle = v) } }
    }
    SliderRow("Transition length", s.transitionSec, 0.5f..10f, { v -> SettingsStore.update { it.copy(transitionSec = v) } }) { "%.1f s".format(it) }
    Heading("Framing")
    SliderRow("Zoom (lower shows more of the visual)", s.visZoom, 0.5f..1.5f, { v -> SettingsStore.update { it.copy(visZoom = v) } }) { "%.0f%%".format(it * 100) }
    // Prominent switch first: also limits swipe, double-tap random and auto-change
    androidx.compose.material3.Card(
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            SwitchRow("Use only liked", s.favOnly,
                if (s.favourites.isEmpty()) "Use the thumbs up on the main screen or on a preview to like effects first"
                else "Swiping, double-tap random and auto-change stay within your ${s.favourites.size} liked effect(s)") { v ->
                SettingsStore.update { it.copy(favOnly = v) }
            }
            SwitchRow("Don't play disliked", s.skipDisliked,
                if (s.dislikes.isEmpty()) "Use the thumbs down on the main screen or on a preview to dislike effects"
                else "Skips your ${s.dislikes.size} disliked effect(s) when swiping, random and auto-change") { v ->
                SettingsStore.update { it.copy(skipDisliked = v) }
            }
        }
    }
    val ctx = LocalContext.current
    val groups = remember { Presets.all.withIndex().groupBy { PresetCategories.of(it.value) } }
    val favs = s.favourites.filter { it in Presets.all.indices }.sorted()
    val gold = Color(0xFF3DA9FF)
    // view switch: list of names, or preview pictures
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilterChip(selected = !s.visualsGrid, onClick = { SettingsStore.update { it.copy(visualsGrid = false) } }, label = { Text("List", fontSize = 13.sp) })
        FilterChip(selected = s.visualsGrid, onClick = { SettingsStore.update { it.copy(visualsGrid = true) } }, label = { Text("Previews", fontSize = 13.sp) })
    }
    run {
        val ver by ThumbStore.version.collectAsState()
        val prog by PreviewJob.progress.collectAsState()
        val have = remember(ver) { ThumbStore.count(ctx) }
        val total = Presets.all.size
        if (prog.running) {
            Hint("Generating previews: ${prog.done} of ${prog.total}. Keep the app open.")
            androidx.compose.material3.LinearProgressIndicator(progress = { if (prog.total > 0) prog.done / prog.total.toFloat() else 0f }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { PreviewJob.cancel() }) { Text("Stop") }
        } else {
            if (have < total) {
                Hint(if (have == 0) "Previews have not been generated yet. Tap below to create them (takes a few minutes, keep the app open)." else "$have of $total previews ready")
                Button(onClick = { PreviewJob.start(ctx, true) }) { Text(if (have == 0) "Generate previews" else "Generate missing previews") }
            }
            if (have > 0) {
                Hint("Replaces every preview. Keep the app open.")
                OutlinedButton(onClick = { ThumbStore.clear(ctx); PreviewJob.start(ctx, false) }) { Text("Regenerate all previews") }
            }
        }
    }
    @Composable
    fun group(title: String, idxs: List<Int>) {
        Heading("$title  (${idxs.size})")
        if (s.visualsGrid) {
            val cols = if (LocalConfiguration.current.screenWidthDp >= 600) 3 else 2
            val w = ((LocalConfiguration.current.screenWidthDp - 32 - 8 * (cols - 1)) / cols).dp
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in idxs) PresetCard(i, i in s.favourites, i in s.dislikes, info.presetIndex == i, w,
                    onLike = { SettingsStore.update { it.copy(favourites = if (i in it.favourites) it.favourites - i else it.favourites + i, dislikes = it.dislikes - i) } },
                    onDislike = { SettingsStore.update { it.copy(dislikes = if (i in it.dislikes) it.dislikes - i else it.dislikes + i, favourites = it.favourites - i) } }
                ) { onSelectPreset(i) }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in idxs) {
                    val fav = i in s.favourites
                    FilterChip(
                        selected = info.presetIndex == i, onClick = { onSelectPreset(i) },
                        label = { Text((if (fav) "+ " else if (i in s.dislikes) "\u2212 " else "") + Presets.all[i].name, fontSize = 13.sp, color = if (fav) (if (info.presetIndex == i) Color.Black else gold) else Color.Unspecified) },
                        colors = if (fav) FilterChipDefaults.filterChipColors(
                            containerColor = gold.copy(alpha = 0.14f), selectedContainerColor = gold) else FilterChipDefaults.filterChipColors(),
                        border = if (fav) BorderStroke(1.5.dp, gold) else null
                    )
                }
            }
        }
    }
    if (favs.isNotEmpty()) group(PresetCategories.FAV, favs)
    PresetCategories.order.forEach { cat ->
        val items = groups[cat] ?: return@forEach
        group(cat, items.map { it.index })
    }
}


@Composable
private fun PresetCard(idx: Int, fav: Boolean, dis: Boolean, selected: Boolean, width: androidx.compose.ui.unit.Dp, onLike: () -> Unit, onDislike: () -> Unit, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val ver by ThumbStore.version.collectAsState()
    val bmp = remember(ver, idx) { ThumbStore.get(ctx, idx) }
    val gold = Color(0xFF3DA9FF)
    val shape = RoundedCornerShape(12.dp)
    val border = when {
        fav -> BorderStroke(2.5.dp, gold)
        selected -> BorderStroke(2.dp, Color.White)
        else -> BorderStroke(0.5.dp, Color(0x33FFFFFF))
    }
    Box(Modifier.width(width).height(width * 2 / 3).clip(shape).background(Color(0xFF12121A)).border(border, shape).clickable(onClick = onClick)) {
        if (bmp != null) {
            androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            Text("No preview yet", fontSize = 11.sp, color = Color(0x88FFFFFF), modifier = Modifier.align(Alignment.Center))
        }
        Row(
            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Presets.all[idx].name, fontSize = 12.sp, color = if (fav) gold else Color.White, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).clickable(onClick = onLike), contentAlignment = Alignment.Center) {
                    Icon(ThumbIcons.Up, "Like", tint = if (fav) gold else Color(0xCCFFFFFF), modifier = Modifier.size(17.dp))
                }
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).clickable(onClick = onDislike), contentAlignment = Alignment.Center) {
                    Icon(ThumbIcons.Down, "Dislike", tint = if (dis) Color(0xFFFF6B6B) else Color(0xCCFFFFFF), modifier = Modifier.size(17.dp))
                }
            }
        }
        if (selected) Text("\u25CF playing", fontSize = 10.sp, color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourTab(s: Settings) {
    SwitchRow("Colours from album art", s.artPalette) { v -> SettingsStore.update { it.copy(artPalette = v) } }
    Heading("Palette")
    if (s.artPalette) Text("Overridden by colours from album art", fontSize = 11.sp, color = Color(0x99FFFFFF))
    val ghost = s.artPalette
    Column(Modifier.alpha(if (ghost) 0.35f else 1f)) {
    SwitchRow("Pick a new palette with each effect", s.autoPalette) { v -> if (!ghost) SettingsStore.update { it.copy(autoPalette = v) } }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Palettes.all.forEachIndexed { i, p ->
            val stops = (0..8).map { k -> val c = Palettes.sample(p, k / 8f); Color(c[0], c[1], c[2]) }
            val sel = !s.autoPalette && s.palette == i
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.width(76.dp).height(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(Brush.horizontalGradient(stops))
                        .border(BorderStroke(if (sel) 2.dp else 0.dp, Color.White), RoundedCornerShape(10.dp))
                        .clickable(enabled = !ghost) { SettingsStore.update { it.copy(palette = i, autoPalette = false) } }
                )
                Text(p.name, fontSize = 10.sp, color = Color(0xCCFFFFFF))
            }
        }
    }
    }
    Heading("Colour")
    SliderRow("Hue shift", s.hue, 0f..1f, { v -> SettingsStore.update { it.copy(hue = v) } }) { "%.0f°".format(it * 360) }
    SliderRow("Colour drift speed", s.paletteSpeed, 0f..3f, { v -> SettingsStore.update { it.copy(paletteSpeed = v) } })
    SliderRow("Saturation", s.saturation, 0f..2f, { v -> SettingsStore.update { it.copy(saturation = v) } })
    SliderRow("Brightness", s.exposure, 0.4f..2.5f, { v -> SettingsStore.update { it.copy(exposure = v) } })
    SliderRow("Palette tint (gradient map)", s.gradientMap, 0f..1f, { v -> SettingsStore.update { it.copy(gradientMap = v) } })
}

@Composable
private fun EffectsTab(s: Settings) {
    Heading("Cinematic effects")
    SwitchRow("Enable cinematic effects", s.fxEnabled) { v -> SettingsStore.update { it.copy(fxEnabled = v) } }
    if (!s.fxEnabled) return
    Heading("Motion")
    SwitchRow("Temporal anti-aliasing", s.taa) { v -> SettingsStore.update { it.copy(taa = v) } }
    SliderRow("Motion blur", s.motionBlur, 0f..1.5f, { v -> SettingsStore.update { it.copy(motionBlur = v) } }) { "%.0f%%".format(it * 100) }
    SliderRow("Gyro parallax (tilt the phone)", s.parallax, 0f..1f, { v -> SettingsStore.update { it.copy(parallax = v) } }) { if (it < 0.01f) "Off" else "%.0f%%".format(it * 100) }
    SliderRow("Camera shake on beats", s.shake, 0f..1f, { v -> SettingsStore.update { it.copy(shake = v) } }) { "%.0f%%".format(it * 100) }
    Heading("Light")
    SliderRow("Anamorphic light streaks", s.streaks, 0f..1f, { v -> SettingsStore.update { it.copy(streaks = v) } }) { "%.0f%%".format(it * 100) }
    SliderRow("Lens flare on beats", s.flare, 0f..1f, { v -> SettingsStore.update { it.copy(flare = v) } }) { "%.0f%%".format(it * 100) }
    SliderRow("Halation (warm glow)", s.halation, 0f..1f, { v -> SettingsStore.update { it.copy(halation = v) } }) { "%.0f%%".format(it * 100) }
    SliderRow("God rays (synthwave sun)", s.godRays, 0f..1f, { v -> SettingsStore.update { it.copy(godRays = v) } }) { "%.0f%%".format(it * 100) }
    Heading("Depth and surfaces")
    SliderRow("Depth of field (terrain)", s.dof, 0f..1.5f, { v -> SettingsStore.update { it.copy(dof = v) } }) { "%.0f%%".format(it * 100) }
    SliderRow("Floor reflections (valley, sphere)", s.reflections, 0f..1f, { v -> SettingsStore.update { it.copy(reflections = v) } }) { "%.0f%%".format(it * 100) }
    Heading("Finish")
    SliderRow("Colour grading (per palette)", s.grading, 0f..1f, { v -> SettingsStore.update { it.copy(grading = v) } }) { "%.0f%%".format(it * 100) }
    SliderRow("Sharpen", s.sharpen, 0f..1f, { v -> SettingsStore.update { it.copy(sharpen = v) } }) { "%.0f%%".format(it * 100) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityTab(s: Settings, info: VisInfo) {
    Heading("Resolution")
    Hint("Rendering at ${info.simW} × ${info.simH}${if (info.hdr) " · 16-bit HDR" else " · 8-bit"}${if (info.gpu.isNotEmpty()) " · ${info.gpu}" else ""}")
    ChipRow(listOf("×0.5", "×0.75", "Native", "×1.5 sharper", "×2 ultra"), listOf(0.5f, 0.75f, 1f, 1.5f, 2f).indexOf(s.renderScale).let { if (it < 0) 2 else it }) { i ->
        SettingsStore.update { it.copy(renderScale = listOf(0.5f, 0.75f, 1f, 1.5f, 2f)[i]) }
    }
    Heading("Frame rate")
    val fpsOpts = listOf(30, 60, 90, 120, 0)
    ChipRow(listOf("30", "60", "90", "120", "Max"), fpsOpts.indexOf(s.maxFps).let { if (it < 0) 1 else it }) { i ->
        SettingsStore.update { it.copy(maxFps = fpsOpts[i]) }
    }
    Heading("Battery")
    SwitchRow("Battery saver adapts quality", s.autoBattery, "30 fps and lighter glow") { v -> SettingsStore.update { it.copy(autoBattery = v) } }
    Heading("Image quality")
    SwitchRow("16-bit HDR buffers", s.hdr, "Removes colour banding and gives glowing highlights") { v -> SettingsStore.update { it.copy(hdr = v) } }
    Text("Glow (bloom) quality", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    ChipRow(listOf("Off", "Medium", "High"), s.bloomQuality.coerceIn(0, 2)) { i -> SettingsStore.update { it.copy(bloomQuality = i) } }
    SliderRow("Glow strength", s.bloomAmount, 0f..2f, { v -> SettingsStore.update { it.copy(bloomAmount = v) } })
    SliderRow("Colour fringing", s.chroma, 0f..1.5f, { v -> SettingsStore.update { it.copy(chroma = v) } })
    SliderRow("Film grain", s.grain, 0f..1f, { v -> SettingsStore.update { it.copy(grain = v) } })
    SliderRow("Vignette", s.vignette, 0f..1f, { v -> SettingsStore.update { it.copy(vignette = v) } })
}

@Composable
private fun AudioTab(s: Settings) {
    Heading("Reaction")
    SliderRow("Overall sensitivity", s.gain, 0.2f..3f, { v -> SettingsStore.update { it.copy(gain = v) } })
    SliderRow("Bass", s.bassGain, 0f..2.5f, { v -> SettingsStore.update { it.copy(bassGain = v) } })
    SliderRow("Mids", s.midGain, 0f..2.5f, { v -> SettingsStore.update { it.copy(midGain = v) } })
    SliderRow("Treble", s.trebGain, 0f..2.5f, { v -> SettingsStore.update { it.copy(trebGain = v) } })
    SliderRow("Smoothing", s.smoothing, 0f..1f, { v -> SettingsStore.update { it.copy(smoothing = v) } })
    SliderRow("Beat detection", s.beatSens, 0f..1f, { v -> SettingsStore.update { it.copy(beatSens = v) } })
    SwitchRow("Auto volume levelling", s.agc, "Keeps quiet and loud music reacting the same") { v -> SettingsStore.update { it.copy(agc = v) } }
    SliderRow("Motion speed", s.speed, 0.25f..2.5f, { v -> SettingsStore.update { it.copy(speed = v) } }) { "×%.2f".format(it) }
    SliderRow("Motion intensity", s.intensity, 0.3f..2f, { v -> SettingsStore.update { it.copy(intensity = v) } }) { "×%.2f".format(it) }
    Heading("Local files")
    SliderRow("Audio/visual sync", s.syncMs.toFloat(), 0f..500f, { v -> SettingsStore.update { it.copy(syncMs = v.toInt()) } }) { "${it.toInt()} ms" }
}

@Composable
private fun AppTab(s: Settings) {
    Heading("App")
    SwitchRow("Float over other apps when you leave", s.autoPip) { v -> SettingsStore.update { it.copy(autoPip = v) } }
    SwitchRow("Show frame rate", s.showFps) { v -> SettingsStore.update { it.copy(showFps = v) } }
    Heading("Live wallpaper")
    val ctx = LocalContext.current
    Button(onClick = {
        try {
            ctx.startActivity(Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, PlasmoraWallpaper::class.java)))
        } catch (_: Throwable) {
            try { ctx.startActivity(Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) } catch (_: Throwable) { }
        }
    }) { Text("Set as wallpaper") }
    Heading("Over other apps")
    SwitchRow("Edge glow over other apps", s.edgeOverlay, "Lights the screen edges to the music") { v ->
        if (v && !app.plasmora.media.EdgeOverlay.canDraw(ctx)) {
            try { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName))) } catch (_: Throwable) { }
        }
        SettingsStore.update { it.copy(edgeOverlay = v) }
    }
    if (s.edgeOverlay) {
        if (!app.plasmora.media.EdgeOverlay.canDraw(ctx)) Hint("Allow \"Display over other apps\" for Plasmora, then come back.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Glow", "Bars", "Both").forEachIndexed { i, nm ->
                val idx = if (i == 0) 0 else if (i == 1) 1 else 2
                FilterChip(selected = s.edgeStyle == idx, onClick = { SettingsStore.update { it.copy(edgeStyle = idx) } }, label = { Text(nm, fontSize = 13.sp) })
            }
        }
        SliderRow("Edge size", s.edgeSize, 0f..1f, { v -> SettingsStore.update { it.copy(edgeSize = v) } }) { "%.0f%%".format(it * 100) }
    }
    Heading("Always-on & screensaver")
    SwitchRow("Dim always-on mode", s.dimMode, "Low brightness and 30 fps for a desk or charger") { v -> SettingsStore.update { it.copy(dimMode = v) } }
    if (s.dimMode) SliderRow("Dim level", s.dimLevel, 0.1f..1f, { v -> SettingsStore.update { it.copy(dimLevel = v) } }) { "%.0f%%".format(it * 100) }
    Button(onClick = {
        try { ctx.startActivity(Intent(android.provider.Settings.ACTION_DREAM_SETTINGS)) }
        catch (_: Throwable) { try { ctx.startActivity(Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)) } catch (_: Throwable) { } }
    }) { Text("Open screen saver settings") }
    Heading("About")
    Button(onClick = {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://sites.google.com/view/plasmora-privacy-policy/home")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) { }
    }) { Text("Privacy policy") }
    OutlinedButton(onClick = { SettingsStore.update { it.copy(onboarded = false) } }) { Text("Show intro again") }
    Heading("Reset")
    var askReset by remember { androidx.compose.runtime.mutableStateOf(false) }
    OutlinedButton(onClick = { askReset = true }) { Text("Reset all settings to default") }
    if (askReset) {
        AlertDialog(
            onDismissRequest = { askReset = false },
            title = { Text("Reset all settings?") },
            text = { Text("Every setting, including colours, effects, sound and cinematic options, goes back to its default, and pinned songs are cleared. Favourite effects and generated previews are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    SettingsStore.resetToDefaults()
                    app.plasmora.media.SongMemory.clear()
                    askReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { askReset = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MusicTab(s: Settings) {
    val ctx = LocalContext.current
    val np by NowPlaying.state.collectAsState()
    if (!np.hasAccess) NotificationAccessPrompt()
    Heading("Player controls")
    Text("Progress bar", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(listOf("Off", "Straight", "Wavy", "Dancer", "Equaliser", "Neon", "Orb"), s.progressStyle) { v -> SettingsStore.update { it.copy(progressStyle = v) } }
    Text("Album art in the player", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    ChipRow(listOf("Off", "Small", "Medium", "Large"), s.artSize) { v -> SettingsStore.update { it.copy(artSize = v) } }
    SliderRow("Player background opacity", s.playerOpacity, 0f..1f, { v -> SettingsStore.update { it.copy(playerOpacity = v) } }) { if (it < 0.02f) "Clear" else "%.0f%%".format(it * 100) }
    SwitchRow("Tap album art to open the music app", s.artOpensApp) { v -> SettingsStore.update { it.copy(artOpensApp = v) } }
    SwitchRow("Show song title when it changes", s.trackToast) { v -> SettingsStore.update { it.copy(trackToast = v) } }
    Heading("Lyrics")
    ChipRow(listOf("Off", "Key lines", "Full lyrics"), s.lyricsMode) { v -> SettingsStore.update { it.copy(lyricsMode = v) } }
    if (s.lyricsMode != 0) {
        Text("Style", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(listOf("Plain", "Glow", "Neon", "Pulse", "Palette", "In the visual"), s.lyricsStyle) { v -> SettingsStore.update { it.copy(lyricsStyle = v) } }
        SliderRow("Readability shade behind text", s.lyricsBack, 0f..1f, { v -> SettingsStore.update { it.copy(lyricsBack = v) } }) { if (it < 0.02f) "Off" else "%.0f%%".format(it * 100) }
        Text("Position", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ChipRow(listOf("Top", "Centre", "Bottom"), s.lyricsPos) { v -> SettingsStore.update { it.copy(lyricsPos = v) } }
    }
    if (s.lyricsMode != 0) Hint("Sends the song title and artist to lrclib.net to find lyrics.")
    Heading("Album art")
    SwitchRow("Album art in the background", s.artBg) { v -> SettingsStore.update { it.copy(artBg = v) } }
    if (s.artBg) {
        SliderRow("Art strength", s.artAmount, 0f..1.5f, { v -> SettingsStore.update { it.copy(artAmount = v) } })
        SliderRow("Art blur", s.artBlur, 0f..1f, { v -> SettingsStore.update { it.copy(artBlur = v) } })
    }
    Heading("Per-song memory")
    SwitchRow("Remember effect per song", s.songMemory, "Pinned songs keep their effect") { v -> SettingsStore.update { it.copy(songMemory = v) } }
    var pins by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(app.plasmora.media.SongMemory.count()) }
    if (pins > 0) {
        Hint("$pins song(s) pinned")
        androidx.compose.material3.TextButton(onClick = { app.plasmora.media.SongMemory.clear(); pins = 0 }) { Text("Clear all pinned songs") }
    }
    Heading("Background")
    SwitchRow("Keep listening after closing the app", s.keepListening, "Keeps capturing until stopped from its notification") { v -> SettingsStore.update { it.copy(keepListening = v) } }
    SwitchRow("Offer to start when music plays", s.offerStart, "A notification offers to start") { v -> SettingsStore.update { it.copy(offerStart = v) } }
}

// ------------------------------------------------------------------ building blocks

@Composable
private fun Heading(t: String) {
    Column(Modifier.padding(top = 10.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(LogoCyan.copy(alpha = 0.45f), LogoViolet.copy(alpha = 0.3f), Color.Transparent))))
        Text(t.uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.6.sp,
            style = androidx.compose.ui.text.TextStyle(brush = LogoGradientH), modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun Hint(t: String) {
    Text(t, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SliderRow(
    label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit,
    fmt: (Float) -> String = { "%.2f".format(it) }
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(fmt(value), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = LogoCyan, activeTrackColor = LogoViolet, inactiveTrackColor = Color(0xFF3A2B5C),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
            ),
            track = { st ->
                val span = (range.endInclusive - range.start).coerceAtLeast(1e-6f)
                val frac = ((st.value - range.start) / span).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A2B5C))) {
                    Box(Modifier.fillMaxWidth(frac).height(3.dp).background(LogoGradientH))
                }
            },
            thumb = { Box(Modifier.size(16.dp).clip(androidx.compose.foundation.shape.CircleShape).background(LogoCyan)) }
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, sub: String? = null, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().graphicsLayer { alpha = if (enabled) 1f else 0.4f }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            if (sub != null) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked, onCheckedChange = onChecked, enabled = enabled,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = LogoViolet, checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color(0xFFB9ABDD), uncheckedTrackColor = Color(0xFF2A1B47), uncheckedBorderColor = Color(0xFF6B4FA0)
            )
        )
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(
                selected = selected == i, onClick = { onSelect(i) }, label = { Text(o, fontSize = 13.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = Color(0x1AFFFFFF), labelColor = Color(0xFFCFC2EE),
                    selectedContainerColor = LogoViolet.copy(alpha = 0.35f), selectedLabelColor = Color.White
                )
            )
        }
    }
}

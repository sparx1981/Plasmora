package app.plasmora.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.plasmora.SettingsStore
import app.plasmora.gl.Palettes
import app.plasmora.gl.Presets
import app.plasmora.gl.VisController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Preview pictures of each effect, captured from the real renderer and cached on the device. */
object ThumbStore {
    private const val VER = 3
    private val mem = ConcurrentHashMap<Int, ImageBitmap>()
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> get() = _version

    private fun file(ctx: Context, idx: Int): File {
        val dir = File(ctx.applicationContext.filesDir, "thumbs"); dir.mkdirs()
        return File(dir, Presets.all[idx].name.replace(Regex("[^A-Za-z0-9]"), "_") + "_v$VER.jpg")
    }

    fun has(ctx: Context, idx: Int): Boolean = idx in Presets.all.indices && (mem.containsKey(idx) || file(ctx, idx).exists())

    fun count(ctx: Context): Int = Presets.all.indices.count { has(ctx, it) }

    fun get(ctx: Context, idx: Int): ImageBitmap? {
        if (idx !in Presets.all.indices) return null
        mem[idx]?.let { return it }
        val f = file(ctx, idx)
        if (f.exists()) {
            BitmapFactory.decodeFile(f.path)?.let { val ib = it.asImageBitmap(); mem[idx] = ib; return ib }
        }
        return null
    }

    fun put(ctx: Context, idx: Int, bmp: Bitmap) {
        if (idx !in Presets.all.indices) return
        try { file(ctx, idx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 86, it) } } catch (_: Throwable) { }
        mem[idx] = bmp.asImageBitmap()
        _version.value++
    }

    fun clear(ctx: Context) {
        mem.clear()
        File(ctx.applicationContext.filesDir, "thumbs").listFiles()?.forEach { it.delete() }
        _version.value++
    }
}

data class PreviewProgress(val running: Boolean = false, val done: Int = 0, val total: Int = 0)

/** Walks through the effects one by one, lets each settle, and captures its preview. */
object PreviewJob {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private val _progress = MutableStateFlow(PreviewProgress())
    val progress: StateFlow<PreviewProgress> get() = _progress

    fun start(ctx: Context, onlyMissing: Boolean, settleMs: Long = 4200L) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        job = scope.launch {
            val list = Presets.all.indices.filter { !onlyMissing || !ThumbStore.has(app, it) }
            if (list.isEmpty()) return@launch
            val saved = SettingsStore.value
            val startIdx = VisController.info.value.presetIndex
            val wasPinned = VisController.pinned.get()
            val wasOverride = VisController.paletteOverride.get()
            _progress.value = PreviewProgress(true, 0, list.size)
            SettingsStore.update { it.copy(transitionSec = 0.5f, autoChange = false) }
            VisController.pinned.set(true)
            var fails = 0
            try {
                for ((n, i) in list.withIndex()) {
                    val pal = Presets.all[i].palette?.let { nm -> Palettes.all.indexOfFirst { it.name == nm } } ?: -1
                    VisController.paletteOverride.set(pal)
                    VisController.select(i)
                    delay(settleMs)
                    val before = ThumbStore.version.value
                    VisController.thumbReq.set(i)
                    var waited = 0
                    while (ThumbStore.version.value == before && waited < 3000) { delay(100); waited += 100 }
                    if (ThumbStore.version.value == before) { fails++; if (fails >= 4) break } else fails = 0
                    _progress.value = PreviewProgress(true, n + 1, list.size)
                }
            } finally {
                VisController.paletteOverride.set(wasOverride)
                VisController.pinned.set(wasPinned)
                SettingsStore.update { it.copy(transitionSec = saved.transitionSec, autoChange = saved.autoChange) }
                VisController.select(startIdx)
                _progress.value = PreviewProgress(false, _progress.value.done, _progress.value.total)
            }
        }
    }

    fun cancel() { job?.cancel() }
}

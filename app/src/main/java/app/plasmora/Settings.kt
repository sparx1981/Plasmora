package app.plasmora

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SourceMode(val label: String) {
    AUTO("Music + mic"),
    SYSTEM("Music only"),
    MIC("Microphone"),
    LOCAL("Local files"),
    DEMO("Demo")
}

data class Settings(
    // source
    val source: SourceMode = SourceMode.AUTO,
    // presets
    val autoChange: Boolean = true,
    val autoChangeSec: Int = 45,
    val shuffle: Boolean = true,
    val transitionSec: Float = 3.5f,
    val lastPreset: Int = 0,
    // colour
    val palette: Int = 0,
    val autoPalette: Boolean = true,
    val paletteSpeed: Float = 0.4f,
    val hue: Float = 0f,
    val saturation: Float = 1.1f,
    val exposure: Float = 1.0f,
    val gradientMap: Float = 0.2f,
    // quality
    val renderScale: Float = 1.0f,
    val maxFps: Int = 60,
    val bloomQuality: Int = 2,
    val bloomAmount: Float = 0.8f,
    val hdr: Boolean = true,
    val chroma: Float = 0.35f,
    val grain: Float = 0.12f,
    val vignette: Float = 0.35f,
    // audio
    val gain: Float = 1.0f,
    val bassGain: Float = 1.0f,
    val midGain: Float = 1.0f,
    val trebGain: Float = 1.0f,
    val smoothing: Float = 0.5f,
    val beatSens: Float = 0.5f,
    val agc: Boolean = true,
    val syncMs: Int = 150,
    // app
    val autoPip: Boolean = false,
    val showFps: Boolean = false,
    // visuals tweaks
    val speed: Float = 1f,
    val intensity: Float = 1f,
    val favourites: Set<Int> = emptySet(),
    val favOnly: Boolean = false,
    val dislikes: Set<Int> = emptySet(),
    val skipDisliked: Boolean = true,
    // music / now playing
    val artBg: Boolean = false,
    val artAmount: Float = 0.55f,
    val artBlur: Float = 0.8f,
    val artPalette: Boolean = false,
    val trackToast: Boolean = true,
    val offerStart: Boolean = true,
    val keepListening: Boolean = true,
    // power
    val autoBattery: Boolean = true,
    // smart changes
    val smartChange: Boolean = true,
    val songMemory: Boolean = true,
    // cinematic effects
    val fxEnabled: Boolean = true,
    val taa: Boolean = true,
    val motionBlur: Float = 0.6f,
    val streaks: Float = 0.5f,
    val flare: Float = 0.35f,
    val dof: Float = 0.6f,
    val godRays: Float = 0.6f,
    val reflections: Float = 0.6f,
    val grading: Float = 0.6f,
    val halation: Float = 0.25f,
    val sharpen: Float = 0.4f,
    val shake: Float = 0.35f,
    val alwaysShowControls: Boolean = true,
    val progressStyle: Int = 1,        // 0 off, 1 straight, 2 wavy, 3 dancer, 4 equaliser, 5 neon, 6 orb
    val artSize: Int = 2,              // 0 off, 1 small, 2 medium, 3 large
    val artOpensApp: Boolean = true,
    val playerOpacity: Float = 0f,   // background of the player controls, 0 = clear
    val lyricsStyle: Int = 5,          // 0 plain, 1 glow, 2 neon, 3 pulse, 4 palette gradient, 5 inside the visual
    val lyricsBack: Float = 0f,      // dark shade behind lyrics for readability
    val lyricsPos: Int = 0,            // 0 top, 1 centre, 2 bottom
    val lyricsMode: Int = 0,           // 0 off, 1 key lines, 2 full
    val parallax: Float = 0.8f,
    val visZoom: Float = 1f,           // framing of the 3D and full-screen looks, <1 = further away
    val director: Boolean = true,
    val edgeOverlay: Boolean = false,
    val edgeStyle: Int = 2,
    val edgeSize: Float = 0.5f,
    val dimMode: Boolean = false,
    val dimLevel: Float = 0.45f,
    val visualsGrid: Boolean = false,
    val onboarded: Boolean = false
)

object SettingsStore {
    private lateinit var prefs: SharedPreferences
    private val _flow = MutableStateFlow(Settings())
    val flow: StateFlow<Settings> get() = _flow
    val value: Settings get() = _flow.value

    fun init(ctx: Context) {
        if (::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("plasmora", Context.MODE_PRIVATE)
        _flow.value = load()
    }

    fun update(f: (Settings) -> Settings) {
        val n = f(_flow.value)
        _flow.value = n
        if (::prefs.isInitialized) save(n)
    }

    /** Puts every setting back to its default. Favourites are kept. */
    fun resetToDefaults() = update { Settings(favourites = it.favourites, dislikes = it.dislikes, onboarded = it.onboarded) }

    private fun load(): Settings {
        val d = Settings()
        val p = prefs
        return Settings(
            source = runCatching { SourceMode.valueOf(p.getString("source", d.source.name)!!) }.getOrDefault(d.source),
            autoChange = p.getBoolean("autoChange", d.autoChange),
            autoChangeSec = p.getInt("autoChangeSec", d.autoChangeSec),
            shuffle = p.getBoolean("shuffle", d.shuffle),
            transitionSec = p.getFloat("transitionSec", d.transitionSec),
            lastPreset = p.getInt("lastPreset", d.lastPreset),
            palette = p.getInt("palette", d.palette),
            autoPalette = p.getBoolean("autoPalette", d.autoPalette),
            paletteSpeed = p.getFloat("paletteSpeed", d.paletteSpeed),
            hue = p.getFloat("hue", d.hue),
            saturation = p.getFloat("saturation", d.saturation),
            exposure = p.getFloat("exposure", d.exposure),
            gradientMap = p.getFloat("gradientMap", d.gradientMap),
            renderScale = p.getFloat("renderScale", d.renderScale),
            maxFps = p.getInt("maxFps", d.maxFps),
            bloomQuality = p.getInt("bloomQuality", d.bloomQuality),
            bloomAmount = p.getFloat("bloomAmount", d.bloomAmount),
            hdr = p.getBoolean("hdr", d.hdr),
            chroma = p.getFloat("chroma", d.chroma),
            grain = p.getFloat("grain", d.grain),
            vignette = p.getFloat("vignette", d.vignette),
            gain = p.getFloat("gain", d.gain),
            bassGain = p.getFloat("bassGain", d.bassGain),
            midGain = p.getFloat("midGain", d.midGain),
            trebGain = p.getFloat("trebGain", d.trebGain),
            smoothing = p.getFloat("smoothing", d.smoothing),
            beatSens = p.getFloat("beatSens", d.beatSens),
            agc = p.getBoolean("agc", d.agc),
            syncMs = p.getInt("syncMs", d.syncMs),
            autoPip = p.getBoolean("autoPip", d.autoPip),
            showFps = p.getBoolean("showFps", d.showFps),
            speed = p.getFloat("speed", d.speed),
            intensity = p.getFloat("intensity", d.intensity),
            favourites = (p.getString("favourites", "") ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.let { raw ->
                // one-time remaps after presets were removed from the middle of the list
                var cur = raw
                if (!p.getBoolean("favMig2", false)) {
                    val gone = intArrayOf(7, 24, 51, 53, 73, 76)
                    cur = cur.filter { it !in gone }.map { o -> o - gone.count { g -> g < o } }
                }
                if (!p.getBoolean("favMig3", false)) {
                    val gone = intArrayOf(38)
                    cur = cur.filter { it !in gone }.map { o -> o - gone.count { g -> g < o } }
                }
                if (!p.getBoolean("favMig4", false)) {
                    val gone = intArrayOf(5, 47, 50, 77, 85, 86)
                    cur = cur.filter { it !in gone }.map { o -> o - gone.count { g -> g < o } }
                }
                cur.toSet()
            },
            favOnly = p.getBoolean("favOnly", d.favOnly),
            visZoom = p.getFloat("visZoom", d.visZoom),
            dislikes = (p.getString("dislikes", "") ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
            skipDisliked = p.getBoolean("skipDisliked", d.skipDisliked),
            artBg = p.getBoolean("artBg", d.artBg),
            artAmount = p.getFloat("artAmount", d.artAmount),
            artBlur = p.getFloat("artBlur", d.artBlur),
            artPalette = p.getBoolean("artPalette", d.artPalette),
            trackToast = p.getBoolean("trackToast", d.trackToast),
            offerStart = p.getBoolean("offerStart", d.offerStart),
            keepListening = p.getBoolean("keepListening", d.keepListening),
            autoBattery = p.getBoolean("autoBattery", d.autoBattery),
            smartChange = p.getBoolean("smartChange", d.smartChange),
            songMemory = p.getBoolean("songMemory", d.songMemory),
            fxEnabled = p.getBoolean("fxEnabled", d.fxEnabled),
            taa = p.getBoolean("taa", d.taa),
            motionBlur = p.getFloat("motionBlur", d.motionBlur),
            streaks = p.getFloat("streaks", d.streaks),
            flare = p.getFloat("flare", d.flare),
            dof = p.getFloat("dof", d.dof),
            godRays = p.getFloat("godRays", d.godRays),
            reflections = p.getFloat("reflections", d.reflections),
            grading = p.getFloat("grading", d.grading),
            halation = p.getFloat("halation", d.halation),
            sharpen = p.getFloat("sharpen", d.sharpen),
            shake = p.getFloat("shake", d.shake),
            alwaysShowControls = p.getBoolean("alwaysShowControls", d.alwaysShowControls),
            progressStyle = p.getInt("progressStyle", d.progressStyle),
            artSize = p.getInt("artSize", d.artSize),
            artOpensApp = p.getBoolean("artOpensApp", d.artOpensApp),
            playerOpacity = p.getFloat("playerOpacity", d.playerOpacity),
            lyricsMode = p.getInt("lyricsMode", d.lyricsMode),
            lyricsStyle = p.getInt("lyricsStyle", d.lyricsStyle),
            lyricsPos = p.getInt("lyricsPos", d.lyricsPos),
            lyricsBack = p.getFloat("lyricsBack", d.lyricsBack),
            parallax = p.getFloat("parallax", d.parallax),
            director = p.getBoolean("director", d.director),
            edgeOverlay = p.getBoolean("edgeOverlay", d.edgeOverlay),
            edgeStyle = p.getInt("edgeStyle", d.edgeStyle),
            edgeSize = p.getFloat("edgeSize", d.edgeSize),
            dimMode = p.getBoolean("dimMode", d.dimMode),
            dimLevel = p.getFloat("dimLevel", d.dimLevel),
            visualsGrid = p.getBoolean("visualsGrid", d.visualsGrid),
            onboarded = p.getBoolean("onboarded", d.onboarded)
        )
    }

    private fun save(s: Settings) {
        prefs.edit()
            .putString("source", s.source.name)
            .putBoolean("autoChange", s.autoChange)
            .putInt("autoChangeSec", s.autoChangeSec)
            .putBoolean("shuffle", s.shuffle)
            .putFloat("transitionSec", s.transitionSec)
            .putInt("lastPreset", s.lastPreset)
            .putInt("palette", s.palette)
            .putBoolean("autoPalette", s.autoPalette)
            .putFloat("paletteSpeed", s.paletteSpeed)
            .putFloat("hue", s.hue)
            .putFloat("saturation", s.saturation)
            .putFloat("exposure", s.exposure)
            .putFloat("gradientMap", s.gradientMap)
            .putFloat("renderScale", s.renderScale)
            .putInt("maxFps", s.maxFps)
            .putInt("bloomQuality", s.bloomQuality)
            .putFloat("bloomAmount", s.bloomAmount)
            .putBoolean("hdr", s.hdr)
            .putFloat("chroma", s.chroma)
            .putFloat("grain", s.grain)
            .putFloat("vignette", s.vignette)
            .putFloat("gain", s.gain)
            .putFloat("bassGain", s.bassGain)
            .putFloat("midGain", s.midGain)
            .putFloat("trebGain", s.trebGain)
            .putFloat("smoothing", s.smoothing)
            .putFloat("beatSens", s.beatSens)
            .putBoolean("agc", s.agc)
            .putInt("syncMs", s.syncMs)
            .putBoolean("autoPip", s.autoPip)
            .putBoolean("showFps", s.showFps)
            .putFloat("speed", s.speed)
            .putFloat("intensity", s.intensity)
            .putBoolean("favMig2", true).putBoolean("favMig3", true).putBoolean("favMig4", true).putString("favourites", s.favourites.sorted().joinToString(","))
            .putBoolean("favOnly", s.favOnly).putFloat("visZoom", s.visZoom)
            .putString("dislikes", s.dislikes.sorted().joinToString(",")).putBoolean("skipDisliked", s.skipDisliked)
            .putBoolean("artBg", s.artBg)
            .putFloat("artAmount", s.artAmount)
            .putFloat("artBlur", s.artBlur)
            .putBoolean("artPalette", s.artPalette)
            .putBoolean("trackToast", s.trackToast)
            .putBoolean("offerStart", s.offerStart)
            .putBoolean("keepListening", s.keepListening)
            .putBoolean("autoBattery", s.autoBattery)
            .putBoolean("smartChange", s.smartChange)
            .putBoolean("songMemory", s.songMemory)
            .putBoolean("fxEnabled", s.fxEnabled)
            .putBoolean("taa", s.taa)
            .putFloat("motionBlur", s.motionBlur)
            .putFloat("streaks", s.streaks)
            .putFloat("flare", s.flare)
            .putFloat("dof", s.dof)
            .putFloat("godRays", s.godRays)
            .putFloat("reflections", s.reflections)
            .putFloat("grading", s.grading)
            .putFloat("halation", s.halation)
            .putFloat("sharpen", s.sharpen)
            .putFloat("shake", s.shake)
            .putBoolean("alwaysShowControls", s.alwaysShowControls)
            .putInt("progressStyle", s.progressStyle)
            .putInt("artSize", s.artSize)
            .putBoolean("artOpensApp", s.artOpensApp)
            .putFloat("playerOpacity", s.playerOpacity)
            .putInt("lyricsMode", s.lyricsMode)
            .putInt("lyricsStyle", s.lyricsStyle)
            .putInt("lyricsPos", s.lyricsPos)
            .putFloat("lyricsBack", s.lyricsBack)
            .putFloat("parallax", s.parallax)
            .putBoolean("director", s.director)
            .putBoolean("edgeOverlay", s.edgeOverlay)
            .putInt("edgeStyle", s.edgeStyle)
            .putFloat("edgeSize", s.edgeSize)
            .putBoolean("dimMode", s.dimMode)
            .putFloat("dimLevel", s.dimLevel)
            .putBoolean("visualsGrid", s.visualsGrid)
            .putBoolean("onboarded", s.onboarded)
            .apply()
    }
}

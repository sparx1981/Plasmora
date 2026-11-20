package app.plasmora.gl

import app.plasmora.audio.AudioFrame
import kotlin.math.cos
import kotlin.math.sin

/** Indices into the warp parameter array. All "per-frame" values are expressed per 1/60 s. */
object P {
    const val ZOOM = 0; const val ROT = 1; const val SWIRL = 2; const val SWIRL_R = 3
    const val TX = 4; const val TY = 5
    const val RIPA = 6; const val RIPF = 7; const val RIPP = 8; const val RIPR = 9
    const val FLOWA = 10; const val FLOWS = 11; const val FLOWT = 12
    const val KAL = 13; const val TWIST = 14
    const val DECAY = 15; const val TR = 16; const val TG = 17; const val TB = 18
    const val HUE = 19; const val SOFT = 20
    const val N = 21

    fun defaults(p: FloatArray) {
        p.fill(0f)
        p[ZOOM] = 1f; p[SWIRL_R] = 3f; p[RIPF] = 10f; p[RIPR] = 1f; p[FLOWS] = 2f; p[FLOWT] = 0.01f
        p[DECAY] = 0.96f; p[TR] = 1f; p[TG] = 1f; p[TB] = 1f
    }
}

class LayerState {
    @JvmField var mode = 0
    @JvmField var amp = 0.3f
    @JvmField var scale = 0.8f
    @JvmField var width = 0.01f
    @JvmField var intensity = 0.1f
    @JvmField var rot = 0f
    @JvmField var cx = 0f
    @JvmField var cy = 0f
    @JvmField var phase = 0f
    @JvmField var spread = 0.3f
    @JvmField var core = 1f
    @JvmField var p1 = 0f
    @JvmField var p2 = 0f
    @JvmField var p3 = 0f
    // analyser layers (modes 10-12)
    @JvmField var count = 56
    @JvmField var gap = 0.22f
    @JvmField var mirror = 0          // 0 up, 1 centre mirror, 2 valley
    @JvmField var base = -0.85f
    @JvmField var height = 1.45f
    @JvmField var power = 1.2f
    @JvmField var capH = 0.012f
    @JvmField var caps = true
    @JvmField var hueLevel = 0.15f
    @JvmField var reflect = 1f         // brightness of the mirrored half (1 = full)
    @JvmField var tilt = 0.38f         // spike ring ellipse squash
    @JvmField var segGap = 0.28f       // LED segment gap
    @JvmField var dist = 0f            // 3D camera distance (0 = layer default)
    @JvmField var aux = 0f             // spare per-mode parameter (radial plane spokes)

    fun reset() {
        mode = 0; amp = 0.3f; scale = 0.8f; width = 0.01f; intensity = 0.1f; rot = 0f
        cx = 0f; cy = 0f; phase = 0f; spread = 0.3f; core = 1f; p1 = 0f; p2 = 0f; p3 = 0f
        count = 56; gap = 0.22f; mirror = 0; base = -0.85f; height = 1.45f; power = 1.2f
        capH = 0.012f; caps = true; hueLevel = 0.15f
        reflect = 1f; tilt = 0.38f; segGap = 0.28f; dist = 0f; aux = 0f
    }
}

typealias WarpFn = (a: AudioFrame, t: Float, mt: Float, p: FloatArray) -> Unit
typealias LayerFn = (a: AudioFrame, t: Float, mt: Float, l: LayerState) -> Unit

class Preset(
    val name: String,
    val warp: WarpFn,
    val layers: List<LayerFn>,
    /** spawn expanding rings on every beat */
    val shock: Boolean = false,
    /** palette name suggested for this look when auto-palette is on */
    val palette: String? = null
)

private fun s(x: Float) = sin(x)
private fun c(x: Float) = cos(x)

object Presets {
    val all: List<Preset> = listOf(
        Preset("Nebula Drift",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.004f + 0.004f * a.bassAtt
                p[P.ROT] = 0.0015f * s(mt * 0.3f) + 0.001f
                p[P.SWIRL] = 0.012f; p[P.SWIRL_R] = 2.5f
                p[P.FLOWA] = 0.0025f; p[P.FLOWS] = 2.2f; p[P.FLOWT] = 0.01f
                p[P.DECAY] = 0.972f; p[P.HUE] = 0.006f; p[P.SOFT] = 0.25f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 0; l.amp = 0.34f * (1f + 0.4f * a.beat); l.scale = 0.92f
                    l.width = 0.008f + 0.008f * a.beat; l.intensity = 0.11f + 0.05f * a.trebAtt
                    l.phase = mt * 0.05f; l.spread = 0.5f },
                { a, _, mt, l -> l.mode = 1; l.scale = 0.26f + 0.05f * a.bassAtt; l.amp = 0.22f
                    l.width = 0.006f; l.intensity = 0.13f; l.rot = mt * 0.2f; l.phase = mt * 0.07f + 0.5f; l.spread = 0.6f }
            )),

        Preset("Vortex",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.012f + 0.006f * a.bass
                p[P.ROT] = 0.01f + 0.004f * s(mt * 0.2f)
                p[P.SWIRL] = 0.05f + 0.03f * a.bassAtt; p[P.SWIRL_R] = 2.0f
                p[P.DECAY] = 0.955f; p[P.HUE] = 0.01f; p[P.SOFT] = 0.15f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 2; l.p1 = 3.5f; l.scale = 0.55f; l.amp = 0.25f
                    l.width = 0.01f; l.intensity = 0.13f + 0.06f * a.beat; l.rot = mt * 0.5f; l.phase = mt * 0.08f; l.spread = 0.7f }
            ), shock = true),

        Preset("Kaleido Bloom",
            warp = { a, _, mt, p ->
                p[P.KAL] = 6f
                p[P.ZOOM] = 1.008f + 0.004f * a.bass
                p[P.ROT] = 0.003f + 0.002f * s(mt * 0.2f)
                p[P.RIPA] = 0.0015f + 0.002f * a.trebAtt; p[P.RIPF] = 14f; p[P.RIPP] = 0.04f; p[P.RIPR] = 1f
                p[P.DECAY] = 0.965f; p[P.HUE] = 0.012f; p[P.SOFT] = 0.2f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 8; l.p1 = 5f; l.p3 = mt * 0.3f; l.scale = 0.5f; l.amp = 0.15f
                    l.width = 0.01f; l.intensity = 0.085f + 0.04f * a.beat; l.rot = mt * 0.15f; l.phase = mt * 0.05f; l.spread = 0.8f },
                { a, _, mt, l -> l.mode = 4; l.scale = 0.2f; l.amp = 0.4f; l.width = 0.007f
                    l.intensity = 0.085f; l.spread = 1.0f; l.phase = 0.3f + mt * 0.03f; l.rot = -mt * 0.1f }
            )),

        Preset("Lissajous Storm",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.003f; p[P.ROT] = 0.006f * s(mt * 0.4f)
                p[P.FLOWA] = 0.004f; p[P.FLOWS] = 3f; p[P.FLOWT] = 0.02f
                p[P.DECAY] = 0.935f; p[P.HUE] = 0.02f; p[P.SOFT] = 0.1f
                p[P.TR] = 1f; p[P.TG] = 0.99f; p[P.TB] = 1.01f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 3; l.p1 = 0.035f; l.scale = 1f; l.amp = 1.4f + 0.8f * a.bassAtt
                    l.width = 0.006f; l.intensity = 0.11f; l.rot = mt * 0.1f; l.phase = mt * 0.06f; l.spread = 0.9f }
            )),

        Preset("Spectrum Tunnel",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.035f + 0.02f * a.bass
                p[P.TWIST] = 0.012f; p[P.ROT] = 0.004f
                p[P.DECAY] = 0.935f; p[P.HUE] = 0.015f; p[P.SOFT] = 0.1f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 4; l.scale = 0.12f + 0.04f * a.bassAtt; l.amp = 0.5f; l.width = 0.009f
                    l.intensity = 0.2f; l.rot = mt * 0.2f; l.phase = mt * 0.04f; l.spread = 2.0f }
            )),

        Preset("Pulse Rings",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.008f
                p[P.RIPA] = 0.003f; p[P.RIPF] = 22f; p[P.RIPP] = 0.15f; p[P.RIPR] = 1f
                p[P.DECAY] = 0.95f; p[P.HUE] = 0.008f; p[P.SOFT] = 0.1f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 1; l.scale = 0.3f; l.amp = 0.25f; l.width = 0.007f
                    l.intensity = 0.14f; l.phase = mt * 0.06f; l.spread = 0.6f; l.rot = mt * 0.1f }
            ), shock = true),

        Preset("Harmonograph",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.002f; p[P.ROT] = 0.002f
                p[P.FLOWA] = 0.0015f; p[P.FLOWS] = 2.5f; p[P.FLOWT] = 0.008f
                p[P.DECAY] = 0.975f; p[P.HUE] = 0.006f; p[P.SOFT] = 0.3f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 7; l.p1 = 3f; l.p2 = 2f; l.p3 = mt * 0.3f; l.scale = 0.65f; l.amp = 0.4f
                    l.width = 0.007f; l.intensity = 0.13f; l.rot = mt * 0.07f; l.phase = mt * 0.05f; l.spread = 0.8f },
                { a, _, mt, l -> l.mode = 7; l.p1 = 5f; l.p2 = 4f; l.p3 = -mt * 0.4f; l.scale = 0.4f; l.amp = 0.3f
                    l.width = 0.006f; l.intensity = 0.1f; l.rot = -mt * 0.05f; l.phase = mt * 0.05f + 0.5f; l.spread = 1.0f }
            )),

        Preset("Solar Flare",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 0.9975f; p[P.ROT] = 0.003f
                p[P.SWIRL] = 0.012f; p[P.SWIRL_R] = 2f
                p[P.FLOWA] = 0.0012f; p[P.FLOWS] = 2.4f; p[P.FLOWT] = 0.006f
                p[P.DECAY] = 0.90f; p[P.TR] = 1f; p[P.TG] = 0.975f; p[P.TB] = 0.93f; p[P.SOFT] = 0.06f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 1; l.scale = 0.5f + 0.04f * a.bass; l.amp = 0.3f; l.width = 0.005f
                    l.intensity = 0.42f + 0.1f * a.beat; l.phase = 0.05f + mt * 0.005f; l.spread = 0.4f; l.rot = mt * 0.03f }
            ), shock = true),

        Preset("Hex Mirror",
            warp = { a, _, mt, p ->
                p[P.KAL] = 12f; p[P.ZOOM] = 1.006f; p[P.ROT] = -0.004f
                p[P.SWIRL] = 0.02f; p[P.RIPA] = 0.001f; p[P.RIPF] = 12f; p[P.RIPP] = 0.03f
                p[P.DECAY] = 0.968f; p[P.HUE] = 0.009f; p[P.SOFT] = 0.15f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 2; l.p1 = 2f; l.scale = 0.7f; l.amp = 0.3f; l.width = 0.008f
                    l.intensity = 0.13f; l.rot = mt * 0.2f; l.phase = mt * 0.06f; l.spread = 0.6f },
                { a, _, mt, l -> l.mode = 3; l.p1 = 0.03f; l.scale = 0.8f; l.amp = 1.3f; l.width = 0.006f
                    l.intensity = 0.09f; l.rot = -mt * 0.15f; l.phase = mt * 0.06f + 0.5f }
            )),

        Preset("Deep Space",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.002f; p[P.ROT] = 0.0007f
                p[P.FLOWA] = 0.0012f; p[P.FLOWS] = 4f; p[P.FLOWT] = 0.006f
                p[P.DECAY] = 0.992f; p[P.SOFT] = 0.05f; p[P.HUE] = 0.002f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 2; l.p1 = 1.2f; l.scale = 0.9f; l.amp = 0.15f; l.width = 0.005f
                    l.intensity = 0.08f + 0.04f * a.beat; l.rot = mt * 0.1f; l.phase = mt * 0.03f; l.spread = 0.9f },
                { a, _, mt, l -> l.mode = 1; l.scale = 0.16f + 0.03f * a.bassAtt; l.amp = 0.1f; l.width = 0.004f
                    l.intensity = 0.09f; l.rot = -mt * 0.2f; l.phase = mt * 0.03f + 0.3f }
            )),

        Preset("Ink in Water",
            warp = { a, _, mt, p ->
                p[P.FLOWA] = 0.01f; p[P.FLOWS] = 1.4f; p[P.FLOWT] = 0.012f
                p[P.TY] = 0.0005f
                p[P.DECAY] = 0.982f; p[P.SOFT] = 0.45f; p[P.HUE] = 0.003f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 4; l.scale = 0.35f; l.amp = 0.4f; l.width = 0.03f
                    l.intensity = 0.12f; l.phase = mt * 0.03f; l.spread = 0.8f; l.rot = mt * 0.1f; l.core = 0.6f },
                { a, _, mt, l -> l.mode = 1; l.scale = 0.2f + 0.08f * a.bassAtt; l.amp = 0.25f; l.width = 0.02f
                    l.intensity = 0.1f; l.phase = mt * 0.04f + 0.5f; l.core = 0.5f }
            )),

        Preset("Chroma Spiral",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.015f; p[P.ROT] = 0.02f
                p[P.SWIRL] = 0.03f; p[P.TWIST] = 0.01f
                p[P.DECAY] = 0.955f; p[P.HUE] = 0.03f; p[P.SOFT] = 0.1f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 2; l.p1 = 4f; l.scale = 0.5f; l.amp = 0.2f; l.width = 0.009f
                    l.intensity = 0.14f; l.rot = mt * 0.4f; l.phase = mt * 0.1f; l.spread = 1.2f },
                { a, _, mt, l -> l.mode = 3; l.p1 = 0.04f; l.scale = 0.6f; l.amp = 1.2f; l.width = 0.005f
                    l.intensity = 0.08f; l.rot = mt * 0.2f; l.phase = mt * 0.1f + 0.5f }
            )),

        // ------------------------------------------------------------ spectrum analysers,

        Preset("Spectrum Bars",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.62f; p[P.TY] = -0.0015f; p[P.HUE] = 0.004f; p[P.SOFT] = 0.1f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 10; l.count = 56; l.mirror = 0; l.base = -0.86f; l.height = 1.45f
                    l.scale = 0.94f; l.intensity = 0.40f * (1f + 0.25f * a.beat); l.phase = mt * 0.03f; l.spread = 0.9f }
            )),

        Preset("Mirror Bars",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.68f; p[P.HUE] = 0.004f; p[P.SOFT] = 0.1f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 10; l.count = 64; l.mirror = 1; l.base = 0f; l.height = 0.85f; l.gap = 0.3f
                    l.scale = 0.94f; l.intensity = 0.40f * (1f + 0.25f * a.beat); l.phase = mt * 0.03f; l.spread = 1.0f }
            )),

        Preset("Valley Bars",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.75f; p[P.HUE] = 0.004f; p[P.SOFT] = 0.12f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 10; l.count = 48; l.mirror = 2; l.height = 0.98f; l.gap = 0.25f
                    l.scale = 0.96f; l.intensity = 0.36f * (1f + 0.25f * a.beat); l.phase = mt * 0.03f; l.spread = 1.3f },
                { a, _, mt, l -> l.mode = 0; l.amp = 0.22f; l.scale = 0.96f; l.width = 0.006f
                    l.intensity = 0.10f; l.phase = mt * 0.04f + 0.5f; l.spread = 0.8f }
            )),

        Preset("Spectrum Wave",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.60f; p[P.TY] = -0.001f; p[P.HUE] = 0.004f; p[P.SOFT] = 0.15f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 11; l.count = 512; l.mirror = 0; l.base = -0.86f; l.height = 1.45f
                    l.scale = 1.0f; l.intensity = 0.50f * (1f + 0.2f * a.beat); l.phase = mt * 0.03f; l.spread = 0.7f }
            )),

        Preset("Mirror Wave",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.65f; p[P.HUE] = 0.004f; p[P.SOFT] = 0.15f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 11; l.count = 512; l.mirror = 1; l.base = 0f; l.height = 0.8f
                    l.scale = 1.0f; l.intensity = 0.50f * (1f + 0.2f * a.beat); l.phase = mt * 0.03f; l.spread = 0.7f }
            )),

        Preset("Valley Ridges",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.0f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 12; l.amp = 0.40f * (1f + 0.15f * a.beat); l.width = 0.011f; l.scale = 0.9f
                    l.p1 = 0.62f; l.p2 = -0.72f; l.p3 = 0.62f
                    l.intensity = 1.1f; l.phase = mt * 0.02f; l.spread = 0.5f; l.hueLevel = 0.5f }
            )),

        // ------------------------------------------------------------ visuals pack,

        Preset("Warp Tunnel",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.55f; p[P.SOFT] = 0.12f; p[P.HUE] = 0.003f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 13; l.p1 = 0f; l.p2 = 2.6f; l.p3 = mt * 0.55f; l.amp = 0.5f
                    l.intensity = 0.55f * (1f + 0.25f * a.beat); l.phase = mt * 0.02f; l.spread = 1.0f }
            )),

        Preset("Hex Tunnel",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.012f; p[P.ROT] = 0.004f * s(mt * 0.3f); p[P.DECAY] = 0.86f
                p[P.SOFT] = 0.2f; p[P.HUE] = 0.006f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 13; l.p1 = 6f; l.p2 = 2.2f; l.p3 = mt * 0.7f; l.amp = -1.4f * s(mt * 0.07f)
                    l.intensity = 0.34f * (1f + 0.3f * a.beat); l.phase = mt * 0.03f; l.spread = 1.4f }
            ), shock = true),

        Preset("Galaxy",
            warp = { a, _, mt, p ->
                p[P.ROT] = 0.003f; p[P.SWIRL] = 0.01f; p[P.SWIRL_R] = 2.0f
                p[P.DECAY] = 0.86f; p[P.SOFT] = 0.3f; p[P.HUE] = 0.004f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 14; l.p1 = 0f; l.p2 = 3f; l.p3 = mt; l.count = 26000
                    l.scale = 0.92f; l.amp = 5.2f; l.width = 0.0042f; l.intensity = 0.045f
                    l.phase = mt * 0.02f; l.spread = 0.9f },
                { a, _, mt, l -> l.mode = 1; l.scale = 0.05f + 0.02f * a.bassAtt; l.amp = 0.05f
                    l.width = 0.02f; l.intensity = 0.28f; l.phase = mt * 0.03f; l.spread = 0.3f }
            )),

        Preset("Starfield",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.006f; p[P.DECAY] = 0.7f; p[P.SOFT] = 0.1f; p[P.HUE] = 0.003f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 14; l.p1 = 1f; l.p3 = mt * 1.2f; l.count = 22000
                    l.scale = 0.85f; l.amp = 0.6f; l.width = 0.0038f; l.intensity = 0.06f
                    l.phase = mt * 0.015f; l.spread = 0.6f }
            )),

        Preset("Ink Smoke",
            warp = { a, _, mt, p ->
                p[P.FLOWA] = 0.0045f; p[P.FLOWS] = 1.5f; p[P.FLOWT] = 0.013f
                p[P.SWIRL] = 0.01f; p[P.ZOOM] = 1.0015f; p[P.DECAY] = 0.987f; p[P.SOFT] = 0.55f; p[P.HUE] = 0.003f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 4; l.scale = 0.10f + 0.03f * a.bassAtt; l.amp = 0.22f; l.width = 0.022f
                    l.cx = 0.45f * c(mt * 0.31f); l.cy = 0.35f * s(mt * 0.43f); l.intensity = 0.16f
                    l.phase = mt * 0.03f; l.spread = 0.8f },
                { a, _, mt, l -> l.mode = 4; l.scale = 0.09f + 0.03f * a.midAtt; l.amp = 0.2f; l.width = 0.02f
                    l.cx = 0.4f * s(mt * 0.37f + 2f); l.cy = 0.4f * c(mt * 0.27f + 1f); l.intensity = 0.15f
                    l.phase = mt * 0.03f + 0.35f; l.spread = 0.8f },
                { a, _, mt, l -> l.mode = 1; l.scale = 0.12f + 0.04f * a.trebAtt; l.amp = 0.12f; l.width = 0.016f
                    l.cx = 0.3f * c(mt * 0.53f + 4f); l.cy = 0.45f * s(mt * 0.33f + 3f); l.intensity = 0.14f
                    l.phase = mt * 0.03f + 0.7f; l.spread = 0.6f }
            )),

        Preset("Radial Spectrum",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.004f; p[P.DECAY] = 0.74f; p[P.SOFT] = 0.12f; p[P.HUE] = 0.004f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 15; l.count = 120; l.base = 0.27f; l.height = 0.5f; l.gap = 0.35f
                    l.rot = mt * 0.06f; l.intensity = 0.42f * (1f + 0.25f * a.beat); l.phase = mt * 0.03f; l.spread = 1.0f },
                { a, _, mt, l -> l.mode = 1; l.scale = 0.235f; l.amp = 0.01f; l.width = 0.006f
                    l.intensity = 0.12f; l.phase = mt * 0.03f + 0.4f; l.spread = 0.4f }
            )),

        Preset("Neon Ribbon",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.002f; p[P.DECAY] = 0.80f; p[P.SOFT] = 0.35f; p[P.HUE] = 0.002f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 11; l.count = 220; l.base = -0.25f; l.height = 0.9f; l.scale = 1f
                    l.mirror = 0; l.core = 0.25f; l.p1 = 0.012f; l.power = 1.1f; l.hueLevel = 0.35f
                    l.intensity = 0.55f * (1f + 0.3f * a.beat); l.phase = mt * 0.02f; l.spread = 0.6f; l.reflect = 1f },
                { a, _, mt, l -> l.mode = 0; l.amp = 0.16f; l.scale = 1f; l.width = 0.006f
                    l.cy = 0.28f; l.intensity = 0.12f; l.phase = mt * 0.04f + 0.3f; l.spread = 0.5f }
            ), palette = "Neon Ribbon"),

        Preset("Silk Waves",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.0015f; p[P.DECAY] = 0.86f; p[P.SOFT] = 0.5f; p[P.HUE] = 0.002f
                p[P.FLOWA] = 0.001f; p[P.FLOWS] = 1.6f; p[P.FLOWT] = 0.01f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 0; l.amp = 0.26f; l.scale = 1f; l.width = 0.007f; l.cy = 0.18f
                    l.intensity = 0.10f; l.phase = mt * 0.03f; l.spread = 0.4f },
                { a, _, mt, l -> l.mode = 0; l.amp = 0.22f; l.scale = 1f; l.width = 0.007f; l.cy = 0.04f
                    l.intensity = 0.10f; l.phase = mt * 0.03f + 0.15f; l.spread = 0.4f },
                { a, _, mt, l -> l.mode = 0; l.amp = 0.18f; l.scale = 1f; l.width = 0.007f; l.cy = -0.1f
                    l.intensity = 0.10f; l.phase = mt * 0.03f + 0.3f; l.spread = 0.4f },
                { a, _, mt, l -> l.mode = 11; l.count = 160; l.base = -0.4f; l.height = 0.7f; l.scale = 1f
                    l.core = 0.18f; l.p1 = 0.01f; l.intensity = 0.28f; l.phase = mt * 0.03f + 0.5f; l.spread = 0.5f }
            ), palette = "Ultraviolet"),

        Preset("Pulse Bars",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.001f; p[P.DECAY] = 0.55f; p[P.SOFT] = 0.1f; p[P.HUE] = 0.001f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 10; l.count = 150; l.mirror = 1; l.base = 0f; l.height = 0.9f
                    l.gap = 0.55f; l.p2 = 1f; l.caps = false; l.scale = 1f
                    l.intensity = 0.45f * (1f + 0.2f * a.beat); l.phase = mt * 0.02f; l.spread = 0.6f; l.hueLevel = 0.3f }
            ), palette = "Cyan Glow"),

        Preset("Grid EQ",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1f; p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 10; l.count = 44; l.mirror = 1; l.base = -0.12f; l.height = 0.8f
                    l.gap = 0.12f; l.p2 = 1f; l.p3 = 0.045f; l.segGap = 0.28f; l.reflect = 0.30f; l.caps = true
                    l.scale = 0.9f; l.intensity = 0.55f; l.phase = 0.6f; l.spread = 1.0f; l.hueLevel = 0.5f }
            ), palette = "Synthwave"),

        Preset("Spike Ring",
            warp = { a, _, mt, p ->
                p[P.ZOOM] = 1.002f; p[P.DECAY] = 0.78f; p[P.SOFT] = 0.2f; p[P.HUE] = 0.003f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 16; l.count = 180; l.base = 0.8f; l.height = 0.7f; l.gap = 0.35f
                    l.tilt = 0.38f; l.cy = -0.1f; l.rot = mt * 0.05f; l.caps = false
                    l.intensity = 0.5f * (1f + 0.25f * a.beat); l.phase = mt * 0.02f; l.spread = 1.0f; l.hueLevel = 0f }
            ), palette = "Spectrum"),

        Preset("Wire Terrain",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 17; l.p1 = 0f; l.p2 = 0f; l.p3 = mt * 0.8f; l.amp = 1.0f; l.count = 22
                    l.width = 1f; l.base = 1.3f; l.height = 0.45f; l.hueLevel = 0.5f
                    l.intensity = 0.9f * (1f + 0.2f * a.beat); l.phase = mt * 0.02f; l.spread = 0.5f }
            ), palette = "Mesh Neon"),

        Preset("Dot Terrain",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 17; l.p1 = 0f; l.p2 = 1f; l.p3 = mt * 0.8f; l.amp = 1.0f; l.count = 30
                    l.width = 2.2f; l.base = 1.3f; l.height = 0.45f; l.hueLevel = 0.5f
                    l.intensity = 1.4f * (1f + 0.2f * a.beat); l.phase = mt * 0.02f; l.spread = 0.5f }
            ), palette = "Amber Wire"),

        Preset("Ripple Plane",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 17; l.p1 = 2f; l.p2 = 0f; l.p3 = mt; l.amp = 0.5f; l.count = 22
                    l.width = 1f; l.base = 2.2f; l.height = 0.6f; l.rot = mt * 0.08f; l.hueLevel = 0.5f
                    l.intensity = 0.9f * (1f + 0.2f * a.beat); l.phase = mt * 0.02f; l.spread = 0.5f }
            ), palette = "Prism Mesh"),

        Preset("Synthwave Valley",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.55f; p[P.SOFT] = 0.12f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 17; l.p1 = 1f; l.p2 = 0f; l.p3 = mt * 1.2f; l.amp = 0.8f; l.count = 13
                    l.width = 1f; l.base = 0.75f; l.height = -0.12f; l.hueLevel = 0.3f
                    l.intensity = 0.9f * (1f + 0.15f * a.beat); l.phase = 0f; l.spread = 0.5f }
            ), palette = "Synthwave"),

        Preset("Wire Sphere",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.6f; p[P.SOFT] = 0.1f; p[P.HUE] = 0.002f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 18; l.scale = 0.36f + 0.025f * a.bassAtt; l.amp = 0.2f; l.rot = mt * 0.25f; l.cy = 0.22f
                    l.height = 0.45f; l.count = 36; l.p3 = mt; l.width = 1f
                    l.intensity = 0.9f * (1f + 0.2f * a.beat); l.phase = mt * 0.02f; l.spread = 1.0f }
            ), palette = "Spectrum"),

        // ---------------------------------------------------------------- round 6: fluid, GPU particles, raymarched,

        Preset("Liquid Light",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.30f; p[P.SOFT] = 0.05f; p[P.HUE] = 0.0f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 19; l.amp = 1.0f; l.p2 = 1.0f; l.p3 = 1.0f; l.hueLevel = 0.8f
                    l.intensity = 0.95f; l.phase = mt * 0.01f; l.spread = 0.7f }
            ), palette = "Plasma Ink"),

        Preset("Ink Vortex",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.30f; p[P.SOFT] = 0.05f; p[P.HUE] = 0.0f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 19; l.amp = 1.5f; l.p2 = 2.2f; l.p3 = 0.8f; l.hueLevel = 1.1f
                    l.intensity = 1.0f; l.phase = mt * 0.015f; l.spread = 1.1f }
            ), palette = "Ultraviolet"),

        Preset("Swarm",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.88f; p[P.SOFT] = 0.2f; p[P.HUE] = 0.002f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 14; l.p1 = 3f; l.p3 = mt; l.count = 140000; l.scale = 0.95f
                    l.rot = mt * 0.12f; l.height = 0.35f + 0.1f * kotlin.math.sin(mt * 0.1f); l.width = 0.0022f
                    l.intensity = 0.030f; l.phase = mt * 0.015f; l.spread = 0.9f }
            ), palette = "Cyan Glow"),

        Preset("Pulse Shell",
            warp = { a, _, mt, p -> p[P.ZOOM] = 1.004f; p[P.DECAY] = 0.84f; p[P.SOFT] = 0.2f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 14; l.p1 = 5f; l.p3 = mt; l.count = 90000; l.scale = 0.95f
                    l.rot = mt * 0.2f; l.height = 0.4f; l.width = 0.0026f
                    l.intensity = 0.045f; l.phase = mt * 0.02f; l.spread = 1.2f }
            ), palette = "Neon Ribbon"),

        Preset("Mandelbulb",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.55f; p[P.SOFT] = 0.1f; p[P.HUE] = 0.001f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 20; l.p1 = 0f; l.count = 72; l.scale = 0.36f; l.cy = 0f
                    l.rot = mt * 0.18f; l.height = 0.3f; l.p3 = mt; l.amp = 0.9f
                    l.intensity = 0.8f; l.phase = mt * 0.01f; l.spread = 0.9f }
            ), palette = "Inferno"),

        Preset("Metaball Bloom",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.55f; p[P.SOFT] = 0.1f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 20; l.p1 = 1f; l.count = 64; l.scale = 0.34f; l.cy = 0.02f
                    l.rot = mt * 0.1f; l.height = 0.15f; l.p3 = mt; l.amp = 1.0f
                    l.intensity = 0.85f; l.phase = mt * 0.012f; l.spread = 0.8f }
            ), palette = "Pearl"),

        Preset("Crystal Cathedral",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.55f; p[P.SOFT] = 0.1f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 20; l.p1 = 2f; l.count = 80; l.scale = 0.46f; l.cy = 0f
                    l.rot = mt * 0.08f; l.height = 0.25f; l.p3 = mt; l.amp = 0.9f
                    l.intensity = 0.8f; l.phase = mt * 0.01f; l.spread = 1.0f }
            ), palette = "Prism Mesh"),

        Preset("Bar Grid",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 21; l.count = 18; l.p1 = 14f; l.scale = 1.3f; l.p3 = 1.2f
                    l.amp = 1.2f; l.gap = 0.3f; l.power = 1.3f; l.height = 0.6f; l.dist = 4.2f
                    l.rot = 0.55f + 0.5f * s(mt * 0.15f); l.cy = 0f; l.reflect = 0.35f; l.hueLevel = 0.4f; l.spread = 1.0f
                    l.intensity = 1.0f; l.phase = mt * 0.02f }
            ), palette = "Spectrum"),

        Preset("Neon Bar Grid",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 21; l.count = 22; l.p1 = 16f; l.scale = 1.3f; l.p3 = 1.2f
                    l.amp = 1.4f; l.gap = 0.45f; l.power = 1.3f; l.height = 0.45f; l.dist = 4.4f
                    l.rot = mt * 0.12f; l.cy = 0f; l.reflect = 0.5f; l.hueLevel = 0.7f; l.spread = 0.8f
                    l.intensity = 1.0f; l.phase = mt * 0.02f }
            ), palette = "Synthwave"),

        Preset("Bar Towers",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 21; l.count = 32; l.p1 = 22f; l.scale = 1.45f; l.p3 = 1.5f
                    l.amp = 1.0f; l.gap = 0.22f; l.power = 1.3f; l.height = 0.85f; l.dist = 4.8f
                    l.rot = 0.4f * s(mt * 0.1f); l.cy = 0f; l.reflect = 0.25f; l.hueLevel = 0.5f; l.spread = 0.9f
                    l.intensity = 1.0f; l.phase = mt * 0.02f }
            ), palette = "Ultraviolet"),

        Preset("Ribbon Flux",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.55f; p[P.SOFT] = 0.12f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 22; l.p1 = 0f; l.count = 44; l.scale = 1.5f; l.amp = 0.8f
                    l.width = 0.0035f; l.core = 0.8f; l.p2 = 1.1f; l.height = 0.35f; l.dist = 3.0f
                    l.rot = 0f; l.cy = 0f; l.power = 1.0f
                    l.intensity = 0.16f * (1f + 0.3f * a.beat); l.hueLevel = 0.3f; l.spread = 0.9f; l.phase = mt * 0.02f }
            ), palette = "Neon Ribbon"),

        Preset("Ribbon Helix",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.55f; p[P.SOFT] = 0.12f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 22; l.p1 = 0f; l.count = 36; l.scale = 1.5f; l.amp = 0.6f
                    l.width = 0.0035f; l.core = 1.1f; l.p2 = 2.2f; l.height = 0.5f; l.dist = 3.0f
                    l.rot = 0.6f * s(mt * 0.2f); l.cy = 0f; l.power = 1.0f
                    l.intensity = 0.2f * (1f + 0.3f * a.beat); l.hueLevel = 0.3f; l.spread = 0.7f; l.phase = mt * 0.02f }
            ), palette = "Cyan Glow"),

        Preset("Dot Ocean",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.6f; p[P.SOFT] = 0.12f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 22; l.p1 = 1f; l.count = 760; l.p2 = 34f; l.scale = 1.6f; l.amp = 0.9f
                    l.width = 2.8f; l.height = 0.38f; l.dist = 3.2f; l.cy = -0.05f; l.power = 1.1f
                    l.p3 = mt * 1.1f; l.intensity = 1.2f * (1f + 0.25f * a.beat)
                    l.hueLevel = 0.4f; l.spread = 0.7f; l.phase = mt * 0.02f }
            ), palette = "Deep Ocean"),

        Preset("Peak Wire",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 23; l.p1 = 0f; l.p2 = 0f; l.count = 40; l.scale = 2.2f
                    l.amp = 0.5f; l.width = 1.2f; l.height = 0.5f; l.dist = 3.3f; l.cy = -0.15f; l.power = 1.2f
                    l.p3 = mt * 0.8f; l.intensity = 0.9f * (1f + 0.2f * a.beat)
                    l.hueLevel = 0.5f; l.spread = 0.4f; l.phase = mt * 0.015f }
            ), palette = "Cyan Glow"),

        Preset("Ocean Waves",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 23; l.p1 = 0f; l.p2 = 1f; l.count = 80; l.scale = 2.6f
                    l.amp = 0.75f; l.aux = 1f; l.width = 0.9f; l.height = 0.5f; l.dist = 3.3f; l.cy = -0.15f; l.power = 1.2f
                    l.p3 = mt * 0.8f; l.intensity = 0.9f * (1f + 0.2f * a.beat)
                    l.hueLevel = 0.6f; l.spread = 0.3f; l.phase = mt * 0.015f }
            ), palette = "Mesh Neon"),

        Preset("Radial Plane",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.55f; p[P.SOFT] = 0.08f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 23; l.p1 = 1f; l.count = 35; l.aux = 64f; l.scale = 1.7f
                    l.amp = 0.4f; l.width = 1.2f; l.height = 0.95f; l.dist = 3.3f; l.cy = 0f; l.power = 1.2f
                    l.p3 = mt * 0.15f; l.intensity = 1.0f * (1f + 0.25f * a.beat)
                    l.hueLevel = 0.6f; l.spread = 0.5f; l.phase = mt * 0.015f }
            ), palette = "Sun & Sea"),

        Preset("Radial Bloom",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.55f; p[P.SOFT] = 0.08f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 23; l.p1 = 1f; l.count = 30; l.aux = 96f; l.scale = 1.8f
                    l.amp = 0.55f; l.width = 1.2f; l.height = 1.05f; l.dist = 3.3f; l.cy = 0f; l.power = 1.2f
                    l.p3 = mt * 0.3f; l.intensity = 1.0f * (1f + 0.25f * a.beat)
                    l.hueLevel = 0.7f; l.spread = 0.7f; l.phase = mt * 0.015f }
            ), palette = "Spectrum"),

        Preset("Plane & Terrain",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.06f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 23; l.p1 = 0f; l.p2 = 0f; l.count = 40; l.scale = 2.2f
                    l.amp = 0.45f; l.width = 1.2f; l.height = 0.45f; l.dist = 3.3f; l.cy = -0.55f; l.power = 1.2f
                    l.p3 = mt * 0.8f; l.intensity = 0.85f; l.hueLevel = 0.6f; l.spread = 0.4f; l.phase = mt * 0.015f },
                { a, _, mt, l -> l.mode = 23; l.p1 = 1f; l.count = 32; l.aux = 56f; l.scale = 1.15f
                    l.amp = 0.28f; l.width = 1.2f; l.height = 1.0f; l.dist = 3.3f; l.cy = 0.42f; l.power = 1.2f
                    l.p3 = mt * 0.12f; l.intensity = 0.9f * (1f + 0.25f * a.beat); l.hueLevel = 0.6f; l.spread = 0.5f; l.phase = mt * 0.015f }
            ), palette = "Sun & Sea"),

        Preset("LED Spectrum",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 10; l.count = 32; l.mirror = 0; l.base = -0.85f; l.height = 1.55f
                    l.gap = 0.18f; l.p2 = 0f; l.p3 = 0.06f; l.segGap = 0.30f; l.reflect = 0.25f; l.caps = true
                    l.aux = 1.0f; l.scale = 0.95f; l.intensity = 0.9f; l.phase = 0f; l.spread = 0f; l.hueLevel = 0f }
            ), palette = "LED Meter"),

        Preset("LED Mirror",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 10; l.count = 40; l.mirror = 1; l.base = -0.05f; l.height = 0.85f
                    l.gap = 0.16f; l.p2 = 1f; l.p3 = 0.05f; l.segGap = 0.30f; l.reflect = 0.85f; l.caps = true
                    l.aux = 1.0f; l.scale = 0.95f; l.intensity = 0.85f; l.phase = 0f; l.spread = 0f; l.hueLevel = 0f }
            ), palette = "LED Meter"),

        Preset("LED Neon",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 10; l.count = 48; l.mirror = 0; l.base = -0.85f; l.height = 1.5f
                    l.gap = 0.14f; l.p2 = 1f; l.p3 = 0.045f; l.segGap = 0.30f; l.reflect = 0.3f; l.caps = true
                    l.aux = 1.0f; l.scale = 0.95f; l.intensity = 0.8f; l.phase = 0f; l.spread = 0f; l.hueLevel = 0f }
            ), palette = "Synthwave"),

        Preset("Hex Pastel",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.35f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 24; l.p1 = 0f; l.scale = 7f; l.p2 = 0.08f; l.p3 = 1f; l.intensity = 0.9f; l.phase = mt * 0.01f; l.spread = 0.5f; l.rot = 0.1f * s(mt * 0.1f) }
            ), palette = "Candy Pastel"),

        Preset("Hex Storm",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.35f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 24; l.p1 = 1f; l.scale = 11f; l.p2 = 0f; l.p3 = 0.7f; l.intensity = 0.9f; l.phase = mt * 0.015f; l.spread = 0.9f }
            ), palette = "Neon Arcade"),

        Preset("Hex Sweep",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.35f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 24; l.p1 = 2f; l.scale = 9f; l.p2 = 0.12f; l.p3 = 0.9f; l.intensity = 0.85f; l.phase = mt * 0.01f; l.spread = 0.6f; l.rot = 0.5f }
            ), palette = "Cyan Glow"),

        Preset("Neon Frames",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 25; l.p1 = 12f; l.p2 = 1.1f; l.scale = 0.6f; l.p3 = mt * 1.1f; l.cy = 0.05f; l.core = 0.6f; l.amp = 1f; l.spread = 0.6f; l.intensity = 0.8f; l.phase = mt * 0.01f }
            ), palette = "Neon Arcade"),

        Preset("Frame Gate",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 25; l.p1 = 8f; l.p2 = 1.6f; l.scale = 0.45f; l.p3 = mt * 1.6f; l.cy = 0.08f; l.core = 0.3f; l.amp = 1.1f; l.spread = 0.8f; l.intensity = 0.8f; l.phase = mt * 0.01f }
            ), palette = "Synthwave"),

        Preset("Neon Maze",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 26; l.p1 = 4f; l.scale = 2f; l.p2 = 0.12f; l.p3 = 1f; l.amp = 1f; l.spread = 0.7f; l.intensity = 0.8f; l.rot = 0.04f * mt; l.phase = mt * 0.01f }
            ), palette = "Neon Arcade"),

        Preset("Maze Bloom",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 26; l.p1 = 5f; l.scale = 1.3f; l.p2 = 0.08f; l.p3 = 0.9f; l.amp = 1.2f; l.spread = 0.9f; l.intensity = 0.75f; l.rot = 0.08f * mt; l.phase = mt * 0.012f }
            ), palette = "Synthwave"),

        Preset("Circuit Maze",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 26; l.p1 = 3f; l.scale = 3f; l.p2 = 0.16f; l.p3 = 1.5f; l.amp = 1f; l.spread = 0.5f; l.intensity = 0.85f; l.phase = mt * 0.01f }
            ), palette = "Cyan Glow"),

        Preset("Vortex Halo",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 28; l.scale = 0.30f; l.amp = 1f; l.p1 = 128f; l.p2 = 1f; l.p3 = 0.8f; l.width = 0.004f; l.core = 1f; l.spread = 1f; l.intensity = 0.85f; l.cy = 0.02f; l.phase = mt * 0.01f }
            ), palette = "Spectrum"),

        Preset("Halo Pulse",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 28; l.scale = 0.19f * (1f + 0.06f * a.beat); l.amp = 1.2f; l.p1 = 96f; l.p2 = 1.6f; l.p3 = 0.4f; l.width = 0.005f; l.core = 1.3f; l.spread = 0.6f; l.intensity = 0.85f; l.phase = mt * 0.012f }
            ), palette = "Neon Arcade"),

        Preset("Neon Tiles",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 29; l.scale = 0.6f; l.p1 = 1f; l.p2 = 40f; l.p3 = mt * 1.2f; l.cy = 0.05f; l.width = 0.02f; l.core = 1f; l.amp = 1f; l.spread = 0.8f; l.intensity = 0.85f; l.phase = mt * 0.01f }
            ), palette = "Neon Arcade"),

        Preset("Light Rain",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 29; l.scale = 0.55f; l.p1 = 1.2f; l.p2 = 64f; l.p3 = mt * 0.8f; l.cy = -0.05f; l.width = 0.014f; l.core = 1.3f; l.amp = 1.2f; l.spread = 0.7f; l.intensity = 0.85f; l.phase = mt * 0.01f }
            ), palette = "Cyan Glow"),

        Preset("Radial Disc",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 30; l.count = 96; l.scale = 1.0f; l.amp = 0.9f; l.power = 1.1f; l.p1 = 0.45f; l.p2 = 0.5f; l.p3 = mt * 0.05f; l.width = 0.85f; l.height = 0.95f; l.dist = 3.2f; l.cy = 0.02f; l.rot = 0.15f * s(mt * 0.15f); l.hueLevel = 0.3f; l.spread = 1f; l.intensity = 1.2f * (1f + 0.2f * a.beat); l.phase = mt * 0.01f }
            ), palette = "Spectrum"),

        Preset("Spectrum Crown",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 30; l.count = 64; l.scale = 1.05f; l.amp = 1.1f; l.power = 1.1f; l.p1 = 0.7f; l.p2 = 0.35f; l.p3 = mt * 0.08f; l.width = 0.7f; l.height = 1.2f; l.dist = 3.3f; l.cy = 0f; l.rot = 0.4f * s(mt * 0.12f); l.hueLevel = 0.6f; l.spread = 0.8f; l.intensity = 1.2f * (1f + 0.2f * a.beat); l.phase = mt * 0.012f }
            ), palette = "Neon Night"),

        Preset("Radial Halo",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 30; l.count = 168; l.scale = 1.0f; l.amp = 0.8f; l.power = 1.2f; l.p1 = 0.55f; l.p2 = 0.4f; l.p3 = mt * 0.03f; l.width = 0.4f; l.height = 1.05f; l.dist = 3.2f; l.cy = 0.02f; l.hueLevel = 0.5f; l.spread = 1.4f; l.intensity = 1.3f * (1f + 0.2f * a.beat); l.phase = mt * 0.01f }
            ), palette = "Cyan Glow"),

        Preset("Bead Terrain",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 22; l.p1 = 1f; l.aux = 1f; l.count = 40; l.p2 = 44f; l.scale = 1.8f; l.amp = 0.4f; l.width = 12f; l.height = 0.62f; l.dist = 3.0f; l.cy = -0.1f; l.power = 1.1f; l.p3 = mt * 0.22f; l.intensity = 1.0f; l.hueLevel = 0.8f; l.spread = 0.2f; l.phase = mt * 0.01f }
            ), palette = "Bead Heat"),

        Preset("Bead Ocean",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 22; l.p1 = 1f; l.aux = 1f; l.count = 40; l.p2 = 44f; l.scale = 1.8f; l.amp = 0.45f; l.width = 12f; l.height = 0.7f; l.dist = 3.0f; l.cy = -0.1f; l.power = 1.1f; l.p3 = mt * 0.22f; l.intensity = 1.0f; l.hueLevel = 0.6f; l.spread = 0.4f; l.phase = mt * 0.01f }
            ), palette = "Deep Ocean"),

        Preset("Cherenkov Pulse",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 31; l.p1 = 41f; l.scale = 0.21f; l.amp = 0.5f; l.width = 0.6f; l.p2 = 6f; l.core = 1f; l.cy = -0.1f; l.spread = 0.3f; l.intensity = 0.9f; l.phase = mt * 0.005f }
            ), palette = "Cherenkov Blue"),

        Preset("Pulse Ripples",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 31; l.p1 = 29f; l.scale = 0.21f; l.amp = 0.38f; l.width = 0.7f; l.p2 = 9f; l.core = 1.7f; l.cy = -0.08f; l.spread = 0.2f; l.intensity = 0.9f; l.phase = mt * 0.005f }
            ), palette = "Cyan Glow"),

        Preset("Neon Corridor",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 32; l.p1 = 4f; l.p2 = 12f; l.p3 = mt * 1.2f; l.scale = 0.5f; l.rot = 0.04f * s(mt * 0.2f); l.cy = -0.17f; l.core = 0f; l.amp = 1f; l.spread = 0.3f; l.intensity = 0.55f; l.phase = mt * 0.004f }
            ), palette = "Hot Corridor"),

        Preset("Triangle Gate",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 32; l.p1 = 3f; l.p2 = 12f; l.p3 = mt * 1.1f; l.scale = 0.4f; l.rot = 0.12f * s(mt * 0.15f); l.cy = -2f; l.core = 1f; l.amp = 1f; l.spread = 0.5f; l.intensity = 0.55f; l.phase = mt * 0.004f }
            ), palette = "Neon Triangle"),

        Preset("Hex Portal",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 32; l.p1 = 6f; l.p2 = 14f; l.p3 = mt * 1.4f; l.scale = 0.5f; l.rot = 0.1f; l.cy = -2f; l.core = 0.4f; l.amp = 1.1f; l.spread = 0.8f; l.intensity = 0.55f; l.phase = mt * 0.01f }
            ), palette = "Cyan Glow"),

        Preset("Neon Smoke",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 33; l.scale = 1.2f; l.p1 = 3f; l.p2 = 0.6f; l.p3 = 1f; l.core = 1f; l.amp = 1f; l.spread = 1f; l.intensity = 0.9f; l.rot = 0.02f * mt; l.phase = mt * 0.01f }
            ), palette = "Spectrum"),

        Preset("Circuit Flow",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 33; l.scale = 1.4f; l.p1 = 6f; l.p2 = 0.8f; l.p3 = 1.8f; l.core = 0.7f; l.amp = 1.1f; l.spread = 0.8f; l.intensity = 0.9f; l.phase = mt * 0.012f }
            ), palette = "Neon Arcade"),

        Preset("Ink Aurora",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.45f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 33; l.scale = 1.0f; l.p1 = 2f; l.p2 = 0.45f; l.p3 = 0.3f; l.core = 1.3f; l.amp = 1f; l.spread = 0.6f; l.intensity = 0.9f; l.phase = mt * 0.008f }
            ), palette = "Deep Ocean"),

        Preset("Crystal Spikes",
            warp = { a, _, mt, p ->
                p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f
            },
            layers = listOf(
                { a, _, mt, l -> l.mode = 34; l.count = 34; l.p2 = 26f; l.scale = 1.9f; l.amp = 1.0f; l.power = 1.0f; l.width = 1.0f; l.height = 0.5f; l.dist = 3.0f; l.cy = -0.1f; l.p3 = mt * 0.6f; l.intensity = 1.0f; l.hueLevel = 0.4f; l.spread = 0.2f; l.phase = mt * 0.01f }
            ), palette = "Teal Crystal"),

        Preset("Smoke Bars",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.35f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 35; l.scale = 0.62f; l.p1 = 30f; l.amp = 1.0f; l.cy = -0.18f; l.p2 = 0.8f; l.p3 = 1.0f; l.core = 1.0f; l.spread = 0.8f; l.width = 0.25f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Spectrum"),

        Preset("Smoke Crown",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.35f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 38; l.scale = 0.2f; l.p1 = 96f; l.amp = 0.8f; l.width = 0.55f; l.p2 = 0.8f; l.p3 = 1.2f; l.core = 1.0f; l.spread = 0.8f; l.rot = mt * 0.03f; l.cy = 0f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Magma"),

        Preset("Smoke Bloom",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.35f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 37; l.scale = 0.07f; l.p1 = 7f; l.amp = 1.0f; l.p2 = 0.7f; l.p3 = 0.6f; l.core = 1.0f; l.spread = 1.0f; l.rot = mt * 0.03f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Spectrum"),

        Preset("Smoke Nova",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.35f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 37; l.scale = 0.16f; l.p1 = 4f; l.amp = 1.4f; l.p2 = 1.1f; l.p3 = 1.2f; l.core = 1.6f; l.spread = 0.5f; l.rot = -mt * 0.05f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Magma"),

        Preset("Circuit Trails",
            warp = { a, _, mt, p -> p[P.ZOOM] = 1.004f; p[P.DECAY] = 0.86f; p[P.SOFT] = 0.1f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 36; l.p1 = 0f; l.count = 26; l.p2 = 8f; l.p3 = 1f; l.scale = 0.8f; l.width = 0.005f; l.amp = 1.0f; l.power = 0.3f; l.dist = 3.2f; l.height = 0.35f; l.rot = mt * 0.08f; l.cy = 0f; l.core = 1.0f; l.spread = 1.0f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Neon Arcade"),

        Preset("Wire Bundle",
            warp = { a, _, mt, p -> p[P.ZOOM] = 1.003f; p[P.DECAY] = 0.84f; p[P.SOFT] = 0.1f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 36; l.p1 = 1f; l.count = 12; l.p2 = 7f; l.p3 = 7f; l.gap = 0.022f; l.scale = 0.8f; l.width = 0.003f; l.amp = 1.0f; l.power = 0.25f; l.dist = 3.2f; l.height = 0.3f; l.rot = mt * 0.06f; l.core = 0.8f; l.spread = 0.8f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Spectrum"),

        Preset("Neon Ribbons",
            warp = { a, _, mt, p -> p[P.ZOOM] = 1.004f; p[P.DECAY] = 0.88f; p[P.SOFT] = 0.12f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 36; l.p1 = 2f; l.count = 8; l.p2 = 28f; l.p3 = 1f; l.scale = 0.95f; l.width = 0.006f; l.amp = 1.0f; l.power = 0.25f; l.dist = 3.2f; l.height = 0.25f; l.rot = mt * 0.1f; l.core = 1.0f; l.spread = 1.0f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Spectrum"),
        Preset("Glass Ring",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.5f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 44; l.count = 26; l.p1 = 2f; l.scale = 0.8f; l.amp = 0.8f; l.power = 1.1f; l.width = 0.62f; l.height = 0.42f; l.dist = 3.4f; l.cy = 0.02f; l.rot = mt * 0.05f; l.p3 = 0f; l.hueLevel = 0.9f; l.spread = 1.0f; l.core = 1.0f; l.intensity = 1.0f * (1f + 0.15f * a.beat); l.phase = mt * 0.01f }
            ), palette = "Neon Arcade"),

        Preset("Dot Ripples",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 39; l.scale = 0.1f; l.p1 = 0.8f; l.amp = 1.0f; l.p2 = 0.5f; l.p3 = 2.2f; l.spread = 0.9f; l.core = 0.6f; l.cy = 0.0f; l.rot = mt * 0.02f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Spectrum"),

        Preset("LED Tunnel",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 40; l.scale = 0.55f; l.p1 = 72f; l.p2 = 0.45f; l.p3 = 6f; l.amp = 0.5f; l.spread = 0.6f; l.core = 1.0f; l.rot = mt * 0.05f; l.phase = mt * 0.01f; l.intensity = 1.0f }
            ), palette = "Hot Corridor"),

        Preset("Ring Bars",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 41; l.scale = 0.26f; l.p1 = 5f; l.amp = 0.6f; l.width = 0.62f; l.p2 = 1.0f; l.p3 = 1.0f; l.core = 1.0f; l.spread = 1.0f; l.cy = 0.10f; l.phase = mt * 0.005f; l.intensity = 1.0f }
            ), palette = "Neon Arcade"),

        Preset("Dot Equaliser",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 42; l.scale = 0.95f; l.p1 = 34f; l.amp = 1.0f; l.p2 = 1f; l.p3 = 1.0f; l.core = 1.0f; l.spread = 1.0f; l.cy = -0.12f; l.phase = mt * 0.005f; l.intensity = 1.0f }
            ), palette = "Spectrum"),

        Preset("Horizon Spectrum",
            warp = { a, _, mt, p -> p[P.DECAY] = 0.4f; p[P.SOFT] = 0.05f },
            layers = listOf(
                { a, _, mt, l -> l.mode = 43; l.scale = 0.75f; l.p1 = 220f; l.amp = 1.0f; l.p2 = 0.5f; l.p3 = 1.0f; l.core = 1.0f; l.spread = 0.6f; l.cy = -0.1f; l.phase = mt * 0.005f; l.intensity = 1.0f }
            ), palette = "Cherenkov Blue")
    )
}

/** Browser categories for the Visuals tab, keyed by preset name. */
object PresetCategories {
    const val FAV = "Liked"
    val order = listOf("Classic", "Spectrum & Bars", "Waves & Ribbons", "3D Terrain & Shapes", "Tunnels", "Particles", "Smoke & Fluid", "Raymarched 3D", "3D Bar Grids", "Ribbons & Dots", "Wire Mountains & Planes", "Hex & Tiles", "Neon Tunnels & Mazes", "Fire & Halos", "Radial & Beads", "Pulse & Tunnels", "Smoke & Spikes", "Smoke & Light Trails", "LED & Glass")
    private val map: Map<String, String> = run {
        val m = HashMap<String, String>()
        fun put(cat: String, vararg n: String) { for (x in n) m[x] = cat }
        put("Classic", "Nebula Drift", "Vortex", "Kaleido Bloom", "Lissajous Storm", "Pulse Rings", "Harmonograph",
            "Hex Mirror", "Chroma Spiral", "Solar Flare", "Deep Space")
        put("Spectrum & Bars", "Spectrum Bars", "Mirror Bars", "Valley Bars", "Radial Spectrum", "Pulse Bars", "Grid EQ", "Spike Ring")
        put("Waves & Ribbons", "Spectrum Wave", "Mirror Wave", "Neon Ribbon", "Silk Waves")
        put("3D Terrain & Shapes", "Valley Ridges", "Wire Terrain", "Dot Terrain", "Ripple Plane", "Synthwave Valley", "Wire Sphere")
        put("Tunnels", "Spectrum Tunnel", "Warp Tunnel", "Hex Tunnel")
        put("Particles", "Galaxy", "Starfield", "Swarm", "Pulse Shell")
        put("Smoke & Fluid", "Ink in Water", "Ink Smoke", "Liquid Light", "Ink Vortex")
        put("Raymarched 3D", "Mandelbulb", "Metaball Bloom", "Crystal Cathedral")
        put("3D Bar Grids", "Bar Grid", "Neon Bar Grid", "Bar Towers")
        put("Ribbons & Dots", "Ribbon Flux", "Ribbon Helix", "Dot Ocean")
        put("Wire Mountains & Planes", "Peak Wire", "Ocean Waves", "Radial Plane", "Radial Bloom", "Plane & Terrain")
        put("Spectrum & Bars", "LED Spectrum", "LED Mirror", "LED Neon")
        put("Hex & Tiles", "Hex Pastel", "Hex Storm", "Hex Sweep", "Neon Tiles", "Light Rain")
        put("Neon Tunnels & Mazes", "Neon Frames", "Frame Gate", "Neon Maze", "Maze Bloom", "Circuit Maze")
        put("Fire & Halos", "Vortex Halo", "Halo Pulse")
        put("Pulse & Tunnels", "Cherenkov Pulse", "Pulse Ripples", "Neon Corridor", "Triangle Gate", "Hex Portal")
        put("LED & Glass", "Glass Ring", "Dot Ripples", "LED Tunnel", "Ring Bars", "Dot Equaliser", "Horizon Spectrum")
        put("Smoke & Light Trails", "Smoke Bars", "Smoke Crown", "Smoke Bloom", "Smoke Nova", "Circuit Trails", "Wire Bundle", "Neon Ribbons")
        put("Smoke & Spikes", "Neon Smoke", "Circuit Flow", "Ink Aurora", "Crystal Spikes")
        put("Radial & Beads", "Radial Disc", "Spectrum Crown", "Radial Halo", "Bead Terrain", "Bead Ocean")
        m
    }
    fun of(p: Preset): String = map[p.name] ?: guess(p.name)
    private fun guess(n: String): String {
        val l = n.lowercase()
        return when {
            "tunnel" in l -> "Tunnels"
            "spectrum" in l || "bars" in l || "analy" in l || "equal" in l -> "Spectrum & Bars"
            "ridge" in l || "valley" in l || "mountain" in l -> "3D Terrain & Shapes"
            "wave" in l || "scope" in l || "ribbon" in l -> "Waves & Ribbons"
            "smoke" in l || "ink" in l || "fluid" in l -> "Smoke & Fluid"
            else -> "Classic"
        }
    }
}

/** Cosine palettes: colour(t) = a + b*cos(2*pi*(c*t + d)). */
class Palette(val name: String, val v: FloatArray)  // a(3) b(3) c(3) d(3)

object Palettes {
    private fun pal(name: String, vararg f: Float) = Palette(name, f)
    val all: List<Palette> = listOf(
        pal("Spectrum",    0.5f, 0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  1f, 1f, 1f,       0.00f, 0.33f, 0.67f),
        pal("Pastel",      0.6f, 0.6f, 0.6f,  0.4f, 0.4f, 0.4f,  1f, 1f, 1f,       0.00f, 0.10f, 0.20f),
        pal("Sunset",      0.5f, 0.4f, 0.4f,  0.5f, 0.4f, 0.4f,  1f, 1f, 0.5f,     0.80f, 0.90f, 0.30f),
        pal("Inferno",     0.5f, 0.35f, 0.3f, 0.5f, 0.4f, 0.3f,  1f, 0.7f, 0.4f,   0.00f, 0.15f, 0.20f),
        pal("Toxic",       0.4f, 0.5f, 0.3f,  0.4f, 0.5f, 0.3f,  1f, 0.8f, 1.2f,   0.20f, 0.00f, 0.55f),
        pal("Deep Ocean",  0.2f, 0.4f, 0.5f,  0.3f, 0.4f, 0.4f,  0.8f, 0.9f, 1f,   0.55f, 0.60f, 0.70f),
        pal("Neon Night",  0.5f, 0.3f, 0.6f,  0.5f, 0.5f, 0.5f,  1f, 1f, 1f,       0.80f, 0.55f, 0.30f),
        pal("Candy",       0.7f, 0.5f, 0.6f,  0.3f, 0.5f, 0.4f,  1f, 1f, 1f,       0.00f, 0.25f, 0.45f),
        pal("Ice",         0.5f, 0.6f, 0.7f,  0.4f, 0.4f, 0.3f,  1f, 0.8f, 0.6f,   0.50f, 0.60f, 0.65f),
        pal("Gold",        0.55f, 0.45f, 0.25f, 0.45f, 0.4f, 0.25f, 1f, 1f, 0.8f,  0.00f, 0.10f, 0.30f),
        pal("Aurora",      0.3f, 0.5f, 0.45f, 0.4f, 0.5f, 0.45f, 1f, 1f, 1f,       0.35f, 0.10f, 0.55f),
        pal("Magma",       0.45f, 0.25f, 0.3f, 0.5f, 0.3f, 0.4f, 1f, 1f, 0.6f,     0.95f, 0.05f, 0.40f),
        pal("Neon Ribbon", 0.68f, 0.48f, 0.47f,  0.56f, 0.28f, 0.31f,  1f, 1f, 1f,  0.49f, 0.10f, -0.10f),
        pal("Cyan Glow", 0.19f, 0.59f, 0.83f,  0.36f, 0.46f, 0.28f,  1f, 1f, 1f,  0.33f, 0.43f, 0.48f),
        pal("Ultraviolet", 0.52f, 0.37f, 0.83f,  0.49f, 0.36f, 0.28f,  1f, 1f, 1f,  0.41f, 0.37f, 0.48f),
        pal("Mesh Neon", 0.60f, 0.56f, 0.85f,  0.44f, 0.39f, 0.28f,  1f, 1f, 1f,  0.40f, 0.08f, -0.03f),
        pal("Amber Wire", 0.85f, 0.60f, 0.17f,  0.30f, 0.34f, 0.28f,  1f, 1f, 1f,  0.50f, 0.43f, 0.34f),
        pal("Synthwave", 0.74f, 0.40f, 0.54f,  0.49f, 0.53f, 0.28f,  1f, 1f, 1f,  0.50f, 0.36f, -0.21f),
        pal("Pearl", 0.78f, 0.70f, 0.83f,  0.28f, 0.28f, 0.31f,  1f, 1f, 1f,  -0.16f, -0.40f, 0.49f),
        pal("Prism Mesh", 0.43f, 0.57f, 0.52f,  0.57f, 0.37f, 0.52f,  1f, 1f, 1f,  0.35f, -0.38f, -0.06f),
        pal("Plasma Ink", 0.42f, 0.22f, 0.55f,  0.55f, 0.45f, 0.45f,  1f, 1f, 1f,  0.85f, 0.15f, 0.45f),
        pal("Liquid Metal", 0.62f, 0.68f, 0.78f,  0.36f, 0.34f, 0.36f,  1f, 1f, 1f,  0.55f, 0.62f, 0.72f),
        pal("LED Meter", 0.43f, 0.12f, 0.27f,  0.64f, 0.92f, 0.18f,  0.44f, 0.33f, 0.13f,  0.65f, -0.08f, 0.41f),
        pal("Candy Pastel", 0.76f, 0.76f, 0.76f,  0.22f, 0.22f, 0.22f,  1f, 1f, 1f,  0.00f, 0.33f, 0.67f),
        pal("Neon Arcade", 0.55f, 0.50f, 0.60f,  0.45f, 0.50f, 0.40f,  1f, 1f, 1f,  0.85f, 0.55f, 0.20f),
        pal("Cherenkov Blue", 0.20f, 0.45f, 0.85f,  0.20f, 0.25f, 0.15f,  0.5f, 0.5f, 0.5f,  0.00f, 0.10f, 0.20f),
        pal("Hot Corridor", 0.65f, 0.35f, 0.25f,  0.35f, 0.30f, 0.30f,  0.6f, 0.6f, 0.6f,  0.00f, 0.10f, 0.25f),
        pal("Teal Crystal", 0.15f, 0.60f, 0.60f,  0.10f, 0.35f, 0.30f,  0.4f, 0.4f, 0.4f,  0.00f, 0.05f, 0.10f),
        pal("Neon Triangle", 0.50f, 0.40f, 0.50f,  0.50f, 0.50f, 0.50f,  0.5f, 0.5f, 0.5f,  0.95f, 0.45f, 0.55f),
        pal("Bead Heat", 0.5f, 0.5f, 0.5f,  0.5f, 0.5f, 0.5f,  1f, 0.8f, 0.5f,  0.35f, 0.00f, -0.12f),
        pal("Sun & Sea", 0.57f, 0.09f, 0.55f,  0.63f, 0.91f, 0.55f,  0.74f, 0.41f, 0.74f,  0.39f, 0.77f, -0.12f)
    )

    /** First colour stops, for drawing preview swatches in the UI (RGB 0..1). */
    fun sample(p: Palette, t: Float): FloatArray {
        val o = FloatArray(3)
        for (i in 0 until 3) {
            val v = p.v[i] + p.v[3 + i] * cos(6.2831853f * (p.v[6 + i] * t + p.v[9 + i]))
            o[i] = v.coerceIn(0f, 1f)
        }
        return o
    }
}

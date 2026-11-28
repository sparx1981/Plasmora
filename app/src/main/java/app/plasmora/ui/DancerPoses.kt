package app.plasmora.ui

import androidx.compose.ui.geometry.Offset

// ---------------------------------------------------------------- dancer poses
// Pose vector: 0 bob, 1 lean, 2 armR, 3 elbowR, 4 armL, 5 elbowL, 6 thighR, 7 kneeR, 8 thighL, 9 kneeL,
//              10 head tilt, 11 shoulder width scale, 12 hop (up), 13 sway x, 14 whole-body rotation
// Eight dancers, each with eight moves. Pure maths (no Android) so it can be unit-rendered.

// ---------------------------------------------------------------- dancer
// Pose vector: 0 bob, 1 lean, 2 armR, 3 elbowR, 4 armL, 5 elbowL, 6 thighR, 7 kneeR, 8 thighL, 9 kneeL,
//              10 head tilt, 11 shoulder width scale, 12 hop (up), 13 sway x
internal fun dancePose(move: Int, p: Float): FloatArray {
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
        7 -> { // shuffle: feet slide side to side, arms pump
            o[6] = 0.35f * s; o[7] = 0.1f; o[8] = 0.35f * s; o[9] = -0.1f
            o[2] = 0.9f + 0.4f * s; o[3] = 1.4f; o[4] = -(0.9f - 0.4f * s); o[5] = -1.4f
            o[13] = 2f * s; o[0] = 0.12f * kotlin.math.abs(s); o[1] = 0.1f * s; o[10] = 0.2f * s
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

internal fun smooth(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }

/** Extra poses for dancers 1..4 (dancer 0 uses dancePose). o[14] = whole-body rotation. */
internal fun dancePose2(style: Int, move: Int, p: Float): FloatArray {
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
            4 -> { // windmill: spinning on the back, legs wide
                o[14] = (p * 1.2f) % 6.28318f; o[2] = 1.57f; o[4] = -1.57f; o[3] = 0.1f; o[5] = -0.1f
                o[6] = 0.7f; o[8] = -0.7f; o[7] = 0.15f * s; o[9] = -0.15f * s }
            5 -> { // freeze: body flat, one arm propping
                o[14] = 1.45f + 0.04f * s; o[2] = 0.2f; o[3] = 0f; o[4] = -0.35f; o[5] = -0.1f
                o[6] = 0.12f; o[8] = -0.12f; o[7] = 0.05f; o[9] = -0.05f; o[13] = 0.5f * s }
            6 -> { // toprock: alternating kicks, arms swinging
                val a = kotlin.math.max(0f, s); val b = kotlin.math.max(0f, -s)
                o[6] = 0.2f + 0.9f * a; o[7] = -0.4f * a; o[8] = -(0.2f + 0.9f * b); o[9] = 0.4f * b
                o[2] = 0.7f - 0.8f * s; o[3] = 1.3f; o[4] = -(0.7f + 0.8f * s); o[5] = -1.3f
                o[12] = 0.2f * kotlin.math.abs(c); o[13] = 1.5f * c; o[1] = 0.1f * s }
            7 -> { // headspin: upside down, legs scissoring
                o[14] = 3.14159f + 0.3f * s; o[2] = 1.0f; o[4] = -1.0f; o[3] = 0.1f; o[5] = -0.1f
                o[6] = 0.6f + 0.5f * kotlin.math.sin(p * 2f); o[8] = -(0.6f + 0.5f * kotlin.math.sin(p * 2f + 3.14f)); o[7] = 0.1f; o[9] = -0.1f }
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
                4 -> { o[2] = 1.57f; o[4] = -1.57f   // arm wave, snapping elbows
                    o[3] = floatArrayOf(0.9f, 0f, -0.9f, 0f)[st]; o[5] = -floatArrayOf(-0.9f, 0f, 0.9f, 0f)[st]
                    o[10] = floatArrayOf(0.3f, 0f, -0.3f, 0f)[st] }
                5 -> { val ev = st and 1 == 0   // pistons: arms pump up and down
                    o[2] = if (ev) 3.0f else 0.3f; o[3] = 0f; o[4] = -(if (ev) 0.3f else 3.0f); o[5] = 0f
                    o[0] = if (ev) 0f else 0.15f; o[6] = 0.08f; o[8] = -0.08f }
                6 -> { o[2] = 0.9f; o[3] = -1.8f; o[4] = -0.9f; o[5] = 1.8f   // chicken elbows, head snaps
                    o[1] = floatArrayOf(0.25f, 0f, -0.25f, 0f)[st]; o[10] = floatArrayOf(0.45f, 0f, -0.45f, 0f)[st] }
                7 -> { val ev = st and 1 == 0   // march: raised knees, right-angle arms
                    o[6] = if (ev) 1.3f else 0f; o[7] = if (ev) -1.3f else 0f
                    o[8] = if (ev) 0f else -1.3f; o[9] = if (ev) 0f else 1.3f
                    o[2] = if (ev) -0.5f else 0.5f; o[3] = 1.57f; o[4] = if (ev) -0.5f else 0.5f; o[5] = -1.57f; o[0] = 0.05f }
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
                4 -> { // plie: slow dip on tiptoe, arms rounded low
                    o[6] = 0.6f * k; o[7] = -0.6f * k; o[8] = -0.6f * k; o[9] = 0.6f * k
                    o[2] = 0.5f; o[3] = 0.9f; o[4] = -0.5f; o[5] = -0.9f; o[10] = 0.1f * s }
                5 -> { // arabesque: one leg lifted out, arms spread
                    o[1] = -0.3f; o[6] = 1.7f + 0.1f * s; o[7] = 0f; o[8] = -0.05f
                    o[2] = 2.6f; o[3] = 0.1f; o[4] = -1.2f; o[5] = -0.1f; o[10] = 0.3f * s }
                6 -> { // jete: big leap, legs split, arms in a V
                    o[12] = 1.4f * kotlin.math.abs(s); o[6] = 1.2f; o[8] = -1.2f; o[7] = 0f; o[9] = 0f
                    o[2] = 2.5f; o[4] = -2.5f; o[3] = 0.1f; o[5] = -0.1f }
                7 -> { // port de bras: slow flowing arms overhead
                    o[2] = 2.0f + 0.9f * s; o[3] = 0.6f * kotlin.math.sin(p + 1f)
                    o[4] = -(2.0f - 0.9f * s); o[5] = -0.6f * kotlin.math.sin(p + 2f)
                    o[1] = 0.2f * s; o[13] = 1.5f * s; o[10] = 0.2f * s }
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
                4 -> { val t = kotlin.math.sin(p * 2f)   // shoulder shrug, hands to chest
                    o[2] = 0.5f; o[3] = 1.9f; o[4] = -0.5f; o[5] = -1.9f
                    o[1] = 0.15f * t; o[10] = 0.3f * t; o[0] = 0.1f * kotlin.math.abs(t); o[6] = 0.2f; o[8] = -0.2f }
                5 -> { // sprinkler: one arm out, one hand behind the head
                    o[2] = 1.57f + 0.25f * s; o[3] = 0.1f; o[4] = -2.4f; o[5] = -1.8f
                    o[6] = 0.15f; o[7] = 0.4f * k; o[8] = -0.15f; o[9] = -0.4f * (1f - k); o[13] = 1.5f * s }
                6 -> { val t = kotlin.math.sin(p * 2f)   // twist: low squat, arms swinging
                    o[6] = 0.5f; o[7] = -0.5f; o[8] = -0.5f; o[9] = 0.5f
                    o[2] = 0.9f + 0.7f * t; o[3] = 0.8f; o[4] = -(0.9f - 0.7f * t); o[5] = -0.8f; o[13] = 1.5f * t; o[11] = 0.6f + 0.4f * kotlin.math.abs(t) }
                7 -> { val a = kotlin.math.max(0f, s); val b = kotlin.math.max(0f, -s)   // kick-step with clap overhead
                    o[6] = 0.2f + 1.2f * a; o[7] = -0.4f * a; o[8] = -(0.2f + 1.2f * b); o[9] = 0.4f * b
                    o[2] = 2.6f; o[3] = 0.3f; o[4] = -2.6f; o[5] = -0.3f; o[12] = 0.25f * kotlin.math.abs(c) }
                else -> { o[2] = p; o[3] = 0.3f; o[4] = -(p + 3.14f); o[5] = -0.3f; o[6] = 0.15f; o[7] = 0.5f * k; o[8] = -0.15f; o[9] = -0.5f * (1f - k); o[0] = 0.15f * kotlin.math.abs(s); o[1] = 0.05f * s }
            }
        }
    }
    return o
}

/** Dancers 5..7: rocker, latin, country. */
internal fun dancePose3(style: Int, move: Int, p: Float): FloatArray {
    val s = kotlin.math.sin(p); val c = kotlin.math.cos(p)
    val k = 0.5f + 0.5f * s
    val o = FloatArray(16); o[11] = 1f
    val s2 = kotlin.math.sin(p * 2f); val c2 = kotlin.math.cos(p * 2f)
    val a = kotlin.math.max(0f, s); val b = kotlin.math.max(0f, -s)
    when (style) {
        5 -> when (move) { // rocker
            0 -> { o[10] = 1.7f * s2; o[1] = 0.3f * kotlin.math.abs(s2); o[2] = 0.5f; o[3] = 1.9f; o[4] = -0.5f; o[5] = -1.9f
                o[6] = 0.4f; o[7] = -0.1f; o[8] = -0.4f; o[9] = 0.1f; o[0] = 0.08f * kotlin.math.abs(s2) }
            1 -> { o[1] = -0.2f; o[2] = 0.4f + 0.3f * s2; o[3] = 1.2f; o[4] = -1.6f; o[5] = 0.3f
                o[6] = 0.5f; o[7] = -0.3f; o[8] = -0.5f; o[9] = 0.3f; o[10] = 0.3f * s }
            2 -> { o[2] = 2.7f + 0.3f * s2; o[3] = 0f; o[4] = -0.7f; o[5] = 1.5f
                o[6] = 0.3f; o[8] = -0.3f; o[12] = 0.3f * kotlin.math.abs(s); o[10] = 0.2f * s }
            3 -> { o[12] = 1.0f * k * k; o[2] = 2.9f; o[4] = -2.9f; o[3] = 0.1f; o[5] = -0.1f
                o[6] = 0.4f + 0.3f * k; o[7] = -0.8f * k; o[8] = -(0.4f + 0.3f * k); o[9] = 0.8f * k }
            4 -> { o[2] = (p * 2f) % 6.28318f; o[3] = 0.2f; o[4] = -1.6f; o[5] = 0.3f
                o[6] = 0.5f; o[7] = -0.3f; o[8] = -0.5f; o[9] = 0.3f; o[1] = 0.1f * s; o[10] = 0.5f * s }
            5 -> { o[6] = 1.3f; o[7] = -2.4f; o[8] = -0.4f; o[9] = 0.2f; o[1] = -0.3f
                o[2] = 2.8f; o[4] = -2.8f; o[3] = 0.1f; o[5] = -0.1f; o[10] = -0.4f + 0.1f * s }
            6 -> { o[6] = 1.1f * a; o[7] = -1.1f * a; o[8] = -1.1f * b; o[9] = 1.1f * b
                o[2] = 1.0f; o[3] = 1.6f; o[4] = -1.0f; o[5] = -1.6f; o[0] = 0.1f * kotlin.math.abs(s); o[10] = 0.3f * s }
            else -> { o[10] = 2.0f * s; o[13] = 2.5f * kotlin.math.sin(p * 0.5f)
                o[2] = 0.3f + 0.4f * s; o[3] = 0.5f; o[4] = -(0.3f - 0.4f * s); o[5] = -0.5f
                o[6] = 0.45f; o[8] = -0.45f; o[7] = -0.1f; o[9] = 0.1f; o[1] = 0.12f * s }
        }
        6 -> when (move) { // latin
            0 -> { o[6] = 0.4f * s; o[7] = 0.4f * a; o[8] = 0.4f * s; o[9] = -0.4f * b
                o[13] = 2f * s; o[2] = 0.7f; o[3] = 1.2f; o[4] = -0.7f; o[5] = -1.2f; o[0] = 0.08f; o[1] = 0.08f * s }
            1 -> { val q = kotlin.math.sin(p * 0.5f)
                o[13] = 3f * q; o[2] = 1.0f + 1.6f * (0.5f + 0.5f * q); o[3] = 0.2f; o[4] = -0.5f; o[5] = -0.2f
                o[6] = 0.2f; o[7] = 0.3f * k; o[8] = -0.2f; o[9] = -0.3f * (1f - k); o[1] = 0.12f * q }
            2 -> { o[11] = kotlin.math.cos(p * 0.8f); o[2] = 2.9f; o[3] = 0.1f; o[4] = -0.6f; o[5] = 1.5f
                o[12] = 0.3f * kotlin.math.abs(kotlin.math.sin(p * 0.8f)); o[6] = 0.1f; o[8] = -0.1f }
            3 -> { o[6] = -0.5f * k; o[7] = 0.2f; o[8] = -0.2f; o[2] = 1.3f; o[3] = 0.2f; o[4] = -2.2f; o[5] = -0.3f
                o[13] = 1.5f * s; o[1] = 0.1f * s }
            4 -> { o[10] = 0.5f * kotlin.math.sin(p * 4f); o[13] = 1.0f * kotlin.math.sin(p * 4f)
                o[2] = 2.0f; o[3] = 1.2f; o[4] = -2.0f; o[5] = -1.2f; o[6] = 0.25f; o[7] = -0.25f; o[8] = -0.25f; o[9] = 0.25f
                o[0] = 0.06f * kotlin.math.abs(kotlin.math.sin(p * 4f)) }
            5 -> { o[13] = 3f * s; o[6] = 1.0f * a; o[7] = -1.0f * a; o[8] = -1.0f * b; o[9] = 1.0f * b
                o[2] = 2.2f; o[3] = 0.3f; o[4] = -1.0f; o[5] = -0.6f; o[1] = -0.25f * s }
            6 -> { val t = kotlin.math.sin(p * 2f); val ta = kotlin.math.max(0f, t); val tb = kotlin.math.max(0f, -t)
                o[6] = 0.2f + 0.9f * ta; o[7] = -0.5f * ta; o[8] = -(0.2f + 0.9f * tb); o[9] = 0.5f * tb
                o[2] = 0.8f; o[3] = 0.9f; o[4] = -0.8f; o[5] = -0.9f; o[12] = 0.15f * kotlin.math.abs(t) }
            else -> { o[2] = 2.6f + 0.5f * s2; o[3] = 0.7f * kotlin.math.sin(p * 2f + 1f)
                o[4] = -(2.6f + 0.5f * kotlin.math.sin(p * 2f + 2f)); o[5] = -0.7f * kotlin.math.sin(p * 2f + 3f)
                o[13] = 2.5f * kotlin.math.sin(p * 0.5f); o[6] = 0.15f * s; o[8] = 0.15f * s; o[0] = 0.08f }
        }
        else -> when (move) { // country
            0 -> { o[6] = 0.15f + 0.5f * a; o[7] = -0.1f * a; o[8] = -0.15f; o[2] = 0.7f; o[3] = -1.3f; o[4] = -0.7f; o[5] = 1.3f
                o[10] = 0.1f * s }
            1 -> { o[13] = 3f * kotlin.math.sin(p * 0.5f); o[6] = 0.5f * s; o[8] = 0.5f * s; o[7] = 0.15f; o[9] = -0.15f
                o[2] = 0.7f; o[3] = 1.0f; o[4] = -0.7f; o[5] = -1.0f; o[0] = 0.06f }
            2 -> { o[12] = 0.6f * kotlin.math.abs(s); o[6] = 0.2f; o[8] = -0.1f; o[9] = -1.4f * a
                o[2] = 0.5f + 0.3f * s; o[3] = 0.8f; o[4] = -(0.5f - 0.3f * s); o[5] = -0.8f; o[10] = 0.2f * s }
            3 -> { o[2] = 2.4f + 0.4f * s2; o[3] = 0.9f * c2; o[4] = -0.7f; o[5] = 1.3f
                o[6] = 0.2f; o[8] = -0.2f; o[7] = 0.1f * k; o[9] = -0.1f * k; o[10] = 0.2f * s2; o[0] = 0.08f * kotlin.math.abs(s2) }
            4 -> { o[6] = 0.8f * a; o[7] = -0.8f * a; o[8] = -0.8f * b; o[9] = 0.8f * b
                o[2] = 2.2f - 0.5f * kotlin.math.abs(c2); o[3] = 1.0f; o[4] = -(2.2f - 0.5f * kotlin.math.abs(c2)); o[5] = -1.0f
                o[0] = 0.1f * kotlin.math.abs(s) }
            5 -> { o[11] = kotlin.math.cos(p * 0.7f); o[13] = 2f * kotlin.math.sin(p * 0.35f)
                o[2] = 0.8f; o[3] = 1.8f; o[4] = -0.8f; o[5] = -1.8f; o[12] = 0.3f * kotlin.math.abs(kotlin.math.sin(p * 0.7f)); o[6] = 0.1f; o[8] = -0.1f }
            6 -> { o[2] = 2.6f; o[3] = -1.0f + 0.4f * s; o[4] = -(0.5f + 0.4f * s); o[5] = -0.8f
                o[6] = 0.2f; o[7] = 0.3f * k; o[8] = -0.2f; o[9] = -0.3f * (1f - k); o[0] = 0.2f * kotlin.math.abs(s); o[10] = 0.2f * s }
            else -> { o[13] = 2.5f * s; o[6] = 0.35f * a; o[7] = -0.2f * a; o[8] = -0.35f * b; o[9] = 0.2f * b
                o[2] = 0.4f + 0.5f * s; o[3] = 1.2f; o[4] = -(0.4f - 0.5f * s); o[5] = -1.2f; o[0] = 0.08f * kotlin.math.abs(c) }
        }
    }
    return o
}

object DancerPoses {
    const val STYLES = 8
    const val MOVES = 8
    /** 0 groove, 1 breaker, 2 robot, 3 ballerina, 4 disco, 5 rocker, 6 latin, 7 country */
    val NAMES = arrayOf("Groove", "Breaker", "Robot", "Ballerina", "Disco", "Rocker", "Latin", "Country")
    /** How energetic each move is, 0 (calm) .. 1 (wild). The dancer picks moves to suit the music's energy. */
    val INTENSITY = arrayOf(
        floatArrayOf(0.35f, 0.45f, 0.80f, 0.60f, 0.90f, 0.70f, 0.10f, 0.50f),
        floatArrayOf(0.40f, 0.85f, 0.90f, 0.70f, 1.00f, 0.20f, 0.30f, 0.95f),
        floatArrayOf(0.40f, 0.30f, 0.50f, 0.35f, 0.20f, 0.70f, 0.10f, 0.60f),
        floatArrayOf(0.45f, 0.30f, 0.25f, 0.85f, 0.10f, 0.35f, 0.95f, 0.05f),
        floatArrayOf(0.50f, 0.75f, 0.40f, 0.60f, 0.15f, 0.30f, 0.80f, 0.95f),
        floatArrayOf(0.80f, 0.45f, 0.50f, 1.00f, 0.90f, 0.15f, 0.60f, 0.20f),
        floatArrayOf(0.40f, 0.15f, 0.70f, 0.30f, 0.85f, 0.10f, 0.90f, 0.55f),
        floatArrayOf(0.15f, 0.40f, 0.80f, 0.60f, 0.90f, 0.50f, 0.20f, 0.70f)
    )

    fun pose(style: Int, move: Int, p: Float): FloatArray = when (style) {
        0 -> dancePose(move, p).copyOf(16)
        in 1..4 -> dancePose2(style, move, p)
        else -> dancePose3(style, move, p)
    }

    /** Joint positions (hip at the origin) after the whole-body rotation. Order: hip, hipR, hipL, kneeR, kneeL, footR,
     *  footL, shoulder-centre, shoulderR, shoulderL, elbowR, elbowL, handR, handL, head. */
    fun skeleton(q: FloatArray, u: Float): List<Offset> {
        fun dir(ang: Float) = Offset(kotlin.math.sin(ang), kotlin.math.cos(ang))
        val thigh = 6.2f * u; val shin = 6.2f * u; val torso = 9f * u; val upper = 5f * u; val fore = 5f * u
        val sw = q[11]
        val up = Offset(kotlin.math.sin(q[1]), -kotlin.math.cos(q[1]))
        val shoulder = up * torso
        val hipR = Offset(1.6f * u * sw, 0f); val hipL = Offset(-1.6f * u * sw, 0f)
        val kneeR = hipR + dir(q[6]) * thigh; val footR = kneeR + dir(q[6] + q[7]) * shin
        val kneeL = hipL + dir(q[8]) * thigh; val footL = kneeL + dir(q[8] + q[9]) * shin
        val shR = shoulder + Offset(2.4f * u * sw, 0.6f * u); val shL = shoulder + Offset(-2.4f * u * sw, 0.6f * u)
        val elR = shR + dir(q[2]) * upper; val haR = elR + dir(q[2] + q[3]) * fore
        val elL = shL + dir(q[4]) * upper; val haL = elL + dir(q[4] + q[5]) * fore
        val headC = shoulder + up * (3.6f * u) + Offset(q[10] * 1.5f * u, 0f)
        val all = listOf(Offset.Zero, hipR, hipL, kneeR, kneeL, footR, footL, shoulder, shR, shL, elR, elL, haR, haL, headC)
        val piv = shoulder * 0.5f
        val cs = kotlin.math.cos(q[14]); val sn = kotlin.math.sin(q[14])
        return all.map { o -> val x = o.x - piv.x; val y = o.y - piv.y; Offset(cs * x - sn * y + piv.x, sn * x + cs * y + piv.y) }
    }
}

/**
 * Chooses which move a dancer does next. A move lasts four dance cycles (eight beats). The next move is picked at
 * random but weighted towards moves whose intensity matches the music's energy, never repeating the last one.
 */
class DancerMoves(seed: Int = 1) {
    private val rnd = kotlin.random.Random(seed)
    private var seg = Int.MIN_VALUE
    var style = 0
    var cur = 0; var next = 0
    var blend = 0f
    var ready = false

    fun reset(style: Int, energy: Float) { this.style = style; ready = true; seg = Int.MIN_VALUE; cur = pick(energy, -1, -1); next = pick(energy, cur, -1) }

    /** Advance with the dance clock (in cycles) and the current energy 0..1. */
    fun update(cycles: Float, energy: Float) {
        val si = kotlin.math.floor(cycles / 4f).toInt()
        if (seg == Int.MIN_VALUE) seg = si
        var guard = 0
        while (seg < si && guard++ < 4) { seg++; cur = next; next = pick(energy, cur, -1) }
        if (seg < si) seg = si
        val fr = cycles / 4f - si
        blend = smooth((fr - 0.9f) / 0.1f)
    }

    private fun pick(energy: Float, avoid1: Int, avoid2: Int): Int {
        val inten = DancerPoses.INTENSITY[style]
        val w = FloatArray(DancerPoses.MOVES) { m ->
            if (m == avoid1 || m == avoid2) 0f else {
                val d = inten[m] - energy
                0.12f + kotlin.math.exp(-(d * d) / (2f * 0.2f * 0.2f))
            }
        }
        var r = rnd.nextFloat() * w.sum()
        for (m in w.indices) { r -= w[m]; if (r <= 0f && w[m] > 0f) return m }
        return w.indices.first { w[it] > 0f }
    }
}

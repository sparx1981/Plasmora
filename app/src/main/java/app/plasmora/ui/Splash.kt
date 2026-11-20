package app.plasmora.ui

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sin

private const val TAU = (2.0 * PI).toFloat()
private const val SPLASH_SEC = 4.1f
private const val FADE_SEC = 0.45f
private val BG = Color(0xFF0A0618)

private fun hueCol(t: Float) = Color.hsl(190f + 110f * (0.5f + 0.5f * cos(TAU * t)), 1f, 0.62f)

/** Opening animation: a waveform draws across the screen, then curls into the Plasmora ring. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var t0 = -1L
        while (true) {
            val ms = withInfiniteAnimationFrameMillis { it }
            if (t0 < 0) t0 = ms
            t = (ms - t0) / 1000f
            if (t >= SPLASH_SEC + FADE_SEC) { onDone(); break }
        }
    }
    val fade = if (t > SPLASH_SEC) 1f - ((t - SPLASH_SEC) / FADE_SEC).coerceIn(0f, 1f) else 1f
    val L = (t / 3.6f).coerceIn(0f, 1f)
    val cu = ((L - 0.45f) * 3f).coerceIn(0f, 1f)
    val ce = cu * cu * (3f - 2f * cu)

    Box(Modifier.fillMaxSize().background(BG.copy(alpha = fade)).alpha(fade)) {
        Canvas(Modifier.fillMaxSize()) { drawSplash(t, L, ce) }
        Text(
            "PLASMORA", color = Color(0xFFE8E4FF), fontSize = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 6.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp).alpha(ce)
        )
    }
}

private fun DrawScope.drawSplash(t: Float, L: Float, ce: Float) {
    val u = min(size.width / 300f, size.height / 400f).coerceAtLeast(0.5f) * 1.25f
    val cx = size.width / 2f
    val cy = size.height * 0.41f
    val wp = min(1f, L * 2.2f)
    val beat = 0.5f + 0.5f * cos(t * TAU * 1.6f)

    // the waveform, turning into the ring
    val n = 120
    for (i in 0..n) {
        val f = i / n.toFloat()
        if (f > wp) break
        val wx = cx + (f - 0.5f) * 260f * u
        val wy = cy + sin(f * TAU * 3f + t * 4f) * (40f - 25f * ce) * sin(f * PI.toFloat()) * u
        val ang = f * TAU - PI.toFloat() / 2f
        val rx = cx + cos(ang) * 55f * u
        val ry = cy + sin(ang) * 55f * u
        val x = wx * (1f - ce) + rx * ce
        val y = wy * (1f - ce) + ry * ce
        drawCircle(hueCol(f), 3.2f * u, Offset(x, y), blendMode = BlendMode.Plus)
    }

    // the logo fades in once the wave has closed
    val la = max(0f, (ce - 0.5f) * 2f)
    if (la > 0f) {
        val r = 55f * u
        val rot = t * 0.4f
        val seg = 90
        for (i in 0 until seg) {
            val a0 = rot + i / seg.toFloat() * TAU
            drawArc(
                color = hueCol(i / seg.toFloat()).copy(alpha = la),
                startAngle = Math.toDegrees(a0.toDouble()).toFloat(),
                sweepAngle = 360f / seg * 1.6f, useCenter = false,
                topLeft = Offset(cx - r, cy - r), size = Size(2 * r, 2 * r),
                style = Stroke(width = r * 0.42f)
            )
        }
        drawCircle(BG.copy(alpha = la), r * 0.57f, Offset(cx, cy))
        drawCircle(Color(0xFFFF4F9A).copy(alpha = la), r * 0.27f, Offset(cx, cy))
        drawCircle(
            Color(0xFFFF4F9A).copy(alpha = ce * 0.6f * beat), r * 0.27f + 8f * u * beat, Offset(cx, cy),
            style = Stroke(width = 2f * u)
        )
    }
}

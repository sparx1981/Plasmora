package app.plasmora.ui

import android.Manifest
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** First-launch intro: what Plasmora does, how to feed it audio from another app, and the two permissions. */
@Composable
fun Onboarding(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val last = 2
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0A0618)).clickable(enabled = false) { }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (step < last) TextButton(onClick = onDone) { Text("Skip") } else Spacer(Modifier.height(48.dp))
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Canvas(Modifier.size(110.dp)) {
                    val c = Offset(size.width / 2, size.height / 2)
                    drawCircle(
                        Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color(0xFFB14CFF), Color(0xFF00E5FF)), c),
                        radius = size.width * 0.36f, center = c, style = Stroke(size.width * 0.12f)
                    )
                    drawCircle(Color(0xFFFF4F9A), radius = size.width * 0.075f, center = c)
                }
                Spacer(Modifier.height(28.dp))
                when (step) {
                    0 -> Page(
                        "Welcome to Plasmora",
                        "Plasmora turns the music you are playing into a live light show. It works with YouTube Music, Spotify and most other music apps."
                    )
                    1 -> Page(
                        "How to use it",
                        "1.  Start your music in another app.\n\n" +
                        "2.  Open Plasmora and choose a sound source. Android will ask to share audio. Tap Start now. Plasmora only listens to the sound. It never records your screen.\n\n" +
                        "3.  Come back to Plasmora to watch the show. Your music keeps playing in the background."
                    )
                    else -> {
                        Page(
                            "Two quick permissions",
                            "Microphone lets Plasmora react to sound when it can't capture an app directly.\n\n" +
                            "Notification access lets Plasmora show the song title and album art, and control playback."
                        )
                        Spacer(Modifier.height(20.dp))
                        OutlinedButton(onClick = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }) { Text("Allow microphone") }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            try { ctx.startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Throwable) { }
                        }) { Text("Allow notification access") }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                for (i in 0..last) {
                    Box(
                        Modifier.padding(4.dp).size(8.dp).clip(CircleShape)
                            .background(if (i == step) Color(0xFF00E5FF) else Color(0xFF3A2B5C))
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Back") } else Spacer(Modifier.size(1.dp))
                Button(onClick = { if (step < last) step++ else onDone() }) { Text(if (step < last) "Next" else "Get started") }
            }
        }
    }
}

@Composable
private fun Page(title: String, body: String) {
    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))
    Text(body, fontSize = 16.sp, lineHeight = 23.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
}

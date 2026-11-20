package app.plasmora.media

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.plasmora.MainActivity
import app.plasmora.audio.AudioHub
import app.plasmora.audio.CaptureService

/** Quick Settings tile: one tap to (re)start listening, or to stop. */
class StartTileService : TileService() {
    private fun listening(): Boolean = AudioHub.status.value.let { it.captureRunning || it.micRunning }

    override fun onStartListening() { refreshTile() }

    private fun refreshTile() {
        val t = qsTile ?: return
        val on = listening()
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Plasmora"
        if (Build.VERSION.SDK_INT >= 29) t.subtitle = if (on) "Listening · tap to stop" else "Tap to start"
        t.updateTile()
    }

    override fun onClick() {
        if (listening()) {
            CaptureService.stop(this)
            refreshTile()
            return
        }
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_START, true)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 11, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION") startActivityAndCollapse(i)
        }
    }
}

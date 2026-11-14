package dev.pocketagent.android

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.pocketagent.ui.MainActivity

/**
 * The Quick Settings tile: pull the shade down anywhere, tap, and the task sheet opens without the bubble.
 * It holds no state and no data. It only asks the running accessibility service to do what a bubble tap does, and
 * when that service is off it opens the app so the user can switch it on.
 */
class PocketTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        super.onClick()
        val running = PocketAccessibilityService.running
        if (running != null) {
            running.startFromTile()
        } else {
            openApp()
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        tile.state = if (PocketAccessibilityService.running != null) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
        tile.updateTile()
    }

    // The Intent overload is the only one before Android 14; the PendingIntent one is used from there on.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

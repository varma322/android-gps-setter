package io.github.jqssun.gpssetter.utils

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

// Quick Settings tile: toggle spoofing to the last-set location without opening the app.
class SpoofTileService : TileService() {

    override fun onStartListening() = updateTile(PrefManager.isStarted)

    override fun onClick() {
        val newStarted = !PrefManager.isStarted
        PrefManager.update(newStarted, PrefManager.getLat, PrefManager.getLng)
        if (!newStarted) NotificationsChannel().cancelAllNotifications(this)
        // use the intended state: PrefManager.update writes asynchronously
        updateTile(newStarted)
    }

    private fun updateTile(on: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) "On" else "Off"
        }
        tile.updateTile()
    }
}

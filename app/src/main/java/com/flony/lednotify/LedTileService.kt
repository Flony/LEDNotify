package com.flony.lednotify

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class LedTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val currentEnabled = prefs.getBoolean("pref_app_enabled", true)
        val newEnabled = !currentEnabled

        prefs.edit().putBoolean("pref_app_enabled", newEnabled).apply()

        if (!newEnabled) {
            sendBroadcast(Intent("com.flony.lednotify.ACTION_CLEAR_LED"))
        }

        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("led_notify_prefs", MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("pref_app_enabled", true)

        if (isEnabled) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.tile_label_on)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_label_off)
        }
        tile.updateTile()
    }
}

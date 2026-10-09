package io.github.jqssun.gpssetter.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Backs the "Stop" action on the location-set notification: stop spoofing without opening the app.
class StopSpoofReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        PrefManager.update(false, PrefManager.getLat, PrefManager.getLng)
        NotificationsChannel().cancelAllNotifications(context)
    }
}

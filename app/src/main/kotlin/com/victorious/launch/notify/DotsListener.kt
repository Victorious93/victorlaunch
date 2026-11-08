package com.victorious.launch.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Notification dots for app icons. Keeps only "package -> count" in memory; titles and text are
 * never read. Runs only after the user grants notification access in Android settings.
 */
class DotsListener : NotificationListenerService() {
    override fun onListenerConnected() { connected = this; publish() }
    override fun onListenerDisconnected() { connected = null; _counts.value = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val active = try { activeNotifications } catch (e: Exception) { null } ?: return
        _counts.value = active.asSequence()
            .filter { it.packageName != packageName }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .filter { it.notification.flags and Notification.FLAG_ONGOING_EVENT == 0 } // no dots for music/FGS
            .groupingBy { it.packageName }
            .eachCount()
    }

    companion object {
        private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val counts: StateFlow<Map<String, Int>> = _counts
        @Volatile private var connected: DotsListener? = null

        fun hasAccess(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        fun ensureBound(ctx: Context) {
            if (connected == null && hasAccess(ctx)) {
                try { NotificationListenerService.requestRebind(ComponentName(ctx, DotsListener::class.java)) } catch (_: Exception) {}
            }
        }
    }
}

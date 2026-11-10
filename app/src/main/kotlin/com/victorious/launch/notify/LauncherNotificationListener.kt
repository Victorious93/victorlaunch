package com.victorious.launch.notify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.notify.NotificationPreview

/**
 * Opt-in NotificationListener used only for launcher badges and long-press
 * previews. Notification content is kept in memory, never logged, and never
 * written to preferences or backups.
 */
class LauncherNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        publish()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        publish()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        publish()
    }

    override fun onListenerDisconnected() {
        NotificationBadgeStore.clear()
    }

    private fun optedIn(): Boolean =
        try {
            VictorLaunchApp.instance.settings.isNotificationBadgesEnabled()
        } catch (_: Exception) {
            false
        }

    private fun publish() {
        if (!optedIn()) {
            NotificationBadgeStore.clear()
            return
        }
        val active =
            try {
                activeNotifications.orEmpty().map { toPreview(it) }
            } catch (_: Exception) {
                emptyList()
            }
        NotificationBadgeStore.publish(active, true)
    }

    private fun toPreview(sbn: StatusBarNotification): NotificationPreview {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return NotificationPreview(
            key = sbn.key.orEmpty(),
            packageName = sbn.packageName.orEmpty(),
            title = title,
            text = text,
            postedAtMs = sbn.postTime,
            ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
        )
    }
}

package com.victorious.launch.notify

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import com.victorious.launch.core.notify.NotificationBadge
import com.victorious.launch.core.notify.NotificationBadges
import com.victorious.launch.core.notify.NotificationPreview
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory only. Never persist, log, or export notification content. */
object NotificationBadgeStore {
    private val _badges = MutableStateFlow<Map<String, NotificationBadge>>(emptyMap())
    val badges: StateFlow<Map<String, NotificationBadge>> = _badges

    fun publish(active: List<NotificationPreview>, optedIn: Boolean) {
        _badges.value = NotificationBadges.summarize(active, optedIn)
    }

    fun clear() {
        _badges.value = emptyMap()
    }

    fun listenerComponent(context: Context): ComponentName =
        ComponentName(context, LauncherNotificationListener::class.java)

    fun isListenerGranted(context: Context): Boolean {
        val cn = listenerComponent(context)
        return if (Build.VERSION.SDK_INT >= 27) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            nm?.isNotificationListenerAccessGranted(cn) == true
        } else {
            val flat =
                android.provider.Settings.Secure.getString(
                    context.contentResolver,
                    "enabled_notification_listeners",
                ).orEmpty()
            flat.split(":").any { it.equals(cn.flattenToString(), ignoreCase = true) }
        }
    }

    fun requestRebind(context: Context) {
        if (Build.VERSION.SDK_INT >= 24 && isListenerGranted(context)) {
            try {
                NotificationListenerService.requestRebind(listenerComponent(context))
            } catch (_: Exception) {
            }
        }
    }
}

val LocalNotificationBadges = staticCompositionLocalOf { emptyMap<String, NotificationBadge>() }

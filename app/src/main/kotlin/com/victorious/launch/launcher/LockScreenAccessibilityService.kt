package com.victorious.launch.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** Opt-in service whose only job is GLOBAL_ACTION_LOCK_SCREEN for a home gesture. */
class LockScreenAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: LockScreenAccessibilityService? = null
            private set

        fun isEnabled(): Boolean = instance != null

        /** Returns false when the service is off or the OS is older than Android 9. */
        fun lock(): Boolean {
            val svc = instance ?: return false
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return false
            return svc.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        }
    }
}

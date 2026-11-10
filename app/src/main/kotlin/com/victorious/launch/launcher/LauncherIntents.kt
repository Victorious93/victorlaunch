package com.victorious.launch.launcher

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow

/** In-process extras from home-screen widgets / shortcuts. Not a bridge op. */
object LauncherIntents {
    const val EXTRA_OPEN = "open"
    const val GITHUB = "github"
    const val DEVICE = "device"

    val open = MutableStateFlow<String?>(null)

    fun consume(intent: Intent?) {
        val target = intent?.getStringExtra(EXTRA_OPEN) ?: return
        if (target.isNotBlank()) open.value = target
    }
}

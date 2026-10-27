package com.victorious.launch.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context

class WidgetHostHolder(context: Context) {
    val host: AppWidgetHost = AppWidgetHost(context.applicationContext, HOST_ID)
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    val packageManager = context.packageManager

    fun start() {
        host.startListening()
    }

    fun stop() {
        try {
            host.stopListening()
        } catch (_: Exception) {
        }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        try {
            host.deleteAppWidgetId(id)
        } catch (_: Exception) {
        }
    }

    fun providers(): List<AppWidgetProviderInfo> = manager.installedProviders

    companion object {
        const val HOST_ID = 0x564C
    }
}

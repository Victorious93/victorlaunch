package com.victorious.launch.launcher

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Apps launched *through VictorLaunch*, most recent first. No usage-access permission needed:
 * the launcher only remembers what it started itself. Stored locally in SharedPreferences.
 */
object RecentLaunches {
    private const val PREFS = "recent_launches"
    private const val KEY = "components"
    private const val MAX = 20
    private val _list = MutableStateFlow<List<String>>(emptyList())
    val list: StateFlow<List<String>> = _list
    @Volatile private var loaded = false

    fun load(ctx: Context): StateFlow<List<String>> {
        if (!loaded) {
            _list.value = decode(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))
            loaded = true
        }
        return list
    }

    fun record(ctx: Context, app: LaunchableApp) {
        load(ctx)
        val id = key(app)
        val next = (listOf(id) + _list.value.filter { it != id }).take(MAX)
        _list.value = next
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next.joinToString("\n")).apply()
    }

    fun clear(ctx: Context) {
        _list.value = emptyList()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** Component + profile serial, so the same app in Island is tracked separately. */
    fun key(app: LaunchableApp) = "${app.component}@${app.userSerial}"

    internal fun decode(raw: String?): List<String> = raw?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
}

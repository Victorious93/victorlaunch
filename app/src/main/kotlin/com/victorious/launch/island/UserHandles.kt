package com.victorious.launch.island

import android.os.UserHandle

/**
 * [UserHandle.getIdentifier] is hidden in the public SDK. Reflect, then
 * parse `UserHandle{N}`, then [UserHandle.hashCode] (AOSP returns the id).
 */
internal fun userIdOf(handle: UserHandle): Int {
    try {
        val method = UserHandle::class.java.getMethod("getIdentifier")
        val value = method.invoke(handle)
        if (value is Int) return value
    } catch (_: Exception) {
    }
    val parsed = USER_HANDLE_ID.find(handle.toString())?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (parsed != null) return parsed
    return handle.hashCode()
}

private val USER_HANDLE_ID = Regex("""\{(\d+)\}""")

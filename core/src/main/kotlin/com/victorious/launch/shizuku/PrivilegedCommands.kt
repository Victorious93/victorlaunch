package com.victorious.launch.shizuku

/**
 * Shell command builders for the Shizuku UserService (uid 0 or 2000).
 * Pure strings — no Android types — so they can be unit-tested.
 */
object PrivilegedCommands {
    fun freeze(packageName: String, frozen: Boolean): List<String> {
        requirePackage(packageName)
        return if (frozen) listOf("pm", "suspend", packageName) else listOf("pm", "unsuspend", packageName)
    }

    fun hide(packageName: String, hidden: Boolean): List<String> {
        requirePackage(packageName)
        return if (hidden) {
            listOf("pm", "disable-user", "--user", "0", packageName)
        } else {
            listOf("pm", "enable", "--user", "0", packageName)
        }
    }

    fun forceStop(packageName: String): List<String> {
        requirePackage(packageName)
        return listOf("am", "force-stop", packageName)
    }

    fun grantUsageAccess(packageName: String): List<String> {
        requirePackage(packageName)
        return listOf("appops", "set", packageName, "GET_USAGE_STATS", "allow")
    }

    fun createManagedProfile(name: String): List<String> {
        val safe = name.ifBlank { "Island" }.replace(Regex("[^A-Za-z0-9 _-]"), "")
        return listOf("pm", "create-user", "--profileOf", "0", "--managed", safe)
    }

    fun quietMode(userId: Int, quiet: Boolean): List<String> {
        require(userId >= 0) { "userId" }
        return if (quiet) listOf("am", "stop-user", "-f", userId.toString())
        else listOf("am", "start-user", userId.toString())
    }

    fun installExisting(packageName: String, userId: Int): List<String> {
        requirePackage(packageName)
        require(userId >= 0) { "userId" }
        return listOf("pm", "install-existing", "--user", userId.toString(), packageName)
    }

    private fun requirePackage(packageName: String) {
        require(packageName.matches(Regex("^[A-Za-z0-9._]+$"))) { "invalid packageName" }
    }
}

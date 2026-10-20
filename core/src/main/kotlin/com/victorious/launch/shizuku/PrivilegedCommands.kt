package com.victorious.launch.shizuku

/**
 * Shell command builders for the Shizuku UserService (uid 0 or 2000).
 * Pure strings — no Android types — so they can be unit-tested.
 */
object PrivilegedCommands {
    fun freeze(packageName: String, frozen: Boolean, userId: Int = 0): List<String> {
        requirePackage(packageName)
        requireUser(userId)
        return if (frozen) {
            listOf("pm", "suspend", "--user", userId.toString(), packageName)
        } else {
            listOf("pm", "unsuspend", "--user", userId.toString(), packageName)
        }
    }

    fun hide(packageName: String, hidden: Boolean, userId: Int = 0): List<String> {
        requirePackage(packageName)
        requireUser(userId)
        return if (hidden) {
            listOf("pm", "disable-user", "--user", userId.toString(), packageName)
        } else {
            listOf("pm", "enable", "--user", userId.toString(), packageName)
        }
    }

    fun forceStop(packageName: String, userId: Int = 0): List<String> {
        requirePackage(packageName)
        requireUser(userId)
        return listOf("am", "force-stop", "--user", userId.toString(), packageName)
    }

    fun grantUsageAccess(packageName: String): List<String> {
        requirePackage(packageName)
        return listOf("appops", "set", packageName, "GET_USAGE_STATS", "allow")
    }

    fun createManagedProfile(name: String): List<String> {
        val safe = name.ifBlank { "Island" }.replace(Regex("[^A-Za-z0-9_-]"), "").ifBlank { "Island" }
        return listOf("pm", "create-user", "--profileOf", "0", "--managed", safe)
    }

    fun quietMode(userId: Int, quiet: Boolean): List<String> {
        requireUser(userId)
        return if (quiet) listOf("am", "stop-user", "-f", userId.toString())
        else listOf("am", "start-user", userId.toString())
    }

    fun installExisting(packageName: String, userId: Int): List<String> {
        requirePackage(packageName)
        requireUser(userId)
        return listOf("pm", "install-existing", "--user", userId.toString(), packageName)
    }

    private fun requirePackage(packageName: String) {
        require(packageName.matches(Regex("^[A-Za-z0-9._]+$"))) { "invalid packageName" }
    }

    private fun requireUser(userId: Int) {
        require(userId >= 0) { "userId" }
    }
}

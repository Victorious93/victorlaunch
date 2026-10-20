package com.victorious.launch.core.protocol

/**
 * Package + signing-certificate allowlist for EXTERNAL_NODE authorization.
 *
 * A caller is trusted only when **both** the package name and a SHA-256 of
 * one of its signing certs match a pinned row. Payload fields never
 * authorize. The release controller cert is a reserved slot ([TBD]) and
 * does not match until it is filled in.
 */
data class TrustedPeer(
    val packageName: String,
    val certSha256: String?,
    val kind: String,
    val role: String,
) {
    val pinned: Boolean
        get() = !certSha256.isNullOrBlank()
}

object TrustedPeers {
    const val CONTROLLER_PACKAGE = Protocol.CONTROLLER_PACKAGE

    const val CONTROLLER_DEBUG_CERT_SHA256 =
        "88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D"

    /** Reserved. Do not treat a blank/TBD release cert as a match. */
    const val CONTROLLER_RELEASE_CERT_SHA256_TBD = "TBD"

    val CONTROLLER_DEBUG =
        TrustedPeer(
            packageName = CONTROLLER_PACKAGE,
            certSha256 = CONTROLLER_DEBUG_CERT_SHA256,
            kind = "debug",
            role = "controller",
        )

    val CONTROLLER_RELEASE =
        TrustedPeer(
            packageName = CONTROLLER_PACKAGE,
            certSha256 = null,
            kind = "release",
            role = "controller",
        )

    val ALL: List<TrustedPeer> = listOf(CONTROLLER_DEBUG, CONTROLLER_RELEASE)

    fun normalize(certSha256: String): String =
        certSha256.replace(":", "").replace(" ", "").lowercase()

    fun isPinnedCert(certSha256: String?): Boolean {
        if (certSha256.isNullOrBlank()) return false
        if (certSha256.equals(CONTROLLER_RELEASE_CERT_SHA256_TBD, ignoreCase = true)) return false
        return true
    }

    fun isTrusted(packageName: String, certs: List<String>): Boolean {
        if (packageName != CONTROLLER_PACKAGE && ALL.none { it.packageName == packageName }) {
            return false
        }
        val incoming = certs.map { normalize(it) }.toSet()
        if (incoming.isEmpty()) return false
        return ALL.any { peer ->
            peer.packageName == packageName &&
                isPinnedCert(peer.certSha256) &&
                incoming.contains(normalize(peer.certSha256!!))
        }
    }

    fun isPinnedController(packageName: String, certs: List<String>): Boolean =
        isTrusted(packageName, certs)

    /**
     * Allowlist first, then extra user-pairing rows (package + cert).
     * Extra pairing rows authorize the caller as a peer; they do **not**
     * grant Don's pinned-controller full-control privilege. That requires
     * [isPinnedController] plus the Settings kill switch.
     */
    fun authorize(
        packageName: String,
        certs: List<String>,
        extraPairings: List<Pair<String, String>> = emptyList(),
    ): Boolean {
        if (isTrusted(packageName, certs)) return true
        val incoming = certs.map { normalize(it) }.toSet()
        return extraPairings.any { (pkg, cert) ->
            pkg == packageName && isPinnedCert(cert) && incoming.contains(normalize(cert))
        }
    }

    fun pinnedController(): TrustedPeer = CONTROLLER_DEBUG
}

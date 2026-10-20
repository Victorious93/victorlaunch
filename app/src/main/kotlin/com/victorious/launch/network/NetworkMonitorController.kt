package com.victorious.launch.network

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import com.victorious.launch.core.status.StatusTrayDomainStatus
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.shizuku.PrivilegedOps
import com.victorious.launch.shizuku.ShizukuSession
import kotlinx.serialization.json.JsonObject

class NetworkMonitorController(
    private val context: Context,
    private val settings: AppSettings,
    private val privileged: PrivilegedOps,
    private val shizuku: ShizukuSession,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val engine = NetworkMonitorEngine()
    private val main = Handler(Looper.getMainLooper())
    private var modern: Any? = null
    private var legacy: LegacyPhoneListener? = null
    private var lastRecoverMs: Long? = null
    private val recoverAtMs = ArrayDeque<Long>()

    fun start() {
        main.post { bindListener() }
    }

    fun refresh() {
        main.post {
            bindListener()
            poll()
        }
    }

    fun stop() {
        main.post {
            unbindListener()
            engine.reset()
        }
    }

    fun snapshotJson(): JsonObject {
        val enabled = settings.isNetworkMonitorEnabled()
        val phone = hasPhonePermission(context)
        val location = hasLocationPermission(context)
        return NetworkStatusCodec.encode(
            enabled = enabled,
            sample = if (enabled) engine.latest() else null,
            alerts = if (enabled) engine.alerts() else emptyList(),
            permissionsReady = phone && location,
            lockActive = settings.isNetworkBlock2g() || settings.isNetworkLockLteNr(),
        )
    }

    fun domainStatus(): StatusTrayDomainStatus =
        NetworkStatusCodec.domainStatus(
            enabled = settings.isNetworkMonitorEnabled(),
            permissionsReady = hasPhonePermission(context) && hasLocationPermission(context),
            sample = engine.latest(),
            latestAlert = engine.latestAlert(),
        )

    fun latestAlerts(): List<NetworkAlert> = engine.alerts()

    fun historySize(): Int = engine.history().size

    fun applyLocks(): String {
        if (!settings.isNetworkMonitorEnabled()) return "Enable Network Monitor first"
        val ready = shizuku.snapshot().ready
        if (!NetworkGate.canLock(true, settings.isNetworkBlock2g() || settings.isNetworkLockLteNr(), ready)) {
            return "Shizuku is not ready. Use the Allow 2G / preferred-network settings screens on this device."
        }
        val notes = mutableListOf<String>()
        if (settings.isNetworkBlock2g()) {
            notes.add(privileged.setAllowed2g(false).detail)
        }
        if (settings.isNetworkLockLteNr()) {
            notes.add(privileged.setAllowedNetworkTypes(NetworkCommands.LTE_NR_BITMASK).detail)
            notes.add(privileged.setPreferredNetworkMode(NetworkCommands.MODE_NR_LTE).detail)
        }
        return notes.joinToString(" · ").ifBlank { "no lock flags on" }
    }

    fun recoverNow(): String {
        if (!settings.isNetworkMonitorEnabled()) return "Enable Network Monitor first"
        return privileged.radioRecover().detail
    }

    private fun bindListener() {
        unbindListener()
        if (!settings.isNetworkMonitorEnabled()) return
        if (!hasPhonePermission(context)) return
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return
        val onChange = Runnable { poll() }
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                modern = NetworkTelephonyApi31.register(tm, context.mainExecutor) { main.post(onChange) }
            } catch (_: Exception) {
            }
        } else {
            val listener =
                object : LegacyPhoneListener() {
                    override fun onRadioChanged() {
                        main.post(onChange)
                    }
                }
            try {
                @Suppress("DEPRECATION")
                tm.listen(listener, listenFlags())
                legacy = listener
            } catch (_: Exception) {
            }
        }
        poll()
    }

    private fun unbindListener() {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return
        val modernCb = modern
        if (Build.VERSION.SDK_INT >= 31 && modernCb != null) {
            try {
                NetworkTelephonyApi31.unregister(tm, modernCb)
            } catch (_: Exception) {
            }
        }
        modern = null
        legacy?.let {
            try {
                @Suppress("DEPRECATION")
                tm.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
            } catch (_: Exception) {
            }
        }
        legacy = null
    }

    private fun poll() {
        if (!settings.isNetworkMonitorEnabled()) return
        val sample = readNetworkSample(context, clock()) ?: return
        val fresh = engine.ingest(true, sample)
        val alert = fresh.lastOrNull() ?: return
        maybeRecover(alert)
    }

    private fun maybeRecover(alert: NetworkAlert) {
        val now = clock()
        val ready = shizuku.snapshot().ready
        if (
            !NetworkAutoRecover.shouldRecover(
                enabled = settings.isNetworkMonitorEnabled(),
                autoRecover = settings.isNetworkAutoRecover(),
                privilegedReady = ready,
                nowMs = now,
                lastRecoverMs = lastRecoverMs,
                recoverAtMs = recoverAtMs.toList(),
                latestAlert = alert,
            )
        ) {
            return
        }
        lastRecoverMs = now
        recoverAtMs.addLast(now)
        while (recoverAtMs.size > 8) recoverAtMs.removeFirst()
        privileged.radioRecover()
    }
}

package com.victorious.launch.network

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

internal fun hasPhonePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
        PackageManager.PERMISSION_GRANTED

internal fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Reads serving-cell + display info on the calling thread. Returns null
 * when permissions are missing. Never claims a modem lock worked.
 */
@android.annotation.SuppressLint("MissingPermission") // READ_PHONE_STATE checked by hasPhonePermission; location by hasLocationPermission
internal fun readNetworkSample(context: Context, nowMs: Long): NetworkSample? {
    if (!hasPhonePermission(context)) return null
    val tm = context.getSystemService(TelephonyManager::class.java) ?: return null
    var rat = NetworkRat.fromTelephonyNetworkType(tm.dataNetworkType)
    if (Build.VERSION.SDK_INT >= 30) {
        try {
            rat = NetworkRat.fromDisplayOverride(displayOverrideType(tm), rat)
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
    var rsrp: Int? = null
    var rsrq: Int? = null
    var sinr: Int? = null
    var cellId: String? = null
    if (hasLocationPermission(context)) {
        try {
            @Suppress("DEPRECATION")
            val cells = tm.allCellInfo
            val serving = cells?.firstOrNull { it.isRegistered }
            if (serving != null) {
                cellId = servingCellId(serving)
                // CellInfo.getCellSignalStrength() is API 30 and CellSignalStrengthNr is API 29: touching
                // either on older Android throws NoSuchMethodError/NoClassDefFoundError (Errors, not
                // Exceptions), so branch on SDK_INT before referencing them.
                if (serving is CellInfoLte) {
                    val ss = serving.cellSignalStrength
                    rsrp = ss.rsrp.takeIf { it in -140..-40 }
                    rsrq = ss.rsrq.takeIf { it in -34..3 }
                    sinr = ss.rssnr.takeIf { it in -20..30 }
                } else if (Build.VERSION.SDK_INT >= 29) {
                    nrSignal(serving)?.let { (p, q, n) -> rsrp = p; rsrq = q; sinr = n }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }
    val registered =
        try {
            tm.serviceState?.state == ServiceState.STATE_IN_SERVICE
        } catch (_: Exception) {
            rat != NetworkRat.NONE
        }
    val dataState = NetworkDataState.fromTelephony(tm.dataState)
    return NetworkSample(
        atMs = nowMs,
        rat = rat,
        rsrp = rsrp,
        rsrq = rsrq,
        sinr = sinr,
        cellId = cellId,
        registered = registered,
        dataState = dataState,
    )
}

@androidx.annotation.RequiresApi(29)
private fun nrSignal(info: CellInfo): Triple<Int?, Int?, Int?>? {
    val ss = (info as? CellInfoNr)?.cellSignalStrength as? CellSignalStrengthNr ?: return null
    return Triple(
        ss.ssRsrp.takeIf { it in -140..-40 },
        ss.ssRsrq.takeIf { it in -43..20 },
        ss.ssSinr.takeIf { it in -23..40 },
    )
}

/** MCC/MNC strings are API 28; below that report only the cell id. */
private fun plmn(mcc: () -> String?, mnc: () -> String?): List<String?> =
    if (Build.VERSION.SDK_INT >= 28) listOf(mcc(), mnc()) else emptyList()

@androidx.annotation.RequiresApi(30)
private fun displayOverrideType(tm: TelephonyManager): Int {
    val method = TelephonyManager::class.java.getMethod("getTelephonyDisplayInfo")
    val display = method.invoke(tm) ?: return 0
    val override = display.javaClass.getMethod("getOverrideNetworkType").invoke(display) as? Int
    return override ?: 0
}

internal fun servingCellId(info: CellInfo): String? {
    val raw =
        when (info) {
            is CellInfoLte -> {
                val id = info.cellIdentity
                val ci = id.ci
                if (ci == Int.MAX_VALUE || ci <= 0) null
                else (plmn({ id.mccString }, { id.mncString }) + ci.toString()).filterNotNull().joinToString("-")
            }
            is CellInfoGsm -> {
                val id = info.cellIdentity
                val cid = id.cid
                if (cid == Int.MAX_VALUE || cid <= 0) null
                else (plmn({ id.mccString }, { id.mncString }) + cid.toString()).filterNotNull().joinToString("-")
            }
            is CellInfoWcdma -> {
                val id = info.cellIdentity
                val cid = id.cid
                if (cid == Int.MAX_VALUE || cid <= 0) null
                else (plmn({ id.mccString }, { id.mncString }) + cid.toString()).filterNotNull().joinToString("-")
            }
            else -> if (Build.VERSION.SDK_INT >= 29) nrCellId(info) else null
        }
    return raw?.takeIf { NetworkSample.CELL.matches(it) }
}

@androidx.annotation.RequiresApi(29)
private fun nrCellId(info: CellInfo): String? {
    val id = (info as? CellInfoNr)?.cellIdentity as? CellIdentityNr ?: return null
    val nci = id.nci
    return if (nci == Long.MAX_VALUE || nci <= 0L) null
    else listOfNotNull(id.mccString, id.mncString, nci.toString()).joinToString("-")
}

@Suppress("DEPRECATION")
internal fun listenFlags(): Int {
    @Suppress("DEPRECATION")
    var flags =
        PhoneStateListener.LISTEN_SERVICE_STATE or
            PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
            PhoneStateListener.LISTEN_DATA_CONNECTION_STATE or
            PhoneStateListener.LISTEN_CELL_INFO
    if (Build.VERSION.SDK_INT >= 30) {
        flags = flags or PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED
    }
    return flags
}

@Suppress("DEPRECATION")
internal abstract class LegacyPhoneListener : PhoneStateListener() {
    abstract fun onRadioChanged()

    override fun onServiceStateChanged(serviceState: ServiceState?) {
        onRadioChanged()
    }

    override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
        onRadioChanged()
    }

    override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
        onRadioChanged()
    }

    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>?) {
        onRadioChanged()
    }

    @androidx.annotation.RequiresApi(30)
    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
        onRadioChanged()
    }
}


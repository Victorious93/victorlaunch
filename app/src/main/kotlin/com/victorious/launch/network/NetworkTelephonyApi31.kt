package com.victorious.launch.network

import android.os.Build
import android.telephony.CellInfo
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

@RequiresApi(Build.VERSION_CODES.S)
internal abstract class ModernTelephonyListener :
    TelephonyCallback(),
    TelephonyCallback.ServiceStateListener,
    TelephonyCallback.SignalStrengthsListener,
    TelephonyCallback.DataConnectionStateListener,
    TelephonyCallback.CellInfoListener,
    TelephonyCallback.DisplayInfoListener {
    abstract fun onRadioChanged()

    override fun onServiceStateChanged(serviceState: ServiceState) {
        onRadioChanged()
    }

    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
        onRadioChanged()
    }

    override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
        onRadioChanged()
    }

    override fun onCellInfoChanged(cellInfo: MutableList<CellInfo>) {
        onRadioChanged()
    }

    override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
        onRadioChanged()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
internal object NetworkTelephonyApi31 {
    fun register(tm: TelephonyManager, executor: Executor, onChange: () -> Unit): TelephonyCallback {
        val cb =
            object : ModernTelephonyListener() {
                override fun onRadioChanged() {
                    onChange()
                }
            }
        tm.registerTelephonyCallback(executor, cb)
        return cb
    }

    fun unregister(tm: TelephonyManager, callback: Any) {
        if (callback is TelephonyCallback) {
            tm.unregisterTelephonyCallback(callback)
        }
    }
}

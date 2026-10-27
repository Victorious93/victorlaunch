package com.victorious.launch.share

import android.content.ComponentName
import android.content.IntentFilter
import android.os.Build
import android.service.chooser.ChooserTarget
import android.service.chooser.ChooserTargetService
import androidx.annotation.RequiresApi
import com.victorious.launch.R
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.share.ShareTargetState

@RequiresApi(Build.VERSION_CODES.M)
class FleetChooserTargetService : ChooserTargetService() {
    override fun onGetChooserTargets(
        targetActivityName: ComponentName?,
        matchedFilter: IntentFilter?,
    ): MutableList<ChooserTarget> {
        val settings = VictorLaunchApp.instance.settings
        val ready =
            FleetSharePublisher.statuses(this, settings).filter { it.state == ShareTargetState.READY }
        val icon = android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher)
        return ready.mapIndexed { index, row ->
            ChooserTarget(
                row.spec.label,
                icon,
                1f - index * 0.05f,
                ComponentName(this, ShareCatcherActivity::class.java),
                android.os.Bundle().apply { putString(FleetSharePublisher.EXTRA_TARGET, row.spec.id) },
            )
        }.toMutableList()
    }
}

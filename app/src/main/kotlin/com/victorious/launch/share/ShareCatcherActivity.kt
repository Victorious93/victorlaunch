package com.victorious.launch.share

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.share.ShareTargetState

class ShareCatcherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = VictorLaunchApp.instance.settings
        FleetSharePublisher.publish(this, settings)
        val incoming = intent
        val forced = incoming.getStringExtra(FleetSharePublisher.EXTRA_TARGET)
        val rows = FleetSharePublisher.statuses(this, settings)
        val ready = rows.filter { it.state == ShareTargetState.READY }
        val match = ready.firstOrNull { it.spec.id == forced }
        if (match?.installedPackage != null) {
            forward(incoming, match.installedPackage!!)
            finish()
            return
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this)
        title.text = "Share to a verified fleet app"
        title.setTextColor(0xFFE840FF.toInt())
        root.addView(title)
        if (ready.isEmpty()) {
            val empty = TextView(this)
            empty.text = "No installed fleet app has a matching package+cert pin. Settings → Share targets to trust an installed app."
            empty.setTextColor(0xFF9AA3B5.toInt())
            root.addView(empty)
        } else {
            ready.forEach { row ->
                val btn = TextView(this)
                btn.text = row.spec.label
                btn.setPadding(24, 24, 24, 24)
                btn.setTextColor(0xFF00E5FF.toInt())
                btn.setOnClickListener {
                    forward(incoming, row.installedPackage!!)
                    finish()
                }
                root.addView(btn)
            }
        }
        setContentView(root)
    }

    private fun forward(incoming: Intent, packageName: String) {
        val send =
            Intent(incoming).apply {
                setPackage(packageName)
                setComponent(null)
                removeExtra(FleetSharePublisher.EXTRA_TARGET)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            startActivity(send)
        } catch (_: Exception) {
            val fallback = packageManager.getLaunchIntentForPackage(packageName)
            if (fallback != null) startActivity(fallback)
        }
    }
}

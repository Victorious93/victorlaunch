package com.victorious.launch.island

import android.app.Activity
import android.os.Bundle

/** Work-profile side of clone/freeze/hide. Started via the cross-profile intent filter. */
class IslandActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IslandCommandReceiver().onReceive(this, intent)
        finish()
    }
}

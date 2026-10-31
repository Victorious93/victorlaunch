package com.victorious.launch.bridge

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle

/** On-device confirmation for remote ops that need it (layout.set from non-controller peers). */
class ConfirmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showNext()
    }

    private fun showNext() {
        val w = BridgeEvents.oldest()
        if (w == null) {
            finish()
            return
        }
        val env = w.pending.envelope
        val summary = env.params.entries.joinToString("\n") { (k, v) -> "• $k: ${v.toString().take(200)}" }
        AlertDialog.Builder(this)
            .setTitle("Allow remote ${env.op}?")
            .setMessage("${w.pending.sourceNodeRef} wants to change your home layout:\n\n$summary")
            .setCancelable(false)
            .setPositiveButton("Allow") { _, _ ->
                BridgeEvents.resolve(w.id, approved = true)
                showNext()
            }
            .setNegativeButton("Deny") { _, _ ->
                BridgeEvents.resolve(w.id, approved = false)
                showNext()
            }
            .show()
    }
}

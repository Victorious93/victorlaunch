package com.victorious.launch.core.protocol

/** Wire error categories from droidcommand-AI REMOTE_CONTROL.md (ratified). */
object ErrorCategory {
    const val INVALID_PARAMETERS = "invalid_parameters"
    const val NOT_PAIRED = "not_paired"
    const val NOT_FOUND = "not_found"
    const val INTERNAL = "internal"
    const val UNAUTHORIZED = "unauthorized"
    const val REPLAY_DETECTED = "replay_detected"
    const val EXPIRED = "expired"
    const val EXTERNAL_CONTROL_DISABLED = "external_control_disabled"
    const val CONFIRMATION_REQUIRED = "confirmation_required"
    const val UNSUPPORTED_OP = "unsupported_op"
    const val CAPABILITY_OFF = "capability_off"
    const val PERMISSION = "permission"
    const val NOT_AVAILABLE = "not_available"
}

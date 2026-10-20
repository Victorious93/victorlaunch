package com.victorious.launch.core.protocol

/**
 * Who may run remote action ops, and whether they skip on-device
 * confirmation.
 *
 * Don's choice (deviates from the fleet default that destructive ops
 * stay gated): the **pinned debug controller**
 * (`ai.droidcommand.app` + debug cert) has full control of
 * `app.launch` / `layout.set` / `panel.open` with no confirmation,
 * until the user flip the Settings kill switch off.
 *
 * Every other peer keeps the old defaults: external control OFF,
 * `layout.set` still requires confirmation even if that generic
 * switch is later turned on.
 */
object RemoteActionPolicy {
    val FULL_CONTROL_OPS: Set<String> =
        setOf(Ops.APP_LAUNCH, Ops.LAYOUT_SET, Ops.PANEL_OPEN)

    fun controllerHasFullControl(
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
    ): Boolean = callerIsPinnedController && controllerFullControlEnabled

    fun remoteAllowed(
        op: String,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
        genericExternalControl: Boolean,
    ): Boolean {
        val descriptor = Ops.descriptor(op) ?: return false
        if (op in FULL_CONTROL_OPS) {
            if (controllerHasFullControl(callerIsPinnedController, controllerFullControlEnabled)) {
                return true
            }
            return genericExternalControl
        }
        return descriptor.remoteAllowed
    }

    fun requiresConfirm(
        op: String,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
    ): Boolean {
        val descriptor = Ops.descriptor(op) ?: return true
        if (op in FULL_CONTROL_OPS &&
            controllerHasFullControl(callerIsPinnedController, controllerFullControlEnabled)
        ) {
            return false
        }
        return descriptor.requiresConfirm
    }

    fun allowsAction(
        op: String,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
        genericExternalControl: Boolean,
        isLocalUi: Boolean,
    ): Boolean {
        if (op !in FULL_CONTROL_OPS) return true
        if (isLocalUi) return true
        return remoteAllowed(
            op,
            callerIsPinnedController,
            controllerFullControlEnabled,
            genericExternalControl,
        )
    }
}

package com.victorious.launch.core

import com.victorious.launch.core.protocol.Ops
import com.victorious.launch.core.protocol.RemoteActionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteActionPolicyTest {
    @Test
    fun pinnedControllerSkipsConfirmAndIsRemoteAllowed() {
        assertTrue(
            RemoteActionPolicy.controllerHasFullControl(
                callerIsPinnedController = true,
                controllerFullControlEnabled = true,
            ),
        )
        for (op in RemoteActionPolicy.FULL_CONTROL_OPS) {
            assertTrue(
                RemoteActionPolicy.remoteAllowed(
                    op,
                    callerIsPinnedController = true,
                    controllerFullControlEnabled = true,
                    genericExternalControl = false,
                ),
            )
            assertFalse(
                RemoteActionPolicy.requiresConfirm(
                    op,
                    callerIsPinnedController = true,
                    controllerFullControlEnabled = true,
                ),
            )
        }
    }

    @Test
    fun otherPeersStayOffAndLayoutSetNeedsConfirm() {
        assertFalse(
            RemoteActionPolicy.remoteAllowed(
                Ops.APP_LAUNCH,
                callerIsPinnedController = false,
                controllerFullControlEnabled = true,
                genericExternalControl = false,
            ),
        )
        assertTrue(
            RemoteActionPolicy.requiresConfirm(
                Ops.LAYOUT_SET,
                callerIsPinnedController = false,
                controllerFullControlEnabled = true,
            ),
        )
        assertTrue(
            RemoteActionPolicy.requiresConfirm(
                Ops.LAYOUT_SET,
                callerIsPinnedController = false,
                controllerFullControlEnabled = false,
            ),
        )
    }

    @Test
    fun killSwitchOffTreatsControllerLikeOtherPeer() {
        assertFalse(
            RemoteActionPolicy.allowsAction(
                Ops.PANEL_OPEN,
                callerIsPinnedController = true,
                controllerFullControlEnabled = false,
                genericExternalControl = false,
                isLocalUi = false,
            ),
        )
        assertTrue(
            RemoteActionPolicy.requiresConfirm(
                Ops.LAYOUT_SET,
                callerIsPinnedController = true,
                controllerFullControlEnabled = false,
            ),
        )
    }

    @Test
    fun configImportStaysRemoteOffEvenForController() {
        assertFalse(
            RemoteActionPolicy.remoteAllowed(
                Ops.CONFIG_IMPORT,
                callerIsPinnedController = true,
                controllerFullControlEnabled = true,
                genericExternalControl = true,
            ),
        )
    }
}

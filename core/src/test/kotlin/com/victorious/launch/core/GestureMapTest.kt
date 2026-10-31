package com.victorious.launch.core

import com.victorious.launch.core.launcher.GestureAction
import com.victorious.launch.core.launcher.GestureBinding
import com.victorious.launch.core.launcher.GestureMap
import com.victorious.launch.core.launcher.HomeGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMapTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val b =
            mapOf(
                HomeGesture.DOUBLE_TAP to GestureBinding(GestureAction.LOCK_SCREEN),
                HomeGesture.TWO_FINGER_SWIPE_UP to GestureBinding(GestureAction.LAUNCH_APP, "com.victorious.sms"),
                HomeGesture.PINCH_OUT to GestureBinding(GestureAction.OPEN_FLEET_TRAY),
            )
        assertEquals(b, GestureMap.decode(GestureMap.encode(b)))
    }

    @Test
    fun decodeSkipsGarbageAndLaunchWithoutPackage() {
        val d = GestureMap.decode("BOGUS=OPEN_DRAWER\nSWIPE_UP=NOPE\nDOUBLE_TAP=LAUNCH_APP\nPINCH_IN=OPEN_SEARCH\nno-equals")
        assertEquals(mapOf(HomeGesture.PINCH_IN to GestureBinding(GestureAction.OPEN_SEARCH)), d)
        assertTrue(GestureMap.decode(null).isEmpty())
    }

    @Test
    fun resolveUsesDefaultsAndOverrides() {
        assertEquals(GestureAction.OPEN_DRAWER, GestureMap.resolve(HomeGesture.SWIPE_UP, emptyMap()).action)
        val o = mapOf(HomeGesture.SWIPE_UP to GestureBinding(GestureAction.EXPAND_NOTIFICATIONS))
        assertEquals(GestureAction.EXPAND_NOTIFICATIONS, GestureMap.resolve(HomeGesture.SWIPE_UP, o).action)
        val bad = mapOf(HomeGesture.DOUBLE_TAP to GestureBinding(GestureAction.LAUNCH_APP, null))
        assertEquals(GestureBinding.NONE, GestureMap.resolve(HomeGesture.DOUBLE_TAP, bad))
    }

    @Test
    fun multiTouchClassification() {
        assertEquals(HomeGesture.PINCH_IN, GestureMap.classifyMultiTouch(400f, 200f, 0f, 50f))
        assertEquals(HomeGesture.PINCH_OUT, GestureMap.classifyMultiTouch(200f, 400f, 0f, 50f))
        assertEquals(HomeGesture.TWO_FINGER_SWIPE_UP, GestureMap.classifyMultiTouch(300f, 310f, -120f, 50f))
        assertEquals(HomeGesture.TWO_FINGER_SWIPE_DOWN, GestureMap.classifyMultiTouch(300f, 290f, 120f, 50f))
        assertNull(GestureMap.classifyMultiTouch(300f, 300f, 10f, 50f))
    }

    @Test
    fun doubleTapWindow() {
        assertTrue(GestureMap.isDoubleTap(1000, 1200, 10f, 10f))
        assertFalse(GestureMap.isDoubleTap(1000, 1400, 10f, 10f))
        assertFalse(GestureMap.isDoubleTap(1000, 1100, 300f, 0f))
        assertFalse(GestureMap.isDoubleTap(0, 100, 0f, 0f))
    }

    @Test
    fun assignableCoversRequestedGestures() {
        assertEquals(7, GestureMap.ASSIGNABLE.size)
        assertTrue(GestureAction.LOCK_SCREEN in GestureMap.USER_ACTIONS)
    }
}

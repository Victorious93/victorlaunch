package com.victorious.launch.core.launcher

import kotlin.math.abs

/**
 * Nova-style home gestures. Every gesture in [GestureMap.ASSIGNABLE] can be
 * bound by the user to a [GestureBinding]; swipe left/right stay page turns.
 */
enum class HomeGesture(val label: String) {
    SWIPE_UP("Swipe up"),
    SWIPE_DOWN("Swipe down"),
    SWIPE_LEFT("Swipe left"),
    SWIPE_RIGHT("Swipe right"),
    PINCH_IN("Pinch in"),
    PINCH_OUT("Pinch out"),
    DOUBLE_TAP("Double-tap"),
    TWO_FINGER_SWIPE_UP("Two-finger swipe up"),
    TWO_FINGER_SWIPE_DOWN("Two-finger swipe down"),
}

enum class GestureAction(val label: String) {
    NOOP("None"),
    OPEN_DRAWER("Open app drawer"),
    EXPAND_NOTIFICATIONS("Expand notifications"),
    OPEN_SEARCH("Open search"),
    OPEN_FLEET_TRAY("Open fleet tray"),
    LAUNCH_APP("Launch app"),
    LOCK_SCREEN("Lock screen (accessibility, opt-in)"),
    NEXT_PAGE("Next page"),
    PREV_PAGE("Previous page"),
}

/** A resolved assignment. [packageName] is only meaningful for [GestureAction.LAUNCH_APP]. */
data class GestureBinding(val action: GestureAction, val packageName: String? = null) {
    companion object {
        val NONE = GestureBinding(GestureAction.NOOP)
    }
}

object GestureMap {
    val ASSIGNABLE: List<HomeGesture> =
        listOf(
            HomeGesture.SWIPE_UP,
            HomeGesture.SWIPE_DOWN,
            HomeGesture.DOUBLE_TAP,
            HomeGesture.PINCH_IN,
            HomeGesture.PINCH_OUT,
            HomeGesture.TWO_FINGER_SWIPE_UP,
            HomeGesture.TWO_FINGER_SWIPE_DOWN,
        )

    /** Actions offered in the settings picker. */
    val USER_ACTIONS: List<GestureAction> =
        listOf(
            GestureAction.NOOP,
            GestureAction.OPEN_DRAWER,
            GestureAction.EXPAND_NOTIFICATIONS,
            GestureAction.OPEN_SEARCH,
            GestureAction.OPEN_FLEET_TRAY,
            GestureAction.LAUNCH_APP,
            GestureAction.LOCK_SCREEN,
        )

    val DEFAULT: Map<HomeGesture, GestureAction> =
        mapOf(
            HomeGesture.SWIPE_UP to GestureAction.OPEN_DRAWER,
            HomeGesture.SWIPE_DOWN to GestureAction.OPEN_SEARCH,
            HomeGesture.SWIPE_LEFT to GestureAction.NEXT_PAGE,
            HomeGesture.SWIPE_RIGHT to GestureAction.PREV_PAGE,
            HomeGesture.PINCH_IN to GestureAction.OPEN_DRAWER,
            HomeGesture.PINCH_OUT to GestureAction.NOOP,
            HomeGesture.DOUBLE_TAP to GestureAction.NOOP,
            HomeGesture.TWO_FINGER_SWIPE_UP to GestureAction.NOOP,
            HomeGesture.TWO_FINGER_SWIPE_DOWN to GestureAction.EXPAND_NOTIFICATIONS,
        )

    fun action(gesture: HomeGesture, overrides: Map<HomeGesture, GestureAction> = emptyMap()): GestureAction =
        overrides[gesture] ?: DEFAULT[gesture] ?: GestureAction.NOOP

    /** User bindings win; a LAUNCH_APP without a package degrades to NOOP. */
    fun resolve(gesture: HomeGesture, bindings: Map<HomeGesture, GestureBinding>): GestureBinding {
        val b = bindings[gesture] ?: GestureBinding(action(gesture))
        if (b.action == GestureAction.LAUNCH_APP && b.packageName.isNullOrBlank()) return GestureBinding.NONE
        return b
    }

    /** Encodes bindings as `GESTURE=ACTION[:package]` lines. */
    fun encode(bindings: Map<HomeGesture, GestureBinding>): String =
        bindings.entries.joinToString("\n") { (g, b) ->
            val suffix = if (b.action == GestureAction.LAUNCH_APP && !b.packageName.isNullOrBlank()) ":${b.packageName}" else ""
            "${g.name}=${b.action.name}$suffix"
        }

    /** Tolerant decoder: unknown gestures/actions and malformed lines are skipped. */
    fun decode(raw: String?): Map<HomeGesture, GestureBinding> {
        if (raw.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<HomeGesture, GestureBinding>()
        raw.lineSequence().forEach { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val gesture = HomeGesture.entries.firstOrNull { it.name == line.substring(0, eq).trim() } ?: return@forEach
            val rhs = line.substring(eq + 1).trim()
            val colon = rhs.indexOf(':')
            val actionName = if (colon >= 0) rhs.substring(0, colon) else rhs
            val action = GestureAction.entries.firstOrNull { it.name == actionName } ?: return@forEach
            val pkg = if (colon >= 0) rhs.substring(colon + 1).trim().ifBlank { null } else null
            if (action == GestureAction.LAUNCH_APP && pkg == null) return@forEach
            out[gesture] = GestureBinding(action, if (action == GestureAction.LAUNCH_APP) pkg else null)
        }
        return out
    }

    fun classifyDrag(dx: Float, dy: Float, threshold: Float): HomeGesture? {
        if (dx * dx + dy * dy < threshold * threshold) return null
        return if (abs(dy) >= abs(dx)) {
            if (dy < 0) HomeGesture.SWIPE_UP else HomeGesture.SWIPE_DOWN
        } else {
            if (dx < 0) HomeGesture.SWIPE_LEFT else HomeGesture.SWIPE_RIGHT
        }
    }

    /**
     * Classifies a gesture that had two or more pointers down. A span change of
     * more than [pinchRatio] wins over centroid travel; otherwise vertical
     * centroid travel beyond [threshold] is a two-finger swipe.
     */
    fun classifyMultiTouch(
        startSpan: Float,
        endSpan: Float,
        centroidDy: Float,
        threshold: Float,
        pinchRatio: Float = 0.25f,
    ): HomeGesture? {
        if (startSpan > 0f) {
            val ratio = endSpan / startSpan
            if (ratio < 1f - pinchRatio) return HomeGesture.PINCH_IN
            if (ratio > 1f + pinchRatio) return HomeGesture.PINCH_OUT
        }
        if (abs(centroidDy) < threshold) return null
        return if (centroidDy < 0) HomeGesture.TWO_FINGER_SWIPE_UP else HomeGesture.TWO_FINGER_SWIPE_DOWN
    }

    /** True when two taps are close enough in time and space to be a double-tap. */
    fun isDoubleTap(
        prevUpMs: Long,
        downMs: Long,
        dx: Float,
        dy: Float,
        timeoutMs: Long = 300L,
        slop: Float = 100f,
    ): Boolean = prevUpMs > 0 && downMs - prevUpMs in 0..timeoutMs && dx * dx + dy * dy <= slop * slop
}

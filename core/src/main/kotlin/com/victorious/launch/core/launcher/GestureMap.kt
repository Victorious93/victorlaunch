package com.victorious.launch.core.launcher

/**
 * Lawnchair / Launcher3-style home gestures. This is the mapping the
 * Compose workspace implements today; Quickstep recents is not vendored.
 */
enum class HomeGesture {
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT,
    SWIPE_RIGHT,
    PINCH_IN,
    DOUBLE_TAP,
}

enum class GestureAction {
    OPEN_DRAWER,
    OPEN_SEARCH,
    NEXT_PAGE,
    PREV_PAGE,
    NOOP,
}

object GestureMap {
    val DEFAULT: Map<HomeGesture, GestureAction> =
        mapOf(
            HomeGesture.SWIPE_UP to GestureAction.OPEN_DRAWER,
            HomeGesture.SWIPE_DOWN to GestureAction.OPEN_SEARCH,
            HomeGesture.SWIPE_LEFT to GestureAction.NEXT_PAGE,
            HomeGesture.SWIPE_RIGHT to GestureAction.PREV_PAGE,
            HomeGesture.PINCH_IN to GestureAction.OPEN_DRAWER,
            HomeGesture.DOUBLE_TAP to GestureAction.NOOP,
        )

    fun action(gesture: HomeGesture, overrides: Map<HomeGesture, GestureAction> = emptyMap()): GestureAction {
        val resolved = overrides[gesture] ?: DEFAULT[gesture] ?: GestureAction.NOOP
        return when (resolved) {
            GestureAction.OPEN_DRAWER,
            GestureAction.OPEN_SEARCH,
            GestureAction.NEXT_PAGE,
            GestureAction.PREV_PAGE,
            GestureAction.NOOP,
            -> resolved
        }
    }

    fun classifyDrag(dx: Float, dy: Float, threshold: Float): HomeGesture? {
        if (dx * dx + dy * dy < threshold * threshold) return null
        return if (kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
            if (dy < 0) HomeGesture.SWIPE_UP else HomeGesture.SWIPE_DOWN
        } else {
            if (dx < 0) HomeGesture.SWIPE_LEFT else HomeGesture.SWIPE_RIGHT
        }
    }
}

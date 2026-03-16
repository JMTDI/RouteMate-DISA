package com.routemate.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Retained so the accessibility permission setup in MainActivity remains valid.
 * Key handling is now done natively in PopupActivity.onKeyDown().
 */
class RouteMateAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

package com.routemate.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * RouteMateAccessibilityService
 *
 * Primary role: intercept KEYCODE_CALL and KEYCODE_ENDCALL while the
 * RouteMate overlay popup is visible.
 *
 * KEYCODE_CALL and KEYCODE_ENDCALL are consumed by the system's
 * PhoneWindowManager before they reach any app view (including
 * TYPE_APPLICATION_OVERLAY windows). The only app-level hook that fires
 * before the system consumes them is AccessibilityService.onKeyEvent()
 * with FLAG_REQUEST_FILTER_KEY_EVENTS.
 */
class RouteMateAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val om = OverlayManager.activeInstance ?: return false
        if (!om.isShowing) return false
        if (event.action != KeyEvent.ACTION_UP) return false

        return when (event.keyCode) {
            KeyEvent.KEYCODE_CALL -> { om.clickFocusedButton(); true }
            KeyEvent.KEYCODE_ENDCALL,
            KeyEvent.KEYCODE_POWER -> { om.clickCancel(); true }
            else -> false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

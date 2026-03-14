@file:Suppress("DEPRECATION")
package com.routemate.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * OutgoingCallReceiver
 *
 * Registered as an ordered broadcast receiver with priority 999 for
 * android.intent.action.NEW_OUTGOING_CALL.
 *
 * This broadcast fires BEFORE Android actually places a call, so by setting
 * resultData = null we cancel the call completely — the other phone never
 * rings. We then show the RouteMate overlay so the user can choose:
 *   • Via RouteMate (DISA re-dial)
 *   • Direct Call (re-place the original call)
 *   • Cancel (do nothing)
 *
 * Re-dialed calls placed by OverlayManager bypass interception via
 * OverlayManager.suppressNextCall.
 */
class OutgoingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_NEW_OUTGOING_CALL) return

        // resultData is the number about to be dialed; null means already cancelled.
        val number = resultData ?: return

        if (OverlayManager.suppressNextCall) {
            // This OFFHOOK is from our own re-directed call — let it proceed.
            OverlayManager.suppressNextCall = false
            return
        }

        // Cancel the outgoing call — the user will choose what to do via the popup.
        resultData = null

        Handler(Looper.getMainLooper()).post {
            OverlayManager(context.applicationContext).show(number)
        }
    }
}

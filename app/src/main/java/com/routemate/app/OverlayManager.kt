package com.routemate.app

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * OverlayManager
 *
 * Draws a TYPE_APPLICATION_OVERLAY window the moment an outgoing call starts
 * (triggered by RouteMateAccessibilityService via PhoneStateListener OFFHOOK).
 *
 * The user sees the popup BEFORE the remote phone begins ringing, with three
 * choices:
 *
 *  • "Via RouteMate" — hang up the in-flight call, wait 700 ms for the
 *    connection to drop, then re-dial the DISA URI automatically.
 *  • "Direct Call"   — dismiss the overlay; the original call continues.
 *  • "Cancel"        — hang up the call and dismiss.
 *
 * hangUp() uses TelecomManager.endCall() (API 28+, needs ANSWER_PHONE_CALLS)
 * with a reflection fallback for API 26-27.
 */
class OverlayManager(private val context: Context) {

    var isShowing: Boolean = false
        private set

    private var overlayView: PopupRootLayout? = null
    private var cancelButton: Button? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    fun show(number: String) {
        if (isShowing) return
        if (!Settings.canDrawOverlays(context)) return

        val view = LayoutInflater.from(context).inflate(R.layout.overlay_popup, null) as PopupRootLayout

        val tvTitle   = view.findViewById<TextView>(R.id.tv_overlay_title)
        val llCids    = view.findViewById<LinearLayout>(R.id.ll_popup_caller_ids)
        val btnDirect = view.findViewById<Button>(R.id.btn_overlay_direct)
        val btnCancel = view.findViewById<Button>(R.id.btn_overlay_cancel)

        tvTitle.text = context.getString(R.string.overlay_title, number)

        val callerIds = getCallerIds()
        val buttons   = mutableListOf<Button>()

        if (callerIds.isEmpty()) {
            val btn = makeCallerButton(view.context, context.getString(R.string.btn_via_routemate))
            btn.setOnClickListener {
                dismiss()
                handler.postDelayed({ placeDisaCall(context, number) }, 500)
            }
            llCids.addView(btn)
            buttons.add(btn)
        } else {
            for ((name, cidNumber) in callerIds) {
                val btn = makeCallerButton(view.context, name)
                btn.setOnClickListener {
                    dismiss()
                    handler.postDelayed({ placeDisaCall(context, number, cidNumber) }, 500)
                }
                llCids.addView(btn)
                buttons.add(btn)
            }
        }

        // Wire D-pad navigation
        buttons.forEach { it.id = View.generateViewId() }
        for (i in buttons.indices) {
            buttons[i].nextFocusDownId = if (i < buttons.size - 1) buttons[i + 1].id else btnDirect.id
            buttons[i].nextFocusUpId   = if (i > 0) buttons[i - 1].id else btnCancel.id
        }
        btnDirect.nextFocusUpId   = buttons.lastOrNull()?.id ?: btnCancel.id
        btnCancel.nextFocusDownId = buttons.firstOrNull()?.id ?: btnDirect.id

        btnDirect.setOnClickListener {
            dismiss()
            handler.postDelayed({ placeDirectCall(context, number) }, 500)
        }
        btnCancel.setOnClickListener { dismiss() }

        view.onCallKey    = { view.findFocus()?.performClick() }
        view.onEndCallKey = { btnCancel.performClick() }

        val params = buildLayoutParams(focusable = false)
        windowManager.addView(view, params)
        overlayView = view
        cancelButton = btnCancel
        isShowing = true
        activeInstance = this

        // Fallback: if the screen turns off (power button held), dismiss the popup.
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) clickCancel()
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        screenOffReceiver = receiver

        // Switch to focusable so D-pad works inside the popup
        handler.post {
            if (isShowing) {
                try {
                    windowManager.updateViewLayout(view, buildLayoutParams(focusable = true))
                } catch (_: IllegalArgumentException) {}
                buttons.firstOrNull()?.requestFocus()
            }
        }
    }

    fun dismiss() {
        screenOffReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
            screenOffReceiver = null
        }
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {}
        overlayView = null
        cancelButton = null
        isShowing = false
        if (activeInstance === this) activeInstance = null
    }

    /** Called from AccessibilityService.onKeyEvent for KEYCODE_CALL. */
    fun clickFocusedButton() {
        overlayView?.findFocus()?.performClick()
    }

    /** Called from AccessibilityService.onKeyEvent for KEYCODE_ENDCALL. */
    fun clickCancel() {
        cancelButton?.performClick()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Window layout params
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildLayoutParams(focusable: Boolean): WindowManager.LayoutParams {
        val baseFlags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND
        val focusFlag = if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            baseFlags or focusFlag,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.6f
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caller ID helpers (instance-side)
    // ─────────────────────────────────────────────────────────────────────────

    private fun getCallerIds(): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val raw   = prefs.getStringSet(MainActivity.KEY_CALLER_IDS, emptySet()) ?: emptySet()
        return raw.map { entry ->
            val idx = entry.indexOf(MainActivity.CID_SEP)
            if (idx >= 0) entry.substring(0, idx) to entry.substring(idx + MainActivity.CID_SEP.length)
            else entry to entry
        }.sortedBy { it.first }
    }

    private fun makeCallerButton(ctx: android.content.Context, label: String): Button {
        val density = ctx.resources.displayMetrics.density
        val dp8     = (8f  * density).toInt()
        val dp52    = (52f * density).toInt()
        return Button(ctx).apply {
            text     = label
            textSize = 16f
            isAllCaps = false
            setTextColor(ContextCompat.getColor(ctx, R.color.white))
            background = ContextCompat.getDrawable(ctx, R.drawable.focused_button_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp52
            ).apply { bottomMargin = dp8 }
            isFocusable            = true
            isFocusableInTouchMode = true
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Static helpers
    // ─────────────────────────────────────────────────────────────────────────

    companion object {

        /** The currently visible OverlayManager instance, or null if no popup is showing. */
        @Volatile var activeInstance: OverlayManager? = null

        /**
         * Set true before placing a re-directed call so OutgoingCallReceiver lets it through
         * without showing a second popup.
         */
        @Volatile var suppressNextCall: Boolean = false

        /**
         * Build DISA URI and place the call using TelecomManager.
         *
         *   tel:{disaNumber}{initialPause}{pin}#{callerIdPause}{callerId}#{destPause}{destination}#
         *
         * If [callerId] is blank, the caller-ID step is omitted (the DISA system
         * will prompt the user to enter it manually via DTMF).
         */
        fun placeDisaCall(context: Context, destination: String, callerId: String = "") {
            val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
            val disaNumber = prefs.getString(MainActivity.KEY_DISA_NUMBER, "") ?: ""
            val pin        = prefs.getString(MainActivity.KEY_PIN, "") ?: ""
            val initialPauseSeconds    = prefs.getInt(MainActivity.KEY_INITIAL_PAUSE, 1) + 1
            val callerIdPauseSeconds   = prefs.getInt(MainActivity.KEY_CALLER_ID_PAUSE, 1) + 1
            val destPauseSeconds       = prefs.getInt(MainActivity.KEY_DEST_PAUSE, 1) + 1

            val cleanDisa     = disaNumber.replace(Regex("[^0-9+]"), "")
            val cleanPin      = pin.replace(Regex("[^0-9*#]"), "")
            val cleanCallerId = callerId.replace(Regex("[^0-9+*#]"), "")
            val cleanDest     = destination.replace(Regex("[^0-9+*#]"), "")

            val initialPauseStr    = ",".repeat(initialPauseSeconds)
            val callerIdPauseStr   = ",".repeat(callerIdPauseSeconds)
            val destPauseStr       = ",".repeat(destPauseSeconds)

            // IMPORTANT: Uri.parse() treats '#' as a fragment separator, which silently
            // drops everything after the PIN's '#' (including the destination number).
            // Uri.fromParts() encodes '#' as %23 so the full dial string reaches the modem.
            val dialString = buildString {
                append(cleanDisa)
                append(initialPauseStr)
                if (cleanPin.isNotBlank()) {
                    append(cleanPin)
                    append("#")
                }
                if (cleanCallerId.isNotBlank()) {
                    append(callerIdPauseStr)
                    append(cleanCallerId)
                    append("#")
                }
                append(destPauseStr)
                append(cleanDest)
                append("#")
            }

            suppressNextCall = true
            placeCallUri(context, Uri.fromParts("tel", dialString, null))
        }

        /**
         * Re-place the original call to [number] directly (used when the user
         * picks "Direct Call" after we cancelled the in-flight call).
         */
        fun placeDirectCall(context: Context, number: String) {
            val cleanNumber = number.replace(Regex("[^0-9+*#,;]"), "")
            if (cleanNumber.isBlank()) return
            suppressNextCall = true
            placeCallUri(context, Uri.fromParts("tel", cleanNumber, null))
        }

        private fun placeCallUri(context: Context, uri: Uri) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_no_permission),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            try {
                telecomManager.placeCall(uri, Bundle())
            } catch (e: SecurityException) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_call_failed, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

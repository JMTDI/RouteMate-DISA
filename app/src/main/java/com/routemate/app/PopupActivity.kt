package com.routemate.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

/**
 * PopupActivity
 *
 * Transparent dialog-style activity that gives the user a choice when an
 * outgoing call is intercepted. Launched by OutgoingCallReceiver after it
 * cancels the call via resultData = null.
 *
 * Using an Activity (instead of a TYPE_APPLICATION_OVERLAY window) means
 * KEYCODE_ENDCALL is delivered normally via onKeyDown — no accessibility
 * service or special permissions needed for key handling.
 */
class PopupActivity : Activity() {

    companion object {
        private const val EXTRA_NUMBER = "number"

        fun start(context: Context, number: String) {
            context.startActivity(Intent(context, PopupActivity::class.java).apply {
                putExtra(EXTRA_NUMBER, number)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            })
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn on screen if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.overlay_popup)

        val number = intent.getStringExtra(EXTRA_NUMBER) ?: ""

        findViewById<TextView>(R.id.tv_overlay_title).text =
            getString(R.string.overlay_title, number)

        val btnVia    = findViewById<Button>(R.id.btn_overlay_via_routemate)
        val btnDirect = findViewById<Button>(R.id.btn_overlay_direct)
        val btnCancel = findViewById<Button>(R.id.btn_overlay_cancel)

        btnVia.setOnClickListener {
            val callerIds = getSortedCallerIds()
            when {
                callerIds.isEmpty() -> {
                    finish()
                    handler.postDelayed({ OverlayManager.placeDisaCall(applicationContext, number) }, 400)
                }
                callerIds.size == 1 -> {
                    finish()
                    handler.postDelayed({ OverlayManager.placeDisaCall(applicationContext, number, callerIds.first()) }, 400)
                }
                else -> showCallerIdPicker(number, callerIds)
            }
        }

        btnDirect.setOnClickListener {
            finish()
            handler.postDelayed({ OverlayManager.placeDirectCall(applicationContext, number) }, 400)
        }

        btnCancel.setOnClickListener {
            finish()
        }

        btnVia.requestFocus()
    }

    private fun getSortedCallerIds(): List<String> {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getStringSet(MainActivity.KEY_CALLER_IDS, emptySet()) ?: emptySet())
            .toList().sorted()
    }

    private fun showCallerIdPicker(destination: String, callerIds: List<String>) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_caller_id_title))
            .setItems(callerIds.toTypedArray()) { _, which ->
                val selected = callerIds[which]
                finish()
                handler.postDelayed({ OverlayManager.placeDisaCall(applicationContext, destination, selected) }, 400)
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {        return when (keyCode) {
            KeyEvent.KEYCODE_ENDCALL,
            KeyEvent.KEYCODE_POWER -> {
                finish()
                true
            }
            KeyEvent.KEYCODE_CALL -> {
                currentFocus?.performClick()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }
}

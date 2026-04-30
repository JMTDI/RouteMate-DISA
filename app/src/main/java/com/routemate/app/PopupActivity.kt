package com.routemate.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

/**
 * PopupActivity
 *
 * Transparent dialog-style activity that gives the user a choice when an
 * outgoing call is intercepted. Launched by OutgoingCallReceiver after it
 * cancels the call via resultData = null.
 *
 * The first menu shows every configured caller ID by name so the user
 * never has to navigate a second picker. Selecting a name routes the call
 * through DISA with that outbound caller ID. "Direct Call" bypasses DISA.
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

        val btnDirect = findViewById<Button>(R.id.btn_overlay_direct)
        val btnCancel = findViewById<Button>(R.id.btn_overlay_cancel)

        btnDirect.setOnClickListener {
            finish()
            handler.postDelayed({ OverlayManager.placeDirectCall(applicationContext, number) }, 400)
        }

        btnCancel.setOnClickListener { finish() }

        populateCallerIdButtons(number, btnDirect, btnCancel)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dynamic caller ID list
    // ─────────────────────────────────────────────────────────────────────────

    private fun populateCallerIdButtons(number: String, btnDirect: Button, btnCancel: Button) {
        val llContainer = findViewById<LinearLayout>(R.id.ll_popup_caller_ids)
        val callerIds   = getSortedCallerIds()
        val buttons     = mutableListOf<Button>()

        if (callerIds.isEmpty()) {
            // No caller IDs configured — offer a single unnamed DISA route
            val btn = makeCallerButton(getString(R.string.btn_via_routemate))
            btn.setOnClickListener {
                finish()
                handler.postDelayed({ OverlayManager.placeDisaCall(applicationContext, number) }, 400)
            }
            llContainer.addView(btn)
            buttons.add(btn)
        } else {
            for ((name, cidNumber) in callerIds) {
                val btn = makeCallerButton(name)
                btn.setOnClickListener {
                    finish()
                    handler.postDelayed(
                        { OverlayManager.placeDisaCall(applicationContext, number, cidNumber) },
                        400
                    )
                }
                llContainer.addView(btn)
                buttons.add(btn)
            }
        }

        // Assign IDs and wire D-pad navigation:
        //   Cancel ← first caller ID ↕ chain ↕ last caller ID → Direct → Cancel
        buttons.forEach { it.id = View.generateViewId() }
        for (i in buttons.indices) {
            buttons[i].nextFocusDownId = if (i < buttons.size - 1) buttons[i + 1].id else btnDirect.id
            buttons[i].nextFocusUpId   = if (i > 0) buttons[i - 1].id else btnCancel.id
        }
        btnDirect.nextFocusUpId   = buttons.lastOrNull()?.id ?: btnCancel.id
        btnCancel.nextFocusDownId = buttons.firstOrNull()?.id ?: btnDirect.id

        buttons.firstOrNull()?.requestFocus()
    }

    private fun makeCallerButton(label: String): Button {
        val dp8  = (8f  * resources.displayMetrics.density).toInt()
        val dp52 = (52f * resources.displayMetrics.density).toInt()
        return Button(this).apply {
            text        = label
            textSize    = 16f
            isAllCaps   = false
            setTextColor(ContextCompat.getColor(this@PopupActivity, R.color.white))
            background = ContextCompat.getDrawable(this@PopupActivity, R.drawable.focused_button_bg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp52
            ).apply { bottomMargin = dp8 }
            isFocusable         = true
            isFocusableInTouchMode = true
        }
    }

    private fun getSortedCallerIds(): List<Pair<String, String>> {
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val raw   = prefs.getStringSet(MainActivity.KEY_CALLER_IDS, emptySet()) ?: emptySet()
        return raw.map { entry ->
            val idx = entry.indexOf(MainActivity.CID_SEP)
            if (idx >= 0) entry.substring(0, idx) to entry.substring(idx + MainActivity.CID_SEP.length)
            else entry to entry          // backward compat: old entry with no name
        }.sortedBy { it.first }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Key handling
    // ─────────────────────────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
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

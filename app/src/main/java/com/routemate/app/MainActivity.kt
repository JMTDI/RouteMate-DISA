package com.routemate.app

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    companion object {
        const val PREFS_NAME = "RouteMatePref"
        const val KEY_DISA_NUMBER = "disa_number"
        const val KEY_PIN = "pin"
        const val KEY_INITIAL_PAUSE = "initial_pause"
        const val KEY_DEST_PAUSE = "dest_pause"
        const val KEY_CALLER_ID_PAUSE = "caller_id_pause"
        const val KEY_CALLER_IDS = "caller_ids"

        private const val REQUEST_CALL_PERMISSION = 1002
    }

    private lateinit var etDisaNumber: EditText
    private lateinit var etPin: EditText
    private lateinit var spinnerInitialPause: Spinner
    private lateinit var spinnerDestPause: Spinner
    private lateinit var spinnerCallerIdPause: Spinner
    private lateinit var llCallerIds: LinearLayout
    private lateinit var btnAddCallerId: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatusAccessibility: TextView
    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvStatusDisa: TextView

    private var pendingTestNumber: String = ""
    private var pendingCallerId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etDisaNumber = findViewById(R.id.et_disa_number)
        etPin = findViewById(R.id.et_pin)
        spinnerInitialPause = findViewById(R.id.spinner_initial_pause)
        spinnerDestPause = findViewById(R.id.spinner_dest_pause)
        spinnerCallerIdPause = findViewById(R.id.spinner_caller_id_pause)
        llCallerIds = findViewById(R.id.ll_caller_ids)
        btnAddCallerId = findViewById(R.id.btn_add_caller_id)
        btnAccessibility = findViewById(R.id.btn_accessibility)
        btnOverlay = findViewById(R.id.btn_overlay)
        btnSave = findViewById(R.id.btn_save)
        btnTest = findViewById(R.id.btn_test)
        tvStatusAccessibility = findViewById(R.id.tv_status_accessibility)
        tvStatusOverlay = findViewById(R.id.tv_status_overlay)
        tvStatusDisa = findViewById(R.id.tv_status_disa)

        setupSpinners()
        loadSettings()
        updateStatus()

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
        btnSave.setOnClickListener { saveSettings() }
        btnTest.setOnClickListener { showTestDialog() }
        btnAddCallerId.setOnClickListener { showAddCallerIdDialog() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI setup
    // ─────────────────────────────────────────────────────────────────────────

    private fun setupSpinners() {
        val pauseOptions = arrayOf("1 second", "2 seconds", "3 seconds", "4 seconds", "5 seconds")

        val adapterInitial = ArrayAdapter(this, android.R.layout.simple_spinner_item, pauseOptions)
        adapterInitial.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerInitialPause.adapter = adapterInitial

        val adapterDest = ArrayAdapter(this, android.R.layout.simple_spinner_item, pauseOptions)
        adapterDest.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDestPause.adapter = adapterDest

        val adapterCallerIdPause = ArrayAdapter(this, android.R.layout.simple_spinner_item, pauseOptions)
        adapterCallerIdPause.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCallerIdPause.adapter = adapterCallerIdPause
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Settings persistence
    // ─────────────────────────────────────────────────────────────────────────

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        etDisaNumber.setText(prefs.getString(KEY_DISA_NUMBER, ""))
        etPin.setText(prefs.getString(KEY_PIN, ""))
        spinnerInitialPause.setSelection(prefs.getInt(KEY_INITIAL_PAUSE, 1))
        spinnerDestPause.setSelection(prefs.getInt(KEY_DEST_PAUSE, 1))
        spinnerCallerIdPause.setSelection(prefs.getInt(KEY_CALLER_ID_PAUSE, 1))
        refreshCallerIdList(prefs.getStringSet(KEY_CALLER_IDS, emptySet()) ?: emptySet())
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DISA_NUMBER, etDisaNumber.text.toString().trim())
            putString(KEY_PIN, etPin.text.toString().trim())
            putInt(KEY_INITIAL_PAUSE, spinnerInitialPause.selectedItemPosition)
            putInt(KEY_DEST_PAUSE, spinnerDestPause.selectedItemPosition)
            putInt(KEY_CALLER_ID_PAUSE, spinnerCallerIdPause.selectedItemPosition)
            apply()
        }
        Toast.makeText(this, getString(R.string.toast_saved), Toast.LENGTH_SHORT).show()
        updateStatus()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Status panel
    // ─────────────────────────────────────────────────────────────────────────

    private fun updateStatus() {
        // Accessibility service status
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        tvStatusAccessibility.text = if (accessibilityEnabled)
            getString(R.string.status_enabled) else getString(R.string.status_not_enabled)
        tvStatusAccessibility.setTextColor(
            if (accessibilityEnabled) ContextCompat.getColor(this, R.color.accent_green)
            else ContextCompat.getColor(this, R.color.status_error)
        )

        // Draw-over-apps permission status
        val overlayGranted = Settings.canDrawOverlays(this)
        tvStatusOverlay.text = if (overlayGranted)
            getString(R.string.status_allowed) else getString(R.string.status_not_allowed)
        tvStatusOverlay.setTextColor(
            if (overlayGranted) ContextCompat.getColor(this, R.color.accent_green)
            else ContextCompat.getColor(this, R.color.status_error)
        )

        // DISA number configured status
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val disaConfigured = !prefs.getString(KEY_DISA_NUMBER, "").isNullOrBlank()
        tvStatusDisa.text = if (disaConfigured)
            getString(R.string.status_configured) else getString(R.string.status_not_set)
        tvStatusDisa.setTextColor(
            if (disaConfigured) ContextCompat.getColor(this, R.color.accent_green)
            else ContextCompat.getColor(this, R.color.status_error)
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val componentName = "$packageName/.RouteMateAccessibilityService"
        return enabledServices.split(":").any { it.equals(componentName, ignoreCase = true) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test DISA call
    // ─────────────────────────────────────────────────────────────────────────

    private fun showTestDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.hint_test_number)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            isFocusableInTouchMode = true
            setPadding(24, 16, 24, 16)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setHintTextColor(ContextCompat.getColor(context, R.color.hint_color))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_test_title))
            .setMessage(getString(R.string.dialog_test_message))
            .setView(input)
            .setPositiveButton(getString(R.string.dialog_call)) { _, _ ->
                val number = input.text.toString().trim()
                if (number.isNotBlank()) {
                    val callerIds = getSortedCallerIds()
                    when {
                        callerIds.isEmpty() -> checkPermissionAndTestCall(number, "")
                        callerIds.size == 1 -> checkPermissionAndTestCall(number, callerIds.first())
                        else -> showCallerIdPickerForTest(number, callerIds)
                    }
                } else {
                    Toast.makeText(this, getString(R.string.toast_enter_number), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun showCallerIdPickerForTest(destination: String, callerIds: List<String>) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.select_caller_id_title))
            .setItems(callerIds.toTypedArray()) { _, which ->
                checkPermissionAndTestCall(destination, callerIds[which])
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun checkPermissionAndTestCall(number: String, callerId: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTestNumber = number
            pendingCallerId = callerId
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ),
                REQUEST_CALL_PERMISSION
            )
        } else {
            OverlayManager.placeDisaCall(this, number, callerId)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CALL_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingTestNumber.isNotBlank()) {
                    OverlayManager.placeDisaCall(this, pendingTestNumber, pendingCallerId)
                    pendingTestNumber = ""
                    pendingCallerId = ""
                }
            } else {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_perm_title))
                    .setMessage(getString(R.string.dialog_perm_message))
                    .setPositiveButton(getString(R.string.dialog_ok), null)
                    .show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Caller ID management
    // ─────────────────────────────────────────────────────────────────────────

    private fun getSortedCallerIds(): List<String> {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getStringSet(KEY_CALLER_IDS, emptySet()) ?: emptySet()).toList().sorted()
    }

    private fun refreshCallerIdList(ids: Set<String>) {
        llCallerIds.removeAllViews()
        if (ids.isEmpty()) {
            val tv = TextView(this).apply {
                text = getString(R.string.label_no_caller_ids)
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.hint_color))
                setPadding(0, 4, 0, 8)
            }
            llCallerIds.addView(tv)
        } else {
            for (id in ids.sorted()) {
                addCallerIdRow(id)
            }
        }
    }

    private fun addCallerIdRow(number: String) {
        val dp6 = (6 * resources.displayMetrics.density).toInt()
        val dp8 = (8 * resources.displayMetrics.density).toInt()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp6 }
        }

        val tv = TextView(this).apply {
            text = number
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.white))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply { gravity = Gravity.CENTER_VERTICAL }
            setPadding(dp8, 0, 0, 0)
        }

        val btnRemove = Button(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.status_error))
            background = null
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { removeCallerId(number) }
        }

        row.addView(tv)
        row.addView(btnRemove)
        llCallerIds.addView(row)
    }

    private fun removeCallerId(number: String) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ids = (prefs.getStringSet(KEY_CALLER_IDS, emptySet()) ?: emptySet()).toMutableSet()
        ids.remove(number)
        prefs.edit().putStringSet(KEY_CALLER_IDS, ids).apply()
        refreshCallerIdList(ids)
    }

    private fun showAddCallerIdDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.hint_caller_id)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            isFocusableInTouchMode = true
            setPadding(24, 16, 24, 16)
            setTextColor(ContextCompat.getColor(context, R.color.white))
            setHintTextColor(ContextCompat.getColor(context, R.color.hint_color))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_caller_title))
            .setMessage(getString(R.string.dialog_add_caller_message))
            .setView(input)
            .setPositiveButton(getString(R.string.dialog_ok)) { _, _ ->
                val number = input.text.toString().trim().replace(Regex("[^0-9+]"), "")
                if (number.isBlank()) {
                    Toast.makeText(this, getString(R.string.toast_caller_id_empty), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val ids = (prefs.getStringSet(KEY_CALLER_IDS, emptySet()) ?: emptySet()).toMutableSet()
                if (ids.contains(number)) {
                    Toast.makeText(this, getString(R.string.toast_caller_id_duplicate), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                ids.add(number)
                prefs.edit().putStringSet(KEY_CALLER_IDS, ids).apply()
                refreshCallerIdList(ids)
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }
}

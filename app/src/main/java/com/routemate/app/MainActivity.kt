package com.routemate.app

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
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

        private const val REQUEST_CALL_PERMISSION = 1002
    }

    private lateinit var etDisaNumber: EditText
    private lateinit var etPin: EditText
    private lateinit var spinnerInitialPause: Spinner
    private lateinit var spinnerDestPause: Spinner
    private lateinit var btnAccessibility: Button
    private lateinit var btnOverlay: Button
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button
    private lateinit var tvStatusAccessibility: TextView
    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvStatusDisa: TextView

    private var pendingTestNumber: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etDisaNumber = findViewById(R.id.et_disa_number)
        etPin = findViewById(R.id.et_pin)
        spinnerInitialPause = findViewById(R.id.spinner_initial_pause)
        spinnerDestPause = findViewById(R.id.spinner_dest_pause)
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
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_DISA_NUMBER, etDisaNumber.text.toString().trim())
            putString(KEY_PIN, etPin.text.toString().trim())
            putInt(KEY_INITIAL_PAUSE, spinnerInitialPause.selectedItemPosition)
            putInt(KEY_DEST_PAUSE, spinnerDestPause.selectedItemPosition)
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
                    checkPermissionAndTestCall(number)
                } else {
                    Toast.makeText(this, getString(R.string.toast_enter_number), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
            .show()
    }

    private fun checkPermissionAndTestCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTestNumber = number
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS
                ),
                REQUEST_CALL_PERMISSION
            )
        } else {
            OverlayManager.placeDisaCall(this, number)
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
                    OverlayManager.placeDisaCall(this, pendingTestNumber)
                    pendingTestNumber = ""
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
}

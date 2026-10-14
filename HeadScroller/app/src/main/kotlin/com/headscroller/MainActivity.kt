package com.headscroller.headscroller

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.headscroller.headscroller.service.HeadTrackingService
import com.headscroller.headscroller.service.ScrollAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnCalibrate: Button
    private lateinit var btnSettings: Button
    private lateinit var tvStatus: TextView
    private var isTracking = false
    private var isCalibrated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggle = findViewById(R.id.btn_toggle)
        btnCalibrate = findViewById(R.id.btn_calibrate)
        btnSettings = findViewById(R.id.btn_settings)
        tvStatus = findViewById(R.id.tv_status)

        btnToggle.setOnClickListener {
            toggleTracking()
        }

        btnCalibrate.setOnClickListener {
            calibrateHeadPosition()
        }

        btnSettings.setOnClickListener {
            openSettings()
        }

        checkPermissionsAndStart()
    }

    private fun toggleTracking() {
        if (isTracking) {
            stopTracking()
        } else {
            startTracking()
        }
    }

    private fun startTracking() {
        if (!isCalibrated) {
            Toast.makeText(this, "Please calibrate first", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            isTracking = true
            btnToggle.setText(R.string.stop_head_tracking)
            tvStatus.setText(R.string.service_active)
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            ContextCompat.startForegroundService(this, Intent(this, HeadTrackingService::class.java))
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    private fun stopTracking() {
        isTracking = false
        btnToggle.setText(R.string.toggle_head_tracking)
        tvStatus.setText(R.string.service_inactive)
        tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        stopService(Intent(this, HeadTrackingService::class.java))
    }

    private fun calibrateHeadPosition() {
        // TODO: Send intent to calibration workflow
        isCalibrated = true
        Toast.makeText(this, "Calibrated - neutral position set", Toast.LENGTH_SHORT).show()
    }

    private fun openSettings() {
        // TODO: Open settings activity
        Toast.makeText(this, "Opening settings", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissionsAndStart() {
        val cameraPermission = checkPermission(Manifest.permission.CAMERA)
        if (cameraPermission == PackageManager.PERMISSION_GRANTED) {
            // Permission already granted, check calibration
        }
    }

    private fun checkPermission(permission: String): Int {
        return ContextCompat.checkSelfPermission(this, permission)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startTracking()
        } else {
            Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
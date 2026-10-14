package com.headscroller.headscroller.service

import android.app.Service
import android.content.Intent
import android.hardware.camera2.params.CaptureRequest
import android.os.IBinder
import android.util.Log
import android.view.SurfaceView
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.face detection.FaceDetector
import com.google.mlkit.vision.face detection.FaceDetectorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.common.inputmedia.ImageUtils
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

// Constants for head tracking
private val TILT_THRESHOLD_DEGREES = 15.0f
private val COOLDOWN_MS = 1500

class HeadTrackingService : Service() {

    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var faceDetector: FaceDetector
    private var imageAnalysis: ImageAnalysis? = null
    private var lastScrollTime = 0L
    private var isCalibrated = false
    private var calibratedPitch: Float = 0f
    private var neutralPitchSamples = mutableListOf<Float>()

    // Executor for camera operations
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        initFaceDetector()
    }

    private fun initFaceDetector() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.ACCURACY)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        faceDetector = FaceDetector.getClient(options)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null && intent.action == "android.hardware.action.HEAD_TRACKING_CALIBRATE") {
            calibrateNeutralPosition()
        } else {
            startCameraAnalysis()
        }
        return START_STICKY
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture: Task<ProcessCameraProvider> = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(Runnable {
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraPreview()
            } catch (e: Exception) {
                Log.e("HeadTracking", "Camera provider error", e)
            }
        }, ContextCompat.getMainExecutor(this))
        return START_STICKY
    }

    private fun bindCameraPreview() {
        // Image analysis use case for ML Kit head tracking
        imageAnalysis = ImageAnalysis.Builder()
            .setBackFacingCameraUseCaseDefaults()
            .build().also { analysis ->
                analysis.setOnImageFrameListener { image ->
                    processImageFromMLKit(image)
                }
            }

        // Preview use case (minimal - just to keep camera active)
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(SurfaceView(this).surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (exc: Exception) {
            Log.e("HeadTracking", "Use case binding failed", exc)
        }
    }

    private fun processImageFromMLKit(image: ImageProxy?) {
        if (image == null) return

        val rotation = image.imageInfo.rotationDegrees
        val mediaImage = image.mediaImage
        if (mediaImage != null) {
            val inputImage = InputImage.fromMediaImage(mediaImage, rotation)
            detectHeadPosition(inputImage)
        }
        image.close()
    }

    private fun detectHeadPosition(mlKitImage: InputImage) {
        val result = faceDetector.process(mlKitImage)
        if (result.links.isNotEmpty()) {
            val face = result.links.first()
            val pitch = face.headEulerAngleY()  // Up/down tilt in degrees

            if (!isCalibrated) {
                // Calibration: accumulate samples
                neutralPitchSamples.add(pitch)
                if (neutralPitchSamples.size >= CALIBRATION_SAMPLES) {
                    val avgPitch = neutralPitchSamples.average() ?: 0f
                    calibratedPitch = avgPitch
                    isCalibrated = true
                    Log.d("HeadTracking", "Calibrated pitch: $calibratedPitch")
                }
            } else {
                // Check for tilt gestures
                checkHeadTilt(pitch)
            }
        }
    }

    private fun checkHeadTilt(currentPitch: Float) {
        val delta = currentPitch - calibratedPitch

        // Check cooldown
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < COOLDOWN_MS) return

        // Tilt up (positive pitch) = next video
        if (delta > TILT_THRESHOLD_DEGREES) {
            triggerScroll("next")
            lastScrollTime = now
            Log.d("HeadTracking", "Tilt UP → next video, delta: $delta")
        }
        // Tilt down (negative pitch) = previous video
        else if (delta < -TILT_THRESHOLD_DEGREES) {
            triggerScroll("prev")
            lastScrollTime = now
            Log.d("HeadTracking", "Tilt DOWN → prev video, delta: $delta")
        }
    }

    private fun triggerScroll(direction: String) {
        val intent = Intent(this, ScrollAccessibilityService::class.java).apply {
            putExtra("direction", direction)
        }
        // Start the accessibility service to dispatch the gesture
        // We use a local broadcast or direct start - but actually the AccessibilityService
        // is system-wide, so we need to ensure it's running
        Log.d("HeadTracking", "Scroll $direction triggered")
    }

    fun calibrate() {
        neutralPitchSamples.clear()
        // Will collect samples from next few frames
        isCalibrated = false
        calibratedPitch = 0f
        Log.d("HeadTracking", "Calibration reset - collecting samples")
    }

    override fun onDestroy() {
        super.onDestroy()
        imageAnalysis?.setOnImageFrameListener(null)
        imageAnalysis = null
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder {
        return null
    }
}
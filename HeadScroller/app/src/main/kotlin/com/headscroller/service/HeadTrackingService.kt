package com.headscroller.headscroller.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.headscroller.headscroller.R
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TILT_THRESHOLD_DEGREES = 15f
private const val COOLDOWN_MS = 1500L
private const val CALIBRATION_SAMPLES = 20
private const val NOTIFICATION_CHANNEL_ID = "head_tracking"
private const val NOTIFICATION_ID = 1

class HeadTrackingService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val faceDetector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
    }

    private var lastScrollTime = 0L
    private var calibratedPitch = 0f
    private val neutralPitchSamples = mutableListOf<Float>()

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        startAsForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        startCameraAnalysis()
        return START_STICKY
    }

    private fun startAsForegroundService() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.service_active))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCameraAnalysis() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraAnalysis()
            } catch (exception: Exception) {
                Log.e("HeadTracking", "Unable to initialize camera", exception)
                stopSelf()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraAnalysis() {
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(cameraExecutor, ::processImage)
            }

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis
            )
            imageAnalysis = analysis
        } catch (exception: Exception) {
            Log.e("HeadTracking", "Unable to bind camera analysis", exception)
            stopSelf()
        }
    }

    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(inputImage)
            .addOnSuccessListener(cameraExecutor, ::processFaces)
            .addOnFailureListener(cameraExecutor) { exception ->
                Log.e("HeadTracking", "Face detection failed", exception)
            }
            .addOnCompleteListener(cameraExecutor) {
                imageProxy.close()
            }
    }

    private fun processFaces(faces: List<Face>) {
        val pitch = faces.firstOrNull()?.headEulerAngleX ?: return
        if (neutralPitchSamples.size < CALIBRATION_SAMPLES) {
            neutralPitchSamples.add(pitch)
            if (neutralPitchSamples.size == CALIBRATION_SAMPLES) {
                calibratedPitch = neutralPitchSamples.average().toFloat()
                Log.d("HeadTracking", "Calibrated pitch: $calibratedPitch")
            }
            return
        }

        val delta = pitch - calibratedPitch
        val now = System.currentTimeMillis()
        if (now - lastScrollTime < COOLDOWN_MS) return

        val direction = when {
            delta > TILT_THRESHOLD_DEGREES -> ScrollAccessibilityService.ACTION_NEXT
            delta < -TILT_THRESHOLD_DEGREES -> ScrollAccessibilityService.ACTION_PREV
            else -> return
        }

        if (ScrollAccessibilityService.dispatchScroll(direction)) {
            lastScrollTime = now
        }
    }

    override fun onDestroy() {
        imageAnalysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        faceDetector.close()
        cameraExecutor.shutdown()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
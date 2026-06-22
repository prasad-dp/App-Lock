package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object IntruderCameraHelper {
    private const val TAG = "IntruderCameraHelper"

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun captureIntruderPhoto(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        onPhotoCaptured: (File) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (!hasCameraPermission(context)) {
            val err = "Camera permission not granted"
            Log.e(TAG, err)
            onError(SecurityException(err))
            return
        }

        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()

                    val imageCapture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()

                    val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        throw IllegalStateException("No front or back camera is available on this device")
                    }

                    // Unbind any existing usecases first
                    cameraProvider.unbindAll()

                    // Bind to lifecycle
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageCapture
                    )

                    // Define output file
                    val intrudersDir = File(context.filesDir, "intruders").apply {
                        if (!exists()) mkdirs()
                    }
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
                    val photoFile = File(intrudersDir, "intruder_$timeStamp.jpg")

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                Log.d(TAG, "Photo captured successfully: ${photoFile.absolutePath}")
                                onPhotoCaptured(photoFile)
                                try {
                                    cameraProvider.unbindAll()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Unbind error", e)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e(TAG, "CameraX save error: ${exception.message}", exception)
                                onError(exception)
                                try {
                                    cameraProvider.unbindAll()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Unbind error", e)
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "ProcessCameraProvider error in callback: ${e.message}", e)
                    onError(e)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (e: Exception) {
            Log.e(TAG, "ProcessCameraProvider initialization error: ${e.message}", e)
            onError(e)
        }
    }
}

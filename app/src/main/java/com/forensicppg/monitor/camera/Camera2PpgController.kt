package com.forensicppg.monitor.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Controlador de cámara optimizado para adquisición de PPG.
 * Gestiona exposición manual y activación de torch (flash).
 */
@ExperimentalCamera2Interop
class Camera2PpgController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val analyzer: ImageAnalysis.Analyzer
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    fun start() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        // Configuración de Análisis de Imagen
        val analysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)

        // Camera2 Interop para control total de exposición y flash
        val camera2Extender = Camera2Interop.Extender(analysisBuilder)
        
        // Desactivar AE para evitar fluctuaciones de brillo que arruinan el PPG
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_OFF
        )
        
        // Exposición fija (1/100s) e ISO fijo para estabilidad absoluta
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.SENSOR_EXPOSURE_TIME,
            10_000_000L // 10ms
        )
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.SENSOR_SENSITIVITY,
            200 // ISO 200 razonable para dedo + flash
        )
        
        // Forzar Torch (Flash)
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.FLASH_MODE,
            CaptureRequest.FLASH_MODE_TORCH
        )

        val imageAnalysis = analysisBuilder.build()
        imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )
        } catch (e: Exception) {
            // Error al vincular cámara
        }
    }

    fun stop() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}

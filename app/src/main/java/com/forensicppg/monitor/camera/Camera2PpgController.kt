package com.forensicppg.monitor.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CaptureRequest
import android.util.Range
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
 * Controlador de cámara optimizado de grado forense para PPG.
 * Gestiona FPS dinámicos altos (60fps), exposición manual fija absoluta
 * y activación continua de flash (torch) para evitar inyección de ruido fotónico.
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

        // Configuración de Análisis de Imagen optimizada para baja latencia
        val analysisBuilder = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)

        // Camera2 Interop para control de bajo nivel
        val camera2Extender = Camera2Interop.Extender(analysisBuilder)
        
        // Desactivar totalmente AE, AWB y AF para estabilidad absoluta
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_MODE_OFF
        )
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AWB_MODE,
            CaptureRequest.CONTROL_AWB_MODE_OFF
        )
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AF_MODE,
            CaptureRequest.CONTROL_AF_MODE_OFF
        )

        // Intentar forzar el rango de FPS máximo (60fps si está disponible)
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
            Range(60, 60)
        )
        
        // Exposición manual ultracorta (5ms) para capturar microvariaciones en 60fps
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.SENSOR_EXPOSURE_TIME,
            5_000_000L // 5ms
        )
        
        // ISO alto pero no máximo, para compensar la exposición corta + flash
        camera2Extender.setCaptureRequestOption(
            CaptureRequest.SENSOR_SENSITIVITY,
            400 
        )
        
        // Forzar Torch (Flash) constante e ininterrumpido
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
            e.printStackTrace()
        }
    }

    fun stop() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}

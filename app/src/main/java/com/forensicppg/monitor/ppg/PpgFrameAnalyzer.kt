package com.forensicppg.monitor.ppg

import android.media.Image
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.forensicppg.monitor.domain.PpgSample

/**
 * Analizador de frames para extracción de señales PPG.
 * OBLIGATORIO: Zero Simulación. Si no hay señal real, devuelve null.
 */
class PpgFrameAnalyzer(
    private val onSampleReady: (PpgSample) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastTimestamp: Long = 0
    private var frameCount: Int = 0
    private var startTime: Long = 0

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val timestamp = imageProxy.imageInfo.timestamp
        if (startTime == 0L) startTime = timestamp
        
        // Medición de FPS real
        frameCount++
        val elapsed = (timestamp - startTime) / 1_000_000_000f
        val fps = if (elapsed > 0) frameCount / elapsed else 0f

        // 1. Extraer datos YUV y convertir a métricas robustas
        val sample = processYuvImage(image, timestamp, fps)

        // 2. Aplicar Gates de Calidad (Zero Simulación)
        if (sample != null && isQualityAcceptable(sample)) {
            onSampleReady(sample)
        }

        imageProxy.close()
    }

    private fun processYuvImage(image: Image, timestamp: Long, fps: Float): PpgSample? {
        // TODO: Implementar conversión YUV -> RGB robusta con ROI dinámico
        // De acuerdo al contrato técnico:
        // - Excluir píxeles con clipping alto/bajo
        // - Calcular maskCoverage y contactScore
        
        // Por ahora devolvemos un placeholder que indica "Buscando contacto"
        // En producción esto no debe usar Math.random()
        
        return null // Fallback cerrado: si no está implementado el cálculo real, no emitimos nada.
    }

    private fun isQualityAcceptable(sample: PpgSample): Boolean {
        return sample.contactScore > 0.70f && 
               sample.maskCoverage > 0.80f && 
               sample.clippingHigh < 0.10f
    }
}

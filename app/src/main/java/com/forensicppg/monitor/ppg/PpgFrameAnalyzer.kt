package com.forensicppg.monitor.ppg

import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.forensicppg.monitor.domain.PpgSample
import kotlin.math.ln

/**
 * Analizador de frames para extracción de señales PPG de grado clínico.
 * Implementa Zero-Allocation (cero recolección de basura por frame).
 * Extrae canales R, G, y B requeridos para el algoritmo POS y aproximación SpO2.
 */
class PpgFrameAnalyzer(
    private val onSampleReady: (PpgSample) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameCount: Int = 0
    private var startTime: Long = 0
    
    // Objeto pre-asignado para evitar Garbage Collection stutters
    private val reusableSample = PpgSample()

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val timestamp = imageProxy.imageInfo.timestamp
        if (startTime == 0L) startTime = timestamp
        
        frameCount++
        val elapsed = (timestamp - startTime) / 1_000_000_000f
        val fps = if (elapsed > 0) frameCount / elapsed else 0f

        processYuvImage(image, timestamp, fps)

        // Quality Gate: Solo reportamos la muestra si pasa las métricas de contacto
        if (reusableSample.rejectReason == null) {
            onSampleReady(reusableSample)
        }

        imageProxy.close()
    }

    private fun processYuvImage(image: Image, timestamp: Long, fps: Float) {
        val width = image.width
        val height = image.height
        
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        // ROI Central aumentado (100x100) para mayor estabilidad de promediado espacial
        val roiSize = 100.coerceAtMost(width / 2).coerceAtMost(height / 2)
        val startX = (width - roiSize) / 2
        val startY = (height - roiSize) / 2

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var validPixels = 0
        var clippingHighCount = 0
        var clippingLowCount = 0

        for (y in startY until startY + roiSize) {
            for (x in startX until startX + roiSize) {
                val yIdx = y * yRowStride + x
                val uvIdx = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                // YUV420 a RGB (Integer math para performance)
                val yVal = (yBuffer[yIdx].toInt() and 0xFF)
                val uVal = (uBuffer[uvIdx].toInt() and 0xFF) - 128
                val vVal = (vBuffer[uvIdx].toInt() and 0xFF) - 128

                var r = (yVal + 1.402f * vVal).toInt()
                var g = (yVal - 0.344136f * uVal - 0.714136f * vVal).toInt()
                var b = (yVal + 1.772f * uVal).toInt()

                if (r > 255) r = 255 else if (r < 0) r = 0
                if (g > 255) g = 255 else if (g < 0) g = 0
                if (b > 255) b = 255 else if (b < 0) b = 0

                if (r > 250 || g > 250 || b > 250) clippingHighCount++
                if (yVal < 8) clippingLowCount++

                // Criterio estricto de contacto: Señal PPG requiere predominancia ROJA fuerte (luz del flash a través de tejido)
                if (r > g * 1.5f && r > b * 1.5f) {
                    sumR += r
                    sumG += g
                    sumB += b
                    validPixels++
                }
            }
        }

        val totalRoiPixels = roiSize * roiSize
        val maskCoverage = validPixels.toFloat() / totalRoiPixels
        val clippingHigh = clippingHighCount.toFloat() / totalRoiPixels
        val clippingLow = clippingLowCount.toFloat() / totalRoiPixels

        val contactScore = (maskCoverage * (1f - clippingHigh) * (1f - clippingLow)).coerceIn(0f, 1f)

        // Si no hay suficiente cobertura de tejido, marcamos como inválido
        if (validPixels < (totalRoiPixels * 0.2f)) {
            reusableSample.rejectReason = "NO_FINGER"
            return
        }

        val avgR = sumR.toFloat() / validPixels
        val avgG = sumG.toFloat() / validPixels
        val avgB = sumB.toFloat() / validPixels

        // Absorbancia relativa (Beer-Lambert: A = -ln(I/I0))
        // Utilizamos el ln directo + epsilon para evitar infinito
        val ppgRed = -ln((avgR + 0.1f) / 256f)
        val ppgGreen = -ln((avgG + 0.1f) / 256f)
        val ppgBlue = -ln((avgB + 0.1f) / 256f)

        reusableSample.update(
            timestamp = timestamp,
            effectiveFps = fps,
            rawRed = avgR,
            rawGreen = avgG,
            rawBlue = avgB,
            ppgGreenAbsorbance = ppgGreen,
            ppgRedAbsorbance = ppgRed,
            ppgBlueAbsorbance = ppgBlue,
            maskCoverage = maskCoverage,
            contactScore = contactScore,
            clippingHigh = clippingHigh,
            clippingLow = clippingLow,
            motionOptical = 0f,
            rejectReason = if (contactScore < 0.6f) "BAD_CONTACT" else null
        )
    }
}

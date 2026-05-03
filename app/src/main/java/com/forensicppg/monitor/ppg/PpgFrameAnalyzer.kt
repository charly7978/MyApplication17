package com.forensicppg.monitor.ppg

import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.forensicppg.monitor.domain.PpgSample
import kotlin.math.ln

/**
 * Analizador de frames para extracción de señales PPG de grado clínico.
 * Implementa Zero-Allocation y validación óptica de tejido estricta.
 */
class PpgFrameAnalyzer(
    private val onSampleReady: (PpgSample) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameCount: Int = 0
    private var startTime: Long = 0
    
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

        // Enviamos siempre la muestra para que el procesador sepa si debe cortar la onda
        onSampleReady(reusableSample)

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

        // ROI Central 100x100
        val roiSize = 100.coerceAtMost(width / 2).coerceAtMost(height / 2)
        val startX = (width - roiSize) / 2
        val startY = (height - roiSize) / 2

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sumR2 = 0L // Para varianza espacial
        
        var validPixels = 0
        var clippingLowCount = 0

        for (y in startY until startY + roiSize) {
            for (x in startX until startX + roiSize) {
                val yIdx = y * yRowStride + x
                val uvIdx = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                val yVal = (yBuffer[yIdx].toInt() and 0xFF)
                val uVal = (uBuffer[uvIdx].toInt() and 0xFF) - 128
                val vVal = (vBuffer[uvIdx].toInt() and 0xFF) - 128

                var r = (yVal + 1.402f * vVal).toInt()
                var g = (yVal - 0.344136f * uVal - 0.714136f * vVal).toInt()
                var b = (yVal + 1.772f * uVal).toInt()

                if (r > 255) r = 255 else if (r < 0) r = 0
                if (g > 255) g = 255 else if (g < 0) g = 0
                if (b > 255) b = 255 else if (b < 0) b = 0

                if (yVal < 5) clippingLowCount++

                // Un dedo sobre el flash es > 90% rojo puro. 
                sumR += r
                sumG += g
                sumB += b
                sumR2 += (r * r).toLong()
                validPixels++
            }
        }

        val totalRoiPixels = roiSize * roiSize
        
        val avgR = sumR.toFloat() / validPixels
        val avgG = sumG.toFloat() / validPixels
        val avgB = sumB.toFloat() / validPixels
        
        // Varianza espacial del rojo: Var(X) = E[X^2] - (E[X])^2
        val avgR2 = sumR2.toFloat() / validPixels
        val varianceR = avgR2 - (avgR * avgR)
        
        // COMPUERTA FORENSE ÓPTICA:
        // 1. Rojo muy alto (saturación de flash por capilares)
        // 2. Verde/Azul muy bajos (la sangre los absorbe masivamente)
        // 3. Varianza extremadamente baja (difusión isotrópica del tejido celular, no hay textura)
        val isFingerPresent = avgR > 180f && avgG < 120f && avgB < 120f && varianceR < 500f && (clippingLowCount.toFloat()/totalRoiPixels) < 0.5f

        val contactScore = if (isFingerPresent) 1.0f else 0.0f

        if (!isFingerPresent) {
            reusableSample.rejectReason = "NO_FINGER_DETECTED"
            reusableSample.contactScore = 0f
            return
        }

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
            maskCoverage = 1.0f,
            contactScore = contactScore,
            clippingHigh = 0f,
            clippingLow = clippingLowCount.toFloat() / totalRoiPixels,
            motionOptical = varianceR, // Guardamos la varianza como referencia
            rejectReason = null
        )
    }
}

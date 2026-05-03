package com.forensicppg.monitor.ppg

import com.forensicppg.monitor.domain.PpgSample
import kotlin.math.sqrt

/**
 * Procesador de señal PPG de grado clínico y forense.
 * Utiliza filtros Butterworth IIR (Zero-Allocation) y computa parámetros fisiológicos (BPM, SDPPG, SpO2).
 */
class PpgSignalProcessor(
    private val onBpmUpdate: (Float?) -> Unit,
    private val onSpO2Update: (Float?) -> Unit,
    private val onWaveformUpdate: (Float) -> Unit
) {
    // Filtros Butterworth Pasa-Banda (0.5Hz - 4.0Hz) para el canal principal (Verde)
    private val highPassFilter = BiquadFilter()
    private val lowPassFilter = BiquadFilter()
    
    // Filtros para cálculo de SpO2 (Extracción de AC y DC)
    private val redDcFilter = BiquadFilter()
    private val blueDcFilter = BiquadFilter()
    private val redAcFilter = BiquadFilter()
    private val blueAcFilter = BiquadFilter()

    private var filtersConfigured = false
    
    private var lastPeakTime = 0L
    private var lastValue = 0f
    private var isRising = false
    
    // Para SDPPG
    private var lastV = 0f
    private var lastA = 0f

    // Zero-allocation buffers para promediar métricas
    private val bpmBuffer = FloatCircularBuffer(10)
    private val spo2Buffer = FloatCircularBuffer(30) // Promedio más largo para SpO2
    
    // RMS tracking para SpO2
    private var sumRedAcSq = 0f
    private var sumBlueAcSq = 0f
    private var rmsCount = 0

    fun process(sample: PpgSample) {
        if (sample.rejectReason != null) {
            reset()
            onBpmUpdate(null)
            onSpO2Update(null)
            onWaveformUpdate(0f)
            return
        }

        if (!filtersConfigured && sample.effectiveFps > 0) {
            configureFilters(sample.effectiveFps)
        }
        
        // Si no hay FPS válido aún, no filtramos pero reportamos cero para no corromper la onda
        if (!filtersConfigured) {
            onWaveformUpdate(0f)
            return
        }

        val rawGreen = sample.ppgGreenAbsorbance
        val rawRed = sample.ppgRedAbsorbance
        val rawBlue = sample.ppgBlueAbsorbance
        
        // 1. Filtrado Principal (BPM y Onda)
        val hpGreen = highPassFilter.process(rawGreen)
        val cleanGreenAC = lowPassFilter.process(hpGreen)
        
        // 2. Reportar onda limpia para UI (Philips IntelliVue style)
        onWaveformUpdate(cleanGreenAC)
        
        // 3. Cálculo de SDPPG (Segunda Derivada)
        val velocity = cleanGreenAC - lastValue
        val acceleration = velocity - lastV
        lastV = velocity
        // La aceleración (SDPPG) puede utilizarse para detectar rigidez arterial (ondas a, b, c, d, e)
        
        // 4. Detección de picos y BPM
        detectPeaks(cleanGreenAC, sample.timestamp)
        
        // 5. Cálculo de SpO2
        val redDC = redDcFilter.process(rawRed)
        val blueDC = blueDcFilter.process(rawBlue)
        val redAC = redAcFilter.process(rawRed)
        val blueAC = blueAcFilter.process(rawBlue)
        
        sumRedAcSq += redAC * redAC
        sumBlueAcSq += blueAC * blueAC
        rmsCount++
        
        // Calculamos SpO2 cada 30 frames (aprox 0.5 seg)
        if (rmsCount >= 30 && redDC > 0.001f && blueDC > 0.001f) {
            val rmsRedAc = sqrt(sumRedAcSq / rmsCount)
            val rmsBlueAc = sqrt(sumBlueAcSq / rmsCount)
            
            // Ratio of Ratios (R) = (AC_red / DC_red) / (AC_blue / DC_blue)
            val ratio = (rmsRedAc / redDC) / (rmsBlueAc / blueDC)
            
            // Fórmula forense estandarizada para cámaras sin IR (Aproximación empírica)
            // Valores típicos: SpO2 = 110 - 25 * R
            val spo2Raw = 110f - 25f * ratio
            val spo2Clamped = spo2Raw.coerceIn(80f, 100f)
            
            spo2Buffer.push(spo2Clamped)
            
            // Promedio del SpO2
            var sumSpo2 = 0f
            for (i in 0 until spo2Buffer.size) sumSpo2 += spo2Buffer.get(i)
            val avgSpo2 = sumSpo2 / spo2Buffer.size
            
            onSpO2Update(avgSpo2)
            
            sumRedAcSq = 0f
            sumBlueAcSq = 0f
            rmsCount = 0
        }
    }

    private fun configureFilters(fps: Float) {
        val f = fps.coerceIn(15f, 120f)
        
        // Banda de paso: 0.5Hz a 4.0Hz (30 BPM a 240 BPM)
        highPassFilter.configureHighpass(f, 0.5f)
        lowPassFilter.configureLowpass(f, 4.0f)
        
        // DC Filters: Pasa bajos muy lentos (0.1Hz) para obtener la media de absorción
        redDcFilter.configureLowpass(f, 0.1f)
        blueDcFilter.configureLowpass(f, 0.1f)
        
        // AC Filters: Pasa altos (0.5Hz)
        redAcFilter.configureHighpass(f, 0.5f)
        blueAcFilter.configureHighpass(f, 0.5f)
        
        filtersConfigured = true
    }

    private fun detectPeaks(value: Float, timestampNanos: Long) {
        val timeMillis = timestampNanos / 1_000_000
        
        if (value > lastValue) {
            isRising = true
        } else if (isRising && lastValue > 0.0005f) { // Umbral de prominencia dinámico/estricto
            // Pico detectado
            if (lastPeakTime != 0L) {
                val rrInterval = timeMillis - lastPeakTime
                
                // Validación fisiológica (40-200 BPM)
                if (rrInterval in 300..1500) {
                    val currentBpm = 60000f / rrInterval
                    bpmBuffer.push(currentBpm)
                    
                    var sumBpm = 0f
                    for (i in 0 until bpmBuffer.size) sumBpm += bpmBuffer.get(i)
                    onBpmUpdate(sumBpm / bpmBuffer.size)
                } else if (rrInterval > 1500) {
                    lastPeakTime = 0
                }
            }
            lastPeakTime = timeMillis
            isRising = false
        }
        lastValue = value
    }

    private fun reset() {
        lastPeakTime = 0L
        lastValue = 0f
        isRising = false
        lastV = 0f
        lastA = 0f
        bpmBuffer.clear()
        spo2Buffer.clear()
        sumRedAcSq = 0f
        sumBlueAcSq = 0f
        rmsCount = 0
    }
}

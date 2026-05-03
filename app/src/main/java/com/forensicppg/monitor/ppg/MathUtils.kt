package com.forensicppg.monitor.ppg

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.tan

/**
 * Búfer circular de tamaño fijo para evitar asignaciones de memoria dinámicas.
 */
class FloatCircularBuffer(val capacity: Int) {
    private val buffer = FloatArray(capacity)
    private var head = 0
    private var count = 0

    fun push(value: Float) {
        buffer[head] = value
        head = (head + 1) % capacity
        if (count < capacity) count++
    }

    fun get(offset: Int): Float {
        if (offset >= count || offset < 0) return 0f
        var index = head - 1 - offset
        if (index < 0) index += capacity
        return buffer[index]
    }
    
    val size: Int get() = count
    
    fun clear() {
        head = 0
        count = 0
        for (i in buffer.indices) buffer[i] = 0f
    }
}

/**
 * Filtro IIR Biquad para procesamiento de señales forenses.
 * Implementación de la forma directa I.
 */
class BiquadFilter {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var x1 = 0f
    private var x2 = 0f
    private var y1 = 0f
    private var y2 = 0f

    /**
     * Configura el filtro como un Pasa-Banda Butterworth de 2do orden.
     */
    fun configureBandpass(sampleRate: Float, centerFreq: Float, bandwidth: Float) {
        val w0 = 2f * PI.toFloat() * centerFreq / sampleRate
        val alpha = sin(w0) * sin(ln(2.0).toFloat() / 2f * bandwidth * w0 / sin(w0))
        
        val a0 = 1f + alpha
        b0 = alpha / a0
        b1 = 0f
        b2 = -alpha / a0
        a1 = (-2f * cos(w0)) / a0
        a2 = (1f - alpha) / a0
    }
    
    /**
     * Configura un pasa-bajos simple Butterworth.
     */
    fun configureLowpass(sampleRate: Float, cutoffFreq: Float) {
        val w0 = 2f * PI.toFloat() * cutoffFreq / sampleRate
        val alpha = sin(w0) / (2f * 0.70710678f) // Q = 1/sqrt(2) for Butterworth
        
        val cosw0 = cos(w0)
        val a0 = 1f + alpha
        b0 = ((1f - cosw0) / 2f) / a0
        b1 = (1f - cosw0) / a0
        b2 = ((1f - cosw0) / 2f) / a0
        a1 = (-2f * cosw0) / a0
        a2 = (1f - alpha) / a0
    }
    
    /**
     * Configura un pasa-altos simple Butterworth.
     */
    fun configureHighpass(sampleRate: Float, cutoffFreq: Float) {
        val w0 = 2f * PI.toFloat() * cutoffFreq / sampleRate
        val alpha = sin(w0) / (2f * 0.70710678f)
        
        val cosw0 = cos(w0)
        val a0 = 1f + alpha
        b0 = ((1f + cosw0) / 2f) / a0
        b1 = -(1f + cosw0) / a0
        b2 = ((1f + cosw0) / 2f) / a0
        a1 = (-2f * cosw0) / a0
        a2 = (1f - alpha) / a0
    }

    fun process(x: Float): Float {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x
        y2 = y1
        y1 = y
        return y
    }

    fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
    }
}

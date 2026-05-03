package com.forensicppg.monitor.domain

/**
 * Representa una muestra de señal PPG procesada desde un frame de cámara.
 * Sigue el contrato de "Zero Simulación" y trazabilidad forense.
 * Transformada a clase mutable para evitar asignaciones de memoria (Zero Allocation) a 60fps.
 */
class PpgSample {
    var timestamp: Long = 0L
    var effectiveFps: Float = 0f
    
    var rawRed: Float = 0f
    var rawGreen: Float = 0f
    var rawBlue: Float = 0f
    
    var ppgGreenAbsorbance: Float = 0f
    var ppgRedAbsorbance: Float = 0f
    var ppgBlueAbsorbance: Float = 0f
    
    var maskCoverage: Float = 0f
    var contactScore: Float = 0f
    var clippingHigh: Float = 0f
    var clippingLow: Float = 0f
    var motionOptical: Float = 0f
    
    var roiBoundingBox: String? = null
    var rejectReason: String? = null

    fun update(
        timestamp: Long,
        effectiveFps: Float,
        rawRed: Float,
        rawGreen: Float,
        rawBlue: Float,
        ppgGreenAbsorbance: Float,
        ppgRedAbsorbance: Float,
        ppgBlueAbsorbance: Float,
        maskCoverage: Float,
        contactScore: Float,
        clippingHigh: Float,
        clippingLow: Float,
        motionOptical: Float,
        rejectReason: String?
    ) {
        this.timestamp = timestamp
        this.effectiveFps = effectiveFps
        this.rawRed = rawRed
        this.rawGreen = rawGreen
        this.rawBlue = rawBlue
        this.ppgGreenAbsorbance = ppgGreenAbsorbance
        this.ppgRedAbsorbance = ppgRedAbsorbance
        this.ppgBlueAbsorbance = ppgBlueAbsorbance
        this.maskCoverage = maskCoverage
        this.contactScore = contactScore
        this.clippingHigh = clippingHigh
        this.clippingLow = clippingLow
        this.motionOptical = motionOptical
        this.rejectReason = rejectReason
    }
}

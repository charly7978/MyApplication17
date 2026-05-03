package com.forensicppg.monitor.domain

/**
 * Representa una muestra de señal PPG procesada desde un frame de cámara.
 * Sigue el contrato de "Zero Simulación" y trazabilidad forense.
 */
data class PpgSample(
    val timestamp: Long,          // Timestamp real del frame (nanos o millis)
    val effectiveFps: Float,      // FPS real medido en el momento de captura
    
    // Valores robustos de intensidad (no promedios simples si es posible)
    val rawRed: Float,
    val rawGreen: Float,
    val rawBlue: Float,
    
    // Absorbancia relativa (Ley de Beer-Lambert aproximada: -ln(I/I0))
    val ppgGreenAbsorbance: Float,
    val ppgRedAbsorbance: Float,
    
    // Métricas de calidad de contacto (Quality Gates)
    val maskCoverage: Float,      // Porcentaje de ROI que es realmente dedo (0.0 - 1.0)
    val contactScore: Float,      // Puntuación de contacto óptico estable
    val clippingHigh: Float,      // Ratio de píxeles saturados (>250)
    val clippingLow: Float,       // Ratio de píxeles oscuros (<8)
    val motionOptical: Float,     // Estimación de movimiento intra-frame
    
    val roiBoundingBox: String? = null, // Representación del ROI usado
    val rejectReason: String? = null    // Motivo por el cual esta muestra podría ser inválida
)

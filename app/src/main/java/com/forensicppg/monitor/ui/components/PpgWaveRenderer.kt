package com.forensicppg.monitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Renderizador de Olla PPG de alto rendimiento.
 * Estilo "Sweep" o "Barrido" emulando monitores hospitalarios (e.g. Philips IntelliVue).
 * Dibuja mediante curvas de Bézier para lograr una representación fluida y "eléctrica".
 */
@Composable
fun PpgWaveRenderer(
    waveValue: Float,
    modifier: Modifier = Modifier,
    waveColor: Color = Color(0xFF00FF00) // Verde clínico por defecto
) {
    // Memoria para el barrido. ~300 puntos de resolución es ideal para móviles
    val pointsCount = 300
    val waveBuffer = remember { FloatArray(pointsCount) }
    var currentIndex by remember { mutableStateOf(0) }

    // Actualizamos el buffer con el nuevo valor
    LaunchedEffect(waveValue) {
        waveBuffer[currentIndex] = waveValue
        currentIndex = (currentIndex + 1) % pointsCount
    }

    // Auto-escalado dinámico
    var maxVal by remember { mutableStateOf(0.01f) }
    var minVal by remember { mutableStateOf(-0.01f) }

    LaunchedEffect(waveValue) {
        // Encontrar min/max para normalizar, con un poco de "decay" para centrarse suavemente
        var localMax = -Float.MAX_VALUE
        var localMin = Float.MAX_VALUE
        for (v in waveBuffer) {
            if (v > localMax) localMax = v
            if (v < localMin) localMin = v
        }
        
        // Evitamos divisiones por cero
        if (localMax - localMin < 0.0001f) {
            localMax += 0.0001f
            localMin -= 0.0001f
        }

        // Suavizado del escalado para que no salte bruscamente
        maxVal = maxVal + 0.1f * (localMax - maxVal)
        minVal = minVal + 0.1f * (localMin - minVal)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val stepX = width / (pointsCount - 1)

        val path = Path()
        
        // El factor de escala vertical (70% del alto para dejar margen)
        val scaleY = (height * 0.7f) / (maxVal - minVal)

        // Dibujamos dos segmentos separados por la barra de barrido (sweep)
        // Segmento 1: De la barra de barrido hacia la derecha (datos viejos)
        // Segmento 2: Desde la izquierda hasta la barra de barrido (datos nuevos)
        
        var isFirstPoint = true
        var prevX = 0f
        var prevY = 0f

        for (i in 0 until pointsCount) {
            // El "gap" visual que representa el punto de barrido actual
            val isGap = (i >= currentIndex && i < currentIndex + 5)
            if (isGap) {
                isFirstPoint = true
                continue
            }

            val x = i * stepX
            // Normalizar entre 0 y 1, luego mapear al centro Y
            val normalized = (waveBuffer[i] - minVal) / (maxVal - minVal)
            // Invertimos Y porque en Canvas Y crece hacia abajo
            val y = centerY + (0.5f - normalized) * (height * 0.7f)

            if (isFirstPoint) {
                path.moveTo(x, y)
                isFirstPoint = false
            } else {
                // Trazado suave con curva Bézier
                val controlX = (prevX + x) / 2
                path.quadraticBezierTo(controlX, prevY, x, y)
            }
            prevX = x
            prevY = y
        }

        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(
                width = 5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        
        // Opcional: Dibujar la grilla del monitor (estilo médico)
        val gridLines = 5
        val gridSpacingY = height / gridLines
        val gridSpacingX = width / 10
        for (i in 1 until gridLines) {
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.3f),
                start = Offset(0f, i * gridSpacingY),
                end = Offset(width, i * gridSpacingY),
                strokeWidth = 1f
            )
        }
        for (i in 1 until 10) {
            drawLine(
                color = Color.DarkGray.copy(alpha = 0.3f),
                start = Offset(i * gridSpacingX, 0f),
                end = Offset(i * gridSpacingX, height),
                strokeWidth = 1f
            )
        }
    }
}

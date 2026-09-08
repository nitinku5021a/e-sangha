package com.digital.sanghaworld.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnalogClock(
    timeLeft: Long,
    totalDuration: Long
) {
    val progress = if (totalDuration > 0) timeLeft.toFloat() / totalDuration.toFloat() else 0f
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

    Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        val strokeWidth = 8.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val radius = diameter / 2
        val center = Offset(size.width / 2, size.height / 2)
        val arcSize = Size(diameter, diameter)
        val topLeft = Offset(center.x - radius, center.y - radius)
        val currentSweep = 360f * progress

        for (i in 0 until 12) {
            val angle = Math.toRadians((i * 30 - 90).toDouble())
            val inner = radius - 14.dp.toPx()
            val outer = radius - 4.dp.toPx()
            drawLine(
                color = tickColor,
                start = Offset(
                    center.x + (inner * cos(angle)).toFloat(),
                    center.y + (inner * sin(angle)).toFloat()
                ),
                end = Offset(
                    center.x + (outer * cos(angle)).toFloat(),
                    center.y + (outer * sin(angle)).toFloat()
                ),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth)
        )

        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = currentSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        if (progress > 0) {
            val endAngleRad = Math.toRadians((-90 + currentSweep).toDouble())
            val handleCenter = Offset(
                x = center.x + (radius * cos(endAngleRad)).toFloat(),
                y = center.y + (radius * sin(endAngleRad)).toFloat()
            )
            drawCircle(color = primaryColor, radius = 9.dp.toPx(), center = handleCenter)
            drawCircle(color = trackColor, radius = 4.dp.toPx(), center = handleCenter)
        }

        drawCircle(
            color = primaryColor.copy(alpha = 0.9f),
            radius = 5.dp.toPx(),
            center = center
        )
    }
}

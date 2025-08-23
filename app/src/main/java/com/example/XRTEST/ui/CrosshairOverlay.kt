package com.example.XRTEST.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * CrosshairOverlay for AR Glass Q&A System
 * Displays targeting crosshair in center of XR view for object selection
 */
@Composable
fun CrosshairOverlay(
    isActive: Boolean = true,
    isTargeting: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Animate crosshair visibility and color
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.3f,
        label = "crosshair_alpha"
    )
    
    val crosshairColor = if (isTargeting) {
        Color.Green
    } else {
        Color.Red
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(64.dp)
                .alpha(alpha)
        ) {
            drawCrosshair(
                color = crosshairColor,
                strokeWidth = 3.dp.toPx(),
                size = size.minDimension
            )
        }
    }
}

/**
 * Draw crosshair targeting reticle
 */
private fun DrawScope.drawCrosshair(
    color: Color,
    strokeWidth: Float,
    size: Float
) {
    val center = Offset(size / 2, size / 2)
    val lineLength = size * 0.3f
    val innerRadius = size * 0.1f
    
    // Draw outer circle
    drawCircle(
        color = color,
        radius = size * 0.4f,
        center = center,
        style = Stroke(width = strokeWidth)
    )
    
    // Draw inner targeting dot
    drawCircle(
        color = color,
        radius = size * 0.03f,
        center = center
    )
    
    // Draw crosshair lines
    // Horizontal line
    drawLine(
        color = color,
        start = Offset(center.x - lineLength, center.y),
        end = Offset(center.x + lineLength, center.y),
        strokeWidth = strokeWidth
    )
    
    // Vertical line
    drawLine(
        color = color,
        start = Offset(center.x, center.y - lineLength),
        end = Offset(center.x, center.y + lineLength),
        strokeWidth = strokeWidth
    )
}

/**
 * Advanced crosshair with targeting status indicator
 */
@Composable
fun AdvancedCrosshairOverlay(
    isActive: Boolean = true,
    targetLocked: Boolean = false,
    confidence: Float = 0f, // 0.0 to 1.0
    modifier: Modifier = Modifier
) {
    val crosshairColor = when {
        targetLocked -> Color.Green
        confidence > 0.7f -> Color.Yellow
        confidence > 0.3f -> Color(0xFFFFA500) // Orange color
        else -> Color.Red
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0.3f,
        label = "advanced_crosshair_alpha"
    )

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(80.dp)
                .alpha(alpha)
        ) {
            drawAdvancedCrosshair(
                color = crosshairColor,
                strokeWidth = 3.dp.toPx(),
                size = size.minDimension,
                confidence = confidence,
                targetLocked = targetLocked
            )
        }
    }
}

/**
 * Draw advanced crosshair with confidence indicator
 */
private fun DrawScope.drawAdvancedCrosshair(
    color: Color,
    strokeWidth: Float,
    size: Float,
    confidence: Float,
    targetLocked: Boolean
) {
    val center = Offset(size / 2, size / 2)
    val radius = size * 0.35f
    
    // Draw confidence arc
    if (confidence > 0f) {
        val sweepAngle = confidence * 360f
        drawArc(
            color = color.copy(alpha = 0.5f),
            startAngle = -90f,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = strokeWidth * 2)
        )
    }
    
    // Draw main crosshair
    drawCrosshair(color, strokeWidth, size)
    
    // Draw lock indicator if target is locked
    if (targetLocked) {
        val lockSize = size * 0.15f
        drawRect(
            color = color,
            topLeft = Offset(center.x - lockSize/2, center.y - lockSize/2),
            size = androidx.compose.ui.geometry.Size(lockSize, lockSize),
            style = Stroke(width = strokeWidth)
        )
    }
}

package com.damiongans.anchor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.damiongans.anchor.ui.theme.AmberGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Physical metronome pendulum with metallic arm, sliding weight,
 * and swing arcs. Scales proportionally from compact phone screens
 * up to large tablet and desktop studio layouts.
 */
@Composable
fun PendulumVisualizer(
    angleDegrees: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 110.dp)
            .testTag("pendulum_visualizer"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height

            val baseRefArm = 98.dp.toPx()
            // Cap arm length by both vertical height and horizontal swing clearance
            val maxArmByHeight = height - 24.dp.toPx()
            val maxArmByWidth = (width * 0.44f) / sin(28.0 * PI / 180.0).toFloat()
            val armLength = min(maxArmByHeight, maxArmByWidth).coerceAtLeast(64.dp.toPx())
            val scaleFactor = (armLength / baseRefArm).coerceIn(0.85f, 1.85f)

            val pivotX = width / 2f
            val pivotY = (height + armLength) / 2f - 4.dp.toPx() * scaleFactor

            val arcRadius = armLength * 0.85f

            // Measurement tick marks
            for (step in -4..4) {
                val tickAngle = step * 6.5
                val rad = (tickAngle - 90.0) * PI / 180.0
                val tickInnerOffset = 8.dp.toPx() * scaleFactor
                val tickOuterOffset = 4.dp.toPx() * scaleFactor
                val tickStartX = pivotX + (arcRadius - tickInnerOffset) * cos(rad).toFloat()
                val tickStartY = pivotY + (arcRadius - tickInnerOffset) * sin(rad).toFloat()
                val tickEndX = pivotX + (arcRadius + tickOuterOffset) * cos(rad).toFloat()
                val tickEndY = pivotY + (arcRadius + tickOuterOffset) * sin(rad).toFloat()

                val tickColor = if (step == 0) AmberGold.copy(alpha = 0.7f) else outlineColor
                val strokeW = (if (step == 0) 2.5.dp.toPx() else 1.5.dp.toPx()) * scaleFactor
                drawLine(
                    color = tickColor,
                    start = Offset(tickStartX, tickStartY),
                    end = Offset(tickEndX, tickEndY),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            // Glow trajectory trail when active
            if (isPlaying) {
                val glowAlpha = 0.25f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = glowAlpha), Color.Transparent),
                        center = Offset(pivotX, pivotY - armLength * 0.6f),
                        radius = armLength * 0.7f
                    ),
                    radius = armLength * 0.7f,
                    center = Offset(pivotX, pivotY - armLength * 0.6f)
                )
            }

            // Swinging pendulum arm and weight
            rotate(degrees = angleDegrees, pivot = Offset(pivotX, pivotY)) {
                // Shadow behind the rod
                drawLine(
                    color = AmberGold.copy(alpha = 0.25f),
                    start = Offset(pivotX, pivotY),
                    end = Offset(pivotX, pivotY - armLength),
                    strokeWidth = 6.dp.toPx() * scaleFactor,
                    cap = StrokeCap.Round
                )

                // Metallic rod
                val rodHalfW = 2.dp.toPx() * scaleFactor
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(AmberGold, Color(0xFFFFF8E1), AmberGold),
                        start = Offset(pivotX - rodHalfW, pivotY),
                        end = Offset(pivotX + rodHalfW, pivotY)
                    ),
                    start = Offset(pivotX, pivotY + 12.dp.toPx() * scaleFactor),
                    end = Offset(pivotX, pivotY - armLength),
                    strokeWidth = 3.5.dp.toPx() * scaleFactor,
                    cap = StrokeCap.Round
                )

                // Pendulum weight (inverted trapezoid sliding weight)
                val weightCenterY = pivotY - armLength * 0.68f
                val weightWidth = 24.dp.toPx() * scaleFactor
                val weightHeight = 28.dp.toPx() * scaleFactor

                val weightPath = Path().apply {
                    moveTo(pivotX - weightWidth * 0.45f, weightCenterY - weightHeight * 0.5f)
                    lineTo(pivotX + weightWidth * 0.45f, weightCenterY - weightHeight * 0.5f)
                    lineTo(pivotX + weightWidth * 0.55f, weightCenterY + weightHeight * 0.5f)
                    lineTo(pivotX - weightWidth * 0.55f, weightCenterY + weightHeight * 0.5f)
                    close()
                }

                // Weight body
                drawPath(
                    path = weightPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFFFD54F), Color(0xFFFF8F00), Color(0xFFFF6F00))
                    )
                )
                // Weight border highlight
                drawPath(
                    path = weightPath,
                    color = Color(0xFFFFF9C4),
                    style = Stroke(width = 1.2.dp.toPx() * scaleFactor)
                )

                // Center horizontal notch in the sliding weight
                drawLine(
                    color = Color(0xFF5D4037),
                    start = Offset(pivotX - weightWidth * 0.35f, weightCenterY),
                    end = Offset(pivotX + weightWidth * 0.35f, weightCenterY),
                    strokeWidth = 1.5.dp.toPx() * scaleFactor
                )
            }

            // Pivot cap
            drawCircle(
                color = surfaceColor,
                radius = 11.dp.toPx() * scaleFactor,
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF9C4), AmberGold, Color(0xFFE65100)),
                    center = Offset(pivotX, pivotY),
                    radius = 8.dp.toPx() * scaleFactor
                ),
                radius = 8.dp.toPx() * scaleFactor,
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 2.5.dp.toPx() * scaleFactor,
                center = Offset(pivotX - 2.dp.toPx() * scaleFactor, pivotY - 2.dp.toPx() * scaleFactor)
            )
        }
    }
}

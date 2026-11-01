package com.damiongans.pulse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.damiongans.pulse.ui.theme.AmberGold
import kotlin.math.cos
import kotlin.math.sin

/**
 * Physical metronome pendulum with metallic arm, sliding weight,
 * and swing arcs.
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
            .height(120.dp)
            .testTag("pendulum_visualizer"),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val width = size.width
            val height = size.height

            val pivotX = width / 2f
            val pivotY = height - 14.dp.toPx()
            val armLength = height - 22.dp.toPx()

            val arcRadius = armLength * 0.85f

            // Measurement tick marks
            for (step in -4..4) {
                val tickAngle = step * 6.5
                val rad = Math.toRadians(tickAngle - 90.0)
                val tickStartX = pivotX + (arcRadius - 8.dp.toPx()) * cos(rad).toFloat()
                val tickStartY = pivotY + (arcRadius - 8.dp.toPx()) * sin(rad).toFloat()
                val tickEndX = pivotX + (arcRadius + 4.dp.toPx()) * cos(rad).toFloat()
                val tickEndY = pivotY + (arcRadius + 4.dp.toPx()) * sin(rad).toFloat()

                val tickColor = if (step == 0) AmberGold.copy(alpha = 0.7f) else outlineColor
                val strokeW = if (step == 0) 2.5.dp.toPx() else 1.5.dp.toPx()
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
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Metallic rod
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(AmberGold, Color(0xFFFFF8E1), AmberGold),
                        start = Offset(pivotX - 2.dp.toPx(), pivotY),
                        end = Offset(pivotX + 2.dp.toPx(), pivotY)
                    ),
                    start = Offset(pivotX, pivotY + 12.dp.toPx()),
                    end = Offset(pivotX, pivotY - armLength),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Pendulum weight (inverted trapezoid sliding weight)
                val weightCenterY = pivotY - armLength * 0.68f
                val weightWidth = 24.dp.toPx()
                val weightHeight = 28.dp.toPx()

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
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Center horizontal notch in the sliding weight
                drawLine(
                    color = Color(0xFF5D4037),
                    start = Offset(pivotX - weightWidth * 0.35f, weightCenterY),
                    end = Offset(pivotX + weightWidth * 0.35f, weightCenterY),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // Pivot cap
            drawCircle(
                color = surfaceColor,
                radius = 11.dp.toPx(),
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFFF9C4), AmberGold, Color(0xFFE65100)),
                    center = Offset(pivotX, pivotY),
                    radius = 8.dp.toPx()
                ),
                radius = 8.dp.toPx(),
                center = Offset(pivotX, pivotY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.8f),
                radius = 2.5.dp.toPx(),
                center = Offset(pivotX - 2.dp.toPx(), pivotY - 2.dp.toPx())
            )
        }
    }
}

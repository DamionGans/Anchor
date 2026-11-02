package com.damiongans.anchor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.ui.theme.AccentDownbeat
import com.damiongans.anchor.ui.theme.AccentMute
import com.damiongans.anchor.ui.theme.AccentSub

@Composable
fun BeatBar(
    totalBeats: Int,
    currentBeat: Int,
    currentSubdivisionIndex: Int,
    subdivision: Subdivision,
    accents: List<BeatAccent>,
    isPlaying: Boolean,
    onBeatClick: (Int) -> Unit,
    isExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDense = totalBeats > 8
    val pillSpacing = when {
        isDense -> 4.dp
        isExpanded -> 10.dp
        else -> 8.dp
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("beat_bar"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main Beat Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isDense) 2.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(pillSpacing, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (index in 0 until totalBeats) {
                val isActive = isPlaying && currentBeat == index
                val accent = accents.getOrNull(index) ?: BeatAccent.REGULAR
                BeatPill(
                    beatIndex = index + 1,
                    accent = accent,
                    isActive = isActive,
                    isDense = isDense,
                    isExpanded = isExpanded && !isDense,
                    onClick = { onBeatClick(index) },
                    modifier = Modifier
                        .weight(1f, fill = isExpanded && totalBeats <= 8)
                        .widthIn(max = if (isExpanded) 96.dp else 80.dp)
                )
            }
        }

        // Subdivision Indicator Dots (shown if subdivision > 1)
        if (subdivision.count > 1) {
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .height(16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isExpanded) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (subIdx in 0 until subdivision.count) {
                    val isSubActive = isPlaying && currentSubdivisionIndex == subIdx
                    val dotColor by animateColorAsState(
                        targetValue = if (isSubActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        animationSpec = if (isSubActive) {
                            androidx.compose.animation.core.snap()
                        } else {
                            androidx.compose.animation.core.tween(durationMillis = 70)
                        },
                        label = "subDotColor"
                    )
                    val dotScale by animateFloatAsState(
                        targetValue = if (isSubActive) 1.3f else 1.0f,
                        animationSpec = if (isSubActive) {
                            androidx.compose.animation.core.snap()
                        } else {
                            androidx.compose.animation.core.tween(durationMillis = 70)
                        },
                        label = "subDotScale"
                    )

                    Box(
                        modifier = Modifier
                            .scale(dotScale)
                            .size(if (isExpanded) 8.dp else 7.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun BeatPill(
    beatIndex: Int,
    accent: BeatAccent,
    isActive: Boolean,
    isDense: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = when (accent) {
        BeatAccent.ACCENT -> AccentDownbeat
        BeatAccent.REGULAR -> primaryColor
        BeatAccent.SUB -> AccentSub
        BeatAccent.MUTE -> AccentMute
    }

    val scale by animateFloatAsState(
        targetValue = if (isActive) 1.08f else 1.0f,
        animationSpec = if (isActive) {
            androidx.compose.animation.core.snap()
        } else {
            androidx.compose.animation.core.tween(durationMillis = 90)
        },
        label = "beatScale"
    )

    val shadowElevation = if (isActive) 10.dp else 1.dp
    val interactionSource = remember { MutableInteractionSource() }
    val cornerRadius = if (isDense) 14.dp else 18.dp

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .scale(scale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(cornerRadius),
                spotColor = if (isActive) accentColor else Color.Transparent
            )
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                if (isActive) {
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = 0.35f), MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    )
                }
            )
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) accentColor else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(cornerRadius)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                onClick = onClick
            )
            .padding(
                vertical = if (isExpanded) 10.dp else 8.dp,
                horizontal = when {
                    isDense -> 4.dp
                    isExpanded -> 12.dp
                    else -> 10.dp
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Beat number
            Text(
                text = beatIndex.toString(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    fontFeatureSettings = "tnum",
                    fontSize = when {
                        isDense -> 15.sp
                        isExpanded -> 21.sp
                        else -> 19.sp
                    }
                ),
                color = if (isActive) accentColor else if (accent == BeatAccent.MUTE) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
            )

            // Status label indicator with visual glyph
            val glyph = when (accent) {
                BeatAccent.ACCENT -> "★"
                BeatAccent.REGULAR -> "●"
                BeatAccent.SUB -> "○"
                BeatAccent.MUTE -> "✕"
            }
            Text(
                text = if (isDense) glyph else "$glyph ${accent.label.uppercase()}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isExpanded) 10.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                ),
                color = if (isActive) accentColor else accentColor.copy(alpha = 0.85f)
            )
        }
    }
}

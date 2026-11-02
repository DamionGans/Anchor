package com.damiongans.anchor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Indication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.damiongans.anchor.ui.icons.AnchorIcons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.ButtonTriggerMode
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.TapTempoStatus
import com.damiongans.anchor.ui.theme.ElectricCoral

@Composable
fun QuickControlsBar(
    totalBeats: Int,
    subdivision: Subdivision,
    isPlaying: Boolean,
    tapStatus: TapTempoStatus,
    buttonTriggerMode: ButtonTriggerMode = ButtonTriggerMode.ON_PRESS,
    onTimeSignatureClick: () -> Unit,
    onSubdivisionSelect: (Subdivision) -> Unit,
    onTapTempo: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    isExpanded: Boolean = false,
    modifier: Modifier = Modifier
) {
    val controlHeight = if (isExpanded) 44.dp else 40.dp
    val transportHeight = if (isExpanded) 74.dp else 64.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (isExpanded) 0.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Time Signature & Subdivisions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Signature Button (e.g. "4/4")
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .height(controlHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(color = MaterialTheme.colorScheme.primary),
                        onClick = onTimeSignatureClick
                    )
                    .padding(horizontal = if (isExpanded) 18.dp else 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AnchorIcons.MusicNote,
                        contentDescription = "Time Signature",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isExpanded) 18.dp else 16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$totalBeats/${if (subdivision == Subdivision.EIGHTH) "8" else "4"}",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExpanded) 15.sp else 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Subdivision Selectors
            Row(
                horizontalArrangement = Arrangement.spacedBy(if (isExpanded) 8.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Subdivision.values().forEach { sub ->
                    val isSelected = subdivision == sub
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                        label = "subBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        label = "subBorder"
                    )

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(width = if (isExpanded) 48.dp else 42.dp, height = controlHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = MaterialTheme.colorScheme.primary),
                                onClick = { onSubdivisionSelect(sub) }
                            )
                            .testTag("subdivision_${sub.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = sub.symbol,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = if (sub == Subdivision.TRIPLET) (if (isExpanded) 14.sp else 13.sp) else (if (isExpanded) 17.sp else 16.sp)
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isExpanded) 14.dp else 10.dp))

        // Bottom Deck: Tap Tempo & Main Play/Pause Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (isExpanded) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tap Tempo Touchpad
            TapTempoTouchpad(
                tapStatus = tapStatus,
                buttonTriggerMode = buttonTriggerMode,
                onTap = onTapTempo,
                modifier = Modifier
                    .weight(1f)
                    .height(transportHeight)
            )

            // Main Play/Pause Button
            MainPlayPauseButton(
                isPlaying = isPlaying,
                buttonTriggerMode = buttonTriggerMode,
                onClick = onPlayPauseToggle,
                modifier = Modifier.size(transportHeight)
            )
        }
    }
}

/**
 * Applies either immediate touch-down triggering ([ButtonTriggerMode.ON_PRESS])
 * or finger lift/release triggering ([ButtonTriggerMode.ON_RELEASE]) while preserving
 * Material ripple, press scale animations, and accessibility semantics.
 */
@Composable
private fun Modifier.triggerModeClickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    triggerMode: ButtonTriggerMode,
    onTrigger: () -> Unit
): Modifier {
    val currentOnTrigger by rememberUpdatedState(onTrigger)
    return when (triggerMode) {
        ButtonTriggerMode.ON_RELEASE -> {
            this.clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = { currentOnTrigger() }
            )
        }
        ButtonTriggerMode.ON_PRESS -> {
            this
                .indication(interactionSource, indication)
                .pointerInput(interactionSource) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val press = PressInteraction.Press(down.position)
                        interactionSource.tryEmit(press)
                        currentOnTrigger()
                        val up = waitForUpOrCancellation()
                        if (up != null) {
                            up.consume()
                            interactionSource.tryEmit(PressInteraction.Release(press))
                        } else {
                            interactionSource.tryEmit(PressInteraction.Cancel(press))
                        }
                    }
                }
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    onClick {
                        currentOnTrigger()
                        true
                    }
                }
        }
    }
}

@Composable
private fun TapTempoTouchpad(
    tapStatus: TapTempoStatus,
    buttonTriggerMode: ButtonTriggerMode,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(),
        label = "tapScale"
    )

    val isLocked = tapStatus.isHighCertainty
    val hasTaps = tapStatus.tapCount > 0

    val accentBorderColor = when {
        isLocked -> Color(0xFF00E676)
        hasTaps -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = modifier
            .scale(scale)
            .shadow(
                elevation = if (isLocked) 6.dp else if (isPressed) 4.dp else 1.dp,
                shape = RoundedCornerShape(20.dp),
                spotColor = if (isLocked) Color(0xFF00E676) else MaterialTheme.colorScheme.secondary
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    isLocked -> Brush.horizontalGradient(
                        listOf(Color(0xFF00E676).copy(alpha = 0.2f), MaterialTheme.colorScheme.surfaceVariant)
                    )
                    isPressed -> Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f), MaterialTheme.colorScheme.surface)
                    )
                    else -> Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            )
            .border(
                width = if (isLocked || isPressed) 2.dp else 1.dp,
                color = accentBorderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .triggerModeClickable(
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.secondary),
                triggerMode = buttonTriggerMode,
                onTrigger = onTap
            )
            .testTag("tap_tempo_button"),
        contentAlignment = Alignment.Center
    ) {
        if (!hasTaps) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = AnchorIcons.TouchApp,
                    contentDescription = "Tap Tempo",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "TAP TEMPO",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap to set BPM",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLocked) AnchorIcons.CheckCircle else AnchorIcons.TouchApp,
                        contentDescription = null,
                        tint = if (isLocked) Color(0xFF00E676) else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLocked) "TEMPO SET: ${tapStatus.estimatedBpm} BPM" else "TAP ${tapStatus.tapCount} (${tapStatus.confidencePercent}%)",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = if (isLocked) Color(0xFF00E676) else MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = if (isLocked) "Tempo locked" else tapStatus.pulseDescription,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MainPlayPauseButton(
    isPlaying: Boolean,
    buttonTriggerMode: ButtonTriggerMode = ButtonTriggerMode.ON_PRESS,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "playScale"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val buttonGradient = if (isPlaying) {
        Brush.radialGradient(
            colors = listOf(ElectricCoral, Color(0xFFD50000))
        )
    } else {
        Brush.radialGradient(
            colors = listOf(primaryColor, primaryColor.copy(alpha = 0.8f))
        )
    }

    val shadowColor = if (isPlaying) ElectricCoral else primaryColor

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .scale(scale)
            .shadow(
                elevation = if (isPlaying) 10.dp else 6.dp,
                shape = CircleShape,
                spotColor = shadowColor
            )
            .clip(CircleShape)
            .background(buttonGradient)
            .triggerModeClickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                triggerMode = buttonTriggerMode,
                onTrigger = onClick
            )
            .testTag("play_pause_button"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) AnchorIcons.Pause else AnchorIcons.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(34.dp)
        )
    }
}

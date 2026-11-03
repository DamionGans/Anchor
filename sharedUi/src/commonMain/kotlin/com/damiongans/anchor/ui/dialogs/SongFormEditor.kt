package com.damiongans.anchor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.TempoMarking
import com.damiongans.anchor.model.TimeSignaturePreset
import com.damiongans.anchor.repository.ISettingsRepository
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.theme.AccentDownbeat
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SongFormEditor(
    initialName: String,
    initialBpm: Int,
    initialBeats: Int,
    initialSubdivision: Subdivision,
    initialAccents: List<BeatAccent>,
    confirmLabel: String,
    onConfirm: (String, Int, Int, Subdivision, List<BeatAccent>) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var bpm by remember { mutableIntStateOf(initialBpm.coerceIn(20, 300)) }
    var beats by remember { mutableIntStateOf(initialBeats.coerceIn(1, ISettingsRepository.MAX_BEATS)) }
    var subdivision by remember { mutableStateOf(initialSubdivision) }
    var accents by remember {
        mutableStateOf(
            resizeAccents(initialAccents, initialBeats.coerceIn(1, ISettingsRepository.MAX_BEATS))
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Song Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Tempo (BPM)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tempo: $bpm BPM (${TempoMarking.forBpm(bpm)})",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(-5, -1, 1, 5).forEach { delta ->
                        OutlinedButton(
                            onClick = { bpm = (bpm + delta).coerceIn(20, 300) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text(
                                text = if (delta > 0) "+$delta" else "$delta",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
                            )
                        }
                    }
                }
            }
            Slider(
                value = bpm.toFloat(),
                onValueChange = { bpm = it.roundToInt().coerceIn(20, 300) },
                valueRange = 20f..300f
            )
        }

        // Time Signature Presets + Beats / Subdivision
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val denom = if (subdivision == Subdivision.EIGHTH) 8 else 4
            Text(
                text = "Time Signature: $beats/$denom (${subdivision.displayName})",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum"
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeSignaturePreset.entries.forEach { preset ->
                    val isSelected = beats == preset.beats && subdivision == preset.subdivision
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                beats = preset.beats
                                subdivision = preset.subdivision
                                accents = resizeAccents(accents, preset.beats)
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    ) {
                        Text(
                            text = preset.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Beats:", style = MaterialTheme.typography.labelMedium)
                    IconButton(
                        onClick = {
                            if (beats > 1) {
                                beats -= 1
                                accents = resizeAccents(accents, beats)
                            }
                        },
                        enabled = beats > 1,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(AnchorIcons.Remove, contentDescription = "Fewer beats")
                    }
                    Text(
                        text = "$beats",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = "tnum"
                        )
                    )
                    IconButton(
                        onClick = {
                            if (beats < ISettingsRepository.MAX_BEATS) {
                                beats += 1
                                accents = resizeAccents(accents, beats)
                            }
                        },
                        enabled = beats < ISettingsRepository.MAX_BEATS,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(AnchorIcons.Add, contentDescription = "More beats")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Subdivision.entries.forEach { sub ->
                        val selected = subdivision == sub
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { subdivision = sub },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(
                                text = sub.symbol,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Interactive Beat Accents Editor
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Beat Accents (tap to cycle Accent / Normal / Soft / Mute)",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                accents.forEachIndexed { idx, accent ->
                    val pillColor = when (accent) {
                        BeatAccent.ACCENT -> AccentDownbeat
                        BeatAccent.REGULAR -> MaterialTheme.colorScheme.primary
                        BeatAccent.SUB -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.65f)
                        BeatAccent.MUTE -> MaterialTheme.colorScheme.surface
                    }
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val updated = accents.toMutableList()
                                updated[idx] = accent.next()
                                accents = updated
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = pillColor.copy(alpha = if (accent == BeatAccent.MUTE) 0.5f else 0.22f),
                        border = BorderStroke(1.5.dp, pillColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${idx + 1}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = accent.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirm(trimmed, bpm, beats, subdivision, accents)
                    }
                },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@Composable
internal fun SongAccentDots(accents: List<BeatAccent>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        accents.forEach { accent ->
            val dotColor = when (accent) {
                BeatAccent.ACCENT -> AccentDownbeat
                BeatAccent.REGULAR -> MaterialTheme.colorScheme.primary
                BeatAccent.SUB -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                BeatAccent.MUTE -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(
                        width = 1.dp,
                        color = if (accent == BeatAccent.MUTE) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            dotColor
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

internal fun resizeAccents(current: List<BeatAccent>, targetBeats: Int): List<BeatAccent> {
    val count = targetBeats.coerceIn(1, ISettingsRepository.MAX_BEATS)
    return (0 until count).map { idx ->
        current.getOrNull(idx) ?: if (idx == 0) BeatAccent.ACCENT else BeatAccent.REGULAR
    }
}

package com.damiongans.anchor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.BeatAccent
import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song
import com.damiongans.anchor.model.Subdivision
import com.damiongans.anchor.model.TempoMarking
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.theme.AccentDownbeat

@Composable
internal fun LibraryTabContent(
    songs: List<Song>,
    playlists: List<Playlist>,
    activePlaylistId: String?,
    activeSongId: String?,
    isActiveSongModified: Boolean,
    currentBpm: Int,
    currentTotalBeats: Int,
    currentSubdivision: Subdivision,
    currentAccents: List<BeatAccent>,
    onSaveCurrentAsSong: (String) -> Unit,
    onCreateSong: (String, Int, Int, Subdivision, List<BeatAccent>) -> Unit,
    onUpdateSong: (String, String, Int, Int, Subdivision, List<BeatAccent>) -> Unit,
    onUpdateSongFromCurrent: (String) -> Unit,
    onDeleteSong: (String) -> Unit,
    onLoadSong: (String) -> Unit,
    onAddSongToPlaylist: (String, String) -> Unit
) {
    var quickSaveName by remember { mutableStateOf("") }
    var showCustomSongEditor by remember { mutableStateOf(false) }
    var editingSongId by remember { mutableStateOf<String?>(null) }

    val activePlaylist = remember(playlists, activePlaylistId) {
        playlists.firstOrNull { it.id == activePlaylistId }
    }
    val activeSong = remember(songs, activeSongId) {
        songs.firstOrNull { it.id == activeSongId }
    }

    // Quick Save Current Metronome Card + Custom Song Builder Toggle
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Save Current Metronome as Song",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val currentDenom = if (currentSubdivision == Subdivision.EIGHTH) 8 else 4
                        Text(
                            text = "$currentBpm BPM • $currentTotalBeats/$currentDenom (${currentSubdivision.displayName})",
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            color = MaterialTheme.colorScheme.primary
                        )
                        SongAccentDots(accents = currentAccents)
                    }
                }
                TextButton(
                    onClick = { showCustomSongEditor = !showCustomSongEditor },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(if (showCustomSongEditor) "Hide Editor" else "Custom Editor")
                }
            }

            if (!showCustomSongEditor) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = quickSaveName,
                        onValueChange = { quickSaveName = it },
                        placeholder = { Text("Song title…") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_song_name_input")
                    )
                    Button(
                        onClick = {
                            val trimmed = quickSaveName.trim()
                            if (trimmed.isNotEmpty()) {
                                onSaveCurrentAsSong(trimmed)
                                quickSaveName = ""
                            }
                        },
                        enabled = quickSaveName.trim().isNotEmpty(),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("save_current_song_button")
                    ) {
                        Icon(
                            imageVector = AnchorIcons.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save")
                    }
                }
            } else {
                SongFormEditor(
                    initialName = quickSaveName,
                    initialBpm = currentBpm,
                    initialBeats = currentTotalBeats,
                    initialSubdivision = currentSubdivision,
                    initialAccents = currentAccents,
                    confirmLabel = "Add to Library",
                    onConfirm = { name, bpm, beats, sub, accents ->
                        onCreateSong(name, bpm, beats, sub, accents)
                        quickSaveName = ""
                        showCustomSongEditor = false
                    },
                    onCancel = { showCustomSongEditor = false }
                )
            }
        }
    }

    // Active song modified banner
    if (activeSong != null && isActiveSongModified) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = AccentDownbeat.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, AccentDownbeat.copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "'${activeSong.name}' has unsaved live changes",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Stored: ${activeSong.bpm} BPM • ${activeSong.timeSignatureDisplay}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { onUpdateSongFromCurrent(activeSong.id) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentDownbeat,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("Update Song", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Library Songs List
    if (songs.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Your Song Library is empty",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Dial in a tempo, time signature, and beat accents on the metronome, or open the Custom Editor above to add songs to your library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        songs.forEach { song ->
            val isLoaded = song.id == activeSongId
            val isEditing = editingSongId == song.id

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (isLoaded) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                border = BorderStroke(
                    width = if (isLoaded) 1.5.dp else 1.dp,
                    color = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isEditing) {
                        SongFormEditor(
                            initialName = song.name,
                            initialBpm = song.bpm,
                            initialBeats = song.totalBeats,
                            initialSubdivision = song.subdivision,
                            initialAccents = song.accents,
                            confirmLabel = "Save Changes",
                            onConfirm = { name, bpm, beats, sub, accents ->
                                onUpdateSong(song.id, name, bpm, beats, sub, accents)
                                editingSongId = null
                            },
                            onCancel = { editingSongId = null }
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onLoadSong(song.id) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = song.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isLoaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isLoaded) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "LOADED",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Black
                                                ),
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${song.bpm} BPM (${TempoMarking.forBpm(song.bpm)}) • ${song.timeSignatureDisplay} (${song.subdivision.displayName})",
                                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    SongAccentDots(accents = song.accents)
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (activePlaylist != null) {
                                    OutlinedButton(
                                        onClick = { onAddSongToPlaylist(activePlaylist.id, song.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = AnchorIcons.Add,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = activePlaylist.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { editingSongId = song.id },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = AnchorIcons.Edit,
                                        contentDescription = "Edit song",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteSong(song.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = AnchorIcons.Delete,
                                        contentDescription = "Delete song",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

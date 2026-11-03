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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.damiongans.anchor.model.TimeSignaturePreset
import com.damiongans.anchor.repository.ISettingsRepository
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.platform.ScrollableArea
import com.damiongans.anchor.ui.platform.scrollbarGutter
import com.damiongans.anchor.ui.platform.touchVerticalScroll
import com.damiongans.anchor.ui.theme.AccentDownbeat
import kotlin.math.roundToInt

private enum class PlaylistsSheetTab(val title: String, val icon: ImageVector) {
    PLAYLISTS("Playlists", AnchorIcons.QueueMusic),
    LIBRARY("Song Library", AnchorIcons.LibraryMusic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsBottomSheet(
    songs: List<Song>,
    playlists: List<Playlist>,
    activePlaylistId: String?,
    activeSongIndex: Int?,
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
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddSongToPlaylist: (String, String) -> Unit,
    onRemoveSongFromPlaylist: (String, Int) -> Unit,
    onMoveSongInPlaylist: (String, Int, Int) -> Unit,
    onSelectPlaylist: (String?) -> Unit,
    onSelectPlaylistSong: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Playlists & Song Library",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Save songs with tempo, meter & accents and step through setlists",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = AnchorIcons.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                PlaylistsSheetTab.entries.forEachIndexed { index, tab ->
                    val countLabel = when (tab) {
                        PlaylistsSheetTab.PLAYLISTS -> if (playlists.isNotEmpty()) " (${playlists.size})" else ""
                        PlaylistsSheetTab.LIBRARY -> if (songs.isNotEmpty()) " (${songs.size})" else ""
                    }
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        modifier = Modifier.minimumInteractiveComponentSize(),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = tab.title + countLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                                        fontFeatureSettings = "tnum"
                                    )
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val scrollState = rememberScrollState()
            ScrollableArea(
                state = scrollState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .touchVerticalScroll(scrollState)
                        .padding(end = scrollbarGutter()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (PlaylistsSheetTab.entries[selectedTab]) {
                        PlaylistsSheetTab.PLAYLISTS -> PlaylistsTabContent(
                            songs = songs,
                            playlists = playlists,
                            activePlaylistId = activePlaylistId,
                            activeSongIndex = activeSongIndex,
                            onCreatePlaylist = onCreatePlaylist,
                            onRenamePlaylist = onRenamePlaylist,
                            onDeletePlaylist = onDeletePlaylist,
                            onAddSongToPlaylist = onAddSongToPlaylist,
                            onRemoveSongFromPlaylist = onRemoveSongFromPlaylist,
                            onMoveSongInPlaylist = onMoveSongInPlaylist,
                            onSelectPlaylist = onSelectPlaylist,
                            onSelectPlaylistSong = onSelectPlaylistSong,
                            onSwitchToLibraryTab = { selectedTab = PlaylistsSheetTab.LIBRARY.ordinal }
                        )

                        PlaylistsSheetTab.LIBRARY -> LibraryTabContent(
                            songs = songs,
                            playlists = playlists,
                            activePlaylistId = activePlaylistId,
                            activeSongId = activeSongId,
                            isActiveSongModified = isActiveSongModified,
                            currentBpm = currentBpm,
                            currentTotalBeats = currentTotalBeats,
                            currentSubdivision = currentSubdivision,
                            currentAccents = currentAccents,
                            onSaveCurrentAsSong = onSaveCurrentAsSong,
                            onCreateSong = onCreateSong,
                            onUpdateSong = onUpdateSong,
                            onUpdateSongFromCurrent = onUpdateSongFromCurrent,
                            onDeleteSong = onDeleteSong,
                            onLoadSong = onLoadSong,
                            onAddSongToPlaylist = onAddSongToPlaylist
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaylistsTabContent(
    songs: List<Song>,
    playlists: List<Playlist>,
    activePlaylistId: String?,
    activeSongIndex: Int?,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddSongToPlaylist: (String, String) -> Unit,
    onRemoveSongFromPlaylist: (String, Int) -> Unit,
    onMoveSongInPlaylist: (String, Int, Int) -> Unit,
    onSelectPlaylist: (String?) -> Unit,
    onSelectPlaylistSong: (Int) -> Unit,
    onSwitchToLibraryTab: () -> Unit
) {
    var newPlaylistName by remember { mutableStateOf("") }
    var expandedPlaylistId by remember(activePlaylistId) { mutableStateOf(activePlaylistId ?: playlists.firstOrNull()?.id) }
    var renamingPlaylistId by remember { mutableStateOf<String?>(null) }
    var renameDraft by remember { mutableStateOf("") }
    val songsById = remember(songs) { songs.associateBy { it.id } }

    // Create Playlist Card
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                placeholder = { Text("New playlist / setlist name…") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("new_playlist_name_input")
            )
            Button(
                onClick = {
                    val trimmed = newPlaylistName.trim()
                    if (trimmed.isNotEmpty()) {
                        onCreatePlaylist(trimmed)
                        newPlaylistName = ""
                    }
                },
                enabled = newPlaylistName.trim().isNotEmpty(),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("create_playlist_button")
            ) {
                Icon(
                    imageVector = AnchorIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Create")
            }
        }
    }

    if (activePlaylistId != null) {
        val activePlaylist = playlists.firstOrNull { it.id == activePlaylistId }
        if (activePlaylist != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AnchorIcons.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Active Setlist: ${activePlaylist.name}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TextButton(
                        onClick = { onSelectPlaylist(null) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }

    if (playlists.isEmpty()) {
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "No playlists yet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Create a playlist above, then add songs from your Song Library to step through them during rehearsal or performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        playlists.forEach { playlist ->
            val isActive = playlist.id == activePlaylistId
            val isExpanded = playlist.id == expandedPlaylistId || isActive

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = if (isActive) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                border = BorderStroke(
                    width = if (isActive) 1.5.dp else 1.dp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (renamingPlaylistId == playlist.id) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = renameDraft,
                                onValueChange = { renameDraft = it },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val trimmed = renameDraft.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onRenamePlaylist(playlist.id, trimmed)
                                    }
                                    renamingPlaylistId = null
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Save")
                            }
                            TextButton(
                                onClick = { renamingPlaylistId = null },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text("Cancel")
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        expandedPlaylistId = playlist.id
                                        onSelectPlaylist(playlist.id)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isActive) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "ACTIVE",
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
                                Text(
                                    text = "${playlist.songIds.size} ${if (playlist.songIds.size == 1) "song" else "songs"} • Tap to load setlist",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!isActive) {
                                    OutlinedButton(
                                        onClick = {
                                            expandedPlaylistId = playlist.id
                                            onSelectPlaylist(playlist.id)
                                        },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Text("Select")
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        renameDraft = playlist.name
                                        renamingPlaylistId = playlist.id
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = AnchorIcons.Edit,
                                        contentDescription = "Rename playlist",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onDeletePlaylist(playlist.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = AnchorIcons.Delete,
                                        contentDescription = "Delete playlist",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (isExpanded) {
                        // Ordered Songs in Playlist
                        if (playlist.songIds.isEmpty()) {
                            Text(
                                text = "No songs in this playlist yet. Add songs from your library below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                playlist.songIds.forEachIndexed { idx, songId ->
                                    val song = songsById[songId] ?: return@forEachIndexed
                                    val isCurrentSong = isActive && activeSongIndex == idx

                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (!isActive) {
                                                    onSelectPlaylist(playlist.id)
                                                }
                                                onSelectPlaylistSong(idx)
                                            },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isCurrentSong) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        } else {
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                                        },
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isCurrentSong) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                            }
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "${idx + 1}.",
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontWeight = FontWeight.Bold,
                                                        fontFeatureSettings = "tnum"
                                                    ),
                                                    color = if (isCurrentSong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = song.name,
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.SemiBold
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${song.bpm} BPM • ${song.timeSignatureDisplay}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        SongAccentDots(accents = song.accents)
                                                    }
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = { onMoveSongInPlaylist(playlist.id, idx, idx - 1) },
                                                    enabled = idx > 0,
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = AnchorIcons.ArrowUpward,
                                                        contentDescription = "Move up",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onMoveSongInPlaylist(playlist.id, idx, idx + 1) },
                                                    enabled = idx < playlist.songIds.lastIndex,
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = AnchorIcons.ArrowDownward,
                                                        contentDescription = "Move down",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onRemoveSongFromPlaylist(playlist.id, idx) },
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = AnchorIcons.Close,
                                                        contentDescription = "Remove from playlist",
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Add Songs from Library Picker
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Add from Song Library",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (songs.isEmpty()) {
                                OutlinedButton(
                                    onClick = onSwitchToLibraryTab,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = AnchorIcons.LibraryMusic,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Song Library to create songs")
                                }
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    songs.forEach { song ->
                                        Surface(
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { onAddSongToPlaylist(playlist.id, song.id) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = AnchorIcons.Add,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                                Text(
                                                    text = "${song.name} (${song.bpm} • ${song.timeSignatureDisplay})",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                                    color = MaterialTheme.colorScheme.onSurface
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
        }
    }
}

@Composable
private fun LibraryTabContent(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongFormEditor(
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
private fun SongAccentDots(accents: List<BeatAccent>) {
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

private fun resizeAccents(current: List<BeatAccent>, targetBeats: Int): List<BeatAccent> {
    val count = targetBeats.coerceIn(1, ISettingsRepository.MAX_BEATS)
    return (0 until count).map { idx ->
        current.getOrNull(idx) ?: if (idx == 0) BeatAccent.ACCENT else BeatAccent.REGULAR
    }
}

package com.damiongans.anchor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.model.Playlist
import com.damiongans.anchor.model.Song
import com.damiongans.anchor.ui.icons.AnchorIcons
import com.damiongans.anchor.ui.theme.AccentDownbeat

/**
 * Slim setlist transport bar displayed on the main screen when a playlist or library song is active.
 * Keeps the main screen non-scrolling while giving one-tap Previous / Next song stepping and quick
 * access to the Playlists & Song Library sheet.
 */
@Composable
fun SetlistBar(
    activePlaylist: Playlist?,
    activeSong: Song?,
    activeSongIndex: Int?,
    isActiveSongModified: Boolean,
    onPreviousSong: () -> Unit,
    onNextSong: () -> Unit,
    onOpenPlaylistsSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (activePlaylist == null && activeSong == null) return

    val totalInPlaylist = activePlaylist?.songIds?.size ?: 0
    val canStepPrevious = activePlaylist != null && activeSongIndex != null && activeSongIndex > 0
    val canStepNext = activePlaylist != null && activeSongIndex != null && activeSongIndex < totalInPlaylist - 1

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("setlist_bar"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.75f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (activePlaylist != null) {
                IconButton(
                    onClick = onPreviousSong,
                    enabled = canStepPrevious,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .testTag("setlist_prev_button")
                ) {
                    Icon(
                        imageVector = AnchorIcons.ChevronLeft,
                        contentDescription = "Previous song",
                        tint = if (canStepPrevious) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Center clickable summary (opens PlaylistsBottomSheet)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenPlaylistsSheet)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("setlist_bar_title"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (activePlaylist != null) AnchorIcons.QueueMusic else AnchorIcons.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activePlaylist != null && activeSongIndex != null && totalInPlaylist > 0) {
                            Text(
                                text = "${activeSongIndex + 1}/$totalInPlaylist · ",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = activeSong?.name ?: activePlaylist?.name ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActiveSongModified && activeSong != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(AccentDownbeat)
                                    .testTag("setlist_modified_dot")
                            )
                        }
                    }

                    val subtitle = when {
                        activePlaylist != null && activeSong != null ->
                            "${activePlaylist.name} • ${activeSong.bpm} BPM • ${activeSong.timeSignatureDisplay}" +
                                if (isActiveSongModified) " (edited)" else ""
                        activePlaylist != null && totalInPlaylist == 0 ->
                            "Empty playlist • Tap to add songs"
                        activeSong != null ->
                            "${activeSong.bpm} BPM • ${activeSong.timeSignatureDisplay}" +
                                if (isActiveSongModified) " (edited)" else ""
                        else -> ""
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (activePlaylist != null) {
                IconButton(
                    onClick = onNextSong,
                    enabled = canStepNext,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .testTag("setlist_next_button")
                ) {
                    Icon(
                        imageVector = AnchorIcons.ChevronRight,
                        contentDescription = "Next song",
                        tint = if (canStepNext) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
        }
    }
}

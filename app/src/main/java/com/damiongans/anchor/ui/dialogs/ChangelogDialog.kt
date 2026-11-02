package com.damiongans.anchor.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.damiongans.anchor.BuildConfig
import com.damiongans.anchor.ui.icons.AnchorIcons

private data class ChangelogEntry(val title: String, val description: String)

private val CURRENT_RELEASE_CHANGELOG = listOf(
    ChangelogEntry("Anchor for iPhone", "Added a native iPhone app with the same single-screen metronome, sounds, accents, time signatures, tap tempo, screen flash, haptics, calibration offsets, and Lock Screen controls."),
    ChangelogEntry("Shared Multiplatform Core", "Timing, delay compensation, click synthesis, settings, update checks, and app state now live in one shared Kotlin Multiplatform module used by both Android and iPhone."),
    ChangelogEntry("Android Platform Layer", "The Android app now contains only Android-specific audio output, haptics, storage, notification, and update code on top of the shared core."),
    ChangelogEntry("iPhone Build Workflow", "A new GitHub Actions workflow tests the shared logic on the iOS simulator and builds an unsigned iPhone app package.")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogDialog(
    onDismiss: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("changelog_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AnchorIcons.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Release Notes (v${BuildConfig.VERSION_NAME})",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = AnchorIcons.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CURRENT_RELEASE_CHANGELOG.forEach { item ->
                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

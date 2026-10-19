package com.armanmaurya.internetradio.ui.mobile.components

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.domain.model.RecordingFile
import java.util.Locale

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecordingFileItem(
    recording: RecordingFile,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    isCurrentlyPlaying: Boolean = false,
    playbackIsPlaying: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sizeMb = recording.sizeBytes / (1024 * 1024f)
    val timeStr = DateUtils.getRelativeTimeSpanString(
        recording.lastModified,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()

    var showMenu by remember(recording.uri) { mutableStateOf(false) }
    var isDeleting by remember(recording.uri) { mutableStateOf(false) }
    val isPureBlack = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black

    val alpha by animateFloatAsState(
        targetValue = if (isDeleting) 0.5f else 1f,
        label = "deletingAlpha"
    )

    Column(
        modifier = modifier
            .alpha(alpha)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else if (isPureBlack) androidx.compose.ui.graphics.Color.Black 
                else if (isCurrentlyPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (isPureBlack) Modifier.border(
                    1.dp,
                    if (isSelected || isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .animateContentSize()
            .combinedClickable(
                enabled = !isDeleting,
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = recording.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            },
            supportingContent = {
                val durationStr = if (recording.durationMs > 0L) {
                    DateUtils.formatElapsedTime(recording.durationMs / 1000L)
                } else null
                val detailsStr = if (durationStr != null) {
                    String.format(Locale.getDefault(), "%s • %.1f MB • %s", durationStr, sizeMb, timeStr)
                } else {
                    String.format(Locale.getDefault(), "%.1f MB • %s", sizeMb, timeStr)
                }
                Text(
                    text = detailsStr,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            trailingContent = {
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                } else {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_recording)) },
                                onClick = {
                                    showMenu = false
                                    isDeleting = true
                                    onDelete()
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            },
            leadingContent = {
                Icon(
                    imageVector = when {
                        isCurrentlyPlaying && playbackIsPlaying -> Icons.Default.GraphicEq
                        isCurrentlyPlaying -> Icons.Default.Pause
                        else -> Icons.Default.Mic
                    },
                    contentDescription = null,
                    tint = if (isSelected || isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
    }
}

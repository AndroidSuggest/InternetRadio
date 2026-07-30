package com.armanmaurya.internetradio.ui.mobile.components

import android.media.MediaPlayer
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.data.repository.RecordingFile
import java.util.Locale

@Composable
fun RecordingFileItem(
    recording: RecordingFile,
    isExpanded: Boolean,
    onClick: () -> Unit,
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

    var showMenu by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    val isPureBlack = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black

    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isDeleting) 0.5f else 1f,
        label = "deletingAlpha"
    )

    Column(
        modifier = modifier
            .alpha(alpha)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPureBlack) androidx.compose.ui.graphics.Color.Black 
                else if (isExpanded) MaterialTheme.colorScheme.surfaceVariant 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (isPureBlack) Modifier.border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .animateContentSize()
            .clickable(enabled = !isDeleting, onClick = onClick)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = recording.fileName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f MB • %s", sizeMb, timeStr),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            trailingContent = {
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
            },
            leadingContent = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
        
        if (isExpanded) {
            InlineMediaPlayer(uri = recording.uri)
        }
    }
}

@Composable
fun InlineMediaPlayer(uri: android.net.Uri) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1) }
    var currentPosition by remember { mutableStateOf(0) }
    
    val mediaPlayer = remember(uri) {
        MediaPlayer().apply {
            setDataSource(context, uri)
            setOnPreparedListener { 
                duration = it.duration
                it.start()
                isPlaying = true
            }
            setOnCompletionListener {
                isPlaying = false
                currentPosition = 0
                progress = 0f
            }
            prepareAsync()
        }
    }

    DisposableEffect(mediaPlayer) {
        onDispose {
            mediaPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = mediaPlayer.currentPosition
            progress = currentPosition.toFloat() / duration.coerceAtLeast(1)
            kotlinx.coroutines.delay(100)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            if (isPlaying) {
                mediaPlayer.pause()
                isPlaying = false
            } else {
                mediaPlayer.start()
                isPlaying = true
            }
        }) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.player_cd_pause) else stringResource(R.string.player_cd_play),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Slider(
            value = progress,
            onValueChange = { 
                progress = it
                val newPosition = (it * duration).toInt()
                currentPosition = newPosition
                mediaPlayer.seekTo(newPosition)
            },
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary
            )
        )
        
        val formattedTime = DateUtils.formatElapsedTime(currentPosition / 1000L)
        Text(
            text = formattedTime, 
            style = MaterialTheme.typography.labelMedium, 
            modifier = Modifier.padding(start = 12.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

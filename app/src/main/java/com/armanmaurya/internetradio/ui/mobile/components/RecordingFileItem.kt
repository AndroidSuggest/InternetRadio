package com.armanmaurya.internetradio.ui.mobile.components

import android.media.MediaPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecordingFileItem(
    recording: RecordingFile,
    isExpanded: Boolean,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
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
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else if (isPureBlack) androidx.compose.ui.graphics.Color.Black 
                else if (isExpanded) MaterialTheme.colorScheme.surfaceVariant 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (isPureBlack) Modifier.border(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
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
                if (selectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null, // handled by the row click
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
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            colors = ListItemDefaults.colors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent
            )
        )
        
        if (isExpanded && !selectionMode) {
            InlineMediaPlayer(uri = recording.uri)
        }
    }
}

@Composable
fun InlineMediaPlayer(uri: android.net.Uri) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var duration by remember { mutableStateOf(1L) }
    var currentPosition by remember { mutableStateOf(0L) }
    
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        duration = this@apply.duration.coerceAtLeast(1L)
                    } else if (state == Player.STATE_ENDED) {
                        isPlaying = false
                        seekTo(0)
                        progress = 0f
                    }
                }
            })
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosition = exoPlayer.currentPosition
            progress = currentPosition.toFloat() / duration.coerceAtLeast(1L)
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
                exoPlayer.pause()
                isPlaying = false
            } else {
                exoPlayer.play()
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
                val newPosition = (it * duration).toLong()
                currentPosition = newPosition
                exoPlayer.seekTo(newPosition)
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

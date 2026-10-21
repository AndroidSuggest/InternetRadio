@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)
package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.ui.mobile.components.RecordingFileItem
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings.components.NowPlayingRecordingCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings.components.NowPlayingTrackQueue
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingsViewModel

@Composable
fun RecordingsTab(
    activeSessions: Map<String, RecordingSession>,
    stationRecordings: List<RecordingFile>?,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    onStopRecording: (String) -> Unit,
    onDeleteRecording: (RecordingFile) -> Unit,
    viewModel: RecordingsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()
) {
    val currentPlayingRecording by viewModel.currentPlayingRecording.collectAsStateWithLifecycle()
    val isPlayingRecording by viewModel.isPlayingRecording.collectAsStateWithLifecycle()
    val recordingPositionMs by viewModel.recordingPositionMs.collectAsStateWithLifecycle()
    val recordingDurationMs by viewModel.recordingDurationMs.collectAsStateWithLifecycle()
    val recordingPlaybackSpeed by viewModel.recordingPlaybackSpeed.collectAsStateWithLifecycle()
    val isRecordingLooping by viewModel.isRecordingLooping.collectAsStateWithLifecycle()
    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerLow == androidx.compose.ui.graphics.Color.Black

    LaunchedEffect(currentPlayingRecording?.uri) {
        if (currentPlayingRecording != null && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
            listState.animateScrollToItem(0)
        }
    }

    var selectionMode by remember { mutableStateOf(false) }
    val selectionTransition = updateTransition(
        targetState = selectionMode,
        label = "SelectionTransition"
    )
    val shouldShowSelectionBar = selectionMode || selectionTransition.currentState
    var selectedFiles by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedFiles = emptySet()
    }

    if (showDeleteConfirmDialog) {
        val deleteCount = selectedFiles.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete_recording)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.delete_recordings_message,
                        deleteCount,
                        deleteCount
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    if (stationRecordings != null) {
                        val filesToDelete = stationRecordings.filter { it.uri.toString() in selectedFiles }
                        viewModel.deleteRecordings(filesToDelete)
                    }
                    selectionMode = false
                    selectedFiles = emptySet()
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    var cachedPlayingRecording by remember { mutableStateOf<RecordingFile?>(null) }
    if (currentPlayingRecording != null) {
        cachedPlayingRecording = currentPlayingRecording
    }
    val rawRecordingToShow = currentPlayingRecording ?: cachedPlayingRecording
    val recordingToShow = remember(rawRecordingToShow, stationRecordings) {
        rawRecordingToShow?.let { rec ->
            stationRecordings?.firstOrNull { it.uri == rec.uri } ?: rec
        }
    }

    val nowPlayingTransition = updateTransition(
        targetState = currentPlayingRecording != null,
        label = "NowPlayingTransition"
    )
    val shouldShowNowPlayingCard = currentPlayingRecording != null || nowPlayingTransition.currentState
    val shouldShowStickyHeader = shouldShowNowPlayingCard || shouldShowSelectionBar
    var showTrackList by rememberSaveable(recordingToShow?.uri?.toString()) { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (stationRecordings == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .nestedScroll(nestedScrollConnection),
                contentPadding = PaddingValues(
                    bottom = 80.dp
                )
            ) {
                if (shouldShowStickyHeader) {
                    if (shouldShowNowPlayingCard) {
                        stickyHeader(key = "sticky_recording_tab_header") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    nowPlayingTransition.AnimatedVisibility(
                                        visible = { it },
                                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                                    ) {
                                        if (recordingToShow != null) {
                                            NowPlayingRecordingCard(
                                                recording = recordingToShow,
                                                isPlaying = isPlayingRecording,
                                                currentPositionMs = recordingPositionMs,
                                                durationMs = recordingDurationMs,
                                                playbackSpeed = recordingPlaybackSpeed,
                                                isLooping = isRecordingLooping,
                                                onPlayPauseClick = viewModel::togglePlayPauseRecording,
                                                onSeek = viewModel::seekRecordingPlayback,
                                                onSpeedChange = viewModel::setPlaybackSpeed,
                                                onToggleLoop = viewModel::toggleLoopRecording,
                                                onRouteClick = {
                                                    viewModel.routeToRecording(recordingToShow.stationName, recordingToShow.uri.toString())
                                                },
                                                onStopClick = viewModel::stopRecordingPlayback,
                                                showTrackList = showTrackList,
                                                onToggleTrackList = { showTrackList = !showTrackList },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(top = 8.dp, bottom = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (shouldShowNowPlayingCard && recordingToShow != null) {
                        item(key = "now_playing_track_queue") {
                            NowPlayingTrackQueue(
                                recording = recordingToShow,
                                currentPositionMs = recordingPositionMs,
                                visible = showTrackList,
                                onSeek = viewModel::seekRecordingPlayback,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (shouldShowSelectionBar) {
                        item(key = "recording_tab_selection_bar") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                selectionTransition.AnimatedVisibility(
                                    visible = { it },
                                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }) + expandVertically(expandFrom = Alignment.Top),
                                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }) + shrinkVertically(shrinkTowards = Alignment.Top)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                top = if (shouldShowNowPlayingCard) 0.dp else 8.dp,
                                                bottom = 8.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { 
                                                    selectionMode = false
                                                    selectedFiles = emptySet()
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Close, 
                                                contentDescription = "Cancel",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        Text(
                                            text = stringResource(R.string.recordings_selected, selectedFiles.size),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                                        )
                                        
                                        val isAllSelected = selectedFiles.size == stationRecordings.size && stationRecordings.isNotEmpty()
                                        
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { 
                                                    if (isAllSelected) {
                                                        selectedFiles = emptySet()
                                                    } else {
                                                        selectedFiles = stationRecordings.map { it.uri.toString() }.toSet()
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll, 
                                                contentDescription = "Select All",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(MaterialTheme.colorScheme.errorContainer)
                                                .clickable { showDeleteConfirmDialog = true }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Delete, 
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (activeSessions.isNotEmpty()) {
                    item(key = "active_recordings_header") {
                        Text(
                            text = stringResource(R.string.recordings_currently_recording),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }
                    
                    items(
                        items = activeSessions.values.chunked(2),
                        key = { rowSessions -> "active_sessions_row_" + rowSessions.joinToString { it.station.stationUuid } }
                    ) { rowSessions ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (session in rowSessions) {
                                val duration by session.durationSeconds.collectAsState(initial = 0L)
                                Box(modifier = Modifier.weight(1f)) {
                                    StationCard(
                                        station = session.station,
                                        onClick = {}, // No action on click for now
                                        isRecordingOverlay = true,
                                        recordingDuration = duration,
                                        onStopRecordingClick = { onStopRecording(session.station.stationUuid) }
                                    )
                                }
                            }
                            if (rowSessions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    
                    item(key = "active_recordings_divider") {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        if (stationRecordings.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.recordings_saved),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
        
                if (stationRecordings.isNotEmpty()) {
                    items(
                        items = stationRecordings,
                        key = { it.uri.toString() }
                    ) { recording ->
                        val isCurrentlyPlaying = currentPlayingRecording?.uri == recording.uri
                        val isSelected = recording.uri.toString() in selectedFiles
        
                        RecordingFileItem(
                            recording = recording,
                            isSelected = isSelected,
                            selectionMode = selectionMode,
                            isCurrentlyPlaying = isCurrentlyPlaying,
                            playbackIsPlaying = isPlayingRecording,
                            onClick = {
                                if (selectionMode) {
                                    selectedFiles = if (isSelected) {
                                        selectedFiles - recording.uri.toString()
                                    } else {
                                        selectedFiles + recording.uri.toString()
                                    }
                                    if (selectedFiles.isEmpty()) {
                                        selectionMode = false
                                    }
                                } else {
                                    if (isCurrentlyPlaying) {
                                        viewModel.togglePlayPauseRecording()
                                    } else {
                                        viewModel.playRecording(recording)
                                    }
                                }
                            },
                            onLongClick = {
                                if (!selectionMode) {
                                    selectionMode = true
                                    selectedFiles = setOf(recording.uri.toString())
                                }
                            },
                            onDelete = {
                                onDeleteRecording(recording)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
            
            if (stationRecordings.isEmpty() && activeSessions.isEmpty() && !shouldShowStickyHeader) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.general_no_recordings),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

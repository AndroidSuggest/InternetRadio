package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.domain.model.PlaybackSource
import com.armanmaurya.internetradio.domain.model.RadioStation
import com.armanmaurya.internetradio.domain.model.RecordingFile
import com.armanmaurya.internetradio.domain.model.RecordingSession
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingsViewModel
import androidx.compose.animation.*
import androidx.compose.animation.core.updateTransition
import com.armanmaurya.internetradio.ui.mobile.components.RecordingFileItem
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings.components.NowPlayingRecordingCard

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RecordingsContent(
    viewModel: RecordingsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    activeSessions: Map<String, RecordingSession>? = null,
    onStopRecording: ((String) -> Unit)? = null,
    onStationClick: (List<RadioStation>, Int, PlaybackSource) -> Unit = { _, _, _ -> },
    onEditStation: (String) -> Unit = {},
    onExportStation: ((RadioStation) -> Unit)? = null,
    playingStationUuid: String? = null,
    isPlaybackActive: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isFoldersLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()
    val vmActiveSessions by viewModel.activeSessions.collectAsStateWithLifecycle()
    val currentActiveSessions = activeSessions ?: vmActiveSessions
    val selectedStationName by viewModel.selectedStationName.collectAsStateWithLifecycle()
    val expandedRecordingUri by viewModel.expandedRecordingUri.collectAsStateWithLifecycle()
    val currentPlayingRecording by viewModel.currentPlayingRecording.collectAsStateWithLifecycle()
    val isPlayingRecording by viewModel.isPlayingRecording.collectAsStateWithLifecycle()
    val recordingPositionMs by viewModel.recordingPositionMs.collectAsStateWithLifecycle()
    val recordingDurationMs by viewModel.recordingDurationMs.collectAsStateWithLifecycle()
    val recordingPlaybackSpeed by viewModel.recordingPlaybackSpeed.collectAsStateWithLifecycle()
    val isRecordingLooping by viewModel.isRecordingLooping.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val isPureBlack = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black

    var cachedPlayingRecording by remember { mutableStateOf<RecordingFile?>(null) }
    if (currentPlayingRecording != null) {
        cachedPlayingRecording = currentPlayingRecording
    }
    val recordingToShow = currentPlayingRecording ?: cachedPlayingRecording

    val nowPlayingTransition = updateTransition(
        targetState = currentPlayingRecording != null,
        label = "NowPlayingTransition"
    )
    val shouldShowNowPlayingCard = currentPlayingRecording != null || nowPlayingTransition.currentState

    var cachedFolderStationName by remember { mutableStateOf<String?>(null) }
    if (selectedStationName != null) {
        cachedFolderStationName = selectedStationName
    }
    val folderStationNameToDisplay = selectedStationName ?: cachedFolderStationName

    val folderHeaderTransition = updateTransition(
        targetState = selectedStationName != null,
        label = "FolderHeaderTransition"
    )
    val shouldShowFolderHeader = selectedStationName != null || folderHeaderTransition.currentState

    var selectionMode by remember { mutableStateOf(false) }
    val selectionTransition = updateTransition(
        targetState = selectionMode,
        label = "SelectionTransition"
    )
    val shouldShowSelectionBar = selectionMode || selectionTransition.currentState
    var selectedFolders by remember { mutableStateOf(emptySet<String>()) }
    var selectedFiles by remember { mutableStateOf(emptySet<String>()) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode || selectedStationName != null) {
        if (selectionMode) {
            selectionMode = false
            selectedFolders = emptySet()
            selectedFiles = emptySet()
        } else if (selectedStationName != null) {
            viewModel.selectStation(null)
            selectionMode = false
            selectedFolders = emptySet()
            selectedFiles = emptySet()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }

    val routeEventId by viewModel.routeEventId.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    LaunchedEffect(routeEventId, selectedStationName) {
        if (routeEventId > 0L && selectedStationName != null && expandedRecordingUri != null && folders.isNotEmpty()) {
            val currentFolder = folders.find { it.stationName == selectedStationName } ?: return@LaunchedEffect
            val fileIndex = currentFolder.recordings.indexOfFirst { it.uri.toString() == expandedRecordingUri }
            if (fileIndex < 0) return@LaunchedEffect

            // Wait for folder transition to settle before checking viewport positions
            delay(320)

            val headerItem = listState.layoutInfo.visibleItemsInfo.find { it.key == "sticky_selection_and_folder_header" }
            val contentItem = listState.layoutInfo.visibleItemsInfo.find { it.key == "recordings_folders_content" }

            val itemHeightPx = if (contentItem != null && currentFolder.recordings.isNotEmpty() && contentItem.size > 0) {
                contentItem.size.toFloat() / currentFolder.recordings.size
            } else {
                with(density) { 80.dp.toPx() }
            }
            val headerBottomPx = (headerItem?.offset ?: 0) + (headerItem?.size ?: 0)
            val viewportEndPx = listState.layoutInfo.viewportEndOffset

            if (contentItem != null) {
                val itemTopInViewport = contentItem.offset + (fileIndex * itemHeightPx)
                val itemBottomInViewport = itemTopInViewport + itemHeightPx

                // If already fully visible between sticky header and viewport bottom, do NOT scroll!
                if (itemTopInViewport >= headerBottomPx && itemBottomInViewport <= viewportEndPx) {
                    return@LaunchedEffect
                }
            }

            // Otherwise, scroll smoothly so the item is placed just below the sticky header
            val targetScrollOffset = maxOf(0, (fileIndex * itemHeightPx - headerBottomPx).toInt())
            val contentItemIndex = contentItem?.index
                ?: run {
                    val activeSessionsOffset = if (currentActiveSessions.isNotEmpty()) 1 + (currentActiveSessions.size + 1) / 2 else 0
                    activeSessionsOffset + 1
                }
            listState.animateScrollToItem(index = contentItemIndex, scrollOffset = targetScrollOffset)
        }
    }

    if (showDeleteConfirmDialog) {
        val deleteCount = if (selectedStationName == null) selectedFolders.size else selectedFiles.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(stringResource(R.string.delete_recording)) },
            text = { Text(
                pluralStringResource(
                    R.plurals.delete_recordings_message,
                    deleteCount,
                    deleteCount
                )
            ) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    if (selectedStationName == null) {
                        viewModel.deleteFolders(selectedFolders.toList())
                    } else {
                        val currentFolder = folders.find { it.stationName == selectedStationName }
                        if (currentFolder != null) {
                            val filesToDelete = currentFolder.recordings.filter { it.uri.toString() in selectedFiles }
                            viewModel.deleteRecordings(filesToDelete)
                        }
                    }
                    selectionMode = false
                    selectedFolders = emptySet()
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

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        val libraryStationUuids by viewModel.libraryStationUuids.collectAsStateWithLifecycle()
        
        val isCardVisible = shouldShowNowPlayingCard || nowPlayingTransition.currentState
        val isHeaderVisible = isCardVisible || shouldShowSelectionBar || shouldShowFolderHeader

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = 16.dp
                )
            ) {
                if (currentActiveSessions.isNotEmpty()) {
                    item(key = "active_recordings_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.recordings_currently_recording),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    val sessionsList = currentActiveSessions.values.toList()
                    items(
                        items = sessionsList.chunked(2),
                        key = { rowSessions -> "session_row_" + rowSessions.joinToString { it.station.stationUuid } }
                    ) { rowSessions ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (session in rowSessions) {
                                val duration by session.durationSeconds.collectAsState(initial = 0L)
                                Box(modifier = Modifier.weight(1f)) {
                                    StationCard(
                                        station = session.station,
                                        onClick = { onStationClick(sessionsList.map { it.station }, sessionsList.indexOf(session), PlaybackSource.None) },
                                        isRecordingOverlay = true,
                                        recordingDuration = duration,
                                        onStopRecordingClick = { (onStopRecording ?: viewModel::stopRecording)(session.station.stationUuid) },
                                        isFavorite = libraryStationUuids.contains(session.station.stationUuid),
                                        onToggleFavoriteClick = { viewModel.toggleLibrary(session.station) },
                                        onEditClick = if (libraryStationUuids.contains(session.station.stationUuid)) { { onEditStation(session.station.stationUuid) } } else null,
                                        onExportClick = { onExportStation?.invoke(session.station) },
                                        isCurrentlyPlaying = playingStationUuid == session.station.stationUuid,
                                        isPlaybackActive = isPlaybackActive
                                    )
                                }
                            }
                            if (rowSessions.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                if (isHeaderVisible) {
                    stickyHeader(key = "sticky_selection_and_folder_header") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
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
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    start = 16.dp,
                                                    end = 16.dp,
                                                    top = 16.dp,
                                                    bottom = 0.dp
                                                )
                                        )
                                    }
                                }

                                selectionTransition.AnimatedVisibility(
                                    visible = { it },
                                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }) + expandVertically(expandFrom = Alignment.Top),
                                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }) + shrinkVertically(shrinkTowards = Alignment.Top)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                start = 16.dp,
                                                end = 16.dp,
                                                top = 16.dp,
                                                bottom = 0.dp
                                            ),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { 
                                                    selectionMode = false
                                                    selectedFolders = emptySet()
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
                                            text = stringResource(R.string.recordings_selected, if (selectedStationName == null) selectedFolders.size else selectedFiles.size),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                                        )
                                        
                                        val isAllSelected = if (selectedStationName == null) {
                                            selectedFolders.size == folders.size && folders.isNotEmpty()
                                        } else {
                                            val currentFolder = folders.find { it.stationName == selectedStationName }
                                            currentFolder != null && selectedFiles.size == currentFolder.recordings.size && currentFolder.recordings.isNotEmpty()
                                        }
                                        
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .clickable { 
                                                    if (isAllSelected) {
                                                        selectedFolders = emptySet()
                                                        selectedFiles = emptySet()
                                                    } else {
                                                        if (selectedStationName == null) {
                                                            selectedFolders = folders.map { it.stationName }.toSet()
                                                        } else {
                                                            val currentFolder = folders.find { it.stationName == selectedStationName }
                                                            if (currentFolder != null) {
                                                                selectedFiles = currentFolder.recordings.map { it.uri.toString() }.toSet()
                                                            }
                                                        }
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

                                folderHeaderTransition.AnimatedVisibility(
                                    visible = { it },
                                    enter = fadeIn() + slideInVertically(initialOffsetY = { -it }) + expandVertically(expandFrom = Alignment.Top),
                                    exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }) + shrinkVertically(shrinkTowards = Alignment.Top)
                                ) {
                                    val currentFolder = folders.find { it.stationName == folderStationNameToDisplay }
                                    if (currentFolder != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(
                                                    start = 16.dp,
                                                    end = 16.dp,
                                                    top = 16.dp,
                                                    bottom = 8.dp
                                                ),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .clickable { viewModel.selectStation(null) }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                                    contentDescription = stringResource(R.string.home_cd_back_to_folders),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                            ) {
                                                Text(
                                                    text = currentFolder.stationName,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (folders.isNotEmpty()) {
                    item(key = "recordings_folders_content") {
                        AnimatedContent(
                            targetState = selectedStationName,
                            transitionSpec = {
                                if (targetState != null) {
                                    // Entering folder (slide left)
                                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> -width } + fadeOut()
                                    )
                                } else {
                                    // Exiting folder (slide right)
                                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                                        slideOutHorizontally { width -> width } + fadeOut()
                                    )
                                }
                            },
                            label = "FolderTransition"
                        ) { stationName ->
                            val currentFolder = folders.find { it.stationName == stationName }

                            if (stationName == null || currentFolder == null) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    folders.forEachIndexed { index, folder ->
                                        key(folder.stationName) {
                                            val isSelected = folder.stationName in selectedFolders
                                            val hasActiveSessions = currentActiveSessions.isNotEmpty()
                                            val topPadding = if (index == 0 && !hasActiveSessions) 16.dp else 4.dp
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(
                                                        start = 16.dp,
                                                        end = 16.dp,
                                                        top = topPadding,
                                                        bottom = 4.dp
                                                    )
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                                        else if (isPureBlack) Color.Black 
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                    .then(
                                                        if (isPureBlack) Modifier.border(
                                                            1.dp,
                                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                                            RoundedCornerShape(12.dp)
                                                        ) else Modifier
                                                    )
                                                    .combinedClickable(
                                                        onClick = { 
                                                            if (selectionMode) {
                                                                if (isSelected) selectedFolders -= folder.stationName else selectedFolders += folder.stationName
                                                                if (selectedFolders.isEmpty()) selectionMode = false
                                                            } else {
                                                                viewModel.selectStation(folder.stationName)
                                                                selectionMode = false
                                                                selectedFolders = emptySet()
                                                                selectedFiles = emptySet()
                                                            }
                                                        },
                                                        onLongClick = {
                                                            if (!selectionMode) {
                                                                selectionMode = true
                                                                selectedFolders += folder.stationName
                                                            }
                                                        }
                                                    ),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(72.dp)
                                                        .clip(RoundedCornerShape(
                                                            topStart = 12.dp,
                                                            bottomStart = 12.dp,
                                                            topEnd = 8.dp,
                                                            bottomEnd = 8.dp
                                                        ))
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Folder,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }
                                                
                                                Spacer(modifier = Modifier.width(16.dp))
                                                
                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                    verticalArrangement = Arrangement.Center
                                                ) {
                                                    Text(
                                                        text = folder.stationName,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = pluralStringResource(
                                                            R.plurals.recordings_count,
                                                            folder.recordings.size,
                                                            folder.recordings.size
                                                        ),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                if (selectionMode) {
                                                    Checkbox(
                                                        checked = isSelected,
                                                        onCheckedChange = null,
                                                        modifier = Modifier.padding(end = 16.dp)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.width(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    currentFolder.recordings.forEach { recording ->
                                        key(recording.uri) {
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
                                                        if (isSelected) selectedFiles -= recording.uri.toString() else selectedFiles += recording.uri.toString()
                                                        if (selectedFiles.isEmpty()) selectionMode = false
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
                                                        selectedFiles += recording.uri.toString()
                                                    }
                                                },
                                                onDelete = {
                                                    viewModel.deleteRecording(recording)
                                                },
                                                modifier = Modifier.padding(horizontal = 16.dp)
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
        
        if (folders.isEmpty() && currentActiveSessions.isEmpty() && currentPlayingRecording == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.general_no_recordings),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

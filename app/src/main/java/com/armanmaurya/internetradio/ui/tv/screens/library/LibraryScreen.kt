package com.armanmaurya.internetradio.ui.tv.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.LibrarySortOption
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.ui.tv.components.StationCard
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    playingStationUuid: String?,
    isPlaybackActive: Boolean,
    onStationClick: (List<RadioStation>, Int, String) -> Unit,
    onAddStation: () -> Unit,
    onEditStation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val useFilter by viewModel.useFilter.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    var sortExpanded by remember { mutableStateOf(false) }

    var isDragLocked by remember { mutableStateOf(true) }
    var movingStationUuid by remember { mutableStateOf<String?>(null) }
    var currentStations by remember { mutableStateOf(stations ?: emptyList()) }

    LaunchedEffect(stations) {
        if (movingStationUuid == null) {
            currentStations = stations ?: emptyList()
        }
    }

    LaunchedEffect(sortOption) {
        if (sortOption != LibrarySortOption.CUSTOM) {
            if (movingStationUuid != null) {
                movingStationUuid = null
                viewModel.updateStationsOrder(currentStations)
            }
            isDragLocked = true
        }
    }

    BackHandler(enabled = movingStationUuid != null) {
        movingStationUuid = null
        viewModel.updateStationsOrder(currentStations)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (movingStationUuid != null) {
                viewModel.updateStationsOrder(currentStations)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp, end = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.toggleFilter() },
                            colors = ButtonDefaults.colors(
                                containerColor = if (useFilter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (useFilter) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                imageVector = if (useFilter) Icons.Default.Close else Icons.Default.FilterList,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(if (useFilter) stringResource(R.string.home_filters_active) else stringResource(R.string.home_use_filters))
                        }

                        val sortText = when (sortOption) {
                            LibrarySortOption.NAME_A_Z, LibrarySortOption.NAME_Z_A -> stringResource(R.string.home_sort_name)
                            LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.OLDEST_ADDED -> stringResource(R.string.home_sort_added)
                            LibrarySortOption.RECENTLY_PLAYED, LibrarySortOption.LEAST_RECENTLY_PLAYED -> stringResource(R.string.home_sort_played)
                            LibrarySortOption.CUSTOM -> stringResource(R.string.home_sort_custom)
                        }
                        val hasDirection = sortOption != LibrarySortOption.CUSTOM
                        val isDescending = when (sortOption) {
                            LibrarySortOption.NAME_A_Z, LibrarySortOption.RECENTLY_ADDED, LibrarySortOption.RECENTLY_PLAYED -> true
                            else -> false
                        }

                        Box {
                            Button(
                                onClick = { sortExpanded = true },
                                colors = ButtonDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(text = sortText)
                                if (hasDirection) {
                                    Icon(
                                        imageVector = if (isDescending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                        contentDescription = if (isDescending) stringResource(R.string.home_descending) else stringResource(R.string.home_ascending),
                                        modifier = Modifier.padding(start = 6.dp).size(18.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = sortExpanded,
                                onDismissRequest = { sortExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text(stringResource(R.string.home_sort_played)) },
                                    onClick = {
                                        if (sortOption == LibrarySortOption.RECENTLY_PLAYED) {
                                            viewModel.setSortOption(LibrarySortOption.LEAST_RECENTLY_PLAYED)
                                        } else {
                                            viewModel.setSortOption(LibrarySortOption.RECENTLY_PLAYED)
                                        }
                                        sortExpanded = false
                                    },
                                    trailingIcon = {
                                        if (sortOption == LibrarySortOption.RECENTLY_PLAYED || sortOption == LibrarySortOption.LEAST_RECENTLY_PLAYED) {
                                            androidx.compose.material3.Icon(
                                                imageVector = if (sortOption == LibrarySortOption.RECENTLY_PLAYED) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = if (sortOption == LibrarySortOption.RECENTLY_PLAYED) stringResource(R.string.home_descending) else stringResource(R.string.home_ascending),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text(stringResource(R.string.home_sort_added)) },
                                    onClick = {
                                        if (sortOption == LibrarySortOption.RECENTLY_ADDED) {
                                            viewModel.setSortOption(LibrarySortOption.OLDEST_ADDED)
                                        } else {
                                            viewModel.setSortOption(LibrarySortOption.RECENTLY_ADDED)
                                        }
                                        sortExpanded = false
                                    },
                                    trailingIcon = {
                                        if (sortOption == LibrarySortOption.RECENTLY_ADDED || sortOption == LibrarySortOption.OLDEST_ADDED) {
                                            androidx.compose.material3.Icon(
                                                imageVector = if (sortOption == LibrarySortOption.RECENTLY_ADDED) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = if (sortOption == LibrarySortOption.RECENTLY_ADDED) stringResource(R.string.home_descending) else stringResource(R.string.home_ascending),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text(stringResource(R.string.home_sort_name)) },
                                    onClick = {
                                        if (sortOption == LibrarySortOption.NAME_A_Z) {
                                            viewModel.setSortOption(LibrarySortOption.NAME_Z_A)
                                        } else {
                                            viewModel.setSortOption(LibrarySortOption.NAME_A_Z)
                                        }
                                        sortExpanded = false
                                    },
                                    trailingIcon = {
                                        if (sortOption == LibrarySortOption.NAME_A_Z || sortOption == LibrarySortOption.NAME_Z_A) {
                                            androidx.compose.material3.Icon(
                                                imageVector = if (sortOption == LibrarySortOption.NAME_A_Z) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                                                contentDescription = if (sortOption == LibrarySortOption.NAME_A_Z) stringResource(R.string.library_cd_sort_a_z) else stringResource(R.string.library_cd_sort_z_a),
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { androidx.compose.material3.Text(stringResource(R.string.home_sort_custom)) },
                                    onClick = {
                                        viewModel.setSortOption(LibrarySortOption.CUSTOM)
                                        sortExpanded = false
                                    },
                                    trailingIcon = {
                                        if (sortOption == LibrarySortOption.CUSTOM) {
                                            androidx.compose.material3.Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Active",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(visible = sortOption == LibrarySortOption.CUSTOM) {
                            Button(
                                onClick = {
                                    if (!isDragLocked && movingStationUuid != null) {
                                        movingStationUuid = null
                                        viewModel.updateStationsOrder(currentStations)
                                    }
                                    isDragLocked = !isDragLocked
                                },
                                colors = ButtonDefaults.colors(
                                    containerColor = if (isDragLocked) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                                    contentColor = if (isDragLocked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isDragLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = if (isDragLocked) "Unlock reordering" else "Lock reordering",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(if (isDragLocked) "Locked" else "Reorder")
                            }
                        }
                    }

                    Button(
                        onClick = onAddStation,
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(stringResource(R.string.edit_station_add_station))
                    }
                }
            }

            if (currentStations.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (useFilter) stringResource(R.string.home_no_library_stations_filter) else stringResource(R.string.home_no_library_stations_yet),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = currentStations,
                    key = { _, station -> station.stationUuid }
                ) { index, station ->
                    val isMoving = movingStationUuid == station.stationUuid
                    val focusRequester = remember { FocusRequester() }

                    LaunchedEffect(index, isMoving) {
                        if (isMoving) {
                            try {
                                focusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    }

                    val moveModifier = if (isMoving) {
                        Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown) {
                                val currentIndex = currentStations.indexOfFirst { it.stationUuid == station.stationUuid }
                                if (currentIndex != -1) {
                                    val targetIndex = when (event.key) {
                                        Key.DirectionLeft -> (currentIndex - 1).coerceAtLeast(0)
                                        Key.DirectionRight -> (currentIndex + 1).coerceAtMost(currentStations.lastIndex)
                                        Key.DirectionUp -> (currentIndex - 4).coerceAtLeast(0)
                                        Key.DirectionDown -> (currentIndex + 4).coerceAtMost(currentStations.lastIndex)
                                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Spacebar, Key.Back, Key.Escape -> {
                                            movingStationUuid = null
                                            viewModel.updateStationsOrder(currentStations)
                                            return@onPreviewKeyEvent true
                                        }
                                        else -> null
                                    }
                                    if (targetIndex != null) {
                                        if (targetIndex != currentIndex) {
                                            currentStations = currentStations.toMutableList().apply {
                                                add(targetIndex, removeAt(currentIndex))
                                            }
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            } else if (event.type == KeyEventType.KeyUp) {
                                val isNavOrConfirm = event.key == Key.DirectionCenter ||
                                        event.key == Key.Enter ||
                                        event.key == Key.NumPadEnter ||
                                        event.key == Key.Spacebar ||
                                        event.key == Key.Back ||
                                        event.key == Key.Escape ||
                                        event.key == Key.DirectionLeft ||
                                        event.key == Key.DirectionRight ||
                                        event.key == Key.DirectionUp ||
                                        event.key == Key.DirectionDown
                                if (isNavOrConfirm) {
                                    return@onPreviewKeyEvent true
                                }
                            }
                            false
                        }
                    } else Modifier

                    StationCard(
                        station = station,
                        onClick = {
                            if (sortOption == LibrarySortOption.CUSTOM && !isDragLocked) {
                                if (movingStationUuid == station.stationUuid) {
                                    movingStationUuid = null
                                    viewModel.updateStationsOrder(currentStations)
                                } else {
                                    movingStationUuid = station.stationUuid
                                }
                            } else {
                                onStationClick(currentStations, index, "tv_library")
                            }
                        },
                        onLongClick = if (sortOption == LibrarySortOption.CUSTOM && !isDragLocked) {
                            {
                                if (movingStationUuid == station.stationUuid) {
                                    movingStationUuid = null
                                    viewModel.updateStationsOrder(currentStations)
                                } else {
                                    movingStationUuid = station.stationUuid
                                }
                            }
                        } else null,
                        modifier = Modifier
                            .animateItem()
                            .focusRequester(focusRequester)
                            .then(moveModifier),
                        isCurrentlyPlaying = station.stationUuid == playingStationUuid,
                        isPlaybackActive = isPlaybackActive && station.stationUuid == playingStationUuid,
                        isMoving = isMoving
                    )
                }
            }
        }
    }
}

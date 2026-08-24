package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.recordings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.repository.RecordingFile
import com.armanmaurya.internetradio.player.RecordingSession
import com.armanmaurya.internetradio.ui.mobile.components.RecordingFileItem
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard

@Composable
fun RecordingsTab(
    activeSessions: Map<String, RecordingSession>,
    stationRecordings: List<RecordingFile>,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    onStopRecording: (String) -> Unit,
    onDeleteRecording: (RecordingFile) -> Unit
) {
    var expandedRecording by remember { mutableStateOf<RecordingFile?>(null) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .nestedScroll(nestedScrollConnection),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            if (activeSessions.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recordings_currently_recording),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(activeSessions.values.chunked(2)) { rowSessions ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                
                item {
                    Divider(modifier = Modifier.padding(vertical = 16.dp))
                    if (stationRecordings.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.recordings_saved),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
            }
    
            if (stationRecordings.isNotEmpty()) {
                items(
                    items = stationRecordings,
                    key = { it.uri.toString() }
                ) { recording ->
                    val isExpanded = expandedRecording?.uri == recording.uri
    
                    RecordingFileItem(
                        recording = recording,
                        isExpanded = isExpanded,
                        onClick = {
                            expandedRecording = if (isExpanded) null else recording
                        },
                        onDelete = { onDeleteRecording(recording) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
        
        if (stationRecordings.isEmpty() && activeSessions.isEmpty()) {
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

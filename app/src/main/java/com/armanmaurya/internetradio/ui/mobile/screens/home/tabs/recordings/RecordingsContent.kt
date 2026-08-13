package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.data.repository.RecordingFile
import com.armanmaurya.internetradio.data.repository.RecordingFolder
import com.armanmaurya.internetradio.ui.shared.viewmodels.RecordingsViewModel
import java.util.Locale
import androidx.compose.animation.*
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import android.media.MediaPlayer
import com.armanmaurya.internetradio.ui.mobile.components.RecordingFileItem

@Composable
fun RecordingsContent(
    viewModel: RecordingsViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel(),
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    var selectedStationName by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val isPureBlack = MaterialTheme.colorScheme.surface == androidx.compose.ui.graphics.Color.Black

    LaunchedEffect(Unit) {
        viewModel.loadFolders()
    }

    LaunchedEffect(folders, selectedStationName) {
        if (selectedStationName != null && folders.none { it.stationName == selectedStationName }) {
            selectedStationName = null
        }
    }

    if (folders.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
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
    } else {
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
                // Show Folders
                LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp)
            ) {
                items(
                    items = folders,
                    key = { it.stationName }
                ) { folder ->
                    Row(
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isPureBlack) androidx.compose.ui.graphics.Color.Black 
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .then(
                                if (isPureBlack) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(12.dp)
                                ) else Modifier
                            )
                            .clickable { selectedStationName = folder.stationName },
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
                        
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                }
            }
        } else {
            // Show Files in Folder
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedStationName = null }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.home_cd_back_to_folders),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentFolder.stationName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                var expandedRecording by remember { mutableStateOf<RecordingFile?>(null) }
                
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)
                ) {
                    items(
                        items = currentFolder.recordings,
                        key = { it.uri.toString() }
                    ) { recording ->
                        val isExpanded = expandedRecording?.uri == recording.uri
                        RecordingFileItem(
                            recording = recording,
                            isExpanded = isExpanded,
                            onClick = {
                                expandedRecording = if (isExpanded) null else recording
                            },
                            onDelete = {
                                viewModel.deleteRecording(recording)
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}
}

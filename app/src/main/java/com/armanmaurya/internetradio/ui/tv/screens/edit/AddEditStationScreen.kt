package com.armanmaurya.internetradio.ui.tv.screens.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AddEditStationScreen(
    stationUuid: String?,
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit
) {
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val station = if (stationUuid != null) stations?.find { it.stationUuid == stationUuid } else null
    val isEditing = stationUuid != null

    BackHandler { onNavigateBack() }

    var name by remember(station) { mutableStateOf(station?.name ?: "") }
    var url by remember(station) { mutableStateOf(station?.url ?: "") }
    var favicon by remember(station) { mutableStateOf(station?.favicon ?: "") }
    var tags by remember(station) { mutableStateOf(station?.tags?.joinToString(", ") ?: "") }
    var country by remember(station) { mutableStateOf(station?.country ?: "") }
    var language by remember(station) { mutableStateOf(station?.language ?: "") }

    val firstFieldFocusRequester = remember { FocusRequester() }

    val canSave = name.isNotBlank() && url.isNotBlank()

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background from favicon
        if (favicon.isNotBlank()) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(favicon)
                    .size(coil3.size.Size.ORIGINAL)
                    .build(),
                contentDescription = null,
                filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // ── Left panel ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .padding(start = 64.dp, top = 48.dp, bottom = 48.dp, end = 32.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Back button
                Surface(
                    onClick = onNavigateBack,
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(8.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Back", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Artwork preview
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    val fallbackPainter = painterResource(id = R.drawable.ic_launcher_foreground)
                    if (favicon.isNotBlank()) {
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                                .data(favicon)
                                .size(coil3.size.Size.ORIGINAL)
                                .build(),
                            contentDescription = "Preview",
                            filterQuality = FilterQuality.High,
                            placeholder = fallbackPainter,
                            error = fallbackPainter,
                            fallback = fallbackPainter,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = name.ifBlank { if (isEditing) "Edit Station" else "New Station" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2
                )

                Spacer(Modifier.weight(1f))

                // Save button
                Button(
                    onClick = {
                        if (isEditing && station != null) {
                            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            viewModel.updateStation(
                                stationUuid = station.stationUuid,
                                name = name,
                                url = url,
                                favicon = favicon,
                                tags = tagList
                            )
                        } else {
                            viewModel.addStation(
                                name = name,
                                url = url,
                                favicon = favicon,
                                tags = tags,
                                country = country,
                                state = "",
                                language = language
                            )
                        }
                        onNavigateBack()
                    },
                    enabled = canSave,
                    scale = ButtonDefaults.scale(focusedScale = 1f),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    ),
                    shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isEditing) "Save Changes" else "Add Station",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            // ── Right panel — form ───────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 48.dp, bottom = 48.dp, end = 96.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = if (isEditing) "Edit Station" else "Add Custom Station",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(Modifier.height(8.dp))

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.border.copy(alpha = 0.5f),
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { androidx.compose.material3.Text("Name *") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(firstFieldFocusRequester),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { androidx.compose.material3.Text("Stream URL *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                OutlinedTextField(
                    value = favicon,
                    onValueChange = { favicon = it },
                    label = { androidx.compose.material3.Text("Favicon URL (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = country,
                        onValueChange = { country = it },
                        label = { androidx.compose.material3.Text("Country (Optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                        )
                    )
                    OutlinedTextField(
                        value = language,
                        onValueChange = { language = it },
                        label = { androidx.compose.material3.Text("Language (Optional)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = fieldColors,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                        )
                    )
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { androidx.compose.material3.Text("Tags (comma separated, Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f)
                    )
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "* Required fields",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

package com.armanmaurya.internetradio.ui.mobile.screens.edit

import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditStationScreen(
    stationUuid: String?,
    viewModel: LibraryViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val station = if (stationUuid != null) stations?.find { it.stationUuid == stationUuid } else null

    val isEditing = stationUuid != null

    if (isEditing && station == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.edit_station_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(stringResource(R.string.error_station_not_found))
            }
        }
    } else {
        val coroutineScope = rememberCoroutineScope()
        var name by remember(station) { mutableStateOf(station?.name ?: "") }
        var url by remember(station) { mutableStateOf(station?.url ?: "") }
        var favicon by remember(station) { mutableStateOf(station?.favicon ?: "") }
        var tags by remember(station) { mutableStateOf(station?.tags?.joinToString(", ") ?: "") }
        var countryCode by remember(station) { mutableStateOf(station?.countryCode ?: "") }
        
        val countries = remember {
            java.util.Locale.getISOCountries().map { code ->
                code to java.util.Locale("", code).getDisplayCountry(java.util.Locale.getDefault())
            }.sortedBy { it.second }
        }
        var expandedCountry by remember { mutableStateOf(false) }
        var countrySearchText by remember(station) {
            mutableStateOf(
                if (countryCode.isNotBlank()) {
                    java.util.Locale("", countryCode).getDisplayCountry(java.util.Locale.getDefault())
                } else ""
            )
        }
        var languageCodes by remember(station) { mutableStateOf(station?.languageCodes?.joinToString(", ") ?: "") }
        
        val allLanguages = remember {
            java.util.Locale.getISOLanguages().map { code ->
                code to java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault())
            }.sortedBy { it.second }
        }
        var expandedLanguage by remember { mutableStateOf(false) }
        var languageSearchText by remember { mutableStateOf("") }
        var homepage by remember(station) { mutableStateOf(station?.homepage ?: "") }
        
        val fetchedTags by viewModel.fetchedTags.collectAsStateWithLifecycle()
        var expandedTags by remember { mutableStateOf(false) }
        var tagsSearchText by remember { mutableStateOf("") }

        var isProbing by remember { mutableStateOf(false) }
        var probedCodec by remember(station) { mutableStateOf(station?.codec?.takeIf { it.isNotBlank() } ?: "unknown") }
        var probedBitrate by remember(station) { mutableStateOf(station?.bitrate ?: 0) }

        LaunchedEffect(url) {
            if (url.startsWith("http")) {
                if (station != null && url == station.url && (station.codec.isNotBlank() || station.bitrate > 0)) {
                    isProbing = false
                } else {
                    isProbing = true
                    kotlinx.coroutines.delay(500) // Debounce
                    val result = viewModel.probeStream(url)
                    if (result != null) {
                        probedCodec = result.codec
                        probedBitrate = result.bitrate
                    }
                    isProbing = false
                }
            } else {
                isProbing = false
            }
        }

        val hasUnsavedChanges = if (isEditing && station != null) {
            name != station.name ||
            url != station.url ||
            favicon != station.favicon ||
            tags != station.tags.joinToString(", ") ||
            countryCode != station.countryCode ||
            languageCodes != station.languageCodes.joinToString(", ") ||
            homepage != station.homepage
        } else {
            name.isNotBlank() || url.isNotBlank() || favicon.isNotBlank() || tags.isNotBlank() || countryCode.isNotBlank() || languageCodes.isNotBlank() || homepage.isNotBlank()
        }

        var showExitWarningDialog by remember { mutableStateOf(false) }

        val handleBackPress = {
            if (hasUnsavedChanges) {
                showExitWarningDialog = true
            } else {
                onNavigateBack()
            }
        }

        BackHandler(enabled = hasUnsavedChanges) {
            showExitWarningDialog = true
        }

        if (showExitWarningDialog) {
            AlertDialog(
                onDismissRequest = { showExitWarningDialog = false },
                title = { Text(stringResource(R.string.edit_station_unsaved_changes)) },
                text = { Text(stringResource(R.string.edit_station_unsaved_changes_message_exit)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showExitWarningDialog = false
                            onNavigateBack()
                        }
                    ) {
                        Text(stringResource(R.string.general_discard))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitWarningDialog = false }) {
                        Text(stringResource(R.string.general_cancel))
                    }
                }
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isEditing) stringResource(R.string.edit_station_title) else stringResource(R.string.edit_station_add_station)) },
                    navigationIcon = {
                        IconButton(onClick = handleBackPress) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                        }
                    },
                    actions = {
                        if (isEditing && station != null && !station.isCustom) {
                            var isResetting by remember { mutableStateOf(false) }
                            var showResetDialog by remember { mutableStateOf(false) }

                            if (showResetDialog) {
                                AlertDialog(
                                    onDismissRequest = { showResetDialog = false },
                                    title = { Text(stringResource(R.string.edit_station_reset_title)) },
                                    text = { Text(stringResource(R.string.edit_station_reset_message)) },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                showResetDialog = false
                                                isResetting = true
                                                viewModel.fetchOriginalStation(station.stationUuid) { freshStation ->
                                                    isResetting = false
                                                    if (freshStation != null) {
                                                        name = freshStation.name
                                                        url = freshStation.url
                                                        favicon = freshStation.favicon
                                                        tags = freshStation.tags.joinToString(", ")
                                                        countryCode = freshStation.countryCode
                                                        countrySearchText = if (countryCode.isNotBlank()) {
                                                            java.util.Locale("", countryCode).getDisplayCountry(java.util.Locale.getDefault())
                                                        } else ""
                                                        languageCodes = freshStation.languageCodes.joinToString(", ")
                                                        homepage = freshStation.homepage
                                                        Toast.makeText(context, context.getString(R.string.edit_station_fields_reset_message), Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        Toast.makeText(context, context.getString(R.string.edit_station_failed_fetch_original_data), Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            }
                                        ) {
                                            Text(stringResource(R.string.general_reset))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showResetDialog = false }) {
                                            Text(stringResource(R.string.general_cancel))
                                        }
                                    }
                                )
                            }

                            if (isResetting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp).size(24.dp), 
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { showResetDialog = true }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.edit_station_cd_reset_to_original))
                                }
                            }
                        }
                        Button(
                            onClick = {
                                if (isEditing && station != null) {
                                    val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    val langList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                    viewModel.updateStation(
                                        stationUuid = station.stationUuid,
                                        name = name,
                                        url = url,
                                        favicon = favicon,
                                        tags = tagList,
                                        countryCode = countryCode,
                                        languageCodes = langList,
                                        homepage = homepage,
                                        codec = probedCodec,
                                        bitrate = probedBitrate
                                    )
                                } else {
                                    viewModel.addStation(
                                        name = name,
                                        url = url,
                                        favicon = favicon,
                                        tags = tags,
                                        countryCode = countryCode,
                                        languageCodes = languageCodes,
                                        homepage = homepage,
                                        codec = probedCodec,
                                        bitrate = probedBitrate
                                    )
                                }
                                Toast.makeText(context, context.getString(R.string.edit_station_saved_message), Toast.LENGTH_SHORT).show()
                                onNavigateBack()
                            },
                            modifier = Modifier.padding(end = 16.dp),
                            enabled = name.isNotBlank() && url.isNotBlank() && hasUnsavedChanges && !isProbing
                        ) {
                            if (isProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(if (isEditing) stringResource(R.string.general_save) else stringResource(R.string.general_add))
                        }
                    }
                )
            }
        ) { paddingValues ->
            val isPureBlack = MaterialTheme.colorScheme.surface == Color.Black
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.edit_station_name_field)) },
                    modifier = Modifier.fillMaxWidth().then(
                        if (isPureBlack) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.edit_station_stream_url_field)) },
                    modifier = Modifier.fillMaxWidth().then(
                        if (isPureBlack) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                    )
                )


                TextField(
                    value = favicon,
                    onValueChange = { favicon = it },
                    label = { Text(stringResource(R.string.edit_station_favicon_url_optional)) },
                    modifier = Modifier.fillMaxWidth().then(
                        if (isPureBlack) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                TextField(
                    value = homepage,
                    onValueChange = { homepage = it },
                    label = { Text("Homepage URL (Optional)") },
                    modifier = Modifier.fillMaxWidth().then(
                        if (isPureBlack) Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        ) else Modifier
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                ExposedDropdownMenuBox(
                    expanded = expandedCountry,
                    onExpandedChange = { expandedCountry = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                        TextField(
                            value = countrySearchText,
                            onValueChange = { newValue ->
                                countrySearchText = newValue
                                expandedCountry = true
                                val matched = countries.find { c -> c.second.equals(newValue, ignoreCase = true) }
                                countryCode = matched?.first ?: ""
                            },
                            label = { Text(stringResource(R.string.edit_station_country_field)) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true).fillMaxWidth().then(
                                if (isPureBlack) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(16.dp)
                                ) else Modifier
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCountry) },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        val filteredCountries = countries.filter { it.second.contains(countrySearchText, ignoreCase = true) }
                        if (filteredCountries.isNotEmpty() && expandedCountry) {
                            ExposedDropdownMenu(
                                expanded = expandedCountry,
                                onDismissRequest = { expandedCountry = false }
                            ) {
                                filteredCountries.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption.second) },
                                        onClick = {
                                            countryCode = selectionOption.first
                                            countrySearchText = selectionOption.second
                                            expandedCountry = false
                                        }
                                    )
                                }
                            }
                        }
                }
                ExposedDropdownMenuBox(
                    expanded = expandedLanguage,
                    onExpandedChange = { expandedLanguage = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                        TextField(
                            value = languageSearchText,
                            onValueChange = { 
                                languageSearchText = it
                                expandedLanguage = true
                            },
                            label = { Text(stringResource(R.string.edit_station_add_languages)) },
                            modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true).fillMaxWidth().then(
                                if (isPureBlack) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(16.dp)
                                ) else Modifier
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLanguage) },
                            colors = TextFieldDefaults.colors(
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        
                        val filteredLanguages = allLanguages.filter { it.second.contains(languageSearchText, ignoreCase = true) }
                        if (filteredLanguages.isNotEmpty() && expandedLanguage) {
                            ExposedDropdownMenu(
                                expanded = expandedLanguage,
                                onDismissRequest = { expandedLanguage = false }
                            ) {
                                filteredLanguages.forEach { selectionOption ->
                                    DropdownMenuItem(
                                        text = { Text(selectionOption.second) },
                                        onClick = {
                                            val currentCodes = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                            if (!currentCodes.contains(selectionOption.first)) {
                                                val sep = if (currentCodes.isEmpty()) "" else ", "
                                                languageCodes = languageCodes.trimEnd(',', ' ') + sep + selectionOption.first
                                            }
                                            languageSearchText = ""
                                            expandedLanguage = false
                                        }
                                    )
                                }
                            }
                        }
                }
                
                val selectedLanguagesList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (selectedLanguagesList.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedLanguagesList.forEach { code ->
                            val dispName = java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault())
                            InputChip(
                                selected = true,
                                onClick = { },
                                label = { Text(if (dispName.isNotBlank()) dispName else code) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove language",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                val updated = selectedLanguagesList.toMutableList()
                                                updated.remove(code)
                                                languageCodes = updated.joinToString(", ")
                                            }
                                    )
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = expandedTags,
                    onExpandedChange = { expandedTags = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = tagsSearchText,
                        onValueChange = { 
                            tagsSearchText = it
                            expandedTags = true
                            viewModel.onTagSearchQueryChange(it)
                        },
                        label = { Text(stringResource(R.string.edit_station_add_tags)) },
                        modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true).fillMaxWidth().then(
                            if (isPureBlack) Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(16.dp)
                            ) else Modifier
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTags) },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = if (isPureBlack) Color.Black else MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    
                    val query = tagsSearchText.trim()
                    if ((fetchedTags.isNotEmpty() || query.isNotEmpty()) && expandedTags) {
                        ExposedDropdownMenu(
                            expanded = expandedTags,
                            onDismissRequest = { expandedTags = false }
                        ) {
                            if (query.isNotEmpty() && fetchedTags.none { it.name.equals(query, ignoreCase = true) }) {
                                DropdownMenuItem(
                                    text = { Text("Create Tag: '$query'") },
                                    onClick = {
                                        val currentTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        if (!currentTags.any { it.equals(query, ignoreCase = true) }) {
                                            val sep = if (currentTags.isEmpty()) "" else ", "
                                            tags = tags.trimEnd(',', ' ') + sep + query
                                        }
                                        tagsSearchText = ""
                                        expandedTags = false
                                        viewModel.onTagSearchQueryChange("")
                                    }
                                )
                            }
                            fetchedTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text(tag.name) },
                                    onClick = {
                                        val currentTags = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                        if (!currentTags.any { it.equals(tag.name, ignoreCase = true) }) {
                                            val sep = if (currentTags.isEmpty()) "" else ", "
                                            tags = tags.trimEnd(',', ' ') + sep + tag.name
                                        }
                                        tagsSearchText = ""
                                        expandedTags = false
                                        viewModel.onTagSearchQueryChange("")
                                    }
                                )
                            }
                        }
                    }
                }
                
                val selectedTagsList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (selectedTagsList.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        selectedTagsList.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove tag",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable {
                                                val updated = selectedTagsList.toMutableList()
                                                updated.remove(tag)
                                                tags = updated.joinToString(", ")
                                            }
                                    )
                                }
                            )
                        }
                    }
                }
                
                if (probedCodec != "unknown" || probedBitrate > 0) {
                    val bitrateText = if (probedBitrate > 0) "$probedBitrate kbps" else "Unknown bitrate"
                    val codecText = if (probedCodec != "unknown") probedCodec.uppercase() else "Unknown Codec"
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Metadata",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$codecText • $bitrateText",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (isProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp).padding(4.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        if (url.startsWith("http")) {
                                            isProbing = true
                                            coroutineScope.launch {
                                                val result = viewModel.probeStream(url)
                                                if (result != null) {
                                                    probedCodec = result.codec
                                                    probedBitrate = result.bitrate
                                                }
                                                isProbing = false
                                            }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh Metadata")
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(160.dp))
            }
        }
    }
}

package com.armanmaurya.internetradio.ui.mobile.screens.settings


import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import org.xmlpull.v1.XmlPullParser
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.AppPreferences
import com.armanmaurya.internetradio.data.model.ConflictStrategy
import com.armanmaurya.internetradio.data.model.StartOfWeek
import com.armanmaurya.internetradio.ui.shared.viewmodels.SettingsViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ExpandableItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Item
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.OptionItem
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.Section
import com.armanmaurya.internetradio.ui.mobile.screens.settings.components.ToggleItem
import com.armanmaurya.internetradio.ui.shared.theme.AppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit,
    onAboutClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val uiState by viewModel.uiState.collectAsState()

    // UI-only state for expand/collapse
    var expandedItem by remember { mutableStateOf<String?>(null) }
    var showHistoryLimitDialog by remember { mutableStateOf(false) }

    // Toast feedback for backup/restore operations
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.backupResult.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Activity result launchers for file picker
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportLibrary(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLibraries(context, uris)
        }
    }

    val topShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
    val middleShape = RoundedCornerShape(4.dp)
    val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val singleShape = RoundedCornerShape(24.dp)

    Scaffold(
        modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
        topBar = { SettingsTopBar(onBackClick) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            AppearanceSection(
                uiState = uiState,
                availableThemes = listOf(AppTheme.LIGHT, AppTheme.DARK, AppTheme.SYSTEM),
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetDynamicTheme = viewModel::setDynamicTheme,
                onSetTheme = viewModel::setAppTheme,
                onSetPureBlack = viewModel::setPureBlack,
                topShape = topShape,
                middleShape = middleShape,
                bottomShape = bottomShape
            )
            GeneralSection(
                uiState = uiState,
                languages = rememberAvailableLanguages(),
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetLanguage = viewModel::setAppLanguage,
                onSetStartOfWeek = viewModel::setStartOfWeek,
                onSetDefaultTab = viewModel::setDefaultTab,
                onSetAutoRouteToBrowseOnSearch = viewModel::setAutoRouteToBrowseOnSearch,
                onSetDisableUpdateCheck = viewModel::setDisableUpdateCheck,
                topShape = topShape,
                middleShape = middleShape,
                bottomShape = bottomShape
            )
            PlayerSection(
                uiState = uiState,
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetAutoPlayOnStart = viewModel::setAutoPlayOnStart,
                onSetStopOnAudioBecomingNoisy = viewModel::setStopOnAudioBecomingNoisy,
                onSetShowCoverArtInNotification = viewModel::setShowCoverArtInNotification,
                showHistoryLimitDialog = showHistoryLimitDialog,
                onToggleHistoryLimitDialog = { showHistoryLimitDialog = !showHistoryLimitDialog },
                onSetHistoryLimit = viewModel::setTrackHistoryLimit,
                onSetMaxRetryDuration = viewModel::setMaxRetryDuration,
                topShape = topShape,
                middleShape = middleShape,
                bottomShape = bottomShape,
                singleShape = singleShape
            )
            BackupSection(
                uiState = uiState,
                expandedItem = expandedItem,
                onExpandedItemChange = { expandedItem = it },
                onSetConflictStrategy = viewModel::setConflictStrategy,
                onExport = { exportLauncher.launch("stations.json") },
                onImport = { importLauncher.launch(arrayOf("application/json")) },
                topShape = topShape,
                bottomShape = bottomShape
            )
            AboutSection(
                onAboutClick = onAboutClick,
                onCheckUpdatesClick = onCheckUpdatesClick,
                topShape = topShape,
                middleShape = middleShape,
                bottomShape = bottomShape
            )

            val packageInfo = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0)
                } catch (e: Exception) {
                    null
                }
            }
            val versionName = packageInfo?.versionName ?: stringResource(R.string.general_unknown)
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode?.toString() ?: "0"
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toString() ?: "0"
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "v$versionName ($versionCode)",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(onBackClick: () -> Unit) {
    CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back)
                )
            }
        }
    )
}

@Composable
private fun AppearanceSection(
    uiState: AppPreferences,
    availableThemes: List<AppTheme>,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetDynamicTheme: (Boolean) -> Unit,
    onSetTheme: (AppTheme) -> Unit,
    onSetPureBlack: (Boolean) -> Unit,
    topShape: RoundedCornerShape,
    middleShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    Section(title = stringResource(R.string.settings_appearance_section)) {
        ToggleItem(
            title = stringResource(R.string.settings_dynamic_theme_title),
            subtitle = stringResource(R.string.settings_dynamic_theme_subtitle),
            isEnabled = uiState.useDynamicColor,
            onToggle = onSetDynamicTheme,
            icon = Icons.Default.AutoAwesome,
            shape = topShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ExpandableItem(
            title = stringResource(R.string.settings_theme_title),
            subtitle = uiState.themeMode.toDisplayString(),
            isExpanded = expandedItem == "Theme",
            onToggle = { onExpandedItemChange(if (expandedItem == "Theme") null else "Theme") },
            icon = Icons.Default.Brightness4,
            shape = middleShape
        ) {
            availableThemes.forEach { theme ->
                OptionItem(
                    label = theme.toDisplayString(),
                    isSelected = uiState.themeMode == theme,
                    onClick = { onSetTheme(theme) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_pure_black_title),
            subtitle = stringResource(R.string.settings_pure_black_subtitle),
            isEnabled = uiState.pureBlack,
            onToggle = onSetPureBlack,
            icon = Icons.Default.Contrast,
            shape = bottomShape
        )
    }
}

@Composable
private fun AppTheme.toDisplayString(): String = when (this) {
    AppTheme.LIGHT -> stringResource(R.string.settings_theme_light)
    AppTheme.DARK -> stringResource(R.string.settings_theme_dark)
    AppTheme.SYSTEM -> stringResource(R.string.settings_theme_system)
}

@Composable
private fun StartOfWeek.toDisplayString(): String = when (this) {
    StartOfWeek.SUNDAY -> stringResource(R.string.settings_start_week_sunday)
    StartOfWeek.MONDAY -> stringResource(R.string.settings_start_week_monday)
    StartOfWeek.FRIDAY -> stringResource(R.string.settings_start_week_friday)
    StartOfWeek.SATURDAY -> stringResource(R.string.settings_start_week_saturday)
}

@Composable
private fun GeneralSection(
    uiState: AppPreferences,
    languages: List<Pair<String, String>>,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetStartOfWeek: (StartOfWeek) -> Unit,
    onSetDefaultTab: (Int) -> Unit,
    onSetAutoRouteToBrowseOnSearch: (Boolean) -> Unit,
    onSetDisableUpdateCheck: (Boolean) -> Unit,
    topShape: RoundedCornerShape,
    middleShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    val currentLocales = AppCompatDelegate.getApplicationLocales()
    val activeLanguageCode = if (currentLocales.isEmpty) {
        "System"
    } else {
        currentLocales[0]?.language ?: "System"
    }

    Section(title = stringResource(R.string.settings_general_section)) {
        ExpandableItem(
            title = stringResource(R.string.settings_language_title),
            subtitle = activeLanguageCode.getLanguageDisplayName(languages),
            isExpanded = expandedItem == "Language",
            onToggle = { onExpandedItemChange(if (expandedItem == "Language") null else "Language") },
            icon = Icons.Default.Translate,
            shape = topShape
        ) {
            languages.forEach { (code, name) ->
                OptionItem(
                    label = name,
                    isSelected = activeLanguageCode == code,
                    onClick = { onSetLanguage(code) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ExpandableItem(
            title = stringResource(R.string.settings_start_week_title),
            subtitle = uiState.startOfWeek.toDisplayString(),
            isExpanded = expandedItem == "StartWeek",
            onToggle = { onExpandedItemChange(if (expandedItem == "StartWeek") null else "StartWeek") },
            icon = Icons.Default.CalendarMonth,
            shape = middleShape
        ) {
            StartOfWeek.entries.forEach { startOfWeek ->
                OptionItem(
                    label = startOfWeek.toDisplayString(),
                    isSelected = uiState.startOfWeek == startOfWeek,
                    onClick = { onSetStartOfWeek(startOfWeek) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        val tabs = listOf(
            stringResource(R.string.home_tab_browse),
            stringResource(R.string.home_tab_recent),
            stringResource(R.string.home_tab_library),
            stringResource(R.string.home_tab_recordings),
            stringResource(R.string.home_tab_schedules)
        )

        ExpandableItem(
            title = stringResource(R.string.settings_default_tab),
            subtitle = tabs.getOrNull(uiState.defaultTab) ?: stringResource(R.string.home_tab_browse),
            isExpanded = expandedItem == "DefaultTab",
            onToggle = { onExpandedItemChange(if (expandedItem == "DefaultTab") null else "DefaultTab") },
            icon = Icons.Default.StarRate,
            shape = middleShape
        ) {
            tabs.forEachIndexed { index, name ->
                OptionItem(
                    label = name,
                    isSelected = uiState.defaultTab == index,
                    onClick = { onSetDefaultTab(index) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_auto_route_search_title),
            subtitle = stringResource(R.string.settings_auto_route_search_subtitle),
            isEnabled = uiState.autoRouteToBrowseOnSearch,
            onToggle = onSetAutoRouteToBrowseOnSearch,
            icon = Icons.Default.Search,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_disable_update_check),
            subtitle = stringResource(R.string.settings_disable_update_check_desc),
            isEnabled = uiState.disableUpdateCheck,
            onToggle = onSetDisableUpdateCheck,
            icon = Icons.Default.Update,
            shape = bottomShape
        )
    }
}

@Composable
private fun PlayerSection(
    uiState: AppPreferences,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetAutoPlayOnStart: (Boolean) -> Unit,
    onSetStopOnAudioBecomingNoisy: (Boolean) -> Unit,
    onSetShowCoverArtInNotification: (Boolean) -> Unit,
    showHistoryLimitDialog: Boolean,
    onToggleHistoryLimitDialog: () -> Unit,
    onSetHistoryLimit: (Int) -> Unit,
    onSetMaxRetryDuration: (Long) -> Unit,
    topShape: RoundedCornerShape,
    middleShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape,
    singleShape: RoundedCornerShape
) {
    Section(title = stringResource(R.string.settings_player_section)) {
        ToggleItem(
            title = stringResource(R.string.settings_auto_play),
            subtitle = stringResource(R.string.settings_auto_play_desc),
            isEnabled = uiState.autoPlayOnStart,
            onToggle = onSetAutoPlayOnStart,
            icon = Icons.Default.PlayArrow,
            shape = topShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_stop_on_audio_noisy),
            subtitle = stringResource(R.string.settings_stop_on_audio_noisy_desc),
            isEnabled = uiState.stopOnAudioBecomingNoisy,
            onToggle = onSetStopOnAudioBecomingNoisy,
            icon = Icons.Default.Headphones,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        ToggleItem(
            title = stringResource(R.string.settings_show_cover_art),
            subtitle = stringResource(R.string.settings_show_cover_art_desc),
            isEnabled = uiState.showCoverArtInNotification,
            onToggle = onSetShowCoverArtInNotification,
            icon = Icons.Default.Image,
            shape = middleShape
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        val retryOptions = listOf(
            60_000L to stringResource(R.string.settings_retry_1_min),
            300_000L to stringResource(R.string.settings_retry_5_min),
            900_000L to stringResource(R.string.settings_retry_15_min),
            1_800_000L to stringResource(R.string.settings_retry_30_min),
            -1L to stringResource(R.string.settings_retry_indefinitely)
        )
        val currentRetryOption = retryOptions.find { it.first == uiState.maxRetryDuration }?.second ?: stringResource(R.string.settings_retry_5_min)

        ExpandableItem(
            title = stringResource(R.string.settings_max_retry_duration),
            subtitle = currentRetryOption,
            isExpanded = expandedItem == "RetryDuration",
            onToggle = { onExpandedItemChange(if (expandedItem == "RetryDuration") null else "RetryDuration") },
            icon = Icons.Default.Update,
            shape = middleShape
        ) {
            retryOptions.forEach { (duration, label) ->
                OptionItem(
                    label = label,
                    isSelected = uiState.maxRetryDuration == duration,
                    onClick = { onSetMaxRetryDuration(duration) }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        Item(
            title = stringResource(R.string.settings_track_history_limit),
            subtitle = androidx.compose.ui.res.pluralStringResource(R.plurals.tracks_count, uiState.trackHistoryLimit, uiState.trackHistoryLimit),
            onClick = onToggleHistoryLimitDialog,
            icon = Icons.Default.History,
            shape = bottomShape
        )

        if (showHistoryLimitDialog) {
            var inputLimit by remember { mutableStateOf(uiState.trackHistoryLimit.toString()) }
            AlertDialog(
                onDismissRequest = onToggleHistoryLimitDialog,
                title = { Text(stringResource(R.string.settings_track_history_limit)) },
                text = {
                    OutlinedTextField(
                        value = inputLimit,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                inputLimit = newValue
                            }
                        },
                        label = { Text(stringResource(R.string.settings_number_of_tracks)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val limitInt = inputLimit.toIntOrNull() ?: 50
                            onSetHistoryLimit(limitInt.coerceIn(1, 500))
                            onToggleHistoryLimitDialog()
                        }
                    ) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onToggleHistoryLimitDialog) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun AboutSection(
    onAboutClick: () -> Unit,
    onCheckUpdatesClick: () -> Unit,
    topShape: RoundedCornerShape,
    middleShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    val context = LocalContext.current

    Section(title = stringResource(R.string.about_title)) {
        Item(
            title = stringResource(R.string.settings_rate_review),
            onClick = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    // Handle error silently
                }
            },
            icon = Icons.Default.StarRate,
            shape = topShape
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
        Item(
            title = stringResource(R.string.about_us),
            onClick = onAboutClick,
            icon = Icons.Default.Info,
            shape = middleShape
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))
        Item(
            title = stringResource(R.string.settings_check_updates),
            onClick = onCheckUpdatesClick,
            icon = Icons.Default.Update,
            shape = bottomShape
        )
    }
}

@Composable
private fun BackupSection(
    uiState: AppPreferences,
    expandedItem: String?,
    onExpandedItemChange: (String?) -> Unit,
    onSetConflictStrategy: (ConflictStrategy) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    topShape: RoundedCornerShape,
    bottomShape: RoundedCornerShape
) {
    val conflictOptions = listOf(
        ConflictStrategy.SKIP to stringResource(R.string.settings_conflict_skip),
        ConflictStrategy.OVERWRITE to stringResource(R.string.settings_conflict_overwrite),
        ConflictStrategy.KEEP_NEWER to stringResource(R.string.settings_conflict_keep_newer)
    )
    val currentLabel = conflictOptions.find { it.first == uiState.conflictStrategy }?.second
        ?: stringResource(R.string.settings_conflict_skip)

    Section(title = stringResource(R.string.settings_backup_section)) {
        ExpandableItem(
            title = stringResource(R.string.settings_conflict_title),
            subtitle = currentLabel,
            isExpanded = expandedItem == "Conflict",
            onToggle = { onExpandedItemChange(if (expandedItem == "Conflict") null else "Conflict") },
            icon = Icons.AutoMirrored.Filled.CallMerge,
            shape = topShape
        ) {
            conflictOptions.forEach { (strategy, label) ->
                OptionItem(
                    label = label,
                    isSelected = uiState.conflictStrategy == strategy,
                    onClick = { onSetConflictStrategy(strategy) },
                    subtitle = when (strategy) {
                        ConflictStrategy.SKIP -> stringResource(R.string.settings_conflict_skip_desc)
                        ConflictStrategy.OVERWRITE -> stringResource(R.string.settings_conflict_overwrite_desc)
                        ConflictStrategy.KEEP_NEWER -> stringResource(R.string.settings_conflict_keep_newer_desc)
                    }
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        Item(
            title = stringResource(R.string.settings_export_title),
            subtitle = stringResource(R.string.settings_export_subtitle),
            onClick = onExport,
            icon = Icons.Default.FileUpload,
            shape = RoundedCornerShape(0.dp)
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(2.dp))

        Item(
            title = stringResource(R.string.settings_import_title),
            subtitle = stringResource(R.string.settings_import_subtitle),
            onClick = onImport,
            icon = Icons.Default.FileDownload,
            shape = bottomShape
        )
    }
}

@Composable
private fun rememberAvailableLanguages(): List<Pair<String, String>> {
    val context = LocalContext.current
    val systemDefaultStr = stringResource(R.string.settings_system_default)
    return remember(systemDefaultStr) {
        buildList {
            add("System" to systemDefaultStr)
            try {
                val parser = context.resources.getXml(R.xml.locales_config)
                var event = parser.next()
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "locale") {
                        val tag = parser.getAttributeValue(
                            "http://schemas.android.com/apk/res/android", "name"
                        )
                        if (!tag.isNullOrBlank()) {
                            val locale = Locale.forLanguageTag(tag)
                            add(tag to locale.getDisplayName(locale).replaceFirstChar { it.uppercaseChar() })
                        }
                    }
                    event = parser.next()
                }
                parser.close()
            } catch (_: Exception) { }
        }
    }
}

@Composable
private fun String.getLanguageDisplayName(availableLanguages: List<Pair<String, String>>): String {
    return availableLanguages.find { it.first == this }?.second ?: stringResource(R.string.settings_system_default)
}

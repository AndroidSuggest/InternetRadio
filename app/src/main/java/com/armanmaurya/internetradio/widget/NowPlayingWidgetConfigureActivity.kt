package com.armanmaurya.internetradio.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.AppPreferences
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.domain.repository.RecentRepository
import com.armanmaurya.internetradio.domain.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.shared.theme.InternetRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class NowPlayingWidgetConfigureActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var recentRepository: RecentRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set result to CANCELED so if the user backs out, the widget is not placed
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            val appPreferences by settingsRepository.appPreferencesFlow.collectAsStateWithLifecycle(
                initialValue = AppPreferences()
            )
            val recentStations by recentRepository.getAllRecent().collectAsStateWithLifecycle(
                initialValue = emptyList<RadioStation>()
            )
            val latest = latestWidgetPayload
            val lastStation = recentStations.firstOrNull()

            val previewTitle = latest?.title?.takeIf { it.isNotBlank() }
                ?: lastStation?.name
                ?: stringResource(R.string.widget_preview_title)

            val previewArtist = latest?.artist?.takeIf { it.isNotBlank() }
                ?: (if (lastStation != null) "" else stringResource(R.string.widget_preview_artist))

            val previewArtworkUrl = latest?.artworkUrl?.takeIf { it.isNotBlank() }
                ?: lastStation?.favicon

            val previewStationThumbUrl = latest?.stationThumbnailUrl?.takeIf { it.isNotBlank() }
                ?: if (latest?.isCoverArtFetched == true) lastStation?.favicon else null

            val isCoverArtFetched = latest?.isCoverArtFetched ?: false
            val isPlaying = latest?.isPlaying ?: false

            val previewBgColor = latest?.bgColor?.let { Color(it) }
            val previewTitleColor = latest?.titleColor?.let { Color(it) }
            val previewArtistColor = latest?.artistColor?.let { Color(it) }

            InternetRadioTheme(appPreferences = appPreferences) {
                WidgetConfigureScreen(
                    appWidgetId = appWidgetId,
                    initialAlpha = appPreferences.widgetBackgroundAlpha,
                    previewTitle = previewTitle,
                    previewArtist = previewArtist,
                    previewArtworkUrl = previewArtworkUrl,
                    previewStationThumbUrl = previewStationThumbUrl,
                    isCoverArtFetched = isCoverArtFetched,
                    isPlaying = isPlaying,
                    extractedBgColor = previewBgColor,
                    extractedTitleColor = previewTitleColor,
                    extractedArtistColor = previewArtistColor,
                    onApply = { chosenAlpha ->
                        // Launch on main scope to save and complete
                        CoroutineScope(Dispatchers.Main).launch {
                            saveWidgetConfiguration(chosenAlpha)
                        }
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }

    private suspend fun saveWidgetConfiguration(alpha: Float) {
        val context = applicationContext

        // Update specific widget instance if appWidgetId is valid
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceId = manager.getGlanceIdBy(appWidgetId)
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[WidgetStateKeys.BG_ALPHA] = alpha
                    }
                }
                NowPlayingWidget().update(context, glanceId)
            } catch (e: Exception) {
                // Ignore if glanceId cannot be resolved
            }
        }

        // Also update global setting and sync all widgets
        settingsRepository.setWidgetBackgroundAlpha(alpha)
        updateWidgetAlpha(context, alpha)

        // Return SUCCESS to launcher
        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigureScreen(
    appWidgetId: Int,
    initialAlpha: Float,
    previewTitle: String = stringResource(R.string.widget_preview_title),
    previewArtist: String = stringResource(R.string.widget_preview_artist),
    previewArtworkUrl: String? = null,
    previewStationThumbUrl: String? = null,
    isCoverArtFetched: Boolean = false,
    isPlaying: Boolean = false,
    extractedBgColor: Color? = null,
    extractedTitleColor: Color? = null,
    extractedArtistColor: Color? = null,
    onApply: (Float) -> Unit,
    onCancel: () -> Unit
) {
    var alpha by remember { mutableStateOf(initialAlpha) }
    val percentageInt = (alpha * 100f).roundToInt()

    val presets = listOf(
        Pair(1.0f, stringResource(R.string.settings_widget_opacity_opaque)),
        Pair(0.75f, "75%"),
        Pair(0.50f, "50%"),
        Pair(0.25f, "25%"),
        Pair(0.0f, stringResource(R.string.settings_widget_opacity_transparent))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_configure_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(android.R.string.cancel)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .navigationBarsPadding()
                ) {
                    Button(
                        onClick = { onApply(alpha) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.widget_configure_apply),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section: Live Preview
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.widget_configure_preview),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                // Simulated home screen background wallpaper
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF1A237E),
                                    Color(0xFF311B92),
                                    Color(0xFF006064),
                                    Color(0xFF263238)
                                )
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Widget Mockup Card matching PlayerContent.kt 1-cell layout
                    val isPureBlack = MaterialTheme.colorScheme.surface == Color.Black
                    val baseBgColor = extractedBgColor ?: if (isPureBlack) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val widgetBgColor = baseBgColor.copy(alpha = alpha)

                    val titleColor = extractedTitleColor ?: MaterialTheme.colorScheme.onSurface
                    val artistColor = extractedArtistColor ?: MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(86.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(widgetBgColor)
                            .then(
                                if (isPureBlack && extractedBgColor == null) Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(8.dp)
                                ) else Modifier
                            )
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork on Left (8.dp corner radius)
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!previewArtworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = previewArtworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Floating station thumbnail badge
                            if (isCoverArtFetched && !previewStationThumbUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(3.dp)
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = previewStationThumbUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }

                        // Right Column: TrackInfo on top, WidgetControls below
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.Start
                        ) {
                            // NowPlayingTrackInfo
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = previewTitle,
                                    style = TextStyle(
                                        color = titleColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (previewArtist.isNotBlank()) {
                                    Text(
                                        text = previewArtist,
                                        style = TextStyle(
                                            color = artistColor,
                                            fontSize = 12.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // WidgetControls
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_widget_prev),
                                    contentDescription = "Previous",
                                    tint = titleColor,
                                    modifier = Modifier.size(36.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Icon(
                                    painter = painterResource(
                                        if (isPlaying) R.drawable.ic_widget_pause
                                        else R.drawable.ic_widget_play
                                    ),
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = titleColor,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(horizontal = 4.dp)
                                )

                                Spacer(modifier = Modifier.width(16.dp))

                                Icon(
                                    painter = painterResource(R.drawable.ic_widget_next),
                                    contentDescription = "Next",
                                    tint = titleColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Section: Controls
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title and Percentage
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_widget_transparency_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "$percentageInt%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Slider
                    Slider(
                        value = alpha,
                        onValueChange = { alpha = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Presets
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.widget_configure_presets),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OptInFlowRow(presets = presets, selectedAlpha = alpha) { chosen ->
                            alpha = chosen
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptInFlowRow(
    presets: List<Pair<Float, String>>,
    selectedAlpha: Float,
    onSelect: (Float) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        presets.forEach { (presetAlpha, label) ->
            val isSelected = (selectedAlpha * 100f).roundToInt() == (presetAlpha * 100f).roundToInt()
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(presetAlpha) },
                label = { Text(label) },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

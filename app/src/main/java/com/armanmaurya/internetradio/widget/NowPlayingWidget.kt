package com.armanmaurya.internetradio.widget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.widget.components.PlayerContent
import com.armanmaurya.internetradio.widget.state.NowPlayingWidgetState

object WidgetStateKeys {
    val TITLE                 = stringPreferencesKey("title")
    val ARTIST                = stringPreferencesKey("artist")
    val ARTWORK_URL           = stringPreferencesKey("artwork_url")
    val IS_PLAYING            = booleanPreferencesKey("is_playing")
    val HAS_NEXT              = booleanPreferencesKey("has_next")
    val HAS_PREV              = booleanPreferencesKey("has_prev")
    val STATION_NAME          = stringPreferencesKey("station_name")
    val STATION_THUMBNAIL_URL = stringPreferencesKey("station_thumbnail_url")
    val IS_COVER_ART_FETCHED  = booleanPreferencesKey("is_cover_art_fetched")
    val BG_COLOR              = intPreferencesKey("bg_color")
    val TITLE_COLOR           = intPreferencesKey("title_color")
    val ARTIST_COLOR          = intPreferencesKey("artist_color")
    val BG_ALPHA              = androidx.datastore.preferences.core.floatPreferencesKey("bg_alpha")
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun recentRepository(): com.armanmaurya.internetradio.domain.repository.RecentRepository
    fun settingsRepository(): com.armanmaurya.internetradio.domain.repository.SettingsRepository
}

class NowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val savedAlpha = try {
            entryPoint.settingsRepository().appPreferencesFlow.first().widgetBackgroundAlpha
        } catch (e: Exception) {
            1.0f
        }
        val initialRecent = try {
            entryPoint.recentRepository().getAllRecent().first().firstOrNull()
        } catch (e: Exception) {
            null
        }

        provideContent {
            // Read state reactively inside provideContent
            val prefs = currentState<Preferences>()
            val widgetAlpha = prefs[WidgetStateKeys.BG_ALPHA] ?: latestWidgetPayload?.bgAlpha ?: savedAlpha
            val isServiceRunning = com.armanmaurya.internetradio.player.PlaybackService.isRunning
            val latest = if (isServiceRunning) latestWidgetPayload else null

            val savedTitle = prefs[WidgetStateKeys.TITLE] ?: latest?.title
            val nothingPlaying = context.getString(R.string.widget_nothing_playing)
            
            var lastStation by remember { mutableStateOf(initialRecent) }
            var cleanedStationUuid by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                entryPoint.recentRepository().getAllRecent().collect { list ->
                    lastStation = list.firstOrNull()
                }
            }

            val isPlaying = prefs[WidgetStateKeys.IS_PLAYING] ?: latest?.isPlaying ?: false
            val isCoverArtFetched = if (isPlaying) (prefs[WidgetStateKeys.IS_COVER_ART_FETCHED] ?: latest?.isCoverArtFetched ?: false) else false

            LaunchedEffect(savedTitle, isPlaying, lastStation) {
                if (isPlaying) {
                    if (prefs[WidgetStateKeys.TITLE] == null && isServiceRunning) {
                        com.armanmaurya.internetradio.player.PlaybackService.requestWidgetUpdate()
                    }
                } else {
                    val currentUuid = lastStation?.stationUuid ?: ""
                    val expectedTitle = lastStation?.name ?: nothingPlaying
                    val expectedStationName = lastStation?.name ?: ""
                    val isPrefsStale = prefs[WidgetStateKeys.TITLE] != expectedTitle ||
                        prefs[WidgetStateKeys.STATION_NAME] != expectedStationName ||
                        prefs[WidgetStateKeys.IS_PLAYING] == true ||
                        !prefs[WidgetStateKeys.ARTIST].isNullOrBlank() ||
                        prefs[WidgetStateKeys.IS_COVER_ART_FETCHED] == true

                    if (cleanedStationUuid != currentUuid && isPrefsStale) {
                        cleanedStationUuid = currentUuid
                        cleanStaleWidgetState(context.applicationContext, lastStation?.name, lastStation?.favicon, id)
                    }
                }
            }
            
            val stationName = if (isPlaying) {
                prefs[WidgetStateKeys.STATION_NAME]?.takeIf { it.isNotBlank() } ?: latest?.stationName ?: lastStation?.name
            } else {
                lastStation?.name
            }
            
            val title = if (isPlaying) {
                savedTitle?.takeIf { it.isNotBlank() && it != "Nothing playing" && it != nothingPlaying } 
                    ?: stationName 
                    ?: nothingPlaying
            } else {
                lastStation?.name ?: nothingPlaying
            }
                
            val artist = if (isPlaying) {
                prefs[WidgetStateKeys.ARTIST] ?: latest?.artist ?: ""
            } else {
                ""
            }

            val artworkUrl = if (isPlaying) {
                (prefs[WidgetStateKeys.ARTWORK_URL] ?: latest?.artworkUrl)?.takeIf { it.isNotBlank() } ?: lastStation?.favicon
            } else {
                lastStation?.favicon
            }
                
            val stationThumbnailUrl = if (isCoverArtFetched) {
                (prefs[WidgetStateKeys.STATION_THUMBNAIL_URL] ?: latest?.stationThumbnailUrl)?.takeIf { it.isNotBlank() } ?: lastStation?.favicon
            } else null
                
            val hasNext = if (isPlaying) (prefs[WidgetStateKeys.HAS_NEXT] ?: latest?.hasNext ?: false) else false
            val hasPrev = if (isPlaying) (prefs[WidgetStateKeys.HAS_PREV] ?: latest?.hasPrev ?: false) else false

            // Pre-computed atomic palette colors (Spotify pattern)
            val precomputedBgColorInt = prefs[WidgetStateKeys.BG_COLOR] ?: latest?.bgColor
            val precomputedTitleColorInt = prefs[WidgetStateKeys.TITLE_COLOR] ?: latest?.titleColor
            val precomputedArtistColorInt = prefs[WidgetStateKeys.ARTIST_COLOR] ?: latest?.artistColor

            var artwork by remember(artworkUrl) { mutableStateOf<ImageProvider?>(null) }
            var stationThumbnail by remember(stationThumbnailUrl) { mutableStateOf<ImageProvider?>(null) }
            
            // Dynamic palette fallback if prefs didn't have precomputed colors (e.g. legacy/initial load)
            var dynamicBgColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            var dynamicTitleColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            var dynamicArtistColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }

            LaunchedEffect(artworkUrl) {
                if (artworkUrl != null) {
                    val bmp = resolveArtwork(context, artworkUrl)
                    if (bmp != null) {
                        artwork = ImageProvider(bmp)
                        if (precomputedBgColorInt == null) {
                            val palette = try {
                                androidx.palette.graphics.Palette.from(bmp).generate()
                            } catch (e: Exception) {
                                null
                            }
                            val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch
                            if (swatch != null && swatch.rgb != android.graphics.Color.TRANSPARENT) {
                                dynamicBgColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(swatch.rgb))
                                dynamicTitleColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(swatch.titleTextColor))
                                dynamicArtistColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(swatch.bodyTextColor))
                            }
                        }
                    } else {
                        artwork = null
                    }
                } else {
                    artwork = null
                }
            }

            LaunchedEffect(stationThumbnailUrl) {
                if (stationThumbnailUrl != null) {
                    val bmp = resolveArtwork(context, stationThumbnailUrl, maxDimension = 96)
                    stationThumbnail = if (bmp != null) ImageProvider(bmp) else null
                } else {
                    stationThumbnail = null
                }
            }

            val rawBgColor = precomputedBgColorInt?.let {
                androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(it))
            } ?: dynamicBgColor ?: GlanceTheme.colors.widgetBackground

            val resolvedColor = rawBgColor.getColor(context)
            val bgColor = androidx.glance.unit.ColorProvider(resolvedColor.copy(alpha = widgetAlpha))

            val titleColor = precomputedTitleColorInt?.let {
                androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(it))
            } ?: dynamicTitleColor ?: GlanceTheme.colors.onSurface

            val artistColor = precomputedArtistColorInt?.let {
                androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(it))
            } ?: dynamicArtistColor ?: GlanceTheme.colors.onSurfaceVariant

            val state = NowPlayingWidgetState(
                title               = title,
                artist              = artist,
                stationName         = stationName,
                artworkUrl          = artworkUrl,
                artwork             = artwork,
                stationThumbnailUrl = stationThumbnailUrl,
                stationThumbnail    = stationThumbnail,
                isCoverArtFetched   = isCoverArtFetched,
                isPlaying           = isPlaying,
                hasNext             = hasNext,
                hasPrev             = hasPrev,
                backgroundColor     = bgColor,
                titleColor          = titleColor,
                artistColor         = artistColor,
            )

            GlanceTheme {
                PlayerContent(
                    state = state,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .appWidgetBackground()
                        .background(bgColor)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

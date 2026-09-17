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
    val DAY_BG_COLOR          = intPreferencesKey("day_bg_color")
    val DAY_TITLE_COLOR       = intPreferencesKey("day_title_color")
    val DAY_ARTIST_COLOR      = intPreferencesKey("day_artist_color")
    val SEED_COLOR            = intPreferencesKey("seed_color")
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
            val precomputedDayBgColorInt = prefs[WidgetStateKeys.DAY_BG_COLOR] ?: latest?.dayBgColor
            val precomputedDayTitleColorInt = prefs[WidgetStateKeys.DAY_TITLE_COLOR] ?: latest?.dayTitleColor
            val precomputedDayArtistColorInt = prefs[WidgetStateKeys.DAY_ARTIST_COLOR] ?: latest?.dayArtistColor

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
                            val extracted = extractPaletteFromBitmap(bmp)
                            if (extracted != null) {
                                dynamicBgColor = androidx.glance.color.ColorProvider(
                                    day = androidx.compose.ui.graphics.Color(extracted.dayBackgroundColor),
                                    night = androidx.compose.ui.graphics.Color(extracted.backgroundColor)
                                )
                                dynamicTitleColor = androidx.glance.color.ColorProvider(
                                    day = androidx.compose.ui.graphics.Color(extracted.dayTitleTextColor),
                                    night = androidx.compose.ui.graphics.Color(extracted.titleTextColor)
                                )
                                dynamicArtistColor = androidx.glance.color.ColorProvider(
                                    day = androidx.compose.ui.graphics.Color(extracted.dayArtistTextColor),
                                    night = androidx.compose.ui.graphics.Color(extracted.artistTextColor)
                                )
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

            val bgColor = if (precomputedBgColorInt != null && precomputedDayBgColorInt != null) {
                androidx.glance.color.ColorProvider(
                    day = androidx.compose.ui.graphics.Color(precomputedDayBgColorInt).copy(alpha = widgetAlpha),
                    night = androidx.compose.ui.graphics.Color(precomputedBgColorInt).copy(alpha = widgetAlpha)
                )
            } else if (precomputedBgColorInt != null) {
                androidx.glance.unit.ColorProvider(
                    androidx.compose.ui.graphics.Color(precomputedBgColorInt).copy(alpha = widgetAlpha)
                )
            } else {
                val base = dynamicBgColor ?: GlanceTheme.colors.widgetBackground
                val resolved = base.getColor(context)
                androidx.glance.unit.ColorProvider(resolved.copy(alpha = widgetAlpha))
            }

            val titleColor = if (precomputedTitleColorInt != null && precomputedDayTitleColorInt != null) {
                androidx.glance.color.ColorProvider(
                    day = androidx.compose.ui.graphics.Color(precomputedDayTitleColorInt),
                    night = androidx.compose.ui.graphics.Color(precomputedTitleColorInt)
                )
            } else if (precomputedTitleColorInt != null) {
                androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(precomputedTitleColorInt))
            } else {
                dynamicTitleColor ?: GlanceTheme.colors.onSurface
            }

            val artistColor = if (precomputedArtistColorInt != null && precomputedDayArtistColorInt != null) {
                androidx.glance.color.ColorProvider(
                    day = androidx.compose.ui.graphics.Color(precomputedDayArtistColorInt),
                    night = androidx.compose.ui.graphics.Color(precomputedArtistColorInt)
                )
            } else if (precomputedArtistColorInt != null) {
                androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(precomputedArtistColorInt))
            } else {
                dynamicArtistColor ?: GlanceTheme.colors.onSurfaceVariant
            }

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

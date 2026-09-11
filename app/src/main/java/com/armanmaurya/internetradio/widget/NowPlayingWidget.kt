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
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun recentRepository(): com.armanmaurya.internetradio.domain.repository.RecentRepository
}

class NowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Read state reactively inside provideContent
            val prefs = currentState<Preferences>()
            val isServiceRunning = com.armanmaurya.internetradio.player.PlaybackService.isRunning
            val latest = if (isServiceRunning) latestWidgetPayload else null

            val savedTitle = prefs[WidgetStateKeys.TITLE] ?: latest?.title
            val nothingPlaying = context.getString(R.string.widget_nothing_playing)
            
            var lastStation by remember { mutableStateOf<com.armanmaurya.internetradio.data.model.RadioStation?>(null) }
            var hasCleanedStaleState by remember { mutableStateOf(false) }

            LaunchedEffect(savedTitle, isServiceRunning) {
                if (isServiceRunning) {
                    if (prefs[WidgetStateKeys.TITLE] == null) {
                        com.armanmaurya.internetradio.player.PlaybackService.requestWidgetUpdate()
                    }
                } else {
                    val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
                    val recent = entryPoint.recentRepository().getAllRecent().first().firstOrNull()
                    lastStation = recent
                    if (!hasCleanedStaleState && (
                        prefs[WidgetStateKeys.IS_PLAYING] == true || 
                        !prefs[WidgetStateKeys.ARTIST].isNullOrBlank() || 
                        prefs[WidgetStateKeys.IS_COVER_ART_FETCHED] == true ||
                        (savedTitle != null && savedTitle != recent?.name && savedTitle != nothingPlaying)
                    )) {
                        hasCleanedStaleState = true
                        cleanStaleWidgetState(context.applicationContext, recent?.name, recent?.favicon, id)
                    }
                }
            }
            
            val isPlaying = if (isServiceRunning) (prefs[WidgetStateKeys.IS_PLAYING] ?: latest?.isPlaying ?: false) else false
            val isCoverArtFetched = if (isServiceRunning) (prefs[WidgetStateKeys.IS_COVER_ART_FETCHED] ?: latest?.isCoverArtFetched ?: false) else false
            
            val savedStationName = prefs[WidgetStateKeys.STATION_NAME]?.takeIf { it.isNotBlank() } ?: latest?.stationName
            val stationName = savedStationName ?: lastStation?.name
            
            val title = if (isServiceRunning) {
                savedTitle?.takeIf { it.isNotBlank() && it != "Nothing playing" && it != nothingPlaying } 
                    ?: stationName 
                    ?: nothingPlaying
            } else {
                stationName ?: nothingPlaying
            }
                
            val artist = if (isServiceRunning) {
                prefs[WidgetStateKeys.ARTIST] ?: latest?.artist ?: ""
            } else {
                ""
            }

            val artworkUrl = if (isServiceRunning) {
                (prefs[WidgetStateKeys.ARTWORK_URL] ?: latest?.artworkUrl)?.takeIf { it.isNotBlank() } ?: lastStation?.favicon
            } else {
                lastStation?.favicon 
                    ?: if (prefs[WidgetStateKeys.IS_COVER_ART_FETCHED] == true) prefs[WidgetStateKeys.STATION_THUMBNAIL_URL]?.takeIf { it.isNotBlank() }
                       else prefs[WidgetStateKeys.ARTWORK_URL]?.takeIf { it.isNotBlank() }
            }
                
            val stationThumbnailUrl = if (isCoverArtFetched) {
                (prefs[WidgetStateKeys.STATION_THUMBNAIL_URL] ?: latest?.stationThumbnailUrl)?.takeIf { it.isNotBlank() } ?: lastStation?.favicon
            } else null
                
            val hasNext = if (isServiceRunning) (prefs[WidgetStateKeys.HAS_NEXT] ?: latest?.hasNext ?: false) else false
            val hasPrev = if (isServiceRunning) (prefs[WidgetStateKeys.HAS_PREV] ?: latest?.hasPrev ?: false) else false

            var artwork by remember(artworkUrl) { mutableStateOf<ImageProvider?>(null) }
            var stationThumbnail by remember(stationThumbnailUrl) { mutableStateOf<ImageProvider?>(null) }
            var bgColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            var titleColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            var artistColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            
            LaunchedEffect(artworkUrl) {
                if (artworkUrl != null) {
                    val bmp = resolveArtwork(context, artworkUrl)
                    if (bmp != null) {
                        artwork = ImageProvider(bmp)
                        
                        val palette = try {
                            androidx.palette.graphics.Palette.from(bmp).generate()
                        } catch (e: Exception) {
                            null
                        }
                        val swatch = palette?.vibrantSwatch ?: palette?.dominantSwatch
                        
                        if (swatch != null && swatch.rgb != android.graphics.Color.TRANSPARENT) {
                            bgColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(swatch.rgb))
                            titleColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(swatch.titleTextColor))
                            artistColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(swatch.bodyTextColor))
                        } else {
                            bgColor = null
                            titleColor = null
                            artistColor = null
                        }
                    } else {
                        artwork = null
                        bgColor = null
                        titleColor = null
                        artistColor = null
                    }
                } else {
                    artwork = null
                    bgColor = null
                    titleColor = null
                    artistColor = null
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
                        .background(bgColor ?: GlanceTheme.colors.primary)
                        .cornerRadius(8.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        }
    }
}

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
import androidx.glance.action.clickable
import com.armanmaurya.internetradio.widget.components.PlayerContent
import com.armanmaurya.internetradio.widget.state.NowPlayingWidgetState

object WidgetStateKeys {
    val TITLE       = stringPreferencesKey("title")
    val ARTIST      = stringPreferencesKey("artist")
    val ARTWORK_URL = stringPreferencesKey("artwork_url")
    val IS_PLAYING  = booleanPreferencesKey("is_playing")
    val HAS_NEXT    = booleanPreferencesKey("has_next")
    val HAS_PREV    = booleanPreferencesKey("has_prev")
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun recentRepository(): com.armanmaurya.internetradio.data.repository.RecentRepository
}

class NowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            // Read state reactively inside provideContent
            val prefs = currentState<Preferences>()
            val savedTitle = prefs[WidgetStateKeys.TITLE]
            
            // Fetch DB reactively ONLY if the saved file is empty/Nothing playing
            var lastStation by remember { mutableStateOf<com.armanmaurya.internetradio.data.model.RadioStation?>(null) }
            
            LaunchedEffect(savedTitle) {
                if (savedTitle == null || savedTitle == "Nothing playing") {
                    val entryPoint = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
                    lastStation = entryPoint.recentRepository().getAllRecent().first().firstOrNull()
                }
            }
            
            val isServiceRunning = com.armanmaurya.internetradio.player.PlaybackService.isRunning
            val isPlaying = if (isServiceRunning) prefs[WidgetStateKeys.IS_PLAYING] ?: false else false
            
            // Trust the saved preferences first. If missing, use the fresh DB query.
            val title = savedTitle?.takeIf { it != "Nothing playing" } 
                ?: lastStation?.name 
                ?: "Nothing playing"
                
            val artworkUrl = prefs[WidgetStateKeys.ARTWORK_URL]?.takeIf { it.isNotBlank() } 
                ?: lastStation?.favicon
                
            val artist     = prefs[WidgetStateKeys.ARTIST] ?: ""
            val hasNext    = prefs[WidgetStateKeys.HAS_NEXT] ?: false
            val hasPrev    = prefs[WidgetStateKeys.HAS_PREV] ?: false

            var artwork by remember(artworkUrl) { mutableStateOf<ImageProvider?>(null) }
            var bgColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            var titleColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            var artistColor by remember(artworkUrl) { mutableStateOf<androidx.glance.unit.ColorProvider?>(null) }
            
            LaunchedEffect(artworkUrl) {
                if (artworkUrl != null) {
                    val bmp = resolveArtwork(context, artworkUrl)
                    if (bmp != null) {
                        artwork = ImageProvider(bmp)
                        
                        val palette = androidx.palette.graphics.Palette.from(bmp).generate()
                        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch
                        
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

            val state = NowPlayingWidgetState(
                title           = title,
                artist          = artist,
                artworkUrl      = artworkUrl,
                artwork         = artwork,
                isPlaying       = isPlaying,
                hasNext         = hasNext,
                hasPrev         = hasPrev,
                backgroundColor = bgColor,
                titleColor      = titleColor,
                artistColor     = artistColor,
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

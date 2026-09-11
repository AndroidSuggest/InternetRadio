package com.armanmaurya.internetradio.widget.components

import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.armanmaurya.internetradio.ui.mobile.MobileActivity
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.widget.WidgetControlReceiver
import com.armanmaurya.internetradio.widget.state.NowPlayingWidgetState

@Composable
fun PlayerContent(state: NowPlayingWidgetState, modifier: GlanceModifier) {
    val size = LocalSize.current
    val artDimension = min(size.width - 16.dp, size.height - 16.dp)
    val context = LocalContext.current

    val showExtraControls = size.width >= 300.dp

    val receiverComponent = ComponentName(context, WidgetControlReceiver::class.java)

    val openAppAction = actionStartActivity(
        Intent(context, MobileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    )

    val openPlayerAction = actionStartActivity(
        Intent(context, MobileActivity::class.java).apply {
            action = "com.armanmaurya.internetradio.ACTION_OPEN_PLAYER"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player_sheet", true)
        }
    )

    val playPauseAction = actionSendBroadcast(
        Intent("com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE").setComponent(receiverComponent)
    )
    val nextAction = actionSendBroadcast(
        Intent("com.armanmaurya.internetradio.ACTION_WIDGET_NEXT").setComponent(receiverComponent)
    )
    val prevAction = actionSendBroadcast(
        Intent("com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS").setComponent(receiverComponent)
    )

    val iconFilter = androidx.glance.ColorFilter.tint(
        state.titleColor ?: GlanceTheme.colors.onPrimary
    )
    val isVerySmallHeight = size.height < 75.dp
    val isTallHeight = size.height >= 110.dp
    val showStationBottom = state.artist.isNotBlank() && 
        !state.stationName.isNullOrBlank() && 
        state.stationName != state.title
    val showStationFloating = state.isCoverArtFetched && state.stationThumbnail != null
    val twoCellArtDimension = if (showStationBottom) {
        androidx.compose.ui.unit.max(40.dp, size.height - 90.dp)
    } else {
        androidx.compose.ui.unit.max(48.dp, size.height - 68.dp)
    }

    val rootModifier = modifier.clickable(openAppAction)

    if (isVerySmallHeight) {
        // Realme Launcher 1-cell (Very short height): 3-column layout
        val thumbDimension = (artDimension * 0.35f).coerceIn(14.dp, 20.dp)
        Row(
            modifier = rootModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight()
                    .clickable(openPlayerAction),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtWork(
                    art = state.artwork,
                    stationArt = state.stationThumbnail,
                    showStationFloating = showStationFloating,
                    floatingThumbDimension = thumbDimension,
                    modifier = GlanceModifier.size(artDimension)
                )
                NowPlayingTrackInfo(
                    title = state.title,
                    artist = state.artist,
                    titleColor = state.titleColor,
                    artistColor = state.artistColor,
                    modifier = GlanceModifier.defaultWeight()
                )
            }
            WidgetControls(
                state = state,
                showExtraControls = showExtraControls,
                iconFilter = iconFilter,
                playPauseAction = playPauseAction,
                prevAction = prevAction,
                nextAction = nextAction,
                modifier = GlanceModifier.padding(end = 4.dp)
            )
        }
    } else if (isTallHeight) {
        // 2-cell height layout: TrackInfo on top, CoverArt and Controls below, Station on bottom when Track Info is present
        val thumbDimension = (twoCellArtDimension * 0.32f).coerceIn(16.dp, 24.dp)
        Column(
            modifier = rootModifier,
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.Start
        ) {
            NowPlayingTrackInfo(
                title = state.title,
                artist = state.artist,
                titleColor = state.titleColor,
                artistColor = state.artistColor,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(bottom = if (showStationBottom) 4.dp else 8.dp)
                    .clickable(openPlayerAction)
            )
            
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArtWork(
                    art = state.artwork,
                    stationArt = state.stationThumbnail,
                    showStationFloating = showStationFloating,
                    floatingThumbDimension = thumbDimension,
                    modifier = GlanceModifier.size(twoCellArtDimension).clickable(openPlayerAction)
                )
                WidgetControls(
                    state = state,
                    showExtraControls = showExtraControls,
                    iconFilter = iconFilter,
                    playPauseAction = playPauseAction,
                    prevAction = prevAction,
                    nextAction = nextAction,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
            }

            if (showStationBottom) {
                Text(
                    text = state.stationName,
                    style = TextStyle(
                        color = state.artistColor ?: GlanceTheme.colors.onPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clickable(openPlayerAction)
                )
            }
        }
    } else {
        // Pixel Launcher 1-cell (Medium height): CoverArt on left, TrackInfo & Controls stacked on right
        val thumbDimension = (artDimension * 0.32f).coerceIn(16.dp, 24.dp)
        Row(
            modifier = rootModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtWork(
                art = state.artwork,
                stationArt = state.stationThumbnail,
                showStationFloating = showStationFloating,
                floatingThumbDimension = thumbDimension,
                modifier = GlanceModifier.size(artDimension).clickable(openPlayerAction)
            )
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NowPlayingTrackInfo(
                    title = state.title,
                    artist = state.artist,
                    titleColor = state.titleColor,
                    artistColor = state.artistColor,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight().clickable(openPlayerAction)
                )
                WidgetControls(
                    state = state,
                    showExtraControls = showExtraControls,
                    iconFilter = iconFilter,
                    playPauseAction = playPauseAction,
                    prevAction = prevAction,
                    nextAction = nextAction,
                    modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun WidgetControls(
    state: NowPlayingWidgetState,
    showExtraControls: Boolean,
    iconFilter: androidx.glance.ColorFilter,
    playPauseAction: androidx.glance.action.Action,
    prevAction: androidx.glance.action.Action,
    nextAction: androidx.glance.action.Action,
    modifier: GlanceModifier = GlanceModifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showExtraControls && state.hasPrev) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_prev),
                contentDescription = "Previous",
                colorFilter = iconFilter,
                modifier = GlanceModifier.size(36.dp).clickable(prevAction)
            )
        } else if (showExtraControls) {
            androidx.glance.layout.Spacer(modifier = GlanceModifier.width(36.dp))
        }

        Image(
            provider = ImageProvider(
                if (state.isPlaying) R.drawable.ic_widget_pause
                else R.drawable.ic_widget_play
            ),
            contentDescription = if (state.isPlaying) "Pause" else "Play",
            colorFilter = iconFilter,
            modifier = GlanceModifier
                .size(40.dp)
                .padding(horizontal = 4.dp)
                .clickable(playPauseAction)
        )

        if (showExtraControls && state.hasNext) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_next),
                contentDescription = "Next",
                colorFilter = iconFilter,
                modifier = GlanceModifier.size(36.dp).clickable(nextAction)
            )
        } else if (showExtraControls) {
            androidx.glance.layout.Spacer(modifier = GlanceModifier.width(36.dp))
        }
    }
}

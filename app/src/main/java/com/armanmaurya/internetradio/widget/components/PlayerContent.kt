package com.armanmaurya.internetradio.widget.components

import android.content.ComponentName
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionSendBroadcast
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.armanmaurya.internetradio.MainActivity
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
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .clickable(openAppAction),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtWork(
                art = state.artwork,
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
        Row(
            modifier = GlanceModifier.padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconFilter = androidx.glance.ColorFilter.tint(
                state.titleColor ?: GlanceTheme.colors.onPrimary
            )

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
}

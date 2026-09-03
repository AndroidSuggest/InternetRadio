package com.armanmaurya.internetradio.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.layout.Column
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

import androidx.glance.unit.ColorProvider

@Composable
fun NowPlayingTrackInfo(
    title: String,
    artist: String,
    titleColor: ColorProvider?,
    artistColor: ColorProvider?,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        Text(
            text = title,
            style = TextStyle(
                color = titleColor ?: GlanceTheme.colors.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            maxLines = 1
        )
        Text(
            text = artist,
            style = TextStyle(
                color = artistColor ?: GlanceTheme.colors.onPrimary,
                fontSize = 14.sp
            ),
            maxLines = 1
        )
    }
}

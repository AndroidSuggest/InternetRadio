package com.armanmaurya.internetradio.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.armanmaurya.internetradio.R

@Composable
fun ArtWork(
    art: ImageProvider?,
    modifier: GlanceModifier,
    stationArt: ImageProvider? = null,
    showStationFloating: Boolean = false,
    floatingThumbDimension: Dp = 20.dp,
) {
    if (art != null) {
        Box(
            modifier = modifier.cornerRadius(8.dp),
            contentAlignment = Alignment.TopStart
        ) {
            Image(
                provider = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize().cornerRadius(8.dp)
            )
            if (showStationFloating && stationArt != null) {
                Box(
                    modifier = GlanceModifier.padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = GlanceModifier
                            .size(floatingThumbDimension)
                            .cornerRadius(4.dp)
                            .background(GlanceTheme.colors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            provider = stationArt,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .cornerRadius(3.dp)
                        )
                    }
                }
            }
        }
    } else {
        Box(
            modifier = modifier.background(GlanceTheme.colors.surfaceVariant).cornerRadius(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.fillMaxSize()
            )
        }
    }
}
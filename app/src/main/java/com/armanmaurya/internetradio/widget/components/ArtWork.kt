package com.armanmaurya.internetradio.widget.components

import androidx.compose.runtime.Composable
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
import com.armanmaurya.internetradio.R

@Composable
fun ArtWork(art: ImageProvider?, modifier: GlanceModifier) {
    Box(
        modifier = modifier.background(GlanceTheme.colors.surfaceVariant).cornerRadius(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (art != null) {
            Image(
                provider = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize()
            )
        }
    }
}
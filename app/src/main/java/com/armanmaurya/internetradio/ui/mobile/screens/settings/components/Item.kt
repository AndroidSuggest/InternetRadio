package com.armanmaurya.internetradio.ui.mobile.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip

@Composable
fun Item(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    iconPainter: Painter? = null,
    subtitle: String? = null,
    shape: Shape = RectangleShape,
    trailingContent: (@Composable () -> Unit)? = null
) {
    androidx.compose.material3.ListItem(
        modifier = Modifier.clip(shape).clickable(onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        supportingContent = subtitle?.let { { Text(text = it) } },
        leadingContent = {
            if (icon != null) {
                Icon(icon, contentDescription = null)
            } else if (iconPainter != null) {
                Icon(iconPainter, contentDescription = null)
            }
        },
        trailingContent = trailingContent,
        headlineContent = { Text(text = title) }
    )
}
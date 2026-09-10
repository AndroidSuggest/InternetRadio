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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.draw.clip

@Composable
fun ToggleItem(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    icon: ImageVector? = null,
    shape: Shape = RectangleShape,
    enabled: Boolean = true
) {
    androidx.compose.material3.ListItem(
        modifier = Modifier
            .clip(shape)
            .then(
                if (enabled) Modifier.clickable { onToggle(!isEnabled) }
                else Modifier
            ),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            headlineColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            supportingColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            leadingIconColor = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        ),
        supportingContent = { Text(text = subtitle) },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        trailingContent = {
            Switch(
                checked = isEnabled,
                onCheckedChange = onToggle,
                enabled = enabled
            )
        },
        headlineContent = { Text(text = title) }
    )
}
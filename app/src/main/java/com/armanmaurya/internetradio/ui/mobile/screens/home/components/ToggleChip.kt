package com.armanmaurya.internetradio.ui.mobile.screens.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun ToggleChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    leadingIcon: ImageVector? = null,
    leadingIconContentDescription: String? = null,
    leadingIconSize: androidx.compose.ui.unit.Dp = 24.dp,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    trailingIconContentDescription: String? = null,
    trailingIconSize: androidx.compose.ui.unit.Dp = 16.dp
) {
    val backgroundColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .animateContentSize()
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides contentColor
        ) {
            if (leadingContent != null) {
            leadingContent()
            Spacer(Modifier.width(8.dp))
        } else if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = leadingIconContentDescription,
                modifier = Modifier.size(leadingIconSize),
                tint = contentColor
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor
        )
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = trailingIconContentDescription,
                modifier = Modifier.size(trailingIconSize),
                tint = contentColor
            )
        }
        }
    }
}

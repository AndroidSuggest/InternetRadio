package com.armanmaurya.internetradio.ui.mobile.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

@Composable
fun RateAppDialog(
    onRateClick: () -> Unit,
    onDismissClick: (permanently: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismissClick(false) },
        icon = {
            Icon(
                imageVector = Icons.Default.StarRate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.settings_rate_review),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = "Are you enjoying Internet Radio? If so, please consider taking a moment to rate the app. Your support means a lot!",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onRateClick
            ) {
                Text("Rate Now")
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismissClick(true) }
            ) {
                Text("No, Thanks")
            }
        }
    )
}

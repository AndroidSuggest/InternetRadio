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

import com.armanmaurya.internetradio.core.config.StoreConfig
import androidx.compose.material.icons.filled.Favorite

@Composable
fun RateAppDialog(
    onRateClick: () -> Unit,
    onDismissClick: (permanently: Boolean) -> Unit
) {
    val isPlay = StoreConfig.isPlayStoreBuild
    AlertDialog(
        onDismissRequest = { onDismissClick(false) },
        icon = {
            Icon(
                imageVector = if (isPlay) Icons.Default.StarRate else Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (isPlay) stringResource(R.string.settings_rate_review) else stringResource(R.string.settings_support_app),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = if (isPlay) {
                    stringResource(R.string.review_message_play)
                } else {
                    stringResource(R.string.review_message_foss)
                },
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onRateClick
            ) {
                Text(if (isPlay) stringResource(R.string.review_rate_now) else stringResource(R.string.review_star_github))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onDismissClick(true) }
            ) {
                Text(stringResource(R.string.review_no_thanks))
            }
        }
    )
}

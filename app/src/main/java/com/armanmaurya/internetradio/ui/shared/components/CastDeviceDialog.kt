package com.armanmaurya.internetradio.ui.shared.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.armanmaurya.internetradio.R
import org.fcast.sender_sdk.CastingDevice
import org.fcast.sender_sdk.DeviceInfo

@Composable
fun CastDeviceDialog(
    devices: List<DeviceInfo>,
    connectedDevice: CastingDevice?,
    volume: Float = 1f,
    onVolumeChange: (Float) -> Unit = {},
    onConnect: (DeviceInfo) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Title
                Text(
                    text = if (connectedDevice != null)
                        stringResource(R.string.cast_connected_to)
                    else
                        stringResource(R.string.cast_to_device),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (connectedDevice != null) {
                    // Connected device view
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(48.dp)
                                .padding(bottom = 16.dp)
                        )
                        Text(
                            text = connectedDevice.name(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.VolumeMute, contentDescription = null)
                            Slider(
                                value = volume,
                                onValueChange = onVolumeChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                            )
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        }
                    }
                } else {
                    // Scanning / device list view
                    Text(
                        text = stringResource(R.string.cast_available_devices),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (devices.isEmpty()) {
                        // Replaced "No devices found" with a loading indicator
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    } else {
                        LazyColumn {
                            items(devices) { device ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onConnect(device) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Cast, contentDescription = null)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = device.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    if (connectedDevice != null) {
                        TextButton(onClick = {
                            onDisconnect()
                            onDismiss()
                        }) {
                            Text(
                                stringResource(R.string.cast_disconnect),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cast_close))
                    }
                }
            }
        }
    }
}

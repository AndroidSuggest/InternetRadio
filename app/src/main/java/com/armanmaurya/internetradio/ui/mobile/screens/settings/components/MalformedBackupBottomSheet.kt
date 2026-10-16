package com.armanmaurya.internetradio.ui.mobile.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MalformedBackupBottomSheet(
    localStationCount: Int,
    onDismissRequest: () -> Unit,
    onOverwriteWithCurrentLibrary: () -> Unit,
    onChooseAnotherFolder: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    fun dismissWithAction(action: () -> Unit = {}) {
        coroutineScope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) {
                onDismissRequest()
                action()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = stringResource(R.string.settings_malformed_backup_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(R.string.settings_malformed_backup_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            val topShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
            val bottomShape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
            val singleShape = RoundedCornerShape(16.dp)

            if (localStationCount > 0) {
                Item(
                    title = stringResource(R.string.settings_malformed_backup_overwrite),
                    subtitle = stringResource(R.string.settings_malformed_backup_overwrite_desc, localStationCount),
                    icon = Icons.Default.CloudUpload,
                    shape = topShape,
                    onClick = {
                        dismissWithAction { onOverwriteWithCurrentLibrary() }
                    }
                )
                Spacer(modifier = Modifier.height(2.dp))
                Item(
                    title = stringResource(R.string.settings_malformed_backup_choose_folder),
                    subtitle = stringResource(R.string.settings_malformed_backup_choose_folder_desc),
                    icon = Icons.Default.FolderOpen,
                    shape = bottomShape,
                    onClick = {
                        dismissWithAction { onChooseAnotherFolder() }
                    }
                )
            } else {
                Item(
                    title = stringResource(R.string.settings_malformed_backup_choose_folder),
                    subtitle = stringResource(R.string.settings_malformed_backup_choose_folder_desc),
                    icon = Icons.Default.FolderOpen,
                    shape = singleShape,
                    onClick = {
                        dismissWithAction { onChooseAnotherFolder() }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { dismissWithAction() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.settings_malformed_backup_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

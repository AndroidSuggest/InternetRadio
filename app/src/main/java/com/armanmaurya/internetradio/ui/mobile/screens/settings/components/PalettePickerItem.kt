package com.armanmaurya.internetradio.ui.mobile.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.ui.shared.theme.AppColor

@Composable
fun PalettePickerItem(
    selectedColor: AppColor,
    isDynamicActive: Boolean,
    onColorSelected: (AppColor) -> Unit,
    darkTheme: Boolean,
    customColorArgb: Int = 0xFF00BCD4.toInt(),
    onOpenCustomColorPicker: () -> Unit = {},
    shape: Shape = RectangleShape,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 10.dp)
    ) {

        val palettes = AppColor.entries
        val row1 = palettes.take(4)
        val row2 = palettes.drop(4)

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            row1.forEach { color ->
                val isSelected = !isDynamicActive && selectedColor == color
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PaletteGridItem(
                        color = color,
                        isSelected = isSelected,
                        darkTheme = darkTheme,
                        customColorArgb = customColorArgb,
                        onClick = {
                            if (isSelected && color == AppColor.CUSTOM) {
                                onOpenCustomColorPicker()
                            } else {
                                onColorSelected(color)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            row2.forEach { color ->
                val isSelected = !isDynamicActive && selectedColor == color
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    PaletteGridItem(
                        color = color,
                        isSelected = isSelected,
                        darkTheme = darkTheme,
                        customColorArgb = customColorArgb,
                        onClick = {
                            if (isSelected && color == AppColor.CUSTOM) {
                                onOpenCustomColorPicker()
                            } else {
                                onColorSelected(color)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaletteGridItem(
    color: AppColor,
    isSelected: Boolean,
    darkTheme: Boolean,
    customColorArgb: Int = 0xFF00BCD4.toInt(),
    onClick: () -> Unit
) {
    val previewColors = color.getPreviewColors(darkTheme, customColorArgb)
    val boxShape = RoundedCornerShape(12.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(62.dp)
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(boxShape)
                .clickable(onClick = onClick)
                .then(
                    if (isSelected) {
                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, boxShape)
                    } else {
                        Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            boxShape
                        )
                    }
                )
        ) {
            // 2x2 grid of 4 palette colors
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(previewColors[0])
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(previewColors[1])
                    )
                }
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(previewColors[2])
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(previewColors[3])
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
            text = stringResource(color.titleRes),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

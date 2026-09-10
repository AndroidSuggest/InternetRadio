package com.armanmaurya.internetradio.ui.mobile.screens.settings.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.shared.theme.AppColor

private val QUICK_PICK_SWATCHES = listOf(
    0xFFE91E63.toInt(), // Pink / Rose
    0xFF9C27B0.toInt(), // Purple
    0xFF673AB7.toInt(), // Deep Purple
    0xFF3F51B5.toInt(), // Indigo
    0xFF2196F3.toInt(), // Blue
    0xFF03A9F4.toInt(), // Light Blue
    0xFF00BCD4.toInt(), // Cyan
    0xFF009688.toInt(), // Teal
    0xFF4CAF50.toInt(), // Green
    0xFF8BC34A.toInt(), // Light Green
    0xFFFFEB3B.toInt(), // Yellow
    0xFFFF9800.toInt(), // Orange
    0xFFF44336.toInt(), // Red
    0xFF795548.toInt(), // Brown
    0xFF607D8B.toInt()  // Blue Grey
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomColorPickerBottomSheet(
    initialColorArgb: Int,
    darkTheme: Boolean,
    onColorApplied: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    val initialHsv = remember(initialColorArgb) {
        FloatArray(3).apply {
            android.graphics.Color.colorToHSV(initialColorArgb, this)
        }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1].coerceIn(0f, 1f)) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceIn(0f, 1f)) }

    val currentArgb = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }

    var hexInput by remember(currentArgb) {
        mutableStateOf(String.format("%06X", 0xFFFFFF and currentArgb))
    }

    val focusManager = LocalFocusManager.current

    val previewColors = remember(currentArgb, darkTheme) {
        AppColor.CUSTOM.getPreviewColors(darkTheme, currentArgb)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.settings_custom_color_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Palette Preview (Left) and Filled Hex Input (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live 4-Quadrant Preview on Left
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(previewColors.getOrElse(0) { Color(currentArgb) })
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(previewColors.getOrElse(1) { Color(currentArgb) })
                            )
                        }
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(previewColors.getOrElse(2) { Color(currentArgb) })
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(previewColors.getOrElse(3) { Color(currentArgb) })
                            )
                        }
                    }
                }

                // Filled Hex Input Box (Not Bordered)
                TextField(
                    value = hexInput,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.take(6).uppercase()
                        hexInput = filtered
                        if (filtered.length == 6) {
                            try {
                                val parsed = android.graphics.Color.parseColor("#$filtered")
                                val newHsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(parsed, newHsv)
                                hue = newHsv[0]
                                saturation = newHsv[1]
                                value = newHsv[2]
                            } catch (_: Exception) {}
                        }
                    },
                    label = { Text(stringResource(R.string.settings_custom_color_hex)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2D Saturation-Value Canvas (Left) & 1D Rainbow Hue Bar (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 2D Saturation-Value Gradient Canvas on Left
                SaturationValueBox(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onColorChange = { s, v ->
                        saturation = s
                        value = v
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )

                // 1D Rainbow Hue Slider Bar on Right
                HueSliderBar(
                    hue = hue,
                    onHueChange = { h ->
                        hue = h
                    },
                    modifier = Modifier
                        .width(32.dp)
                        .fillMaxHeight()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick-Pick Color Swatches
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QUICK_PICK_SWATCHES) { swatchArgb ->
                    val isSelected = currentArgb == swatchArgb
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(swatchArgb))
                            .clickable {
                                val newHsv = FloatArray(3)
                                android.graphics.Color.colorToHSV(swatchArgb, newHsv)
                                hue = newHsv[0]
                                saturation = newHsv[1]
                                value = newHsv[2]
                            }
                            .then(
                                if (isSelected) {
                                    Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier.border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
                                }
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Bottom Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                        }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismissRequest()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_custom_color_cancel))
                }
                Button(
                    onClick = {
                        onColorApplied(currentArgb)
                        coroutineScope.launch {
                            sheetState.hide()
                        }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismissRequest()
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_custom_color_apply))
                }
            }
        }
    }
}

@Composable
private fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onColorChange: (sat: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val pureHueColor = remember(hue) { Color.hsv(hue, 1f, 1f) }
    val horizontalGradient = remember(pureHueColor) {
        Brush.horizontalGradient(listOf(Color.White, pureHueColor))
    }
    val verticalGradient = remember {
        Brush.verticalGradient(listOf(Color.Transparent, Color.Black))
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = (offset.x / size.width).coerceIn(0f, 1f)
                    val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                    onColorChange(s, v)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val s = (offset.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - offset.y / size.height).coerceIn(0f, 1f)
                        onColorChange(s, v)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                        onColorChange(s, v)
                    }
                )
            }
    ) {
        drawRect(brush = horizontalGradient)
        drawRect(brush = verticalGradient)

        val thumbRadius = 12.dp.toPx()
        val thumbX = (saturation * size.width).coerceIn(thumbRadius, size.width - thumbRadius)
        val thumbY = ((1f - value) * size.height).coerceIn(thumbRadius, size.height - thumbRadius)

        // Outer shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = thumbRadius,
            center = Offset(thumbX, thumbY)
        )
        // White ring
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(thumbX, thumbY)
        )
        // Selected color core
        drawCircle(
            color = Color.hsv(hue, saturation, value),
            radius = 7.dp.toPx(),
            center = Offset(thumbX, thumbY)
        )
    }
}

@Composable
private fun HueSliderBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = remember {
        listOf(
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
            Color.Red
        )
    }
    val rainbowBrush = remember {
        Brush.verticalGradient(rainbowColors)
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val h = (offset.y / size.height).coerceIn(0f, 1f) * 360f
                    onHueChange(h)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val h = (offset.y / size.height).coerceIn(0f, 1f) * 360f
                        onHueChange(h)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val h = (change.position.y / size.height).coerceIn(0f, 1f) * 360f
                        onHueChange(h)
                    }
                )
            }
    ) {
        drawRect(brush = rainbowBrush)

        val thumbRadius = 12.dp.toPx()
        val centerX = size.width / 2
        val thumbY = ((hue / 360f).coerceIn(0f, 1f) * size.height).coerceIn(thumbRadius, size.height - thumbRadius)

        // Outer shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = thumbRadius,
            center = Offset(centerX, thumbY)
        )
        // White ring
        drawCircle(
            color = Color.White,
            radius = 10.dp.toPx(),
            center = Offset(centerX, thumbY)
        )
        // Selected hue core
        drawCircle(
            color = Color.hsv(hue, 1f, 1f),
            radius = 7.dp.toPx(),
            center = Offset(centerX, thumbY)
        )
    }
}

package com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import coil3.compose.AsyncImage
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import com.armanmaurya.internetradio.data.local.entity.ScheduleType
import java.util.Locale
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleItem(
    schedule: ScheduleEntity,
    stationFavicon: String?,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val startAmPm = if (schedule.timeHour >= 12) "PM" else "AM"
    val startHour12 = if (schedule.timeHour % 12 == 0) 12 else schedule.timeHour % 12
    val startTimeString = String.format(Locale.getDefault(), "%02d:%02d %s", startHour12, schedule.timeMinute, startAmPm)
    
    val endTotalMinutes = schedule.timeHour * 60 + schedule.timeMinute + schedule.durationMinutes
    val endHour = (endTotalMinutes / 60) % 24
    val endMinute = endTotalMinutes % 60
    val endAmPm = if (endHour >= 12) "PM" else "AM"
    val endHour12 = if (endHour % 12 == 0) 12 else endHour % 12
    val endTimeString = String.format(Locale.getDefault(), "%02d:%02d %s", endHour12, endMinute, endAmPm)
    
    val timeRangeString = if (schedule.durationMinutes > 0) {
        "$startTimeString - $endTimeString"
    } else {
        "$startTimeString - ${stringResource(R.string.schedule_indefinite)}"
    }

    val repeatText = if (schedule.isRecurring) {
        null
    } else {
        stringResource(R.string.schedule_once)
    }

    val typeText = if (schedule.type == ScheduleType.PLAYBACK) stringResource(R.string.schedule_playback) else stringResource(R.string.schedule_record)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = stationFavicon?.ifBlank { null },
                    contentDescription = stringResource(R.string.schedule_station_logo_cd),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E1E1E)),
                    error = painterResource(id = R.drawable.ic_launcher_foreground),
                    fallback = painterResource(id = R.drawable.ic_launcher_foreground)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    val titleText = if (schedule.scheduleName.isNotBlank()) {
                        "${schedule.scheduleName} • ${schedule.stationName}"
                    } else {
                        schedule.stationName
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (schedule.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (repeatText != null) "$timeRangeString • $typeText • $repeatText" else "$timeRangeString • $typeText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = schedule.isEnabled,
                    onCheckedChange = onToggle
                )
            }

            if (schedule.isRecurring) {
                val enabledDays = schedule.daysOfWeek.split(",").mapNotNull { it.toIntOrNull() }
                val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (i in 1..7) {
                        val isSelected = enabledDays.contains(i)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNames[i - 1],
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
        }
    }
}

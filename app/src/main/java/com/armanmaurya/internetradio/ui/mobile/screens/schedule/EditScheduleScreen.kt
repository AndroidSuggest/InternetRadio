package com.armanmaurya.internetradio.ui.mobile.screens.schedule

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.data.local.entity.ScheduleEntity
import com.armanmaurya.internetradio.data.local.entity.ScheduleType
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules.SchedulesViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EditScheduleScreen(
    scheduleId: Int? = null,
    viewModel: SchedulesViewModel,
    onNavigateBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val libraryStationsState by viewModel.libraryStations.collectAsState()
    val libraryStations = libraryStationsState ?: emptyList()
    val schedulesState by viewModel.schedules.collectAsState()
    val schedules = schedulesState ?: emptyList()
    val appPreferences by viewModel.appPreferences.collectAsState()
    
    val scheduleToEdit = remember(scheduleId, schedules) {
        schedules.find { it.id == scheduleId }
    }
    
    var selectedStation by remember(scheduleToEdit, libraryStations) { 
        mutableStateOf(scheduleToEdit?.let { s -> libraryStations.find { it.stationUuid == s.stationUuid } }) 
    }
    var isSheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filteredStations = remember(searchQuery, libraryStations) {
        if (searchQuery.isBlank()) {
            libraryStations
        } else {
            libraryStations.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    if (isSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSheetOpen = false },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.schedule_select_station_title), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.schedule_search_library)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (filteredStations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (libraryStations.isEmpty()) stringResource(R.string.schedule_no_stations_library) else stringResource(R.string.schedule_no_matches_found))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredStations) { station ->
                            StationCard(
                                station = station,
                                onClick = {
                                    selectedStation = station
                                    isSheetOpen = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    ScheduleConfigurationForm(
        station = selectedStation,
        initialSchedule = scheduleToEdit,
        startOfWeek = appPreferences.startOfWeek,
        onStationClick = { isSheetOpen = true },
        onSave = { entity ->
            viewModel.saveSchedule(entity)
            Toast.makeText(context, context.getString(R.string.schedule_saved), Toast.LENGTH_SHORT).show()
            onNavigateBack()
        },
        onNavigateBack = onNavigateBack,
        onDelete = if (scheduleToEdit != null) { {
            viewModel.deleteSchedule(scheduleToEdit)
            Toast.makeText(context, context.getString(R.string.schedule_deleted), Toast.LENGTH_SHORT).show()
            onNavigateBack()
        } } else null,
        contentPadding = contentPadding
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleConfigurationForm(
    modifier: Modifier = Modifier,
    station: RadioStation?,
    initialSchedule: ScheduleEntity?,
    startOfWeek: com.armanmaurya.internetradio.data.model.StartOfWeek,
    onStationClick: () -> Unit,
    onSave: (ScheduleEntity) -> Unit,
    onNavigateBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val alarmManager = remember { context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager }

    val calendar = Calendar.getInstance()
    var scheduleName by remember(initialSchedule) { mutableStateOf(initialSchedule?.scheduleName ?: "") }
    var startHour by remember(initialSchedule) { mutableStateOf(initialSchedule?.timeHour ?: calendar.get(Calendar.HOUR_OF_DAY)) }
    var startMinute by remember(initialSchedule) { mutableStateOf(initialSchedule?.timeMinute ?: calendar.get(Calendar.MINUTE)) }
    var endHour by remember(initialSchedule) { 
        mutableStateOf(
            initialSchedule?.let { ((it.timeHour * 60 + it.timeMinute + it.durationMinutes) / 60) % 24 } ?: ((calendar.get(Calendar.HOUR_OF_DAY) + 1) % 24)
        ) 
    }
    var endMinute by remember(initialSchedule) { 
        mutableStateOf(
            initialSchedule?.let { (it.timeHour * 60 + it.timeMinute + it.durationMinutes) % 60 } ?: calendar.get(Calendar.MINUTE)
        ) 
    }
    
    var hasEndTime by remember(initialSchedule) { 
        mutableStateOf(initialSchedule == null || initialSchedule.durationMinutes > 0) 
    }

    var scheduleType by remember(initialSchedule) { mutableStateOf(initialSchedule?.type ?: ScheduleType.PLAYBACK) }
    var keepPlayback by remember(initialSchedule) { mutableStateOf(initialSchedule?.keepPlayback ?: false) }
    var playOnRecording by remember(initialSchedule) { mutableStateOf(initialSchedule?.playOnRecording ?: true) }
    val selectedDays = remember(initialSchedule) { 
        val days = mutableStateListOf<Int>()
        initialSchedule?.daysOfWeek?.split(",")?.mapNotNull { it.toIntOrNull() }?.let { days.addAll(it) }
        days 
    }
    var volumeLevel by remember(initialSchedule) { mutableFloatStateOf(initialSchedule?.volumeLevel ?: 1.0f) }

    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    
    val is24HourFormat = android.text.format.DateFormat.is24HourFormat(context)

    val currentConfig = androidx.compose.ui.platform.LocalConfiguration.current
    val timePickerConfiguration = remember(is24HourFormat, currentConfig) {
        android.content.res.Configuration(currentConfig).apply {
            if (!is24HourFormat) {
                setLocale(java.util.Locale.ENGLISH)
            }
        }
    }
    val timePickerContext = remember(timePickerConfiguration) {
        if (!is24HourFormat) context.createConfigurationContext(timePickerConfiguration) else context
    }

    if (showStartTimePicker) {
        val startTimePickerState = rememberTimePickerState(initialHour = startHour, initialMinute = startMinute, is24Hour = is24HourFormat)
        TimePickerDialog(
            onDismissRequest = { showStartTimePicker = false },
            onConfirm = {
                startHour = startTimePickerState.hour
                startMinute = startTimePickerState.minute
                showStartTimePicker = false
            }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides timePickerConfiguration,
                androidx.compose.ui.platform.LocalContext provides timePickerContext
            ) {
                TimePicker(state = startTimePickerState)
            }
        }
    }

    if (showEndTimePicker) {
        val endTimePickerState = rememberTimePickerState(initialHour = endHour, initialMinute = endMinute, is24Hour = is24HourFormat)
        TimePickerDialog(
            onDismissRequest = { showEndTimePicker = false },
            onConfirm = {
                endHour = endTimePickerState.hour
                endMinute = endTimePickerState.minute
                hasEndTime = true
                showEndTimePicker = false
            }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalConfiguration provides timePickerConfiguration,
                androidx.compose.ui.platform.LocalContext provides timePickerContext
            ) {
                TimePicker(state = endTimePickerState)
            }
        }
    }

    fun formatTime(hour: Int, minute: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }
        return com.armanmaurya.internetradio.utils.FormatUtils.getTimeFormat(context).format(cal.time)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (initialSchedule != null) stringResource(R.string.schedule_edit) else stringResource(R.string.schedule_create)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(
                        onClick = {
                            if (station == null) return@TextButton
            
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                context.startActivity(intent)
                                Toast.makeText(context, context.getString(R.string.schedule_grant_exact_alarm_permission), Toast.LENGTH_LONG).show()
                                return@TextButton
                            }
                            
                            var duration = 0
                            if (hasEndTime) {
                                duration = (endHour * 60 + endMinute) - (startHour * 60 + startMinute)
                                if (duration <= 0) duration += 24 * 60 // crosses midnight
                            }
                            
                            val entity = ScheduleEntity(
                                id = initialSchedule?.id ?: 0,
                                stationUuid = station.stationUuid,
                                stationName = station.name,
                                type = scheduleType,
                                triggerTimeInMillis = 0L,
                                durationMinutes = duration,
                                isRecurring = selectedDays.isNotEmpty(),
                                daysOfWeek = selectedDays.joinToString(","),
                                timeHour = startHour,
                                timeMinute = startMinute,
                                isEnabled = true,
                                volumeLevel = volumeLevel,
                                keepPlayback = keepPlayback,
                                playOnRecording = playOnRecording,
                                scheduleName = scheduleName.trim()
                            )
                            onSave(entity)
                        },
                        enabled = station != null
                    ) {
                        Text(if (initialSchedule != null) stringResource(R.string.schedule_action_save) else stringResource(R.string.schedule_action_create))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = scheduleName,
            onValueChange = { scheduleName = it },
            label = { Text(stringResource(R.string.schedule_name_optional)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            onClick = onStationClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (station != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    coil3.compose.SubcomposeAsyncImage(
                        model = coil3.request.ImageRequest.Builder(LocalContext.current)
                            .data(station.favicon.ifBlank { null })
                            .size(coil3.size.Size.ORIGINAL)
                            .build(),
                        contentDescription = stringResource(R.string.schedule_station_logo_cd),
                        contentScale = ContentScale.Crop,
                        filterQuality = FilterQuality.High,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        error = {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxSize()
                            )
                        },
                        loading = {
                            androidx.compose.foundation.Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    )

                    val gradientBrush = remember {
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(gradientBrush)
                    )
                    
                    val subtitleText = remember(station.country, station.language, station.codec, station.bitrate) {
                        buildString {
                            if (station.country.isNotBlank()) append(station.country)
                            if (station.country.isNotBlank() && station.language.isNotBlank()) append(" • ")
                            if (station.language.isNotBlank()) append(station.language)
                            
                            val hasPrevious = station.country.isNotBlank() || station.language.isNotBlank()
                            if (hasPrevious && (station.codec.isNotBlank() || station.bitrate > 0)) {
                                append(" | ")
                            }
                            if (station.codec.isNotBlank()) append(station.codec)
                            if (station.codec.isNotBlank() && station.bitrate > 0) append(" ")
                            if (station.bitrate > 0) append("${station.bitrate} kbps")
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Radio, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.schedule_select_a_station), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }


        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(
                onClick = { showStartTimePicker = true },
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.schedule_start_time), style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatTime(startHour, startMinute),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .clip(CardDefaults.shape)
                    .combinedClickable(
                        onClick = { showEndTimePicker = true },
                        onLongClick = { hasEndTime = false }
                    )
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.schedule_end_time), style = MaterialTheme.typography.labelMedium)
                    Text(stringResource(R.string.schedule_hold_to_clear), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasEndTime) formatTime(endHour, endMinute) else stringResource(R.string.schedule_none),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val dayOrder = startOfWeek.getDaysOrder()
            
            for (dayValue in dayOrder) {
                val isSelected = selectedDays.contains(dayValue)
                val javaTimeDay = if (dayValue == 1) java.time.DayOfWeek.SUNDAY else java.time.DayOfWeek.of(dayValue - 1)
                val dayName = javaTimeDay.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).take(2)
                val cornerPercent by androidx.compose.animation.core.animateIntAsState(
                    targetValue = if (isSelected) 25 else 50,
                    label = "cornerPercent"
                )
                
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        onClick = {
                            if (isSelected) selectedDays.remove(dayValue)
                            else selectedDays.add(dayValue)
                        },
                        shape = RoundedCornerShape(percent = cornerPercent),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .sizeIn(maxWidth = 44.dp, maxHeight = 44.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = dayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isPlayback = scheduleType == ScheduleType.PLAYBACK
            
            val playbackInnerCorner by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (isPlayback) 20.dp else 8.dp, 
                label = "playbackInnerCorner"
            )
            val recordInnerCorner by androidx.compose.animation.core.animateDpAsState(
                targetValue = if (!isPlayback) 20.dp else 8.dp, 
                label = "recordInnerCorner"
            )

            val leftShape = RoundedCornerShape(
                topStart = 20.dp,
                bottomStart = 20.dp,
                topEnd = playbackInnerCorner,
                bottomEnd = playbackInnerCorner
            )

            val rightShape = RoundedCornerShape(
                topStart = recordInnerCorner,
                bottomStart = recordInnerCorner,
                topEnd = 20.dp,
                bottomEnd = 20.dp
            )
            
            val playbackColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isPlayback) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "playbackColor"
            )
            val playbackContentColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isPlayback) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "playbackContentColor"
            )
            
            val recordColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (!isPlayback) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                label = "recordColor"
            )
            val recordContentColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (!isPlayback) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "recordContentColor"
            )

            Surface(
                modifier = Modifier.weight(1f).height(40.dp),
                shape = leftShape,
                color = playbackColor,
                contentColor = playbackContentColor,
                onClick = { scheduleType = ScheduleType.PLAYBACK }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.schedule_playback),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            
            Surface(
                modifier = Modifier.weight(1f).height(40.dp),
                shape = rightShape,
                color = recordColor,
                contentColor = recordContentColor,
                onClick = { scheduleType = ScheduleType.RECORD }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.schedule_record),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        AnimatedVisibility(
            visible = scheduleType == ScheduleType.RECORD,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(stringResource(R.string.schedule_listen_while_recording), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.schedule_listen_while_recording_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = playOnRecording,
                            onCheckedChange = { playOnRecording = it }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        val showPlaybackOptions = scheduleType == ScheduleType.PLAYBACK || (scheduleType == ScheduleType.RECORD && playOnRecording)

        AnimatedVisibility(
            visible = showPlaybackOptions,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.schedule_volume, (volumeLevel * 100).toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Slider(
                            value = volumeLevel,
                            onValueChange = { volumeLevel = it },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            track = { sliderState ->
                                SliderDefaults.Track(
                                    sliderState = sliderState,
                                    modifier = Modifier.height(24.dp),
                                    colors = SliderDefaults.colors()
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        AnimatedVisibility(
            visible = scheduleType == ScheduleType.RECORD && playOnRecording,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(stringResource(R.string.schedule_keep_playback_after_recording), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.schedule_keep_playback_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = keepPlayback,
                            onCheckedChange = { keepPlayback = it }
                        )
                    }
                }
            }
        }

        val safeDrawingBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()
        val bottomPadding = androidx.compose.ui.unit.max(contentPadding.calculateBottomPadding(), safeDrawingBottom)
        Spacer(modifier = Modifier.height(16.dp + bottomPadding))
        }
    }
}

@Composable
fun TimePickerDialog(
    title: String = stringResource(R.string.schedule_select_time),
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { content() },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.general_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.general_cancel))
            }
        }
    )
}

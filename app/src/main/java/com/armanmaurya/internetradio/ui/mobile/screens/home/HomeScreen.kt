package com.armanmaurya.internetradio.ui.mobile.screens.home


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.RadioSearchBar
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.library.LibraryContent
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.browse.BrowseContent
import com.armanmaurya.internetradio.ui.shared.viewmodels.BrowseViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recent.RecentContent
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import com.armanmaurya.internetradio.data.model.RadioStation

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationDrawerItem
import com.armanmaurya.internetradio.ui.shared.viewmodels.LibraryViewModel
import com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.schedules.SchedulesTabContent
import com.armanmaurya.internetradio.ui.mobile.screens.home.components.StationListCard
import com.armanmaurya.internetradio.ui.mobile.screens.home.layout.ExpandedHomeLayout
import com.armanmaurya.internetradio.ui.mobile.screens.home.layout.CompactHomeLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    widthSizeClass: WindowWidthSizeClass,
    onSettingsClick: () -> Unit,
    onCountryClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onTagClick: () -> Unit,
    onEditSchedule: (Int?) -> Unit,
    onEditStation: (String?) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: HomeViewModel = hiltViewModel(),
    browseViewModel: BrowseViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val browseUiState by browseViewModel.uiState.collectAsStateWithLifecycle()
    val libraryStations by libraryViewModel.stations.collectAsStateWithLifecycle(initialValue = emptyList())
    val libraryUuids by libraryViewModel.stationUuids.collectAsStateWithLifecycle(initialValue = emptySet())
    val searchLibraryStations by libraryViewModel.searchStations.collectAsStateWithLifecycle(initialValue = emptyList())
    val filteredLibraryStations = searchLibraryStations ?: emptyList()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val playingStationUuid = playbackState.currentStation?.stationUuid
    val isPlaybackActive = playbackState.isPlaying

    if (!uiState.isPreferencesLoaded) {
        return // Wait for preferences to load before rendering
    }

    val tabs = listOf(
        stringResource(R.string.home_tab_browse),
        stringResource(R.string.home_tab_recent),
        stringResource(R.string.home_tab_library),
        stringResource(R.string.home_tab_recordings),
        stringResource(R.string.home_tab_schedules)
    )
    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab,
        pageCount = { tabs.size }
    )
    
    // Defer beyondViewportPageCount to avoid lag during navigation transitions
    var beyondBounds by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(400)
        beyondBounds = 1
    }
    
    val context = LocalContext.current
    var stationToExport by remember { mutableStateOf<RadioStation?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && stationToExport != null) {
            val result = com.armanmaurya.internetradio.utils.ExportUtils.exportStation(context, uri, stationToExport!!)
            val message = if (result.isSuccess) context.getString(R.string.export_success, stationToExport!!.name) else context.getString(R.string.export_failed, result.exceptionOrNull()?.localizedMessage)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            stationToExport = null
        }
    }
    val onExportStation: (RadioStation) -> Unit = { station ->
        stationToExport = station
        exportLauncher.launch("${station.name}.json")
    }

    val coroutineScope = rememberCoroutineScope()

    val density = LocalDensity.current
    val tabWidths = remember {
        mutableStateListOf<Dp>().apply {
            repeat(tabs.size) { add(0.dp) }
        }
    }

    val sheetState = rememberModalBottomSheetState()
    var isSearchExpanded by remember { mutableStateOf(false) }

    // Forward search query from HomeViewModel → ViewModels
    LaunchedEffect(uiState.searchQuery) {
        browseViewModel.onSearchQueryChange(uiState.searchQuery)
        libraryViewModel.onSearchQueryChange(uiState.searchQuery)
    }

    // Keep pager in sync with tab state from HomeViewModel (tab click)
    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(uiState.selectedTab)
        }
    }

    // Keep HomeViewModel's selectedTab in sync when user swipes pager
    LaunchedEffect(pagerState.settledPage) {
        viewModel.onTabSelected(pagerState.settledPage)
    }

    Scaffold(
        topBar = {
            if (widthSizeClass != WindowWidthSizeClass.Expanded) {
                val isPureBlack = MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black
            RadioSearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                isSearchExpanded = isSearchExpanded,
                onExpandedChange = { isSearchExpanded = it },
                onSearchCleared = viewModel::onSearchCleared,
                onCountryClick = onCountryClick,
                onLanguageClick = onLanguageClick,
                onTagClick = onTagClick,
                onSettingsClick = onSettingsClick,
                onSearch = { if (uiState.autoRouteToBrowseOnSearch) viewModel.onTabSelected(0) },
                selectedCountryCode = uiState.selectedCountryCode,
                selectedStateCode = uiState.selectedStateCode,
                selectedLanguage = uiState.selectedLanguage,
                selectedTags = uiState.selectedTags
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    if (filteredLibraryStations.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.home_tab_library),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(
                            items = filteredLibraryStations.take(5),
                            key = { "lib_${it.stationUuid}" }
                        ) { station ->
                            StationListCard(
                                station = station,
                                isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                isPlaybackActive = isPlaybackActive,
                                isFavorite = true,
                                onClick = {
                                    viewModel.onSearchQueryChange(station.name)
                                    isSearchExpanded = false
                                    if (uiState.autoRouteToBrowseOnSearch) {
                                        viewModel.onTabSelected(2)
                                        libraryViewModel.setFilterEnabled(true)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .animateItem()
                            )
                        }
                    }

                    if (browseUiState.stations.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.home_tab_browse),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(
                            items = browseUiState.stations.take(10),
                            key = { "browse_${it.stationUuid}" }
                        ) { station ->
                            StationListCard(
                                station = station,
                                isCurrentlyPlaying = playingStationUuid == station.stationUuid,
                                isPlaybackActive = isPlaybackActive,
                                isFavorite = libraryUuids.contains(station.stationUuid),
                                onClick = {
                                    viewModel.onSearchQueryChange(station.name)
                                    isSearchExpanded = false
                                    if (uiState.autoRouteToBrowseOnSearch) {
                                        viewModel.onTabSelected(0)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .animateItem()
                            )
                        }
                    }
                }
                }
            }
        },

        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val pagerContent = @Composable {
            // Tab Container with horizontal pager
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = if (widthSizeClass == WindowWidthSizeClass.Expanded) {
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp)
                } else {
                    RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                },
                border = if (MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                } else null
            ) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = beyondBounds,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    when (page) {
                        0 -> BrowseContent(
                            onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                            onEditStation = onEditStation,
                            onExportStation = onExportStation,
                            contentPadding = contentPadding,
                            viewModel = browseViewModel,
                            playingStationUuid = playingStationUuid,
                            isPlaybackActive = isPlaybackActive
                        )
                        1 -> RecentContent(
                            onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                            onEditStation = onEditStation,
                            onExportStation = onExportStation,
                            contentPadding = contentPadding,
                            playingStationUuid = playingStationUuid,
                            isPlaybackActive = isPlaybackActive,
                            searchQuery = uiState.searchQuery
                        )
                        2 -> LibraryContent(
                            onStationClick = { stations, index, source -> playerViewModel.play(stations, index, source) },
                            onEditStation = { stationUuid -> onEditStation(stationUuid) },
                            onExportStation = onExportStation,
                            contentPadding = contentPadding,
                            playingStationUuid = playingStationUuid,
                            isPlaybackActive = isPlaybackActive,
                            searchQuery = uiState.searchQuery
                        )
                        3 -> com.armanmaurya.internetradio.ui.mobile.screens.home.tabs.recordings.RecordingsContent(
                            contentPadding = contentPadding
                        )
                        4 -> SchedulesTabContent(
                            onEditSchedule = onEditSchedule,
                            contentPadding = contentPadding
                        )
                    }
                }
            }
        }

        if (widthSizeClass == WindowWidthSizeClass.Expanded) {
            ExpandedHomeLayout(
                innerPadding = innerPadding,
                pagerContent = pagerContent,
                searchQuery = uiState.searchQuery,
                isSearchExpanded = isSearchExpanded,
                onSearchExpandedChange = { isSearchExpanded = it },
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onSearchCleared = viewModel::onSearchCleared,
                onCountryClick = onCountryClick,
                onLanguageClick = onLanguageClick,
                onTagClick = onTagClick,
                onSettingsClick = onSettingsClick,
                onSearch = { _ -> if (uiState.autoRouteToBrowseOnSearch) viewModel.onTabSelected(0) },
                selectedCountryCode = uiState.selectedCountryCode,
                selectedStateCode = uiState.selectedStateCode,
                selectedLanguage = uiState.selectedLanguage,
                selectedTags = uiState.selectedTags,
                browseStations = browseUiState.stations,
                libraryStations = filteredLibraryStations,
                libraryUuids = libraryUuids,
                onLibraryStationClick = { station ->
                    viewModel.onSearchQueryChange(station.name)
                    isSearchExpanded = false
                    if (uiState.autoRouteToBrowseOnSearch) {
                        viewModel.onTabSelected(2)
                        libraryViewModel.setFilterEnabled(true)
                    }
                },
                onBrowseStationClick = { station ->
                    viewModel.onSearchQueryChange(station.name)
                    isSearchExpanded = false
                    if (uiState.autoRouteToBrowseOnSearch) {
                        viewModel.onTabSelected(0)
                    }
                },
                tabs = tabs,
                pagerState = pagerState,
                coroutineScope = coroutineScope
            )
        } else {
            CompactHomeLayout(
                innerPadding = innerPadding,
                pagerState = pagerState,
                tabs = tabs,
                tabWidths = tabWidths,
                coroutineScope = coroutineScope,
                pagerContent = pagerContent
            )
        }
    }
}



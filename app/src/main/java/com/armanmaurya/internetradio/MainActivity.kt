package com.armanmaurya.internetradio

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.armanmaurya.internetradio.ui.mobile.navigation.AppNavHost
import com.armanmaurya.internetradio.ui.mobile.navigation.AppDestination
import com.armanmaurya.internetradio.ui.mobile.screens.player.PlayerSheetContent
import android.widget.Toast
import com.armanmaurya.internetradio.ui.shared.viewmodels.PlayerViewModel
import com.armanmaurya.internetradio.ui.shared.theme.InternetRadioTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import javax.inject.Inject

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import com.armanmaurya.internetradio.ui.shared.viewmodels.MainViewModel
import com.armanmaurya.internetradio.ui.shared.components.UpdateBottomSheet

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val _intentFlow = kotlinx.coroutines.flow.MutableSharedFlow<Intent>(
        replay = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { _intentFlow.tryEmit(it) }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        intent?.let { _intentFlow.tryEmit(it) }
        installSplashScreen()
        super.onCreate(savedInstanceState)
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }

        // Sync whatever locale is currently active (set by our settings or system App Info)
        // back to DataStore so our UI always reflects the real current language.
        val currentLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        val activeTag = if (currentLocales.isEmpty) "System" else currentLocales[0]?.toLanguageTag() ?: "System"
        lifecycleScope.launch {
            settingsRepository.setAppLanguage(activeTag)
        }
        enableEdgeToEdge()
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val updateAvailable by mainViewModel.updateAvailable.collectAsStateWithLifecycle()
            
            val appPreferences by settingsRepository.appPreferencesFlow
                .collectAsStateWithLifecycle(initialValue = com.armanmaurya.internetradio.data.model.AppPreferences())

            LaunchedEffect(appPreferences.disableUpdateCheck) {
                if (!appPreferences.disableUpdateCheck) {
                    val versionName = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
                    } catch (e: Exception) {
                        "0.0.0"
                    }
                    mainViewModel.checkForUpdates(versionName)
                }
            }

            InternetRadioTheme(appPreferences = appPreferences) {
                updateAvailable?.let { release ->
                    UpdateBottomSheet(
                        release = release,
                        onDismiss = { mainViewModel.dismissUpdate() },
                        onConfirm = { 
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.html_url)))
                        }
                    )
                }
                
                val navController = rememberNavController()
                val homeViewModel: com.armanmaurya.internetradio.ui.mobile.screens.home.HomeViewModel = hiltViewModel()
                LaunchedEffect(Unit) {
                    _intentFlow.collect { intent ->
                        if (intent.getStringExtra("open_tab") == "recordings") {
                            intent.removeExtra("open_tab")
                            navController.popBackStack(AppDestination.Discover.route, inclusive = false)
                            homeViewModel.onTabSelected(3)
                        }
                    }
                }
                val playerViewModel: PlayerViewModel = hiltViewModel()
                val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

                val scope = rememberCoroutineScope()
                val scaffoldState = rememberBottomSheetScaffoldState(
                    bottomSheetState = rememberStandardBottomSheetState(
                        initialValue = SheetValue.PartiallyExpanded,
                        skipHiddenState = false
                    )
                )
                
                val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted ->
                    if (granted) {
                        playerViewModel.proceedWithRecording()
                    }
                }

                LaunchedEffect(Unit) {
                    playerViewModel.permissionRequestEvent.collect { _ ->
                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                BackHandler(enabled = scaffoldState.bottomSheetState.currentValue == SheetValue.Expanded) {
                    scope.launch {
                        scaffoldState.bottomSheetState.partialExpand()
                    }
                }

                // Handle Swipe to Dismiss (Stop playback when swiped away)
                LaunchedEffect(scaffoldState.bottomSheetState.currentValue) {
                    if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden && playbackState.currentStation != null) {
                        playerViewModel.stop()
                    }
                }

                // Show "Recording saved" toast at Activity level so it appears even when the
                // miniplayer sheet is dismissed (the sheet's composable would already be gone).
                val localContext = LocalContext.current
                LaunchedEffect(Unit) {
                    playerViewModel.recordingSavedEvent.collect {
                        android.widget.Toast.makeText(
                            localContext,
                            localContext.getString(R.string.player_recording_saved),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // Handle Re-appearing (Show player when a station starts playing) and Hiding (when playback stops)
                LaunchedEffect(playbackState.currentStation) {
                    if (playbackState.currentStation != null && scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
                        scaffoldState.bottomSheetState.partialExpand()
                    } else if (playbackState.currentStation == null && scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
                        scaffoldState.bottomSheetState.hide()
                    }
                }

                val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
                LaunchedEffect(scaffoldState.bottomSheetState.targetValue) {
                    if (scaffoldState.bottomSheetState.targetValue == SheetValue.Expanded) {
                        keyboardController?.hide()
                    }
                }

                val density = LocalDensity.current
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val sheetPeekHeight = if (playbackState.currentStation != null) 72.dp + bottomInset else 0.dp

                val onCheckUpdates: () -> Unit = {
                    runOnUiThread {
                        android.widget.Toast.makeText(this@MainActivity, getString(R.string.settings_checking_for_updates), android.widget.Toast.LENGTH_SHORT).show()
                    }
                    val vName = try {
                        packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"
                    } catch (e: Exception) {
                        "0.0.0"
                    }
                    mainViewModel.checkForUpdates(vName, force = true) { hasUpdate ->
                        if (!hasUpdate) {
                            runOnUiThread {
                                android.widget.Toast.makeText(this@MainActivity, getString(R.string.settings_no_update_available), android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

                val windowSizeClass = calculateWindowSizeClass(this@MainActivity)
                val widthSizeClass = windowSizeClass.widthSizeClass
                val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded


                BottomSheetScaffold(
                    modifier = Modifier.imePadding(),
                    scaffoldState = scaffoldState,
                    sheetPeekHeight = sheetPeekHeight,
                    sheetMaxWidth = androidx.compose.ui.unit.Dp.Unspecified,
                    sheetDragHandle = null,
                    sheetContent = {
                        val configuration = LocalConfiguration.current
                        val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                        val imeInsets = WindowInsets.ime

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .heightIn(min = 72.dp + bottomInset)
                        ) {
                            val peekHeightPx = with(density) { (72.dp + bottomInset).toPx() }
                            
                            val progress by remember(screenHeightPx, peekHeightPx, imeInsets) {
                                derivedStateOf {
                                    val imeHeightPx = imeInsets.getBottom(density).toFloat()
                                    val fullHeight = screenHeightPx - imeHeightPx
                                    
                                    val currentOffset = try {
                                        scaffoldState.bottomSheetState.requireOffset()
                                    } catch (e: Exception) {
                                        fullHeight - peekHeightPx
                                    }
                                    
                                    val totalRange = fullHeight - peekHeightPx
                                    if (totalRange > 0) {
                                        (1f - (currentOffset / totalRange)).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                }
                            }

                            val enableSwipeToDismiss by remember(
                                progress,
                                scaffoldState.bottomSheetState.currentValue,
                                scaffoldState.bottomSheetState.targetValue
                            ) {
                                derivedStateOf {
                                    progress == 0f && scaffoldState.bottomSheetState.currentValue == SheetValue.PartiallyExpanded && scaffoldState.bottomSheetState.targetValue == SheetValue.PartiallyExpanded
                                }
                            }
                            val currentSwipeAllowed by rememberUpdatedState(enableSwipeToDismiss)
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.Settled) {
                                        true
                                    } else { // SwipeToDismissBoxValue.StartToEnd, SwipeToDismissBoxValue.EndToStart
                                        if (currentSwipeAllowed) {
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                }
                            )

                            LaunchedEffect(playbackState.currentStation) {
                                if (playbackState.currentStation != null) {
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                            }

                            val isFavorite by playerViewModel.isFavorite.collectAsStateWithLifecycle()
                            val trackHistory by playerViewModel.trackHistory.collectAsStateWithLifecycle()
                            val stationRecordings by playerViewModel.stationRecordings.collectAsStateWithLifecycle()
                            val activeSessions by playerViewModel.activeSessions.collectAsStateWithLifecycle()
                            val isRecording by playerViewModel.isCurrentStationRecording.collectAsStateWithLifecycle()
                            val recordingDuration by playerViewModel.currentRecordingDuration.collectAsStateWithLifecycle()
                            val amplitude by playerViewModel.amplitude.collectAsStateWithLifecycle()
                            val retryCountdown by playerViewModel.retryCountdown.collectAsStateWithLifecycle()
                            val discoveredCastDevices by playerViewModel.discoveredCastDevices.collectAsStateWithLifecycle()
                            val connectedCastDevice by playerViewModel.connectedCastDevice.collectAsStateWithLifecycle()
                            val castPlaybackState by playerViewModel.castPlaybackState.collectAsStateWithLifecycle()
                            val castVolume by playerViewModel.castVolume.collectAsStateWithLifecycle()
                            val lyricsState by playerViewModel.lyricsState.collectAsStateWithLifecycle()

                            val effectivePlaybackState = if (connectedCastDevice != null) {
                                val stateName = castPlaybackState?.toString()?.uppercase() ?: ""
                                playbackState.copy(
                                    isPlaying = stateName.contains("PLAY"),
                                    isLoading = stateName.contains("BUFFER")
                                )
                            } else {
                                playbackState
                            }

                            val localContext = LocalContext.current

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = enableSwipeToDismiss,
                                enableDismissFromEndToStart = enableSwipeToDismiss,
                                gesturesEnabled = enableSwipeToDismiss,
                                onDismiss = { direction ->
                                    scope.launch { scaffoldState.bottomSheetState.hide() }
                                },
                                backgroundContent = {
                                    if (dismissState.dismissDirection != SwipeToDismissBoxValue.Settled) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(72.dp)
                                                .padding(start = 16.dp, end = 16.dp)
                                                .alpha(1f - (progress * 5f).coerceIn(0f, 1f)),
                                            // horizontal = 16.dp
                                            contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                                Alignment.CenterStart
                                            } else if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                                Alignment.CenterEnd
                                            } else {
                                                Alignment.Center
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Stop,
                                                contentDescription = getString(R.string.player_cd_stop),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            ) {
                                PlayerSheetContent(
                                    isWidescreen = isExpanded,
                                    playbackState = effectivePlaybackState,
                                    isFavorite = isFavorite,
                                    trackHistory = trackHistory,
                                    stationRecordings = stationRecordings,
                                    activeSessions = activeSessions,
                                    retryCountdown = retryCountdown,
                                    lyricsState = lyricsState,
                                    progress = progress,
                                    onTogglePlayPause = playerViewModel::togglePlayPause,
                                    onToggleFavorite = playerViewModel::toggleFavorite,
                                    onSetSleepTimer = playerViewModel::setSleepTimer,
                                    onCancelSleepTimer = playerViewModel::cancelSleepTimer,
                                    onCollapse = {
                                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                    },
                                    onExpand = {
                                        scope.launch { scaffoldState.bottomSheetState.expand() }
                                    },
                                    onNext = playerViewModel::next,
                                    onPrevious = playerViewModel::previous,
                                    onPlayIndex = playerViewModel::playIndex,
                                    onEditStation = { station ->
                                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                                        navController.navigate(AppDestination.EditStation.createRoute(station.stationUuid))
                                    },
                                    isRecording = isRecording,
                                    recordingDuration = recordingDuration,
                                    amplitude = amplitude,
                                    onToggleRecording = playerViewModel::toggleRecording,
                                    onSyncOffsetChange = playerViewModel::setLyricsSyncOffset,
                                    discoveredCastDevices = discoveredCastDevices,
                                    volume = castVolume.toFloat(),
                                    onVolumeChange = playerViewModel::setVolume,
                                    connectedCastDevice = connectedCastDevice,
                                    onConnectCastDevice = playerViewModel::connectToCastDevice,
                                    onDisconnectCastDevice = playerViewModel::disconnectCastDevice,
                                    onDeleteRecording = playerViewModel::deleteRecording,
                                    getCurrentPosition = { playerViewModel.currentPosition }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    AppNavHost(
                        navController = navController,
                        widthSizeClass = widthSizeClass,
                        contentPadding = innerPadding,
                        modifier = Modifier.fillMaxSize(),
                        onCheckUpdates = onCheckUpdates
                    )
                }
            }
        }
    }
}

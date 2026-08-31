package com.armanmaurya.internetradio.player

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.armanmaurya.internetradio.MainActivity
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.TrackHistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject
    lateinit var audioAttributes: AudioAttributes

    @Inject
    lateinit var autoCallback: AutoMediaLibraryCallback

    @Inject
    lateinit var trackHistoryRepository: TrackHistoryRepository

    @Inject
    lateinit var recordingManager: RecordingManager

    @Inject
    lateinit var retryStateTracker: RetryStateTracker

    @Inject
    lateinit var scheduleRepository: com.armanmaurya.internetradio.data.repository.ScheduleRepository

    @Inject
    lateinit var settingsRepository: com.armanmaurya.internetradio.data.repository.SettingsRepository

    @Inject
    lateinit var libraryRepository: com.armanmaurya.internetradio.data.repository.LibraryRepository

    @Inject
    lateinit var recentRepository: com.armanmaurya.internetradio.data.repository.RecentRepository

    @Inject
    lateinit var coverArtRepository: com.armanmaurya.internetradio.data.repository.CoverArtRepository

    @Inject
    lateinit var okHttpClient: okhttp3.OkHttpClient

    private var player: Player? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var loadErrorHandlingPolicy: ExponentialBackoffLoadErrorHandlingPolicy
    
    private var stopOnAudioBecomingNoisy: Boolean = true
    private var pauseOnVolumeZero: Boolean = false
    private var previousVolume: Int = -1
    private var ignoreNextVolumeZero: Boolean = false
    private var showCoverArtInNotification: Boolean = true
    private var alarmFadeInSeconds: Int = 0
    private var volumeFadeJob: kotlinx.coroutines.Job? = null
    private var activeTrackTitle: String? = null
    
    private val audioNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                if (stopOnAudioBecomingNoisy) {
                    player?.pause()
                }
            }
        }
    }

    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var libraryStatusJob: kotlinx.coroutines.Job? = null

    /**
     * Watches for station changes so the ❤️ button on Android Auto's now-playing
     * screen always shows the correct filled / outline state.
     */
    private val stationChangeListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            libraryStatusJob?.cancel()
            val stationUuid = mediaItem?.mediaId
            if (stationUuid != null) {
                libraryStatusJob = serviceScope.launch {
                    libraryRepository.isStationInLibrary(stationUuid).collect {
                        autoCallback.updateLibraryButton(stationUuid)
                    }
                }
            } else {
                autoCallback.updateLibraryButton(null)
            }
            
            if (stationUuid == null) return
            
            val tagStation = mediaItem.localConfiguration?.tag as? RadioStation
            if (tagStation != null) {
                serviceScope.launch {
                    recentRepository.addRecentStation(tagStation)
                }
            } else {
                serviceScope.launch {
                    val dbStation = libraryRepository.getStationById(stationUuid)
                    if (dbStation != null) {
                        recentRepository.addRecentStation(dbStation)
                    }
                }
            }
        }

        override fun onMetadata(metadata: androidx.media3.common.Metadata) {
            var rawTrackTitle: String? = null
            var rawArtist: String? = null

            for (i in 0 until metadata.length()) {
                val entry = metadata.get(i)
                if (entry is androidx.media3.extractor.metadata.icy.IcyInfo) {
                    rawTrackTitle = entry.title
                } else if (entry is androidx.media3.extractor.metadata.id3.TextInformationFrame) {
                    if (entry.id == "TIT2" || entry.id == "TT2") {
                        rawTrackTitle = entry.values.firstOrNull()?.toString()
                    } else if (entry.id == "TPE1" || entry.id == "TP1") {
                        rawArtist = entry.values.firstOrNull()?.toString()
                    }
                }
            }

            if (!rawTrackTitle.isNullOrBlank()) {
                val trackName: String
                val artistName: String?
                
                if (rawArtist != null) {
                    trackName = rawTrackTitle
                    artistName = rawArtist
                } else if (rawTrackTitle.contains(" - ")) {
                    val parts = rawTrackTitle.split(" - ", limit = 2)
                    if (parts.size == 2) {
                        artistName = parts[0].trim()
                        trackName = parts[1].trim()
                    } else {
                        artistName = null
                        trackName = rawTrackTitle
                    }
                } else {
                    artistName = null
                    trackName = rawTrackTitle
                }

                val trackTitle = if (artistName != null) {
                    "$trackName - $artistName"
                } else {
                    trackName
                }

                val currentPlayer = player ?: return
                val currentMediaItem = currentPlayer.currentMediaItem ?: return
                
                // Avoid unnecessary updates
                val currentExtras = currentMediaItem.mediaMetadata.extras
                val previousRawTitle = currentExtras?.getString("icy_raw_title")
                
                if (previousRawTitle == trackTitle) return
                
                activeTrackTitle = trackTitle
                
                val stationName = currentExtras?.getString("stationName")
                val stationFaviconStr = currentExtras?.getString("stationFavicon")
                val stationFaviconUri = when {
                    stationFaviconStr?.endsWith(".svg", ignoreCase = true) == true ->
                        android.net.Uri.parse(SvgProxyProvider.createProxyUri(this@PlaybackService, stationFaviconStr))
                    !stationFaviconStr.isNullOrBlank() -> android.net.Uri.parse(stationFaviconStr)
                    else -> android.net.Uri.EMPTY
                }

                val newExtras = android.os.Bundle(currentExtras ?: android.os.Bundle.EMPTY).apply {
                    putString("icy_raw_title", trackTitle)
                    putString("icy_title", trackTitle)
                    putString("is_fetching_artwork", "true")
                    remove("track_cover_art_url") // Clear old cover art for the new track
                    
                    if (previousRawTitle == null) {
                        // First track since tuning in. We do not know when it actually started.
                        putLong("track_start_time", -1L)
                    } else {
                        // Real track change while listening! We know the exact start time.
                        putLong("track_start_time", currentPlayer.currentPosition)
                    }
                }

                val newMetadataBuilder = currentMediaItem.mediaMetadata.buildUpon()
                    .setTitle(trackName)
                    .setArtist(artistName)
                    .setArtworkUri(stationFaviconUri) // Show station thumbnail while fetching track cover art
                    .setExtras(newExtras)
                    
                val newMediaItem = currentMediaItem.buildUpon()
                    .setMediaMetadata(newMetadataBuilder.build())
                    .build()
                    
                // Update metadata without interrupting playback
                currentPlayer.replaceMediaItem(currentPlayer.currentMediaItemIndex, newMediaItem)
                
                // Log the track history
                val stationUuid = currentMediaItem.mediaId
                serviceScope.launch {
                    val trackId = trackHistoryRepository.logTrack(stationUuid, trackTitle)
                    
                    // Fetch track cover art and cleaned metadata
                    val metadata = coverArtRepository.getTrackMetadata(trackName, artistName)
                    
                    // Determine the cleaned title
                    val cleanedTitle = if (metadata != null) {
                        val cTrack = metadata.trackName
                        val cArtist = metadata.artistName
                        when {
                            cTrack != null && cArtist != null -> "$cTrack - $cArtist"
                            cTrack != null -> cTrack
                            else -> trackTitle
                        }
                    } else {
                        trackTitle
                    }

                    if (trackId != null) {
                        trackHistoryRepository.updateTrackMetadata(trackId, cleanedTitle, metadata?.coverArtUrl)
                    }
                    
                    // Ensure track hasn't changed while fetching
                    if (activeTrackTitle == trackTitle) {
                        val updatedExtras = android.os.Bundle(newExtras).apply {
                            putString("is_fetching_artwork", "false")
                            putString("icy_title", cleanedTitle)
                            if (metadata?.trackName != null) putString("clean_track_name", metadata.trackName)
                            if (metadata?.artistName != null) putString("clean_artist_name", metadata.artistName)
                            if (metadata?.coverArtUrl != null) {
                                putString("track_cover_art_url", metadata.coverArtUrl) // Make cover art available to internal UI
                            }
                        }
                        val metadataWithArt = newMetadataBuilder
                            .setArtworkUri(if (showCoverArtInNotification && metadata?.coverArtUrl != null) android.net.Uri.parse(metadata.coverArtUrl) else stationFaviconUri)
                            .setExtras(updatedExtras)
                            .setDescription(System.currentTimeMillis().toString()) // Force ExoPlayer to detect a metadata change
                            .build()
                        val itemWithArt = newMediaItem.buildUpon()
                            .setMediaMetadata(metadataWithArt)
                            .build()
                            
                        player?.let { p ->
                            for (i in 0 until p.mediaItemCount) {
                                if (p.getMediaItemAt(i).mediaId == stationUuid) {
                                    val currentItemAtI = p.getMediaItemAt(i)
                                    val updatedItem = currentItemAtI.buildUpon()
                                        .setMediaMetadata(metadataWithArt)
                                        .build()
                                    p.replaceMediaItem(i, updatedItem)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        loadErrorHandlingPolicy = ExponentialBackoffLoadErrorHandlingPolicy(retryStateTracker)
        
        serviceScope.launch {
            settingsRepository.appPreferencesFlow.collect { prefs ->
                loadErrorHandlingPolicy.maxRetryDurationMs = prefs.maxRetryDuration
                stopOnAudioBecomingNoisy = prefs.stopOnAudioBecomingNoisy
                pauseOnVolumeZero = prefs.pauseOnVolumeZero
                showCoverArtInNotification = prefs.showCoverArtInNotification
                alarmFadeInSeconds = if (prefs.isAlarmVolumeTransitionEnabled) prefs.alarmVolumeTransitionSeconds else 0
            }
        }

        var retryToast: android.widget.Toast? = null
        serviceScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            retryStateTracker.retryToastEvent.collect {
                retryToast?.cancel()
                retryToast = android.widget.Toast.makeText(
                    this@PlaybackService,
                    getString(com.armanmaurya.internetradio.R.string.player_retrying_connection),
                    android.widget.Toast.LENGTH_SHORT
                )
                retryToast?.show()
            }
        }

        // A *network* interceptor fires on every hop including after redirects,
        // so "Icy-MetaData: 1" reaches the final streaming server even when the
        // station URL is a redirect (e.g. ondacero.es → streamtheworld.com).
        // OkHttp strips application-level headers / setDefaultRequestProperties
        // on cross-domain redirects, which is why ICY metadata was missing for
        // stations served through redirect endpoints.
        val streamingOkHttpClient = okHttpClient.newBuilder()
            .addNetworkInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Icy-MetaData", "1")
                    .build()
                chain.proceed(request)
            }
            .build()

        val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(streamingOkHttpClient)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(AmplitudeAudioProcessor(recordingManager)))
                    .build()
            }
        }

        val exoPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(audioAttributes, true)
            .setDeviceVolumeControlEnabled(true)
            .build()
            
        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
        exoPlayer.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            
        registerReceiver(audioNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        player = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands()
                val builder = commands.buildUpon()
                    .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_BACK)
                    .remove(Player.COMMAND_SEEK_FORWARD)

                if (mediaItemCount <= 1) {
                    builder
                        .remove(Player.COMMAND_SEEK_TO_NEXT)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                }
                return builder.build()
            }
            
            override fun isCurrentMediaItemDynamic(): Boolean {
                return true
            }

            override fun isCurrentMediaItemLive(): Boolean {
                return true
            }

            override fun isCurrentMediaItemSeekable(): Boolean {
                return false
            }

            override fun getDuration(): Long {
                return androidx.media3.common.C.TIME_UNSET
            }

            override fun hasNextMediaItem(): Boolean {
                return mediaItemCount > 1 && super.hasNextMediaItem()
            }
            
            override fun hasPreviousMediaItem(): Boolean {
                return mediaItemCount > 1 && super.hasPreviousMediaItem()
            }

            override fun play() {
                // Since stop() removes the notification, we let it pause() normally.
                val item = currentMediaItem
                if (item != null && !playWhenReady) {
                    retryStateTracker.reset()
                    if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                        // The player was paused and likely has a stale buffer or a dead socket.
                        // We call stop() to drop the old connection and buffer, 
                        // then prepare() to connect fresh to the live edge.
                        super.stop()
                        super.prepare()
                    }
                }
                super.play()
            }
            
            override fun pause() {
                retryStateTracker.reset()
                super.pause()
            }
            
            override fun stop() {
                retryStateTracker.reset()
                super.stop()
            }
        }

        player?.let {
            it.addListener(stationChangeListener)
            it.addListener(object : androidx.media3.common.Player.Listener {
                override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                    val isZero = volume == 0 || muted
                    val wasNonZero = previousVolume > 0
                    
                    if (isZero && wasNonZero) {
                        if (ignoreNextVolumeZero) {
                            ignoreNextVolumeZero = false
                        } else if (pauseOnVolumeZero) {
                            player?.pause()
                        }
                    } else if (!isZero) {
                        ignoreNextVolumeZero = false
                    }
                    
                    previousVolume = if (muted) 0 else volume
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateWidget()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    updateWidget()
                }

                override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                    updateWidget()
                }
            })

            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            mediaLibrarySession = MediaLibrarySession.Builder(this, it, autoCallback)
                .setSessionActivity(pendingIntent)
                .setBitmapLoader(CoilBitmapLoader(this))
                .build()

            // Give the callback a reference to the session so it can push
            // custom layout updates (e.g. refreshing the heart icon) at any time
            autoCallback.activeSession = mediaLibrarySession
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        isRunning = false
        // Push a final stopped-state widget update before tearing down
        pushStoppedWidgetUpdate()
        serviceScope.cancel()
        // Clear session ref first so the callback stops pushing updates
        autoCallback.activeSession = null
        try {
            unregisterReceiver(audioNoisyReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        
        mediaLibrarySession?.run {
            player.removeListener(stationChangeListener)
            player.release()
            release()
        }
        mediaLibrarySession = null
        player = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                pushStoppedWidgetUpdate()
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "com.armanmaurya.internetradio.ACTION_PLAY_SCHEDULE" || action == "com.armanmaurya.internetradio.ACTION_PLAY_STATION") {
            // Instantly elevate to foreground to bypass Android 12+ background network restrictions
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channelId = "schedule_channel"
                val nm = getSystemService(android.app.NotificationManager::class.java)
                if (nm.getNotificationChannel(channelId) == null) {
                    val channel = android.app.NotificationChannel(
                        channelId, "Scheduled Playback",
                        android.app.NotificationManager.IMPORTANCE_LOW
                    ).apply { setShowBadge(false) }
                    nm.createNotificationChannel(channel)
                }
                val notification = android.app.Notification.Builder(this, channelId)
                    .setSmallIcon(com.armanmaurya.internetradio.R.drawable.media3_notification_small_icon)
                    .setContentTitle("Connecting to station...")
                    .setOngoing(true)
                    .build()
                startForeground(2001, notification)
            }

            if (action == "com.armanmaurya.internetradio.ACTION_PLAY_SCHEDULE") {
                val scheduleId = intent.getIntExtra(ScheduleReceiver.EXTRA_SCHEDULE_ID, -1)
                if (scheduleId != -1) {
                    serviceScope.launch(Dispatchers.IO) {
                        val schedule = scheduleRepository.getScheduleById(scheduleId)
                        if (schedule == null) {
                            if (player?.playbackState != Player.STATE_READY) {
                                stopForeground(true)
                                stopSelf()
                            }
                            return@launch
                        }
                        val libraryStation = libraryRepository.getStationById(schedule.stationUuid)
                        val prefs = settingsRepository.appPreferencesFlow.first()
                        
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            startStationPlayback(
                                stationUuid = schedule.stationUuid,
                                stationUrl = libraryStation?.urlResolved ?: libraryStation?.url ?: "",
                                stationName = schedule.stationName,
                                stationFavicon = libraryStation?.favicon ?: "",
                                volumeLevel = schedule.volumeLevel,
                                transitionSeconds = if (prefs.isAlarmVolumeTransitionEnabled) prefs.alarmVolumeTransitionSeconds else 0
                            )
                        }
                    }
                }
                return super.onStartCommand(intent, flags, startId)
            }

            val stationUuid = intent.getStringExtra("STATION_UUID")
            val stationUrl = intent.getStringExtra("STATION_URL")
            val stationName = intent.getStringExtra("STATION_NAME") ?: ""
            val stationFavicon = intent.getStringExtra("STATION_FAVICON") ?: ""
            val volumeLevel = intent.getFloatExtra("VOLUME_LEVEL", -1f)
            val transitionSeconds = intent.getIntExtra("ALARM_TRANSITION_SECONDS", alarmFadeInSeconds)

            if (stationUuid != null && !stationUrl.isNullOrBlank()) {
                startStationPlayback(
                    stationUuid, stationUrl, stationName, stationFavicon,
                    volumeLevel, transitionSeconds
                )
            }
        } else if (action == "com.armanmaurya.internetradio.ACTION_STOP_PLAYBACK") {
            volumeFadeJob?.cancel()
            player?.volume = 1f
            player?.stop()
        } else if (action == "com.armanmaurya.internetradio.ACTION_WIDGET_PLAY_PAUSE") {
            val p = player
            if (p != null) {
                when {
                    p.isPlaying || (p.playbackState == Player.STATE_BUFFERING && p.playWhenReady) -> {
                        p.pause()
                    }
                    p.mediaItemCount == 0 -> {
                        // Cold boot: app was killed. Restore last played station with full playlist context.
                        serviceScope.launch {
                            restoreAndPlayLastStation()
                        }
                    }
                    else -> {
                        if (p.playbackState == Player.STATE_IDLE) p.prepare()
                        p.play()
                    }
                }
            }
        } else if (action == "com.armanmaurya.internetradio.ACTION_WIDGET_NEXT") {
            player?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem()
        } else if (action == "com.armanmaurya.internetradio.ACTION_WIDGET_PREVIOUS") {
            player?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem()
        } else if (action == "com.armanmaurya.internetradio.ACTION_WIDGET_UPDATE") {
            updateWidget()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startStationPlayback(
        stationUuid: String,
        stationUrl: String,
        stationName: String,
        stationFavicon: String,
        volumeLevel: Float,
        transitionSeconds: Int
    ) {
        val artworkUri = when {
            stationFavicon.endsWith(".svg", ignoreCase = true) ->
                android.net.Uri.parse(SvgProxyProvider.createProxyUri(this, stationFavicon))
            stationFavicon.isNotBlank() -> android.net.Uri.parse(stationFavicon)
            else -> android.net.Uri.EMPTY
        }
        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setMediaId(stationUuid)
            .setUri(stationUrl)
            .setLiveConfiguration(androidx.media3.common.MediaItem.LiveConfiguration.Builder().build())
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(stationName)
                    .setArtworkUri(artworkUri)
                    .setExtras(android.os.Bundle().apply {
                        putString("stationName", stationName)
                        putString("stationFavicon", stationFavicon)
                    })
                    .build()
            )
            .build()

        val isSameStation = player?.currentMediaItem?.mediaId == stationUuid
        volumeFadeJob?.cancel()

        val applySystemVolume = {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            val targetVolume = (volumeLevel * maxVolume).toInt()
            if (targetVolume == 0) ignoreNextVolumeZero = true
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
        }

        if (volumeLevel >= 0f) {
            if (isSameStation && player?.playbackState == androidx.media3.common.Player.STATE_READY) {
                applySystemVolume()
            } else {
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == androidx.media3.common.Player.STATE_READY) {
                            applySystemVolume()
                            player?.removeListener(this)
                        }
                    }
                }
                player?.addListener(listener)
            }
            if (transitionSeconds > 0) {
                player?.volume = 0f
                volumeFadeJob = serviceScope.launch {
                    val steps = transitionSeconds * 10
                    val volumeStep = 1.0f / steps
                    for (i in 1..steps) {
                        kotlinx.coroutines.delay(100)
                        player?.volume = (volumeStep * i).coerceIn(0f, 1f)
                    }
                    player?.volume = 1.0f
                }
            } else {
                player?.volume = 1f
            }
        } else {
            player?.volume = 1f
        }

        player?.playWhenReady = true
        if (!isSameStation) {
            player?.setMediaItem(mediaItem)
            player?.prepare()
        } else if (player?.playbackState == androidx.media3.common.Player.STATE_IDLE || player?.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            player?.prepare()
        }
    }

    private fun updateWidget() {
        val widgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val widgetComponent = android.content.ComponentName(this, com.armanmaurya.internetradio.widget.PlayerWidgetProvider::class.java)
        val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)
        if (widgetIds.isNotEmpty()) {
            val p = player
            val trackTitle = p?.mediaMetadata?.title?.toString()
            val stationName = p?.currentMediaItem?.mediaMetadata?.extras?.getString("stationName")
            val artworkUri = p?.mediaMetadata?.artworkUri?.toString()
            
            com.armanmaurya.internetradio.widget.PlayerWidgetProvider.updateWidgets(
                context = this,
                appWidgetManager = widgetManager,
                appWidgetIds = widgetIds,
                player = p,
                trackTitle = trackTitle,
                stationName = stationName,
                artworkUri = artworkUri
            )
        }
    }

    /**
     * Pushes a final "stopped" widget update from the recent DB.
     * Called when the service is about to be destroyed so the widget
     * shows the correct station name/art with a Play button instead of freezing.
     * Uses a new independent scope since serviceScope may already be cancelled.
     */
    private fun pushStoppedWidgetUpdate() {
        val widgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val widgetComponent = android.content.ComponentName(this, com.armanmaurya.internetradio.widget.PlayerWidgetProvider::class.java)
        val widgetIds = widgetManager.getAppWidgetIds(widgetComponent)
        if (widgetIds.isEmpty()) return

        // Use a fresh scope since serviceScope may have been cancelled already
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val lastStation = recentRepository.getAllRecent().first().firstOrNull()
            com.armanmaurya.internetradio.widget.PlayerWidgetProvider.updateWidgets(
                context = this@PlaybackService,
                appWidgetManager = widgetManager,
                appWidgetIds = widgetIds,
                player = null,                    // null → isPlaying=false → shows Play icon
                trackTitle = lastStation?.name,   // Clean station name from DB, not live stream title
                stationName = lastStation?.name,
                artworkUri = lastStation?.favicon
            )
        }
    }

    /**
     * Restores the last played station with a full playlist context, mirroring
     * the autoPlayOnStart strategy used by PlayerController:
     * 1. Check if the station is in the library → load full library as playlist
     * 2. Fall back to the full recents list as playlist
     */
    private suspend fun restoreAndPlayLastStation() {
        val p = player ?: return
        val lastStation = recentRepository.getAllRecent().first().firstOrNull() ?: run {
            stopSelf()
            return
        }

        val libraryStations = libraryRepository.getAllStations().first()
        val libraryIndex = libraryStations.indexOfFirst { it.stationUuid == lastStation.stationUuid }

        val mediaItems: List<androidx.media3.common.MediaItem>
        val startIndex: Int

        if (libraryIndex != -1) {
            // Found in library: load the full library as playlist
            mediaItems = libraryStations.map { station ->
                buildMediaItem(station)
            }
            startIndex = libraryIndex
        } else {
            // Not in library: fall back to full recents list
            val recentStations = recentRepository.getAllRecent().first()
            val recentIndex = recentStations.indexOfFirst { it.stationUuid == lastStation.stationUuid }.coerceAtLeast(0)
            mediaItems = recentStations.map { station ->
                buildMediaItem(station)
            }
            startIndex = recentIndex
        }

        p.volume = 1f
        p.setMediaItems(mediaItems, startIndex, 0L)
        p.playWhenReady = true
        p.prepare()
    }

    /**
     * Builds a MediaItem from a RadioStation, handling SVG artwork proxying.
     */
    private fun buildMediaItem(station: com.armanmaurya.internetradio.data.model.RadioStation): androidx.media3.common.MediaItem {
        val artworkUri = when {
            station.favicon.endsWith(".svg", ignoreCase = true) ->
                android.net.Uri.parse(SvgProxyProvider.createProxyUri(this, station.favicon))
            station.favicon.isNotBlank() -> android.net.Uri.parse(station.favicon)
            else -> android.net.Uri.EMPTY
        }
        return androidx.media3.common.MediaItem.Builder()
            .setMediaId(station.stationUuid)
            .setUri(station.urlResolved.ifBlank { station.url })
            .setLiveConfiguration(androidx.media3.common.MediaItem.LiveConfiguration.Builder().build())
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(station.name)
                    .setArtworkUri(artworkUri)
                    .setExtras(android.os.Bundle().apply {
                        putString("stationName", station.name)
                        putString("stationFavicon", station.favicon)
                    })
                    .build()
            )
            .build()
    }
}

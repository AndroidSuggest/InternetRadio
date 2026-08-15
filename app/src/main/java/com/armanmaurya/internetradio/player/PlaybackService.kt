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
import androidx.media3.datasource.DefaultHttpDataSource
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
    lateinit var settingsRepository: com.armanmaurya.internetradio.data.repository.SettingsRepository

    @Inject
    lateinit var libraryRepository: com.armanmaurya.internetradio.data.repository.LibraryRepository

    @Inject
    lateinit var recentRepository: com.armanmaurya.internetradio.data.repository.RecentRepository

    @Inject
    lateinit var coverArtRepository: com.armanmaurya.internetradio.data.repository.CoverArtRepository

    private var player: Player? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var loadErrorHandlingPolicy: ExponentialBackoffLoadErrorHandlingPolicy
    
    private var stopOnAudioBecomingNoisy: Boolean = true
    private var showCoverArtInNotification: Boolean = true
    private var alarmFadeInSeconds: Int = 0
    private var volumeFadeJob: kotlinx.coroutines.Job? = null
    
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

    /**
     * Watches for station changes so the ❤️ button on Android Auto's now-playing
     * screen always shows the correct filled / outline state.
     */
    private val stationChangeListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            autoCallback.updateLibraryButton(mediaItem?.mediaId)
            
            val stationUuid = mediaItem?.mediaId ?: return
            
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
                    trackHistoryRepository.logTrack(stationUuid, trackTitle)
                    
                    // Fetch track cover art
                    val coverArtUrl = coverArtRepository.getCoverArt(trackName, artistName)
                    if (coverArtUrl != null) {
                        android.util.Log.d("PlaybackService", "Found cover art: \$coverArtUrl")
                        trackHistoryRepository.updateCoverArt(stationUuid, trackTitle, coverArtUrl)
                    }
                    
                    val latestPlayer = player ?: return@launch
                    val latestMediaItem = latestPlayer.currentMediaItem ?: return@launch
                    val latestRawTitle = latestMediaItem.mediaMetadata.extras?.getString("icy_raw_title")
                    
                    // Ensure track hasn't changed while fetching
                    if (latestRawTitle == trackTitle) {
                        val latestExtras = latestMediaItem.mediaMetadata.extras
                        val updatedExtras = android.os.Bundle(latestExtras ?: android.os.Bundle.EMPTY).apply {
                            putString("is_fetching_artwork", "false")
                            if (coverArtUrl != null) {
                                putString("track_cover_art_url", coverArtUrl) // Make cover art available to internal UI
                            }
                        }
                        val metadataWithArt = latestMediaItem.mediaMetadata.buildUpon()
                            .setArtworkUri(if (showCoverArtInNotification && coverArtUrl != null) android.net.Uri.parse(coverArtUrl) else stationFaviconUri)
                            .setExtras(updatedExtras)
                            .build()
                        val itemWithArt = latestMediaItem.buildUpon()
                            .setMediaMetadata(metadataWithArt)
                            .build()
                        latestPlayer.replaceMediaItem(latestPlayer.currentMediaItemIndex, itemWithArt)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        loadErrorHandlingPolicy = ExponentialBackoffLoadErrorHandlingPolicy(retryStateTracker)
        
        serviceScope.launch {
            settingsRepository.appPreferencesFlow.collect { prefs ->
                loadErrorHandlingPolicy.maxRetryDurationMs = prefs.maxRetryDuration
                stopOnAudioBecomingNoisy = prefs.stopOnAudioBecomingNoisy
                showCoverArtInNotification = prefs.showCoverArtInNotification
                alarmFadeInSeconds = prefs.alarmVolumeTransitionSeconds
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

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))

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
            .build()
            
        exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
            
        registerReceiver(audioNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        player = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                val commands = super.getAvailableCommands()
                if (mediaItemCount <= 1) {
                    return commands.buildUpon()
                        .remove(Player.COMMAND_SEEK_TO_NEXT)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .build()
                }
                return commands
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
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    recordingManager.setPlaying(isPlaying)
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
        serviceScope.cancel()
        // Clear session ref first so the callback stops pushing updates
        autoCallback.activeSession = null
        try {
            unregisterReceiver(audioNoisyReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        
        recordingManager.stopRecording()
        
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
                stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == "com.armanmaurya.internetradio.ACTION_PLAY_STATION") {
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

            val stationUuid = intent.getStringExtra("STATION_UUID")
            val startRecording = intent.getBooleanExtra("START_RECORDING", false)
            val durationMinutes = intent.getIntExtra("RECORDING_DURATION", 0)
            val keepPlayback = intent.getBooleanExtra("KEEP_PLAYBACK", false)
            val stationUrl = intent.getStringExtra("STATION_URL")
            val stationName = intent.getStringExtra("STATION_NAME") ?: ""
            val stationFavicon = intent.getStringExtra("STATION_FAVICON") ?: ""
            val volumeLevel = intent.getFloatExtra("VOLUME_LEVEL", -1f)

            if (stationUuid != null && !stationUrl.isNullOrBlank()) {
                // --- SYNCHRONOUS playback setup (no coroutine, no race) ---
                // Build MediaItem directly from intent extras so playback is set up
                // before PlayerController's MediaController can connect and interfere.
                val artworkUri = when {
                    stationFavicon.endsWith(".svg", ignoreCase = true) ->
                        android.net.Uri.parse(SvgProxyProvider.createProxyUri(this, stationFavicon))
                    stationFavicon.isNotBlank() -> android.net.Uri.parse(stationFavicon)
                    else -> android.net.Uri.EMPTY
                }
                val mediaItem = androidx.media3.common.MediaItem.Builder()
                    .setMediaId(stationUuid)
                    .setUri(stationUrl)
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

                // Set playWhenReady=true BEFORE setMediaItem so ExoPlayer auto-starts
                // on STATE_READY. This runs synchronously before any coroutine can run.
                // We must change the system volume ONLY after the player requests audio focus 
                // and the foreground service is fully registered, otherwise Android ignores it 
                // for fully closed background apps.
                volumeFadeJob?.cancel()
                if (volumeLevel >= 0f) {
                    val listener = object : androidx.media3.common.Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == androidx.media3.common.Player.STATE_READY) {
                                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                val targetVolume = (volumeLevel * maxVolume).toInt()
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                                player?.removeListener(this)
                            }
                        }
                    }
                    player?.addListener(listener)
                    
                    val transitionSeconds = intent.getIntExtra("ALARM_TRANSITION_SECONDS", alarmFadeInSeconds)
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
                player?.setMediaItem(mediaItem)
                player?.prepare()

                // Schedule stop alarm synchronously (AlarmManager is not async)
                if (durationMinutes > 0) {
                    val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                    val stopIntent = Intent(this, ScheduleReceiver::class.java).apply {
                        this.action = ScheduleReceiver.ACTION_STOP_RECORDING
                        putExtra("KEEP_PLAYBACK", keepPlayback)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        this,
                        stationUuid.hashCode(),
                        stopIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val stopAt = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
                    val alarmClockInfo = android.app.AlarmManager.AlarmClockInfo(stopAt, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                }

                // Recording still needs the full station object — keep async
                if (startRecording) {
                    serviceScope.launch {
                        val station = libraryRepository.getStationById(stationUuid) ?: return@launch
                        recordingManager.startRecording(station)
                    }
                }
            }
        } else if (action == "com.armanmaurya.internetradio.ACTION_STOP_PLAYBACK") {
            volumeFadeJob?.cancel()
            player?.volume = 1f
            player?.stop()
            recordingManager.stopRecording()
        }
        return super.onStartCommand(intent, flags, startId)
    }
}

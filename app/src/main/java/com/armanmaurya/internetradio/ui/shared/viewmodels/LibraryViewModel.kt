package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.RadioStation
import com.armanmaurya.internetradio.data.repository.LibraryRepository
import com.armanmaurya.internetradio.data.repository.SettingsRepository
import com.armanmaurya.internetradio.data.repository.StationRepository
import com.armanmaurya.internetradio.player.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val stationRepository: StationRepository,
    private val playerController: PlayerController,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    // Using useFilterOnFavorites and isGridViewFavorites for now, maybe we can rename these in Settings later
    val useFilter: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.useFilterOnFavorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isGridView: StateFlow<Boolean> = settingsRepository.appPreferencesFlow
        .map { it.isGridViewFavorites }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val sortOption: StateFlow<com.armanmaurya.internetradio.data.model.LibrarySortOption> = settingsRepository.appPreferencesFlow
        .map { it.librarySortOption }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.armanmaurya.internetradio.data.model.LibrarySortOption.RECENTLY_ADDED)

    fun setSortOption(option: com.armanmaurya.internetradio.data.model.LibrarySortOption) {
        viewModelScope.launch {
            settingsRepository.setLibrarySortOption(option)
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    private val _tagSearchQuery = MutableStateFlow("")
    
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    val fetchedTags: StateFlow<List<com.armanmaurya.internetradio.data.model.Tag>> = _tagSearchQuery
        .debounce(500)
        .flatMapLatest { query ->
            flow {
                if (query.isNotBlank()) {
                    stationRepository.getTags(query)
                        .onSuccess { emit(it.take(10)) }
                        .onFailure { emit(emptyList()) }
                } else {
                    emit(emptyList())
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onTagSearchQueryChange(query: String) {
        _tagSearchQuery.value = query
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val sortedStationsFlow = settingsRepository.appPreferencesFlow
        .map { it.librarySortOption }
        .distinctUntilChanged()
        .flatMapLatest { sortOpt ->
            when (sortOpt) {
                com.armanmaurya.internetradio.data.model.LibrarySortOption.NAME_A_Z -> libraryRepository.getStationsByName()
                com.armanmaurya.internetradio.data.model.LibrarySortOption.NAME_Z_A -> libraryRepository.getStationsByNameDescending()
                com.armanmaurya.internetradio.data.model.LibrarySortOption.RECENTLY_PLAYED -> libraryRepository.getStationsByRecentlyPlayed()
                com.armanmaurya.internetradio.data.model.LibrarySortOption.LEAST_RECENTLY_PLAYED -> libraryRepository.getStationsByLeastRecentlyPlayed()
                com.armanmaurya.internetradio.data.model.LibrarySortOption.CUSTOM -> libraryRepository.getStationsByCustomOrder()
                com.armanmaurya.internetradio.data.model.LibrarySortOption.RECENTLY_ADDED -> libraryRepository.getAllStations()
                com.armanmaurya.internetradio.data.model.LibrarySortOption.OLDEST_ADDED -> libraryRepository.getStationsByOldestAdded()
            }
        }

    private fun filterStations(
        stationsList: List<RadioStation>,
        preferences: com.armanmaurya.internetradio.data.model.AppPreferences,
        query: String
    ): List<RadioStation> {
        val hasQuery = query.isNotBlank()
        val hasCountryFilter = !preferences.selectedCountryCode.isNullOrBlank()
        val hasStateFilter = !preferences.selectedStateCode.isNullOrBlank()
        val hasLanguageFilter = !preferences.selectedLanguage.isNullOrBlank()
        val hasTagFilter = preferences.selectedTags.isNotEmpty()

        if (!hasQuery && !hasCountryFilter && !hasStateFilter && !hasLanguageFilter && !hasTagFilter) {
            return stationsList
        }

        return stationsList.filter { station ->
            val queryMatch = !hasQuery ||
                    station.name.contains(query, ignoreCase = true) ||
                    station.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            val countryMatch = !hasCountryFilter ||
                    station.countryCode == preferences.selectedCountryCode
            val stateMatch = !hasStateFilter ||
                    station.iso3166_2 == preferences.selectedStateCode
            val languageMatch = if (!hasLanguageFilter) true else {
                val selectedCode = preferences.selectedLanguage!!
                station.languageCodes.contains(selectedCode)
            }
            val tagsMatch = !hasTagFilter ||
                    preferences.selectedTags.any { it in station.tags }

            queryMatch && countryMatch && stateMatch && languageMatch && tagsMatch
        }
    }

    val stations: StateFlow<List<RadioStation>?> = combine(
        sortedStationsFlow,
        settingsRepository.appPreferencesFlow,
        _searchQuery
    ) { stationsList, preferences, query ->
        if (preferences.useFilterOnFavorites) {
            filterStations(stationsList, preferences, query)
        } else {
            stationsList
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val searchStations: StateFlow<List<RadioStation>?> = combine(
        sortedStationsFlow,
        settingsRepository.appPreferencesFlow,
        _searchQuery
    ) { stationsList, preferences, query ->
        filterStations(stationsList, preferences, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Set of all bookmarked UUIDs — used by Browse/Recent to show the bookmark badge
    val stationUuids: StateFlow<Set<String>> = libraryRepository.getAllStations()
        .map { list -> list.map { it.stationUuid }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleFilter() {
        viewModelScope.launch {
            settingsRepository.setUseFilterOnFavorites(!useFilter.value)
        }
    }

    fun setFilterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseFilterOnFavorites(enabled)
        }
    }

    fun onGridViewChange(isGrid: Boolean) {
        viewModelScope.launch { settingsRepository.setGridViewFavorites(isGrid) }
    }

    fun isStationInLibrary(stationUuid: String) =
        libraryRepository.isStationInLibrary(stationUuid)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun addStationToLibrary(station: RadioStation) {
        viewModelScope.launch {
            libraryRepository.addStationToLibrary(station)
        }
    }

    fun removeStation(stationUuid: String) {
        viewModelScope.launch {
            libraryRepository.removeStationFromLibrary(stationUuid)
        }
    }

    fun updateStation(
        stationUuid: String,
        name: String,
        url: String,
        favicon: String,
        tags: List<String>,
        countryCode: String,
        languageCodes: List<String>,
        homepage: String,
        iso31662: String? = null,
        codec: String,
        bitrate: Int
    ) {
        viewModelScope.launch {
            libraryRepository.updateStation(
                stationUuid = stationUuid,
                name = name,
                url = url,
                favicon = favicon,
                tags = tags,
                countryCode = countryCode,
                languageCodes = languageCodes,
                homepage = homepage,
                iso31662 = iso31662,
                codec = codec,
                bitrate = bitrate
            )
            val updatedStation = libraryRepository.getStationById(stationUuid)
            if (updatedStation != null && playerController.playbackState.value.currentStation?.stationUuid == stationUuid) {
                playerController.updateCurrentStation(updatedStation)
            }
        }
    }

    fun fetchOriginalStation(stationUuid: String, onResult: (RadioStation?) -> Unit) {
        viewModelScope.launch {
            stationRepository.getStationsByUuid(listOf(stationUuid))
                .onSuccess { stations ->
                    onResult(stations.firstOrNull())
                }
                .onFailure {
                    onResult(null)
                }
        }
    }

    data class StreamProbeResult(
        val codec: String, 
        val bitrate: Int,
        val name: String?,
        val description: String?,
        val genre: String?,
        val homepage: String?
    )

    suspend fun probeStream(url: String): StreamProbeResult? = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext null
        var detectedCodec = ""
        var detectedBitrate = 0
        var isHls = url.contains(".m3u8")
        
        var icyName: String? = null
        var icyDescription: String? = null
        var icyGenre: String? = null
        var icyUrl: String? = null
        
        try {
            val request = okhttp3.Request.Builder().url(url).header("Icy-MetaData", "1").build()
            okHttpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                detectedBitrate = response.header("icy-br")?.toIntOrNull() ?: 0
                icyName = response.header("icy-name")
                icyDescription = response.header("icy-description")
                icyGenre = response.header("icy-genre")
                icyUrl = response.header("icy-url")
                
                if (contentType.contains("mpegurl") || contentType.contains("x-mpegurl")) isHls = true
                
                detectedCodec = when {
                    isHls -> ""
                    contentType.contains("flac") -> "FLAC"
                    contentType.contains("mpeg") -> "MP3"
                    contentType.contains("aacp") || contentType.contains("aac+") -> "AAC+"
                    contentType.contains("aac") -> "AAC"
                    contentType.contains("ogg") -> "OGG"
                    else -> ""
                }

                if (!isHls && response.isSuccessful) {
                    try {
                        val bodyBytes = response.peekBody(2048).bytes()
                        val bodyString = String(bodyBytes, Charsets.ISO_8859_1)
                        
                        if (bodyString.contains("\u007FFLAC") || bodyString.contains("fLaC")) {
                            detectedCodec = "FLAC"
                        } else if (bodyString.contains("OpusHead")) {
                            detectedCodec = "OPUS"
                        } else if (bodyString.contains("\u0001vorbis")) {
                            detectedCodec = "VORBIS"
                        } else if (bodyString.contains("OggS")) {
                            if (detectedCodec == "") detectedCodec = "OGG"
                        } else {
                            var foundSync = false
                            for (i in 0 until bodyBytes.size - 1) {
                                val b1 = bodyBytes[i].toInt() and 0xFF
                                val b2 = bodyBytes[i + 1].toInt() and 0xFF
                                if (b1 == 0xFF && (b2 and 0xE0) == 0xE0) {
                                    if ((b2 and 0xF6) == 0xF0) {
                                        if (detectedCodec != "AAC+") {
                                            detectedCodec = "AAC"
                                        }
                                        foundSync = true
                                        break
                                    } else if ((b2 and 0x06) == 0x02 || (b2 and 0x06) == 0x04) {
                                        detectedCodec = "MP3"
                                        foundSync = true
                                        break
                                    }
                                }
                            }
                            if (!foundSync && bodyString.startsWith("ID3") && detectedCodec == "") {
                                detectedCodec = "MP3"
                            }
                        }
                    } catch (e: Exception) {
                        // ignore and use content-type detection
                    }
                }

                if (isHls && contentType.contains("mpegurl") && response.isSuccessful) {
                    val bodyString = response.peekBody(10240).string()
                    val bandwidthMatch = Regex("BANDWIDTH=(\\d+)").find(bodyString)
                    if (bandwidthMatch != null && detectedBitrate == 0) {
                        detectedBitrate = (bandwidthMatch.groupValues[1].toIntOrNull() ?: 0) / 1000
                    }
                    val codecMatch = Regex("CODECS=\"([^\"]+)\"").find(bodyString)
                    if (codecMatch != null) {
                        val codecStr = codecMatch.groupValues[1].lowercase()
                        detectedCodec = when {
                            codecStr.contains("mp4a") -> "AAC"
                            codecStr.contains("mp3") -> "MP3"
                            else -> detectedCodec
                        }
                    }
                }
            }
            StreamProbeResult(detectedCodec, detectedBitrate, icyName, icyDescription, icyGenre, icyUrl)
        } catch (e: Exception) {
            null
        }
    }

    fun addStation(
        name: String,
        url: String,
        favicon: String,
        tags: String,
        countryCode: String,
        languageCodes: String,
        homepage: String,
        iso31662: String? = null,
        codec: String = "unknown",
        bitrate: Int = 0
    ) {
        val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val langList = languageCodes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        viewModelScope.launch {
            libraryRepository.addCustomStation(
                name = name,
                url = url,
                favicon = favicon,
                tags = tagList,
                countryCode = countryCode,
                languageCodes = langList,
                homepage = homepage,
                iso31662 = iso31662,
                codec = codec,
                bitrate = bitrate
            )
        }
    }

    fun uploadStationToRadioBrowser(
        stationUuid: String,
        name: String,
        url: String,
        homepage: String,
        favicon: String,
        countryCode: String,
        iso31662: String? = null,
        languageCodes: List<String>,
        tags: List<String>,
        codec: String,
        bitrate: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = if (stationUuid.isEmpty()) {
                libraryRepository.uploadAndSaveNewStation(
                    name = name,
                    url = url,
                    homepage = homepage,
                    favicon = favicon,
                    countryCode = countryCode,
                    iso31662 = iso31662,
                    languageCodes = languageCodes,
                    tags = tags,
                    codec = codec,
                    bitrate = bitrate
                )
            } else {
                libraryRepository.uploadExistingCustomStation(
                    stationUuid = stationUuid,
                    name = name,
                    url = url,
                    homepage = homepage,
                    favicon = favicon,
                    countryCode = countryCode,
                    iso31662 = iso31662,
                    languageCodes = languageCodes,
                    tags = tags,
                    codec = codec,
                    bitrate = bitrate
                )
            }
            result.onSuccess { newUuid ->
                // Update player state if the uploaded station is currently playing
                val currentPlayingId = playerController.playbackState.value.currentStation?.stationUuid
                if (currentPlayingId == stationUuid || currentPlayingId == newUuid) {
                    val updatedStation = libraryRepository.getStationById(newUuid)
                    if (updatedStation != null) {
                        playerController.updateCurrentStation(updatedStation, oldUuid = stationUuid)
                    }
                }
                onSuccess()
            }.onFailure { e ->
                onError(e.message ?: "Failed to upload station")
            }
        }
    }

    fun updateStationsOrder(orderedStations: List<RadioStation>) {
        viewModelScope.launch {
            val entities = libraryRepository.getAllStationEntities()
            val updatedEntities = orderedStations.mapIndexedNotNull { index, station ->
                entities.find { it.stationUuid == station.stationUuid }?.copy(orderIndex = index)
            }
            libraryRepository.updateStations(updatedEntities)
        }
    }

    // --- Similar Stations by URL ---
    private val _duplicateStations = MutableStateFlow<List<RadioStation>>(emptyList())
    val duplicateStations = _duplicateStations.asStateFlow()

    private val _isCheckingUrl = MutableStateFlow(false)
    val isCheckingUrl = _isCheckingUrl.asStateFlow()

    private var urlCheckJob: Job? = null

    fun checkDuplicateUrl(url: String) {
        urlCheckJob?.cancel()
        if (url.isBlank()) {
            _duplicateStations.value = emptyList()
            _isCheckingUrl.value = false
            return
        }
        urlCheckJob = viewModelScope.launch {
            delay(500)
            _isCheckingUrl.value = true
            stationRepository.getStationsByUrl(url)
                .onSuccess { _duplicateStations.value = it }
                .onFailure { _duplicateStations.value = emptyList() }
            _isCheckingUrl.value = false
        }
    }
}
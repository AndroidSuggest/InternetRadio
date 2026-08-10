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
    val stations: StateFlow<List<RadioStation>?> = combine(
        settingsRepository.appPreferencesFlow
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
            },
        settingsRepository.appPreferencesFlow,
        _searchQuery
    ) { stationsList: List<RadioStation>, preferences: com.armanmaurya.internetradio.data.model.AppPreferences, query: String ->
        if (preferences.useFilterOnFavorites) {
            val hasQuery = query.isNotBlank()
            val hasCountryFilter = !preferences.selectedCountryCode.isNullOrBlank()
            val hasLanguageFilter = !preferences.selectedLanguage.isNullOrBlank()
            val hasTagFilter = preferences.selectedTags.isNotEmpty()

            // If no filter criteria are set at all, show everything
            if (!hasQuery && !hasCountryFilter && !hasLanguageFilter && !hasTagFilter) {
                stationsList
            } else {
                stationsList.filter { station ->
                    val queryMatch = !hasQuery ||
                            station.name.contains(query, ignoreCase = true) ||
                            station.tags.any { tag -> tag.contains(query, ignoreCase = true) }
                    val countryMatch = !hasCountryFilter ||
                            station.countryCode == preferences.selectedCountryCode
                    val languageMatch = !hasLanguageFilter ||
                            station.language == preferences.selectedLanguage
                    val tagsMatch = !hasTagFilter ||
                            preferences.selectedTags.any { it in station.tags }

                    queryMatch && countryMatch && languageMatch && tagsMatch
                }
            }
        } else {
            stationsList
        }
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

    data class StreamProbeResult(val codec: String, val bitrate: Int)

    suspend fun probeStream(url: String): StreamProbeResult? = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext null
        var detectedCodec = "unknown"
        var detectedBitrate = 0
        var isHls = url.contains(".m3u8")
        
        try {
            val request = okhttp3.Request.Builder().url(url).header("Icy-MetaData", "1").build()
            okHttpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                detectedBitrate = response.header("icy-br")?.toIntOrNull() ?: 0
                if (contentType.contains("mpegurl") || contentType.contains("x-mpegurl")) isHls = true
                
                detectedCodec = when {
                    contentType.contains("mpeg") -> "MP3"
                    contentType.contains("aac") -> "AAC"
                    contentType.contains("ogg") -> "OGG"
                    else -> "unknown"
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
            StreamProbeResult(detectedCodec, detectedBitrate)
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
                    languageCodes = languageCodes,
                    tags = tags,
                    codec = codec,
                    bitrate = bitrate
                )
            }
            result.onSuccess {
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
}
package com.armanmaurya.internetradio.ui.shared.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armanmaurya.internetradio.data.model.Language
import com.armanmaurya.internetradio.data.repository.StationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.neovisionaries.i18n.LanguageAlpha3Code

data class LanguageSelectUiState(
    val languages: List<Language> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

@HiltViewModel
class LanguageSelectViewModel @Inject constructor(
    private val repository: StationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanguageSelectUiState())
    val uiState: StateFlow<LanguageSelectUiState> = _uiState.asStateFlow()

    init {
        loadLanguages()
    }

    private fun loadLanguages() {
        val localLanguages = LanguageAlpha3Code.values().mapNotNull { langCode ->
            val iso3B = langCode.alpha3B?.name
            if (iso3B != null) {
                Language(
                    name = langCode.getName(),
                    isoCode = iso3B,
                    stationCount = 0
                )
            } else null
        }.filter { it.name.isNotBlank() }.sortedBy { it.name }.distinctBy { it.name }
        
        _uiState.update { it.copy(languages = localLanguages, isLoading = false, error = null) }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch() {
        _uiState.update { 
            val newActive = !it.isSearchActive
            it.copy(
                isSearchActive = newActive,
                searchQuery = if (newActive) it.searchQuery else ""
            )
        }
    }
}

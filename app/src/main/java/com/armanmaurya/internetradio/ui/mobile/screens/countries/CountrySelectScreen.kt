package com.armanmaurya.internetradio.ui.mobile.screens.countries


import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.Country
import com.armanmaurya.internetradio.ui.mobile.screens.countries.components.CountryItem
import com.armanmaurya.internetradio.ui.shared.viewmodels.CountrySelectViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountrySelectScreen(
    onCountrySelected: (Country, String?) -> Unit,
    onBackClick: () -> Unit,
    selectedCountryCode: String? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: CountrySelectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedCountryForStates by remember { mutableStateOf<Country?>(null) }
    
    val filteredCountries = remember(uiState.countries, uiState.searchQuery) {
        if (uiState.searchQuery.isBlank()) {
            uiState.countries
        } else {
            uiState.countries.filter { 
                it.name.contains(uiState.searchQuery, ignoreCase = true) ||
                it.isoCode.contains(uiState.searchQuery, ignoreCase = true)
            }
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isSearchActive) {
        if (uiState.isSearchActive) {
            focusRequester.requestFocus()
        }
    }

    val totalStations = remember(uiState.countries) {
        uiState.countries.sumOf { it.stationCount }
    }
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(
                        targetState = uiState.isSearchActive,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "TitleSearchTransition"
                    ) { isSearch ->
                        if (isSearch) {
                            TextField(
                                value = uiState.searchQuery,
                                onValueChange = viewModel::onSearchQueryChange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                placeholder = { Text(stringResource(R.string.select_country_search)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                )
                            )
                        } else {
                            Text(stringResource(R.string.select_country_title))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isSearchActive) {
                            viewModel.toggleSearch()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (!uiState.isSearchActive && !selectedCountryCode.isNullOrBlank()) {
                        TextButton(onClick = { onCountrySelected(Country(name = context.getString(R.string.select_country_all), isoCode = "", stationCount = totalStations), null) }) {
                            Text(stringResource(R.string.general_clear))
                        }
                    }
                    IconButton(onClick = viewModel::toggleSearch) {
                        Icon(
                            imageVector = if (uiState.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (uiState.isSearchActive) stringResource(R.string.cd_close_search) else stringResource(R.string.cd_search)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.error ?: stringResource(R.string.error_unknown))
                }
            } else {
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = remember {
                        val index = filteredCountries.indexOfFirst { it.isoCode == selectedCountryCode }
                        if (index >= 0) index + 1 else 0
                    }
                )

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        bottom = androidx.compose.ui.unit.max(contentPadding.calculateBottomPadding(), WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
                    )
                ) {
                    item {
                        CountryItem(
                            country = Country(name = stringResource(R.string.select_country_all), isoCode = "", stationCount = totalStations),
                            isSelected = selectedCountryCode.isNullOrBlank(),
                            onClick = { onCountrySelected(Country(name = context.getString(R.string.select_country_all), isoCode = "", stationCount = totalStations), null) }
                        )
                    }
                    itemsIndexed(filteredCountries, key = { _, country -> country.isoCode }) { _, country ->
                        val isSelected = country.isoCode == selectedCountryCode
                        val statesForCountry = remember(country.isoCode) {
                            com.armanmaurya.internetradio.util.StateUtils.getStatesForCountry(context, country.isoCode)
                        }
                        CountryItem(
                            country = country,
                            isSelected = isSelected,
                            hasStates = statesForCountry.isNotEmpty(),
                            stateCount = statesForCountry.size,
                            onClick = { 
                                if (statesForCountry.isNotEmpty()) {
                                    selectedCountryForStates = country
                                } else {
                                    onCountrySelected(country, null) 
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
    
    
    var displayCountryForStates by remember { mutableStateOf<Country?>(null) }
    LaunchedEffect(selectedCountryForStates) {
        if (selectedCountryForStates != null) {
            displayCountryForStates = selectedCountryForStates
        }
    }
    
    AnimatedVisibility(
        visible = selectedCountryForStates != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.fillMaxSize()
    ) {
        displayCountryForStates?.let { currentCountry ->
            BackHandler {
                selectedCountryForStates = null
            }
            var stateSearchQuery by remember { mutableStateOf("") }
            val allStates = remember(currentCountry.isoCode) {
                com.armanmaurya.internetradio.util.StateUtils.getStatesForCountry(context, currentCountry.isoCode)
            }
            val filteredStates = remember(allStates, stateSearchQuery) {
                if (stateSearchQuery.isBlank()) allStates
                else allStates.filter { it.getDisplayName(java.util.Locale.getDefault().language).contains(stateSearchQuery, ignoreCase = true) }
            }
            
            var isStateSearchActive by remember { mutableStateOf(false) }
            val stateFocusRequester = remember { FocusRequester() }

            LaunchedEffect(isStateSearchActive) {
                if (isStateSearchActive) {
                    stateFocusRequester.requestFocus()
                }
            }

            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                topBar = {
                    TopAppBar(
                        title = {
                            AnimatedContent(
                                targetState = isStateSearchActive,
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                                label = "StateSearchTransition"
                            ) { isSearch ->
                                if (isSearch) {
                                    TextField(
                                        value = stateSearchQuery,
                                        onValueChange = { stateSearchQuery = it },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(stateFocusRequester),
                                        placeholder = { Text(stringResource(R.string.select_country_search_states)) },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent,
                                        )
                                    )
                                } else {
                                    Text(stringResource(R.string.select_country_select_state_in, currentCountry.name))
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                if (isStateSearchActive) {
                                    isStateSearchActive = false
                                    stateSearchQuery = ""
                                } else {
                                    selectedCountryForStates = null
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                            }
                        },
                        actions = {
                            IconButton(onClick = {
                                if (isStateSearchActive) {
                                    isStateSearchActive = false
                                    stateSearchQuery = ""
                                } else {
                                    isStateSearchActive = true
                                }
                            }) {
                                Icon(
                                    imageVector = if (isStateSearchActive) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = stringResource(R.string.cd_search)
                                )
                            }
                        }
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(
                        bottom = androidx.compose.ui.unit.max(contentPadding.calculateBottomPadding(), WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding())
                    )
                ) {
                    item(key = "all") {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.select_country_all_of, currentCountry.name)) },
                            modifier = Modifier
                                .animateItem()
                                .clickable {
                                    selectedCountryForStates = null
                                    onCountrySelected(currentCountry, null)
                                }
                        )
                    }
                    itemsIndexed(filteredStates, key = { _, st -> st.code }) { _, st ->
                        ListItem(
                            headlineContent = { Text(st.getDisplayName(java.util.Locale.getDefault().language)) },
                            trailingContent = { Text(st.code, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier
                                .animateItem()
                                .clickable {
                                    selectedCountryForStates = null
                                    onCountrySelected(currentCountry, st.code)
                                }
                        )
                    }
                }
            }
        }
    }
}


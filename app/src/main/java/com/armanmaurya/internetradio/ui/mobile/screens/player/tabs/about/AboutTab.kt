package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.about

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.model.RadioStation

@Composable
fun AboutTab(
    station: RadioStation,
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isPureBlack = MaterialTheme.colorScheme.surfaceContainerLow == androidx.compose.ui.graphics.Color.Black

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .nestedScroll(nestedScrollConnection),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        val countryName = station.countryCode.takeIf { it.isNotBlank() }?.let { java.util.Locale("", it).getDisplayCountry(java.util.Locale.getDefault()) }
        val stateName = station.iso3166_2?.takeIf { it.isNotBlank() }?.let { iso3166_2 ->
            if (station.countryCode.isNotBlank()) {
                com.armanmaurya.internetradio.core.utils.StateUtils.getStatesForCountry(context, station.countryCode).find { it.code == iso3166_2 }?.getDisplayName(java.util.Locale.getDefault().language) ?: iso3166_2
            } else iso3166_2
        }

        if (!countryName.isNullOrBlank() || !stateName.isNullOrBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!countryName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.primaryContainer)
                                .then(
                                    if (isPureBlack) Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.basicMarquee()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = stringResource(R.string.edit_station_country_field),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = countryName,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (!countryName.isNullOrBlank() && !stateName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(6.dp)
                                .background(
                                    if (isPureBlack) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                        )
                    }

                    if (!stateName.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.primaryContainer)
                                .then(
                                    if (isPureBlack) Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.basicMarquee()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = stringResource(R.string.edit_station_state_field),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stateName,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        val languageText = station.languageCodes.takeIf { it.isNotEmpty() }?.joinToString(", ") { code ->
            com.neovisionaries.i18n.LanguageAlpha3Code.getByCodeIgnoreCase(code)?.getName() ?: java.util.Locale(code).getDisplayLanguage(java.util.Locale.getDefault())
        }

        if (!languageText.isNullOrBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.primaryContainer)
                        .then(
                            if (isPureBlack) Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.basicMarquee()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = stringResource(R.string.edit_station_language_field),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = languageText,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (station.tags.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.player_label_tags),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    station.tags.forEach { tag ->
                        SuggestionChip(
                            onClick = { },
                            label = { Text(tag) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (isPureBlack) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.secondaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            border = if (isPureBlack) SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                borderWidth = 1.dp
                            ) else null
                        )
                    }
                }
            }
        }

        if (station.homepage.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.player_label_website),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                Text(
                    text = station.homepage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(station.homepage))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_cannot_open_website), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(station.homepage))
                                Toast.makeText(context, context.getString(R.string.player_website_copied), Toast.LENGTH_SHORT).show()
                            }
                        )
                        .padding(top = 4.dp, bottom = 8.dp)
                )
            }
        }

        if (station.url.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.player_tab_about_stream_url),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                Text(
                    text = station.url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(station.url))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_cannot_open_url), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(station.url))
                                Toast.makeText(context, context.getString(R.string.player_stream_url_copied), Toast.LENGTH_SHORT).show()
                            }
                        )
                        .padding(top = 4.dp, bottom = 8.dp)
                )
            }
        }

        if (station.favicon.isNotBlank()) {
            item {
                Text(
                    text = stringResource(R.string.edit_station_favicon_url_field),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
                Text(
                    text = station.favicon,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(station.favicon))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, context.getString(R.string.error_cannot_open_url), Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString(station.favicon))
                                Toast.makeText(context, context.getString(R.string.player_favicon_url_copied), Toast.LENGTH_SHORT).show()
                            }
                        )
                        .padding(top = 4.dp, bottom = 32.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

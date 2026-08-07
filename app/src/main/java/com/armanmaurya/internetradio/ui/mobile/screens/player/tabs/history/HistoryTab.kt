package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.history

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.data.local.entity.TrackHistoryEntity

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryTab(
    trackHistory: List<TrackHistoryEntity>,
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
            .padding(horizontal = 16.dp)
            .nestedScroll(nestedScrollConnection)
    ) {
        if (trackHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.player_no_tracks_played),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(trackHistory.size) { index ->
                val track = trackHistory[index]
                val time = DateUtils.getRelativeTimeSpanString(
                    track.timestamp,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()

                var isExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isPureBlack) androidx.compose.ui.graphics.Color.Black
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                        .then(
                            if (isPureBlack) Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            ) else Modifier
                        )
                        .animateContentSize()
                ) {
                    val parts = track.trackTitle.split(" - ", limit = 2)
                    val title = parts[0].trim()
                    val subtitle = if (parts.size > 1) parts[1].trim() else ""

                    ListItem(
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { isExpanded = !isExpanded },
                                onLongClick = {
                                    clipboardManager.setText(AnnotatedString(track.trackTitle))
                                    Toast.makeText(context, context.getString(R.string.player_copied_track_to_clipboard), Toast.LENGTH_SHORT).show()
                                }
                            ),
                        headlineContent = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                        },
                        supportingContent = if (subtitle.isNotEmpty()) {
                            {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        } else null,
                        trailingContent = {
                            Text(
                                text = time,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            SubcomposeAsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(track.coverArtUrl)
                                    .size(Size.ORIGINAL)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                error = {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = androidx.compose.ui.graphics.Color.Transparent
                        )
                    )

                    AnimatedVisibility(
                        visible = isExpanded,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(track.trackTitle))
                                    Toast.makeText(context, context.getString(R.string.player_copied_to_clipboard), Toast.LENGTH_SHORT).show()
                                    isExpanded = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.player_cd_copy_track_name),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val query = java.net.URLEncoder.encode(track.trackTitle, "UTF-8")
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.youtube.com/results?search_query=$query"))
                                    context.startActivity(intent)
                                    isExpanded = false
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_youtube),
                                    contentDescription = stringResource(R.string.player_cd_search_youtube),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val query = java.net.URLEncoder.encode(track.trackTitle, "UTF-8")
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("spotify:search:$query"))
                                    try {
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://open.spotify.com/search/$query"))
                                        context.startActivity(webIntent)
                                    }
                                    isExpanded = false
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_spotify),
                                    contentDescription = stringResource(R.string.player_cd_search_spotify),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    val query = java.net.URLEncoder.encode(track.trackTitle, "UTF-8")
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=$query"))
                                    context.startActivity(intent)
                                    isExpanded = false
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_google),
                                    contentDescription = stringResource(R.string.player_cd_search_google),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.armanmaurya.internetradio.ui.mobile.screens.player.tabs.lyrics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.draw.drawWithContent
import com.armanmaurya.internetradio.data.model.LyricsState
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.animateScrollBy

@Composable
fun LyricsTab(
    listState: LazyListState,
    nestedScrollConnection: NestedScrollConnection,
    lyricsState: LyricsState,
    trackStartTime: Long?
) {
    var isSyncEnabled by rememberSaveable { mutableStateOf(true) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Inner box with the gradient blur mask
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .graphicsLayer { alpha = 0.99f }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.15f to Color.Black,
                            0.85f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            when (lyricsState) {
                is LyricsState.Loading -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is LyricsState.NotAvailable -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text(
                                text = "Lyrics not available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is LyricsState.Success -> {
                    val canSync = !lyricsState.syncedLyrics.isNullOrEmpty() && trackStartTime != null
                    val actuallySync = canSync && isSyncEnabled
                    
                    if (actuallySync) {
                        SyncedLyricsView(
                            lines = lyricsState.syncedLyrics,
                            listState = listState,
                            trackStartTime = trackStartTime
                        )
                    } else if (!lyricsState.plainLyrics.isNullOrEmpty()) {
                        PlainLyricsView(
                            lyrics = lyricsState.plainLyrics,
                            listState = listState
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            item {
                                Text(
                                    text = "Lyrics not available",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overlay Toggle Button
        if (lyricsState is LyricsState.Success) {
            val canSync = !lyricsState.syncedLyrics.isNullOrEmpty() && trackStartTime != null
            val isCurrentlySynced = canSync && isSyncEnabled
            
            // Only show button if we have SOME lyrics
            if (!lyricsState.syncedLyrics.isNullOrEmpty() || !lyricsState.plainLyrics.isNullOrEmpty()) {
                FilledTonalButton(
                    onClick = { 
                        if (canSync) {
                            isSyncEnabled = !isSyncEnabled
                        }
                    },
                    enabled = canSync,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Text(text = if (isCurrentlySynced) "Synced" else "Plain")
                }
            }
        }
    }
}

@Composable
fun PlainLyricsView(
    lyrics: String,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 72.dp, bottom = 120.dp)
    ) {
        item {
            Text(
                text = lyrics,
                style = MaterialTheme.typography.headlineMedium, // match synced lyrics
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start, // left aligned
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SyncedLyricsView(
    lines: List<com.armanmaurya.internetradio.data.model.LrcLine>,
    listState: LazyListState,
    trackStartTime: Long
) {
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var activeIndex by remember(trackStartTime) { mutableIntStateOf(0) }

    LaunchedEffect(trackStartTime) {
        listState.scrollToItem(0)
        while (true) {
            currentTimeMs = System.currentTimeMillis() - trackStartTime
            delay(100) // update 10 times a second
        }
    }

    // Calculate new index smoothly without launching effects every 100ms
    val derivedIndex = remember(currentTimeMs) {
        val idx = lines.indexOfLast { currentTimeMs >= it.timestampMs }
        if (idx >= 0) idx else 0
    }

    LaunchedEffect(derivedIndex) {
        activeIndex = derivedIndex
        if (!listState.isScrollInProgress) {
            val targetIndex = java.lang.Math.max(0, activeIndex - 3)
            val targetItem = listState.layoutInfo.visibleItemsInfo.find { it.index == targetIndex }
            
            if (targetItem != null) {
                val scrollDistance = targetItem.offset - listState.layoutInfo.viewportStartOffset
                listState.animateScrollBy(
                    value = scrollDistance.toFloat(),
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = 800,
                        easing = androidx.compose.animation.core.LinearOutSlowInEasing
                    )
                )
            } else {
                listState.animateScrollToItem(targetIndex)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 72.dp, bottom = 160.dp)
    ) {
        items(lines.size) { index ->
            val isActive = index == activeIndex
            val line = lines[index]
            
            val textColor by androidx.compose.animation.animateColorAsState(
                targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 800)
            )
            
            Text(
                text = line.text,
                style = MaterialTheme.typography.headlineMedium, // constant style
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, // constant bold
                color = textColor,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}

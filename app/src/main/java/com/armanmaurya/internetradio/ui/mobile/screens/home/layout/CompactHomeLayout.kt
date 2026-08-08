package com.armanmaurya.internetradio.ui.mobile.screens.home.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CompactHomeLayout(
    innerPadding: PaddingValues,
    pagerState: androidx.compose.foundation.pager.PagerState,
    tabs: List<String>,
    tabWidths: androidx.compose.runtime.snapshots.SnapshotStateList<Dp>,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    pagerContent: @Composable () -> Unit
) {
    val density = LocalDensity.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
    ) {
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 8.dp,
            modifier = Modifier.padding(horizontal = 4.dp),
            indicator = { tabPositions ->
                if (pagerState.currentPage < tabPositions.size) {
                    val pagerPage = pagerState.currentPage
                    val fraction = pagerState.currentPageOffsetFraction
                    val targetPage = when {
                        fraction > 0 && pagerPage < tabs.size - 1 -> pagerPage + 1
                        fraction < 0 && pagerPage > 0 -> pagerPage - 1
                        else -> pagerPage
                    }

                    val currentTabPosition = tabPositions[pagerPage]
                    val targetTabPosition = tabPositions[targetPage]

                    val currentContentWidth = tabWidths.getOrElse(pagerPage) { 0.dp }
                    val targetContentWidth = tabWidths.getOrElse(targetPage) { 0.dp }

                    val indicatorWidth = lerp(currentContentWidth, targetContentWidth, fraction.absoluteValue) + 32.dp
                    val indicatorOffset = lerp(currentTabPosition.left, targetTabPosition.left, fraction.absoluteValue)
                    val tabWidth = lerp(currentTabPosition.width, targetTabPosition.width, fraction.absoluteValue)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .wrapContentSize(Alignment.BottomStart)
                            .offset(x = indicatorOffset + (tabWidth - indicatorWidth) / 2)
                            .width(indicatorWidth)
                            .fillMaxHeight()
                            .padding(vertical = 8.dp)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(100)
                            )
                            .then(
                                if (MaterialTheme.colorScheme.surfaceContainerHigh == Color.Black) {
                                    Modifier.border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                        RoundedCornerShape(100)
                                    )
                                } else Modifier
                            )
                            .zIndex(-1f)
                    )
                }
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                style = if (pagerState.currentPage == index)
                                    MaterialTheme.typography.titleSmall
                                else
                                    MaterialTheme.typography.bodyMedium,
                                color = if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 18.dp)
                                    .onGloballyPositioned { coords ->
                                        if (index < tabWidths.size) {
                                            tabWidths[index] = with(density) { coords.size.width.toDp() }
                                        }
                                    }
                            )
                        }
                    )
                }
            }
        }

        pagerContent()
    }
}

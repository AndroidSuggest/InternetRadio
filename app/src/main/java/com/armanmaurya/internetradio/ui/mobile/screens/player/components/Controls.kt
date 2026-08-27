@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
package com.armanmaurya.internetradio.ui.mobile.screens.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.armanmaurya.internetradio.R
import com.armanmaurya.internetradio.ui.mobile.screens.player.collapseHeight

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.Controls(
    historyProgress: Float,
    showSleepTimerDialog: Boolean,
    sleepTimerEndTime: Long?,
    remainingTime: Long,
    sleepTimerProgress: Float,
    hasPrevious: Boolean,
    hasNext: Boolean,
    isPlaying: Boolean,
    isLoading: Boolean,
    isRecording: Boolean,
    onOpenSleepTimer: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleRecording: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .collapseHeight(historyProgress)
            .alpha(1f - historyProgress),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(0.7f).height(64.dp)) {
            androidx.compose.animation.AnimatedVisibility(
                visible = !showSleepTimerDialog,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300))
            ) {
                FilledTonalIconButton(
                    onClick = onOpenSleepTimer,
                    modifier = Modifier
                        .sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "sleep_timer_container"),
                            animatedVisibilityScope = this@AnimatedVisibility,
                            enter = fadeIn(tween(300)),
                            exit = fadeOut(tween(300)),
                            boundsTransform = { _, _ -> tween(durationMillis = 350) },
                            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(20.dp))
                        )
                        .fillMaxSize(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    if (sleepTimerEndTime != null) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { sleepTimerProgress },
                                modifier = Modifier.size(28.dp),
                                color = LocalContentColor.current,
                                trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                                strokeWidth = 2.dp
                            )
                            val mins = (remainingTime / 60000).toInt() + 1
                            Text(
                                text = mins.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = LocalContentColor.current
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = stringResource(R.string.player_sleep_timer_title),
                            modifier = Modifier
                                .sharedElement(
                                    sharedContentState = rememberSharedContentState(key = "sleep_timer_icon"),
                                    animatedVisibilityScope = this@AnimatedVisibility,
                                    boundsTransform = { _, _ -> tween(durationMillis = 350) }
                                )
                                .size(28.dp)
                        )
                    }
                }
            }
        }

        FilledIconButton(
            onClick = onPrevious,
            enabled = hasPrevious,
            modifier = Modifier.size(64.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.player_cd_previous),
                modifier = Modifier.size(32.dp)
            )
        }

        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.weight(2f).height(64.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (isLoading) {
                androidx.compose.material3.LoadingIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        FilledIconButton(
            onClick = onNext,
            enabled = hasNext,
            modifier = Modifier.size(64.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.player_cd_next),
                modifier = Modifier.size(32.dp)
            )
        }

        FilledTonalIconButton(
            onClick = onToggleRecording,
            modifier = Modifier.weight(0.7f).height(64.dp),
            shape = RoundedCornerShape(20.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (isRecording) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = stringResource(R.string.player_cd_record),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

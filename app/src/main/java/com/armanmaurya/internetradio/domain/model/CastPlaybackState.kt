package com.armanmaurya.internetradio.domain.model

enum class CastPlaybackState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED;

    val isPlaying: Boolean get() = this == PLAYING
    val isBuffering: Boolean get() = this == BUFFERING
}

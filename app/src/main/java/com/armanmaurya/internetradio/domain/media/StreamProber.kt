package com.armanmaurya.internetradio.domain.media

import com.armanmaurya.internetradio.domain.model.StreamProbeResult

interface StreamProber {
    suspend fun probe(url: String): StreamProbeResult?
}

package com.armanmaurya.internetradio.player

object StreamFormatUtils {
    enum class AudioFormat(val extension: String) {
        MP3("mp3"), AAC("aac"), OGG("ogg"), UNKNOWN("aac")
    }

    fun isHlsContentType(ct: String): Boolean {
        val c = ct.lowercase()
        return c.contains("mpegurl") || c.contains("m3u")
    }

    fun audioFormatFromMagicBytes(b: ByteArray): AudioFormat {
        if (b.size < 2) return AudioFormat.UNKNOWN
        return when {
            // OGG: "OggS"
            b.size >= 4 && b[0] == 0x4F.toByte() && b[1] == 0x67.toByte() &&
            b[2] == 0x67.toByte() && b[3] == 0x53.toByte() -> AudioFormat.OGG

            // AAC ADTS: FF F1 or FF F9
            b[0] == 0xFF.toByte() &&
            (b[1] == 0xF1.toByte() || b[1] == 0xF9.toByte()) -> AudioFormat.AAC

            // MP3 ID3 header: "ID3"
            b.size >= 3 && b[0] == 0x49.toByte() && b[1] == 0x44.toByte() &&
            b[2] == 0x33.toByte() -> AudioFormat.MP3

            // MP3 sync words
            b[0] == 0xFF.toByte() &&
            (b[1] == 0xFB.toByte() || b[1] == 0xFA.toByte() ||
             b[1] == 0xF3.toByte() || b[1] == 0xF2.toByte()) -> AudioFormat.MP3

            else -> AudioFormat.UNKNOWN
        }
    }

    data class StreamProbeResult(
        val codec: String, 
        val bitrate: Int,
        val name: String?,
        val description: String?,
        val genre: String?,
        val homepage: String?
    )

    suspend fun probeStream(url: String, okHttpClient: okhttp3.OkHttpClient): StreamProbeResult? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext null
        var detectedCodec = ""
        var detectedBitrate = 0
        var isHls = url.contains(".m3u8")
        
        var icyName: String? = null
        var icyDescription: String? = null
        var icyGenre: String? = null
        var icyUrl: String? = null
        
        try {
            val request = okhttp3.Request.Builder().url(url).header("Icy-MetaData", "1").build()
            okHttpClient.newCall(request).execute().use { response ->
                val contentType = response.header("Content-Type")?.lowercase() ?: ""
                detectedBitrate = response.header("icy-br")?.toIntOrNull() ?: 0
                icyName = response.header("icy-name")
                icyDescription = response.header("icy-description")
                icyGenre = response.header("icy-genre")
                icyUrl = response.header("icy-url")
                
                if (contentType.contains("mpegurl") || contentType.contains("x-mpegurl")) isHls = true
                
                detectedCodec = when {
                    isHls -> ""
                    contentType.contains("flac") -> "FLAC"
                    contentType.contains("mpeg") -> "MP3"
                    contentType.contains("aacp") || contentType.contains("aac+") -> "AAC+"
                    contentType.contains("aac") -> "AAC"
                    contentType.contains("ogg") -> "OGG"
                    else -> ""
                }

                if (!isHls && response.isSuccessful) {
                    try {
                        val bodyBytes = response.peekBody(2048).bytes()
                        val bodyString = String(bodyBytes, kotlin.text.Charsets.ISO_8859_1)
                        
                        if (bodyString.contains("\u007FFLAC") || bodyString.contains("fLaC")) {
                            detectedCodec = "FLAC"
                        } else if (bodyString.contains("OpusHead")) {
                            detectedCodec = "OPUS"
                        } else if (bodyString.contains("\u0001vorbis")) {
                            detectedCodec = "VORBIS"
                        } else if (bodyString.contains("OggS")) {
                            if (detectedCodec == "") detectedCodec = "OGG"
                        } else {
                            var foundSync = false
                            for (i in 0 until bodyBytes.size - 1) {
                                val b1 = bodyBytes[i].toInt() and 0xFF
                                val b2 = bodyBytes[i + 1].toInt() and 0xFF
                                if (b1 == 0xFF && (b2 and 0xE0) == 0xE0) {
                                    if ((b2 and 0xF6) == 0xF0) {
                                        if (detectedCodec != "AAC+") {
                                            detectedCodec = "AAC"
                                        }
                                        foundSync = true
                                        break
                                    } else if ((b2 and 0x06) == 0x02 || (b2 and 0x06) == 0x04) {
                                        detectedCodec = "MP3"
                                        foundSync = true
                                        break
                                    }
                                }
                            }
                            if (!foundSync && bodyString.startsWith("ID3") && detectedCodec == "") {
                                detectedCodec = "MP3"
                            }
                        }
                    } catch (e: Exception) {
                        // ignore and use content-type detection
                    }
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
                    
                    if (detectedBitrate == 0) {
                        val fallbackMatch = Regex("audio(?:=|%3[Dd])(\\d+)").find(url) ?: Regex("audio(?:=|%3[Dd])(\\d+)").find(bodyString)
                        if (fallbackMatch != null) {
                            val bps = fallbackMatch.groupValues[1].toIntOrNull() ?: 0
                            detectedBitrate = if (bps > 1000) bps / 1000 else bps
                        }
                    }
                    
                    if (detectedCodec.isBlank()) {
                        if (bodyString.contains(".aac") || url.contains("aac", ignoreCase = true)) {
                            detectedCodec = "AAC"
                        } else if (bodyString.contains(".ts")) {
                            detectedCodec = "AAC"
                        }
                    }
                }
            }
            StreamProbeResult(detectedCodec, detectedBitrate, icyName, icyDescription, icyGenre, icyUrl)
        } catch (e: Exception) {
            null
        }
    }
}

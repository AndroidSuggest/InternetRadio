package com.armanmaurya.internetradio.core.media.player

import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment

@UnstableApi
object TrackParser {

    private val STREAM_TITLE_REGEX = Regex("StreamTitle=['\"](.*?)['\"];?", RegexOption.IGNORE_CASE)

    fun parse(metadata: Metadata): String? {
        var streamTitle: String? = null
        var title: String? = null
        var artist: String? = null

        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is IcyInfo -> {
                    val raw = entry.title?.takeIf { it.isNotBlank() }
                    if (raw != null && streamTitle == null) {
                        streamTitle = extractStreamTitle(raw)
                    }
                }

                is TextInformationFrame -> {
                    val text = entry.values.filter { it.isNotBlank() }.joinToString(", ").takeIf { it.isNotEmpty() }

                    if (text != null) {
                        val frameId = entry.id.uppercase()
                        when {
                            frameId in listOf("TIT2", "TT2") -> {
                                if (title == null) title = text
                            }
                            frameId in listOf("TPE1", "TP1", "TPE2", "TP2") -> {
                                if (artist == null) artist = text
                            }
                            frameId in listOf("TIT1", "TT1", "TIT3", "TT3") -> {
                                if (title == null) title = text
                            }
                            frameId in listOf("TXXX", "TXX") -> {
                                val desc = entry.description?.uppercase()
                                when {
                                    desc == "STREAMTITLE" -> {
                                        if (streamTitle == null) streamTitle = extractStreamTitle(text)
                                    }
                                    desc in listOf("TITLE", "SONG", "TRACK") -> {
                                        if (title == null) title = text
                                    }
                                    desc in listOf("ARTIST", "PERFORMER") -> {
                                        if (artist == null) artist = text
                                    }
                                }
                            }
                        }
                    }
                }

                is CommentFrame -> {
                    val desc = entry.description.uppercase()
                    val text = entry.text.takeIf { it.isNotBlank() }
                    if (text != null && streamTitle == null) {
                        if (desc == "STREAMTITLE" || text.startsWith("StreamTitle=", ignoreCase = true)) {
                            streamTitle = extractStreamTitle(text)
                        }
                    }
                }

                is InternalFrame -> {
                    val desc = entry.description.uppercase()
                    val text = entry.text.takeIf { it.isNotBlank() }
                    if (text != null) {
                        when {
                            desc == "STREAMTITLE" -> {
                                if (streamTitle == null) streamTitle = extractStreamTitle(text)
                            }
                            desc in listOf("TITLE", "SONG", "TRACK") -> {
                                if (title == null) title = text
                            }
                            desc in listOf("ARTIST", "PERFORMER") -> {
                                if (artist == null) artist = text
                            }
                        }
                    }
                }

                is VorbisComment -> {
                    val key = entry.key.uppercase()
                    val value = entry.value.takeIf { it.isNotBlank() }
                    if (value != null) {
                        when {
                            key == "STREAMTITLE" -> {
                                if (streamTitle == null) streamTitle = extractStreamTitle(value)
                            }
                            key in listOf("TITLE", "TRACK") -> {
                                if (title == null) title = value
                            }
                            key in listOf("ARTIST", "ALBUMARTIST") -> {
                                if (artist == null) artist = value
                            }
                        }
                    }
                }
            }
        }

        return formatTrack(artist, title, streamTitle)
    }

    private fun extractStreamTitle(raw: String): String {
        val match = STREAM_TITLE_REGEX.find(raw)
        return match?.groupValues?.getOrNull(1)?.trim() ?: raw.trim()
    }

    private fun formatTrack(artist: String?, title: String?, streamTitle: String?): String? {
        if (!streamTitle.isNullOrBlank()) {
            return streamTitle.trim()
        }
        val cleanTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        val cleanArtist = artist?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            cleanArtist != null && cleanTitle != null -> {
                if (cleanTitle.startsWith(cleanArtist, ignoreCase = true) || cleanTitle.contains(" - ")) {
                    cleanTitle
                } else {
                    "$cleanTitle - $cleanArtist"
                }
            }
            cleanTitle != null -> cleanTitle
            cleanArtist != null -> cleanArtist
            else -> null
        }
    }
}
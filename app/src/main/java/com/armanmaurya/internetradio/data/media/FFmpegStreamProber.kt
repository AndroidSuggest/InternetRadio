package com.armanmaurya.internetradio.data.media

import com.armanmaurya.internetradio.domain.media.StreamProber
import com.armanmaurya.internetradio.domain.model.StreamProbeResult
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject
import android.util.Log

class FFmpegStreamProber @Inject constructor() : StreamProber {

    override suspend fun probe(url: String): StreamProbeResult? = withContext(Dispatchers.IO) {
        if (!url.startsWith("http")) return@withContext null

        try {
            // Use executeWithArguments to avoid string parsing issues (like quotes around the URL breaking it)
            // Add Icy-MetaData header so Icecast servers return the icy-name and icy-br metadata
            val arguments = arrayOf(
                "-headers", "Icy-MetaData: 1\r\n",
                "-probesize", "32768",
                "-analyzeduration", "1000000",
                "-v", "error",
                "-print_format", "json",
                "-show_format",
                "-show_streams",
                url
            )
            
            val session = FFprobeKit.executeWithArguments(arguments)
            
            Log.d("FFprobe", "Probing URL: $url")
            Log.d("FFprobe", "Return code: ${session.returnCode?.value}")
            Log.d("FFprobe", "Output: ${session.output}")
            
            if (session.returnCode?.isValueSuccess == true) {
                val output = session.output
                val json = JSONObject(output)
                Log.d("FFprobe", "Parsed JSON: $json")
                
                var detectedCodec = ""
                var detectedBitrate = 0
                
                // Parse Codec and Stream-level Bitrate (for HLS)
                if (json.has("streams")) {
                    val streams = json.getJSONArray("streams")
                    if (streams.length() > 0) {
                        val stream = streams.getJSONObject(0)
                        detectedCodec = stream.optString("codec_name", "").uppercase()
                        
                        // Fallback bitrate from stream object
                        val streamBitrate = stream.optString("bit_rate", "0").toIntOrNull() ?: 0
                        if (streamBitrate > 0) {
                            detectedBitrate = if (streamBitrate > 1000) streamBitrate / 1000 else streamBitrate
                        }
                        
                        // Fallback bitrate from HLS tags
                        if (detectedBitrate == 0 && stream.has("tags")) {
                            val streamTags = stream.getJSONObject("tags")
                            val variantBitrate = streamTags.optString("variant_bitrate", "0").toIntOrNull() ?: 0
                            if (variantBitrate > 0) {
                                detectedBitrate = if (variantBitrate > 1000) variantBitrate / 1000 else variantBitrate
                            }
                        }
                    }
                }
                
                // Parse Global Bitrate & ICY Metadata from Format
                var icyName: String? = null
                var icyDescription: String? = null
                var icyGenre: String? = null
                var icyUrl: String? = null

                if (json.has("format")) {
                    val format = json.getJSONObject("format")
                    
                    if (detectedBitrate == 0) {
                        // Global bitrate is usually returned in bps (e.g., 128000), we want kbps
                        val rawBitrate = format.optString("bit_rate", "0").toIntOrNull() ?: 0
                        detectedBitrate = if (rawBitrate > 1000) rawBitrate / 1000 else rawBitrate
                    }
                    
                    if (format.has("tags")) {
                        val tags = format.getJSONObject("tags")
                        icyName = tags.optString("icy-name", null) ?: tags.optString("StreamTitle", null)
                        icyDescription = tags.optString("icy-description", null)
                        icyGenre = tags.optString("icy-genre", null)
                        icyUrl = tags.optString("icy-url", null)
                    }
                }

                Log.d("FFprobe", "Result -> Codec: $detectedCodec, Bitrate: $detectedBitrate, Name: $icyName")
                return@withContext StreamProbeResult(
                    codec = detectedCodec,
                    bitrate = detectedBitrate,
                    name = icyName,
                    description = icyDescription,
                    genre = icyGenre,
                    homepage = icyUrl
                )
            } else {
                Log.e("FFprobe", "FFprobe failed! Code: ${session.returnCode?.value}, FailStack: ${session.failStackTrace}")
            }
            return@withContext null
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}

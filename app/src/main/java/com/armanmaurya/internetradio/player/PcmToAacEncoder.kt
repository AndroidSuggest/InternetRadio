package com.armanmaurya.internetradio.player

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import java.io.FileDescriptor
import java.nio.ByteBuffer

class PcmToAacEncoder(
    private val sampleRate: Int,
    private val channelCount: Int,
    private val bytesPerFrame: Int,
    private val fileDescriptor: FileDescriptor?,
    private val filePath: String?
) {
    private var mediaCodec: MediaCodec? = null
    private var outputStream: java.io.OutputStream? = null
    private val bufferInfo = MediaCodec.BufferInfo()
    private var isRecording = false
    private var totalFramesEncoded: Long = 0

    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1048576)
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec?.start()

            if (fileDescriptor != null) {
                outputStream = java.io.FileOutputStream(fileDescriptor)
            } else if (filePath != null) {
                outputStream = java.io.FileOutputStream(filePath)
            } else {
                throw IllegalArgumentException("Either fileDescriptor or filePath must be provided")
            }

            isRecording = true
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error initializing encoder", e)
            release()
        }
    }

    @Synchronized
    fun encode(pcmData: ByteArray, offset: Int, length: Int) {
        if (!isRecording) return

        try {
            val codec = mediaCodec ?: return
            val inputBufferIndex = codec.dequeueInputBuffer(10000)
            if (inputBufferIndex >= 0) {
                val inputBuffer = codec.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmData, offset, length)
                
                val numFrames = length / bytesPerFrame
                val presentationTimeUs = (totalFramesEncoded * 1000000L) / sampleRate
                totalFramesEncoded += numFrames
                codec.queueInputBuffer(inputBufferIndex, 0, length, presentationTimeUs, 0)
            }

            drain(false)
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error encoding audio", e)
        }
    }

    private fun drain(endOfStream: Boolean) {
        val codec = mediaCodec ?: return
        var tryAgainCount = 0

        while (true) {
            val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)

            if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    break
                }
                tryAgainCount++
                if (tryAgainCount > 100) {
                    Log.w("PcmToAacEncoder", "Timeout waiting for EOS")
                    break
                }
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Not needed for ADTS
            } else if (outputBufferIndex < 0) {
                // Ignore other statuses
            } else {
                val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                if (outputBuffer != null && bufferInfo.size != 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    
                    val outData = ByteArray(bufferInfo.size + 7)
                    addADTStoPacket(outData, outData.size)
                    outputBuffer.get(outData, 7, bufferInfo.size)
                    
                    try {
                        outputStream?.write(outData)
                    } catch (e: Exception) {
                        Log.e("PcmToAacEncoder", "Error writing to file", e)
                    }
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)

                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d("PcmToAacEncoder", "End of stream reached")
                    break
                }
            }
        }
    }

    private fun addADTStoPacket(packet: ByteArray, packetLen: Int) {
        val profile = 2 // AAC LC
        val freqIdx = when (sampleRate) {
            96000 -> 0
            88200 -> 1
            64000 -> 2
            48000 -> 3
            44100 -> 4
            32000 -> 5
            24000 -> 6
            22050 -> 7
            16000 -> 8
            12000 -> 9
            11025 -> 10
            8000 -> 11
            7350 -> 12
            else -> 4 // default to 44100
        }
        val chanCfg = channelCount

        packet[0] = 0xFF.toByte()
        packet[1] = 0xF9.toByte()
        packet[2] = (((profile - 1) shl 6) + (freqIdx shl 2) + (chanCfg shr 2)).toByte()
        packet[3] = (((chanCfg and 3) shl 6) + (packetLen shr 11)).toByte()
        packet[4] = ((packetLen and 0x7FF) shr 3).toByte()
        packet[5] = (((packetLen and 7) shl 5) + 0x1F).toByte()
        packet[6] = 0xFC.toByte()
    }

    @Synchronized
    fun stop() {
        if (!isRecording) return
        isRecording = false
        
        try {
            val codec = mediaCodec
            if (codec != null) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val presentationTimeUs = (totalFramesEncoded * 1000000L) / sampleRate
                    codec.queueInputBuffer(inputBufferIndex, 0, 0, presentationTimeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
                drain(true)
            }
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error stopping encoder", e)
        } finally {
            release()
        }
    }

    private fun release() {
        try {
            mediaCodec?.stop()
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error stopping MediaCodec", e)
        }
        try {
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error releasing MediaCodec", e)
        }
        mediaCodec = null

        try {
            outputStream?.flush()
            outputStream?.close()
        } catch (e: Exception) {
            Log.e("PcmToAacEncoder", "Error closing output stream", e)
        }
        outputStream = null
    }
}

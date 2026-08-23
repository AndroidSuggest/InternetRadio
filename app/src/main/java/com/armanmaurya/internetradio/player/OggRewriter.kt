package com.armanmaurya.internetradio.player

import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object OggRewriter {
    // Standard Ogg CRC32 polynomial: 0x04c11db7
    private val crcTable = IntArray(256)

    init {
        for (i in 0 until 256) {
            var r = i shl 24
            for (j in 0 until 8) {
                r = if ((r and -0x80000000) != 0) {
                    (r shl 1) xor 0x04c11db7
                } else {
                    r shl 1
                }
            }
            crcTable[i] = r
        }
    }

    private fun updateCrc(crc: Int, data: ByteArray, offset: Int, length: Int): Int {
        var currentCrc = crc
        for (i in offset until offset + length) {
            val byteVal = data[i].toInt() and 0xFF
            val index = ((currentCrc ushr 24) xor byteVal) and 0xFF
            currentCrc = (currentCrc shl 8) xor crcTable[index]
        }
        return currentCrc
    }

    fun remuxStream(input: InputStream, output: OutputStream, scope: CoroutineScope, job: Job?, onBytesWritten: (Int) -> Unit) {
        var firstGranulePosition = -1L
        
        val headerBuf = ByteArray(27)
        val segmentTable = ByteArray(255)
        var pageData = ByteArray(65536)

        while (scope.isActive && job?.isActive == true) {
            // Find OggS sync
            var synced = false
            while (scope.isActive && job?.isActive == true) {
                val b = input.read()
                if (b == -1) return
                if (b == 0x4F) { // 'O'
                    headerBuf[0] = b.toByte()
                    if (readFully(input, headerBuf, 3, 1)) {
                        if (headerBuf[1] == 0x67.toByte() && headerBuf[2] == 0x67.toByte() && headerBuf[3] == 0x53.toByte()) {
                            synced = true
                            break
                        }
                    }
                }
            }
            
            if (!synced) break

            // Read the rest of the 27-byte header
            if (!readFully(input, headerBuf, 23, 4)) break
            
            // Read Granule Position (bytes 6-13, little endian)
            val buffer = ByteBuffer.wrap(headerBuf, 6, 8).order(ByteOrder.LITTLE_ENDIAN)
            val originalGranule = buffer.long
            
            // Rewrite Granule Position
            var newGranule = originalGranule
            if (originalGranule != -1L && originalGranule != 0L) {
                if (firstGranulePosition == -1L) {
                    firstGranulePosition = originalGranule
                }
                newGranule = originalGranule - firstGranulePosition
                if (newGranule < 0) newGranule = 0
            }
            
            val newBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            newBuffer.putLong(newGranule)
            val newGranuleBytes = newBuffer.array()
            System.arraycopy(newGranuleBytes, 0, headerBuf, 6, 8)
            
            // Clear the CRC field (bytes 22-25) before calculating
            headerBuf[22] = 0
            headerBuf[23] = 0
            headerBuf[24] = 0
            headerBuf[25] = 0

            // Read segment count
            val segmentCount = headerBuf[26].toInt() and 0xFF
            
            // Read segment table
            if (segmentCount > 0) {
                if (!readFully(input, segmentTable, segmentCount, 0)) break
            }
            
            var dataSize = 0
            for (i in 0 until segmentCount) {
                dataSize += segmentTable[i].toInt() and 0xFF
            }
            
            // Resize pageData buffer if needed
            if (pageData.size < dataSize) {
                pageData = ByteArray(dataSize)
            }
            
            // Read page data
            if (dataSize > 0) {
                if (!readFully(input, pageData, dataSize, 0)) break
            }
            
            // Calculate CRC
            var crc = 0
            crc = updateCrc(crc, headerBuf, 0, 27)
            if (segmentCount > 0) {
                crc = updateCrc(crc, segmentTable, 0, segmentCount)
            }
            if (dataSize > 0) {
                crc = updateCrc(crc, pageData, 0, dataSize)
            }
            
            // Write CRC (little endian)
            headerBuf[22] = (crc and 0xFF).toByte()
            headerBuf[23] = ((crc ushr 8) and 0xFF).toByte()
            headerBuf[24] = ((crc ushr 16) and 0xFF).toByte()
            headerBuf[25] = ((crc ushr 24) and 0xFF).toByte()
            
            // Write out the modified page
            output.write(headerBuf, 0, 27)
            if (segmentCount > 0) {
                output.write(segmentTable, 0, segmentCount)
            }
            if (dataSize > 0) {
                output.write(pageData, 0, dataSize)
            }
            
            onBytesWritten(27 + segmentCount + dataSize)
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int, offset: Int): Boolean {
        var totalRead = 0
        while (totalRead < length) {
            val read = input.read(buffer, offset + totalRead, length - totalRead)
            if (read == -1) return false
            totalRead += read
        }
        return true
    }
}

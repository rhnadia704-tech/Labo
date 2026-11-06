package com.example.core.system.sparse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object SparseConverter {

    const val SPARSE_HEADER_MAGIC = 0xED26FF3A.toInt()
    const val CHUNK_TYPE_RAW = 0xCAC1
    const val CHUNK_TYPE_FILL = 0xCAC2
    const val CHUNK_TYPE_DONT_CARE = 0xCAC3
    const val CHUNK_TYPE_CRC32 = 0xCAC4

    fun isSparseImage(file: File): Boolean {
        if (!file.exists() || file.length() < 28) return false
        try {
            RandomAccessFile(file, "r").use { raf ->
                val magic = raf.readInt() // big endian in java, but sparse is little endian 0x3AFF26ED
                val leMagic = Integer.reverseBytes(magic)
                return leMagic == SPARSE_HEADER_MAGIC
            }
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun unsparse(
        sparseFile: File,
        rawOutputFile: File,
        onProgress: (Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            RandomAccessFile(sparseFile, "r").use { inRaf ->
                val hdrBuf = ByteArray(28)
                inRaf.readFully(hdrBuf)
                val hdr = ByteBuffer.wrap(hdrBuf).order(ByteOrder.LITTLE_ENDIAN)

                val magic = hdr.getInt(0)
                if (magic != SPARSE_HEADER_MAGIC) {
                    return@withContext false
                }

                val majorVersion = hdr.getShort(4).toInt()
                val minorVersion = hdr.getShort(6).toInt()
                val fileHdrSz = hdr.getShort(8).toInt()
                val chunkHdrSz = hdr.getShort(10).toInt()
                val blkSz = hdr.getInt(12)
                val totalBlks = hdr.getInt(16).toLong() and 0xFFFFFFFFL
                val totalChunks = hdr.getInt(20).toLong() and 0xFFFFFFFFL

                if (fileHdrSz > 28) {
                    inRaf.seek(fileHdrSz.toLong())
                }

                RandomAccessFile(rawOutputFile, "rw").use { outRaf ->
                    val expectedSize = totalBlks * blkSz
                    outRaf.setLength(expectedSize)
                    val chunkHdrBuf = ByteArray(12)
                    var processedBlocks = 0L

                    for (chunkIdx in 0 until totalChunks) {
                        inRaf.readFully(chunkHdrBuf)
                        val chk = ByteBuffer.wrap(chunkHdrBuf).order(ByteOrder.LITTLE_ENDIAN)
                        val chunkType = chk.getShort(0).toInt() and 0xFFFF
                        val chunkSzBlocks = chk.getInt(4).toLong() and 0xFFFFFFFFL
                        val totalSzBytes = chk.getInt(8).toLong() and 0xFFFFFFFFL

                        val dataSzBytes = chunkSzBlocks * blkSz

                        when (chunkType) {
                            CHUNK_TYPE_RAW -> {
                                val buffer = ByteArray(64 * 1024)
                                var remaining = dataSzBytes
                                while (remaining > 0) {
                                    val toRead = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                                    inRaf.readFully(buffer, 0, toRead)
                                    outRaf.write(buffer, 0, toRead)
                                    remaining -= toRead
                                }
                            }
                            CHUNK_TYPE_FILL -> {
                                val fillValue = inRaf.readInt()
                                val fillBytes = ByteArray(4)
                                ByteBuffer.wrap(fillBytes).order(ByteOrder.LITTLE_ENDIAN).putInt(fillValue)
                                val fillBlock = ByteArray(blkSz)
                                for (b in 0 until blkSz step 4) {
                                    System.arraycopy(fillBytes, 0, fillBlock, b, 4)
                                }
                                for (blk in 0 until chunkSzBlocks) {
                                    outRaf.write(fillBlock)
                                }
                            }
                            CHUNK_TYPE_DONT_CARE -> {
                                // Seek forward without writing (sparse hole) or write zeros
                                outRaf.seek(outRaf.filePointer + dataSzBytes)
                            }
                            CHUNK_TYPE_CRC32 -> {
                                inRaf.skipBytes(4)
                            }
                            else -> {
                                inRaf.skipBytes((totalSzBytes - 12).toInt())
                            }
                        }

                        processedBlocks += chunkSzBlocks
                        if (totalBlks > 0 && chunkIdx % 20 == 0L) {
                            val prog = (processedBlocks.toFloat() / totalBlks).coerceIn(0f, 1f)
                            onProgress(prog, "Conversion sparse : $processedBlocks / $totalBlks blocs")
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

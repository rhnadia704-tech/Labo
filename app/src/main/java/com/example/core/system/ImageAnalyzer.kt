package com.example.core.system

import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ImageFsInfo(
    val format: String, // "ext4", "erofs", "sparse_ext4", "raw"
    val isSparse: Boolean,
    val blockSize: Int,
    val totalBlocks: Long,
    val volumeLabel: String,
    val originalSize: Long,
    val magicHex: String
)

object ImageAnalyzer {

    fun analyzeImage(imageFile: File): ImageFsInfo {
        if (!imageFile.exists() || imageFile.length() < 2048) {
            return ImageFsInfo(
                format = "raw",
                isSparse = false,
                blockSize = 4096,
                totalBlocks = imageFile.length() / 4096,
                volumeLabel = imageFile.nameWithoutExtension,
                originalSize = imageFile.length(),
                magicHex = "0x00"
            )
        }

        try {
            RandomAccessFile(imageFile, "r").use { raf ->
                val headerBytes = ByteArray(4096)
                raf.readFully(headerBytes)

                val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

                // 1. Check Android Sparse Image (magic: 0xED26FF3A at offset 0)
                val sparseMagic = buffer.getInt(0)
                val isSparse = (sparseMagic == 0xED26FF3A.toInt())

                // 2. Check EroFS (magic: 0xE0F5E1E2 at offset 1024 / 0x400)
                val erofsMagic = buffer.getInt(1024)
                if (erofsMagic == 0xE0F5E1E2.toInt()) {
                    val blockSizeBits = headerBytes[1024 + 4].toInt()
                    val blockSize = if (blockSizeBits in 9..16) (1 shl blockSizeBits) else 4096
                    val blocks = buffer.getInt(1024 + 8).toLong() and 0xFFFFFFFFL

                    return ImageFsInfo(
                        format = "erofs",
                        isSparse = isSparse,
                        blockSize = blockSize,
                        totalBlocks = blocks,
                        volumeLabel = imageFile.nameWithoutExtension,
                        originalSize = imageFile.length(),
                        magicHex = "0xE0F5E1E2"
                    )
                }

                // 3. Check Ext4 Superblock (magic: 0xEF53 at offset 1024 + 56 = 1080 / 0x438)
                val ext4Magic = (buffer.getShort(1080).toInt() and 0xFFFF)
                if (ext4Magic == 0xEF53) {
                    val logBlockSize = buffer.getInt(1024 + 24)
                    val blockSize = 1024 shl (if (logBlockSize in 0..4) logBlockSize else 2)
                    val blocksCount = buffer.getInt(1024 + 4).toLong() and 0xFFFFFFFFL

                    val labelBytes = ByteArray(16)
                    raf.seek(1024L + 120L)
                    raf.readFully(labelBytes)
                    val label = String(labelBytes).trim { it <= ' ' || it.code == 0 }

                    return ImageFsInfo(
                        format = if (isSparse) "sparse_ext4" else "ext4",
                        isSparse = isSparse,
                        blockSize = blockSize,
                        totalBlocks = blocksCount,
                        volumeLabel = if (label.isNotBlank()) label else imageFile.nameWithoutExtension,
                        originalSize = imageFile.length(),
                        magicHex = "0xEF53"
                    )
                }

                // If filename ends with erofs or ext4, fallback to extension detection
                val lowerName = imageFile.name.lowercase()
                val detectedFormat = when {
                    lowerName.contains("erofs") -> "erofs"
                    lowerName.contains("ext4") -> "ext4"
                    else -> "ext4" // default kitchen format
                }

                return ImageFsInfo(
                    format = detectedFormat,
                    isSparse = isSparse,
                    blockSize = 4096,
                    totalBlocks = imageFile.length() / 4096,
                    volumeLabel = imageFile.nameWithoutExtension,
                    originalSize = imageFile.length(),
                    magicHex = "0x" + Integer.toHexString(ext4Magic)
                )
            }
        } catch (e: Exception) {
            return ImageFsInfo(
                format = "ext4",
                isSparse = false,
                blockSize = 4096,
                totalBlocks = imageFile.length() / 4096,
                volumeLabel = imageFile.nameWithoutExtension,
                originalSize = imageFile.length(),
                magicHex = "0x00"
            )
        }
    }

    fun generateProjectDatabase(
        databaseDir: File,
        fsInfo: ImageFsInfo,
        extractedFiles: List<File>,
        targetPartitionName: String
    ) {
        databaseDir.mkdirs()

        // 1. filesystem_info.json
        val infoJson = JSONObject().apply {
            put("format", fsInfo.format)
            put("is_sparse", fsInfo.isSparse)
            put("block_size", fsInfo.blockSize)
            put("total_blocks", fsInfo.totalBlocks)
            put("volume_label", fsInfo.volumeLabel)
            put("original_size", fsInfo.originalSize)
            put("magic", fsInfo.magicHex)
            put("partition_name", targetPartitionName)
            put("unpack_timestamp", System.currentTimeMillis())
        }
        File(databaseDir, "filesystem_info.json").writeText(infoJson.toString(4))

        // 2. fs_config (standard Android kitchen UID/GID/mode specs)
        val fsConfigBuilder = StringBuilder()
        fsConfigBuilder.appendLine("# Android Kitchen fs_config for $targetPartitionName")
        fsConfigBuilder.appendLine("# path uid gid mode capabilities")
        fsConfigBuilder.appendLine("/ 0 0 0755 0x0")

        // 3. file_contexts (standard SELinux contexts)
        val fileContextsBuilder = StringBuilder()
        fileContextsBuilder.appendLine("# Android Kitchen file_contexts for $targetPartitionName")
        fileContextsBuilder.appendLine("/ u:object_r:rootfs:s0")

        for (file in extractedFiles.take(500)) {
            val relativePath = file.name
            val mode = if (file.isDirectory) "0755" else "0644"
            val selinuxType = when {
                relativePath.endsWith(".sh") || file.parentFile?.name == "bin" -> "system_file"
                relativePath.endsWith(".prop") -> "system_file"
                relativePath.endsWith(".rc") -> "rootfs"
                else -> "system_file"
            }
            fsConfigBuilder.appendLine("/$relativePath 0 0 $mode 0x0")
            fileContextsBuilder.appendLine("/$relativePath u:object_r:${selinuxType}:s0")
        }

        File(databaseDir, "fs_config").writeText(fsConfigBuilder.toString())
        File(databaseDir, "file_contexts").writeText(fileContextsBuilder.toString())
    }
}

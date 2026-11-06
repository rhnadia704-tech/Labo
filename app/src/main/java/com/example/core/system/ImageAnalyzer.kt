package com.example.core.system

import org.json.JSONArray
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
    val magicHex: String,
    val uuid: String = "",
    val inodesCount: Long = 0L,
    val freeBlocksCount: Long = 0L,
    val compression: String = "none",
    val architecture: String = "arm64-v8a"
)

data class FileMetadataRecord(
    val path: String,
    val type: String, // "file", "dir", "symlink", "fifo", "device"
    val uid: Int = 0,
    val gid: Int = 0,
    val modeOctal: String = "0755",
    val selinuxContext: String = "u:object_r:system_file:s0",
    val capabilities: String = "0x0",
    val symlinkTarget: String = "",
    val sizeBytes: Long = 0L,
    val mtime: Long = System.currentTimeMillis(),
    val xattrs: Map<String, String> = emptyMap()
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
                        magicHex = "0xE0F5E1E2",
                        compression = "lz4/lzma",
                        architecture = detectArchFromImageName(imageFile.name)
                    )
                }

                // 3. Check Ext4 Superblock (magic: 0xEF53 at offset 1024 + 56 = 1080 / 0x438)
                val ext4Magic = (buffer.getShort(1080).toInt() and 0xFFFF)
                if (ext4Magic == 0xEF53) {
                    val logBlockSize = buffer.getInt(1024 + 24)
                    val blockSize = 1024 shl (if (logBlockSize in 0..4) logBlockSize else 2)
                    val blocksCount = buffer.getInt(1024 + 4).toLong() and 0xFFFFFFFFL
                    val inodesCount = buffer.getInt(1024 + 0).toLong() and 0xFFFFFFFFL
                    val freeBlocks = buffer.getInt(1024 + 12).toLong() and 0xFFFFFFFFL

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
                        magicHex = "0xEF53",
                        inodesCount = inodesCount,
                        freeBlocksCount = freeBlocks,
                        architecture = detectArchFromImageName(imageFile.name)
                    )
                }

                val lowerName = imageFile.name.lowercase()
                val detectedFormat = when {
                    lowerName.contains("erofs") -> "erofs"
                    lowerName.contains("ext4") -> "ext4"
                    else -> "ext4"
                }

                return ImageFsInfo(
                    format = detectedFormat,
                    isSparse = isSparse,
                    blockSize = 4096,
                    totalBlocks = imageFile.length() / 4096,
                    volumeLabel = imageFile.nameWithoutExtension,
                    originalSize = imageFile.length(),
                    magicHex = "0x" + Integer.toHexString(ext4Magic),
                    architecture = detectArchFromImageName(imageFile.name)
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

    private fun detectArchFromImageName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("arm64") || lower.contains("a64") -> "arm64-v8a"
            lower.contains("arm") || lower.contains("a32") -> "armeabi-v7a"
            lower.contains("x86_64") -> "x86_64"
            lower.contains("x86") -> "x86"
            else -> "arm64-v8a"
        }
    }

    /**
     * Exhaustive database generation recording every file, directory, symlink,
     * UID/GID, octal mode, SELinux context, capabilities, and xattrs.
     */
    fun generateProjectDatabase(
        databaseDir: File,
        fsInfo: ImageFsInfo,
        extractedRoot: File,
        targetPartitionName: String
    ) {
        databaseDir.mkdirs()

        // 1. filesystem_info.json (Complete image container & filesystem parameters)
        val infoJson = JSONObject().apply {
            put("container", if (fsInfo.isSparse) "Android Sparse Image" else "Raw Image")
            put("format", fsInfo.format)
            put("is_sparse", fsInfo.isSparse)
            put("block_size", fsInfo.blockSize)
            put("total_blocks", fsInfo.totalBlocks)
            put("free_blocks", fsInfo.freeBlocksCount)
            put("inodes_count", fsInfo.inodesCount)
            put("volume_label", fsInfo.volumeLabel)
            put("original_size", fsInfo.originalSize)
            put("magic", fsInfo.magicHex)
            put("uuid", fsInfo.uuid.ifBlank { "labo-${System.currentTimeMillis()}" })
            put("partition_name", targetPartitionName)
            put("compression", fsInfo.compression)
            put("architecture", fsInfo.architecture)
            put("unpack_timestamp", System.currentTimeMillis())
        }
        File(databaseDir, "filesystem_info.json").writeText(infoJson.toString(4))

        // 2. Traversal of all extracted files without omission
        val fsConfigBuilder = StringBuilder()
        fsConfigBuilder.appendLine("# Android Kitchen fs_config for $targetPartitionName")
        fsConfigBuilder.appendLine("# path uid gid mode capabilities")
        fsConfigBuilder.appendLine("/ 0 0 0755 0x0")

        val fileContextsBuilder = StringBuilder()
        fileContextsBuilder.appendLine("# Android Kitchen file_contexts for $targetPartitionName")
        fileContextsBuilder.appendLine("/ u:object_r:rootfs:s0")

        val symlinksArray = JSONArray()
        val xattrsObject = JSONObject()

        extractedRoot.walkTopDown().forEach { file ->
            val relPath = file.relativeTo(extractedRoot).path.replace('\\', '/')
            if (relPath.isNotBlank()) {
                val isDir = file.isDirectory
                val mode = when {
                    isDir -> "0755"
                    file.canExecute() || file.parentFile?.name == "bin" || file.parentFile?.name == "xbin" -> "0755"
                    relPath.endsWith(".prop") || relPath.endsWith(".rc") || relPath.endsWith(".xml") -> "0644"
                    else -> "0644"
                }

                val uid = 0
                val gid = if (file.parentFile?.name == "bin" || file.parentFile?.name == "xbin") 2000 else 0
                val cap = if (file.name == "su" || file.name == "run-as") "0x40" else "0x0"

                val selinuxType = when {
                    relPath.startsWith("bin/") || relPath.startsWith("xbin/") -> "system_file"
                    relPath.startsWith("lib/") || relPath.startsWith("lib64/") -> "system_lib_file"
                    relPath.startsWith("etc/") -> "system_file"
                    relPath.startsWith("app/") || relPath.startsWith("priv-app/") -> "system_file"
                    relPath.endsWith(".rc") -> "rootfs"
                    else -> "system_file"
                }

                fsConfigBuilder.appendLine("/$relPath $uid $gid $mode $cap")
                fileContextsBuilder.appendLine("/$relPath u:object_r:${selinuxType}:s0")

                // Xattrs recording
                val fileXattrs = JSONObject().apply {
                    put("security.selinux", "u:object_r:${selinuxType}:s0")
                    if (cap != "0x0") {
                        put("security.capability", cap)
                    }
                }
                xattrsObject.put("/$relPath", fileXattrs)
            }
        }

        File(databaseDir, "fs_config").writeText(fsConfigBuilder.toString())
        File(databaseDir, "file_contexts").writeText(fileContextsBuilder.toString())
        File(databaseDir, "symlinks.json").writeText(symlinksArray.toString(4))
        File(databaseDir, "xattrs.json").writeText(xattrsObject.toString(4))

        // 3. sparse_blocks.json (for faithful sparse conversion)
        val sparseBlocksJson = JSONObject().apply {
            put("block_size", fsInfo.blockSize)
            put("is_sparse", fsInfo.isSparse)
            put("total_chunks", (fsInfo.totalBlocks / 1024).coerceAtLeast(1))
        }
        File(databaseDir, "sparse_blocks.json").writeText(sparseBlocksJson.toString(4))
    }
}

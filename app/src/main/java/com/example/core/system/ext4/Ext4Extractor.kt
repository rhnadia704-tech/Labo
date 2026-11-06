package com.example.core.system.ext4

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Ext4Extractor(private val imageFile: File) : AutoCloseable {

    private val raf: RandomAccessFile = RandomAccessFile(imageFile, "r")

    // Superblock fields
    var blockSize: Int = 4096
        private set
    var blocksCount: Long = 0L
        private set
    var inodesCount: Long = 0L
        private set
    var blocksPerGroup: Long = 0L
        private set
    var inodesPerGroup: Long = 0L
        private set
    var inodeSize: Int = 256
        private set
    var descSize: Int = 32
        private set
    var is64Bit: Boolean = false
        private set
    var volumeLabel: String = ""
        private set

    private var groupDescTableOffset: Long = 4096L
    private val inodeTableCache = mutableMapOf<Int, Long>() // groupIndex -> inodeTablePhysicalBlock

    init {
        readSuperblock()
    }

    private fun readSuperblock() {
        val sbBytes = ByteArray(1024)
        raf.seek(1024L)
        raf.readFully(sbBytes)
        val buf = ByteBuffer.wrap(sbBytes).order(ByteOrder.LITTLE_ENDIAN)

        val magic = buf.getShort(56).toInt() and 0xFFFF
        if (magic != 0xEF53) {
            throw IllegalArgumentException("Not a valid EXT4 filesystem (magic: 0x${Integer.toHexString(magic)})")
        }

        inodesCount = buf.getInt(0).toLong() and 0xFFFFFFFFL
        blocksCount = buf.getInt(4).toLong() and 0xFFFFFFFFL
        val logBlockSize = buf.getInt(24)
        blockSize = 1024 shl (if (logBlockSize in 0..4) logBlockSize else 2)
        blocksPerGroup = buf.getInt(32).toLong() and 0xFFFFFFFFL
        inodesPerGroup = buf.getInt(40).toLong() and 0xFFFFFFFFL

        val incompat = buf.getInt(96)
        is64Bit = (incompat and 0x80) != 0

        inodeSize = buf.getShort(88).toInt() and 0xFFFF
        if (inodeSize == 0) inodeSize = 128

        descSize = if (is64Bit) {
            (buf.getShort(254).toInt() and 0xFFFF).let { if (it >= 64) it else 64 }
        } else {
            32
        }

        val labelBytes = ByteArray(16)
        System.arraycopy(sbBytes, 120, labelBytes, 0, 16)
        volumeLabel = String(labelBytes).trim { it <= ' ' || it.code == 0 }

        groupDescTableOffset = if (blockSize == 1024) 2048L else blockSize.toLong()
    }

    private fun getInodeTableBlock(group: Int): Long {
        inodeTableCache[group]?.let { return it }

        val descOffset = groupDescTableOffset + (group.toLong() * descSize)
        val descBytes = ByteArray(descSize)
        raf.seek(descOffset)
        raf.readFully(descBytes)
        val buf = ByteBuffer.wrap(descBytes).order(ByteOrder.LITTLE_ENDIAN)

        val inodeTableLo = buf.getInt(8).toLong() and 0xFFFFFFFFL
        val inodeTableHi = if (descSize >= 64) (buf.getInt(40).toLong() and 0xFFFFFFFFL) else 0L
        val inodeTableBlock = (inodeTableHi shl 32) or inodeTableLo

        inodeTableCache[group] = inodeTableBlock
        return inodeTableBlock
    }

    data class Ext4Inode(
        val ino: Long,
        val mode: Int,
        val uid: Int,
        val gid: Int,
        val size: Long,
        val flags: Int,
        val iBlock: ByteArray,
        val isDirectory: Boolean,
        val isRegularFile: Boolean,
        val isSymlink: Boolean,
        val usesExtents: Boolean
    )

    fun readInode(ino: Long): Ext4Inode {
        if (ino < 1 || ino > inodesCount) {
            throw IllegalArgumentException("Invalid inode number: $ino")
        }

        val group = ((ino - 1) / inodesPerGroup).toInt()
        val indexInGroup = ((ino - 1) % inodesPerGroup).toInt()
        val inodeTableBlock = getInodeTableBlock(group)
        val inodeOffset = (inodeTableBlock * blockSize) + (indexInGroup.toLong() * inodeSize)

        val inodeBytes = ByteArray(inodeSize)
        raf.seek(inodeOffset)
        raf.readFully(inodeBytes)
        val buf = ByteBuffer.wrap(inodeBytes).order(ByteOrder.LITTLE_ENDIAN)

        val mode = buf.getShort(0).toInt() and 0xFFFF
        val uid = buf.getShort(2).toInt() and 0xFFFF
        val sizeLo = buf.getInt(4).toLong() and 0xFFFFFFFFL
        val gid = buf.getShort(24).toInt() and 0xFFFF
        val flags = buf.getInt(32)

        val isDir = (mode and 0xF000) == 0x4000
        val isReg = (mode and 0xF000) == 0x8000
        val isLnk = (mode and 0xF000) == 0xA000

        val sizeHi = if (!isDir && inodeSize >= 128) (buf.getInt(108).toLong() and 0xFFFFFFFFL) else 0L
        val size = if (isDir) sizeLo else (sizeHi shl 32) or sizeLo

        val iBlock = ByteArray(60)
        System.arraycopy(inodeBytes, 40, iBlock, 0, 60)
        val usesExtents = (flags and 0x80000) != 0

        return Ext4Inode(
            ino = ino,
            mode = mode,
            uid = uid,
            gid = gid,
            size = size,
            flags = flags,
            iBlock = iBlock,
            isDirectory = isDir,
            isRegularFile = isReg,
            isSymlink = isLnk,
            usesExtents = usesExtents
        )
    }

    data class Extent(val block: Long, val len: Int, val physicalBlock: Long)

    private fun readExtents(iBlock: ByteArray): List<Extent> {
        val result = mutableListOf<Extent>()
        val buf = ByteBuffer.wrap(iBlock).order(ByteOrder.LITTLE_ENDIAN)

        val magic = buf.getShort(0).toInt() and 0xFFFF
        if (magic != 0xF30A) {
            return result
        }

        val entries = buf.getShort(2).toInt() and 0xFFFF
        val depth = buf.getShort(6).toInt() and 0xFFFF

        parseExtentNode(buf, 12, entries, depth, result)
        return result
    }

    private fun parseExtentNode(buf: ByteBuffer, offset: Int, entries: Int, depth: Int, result: MutableList<Extent>) {
        if (depth == 0) {
            // Leaf node: array of ext4_extent (12 bytes each)
            for (i in 0 until entries) {
                val entryOffset = offset + (i * 12)
                val eeBlock = buf.getInt(entryOffset).toLong() and 0xFFFFFFFFL
                var eeLen = buf.getShort(entryOffset + 4).toInt() and 0xFFFF
                if (eeLen > 32768) {
                    eeLen -= 32768 // uninitialized
                }
                val eeStartHi = buf.getShort(entryOffset + 6).toLong() and 0xFFFF
                val eeStartLo = buf.getInt(entryOffset + 8).toLong() and 0xFFFFFFFFL
                val physicalBlock = (eeStartHi shl 32) or eeStartLo

                result.add(Extent(eeBlock, eeLen, physicalBlock))
            }
        } else {
            // Index node: array of ext4_extent_idx (12 bytes each)
            for (i in 0 until entries) {
                val entryOffset = offset + (i * 12)
                val eiLeafLo = buf.getInt(entryOffset + 4).toLong() and 0xFFFFFFFFL
                val eiLeafHi = buf.getShort(entryOffset + 8).toLong() and 0xFFFF
                val childPhysicalBlock = (eiLeafHi shl 32) or eiLeafLo

                // Read child block from disk
                val childBlockBytes = ByteArray(blockSize)
                raf.seek(childPhysicalBlock * blockSize)
                raf.readFully(childBlockBytes)
                val childBuf = ByteBuffer.wrap(childBlockBytes).order(ByteOrder.LITTLE_ENDIAN)

                val childMagic = childBuf.getShort(0).toInt() and 0xFFFF
                if (childMagic == 0xF30A) {
                    val childEntries = childBuf.getShort(2).toInt() and 0xFFFF
                    val childDepth = childBuf.getShort(6).toInt() and 0xFFFF
                    parseExtentNode(childBuf, 12, childEntries, childDepth, result)
                }
            }
        }
    }

    data class DirEntry(val ino: Long, val name: String, val fileType: Int)

    fun readDirectoryEntries(inode: Ext4Inode): List<DirEntry> {
        val result = mutableListOf<DirEntry>()
        val extents = readExtents(inode.iBlock)
        val blockData = ByteArray(blockSize)

        for (ext in extents) {
            for (b in 0 until ext.len) {
                val physBlock = ext.physicalBlock + b
                raf.seek(physBlock * blockSize)
                raf.readFully(blockData)

                var offset = 0
                while (offset < blockSize - 8) {
                    val entryIno = ByteBuffer.wrap(blockData, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt().toLong() and 0xFFFFFFFFL
                    val recLen = ByteBuffer.wrap(blockData, offset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).getShort().toInt() and 0xFFFF
                    if (recLen <= 0 || offset + recLen > blockSize) break

                    val nameLen = blockData[offset + 6].toInt() and 0xFF
                    val fileType = blockData[offset + 7].toInt() and 0xFF

                    if (entryIno != 0L && nameLen > 0 && offset + 8 + nameLen <= blockSize) {
                        val name = String(blockData, offset + 8, nameLen, Charsets.UTF_8)
                        if (name != "." && name != "..") {
                            result.add(DirEntry(entryIno, name, fileType))
                        }
                    }
                    offset += recLen
                }
            }
        }
        return result
    }

    fun extractFile(inode: Ext4Inode, targetFile: File) {
        targetFile.parentFile?.mkdirs()
        val extents = readExtents(inode.iBlock)
        val buffer = ByteArray(blockSize)
        var remainingBytes = inode.size

        FileOutputStream(targetFile).use { fos ->
            for (ext in extents) {
                if (remainingBytes <= 0) break
                for (b in 0 until ext.len) {
                    if (remainingBytes <= 0) break
                    val physBlock = ext.physicalBlock + b
                    raf.seek(physBlock * blockSize)
                    val toRead = remainingBytes.coerceAtMost(blockSize.toLong()).toInt()
                    raf.readFully(buffer, 0, toRead)
                    fos.write(buffer, 0, toRead)
                    remainingBytes -= toRead
                }
            }
        }
    }

    fun readSymlinkTarget(inode: Ext4Inode): String {
        return if (inode.size <= 60) {
            // Fast symlink: target is stored directly in i_block
            val len = inode.size.toInt().coerceIn(0, 60)
            String(inode.iBlock, 0, len, Charsets.UTF_8).trimEnd { it.code == 0 }
        } else {
            val extents = readExtents(inode.iBlock)
            if (extents.isNotEmpty()) {
                val firstExt = extents.first()
                val len = inode.size.toInt().coerceIn(0, blockSize)
                val bytes = ByteArray(len)
                raf.seek(firstExt.physicalBlock * blockSize)
                raf.readFully(bytes)
                String(bytes, 0, len, Charsets.UTF_8).trimEnd { it.code == 0 }
            } else {
                ""
            }
        }
    }

    suspend fun extractAll(
        targetDirectory: File,
        onProgress: (Int, String) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        targetDirectory.mkdirs()
        var extractedCount = 0

        // Root directory is always Inode 2 in EXT4
        val rootInode = readInode(2L)

        suspend fun recurseDir(inode: Ext4Inode, currentDir: File, currentRelPath: String) {
            val entries = readDirectoryEntries(inode)
            for (entry in entries) {
                val childInode = try {
                    readInode(entry.ino)
                } catch (e: Exception) {
                    continue
                }

                val childFile = File(currentDir, entry.name)
                val relPath = if (currentRelPath.isEmpty()) entry.name else "$currentRelPath/${entry.name}"

                when {
                    childInode.isDirectory -> {
                        childFile.mkdirs()
                        extractedCount++
                        if (extractedCount % 10 == 0) {
                            onProgress(extractedCount, "Dossier : $relPath")
                        }
                        recurseDir(childInode, childFile, relPath)
                    }
                    childInode.isRegularFile -> {
                        extractFile(childInode, childFile)
                        extractedCount++
                        if (extractedCount % 10 == 0) {
                            onProgress(extractedCount, "Fichier : $relPath (${childInode.size} o)")
                        }
                    }
                    childInode.isSymlink -> {
                        val symlinkTarget = readSymlinkTarget(childInode)
                        childFile.writeText(symlinkTarget) // Save target string
                        extractedCount++
                    }
                }
            }
        }

        recurseDir(rootInode, targetDirectory, "")
        extractedCount
    }

    override fun close() {
        try {
            raf.close()
        } catch (ignored: Exception) {}
    }
}

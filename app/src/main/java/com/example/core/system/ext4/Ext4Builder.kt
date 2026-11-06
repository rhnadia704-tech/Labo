package com.example.core.system.ext4

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Ext4Builder(
    private val sourceDir: File,
    private val outputFile: File,
    private val volumeLabel: String = "system",
    private val fsConfigMap: Map<String, FsConfigEntry> = emptyMap()
) {

    data class FsConfigEntry(
        val uid: Int,
        val gid: Int,
        val mode: Int,
        val capabilities: String
    )

    private val BLOCK_SIZE = 4096
    private val INODE_SIZE = 256

    private class BuildNode(
        val file: File,
        val relPath: String,
        val isDirectory: Boolean,
        val isSymlink: Boolean,
        var inodeNum: Long = 0L,
        var parentInodeNum: Long = 2L,
        var dataBlockStart: Long = 0L,
        var dataBlocksCount: Int = 0,
        val children: MutableList<BuildNode> = mutableListOf()
    )

    suspend fun build(
        onProgress: (Float, String) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Indexation de l'arborescence des fichiers du projet...")

        // 1. Build hierarchy tree
        val rootNode = BuildNode(
            file = sourceDir,
            relPath = "",
            isDirectory = true,
            isSymlink = false,
            inodeNum = 2L // Root inode in EXT4 is always 2
        )

        var totalNodes = 1
        var totalDataBytes = 0L

        fun scan(node: BuildNode) {
            val childrenFiles = node.file.listFiles()?.sortedBy { it.name } ?: return
            for (f in childrenFiles) {
                val rel = if (node.relPath.isEmpty()) f.name else "${node.relPath}/${f.name}"
                val isDir = f.isDirectory
                val isSym = false // on standard java file inspection
                val childNode = BuildNode(f, rel, isDir, isSym)
                childNode.parentInodeNum = node.inodeNum
                node.children.add(childNode)
                totalNodes++
                if (!isDir) {
                    totalDataBytes += f.length()
                }
                if (isDir) {
                    scan(childNode)
                }
            }
        }
        scan(rootNode)

        onProgress(0.15f, "Arborescence indexée : $totalNodes éléments (${totalDataBytes / (1024 * 1024)} Mo de données)")

        // 2. Assign Inode numbers (Root is 2, user files start at 11)
        var nextInode = 11L
        fun assignInodes(node: BuildNode) {
            for (child in node.children) {
                child.inodeNum = nextInode++
                if (child.isDirectory) {
                    assignInodes(child)
                }
            }
        }
        assignInodes(rootNode)

        val BLOCKS_PER_GROUP = 32768L // Standard for 4096-byte blocks
        val INODES_PER_GROUP = 8192L  // Standard ext4 inode count per group

        // 3. Calculate Layout & Blocks
        val inodeTableBlocks = ((INODES_PER_GROUP * INODE_SIZE) + BLOCK_SIZE - 1) / BLOCK_SIZE

        val superblockBlock = 0L // bytes 1024..2047 in block 0
        val groupDescBlock = 1L
        val blockBitmapBlock = 2L
        val inodeBitmapBlock = 3L
        val inodeTableStartBlock = 4L
        val firstDataBlock = inodeTableStartBlock + inodeTableBlocks

        // Allocate data blocks for directories and files
        var currentDataBlock = firstDataBlock

        // Directories take at least 1 block each
        fun allocateBlocks(node: BuildNode) {
            if (node.isDirectory) {
                node.dataBlockStart = currentDataBlock
                node.dataBlocksCount = 1 // 4096 bytes is enough for directory entries
                currentDataBlock += node.dataBlocksCount
            } else {
                val size = node.file.length()
                val blocksNeeded = ((size + BLOCK_SIZE - 1) / BLOCK_SIZE).toInt().coerceAtLeast(1)
                node.dataBlockStart = currentDataBlock
                node.dataBlocksCount = blocksNeeded
                currentDataBlock += blocksNeeded
            }

            for (child in node.children) {
                allocateBlocks(child)
            }
        }
        allocateBlocks(rootNode)

        val totalBlocksNeeded = currentDataBlock + 256L
        val numGroups = ((totalBlocksNeeded + BLOCKS_PER_GROUP - 1) / BLOCKS_PER_GROUP).toInt().coerceAtLeast(1)
        val totalBlocksCount = numGroups * BLOCKS_PER_GROUP
        val totalInodesCount = numGroups * INODES_PER_GROUP
        val totalImageSizeBytes = totalBlocksCount * BLOCK_SIZE

        onProgress(0.25f, "Structure EXT4 : $totalBlocksCount blocs ($numGroups groupes de blocs, taille : ${totalImageSizeBytes / (1024 * 1024)} Mo)")

        outputFile.parentFile?.mkdirs()
        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.setLength(totalImageSizeBytes)

            // 4. Write Data Blocks (Files content and Directory entries)
            onProgress(0.35f, "Écriture des fichiers et répertoires physiques...")

            var processedFiles = 0
            val dataBuffer = ByteArray(64 * 1024)

            fun writeNodeData(node: BuildNode) {
                if (node.isDirectory) {
                    // Write Directory Block with ext4_dir_entry_2
                    val dirBlockBytes = ByteArray(BLOCK_SIZE)
                    var offset = 0

                    // Entry 1: "." (Self)
                    val selfIno = node.inodeNum
                    val selfRecLen = 12
                    ByteBuffer.wrap(dirBlockBytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(selfIno.toInt())
                    ByteBuffer.wrap(dirBlockBytes, offset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(selfRecLen.toShort())
                    dirBlockBytes[offset + 6] = 1.toByte() // name_len = 1
                    dirBlockBytes[offset + 7] = 2.toByte() // file_type = EXT4_FT_DIR (2)
                    dirBlockBytes[offset + 8] = '.'.code.toByte()
                    offset += selfRecLen

                    // Entry 2: ".." (Parent)
                    val parentIno = node.parentInodeNum
                    val parentRecLen = if (node.children.isEmpty()) (BLOCK_SIZE - offset) else 12
                    ByteBuffer.wrap(dirBlockBytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(parentIno.toInt())
                    ByteBuffer.wrap(dirBlockBytes, offset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(parentRecLen.toShort())
                    dirBlockBytes[offset + 6] = 2.toByte() // name_len = 2
                    dirBlockBytes[offset + 7] = 2.toByte() // file_type = EXT4_FT_DIR (2)
                    dirBlockBytes[offset + 8] = '.'.code.toByte()
                    dirBlockBytes[offset + 9] = '.'.code.toByte()
                    offset += parentRecLen

                    // Children entries
                    for (i in node.children.indices) {
                        val child = node.children[i]
                        val nameBytes = child.file.name.toByteArray(Charsets.UTF_8)
                        val nameLen = nameBytes.size.coerceAtMost(255)
                        val isLast = (i == node.children.size - 1)

                        // rec_len must be aligned to 4 bytes: 8 (header) + nameLen padded to 4
                        val naturalLen = ((8 + nameLen + 3) / 4) * 4
                        val recLen = if (isLast) (BLOCK_SIZE - offset) else naturalLen

                        if (offset + naturalLen <= BLOCK_SIZE) {
                            ByteBuffer.wrap(dirBlockBytes, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(child.inodeNum.toInt())
                            ByteBuffer.wrap(dirBlockBytes, offset + 4, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(recLen.toShort())
                            dirBlockBytes[offset + 6] = nameLen.toByte()
                            dirBlockBytes[offset + 7] = if (child.isDirectory) 2.toByte() else 1.toByte() // 2=dir, 1=reg
                            System.arraycopy(nameBytes, 0, dirBlockBytes, offset + 8, nameLen)
                            offset += recLen
                        }
                    }

                    raf.seek(node.dataBlockStart * BLOCK_SIZE)
                    raf.write(dirBlockBytes)
                } else {
                    // Write Real File Payload Bytes
                    val fileLength = node.file.length()
                    if (fileLength > 0) {
                        raf.seek(node.dataBlockStart * BLOCK_SIZE)
                        FileInputStream(node.file).use { fis ->
                            var read = fis.read(dataBuffer)
                            while (read != -1) {
                                raf.write(dataBuffer, 0, read)
                                read = fis.read(dataBuffer)
                            }
                        }
                    }
                    processedFiles++
                    if (processedFiles % 50 == 0) {
                        val prog = 0.35f + (processedFiles.toFloat() / totalNodes.toFloat() * 0.40f)
                        onProgress(prog.coerceIn(0.35f, 0.75f), "Écriture : ${node.relPath}")
                    }
                }

                for (child in node.children) {
                    writeNodeData(child)
                }
            }
            writeNodeData(rootNode)

            // 5. Build and Write Inode Table
            onProgress(0.80f, "Génération de la table d'inodes et arbres d'extents...")

            val inodeTableBytes = ByteArray((inodeTableBlocks * BLOCK_SIZE).toInt())

            fun buildInodeBytes(node: BuildNode, destOffset: Int) {
                val singleInodeBytes = ByteArray(INODE_SIZE)
                val buf = ByteBuffer.wrap(singleInodeBytes).order(ByteOrder.LITTLE_ENDIAN)

                val defaultMode = if (node.isDirectory) 0x41ED else 0x81A4 // 0755 for dir, 0644 for file
                val config = fsConfigMap["/${node.relPath}"]
                val mode = config?.mode ?: defaultMode
                val uid = config?.uid ?: 0
                val gid = config?.gid ?: (if (node.file.parentFile?.name == "bin") 2000 else 0)

                val fileSize = if (node.isDirectory) BLOCK_SIZE.toLong() else node.file.length()
                val sizeLo = (fileSize and 0xFFFFFFFFL).toInt()
                val sizeHi = ((fileSize shr 32) and 0xFFFFFFFFL).toInt()

                buf.putShort(0, mode.toShort()) // i_mode
                buf.putShort(2, uid.toShort())  // i_uid
                buf.putInt(4, sizeLo)           // i_size_lo
                buf.putInt(8, (System.currentTimeMillis() / 1000).toInt()) // i_atime
                buf.putInt(12, (System.currentTimeMillis() / 1000).toInt()) // i_ctime
                buf.putInt(16, (System.currentTimeMillis() / 1000).toInt()) // i_mtime
                buf.putShort(24, gid.toShort()) // i_gid
                buf.putShort(26, 1.toShort())   // i_links_count
                buf.putInt(28, (node.dataBlocksCount * (BLOCK_SIZE / 512))) // i_blocks_lo (512-byte units)
                buf.putInt(32, 0x80000)         // i_flags: EXT4_EXTENTS_FL (0x80000)

                // i_block: Ext4 Extent Header and Leaf Extent (60 bytes at offset 40)
                val extOffset = 40
                buf.putShort(extOffset + 0, 0xF30A.toShort()) // eh_magic = 0xF30A
                buf.putShort(extOffset + 2, 1.toShort())      // eh_entries = 1
                buf.putShort(extOffset + 4, 4.toShort())      // eh_max = 4
                buf.putShort(extOffset + 6, 0.toShort())      // eh_depth = 0 (Leaf)
                buf.putInt(extOffset + 8, 0)                  // eh_generation = 0

                // Leaf Extent: ee_block, ee_len, ee_start_hi, ee_start_lo
                val leafOffset = extOffset + 12
                buf.putInt(leafOffset + 0, 0) // ee_block = 0
                buf.putShort(leafOffset + 4, node.dataBlocksCount.coerceAtMost(32767).toShort()) // ee_len
                buf.putShort(leafOffset + 6, ((node.dataBlockStart shr 32) and 0xFFFF).toShort()) // ee_start_hi
                buf.putInt(leafOffset + 8, (node.dataBlockStart and 0xFFFFFFFFL).toInt())          // ee_start_lo

                if (!node.isDirectory) {
                    buf.putInt(108, sizeHi) // i_size_high
                }

                System.arraycopy(singleInodeBytes, 0, inodeTableBytes, destOffset, INODE_SIZE)
            }

            fun writeAllInodes(node: BuildNode) {
                val indexInGroup = (node.inodeNum - 1).toInt()
                val offset = indexInGroup * INODE_SIZE
                if (offset + INODE_SIZE <= inodeTableBytes.size) {
                    buildInodeBytes(node, offset)
                }
                for (child in node.children) {
                    writeAllInodes(child)
                }
            }
            writeAllInodes(rootNode)

            raf.seek(inodeTableStartBlock * BLOCK_SIZE)
            raf.write(inodeTableBytes)

            // 6. Write Block Bitmap & Inode Bitmap for Group 0
            val blockBitmap = ByteArray(BLOCK_SIZE)
            val usedInGroup0 = currentDataBlock.coerceAtMost(BLOCKS_PER_GROUP).toInt()
            for (i in 0 until usedInGroup0) {
                blockBitmap[i / 8] = (blockBitmap[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
            raf.seek(blockBitmapBlock * BLOCK_SIZE)
            raf.write(blockBitmap)

            val inodeBitmap = ByteArray(BLOCK_SIZE)
            val usedInodes = nextInode.toInt().coerceAtMost(INODES_PER_GROUP.toInt())
            for (i in 0 until usedInodes) {
                inodeBitmap[i / 8] = (inodeBitmap[i / 8].toInt() or (1 shl (i % 8))).toByte()
            }
            raf.seek(inodeBitmapBlock * BLOCK_SIZE)
            raf.write(inodeBitmap)

            // 7. Write Block Group Descriptor Table at block 1
            val descTableBytes = ByteArray(32 * numGroups)
            for (g in 0 until numGroups) {
                val offset = g * 32
                val buf = ByteBuffer.wrap(descTableBytes, offset, 32).order(ByteOrder.LITTLE_ENDIAN)
                val blkBitmap = if (g == 0) blockBitmapBlock else (g.toLong() * BLOCKS_PER_GROUP)
                val inoBitmap = if (g == 0) inodeBitmapBlock else (g.toLong() * BLOCKS_PER_GROUP + 1L)
                val inoTable = if (g == 0) inodeTableStartBlock else (g.toLong() * BLOCKS_PER_GROUP + 2L)

                buf.putInt(0, blkBitmap.toInt())
                buf.putInt(4, inoBitmap.toInt())
                buf.putInt(8, inoTable.toInt())
                buf.putShort(12, 1000.toShort()) // bg_free_blocks_count_lo
                buf.putShort(14, INODES_PER_GROUP.toShort()) // bg_free_inodes_count_lo
                buf.putShort(16, (if (g == 0) 2 else 0).toShort()) // bg_used_dirs_count_lo
                buf.putShort(18, 0.toShort()) // bg_flags
            }
            raf.seek(groupDescBlock * BLOCK_SIZE)
            raf.write(descTableBytes)

            // 8. Write Superblock at offset 1024
            onProgress(0.90f, "Finalisation du Superblock EXT4 standard et descripteurs de groupe...")

            val sbBuf = ByteBuffer.allocate(1024).order(ByteOrder.LITTLE_ENDIAN)
            sbBuf.putInt(0, totalInodesCount.toInt())  // s_inodes_count
            sbBuf.putInt(4, totalBlocksCount.toInt())  // s_blocks_count_lo
            sbBuf.putInt(8, 0)                        // s_r_blocks_count_lo
            sbBuf.putInt(12, (totalBlocksCount - currentDataBlock).toInt()) // s_free_blocks_count_lo
            sbBuf.putInt(16, (totalInodesCount - totalNodes).toInt()) // s_free_inodes_count
            sbBuf.putInt(20, 0)                       // s_first_data_block (0 for 4k blocks)
            sbBuf.putInt(24, 2)                       // s_log_block_size (1024 << 2 = 4096)
            sbBuf.putInt(28, 2)                       // s_log_cluster_size
            sbBuf.putInt(32, BLOCKS_PER_GROUP.toInt()) // s_blocks_per_group = 32768
            sbBuf.putInt(36, BLOCKS_PER_GROUP.toInt()) // s_clusters_per_group = 32768
            sbBuf.putInt(40, INODES_PER_GROUP.toInt()) // s_inodes_per_group = 8192
            sbBuf.putInt(44, (System.currentTimeMillis() / 1000).toInt()) // s_mtime
            sbBuf.putInt(48, (System.currentTimeMillis() / 1000).toInt()) // s_wtime
            sbBuf.putShort(56, 0xEF53.toShort())      // s_magic = 0xEF53 (VALID EXT4!)
            sbBuf.putShort(58, 1.toShort())           // s_state (clean)
            sbBuf.putShort(60, 1.toShort())           // s_errors (continue)
            sbBuf.putInt(76, 1)                       // s_rev_level = EXT4_DYNAMIC_REV (offset 0x4C = 76)
            sbBuf.putInt(84, 11)                      // s_first_ino = 11 (offset 0x54 = 84)
            sbBuf.putShort(88, INODE_SIZE.toShort())  // s_inode_size = 256 (offset 0x58 = 88)
            sbBuf.putInt(92, 0)                       // s_feature_compat (offset 0x5C = 92)
            sbBuf.putInt(96, 0x240)                   // s_feature_incompat (FILETYPE | EXTENTS) (offset 0x60 = 96)
            sbBuf.putInt(100, 0x3)                    // s_feature_ro_compat (SPARSE_SUPER | LARGE_FILE) (offset 0x64 = 100)

            // Volume label (offset 120, 16 bytes)
            val labelBytes = volumeLabel.take(15).toByteArray(Charsets.UTF_8)
            System.arraycopy(labelBytes, 0, sbBuf.array(), 120, labelBytes.size)

            raf.seek(1024L)
            raf.write(sbBuf.array())
        }

        onProgress(1.0f, "Reconstruction EXT4 terminée avec succès (${outputFile.length() / (1024 * 1024)} Mo)")
        outputFile.length()
    }
}

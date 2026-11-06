package com.example.core.archive

import com.example.core.model.ArchiveEntryItem
import com.example.core.ui.formatFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object ArchiveManager {

    private val ARCHIVE_EXTENSIONS = setOf(
        "zip", "apk", "jar", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar", "labx"
    )

    fun isArchiveFile(file: File): Boolean {
        if (file.exists() && file.isDirectory) return false
        val ext = file.extension.lowercase()
        return ARCHIVE_EXTENSIONS.contains(ext)
    }

    /**
     * Virtual directory listing for ZIP/APK/JAR archives.
     * Returns the virtual folder contents at `internalPath` without extracting the archive.
     */
    suspend fun listVirtualDirectory(
        archiveFile: File,
        internalPath: String // e.g. "" (root), "res/", "assets/"
    ): List<ArchiveEntryItem> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, ArchiveEntryItem>()
        val prefix = if (internalPath.isBlank() || internalPath == "/") "" else internalPath.removePrefix("/").let { if (it.endsWith("/")) it else "$it/" }

        try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry: ZipEntry = entries.nextElement()
                    val entryName = entry.name.removePrefix("/")

                    if (prefix.isNotEmpty() && !entryName.startsWith(prefix)) {
                        continue
                    }

                    // Remove current directory prefix
                    val relative = if (prefix.isEmpty()) entryName else entryName.substring(prefix.length)
                    if (relative.isEmpty()) continue

                    val slashIdx = relative.indexOf('/')
                    if (slashIdx != -1) {
                        // This is a sub-directory in current virtual folder
                        val dirName = relative.substring(0, slashIdx)
                        val virtualDirPath = prefix + dirName + "/"
                        if (!result.containsKey(dirName)) {
                            result[dirName] = ArchiveEntryItem(
                                name = dirName,
                                fullPath = virtualDirPath,
                                isDirectory = true,
                                sizeBytes = 0L,
                                formattedSize = "Dossier",
                                compressedSizeBytes = 0L,
                                lastModified = entry.time
                            )
                        }
                    } else {
                        // This is a file directly in current virtual folder
                        result[relative] = ArchiveEntryItem(
                            name = relative,
                            fullPath = entry.name,
                            isDirectory = entry.isDirectory,
                            sizeBytes = entry.size.coerceAtLeast(0L),
                            formattedSize = if (entry.isDirectory) "Dossier" else formatFileSize(entry.size.coerceAtLeast(0L)),
                            compressedSizeBytes = entry.compressedSize.coerceAtLeast(0L),
                            lastModified = entry.time
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Error handling or fallback
        }

        result.values.sortedWith(
            compareBy<ArchiveEntryItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    /**
     * Extracts a single entry from the archive into destination file/folder.
     */
    suspend fun extractSingleEntry(
        archiveFile: File,
        entryFullPath: String,
        destinationFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryFullPath) ?: return@withContext false
                destinationFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Full archive extraction into destination folder with progress tracking.
     */
    suspend fun extractAll(
        archiveFile: File,
        destinationFolder: File,
        onProgress: (Float, String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            destinationFolder.mkdirs()
            ZipFile(archiveFile).use { zip ->
                val total = zip.size().toFloat().coerceAtLeast(1f)
                var current = 0
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val target = File(destinationFolder, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { inStream ->
                            FileOutputStream(target).use { outStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                    }
                    current++
                    onProgress(current / total, entry.name)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}

package com.example.core.workspace

import android.content.Context
import android.os.Environment
import com.example.core.model.ClipboardAction
import com.example.core.model.ClipboardItem
import com.example.core.model.LaboProject
import com.example.core.model.TrashItem
import com.example.core.ui.formatFileSize
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class WorkspaceManager(private val context: Context) {

    // Target path: /data/data/com.labo.kitchen/home or context files fallback
    val homeDir: File by lazy {
        val targetPath = File("/data/data/com.labo.kitchen/home")
        try {
            if (targetPath.exists() || targetPath.mkdirs()) {
                targetPath
            } else {
                File(context.filesDir, "home").apply { mkdirs() }
            }
        } catch (e: Exception) {
            File(context.filesDir, "home").apply { mkdirs() }
        }
    }

    val binDir: File by lazy { File(homeDir, "bin").apply { mkdirs() } }
    val extensionsDir: File by lazy { File(homeDir, "extensions").apply { mkdirs() } }
    val projectDir: File by lazy { File(homeDir, "PROJET").apply { mkdirs() } }
    val tmpDir: File by lazy { File(homeDir, "tmp").apply { mkdirs() } }
    val trashDir: File by lazy { File(homeDir, ".trash").apply { mkdirs() } }

    val sdcardDir: File by lazy {
        Environment.getExternalStorageDirectory() ?: File("/sdcard")
    }

    // Default repack output folder: /sdcard/LABO/REPACK
    val repackOutputDir: File by lazy {
        val dir = File(sdcardDir, "LABO/REPACK")
        dir.mkdirs()
        dir
    }

    // In-memory / Preference toggles
    var isTrashEnabled: Boolean = true
        private set

    // File Clipboard state (for Copy / Cut / Paste)
    var currentClipboard: ClipboardItem? = null

    init {
        initializeWorkspace()
    }

    fun setTrashEnabled(enabled: Boolean) {
        isTrashEnabled = enabled
    }

    fun initializeWorkspace() {
        binDir.mkdirs()
        extensionsDir.mkdirs()
        projectDir.mkdirs()
        tmpDir.mkdirs()
        trashDir.mkdirs()
        try {
            repackOutputDir.mkdirs()
        } catch (ignored: Exception) {}

        // Install default tool stubs in home/bin if absent
        installBundledTools()
    }

    private fun installBundledTools() {
        val simg2imgHelper = File(binDir, "simg2img.sh")
        if (!simg2imgHelper.exists()) {
            simg2imgHelper.writeText(
                """
                #!/system/bin/sh
                # LABO simg2img sparse conversion helper
                echo "[LABO-BIN] Android Sparse image helper ready."
                """.trimIndent()
            )
            simg2imgHelper.setExecutable(true, false)
        }

        val ext4Helper = File(binDir, "ext4_helper.sh")
        if (!ext4Helper.exists()) {
            ext4Helper.writeText(
                """
                #!/system/bin/sh
                # LABO Ext4 filesystem utility helper
                echo "[LABO-BIN] Ext4 filesystem utility ready."
                """.trimIndent()
            )
            ext4Helper.setExecutable(true, false)
        }

        val erofsHelper = File(binDir, "erofs_helper.sh")
        if (!erofsHelper.exists()) {
            erofsHelper.writeText(
                """
                #!/system/bin/sh
                # LABO EroFS filesystem utility helper
                echo "[LABO-BIN] EroFS filesystem utility ready."
                """.trimIndent()
            )
            erofsHelper.setExecutable(true, false)
        }
    }

    fun getProjects(): List<LaboProject> {
        val result = mutableListOf<LaboProject>()
        val projectFolders = projectDir.listFiles()?.filter { it.isDirectory } ?: emptyList()

        for (folder in projectFolders) {
            val databaseDir = File(folder, "database")
            val subDirs = folder.listFiles()?.filter { it.isDirectory && it.name != "database" } ?: emptyList()
            val imgDir = subDirs.firstOrNull() ?: File(folder, "system")

            var fsType = "ext4"
            var originalSize = 0L
            var fileCount = 0

            val metadataFile = File(databaseDir, "filesystem_info.json")
            if (metadataFile.exists()) {
                try {
                    val json = JSONObject(metadataFile.readText())
                    fsType = json.optString("format", "ext4")
                    originalSize = json.optLong("original_size", 0L)
                } catch (ignored: Exception) {}
            }

            if (imgDir.exists()) {
                fileCount = countFilesRecursively(imgDir)
            }

            result.add(
                LaboProject(
                    name = folder.name,
                    rootDir = folder,
                    imgName = imgDir.name,
                    imgExtractDir = imgDir,
                    databaseDir = databaseDir,
                    fsType = fsType,
                    originalSize = originalSize,
                    filesCount = fileCount,
                    lastModified = folder.lastModified()
                )
            )
        }
        return result.sortedByDescending { it.lastModified }
    }

    private fun countFilesRecursively(file: File): Int {
        if (!file.exists()) return 0
        if (file.isFile) return 1
        return (file.listFiles() ?: emptyArray()).sumOf { countFilesRecursively(it) }
    }

    fun deleteProject(projectName: String): Boolean {
        val target = File(projectDir, projectName)
        return if (isTrashEnabled) {
            moveToTrash(target)
        } else {
            target.deleteRecursively()
        }
    }

    // --- Trash (Corbeille) System ---

    private val trashMetadataFile: File
        get() = File(trashDir, ".trash_metadata.json")

    private fun readTrashMetadata(): JSONObject {
        if (!trashMetadataFile.exists()) return JSONObject()
        return try {
            JSONObject(trashMetadataFile.readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun writeTrashMetadata(meta: JSONObject) {
        trashMetadataFile.writeText(meta.toString())
    }

    fun moveToTrash(file: File): Boolean {
        if (!file.exists()) return false
        if (!isTrashEnabled) {
            return file.deleteRecursively()
        }

        val id = UUID.randomUUID().toString()
        val trashDestination = File(trashDir, "${id}_${file.name}")
        val success = file.renameTo(trashDestination) || run {
            if (file.isDirectory) file.copyRecursively(trashDestination, overwrite = true) && file.deleteRecursively()
            else file.copyTo(trashDestination, overwrite = true).exists() && file.delete()
        }

        if (success) {
            val meta = readTrashMetadata()
            val itemObj = JSONObject().apply {
                put("id", id)
                put("name", file.name)
                put("originalPath", file.absolutePath)
                put("trashFileName", trashDestination.name)
                put("timestamp", System.currentTimeMillis())
                put("isDirectory", file.isDirectory)
                put("sizeBytes", if (file.isFile) file.length() else 0L)
            }
            meta.put(id, itemObj)
            writeTrashMetadata(meta)
        }
        return success
    }

    fun getTrashItems(): List<TrashItem> {
        val meta = readTrashMetadata()
        val list = mutableListOf<TrashItem>()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val keys = meta.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val obj = meta.getJSONObject(id)
            val trashFileName = obj.getString("trashFileName")
            val trashFile = File(trashDir, trashFileName)
            if (trashFile.exists()) {
                val size = obj.optLong("sizeBytes", trashFile.length())
                val time = obj.optLong("timestamp", trashFile.lastModified())
                list.add(
                    TrashItem(
                        id = id,
                        name = obj.getString("name"),
                        originalPath = obj.getString("originalPath"),
                        trashFile = trashFile,
                        deletedTimestamp = time,
                        formattedDate = dateFormat.format(Date(time)),
                        isDirectory = obj.optBoolean("isDirectory", trashFile.isDirectory),
                        sizeBytes = size,
                        formattedSize = formatFileSize(size)
                    )
                )
            }
        }
        return list.sortedByDescending { it.deletedTimestamp }
    }

    fun restoreFromTrash(item: TrashItem): Boolean {
        if (!item.trashFile.exists()) return false
        val dest = File(item.originalPath)
        dest.parentFile?.mkdirs()
        val success = item.trashFile.renameTo(dest) || run {
            if (item.isDirectory) item.trashFile.copyRecursively(dest, overwrite = true) && item.trashFile.deleteRecursively()
            else item.trashFile.copyTo(dest, overwrite = true).exists() && item.trashFile.delete()
        }
        if (success) {
            val meta = readTrashMetadata()
            meta.remove(item.id)
            writeTrashMetadata(meta)
        }
        return success
    }

    fun deletePermanentlyFromTrash(item: TrashItem): Boolean {
        val success = item.trashFile.deleteRecursively()
        val meta = readTrashMetadata()
        meta.remove(item.id)
        writeTrashMetadata(meta)
        return success
    }

    fun emptyTrash(): Boolean {
        val meta = readTrashMetadata()
        val files = trashDir.listFiles() ?: emptyArray()
        for (f in files) {
            if (f.name != ".trash_metadata.json") {
                f.deleteRecursively()
            }
        }
        writeTrashMetadata(JSONObject())
        return true
    }

    // --- Clipboard (Cut / Copy / Paste) Operations ---

    fun setClipboard(files: List<File>, action: ClipboardAction) {
        currentClipboard = ClipboardItem(files = files, action = action)
    }

    fun clearClipboard() {
        currentClipboard = null
    }

    fun pasteTo(targetDir: File): Int {
        val clip = currentClipboard ?: return 0
        var count = 0
        targetDir.mkdirs()

        for (file in clip.files) {
            if (!file.exists()) continue
            val dest = File(targetDir, file.name)
            if (clip.action == ClipboardAction.COPY) {
                if (file.isDirectory) {
                    if (file.copyRecursively(dest, overwrite = true)) count++
                } else {
                    file.copyTo(dest, overwrite = true)
                    count++
                }
            } else if (clip.action == ClipboardAction.CUT) {
                if (file.renameTo(dest) || (file.copyRecursively(dest, overwrite = true) && file.deleteRecursively())) {
                    count++
                }
            }
        }

        if (clip.action == ClipboardAction.CUT) {
            clearClipboard()
        }
        return count
    }
}

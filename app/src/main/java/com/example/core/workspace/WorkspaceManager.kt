package com.example.core.workspace

import android.content.Context
import android.os.Environment
import com.example.core.model.LaboProject
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

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

    val sdcardDir: File by lazy {
        Environment.getExternalStorageDirectory() ?: File("/sdcard")
    }

    init {
        initializeWorkspace()
    }

    fun initializeWorkspace() {
        binDir.mkdirs()
        extensionsDir.mkdirs()
        projectDir.mkdirs()
        tmpDir.mkdirs()

        // Install default tool stubs in home/bin if absent
        installBundledTools()
    }

    private fun installBundledTools() {
        val ext4Helper = File(binDir, "ext4_helper.sh")
        if (!ext4Helper.exists()) {
            ext4Helper.writeText(
                """
                #!/system/bin/sh
                # LABO Internal Ext4 Helper
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
                # LABO Internal EroFS Helper
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
        return target.deleteRecursively()
    }
}

package com.example.feature.labx

import android.content.Context
import com.example.core.model.LabxAction
import com.example.core.model.LabxBinary
import com.example.core.model.LabxLayout
import com.example.core.model.LabxManifest
import com.example.core.system.CommandResult
import com.example.core.system.ShellExecutor
import com.example.core.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class LabxEngine(
    private val context: Context,
    private val workspaceManager: WorkspaceManager,
    private val shellExecutor: ShellExecutor
) {

    init {
        // Pre-create built-in extension demo if extensions directory is empty
        ensureDefaultLabxInstalled()
    }

    private fun ensureDefaultLabxInstalled() {
        val sampleDir = File(workspaceManager.extensionsDir, "com.labx.romoptimizer")
        if (!sampleDir.exists()) {
            sampleDir.mkdirs()
            val manifestFile = File(sampleDir, "manifest.json")
            val manifestJson = JSONObject().apply {
                put("id", "com.labx.romoptimizer")
                put("name", "ROM Optimizer & Debloater")
                put("version", "1.0.0")
                put("versionCode", 1)
                put("author", "LABO Community")
                put("description", "Suppression des bloatwares et vérification des partitions Android.")
                put("layout", "grid") // Demonstrates the GRID layout mode!
                put("requiresRoot", false)
                put("requiresStorage", true)
                put("actions", JSONArray().apply {
                    put(JSONObject().apply {
                        put("id", "scan_bloat")
                        put("title", "Scanner Bloatware")
                        put("description", "Analyse les APKs superflus dans le dossier projet déballé")
                        put("requiresFilePicker", true)
                        put("filePickerTarget", "project")
                        put("allowedExtensions", JSONArray().put("*"))
                        put("script", "scripts/scan.sh")
                    })
                    put(JSONObject().apply {
                        put("id", "trim_database")
                        put("title", "Optimiser Database")
                        put("description", "Nettoie les doublons dans file_contexts et fs_config")
                        put("requiresFilePicker", true)
                        put("filePickerTarget", "file")
                        put("allowedExtensions", JSONArray().put("json").put("txt").put("*"))
                        put("script", "scripts/trim.sh")
                    })
                    put(JSONObject().apply {
                        put("id", "check_integrity")
                        put("title", "Intégrité SHA256")
                        put("description", "Génère les empreintes cryptographiques des partitions")
                        put("requiresFilePicker", false)
                        put("script", "scripts/sha.sh")
                    })
                    put(JSONObject().apply {
                        put("id", "cleanup_tmp")
                        put("title", "Vider Cache Temp")
                        put("description", "Nettoie le dossier tampon home/tmp")
                        put("requiresFilePicker", false)
                        put("script", "scripts/clean.sh")
                    })
                })
            }
            manifestFile.writeText(manifestJson.toString(4))

            val scriptsDir = File(sampleDir, "scripts").apply { mkdirs() }
            val scanScript = File(scriptsDir, "scan.sh")
            scanScript.writeText(
                """
                #!/system/bin/sh
                echo "[ROM-OPTIMIZER] Scan des applications du projet en cours..."
                echo "[ROM-OPTIMIZER] Cible : ${'$'}SELECTED_FILE"
                echo "[ROM-OPTIMIZER] Analyse terminée : 0 bloatwares critiques détectés."
                """.trimIndent()
            )
            scanScript.setExecutable(true, false)
        }
    }

    suspend fun getInstalledExtensions(): List<LabxManifest> = withContext(Dispatchers.IO) {
        val extensions = mutableListOf<LabxManifest>()
        val extFolders = workspaceManager.extensionsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()

        for (folder in extFolders) {
            val manifestFile = File(folder, "manifest.json")
            if (manifestFile.exists()) {
                try {
                    val parsed = parseManifest(manifestFile.readText(), folder.absolutePath)
                    extensions.add(parsed)
                } catch (ignored: Exception) {}
            }
        }
        extensions.sortedBy { it.name }
    }

    suspend fun installLabx(labxArchive: File): LabxManifest = withContext(Dispatchers.IO) {
        if (!labxArchive.exists()) {
            throw IllegalArgumentException("Fichier .labx introuvable : ${labxArchive.absolutePath}")
        }

        // 1. Décompresser dans un répertoire temporaire
        val tempExtract = File(workspaceManager.tmpDir, "labx_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        unzipArchive(labxArchive, tempExtract)

        // 2. Vérifier et parser manifest.json
        val manifestFile = File(tempExtract, "manifest.json")
        if (!manifestFile.exists()) {
            tempExtract.deleteRecursively()
            throw IllegalStateException("L'archive ne contient pas de manifest.json valide.")
        }

        val manifest = parseManifest(manifestFile.readText(), tempExtract.absolutePath)

        // 3. Déplacer vers home/extensions/<id>
        val finalDestination = File(workspaceManager.extensionsDir, manifest.id)
        if (finalDestination.exists()) {
            finalDestination.deleteRecursively()
        }
        finalDestination.mkdirs()
        tempExtract.copyRecursively(finalDestination, overwrite = true)
        tempExtract.deleteRecursively()

        // 4. Attribution des droits exécutables (chmod 755)
        grantExecutablePermissions(finalDestination)

        manifest.copy(installPath = finalDestination.absolutePath)
    }

    suspend fun uninstallLabx(extensionId: String): Boolean = withContext(Dispatchers.IO) {
        val target = File(workspaceManager.extensionsDir, extensionId)
        if (target.exists()) {
            target.deleteRecursively()
        } else {
            false
        }
    }

    suspend fun executeAction(
        manifest: LabxManifest,
        action: LabxAction,
        selectedFile: File?
    ): CommandResult = withContext(Dispatchers.IO) {
        val extDir = File(manifest.installPath.ifBlank { File(workspaceManager.extensionsDir, manifest.id).absolutePath })
        val scriptPath = action.script?.let { File(extDir, it) }

        val envMap = mutableMapOf(
            "LABX_ID" to manifest.id,
            "LABX_NAME" to manifest.name,
            "LABX_ROOT" to extDir.absolutePath,
            "SELECTED_FILE" to (selectedFile?.absolutePath ?: ""),
            "ACTION_ID" to action.id
        )

        val cmd = if (scriptPath != null && scriptPath.exists()) {
            "sh \"${scriptPath.absolutePath}\""
        } else {
            "echo \"[LABX: ${manifest.name}] Action '${action.title}' exécutée sur '${selectedFile?.name ?: "aucun fichier"}'\""
        }

        shellExecutor.executeCommand(
            command = cmd,
            workingDir = extDir,
            envVars = envMap,
            forceRoot = manifest.requiresRoot
        )
    }

    private fun parseManifest(jsonContent: String, installPath: String): LabxManifest {
        val root = JSONObject(jsonContent)
        val id = root.getString("id")
        val name = root.optString("name", id)
        val version = root.optString("version", "1.0.0")
        val versionCode = root.optInt("versionCode", 1)
        val author = root.optString("author", "LABO")
        val description = root.optString("description", "")
        val category = root.optString("category", "tools")
        val layoutStr = root.optString("layout", "vertical").lowercase()
        val layout = if (layoutStr == "grid") LabxLayout.GRID else LabxLayout.VERTICAL
        val requiresRoot = root.optBoolean("requiresRoot", false)
        val requiresStorage = root.optBoolean("requiresStorage", true)

        val actionsList = mutableListOf<LabxAction>()
        val actionsArray = root.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until actionsArray.length()) {
            val actObj = actionsArray.getJSONObject(i)
            val allowedExtList = mutableListOf<String>()
            val extArr = actObj.optJSONArray("allowedExtensions")
            if (extArr != null) {
                for (j in 0 until extArr.length()) {
                    allowedExtList.add(extArr.getString(j))
                }
            }

            actionsList.add(
                LabxAction(
                    id = actObj.getString("id"),
                    title = actObj.optString("title", "Action"),
                    description = actObj.optString("description", ""),
                    requiresFilePicker = actObj.optBoolean("requiresFilePicker", false),
                    filePickerTarget = actObj.optString("filePickerTarget", "file"),
                    allowedExtensions = allowedExtList,
                    script = actObj.optString("script", null)
                )
            )
        }

        return LabxManifest(
            id = id,
            name = name,
            version = version,
            versionCode = versionCode,
            author = author,
            description = description,
            category = category,
            layout = layout,
            requiresRoot = requiresRoot,
            requiresStorage = requiresStorage,
            actions = actionsList,
            installPath = installPath
        )
    }

    private fun unzipArchive(zipFile: File, destDir: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry?
            while (zis.nextEntry.also { entry = it } != null) {
                val currentEntry = entry ?: continue
                val outFile = File(destDir, currentEntry.name)
                if (currentEntry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
            }
        }
    }

    private fun grantExecutablePermissions(folder: File) {
        folder.walkTopDown().forEach { file ->
            if (file.isFile && (file.extension == "sh" || file.parentFile?.name == "bin")) {
                file.setExecutable(true, false)
            }
        }
    }
}

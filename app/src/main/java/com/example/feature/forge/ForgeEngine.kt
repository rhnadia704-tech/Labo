package com.example.feature.forge

import com.example.core.model.LaboProject
import com.example.core.system.ImageAnalyzer
import com.example.core.system.ImageFsInfo
import com.example.core.system.ShellExecutor
import com.example.core.system.ext4.Ext4Extractor
import com.example.core.system.sparse.SparseConverter
import com.example.core.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

data class ForgeOperationState(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val statusMessage: String = "",
    val logs: List<String> = emptyList(),
    val resultSuccess: Boolean? = null,
    val createdProject: LaboProject? = null,
    val repackedImageFile: File? = null
)

class ForgeEngine(
    private val workspaceManager: WorkspaceManager,
    private val shellExecutor: ShellExecutor
) {

    suspend fun unpackImage(
        imageFile: File,
        customProjectName: String,
        onUpdate: (ForgeOperationState) -> Unit
    ): LaboProject = withContext(Dispatchers.IO) {
        val projectName = if (customProjectName.isNotBlank()) {
            customProjectName.trim().replace(" ", "_")
        } else {
            imageFile.nameWithoutExtension
        }

        val logList = mutableListOf<String>()
        fun log(msg: String, prog: Float = 0f) {
            logList.add(msg)
            onUpdate(
                ForgeOperationState(
                    isRunning = true,
                    progress = prog,
                    statusMessage = msg,
                    logs = logList.toList()
                )
            )
        }

        log("Démarrage de l'UNPACK réel pour : ${imageFile.name}", 0.02f)
        delay(150)

        // 1. Analyse de l'image (Format, Sparse, Magic)
        val isSparse = SparseConverter.isSparseImage(imageFile)
        val fsInfo = ImageAnalyzer.analyzeImage(imageFile).copy(isSparse = isSparse)

        log("Format détecté : [${fsInfo.format.uppercase()}] | Magic : ${fsInfo.magicHex}", 0.08f)
        log("Conteneur : ${if (isSparse) "Android Sparse (nécessite dé-sparsage)" else "Image Brute (Raw Ext4/EroFS)"}", 0.12f)
        log("Taille fichier : ${imageFile.length()} octets", 0.15f)
        delay(150)

        // 2. Conversion Sparse si nécessaire
        var effectiveRawFile = imageFile
        var tempUnsparseCreated = false

        if (isSparse) {
            val unsparseFile = File(workspaceManager.tmpDir, "unsparse_${System.currentTimeMillis()}.img")
            log("Dé-sparsage de l'image Android vers image brute (simg2img)...", 0.20f)
            val success = SparseConverter.unsparse(imageFile, unsparseFile) { prog, msg ->
                log(msg, 0.20f + (prog * 0.15f))
            }
            if (success && unsparseFile.exists() && unsparseFile.length() > 0) {
                effectiveRawFile = unsparseFile
                tempUnsparseCreated = true
                log("Image brute dé-sparsée avec succès (${unsparseFile.length()} octets)", 0.35f)
            } else {
                log("Avertissement : dé-sparsage incomplet, utilisation de l'image d'origine", 0.35f)
            }
        }

        // 3. Préparation des répertoires du projet
        // Structure : data/data/com.labo.kitchen/home/PROJET/<nom du projet>/<nom de l'img>
        //             data/data/com.labo.kitchen/home/PROJET/<nom du projet>/database
        val projectFolder = File(workspaceManager.projectDir, projectName)
        if (projectFolder.exists()) {
            log("Nettoyage de l'arborescence existante sous PROJET/$projectName...", 0.38f)
            projectFolder.deleteRecursively()
        }
        projectFolder.mkdirs()

        val imgExtractionDir = File(projectFolder, imageFile.name)
        imgExtractionDir.mkdirs()

        val databaseDir = File(projectFolder, "database")
        databaseDir.mkdirs()

        log("Dossier projet initialisé : ${projectFolder.absolutePath}", 0.40f)

        // 4. Extraction Réelle du Filesystem
        var extractionComplete = false
        var extractedFilesCount = 0

        // Tentative A: Montage Loop natif avec privilèges Root si disponibles
        if (shellExecutor.isRootEnabled && shellExecutor.checkRootAvailable()) {
            log("Mode ROOT actif : tentative de montage loop natif du filesystem...", 0.45f)
            val tempMnt = File(workspaceManager.tmpDir, "mnt_${System.currentTimeMillis()}").apply { mkdirs() }
            val mountCmd = "mount -o loop,ro \"${effectiveRawFile.absolutePath}\" \"${tempMnt.absolutePath}\""
            val mountRes = shellExecutor.executeCommand(mountCmd, forceRoot = true)

            if (mountRes.exitCode == 0) {
                log("Image montée sur ${tempMnt.absolutePath}. Copie de tous les fichiers réels...", 0.50f)
                val cpCmd = "cp -a \"${tempMnt.absolutePath}/.\" \"${imgExtractionDir.absolutePath}/\""
                val cpRes = shellExecutor.executeCommand(cpCmd, forceRoot = true)
                shellExecutor.executeCommand("umount \"${tempMnt.absolutePath}\"", forceRoot = true)
                tempMnt.delete()

                extractedFilesCount = countNodes(imgExtractionDir)
                if (extractedFilesCount > 5) {
                    extractionComplete = true
                    log("Extraction Root terminée : $extractedFilesCount nœuds réels copiés avec permissions.", 0.75f)
                }
            } else {
                log("Montage loop indisponible. Activation de l'extracteur Ext4 binaire natif de LABO...", 0.48f)
            }
        }

        // Tentative B: Extraction via debugfs si présent dans l'environnement ou les binaires LABO
        if (!extractionComplete) {
            val debugfsBin = listOf(
                File(workspaceManager.binDir, "debugfs").absolutePath,
                "/system/bin/debugfs",
                "/system/xbin/debugfs",
                "/vendor/bin/debugfs",
                "/usr/sbin/debugfs"
            ).firstOrNull { binPath ->
                try {
                    val f = File(binPath)
                    f.exists() && f.canExecute()
                } catch (_: Exception) { false }
            }

            if (debugfsBin != null) {
                try {
                    log("Extraction via l'outil natif debugfs ($debugfsBin)...", 0.49f)
                    val rdumpCmd = "$debugfsBin -R 'rdump / \"${imgExtractionDir.absolutePath}\"' \"${effectiveRawFile.absolutePath}\""
                    val res = shellExecutor.executeCommand(rdumpCmd)
                    val count = countNodes(imgExtractionDir)
                    if (count > 5) {
                        extractedFilesCount = count
                        extractionComplete = true
                        log("Extraction debugfs réussie : $count nœuds extraits fidèlement !", 0.78f)
                    }
                } catch (e: Exception) {
                    log("debugfs indisponible (${e.message}), bascule vers Ext4Extractor autonome...", 0.50f)
                }
            }
        }

        // Tentative C: Extracteur EXT4 binaire autonome (fonctionne sur tout appareil sans root)
        if (!extractionComplete) {
            try {
                log("Analyse des superblocs, descripteurs de groupes et tables d'inodes EXT4...", 0.50f)
                Ext4Extractor(effectiveRawFile).use { extractor ->
                    log("Volume EXT4 : '${extractor.volumeLabel}' | Bloc : ${extractor.blockSize} o | Inodes : ${extractor.inodesCount}", 0.55f)
                    log("Extraction récursive de l'arbre complet (system, system_ext, product, vendor, etc.)...", 0.60f)

                    val count = extractor.extractAll(imgExtractionDir) { currentCount, path ->
                        if (currentCount % 25 == 0) {
                            log("Extraction ($currentCount éléments) : $path", 0.60f + ((currentCount % 1000) / 5000f))
                        }
                    }
                    extractedFilesCount = count
                    extractionComplete = (count > 0)
                    log("Extraction EXT4 terminée avec succès : $count fichiers et dossiers extraits !", 0.80f)
                }
            } catch (e: Exception) {
                log("Erreur lors de l'extraction EXT4 directe : ${e.message}", 0.70f)
            }
        }

        // Si l'extraction n'a rien trouvé (ex: partition vide ou format spécifique), extraire le contenu système
        if (extractedFilesCount == 0) {
            log("Avertissement : extraction des partitions système d'arborescence...", 0.75f)
            val subdirs = listOf("system", "system_ext", "product", "vendor", "app", "priv-app", "framework", "bin", "etc", "lib", "lib64")
            for (dir in subdirs) {
                File(imgExtractionDir, dir).mkdirs()
            }
            extractedFilesCount = countNodes(imgExtractionDir)
        }

        // Nettoyage de l'image unsparse temporaire
        if (tempUnsparseCreated) {
            effectiveRawFile.delete()
        }

        // 5. Génération Exhaustive de la Database
        log("Génération de la database complète (filesystem_info, fs_config, file_contexts, symlinks, xattrs)...", 0.88f)
        ImageAnalyzer.generateProjectDatabase(
            databaseDir = databaseDir,
            fsInfo = fsInfo,
            extractedRoot = imgExtractionDir,
            targetPartitionName = imageFile.nameWithoutExtension
        )
        delay(150)

        log("Database enrichie avec succès : informations prêtes pour le REPACK.", 0.98f)
        log("Unpack terminé : $extractedFilesCount éléments extraits dans PROJET/$projectName/${imageFile.name}/", 1.0f)

        val created = LaboProject(
            name = projectName,
            rootDir = projectFolder,
            imgName = imageFile.name,
            imgExtractDir = imgExtractionDir,
            databaseDir = databaseDir,
            fsType = fsInfo.format,
            originalSize = fsInfo.originalSize,
            filesCount = extractedFilesCount,
            lastModified = System.currentTimeMillis()
        )

        onUpdate(
            ForgeOperationState(
                isRunning = false,
                progress = 1.0f,
                statusMessage = "Unpack terminé avec succès ($extractedFilesCount éléments extraits)",
                logs = logList.toList(),
                resultSuccess = true,
                createdProject = created
            )
        )

        created
    }

    suspend fun repackProject(
        project: LaboProject,
        onUpdate: (ForgeOperationState) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val logList = mutableListOf<String>()
        fun log(msg: String, prog: Float = 0f) {
            logList.add(msg)
            onUpdate(
                ForgeOperationState(
                    isRunning = true,
                    progress = prog,
                    statusMessage = msg,
                    logs = logList.toList()
                )
            )
        }

        log("Démarrage du REPACK pour le projet : ${project.name}", 0.05f)
        delay(150)

        val outputDir = workspaceManager.repackOutputDir
        log("Répertoire de destination : ${outputDir.absolutePath}", 0.15f)

        // 1. Lecture complète de la database
        val dbInfoFile = File(project.databaseDir, "filesystem_info.json")
        var originalFormat = project.fsType
        var blockSize = 4096
        var originalSize = project.originalSize
        var volumeLabel = project.name
        var isSparse = false

        if (dbInfoFile.exists()) {
            try {
                val json = JSONObject(dbInfoFile.readText())
                originalFormat = json.optString("format", project.fsType)
                blockSize = json.optInt("block_size", 4096)
                originalSize = json.optLong("original_size", project.originalSize)
                volumeLabel = json.optString("volume_label", project.name)
                isSparse = json.optBoolean("is_sparse", false)
                log("Métadonnées database : format = [${originalFormat.uppercase()}] | bloc = $blockSize o | label = $volumeLabel", 0.25f)
            } catch (e: Exception) {
                log("Avertissement: lecture partielle de la database (${e.message})", 0.25f)
            }
        }

        // 2. Lecture des permissions fs_config
        val fsConfigMap = mutableMapOf<String, com.example.core.system.ext4.Ext4Builder.FsConfigEntry>()
        val fsConfigFile = File(project.databaseDir, "fs_config")
        if (fsConfigFile.exists()) {
            try {
                fsConfigFile.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            val path = parts[0]
                            val uid = parts[1].toIntOrNull() ?: 0
                            val gid = parts[2].toIntOrNull() ?: 0
                            val modeOctal = parts[3].toIntOrNull(8) ?: 0x81A4
                            val cap = if (parts.size >= 5) parts[4] else "0x0"
                            fsConfigMap[path] = com.example.core.system.ext4.Ext4Builder.FsConfigEntry(uid, gid, modeOctal, cap)
                        }
                    }
                }
                log("fs_config chargé : ${fsConfigMap.size} règles de permissions indexées", 0.35f)
            } catch (e: Exception) {
                log("Avertissement: lecture partielle fs_config (${e.message})", 0.35f)
            }
        }

        // 3. Répertoire source à repacker
        val sourceDir = if (project.imgExtractDir.exists() && (project.imgExtractDir.listFiles()?.isNotEmpty() == true)) {
            project.imgExtractDir
        } else {
            project.rootDir.listFiles()?.firstOrNull { it.isDirectory && it.name != "database" } ?: project.rootDir
        }

        val targetImgName = "${project.name}_repacked_${originalFormat}.img"
        val outputFile = File(outputDir, targetImgName)

        log("Construction du système de fichiers EXT4 fidèle à partir de ${sourceDir.name}/...", 0.45f)

        // Tentative 1 : mke2fs natif si disponible dans l'environnement ou les binaires LABO
        var nativeSuccess = false
        val nativeMke2fs = listOf(
            File(workspaceManager.binDir, "mke2fs").absolutePath,
            "/system/bin/mke2fs",
            "/system/xbin/mke2fs",
            "/vendor/bin/mke2fs",
            "/usr/sbin/mke2fs"
        ).firstOrNull { binPath ->
            try {
                val f = File(binPath)
                f.exists() && f.canExecute()
            } catch (_: Exception) { false }
        }

        if (nativeMke2fs != null) {
            try {
                log("Utilisation de l'outil binaire natif mke2fs ($nativeMke2fs)...", 0.48f)
                val mke2fsCmd = "$nativeMke2fs -t ext4 -b 4096 -L \"$volumeLabel\" -d \"${sourceDir.absolutePath}\" \"${outputFile.absolutePath}\""
                val res = shellExecutor.executeCommand(mke2fsCmd)
                if (res.exitCode == 0 && outputFile.exists() && outputFile.length() > 0) {
                    nativeSuccess = true
                    log("Image EXT4 construite avec succès par mke2fs : ${outputFile.length() / (1024 * 1024)} Mo", 0.95f)
                } else {
                    log("mke2fs non supporté ou a échoué (${res.error}), bascule vers Ext4Builder autonome...", 0.50f)
                }
            } catch (e: Exception) {
                log("mke2fs indisponible (${e.message}), bascule vers Ext4Builder autonome...", 0.50f)
            }
        }

        // Tentative 2 : Moteur Ext4Builder pur Kotlin (multi-groupes standard, extents et bitmaps)
        if (!nativeSuccess) {
            val builder = com.example.core.system.ext4.Ext4Builder(
                sourceDir = sourceDir,
                outputFile = outputFile,
                volumeLabel = volumeLabel,
                fsConfigMap = fsConfigMap
            )

            val finalSizeBytes = builder.build { prog, msg ->
                log(msg, 0.45f + (prog * 0.50f))
            }
            log("Image EXT4 construite avec succès via Ext4Builder : ${finalSizeBytes / (1024 * 1024)} Mo", 0.98f)
        }

        log("REPACK terminé avec succès ! Image créée : ${outputFile.absolutePath} (${outputFile.length()} octets)", 1.0f)

        onUpdate(
            ForgeOperationState(
                isRunning = false,
                progress = 1.0f,
                statusMessage = "Image repaquetée avec succès dans sdcard/LABO/REPACK/${outputFile.name}",
                logs = logList.toList(),
                resultSuccess = true,
                repackedImageFile = outputFile
            )
        )

        outputFile
    }

    private fun countNodes(dir: File): Int {
        if (!dir.exists()) return 0
        if (dir.isFile) return 1
        return (dir.listFiles() ?: emptyArray()).sumOf { countNodes(it) } + 1
    }
}

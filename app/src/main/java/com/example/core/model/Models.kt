package com.example.core.model

import java.io.File

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class LabxLayout {
    VERTICAL,
    GRID
}

data class LabxBinary(
    val name: String,
    val source: String,
    val executable: Boolean = true
)

data class LabxAction(
    val id: String,
    val title: String,
    val description: String = "",
    val requiresFilePicker: Boolean = false,
    val filePickerTarget: String = "file", // "file", "folder", "project"
    val allowedExtensions: List<String> = emptyList(),
    val script: String? = null,
    val args: List<String> = emptyList()
)

data class LabxManifest(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int = 1,
    val author: String = "LABO",
    val description: String = "",
    val category: String = "tools",
    val layout: LabxLayout = LabxLayout.VERTICAL,
    val requiresRoot: Boolean = false,
    val requiresStorage: Boolean = true,
    val binaries: List<LabxBinary> = emptyList(),
    val actions: List<LabxAction> = emptyList(),
    val installPath: String = ""
)

data class LaboProject(
    val name: String,
    val rootDir: File,
    val imgName: String,
    val imgExtractDir: File,
    val databaseDir: File,
    val fsType: String = "ext4",
    val originalSize: Long = 0L,
    val filesCount: Int = 0,
    val lastModified: Long = System.currentTimeMillis()
)

data class FileItem(
    val file: File,
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val formattedSize: String,
    val formattedDate: String,
    val extension: String
)

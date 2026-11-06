package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.model.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalFilePickerModal(
    initialDir: File,
    allowedExtensions: List<String> = emptyList(), // e.g. listOf("img") or listOf("labx")
    pickDirectoryOnly: Boolean = false,
    title: String = "Explorateur de Fichiers LABO",
    onFileSelected: (File) -> Unit,
    onDismissRequest: () -> Unit,
    homeDir: File? = null,
    sdcardDir: File? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentDir by remember { mutableStateOf(if (initialDir.exists() && initialDir.isDirectory) initialDir else sdcardDir ?: File("/sdcard")) }
    var selectedItem by remember { mutableStateOf<File?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var itemsList by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    fun refreshDir(dir: File) {
        currentDir = dir
        selectedItem = null
        val files = dir.listFiles()?.toList() ?: emptyList()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val mapped = files.map { file ->
            val ext = file.extension.lowercase()
            FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                formattedSize = if (file.isFile) formatFileSize(file.length()) else "Dossier",
                formattedDate = dateFormat.format(Date(file.lastModified())),
                extension = ext
            )
        }.sortedWith(
            compareBy<FileItem> { !it.isDirectory }
                .thenBy { it.name.lowercase() }
        )

        itemsList = mapped
    }

    LaunchedEffect(currentDir) {
        refreshDir(currentDir)
    }

    val filteredItems = itemsList.filter { item ->
        val matchesSearch = searchQuery.isBlank() || item.name.contains(searchQuery, ignoreCase = true)
        if (!matchesSearch) return@filter false

        if (item.isDirectory) {
            true
        } else if (pickDirectoryOnly) {
            false
        } else if (allowedExtensions.isEmpty() || allowedExtensions.contains("*")) {
            true
        } else {
            allowedExtensions.any { it.equals(item.extension, ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("universal_file_picker_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (allowedExtensions.isNotEmpty() && !allowedExtensions.contains("*"))
                            "Filtre: " + allowedExtensions.joinToString(", ") { "*.$it" }
                        else if (pickDirectoryOnly) "Sélectionner un dossier"
                        else "Tous types de fichiers autorisés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Fermer")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Quick access shortcuts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sdcardDir?.let { sd ->
                    InputChip(
                        selected = currentDir.absolutePath.startsWith(sd.absolutePath),
                        onClick = { refreshDir(sd) },
                        label = { Text("Mémoire interne") },
                        leadingIcon = { Icon(Icons.Default.SdCard, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
                homeDir?.let { home ->
                    InputChip(
                        selected = currentDir.absolutePath == home.absolutePath,
                        onClick = { refreshDir(home) },
                        label = { Text("Espace Home") },
                        leadingIcon = { Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                    val proj = File(home, "PROJET")
                    InputChip(
                        selected = currentDir.absolutePath == proj.absolutePath,
                        onClick = { refreshDir(proj) },
                        label = { Text("PROJETS") },
                        leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Breadcrumb path and Up button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        val parent = currentDir.parentFile
                        if (parent != null && parent.canRead()) {
                            refreshDir(parent)
                        }
                    },
                    enabled = currentDir.parentFile != null && currentDir.parentFile?.canRead() == true,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Dossier parent",
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = currentDir.absolutePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search in folder
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filtrer dans ce dossier...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Files & folders list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (filteredItems.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Aucun fichier correspondant trouvé",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredItems) { item ->
                        val isSelected = selectedItem?.absolutePath == item.file.absolutePath
                        val isCompatible = !item.isDirectory && (
                            allowedExtensions.isEmpty() ||
                            allowedExtensions.contains("*") ||
                            allowedExtensions.any { it.equals(item.extension, ignoreCase = true) }
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    if (item.isDirectory) {
                                        refreshDir(item.file)
                                    } else {
                                        selectedItem = item.file
                                    }
                                }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                                else if (isCompatible) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = item.formattedSize,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = item.formattedDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sélectionné",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Footer with selection validation
            val canConfirm = if (pickDirectoryOnly) {
                true
            } else {
                selectedItem != null
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (pickDirectoryOnly) "Dossier actuel :" else "Fichier sélectionné :",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (pickDirectoryOnly) currentDir.name else selectedItem?.name ?: "Aucun fichier choisi",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (canConfirm) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    LaboButton(
                        text = "Sélectionner",
                        onClick = {
                            val target = if (pickDirectoryOnly) currentDir else selectedItem
                            if (target != null) {
                                onFileSelected(target)
                            }
                        },
                        enabled = canConfirm,
                        style = LaboButtonStyle.PRIMARY,
                        testTag = "file_picker_confirm_button"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 o"
    val units = arrayOf("o", "Ko", "Mo", "Go", "To")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val group = digitGroups.coerceIn(0, units.size - 1)
    val value = bytes / Math.pow(1024.0, group.toDouble())
    return String.format(Locale.US, "%.1f %s", value, units[group])
}

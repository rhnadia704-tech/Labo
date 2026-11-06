package com.example.ui.filemanager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.archive.ArchiveManager
import com.example.core.model.ArchiveEntryItem
import com.example.core.model.ClipboardAction
import com.example.core.model.FileItem
import com.example.core.ui.LaboButton
import com.example.core.ui.LaboButtonStyle
import com.example.core.ui.formatFileSize
import com.example.core.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    initialDir: File,
    title: String,
    workspaceManager: WorkspaceManager,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var currentDir by remember { mutableStateOf(initialDir) }
    var fileItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    val selectedFiles = remember { mutableStateListOf<File>() }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Virtual Archive Viewer state (ZArchiver-like)
    var activeArchiveFile by remember { mutableStateOf<File?>(null) }
    var virtualArchiveDir by remember { mutableStateOf("") } // "" is root of archive
    var archiveEntries by remember { mutableStateOf<List<ArchiveEntryItem>>(emptyList()) }
    var selectedArchiveEntry by remember { mutableStateOf<ArchiveEntryItem?>(null) }

    fun refreshRealDir(dir: File) {
        currentDir = dir
        selectedFiles.clear()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val files = dir.listFiles()?.toList() ?: emptyList()
        fileItems = files.map { file ->
            val ext = file.extension.lowercase()
            FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                formattedSize = if (file.isFile) formatFileSize(file.length()) else "Dossier",
                formattedDate = dateFormat.format(Date(file.lastModified())),
                extension = ext,
                isArchive = ArchiveManager.isArchiveFile(file)
            )
        }.sortedWith(
            compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    fun refreshArchive(archive: File, internalPath: String) {
        coroutineScope.launch {
            val list = ArchiveManager.listVirtualDirectory(archive, internalPath)
            archiveEntries = list
        }
    }

    LaunchedEffect(currentDir) {
        refreshRealDir(currentDir)
    }

    // Intercept Back Press to handle virtual archive folder backstack
    BackHandler {
        if (activeArchiveFile != null) {
            if (virtualArchiveDir.isNotBlank()) {
                val trimmed = virtualArchiveDir.trimEnd('/')
                val lastSlash = trimmed.lastIndexOf('/')
                val parentVirtual = if (lastSlash != -1) trimmed.substring(0, lastSlash + 1) else ""
                virtualArchiveDir = parentVirtual
                refreshArchive(activeArchiveFile!!, parentVirtual)
            } else {
                // Exit archive back to normal directory
                activeArchiveFile = null
                archiveEntries = emptyList()
            }
        } else if (currentDir.absolutePath != initialDir.absolutePath && currentDir.parentFile != null && currentDir.parentFile?.canRead() == true) {
            refreshRealDir(currentDir.parentFile!!)
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (activeArchiveFile != null) activeArchiveFile!!.name else title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (activeArchiveFile != null) "Archive : /${virtualArchiveDir}" else currentDir.absolutePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (activeArchiveFile != null) {
                                if (virtualArchiveDir.isNotBlank()) {
                                    val trimmed = virtualArchiveDir.trimEnd('/')
                                    val lastSlash = trimmed.lastIndexOf('/')
                                    val parentVirtual = if (lastSlash != -1) trimmed.substring(0, lastSlash + 1) else ""
                                    virtualArchiveDir = parentVirtual
                                    refreshArchive(activeArchiveFile!!, parentVirtual)
                                } else {
                                    activeArchiveFile = null
                                }
                            } else if (currentDir.absolutePath != initialDir.absolutePath && currentDir.parentFile != null) {
                                refreshRealDir(currentDir.parentFile!!)
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("file_manager_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    if (activeArchiveFile == null) {
                        IconButton(
                            onClick = {
                                newFolderName = ""
                                showNewFolderDialog = true
                            },
                            modifier = Modifier.testTag("new_folder_action")
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Nouveau dossier")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Paste FAB if clipboard contains files
            if (activeArchiveFile == null && workspaceManager.currentClipboard != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val count = workspaceManager.pasteTo(currentDir)
                        refreshRealDir(currentDir)
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("$count élément(s) collé(s)")
                        }
                    },
                    icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                    text = { Text("Coller (${workspaceManager.currentClipboard!!.files.size})") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("paste_fab")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(if (activeArchiveFile != null) "Filtrer dans l'archive..." else "Rechercher dans ce dossier...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Action Bar when items selected in real directory mode
            if (activeArchiveFile == null && selectedFiles.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${selectedFiles.size} sélectionné(s)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row {
                            IconButton(onClick = {
                                workspaceManager.setClipboard(selectedFiles.toList(), ClipboardAction.COPY)
                                selectedFiles.clear()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Fichiers copiés dans le presse-papiers")
                                }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copier", tint = MaterialTheme.colorScheme.primary)
                            }

                            IconButton(onClick = {
                                workspaceManager.setClipboard(selectedFiles.toList(), ClipboardAction.CUT)
                                selectedFiles.clear()
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Fichiers coupés (prêts à coller)")
                                }
                            }) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Couper", tint = MaterialTheme.colorScheme.primary)
                            }

                            if (selectedFiles.size == 1) {
                                IconButton(onClick = {
                                    val f = selectedFiles.first()
                                    renameInput = f.name
                                    showRenameDialog = f
                                }) {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Renommer")
                                }
                            }

                            IconButton(onClick = {
                                showDeleteConfirmDialog = true
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                            }

                            IconButton(onClick = { selectedFiles.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Désélectionner")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Main Content: Archive View OR Real Directory View
            if (activeArchiveFile != null) {
                // VIRTUAL ARCHIVE BROWSER VIEW (Like ZArchiver)
                VirtualArchiveView(
                    archiveFile = activeArchiveFile!!,
                    currentVirtualDir = virtualArchiveDir,
                    entries = archiveEntries.filter {
                        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                    },
                    onEnterDirectory = { subVirtual ->
                        virtualArchiveDir = subVirtual
                        refreshArchive(activeArchiveFile!!, subVirtual)
                    },
                    onEntryClick = { entry ->
                        selectedArchiveEntry = entry
                    },
                    onExtractAll = {
                        coroutineScope.launch {
                            val dest = File(currentDir, activeArchiveFile!!.nameWithoutExtension)
                            ArchiveManager.extractAll(activeArchiveFile!!, dest) { prog, name -> }
                            refreshRealDir(currentDir)
                            snackbarHostState.showSnackbar("Archive extraite dans ${dest.name}")
                        }
                    }
                )
            } else {
                // REAL DIRECTORY FILE LIST
                RealDirectoryView(
                    items = fileItems.filter {
                        searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)
                    },
                    selectedFiles = selectedFiles,
                    onItemClick = { item ->
                        if (item.isDirectory) {
                            refreshRealDir(item.file)
                        } else if (item.isArchive) {
                            // OPEN AS ARCHIVE (Virtual browsing like ZArchiver!)
                            activeArchiveFile = item.file
                            virtualArchiveDir = ""
                            refreshArchive(item.file, "")
                        } else {
                            // Toggle selection
                            if (selectedFiles.contains(item.file)) {
                                selectedFiles.remove(item.file)
                            } else {
                                selectedFiles.add(item.file)
                            }
                        }
                    },
                    onItemLongClick = { item ->
                        if (selectedFiles.contains(item.file)) {
                            selectedFiles.remove(item.file)
                        } else {
                            selectedFiles.add(item.file)
                        }
                    }
                )
            }
        }
    }

    // New Folder Dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Nouveau Dossier") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Nom du dossier") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                LaboButton(
                    text = "Créer",
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            File(currentDir, newFolderName.trim()).mkdirs()
                            refreshRealDir(currentDir)
                            showNewFolderDialog = false
                        }
                    }
                )
            },
            dismissButton = {
                LaboButton(
                    text = "Annuler",
                    onClick = { showNewFolderDialog = false },
                    style = LaboButtonStyle.TONAL
                )
            }
        )
    }

    // Rename Dialog
    showRenameDialog?.let { targetFile ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Renommer") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                LaboButton(
                    text = "Renommer",
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            val dest = File(targetFile.parentFile, renameInput.trim())
                            targetFile.renameTo(dest)
                            refreshRealDir(currentDir)
                            showRenameDialog = null
                        }
                    }
                )
            },
            dismissButton = {
                LaboButton(
                    text = "Annuler",
                    onClick = { showRenameDialog = null },
                    style = LaboButtonStyle.TONAL
                )
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(if (workspaceManager.isTrashEnabled) "Mettre en corbeille ?" else "Supprimer définitivement ?") },
            text = {
                Text(
                    if (workspaceManager.isTrashEnabled)
                        "Les ${selectedFiles.size} élément(s) seront déplacés vers la corbeille."
                    else
                        "Attention : la suppression définitive est irréversible."
                )
            },
            confirmButton = {
                LaboButton(
                    text = if (workspaceManager.isTrashEnabled) "Mettre en corbeille" else "Supprimer",
                    onClick = {
                        for (f in selectedFiles) {
                            if (workspaceManager.isTrashEnabled) {
                                workspaceManager.moveToTrash(f)
                            } else {
                                f.deleteRecursively()
                            }
                        }
                        selectedFiles.clear()
                        refreshRealDir(currentDir)
                        showDeleteConfirmDialog = false
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Éléments supprimés")
                        }
                    },
                    style = LaboButtonStyle.DANGER
                )
            },
            dismissButton = {
                LaboButton(
                    text = "Annuler",
                    onClick = { showDeleteConfirmDialog = false },
                    style = LaboButtonStyle.TONAL
                )
            }
        )
    }

    // Archive Entry Detail Dialog (View/Extract single file)
    selectedArchiveEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { selectedArchiveEntry = null },
            title = {
                Text(
                    text = entry.name,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            text = {
                Column {
                    Text("Chemin interne : /${entry.fullPath}", style = MaterialTheme.typography.bodySmall)
                    Text("Taille réelle : ${entry.formattedSize}", style = MaterialTheme.typography.bodySmall)
                    Text("Taille compressée : ${formatFileSize(entry.compressedSizeBytes)}", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                LaboButton(
                    text = "Extraire dans ce dossier",
                    onClick = {
                        activeArchiveFile?.let { arc ->
                            coroutineScope.launch {
                                val target = File(currentDir, entry.name)
                                ArchiveManager.extractSingleEntry(arc, entry.fullPath, target)
                                refreshRealDir(currentDir)
                                selectedArchiveEntry = null
                                snackbarHostState.showSnackbar("Extrait : ${entry.name}")
                            }
                        }
                    }
                )
            },
            dismissButton = {
                LaboButton(
                    text = "Fermer",
                    onClick = { selectedArchiveEntry = null },
                    style = LaboButtonStyle.TONAL
                )
            }
        )
    }
}

@Composable
private fun RealDirectoryView(
    items: List<FileItem>,
    selectedFiles: List<File>,
    onItemClick: (FileItem) -> Unit,
    onItemLongClick: (FileItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        if (items.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Dossier vide", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(items) { item ->
                val isSelected = selectedFiles.contains(item.file)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else Color.Transparent
                        )
                        .clickable { onItemClick(item) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (item.isDirectory) Icons.Default.Folder
                        else if (item.isArchive) Icons.Default.FolderZip
                        else Icons.Default.Description,
                        contentDescription = null,
                        tint = if (item.isDirectory) MaterialTheme.colorScheme.primary
                        else if (item.isArchive) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (item.isDirectory || item.isArchive) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.isArchive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ARCHIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        Text(
                            text = "${item.formattedSize} • ${item.formattedDate}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
private fun VirtualArchiveView(
    archiveFile: File,
    currentVirtualDir: String,
    entries: List<ArchiveEntryItem>,
    onEnterDirectory: (String) -> Unit,
    onEntryClick: (ArchiveEntryItem) -> Unit,
    onExtractAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Mode Exploration d'Archive",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "Dossier : /${currentVirtualDir.ifBlank { "racine" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LaboButton(
                text = "Tout Extraire",
                onClick = onExtractAll,
                icon = Icons.Default.Unarchive,
                style = LaboButtonStyle.PRIMARY
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (entries.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Aucun élément dans ce dossier virtuel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(entries) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (entry.isDirectory) {
                                    onEnterDirectory(entry.fullPath)
                                } else {
                                    onEntryClick(entry)
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (entry.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = entry.formattedSize,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), thickness = 0.5.dp)
                }
            }
        }
    }
}

package com.example.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.core.model.FileItem
import com.example.core.model.LaboProject
import com.example.core.model.LabxManifest
import com.example.core.model.ThemeMode
import com.example.core.system.ShellExecutor
import com.example.core.ui.LaboButton
import com.example.core.ui.LaboButtonStyle
import com.example.core.ui.UniversalFilePickerModal
import com.example.core.ui.formatFileSize
import com.example.core.workspace.WorkspaceManager
import com.example.feature.labx.LabxEngine
import com.example.ui.drawer.SettingsModal
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workspaceManager: WorkspaceManager,
    shellExecutor: ShellExecutor,
    labxEngine: LabxEngine,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenForge: () -> Unit,
    onOpenLabx: (LabxManifest) -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // State
    var installedExtensions by remember { mutableStateOf<List<LabxManifest>>(emptyList()) }
    var projectsList by remember { mutableStateOf<List<LaboProject>>(emptyList()) }
    var selectedBottomTab by remember { mutableIntStateOf(0) } // 0: Sdcard, 1: PROJET
    var showSettingsModal by remember { mutableStateOf(false) }
    var showPlusLabxPicker by remember { mutableStateOf(false) }
    var isRootActive by remember { mutableStateOf(shellExecutor.isRootEnabled) }

    fun refreshAll() {
        coroutineScope.launch {
            installedExtensions = labxEngine.getInstalledExtensions()
            projectsList = workspaceManager.getProjects()
        }
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(300.dp)
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "L",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "LABO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Kitchen & Labx Hub",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Espace : /data/data/com.labo.kitchen/home",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(12.dp))

                // Menu Item: PLUS (Ajouter un Labx via le gestionnaire popup)
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Plus (Ajouter un Labx)",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            showPlusLabxPicker = true
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_item_plus")
                )

                // Menu Item: PARAMÈTRES
                NavigationDrawerItem(
                    label = {
                        Text(
                            text = "Paramètres",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null
                        )
                    },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            showSettingsModal = true
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        unselectedContainerColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .testTag("drawer_item_settings")
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(end = 8.dp)
                        ) {
                            Text(
                                text = "LABO",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )

                            // Root & status pill
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isRootActive) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { showSettingsModal = true }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isRootActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isRootActive) "ROOT ACTIF" else "NON-ROOT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isRootActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("home_menu_button")
                        ) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu latéral")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Section 1: Carousel horizontal des fonctionnalités
                Text(
                    text = "FONCTIONNALITÉS & EXTENSIONS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Feature 1: FORGE (Built-in kitchen engine)
                    item {
                        FeatureCarouselCard(
                            title = "FORGE",
                            subtitle = "Moteur Ext4 & EroFS",
                            description = "Déballer (UNPACK) et repacker (REPACK) des images système avec base de données.",
                            icon = Icons.Default.Build,
                            isCore = true,
                            onClick = onOpenForge,
                            testTag = "carousel_card_forge"
                        )
                    }

                    // Feature 2..N: Installed Labx extensions
                    items(installedExtensions) { ext ->
                        FeatureCarouselCard(
                            title = ext.name,
                            subtitle = "Extension Labx v${ext.version}",
                            description = ext.description.ifBlank { "Module additionnel LABO" },
                            icon = Icons.Default.Extension,
                            isCore = false,
                            onClick = { onOpenLabx(ext) },
                            testTag = "carousel_card_labx_${ext.id}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Bottom Navigation Tabs (Sdcard / PROJET)
                TabRow(
                    selectedTabIndex = selectedBottomTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedBottomTab == 0,
                        onClick = { selectedBottomTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SdCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sdcard", fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.testTag("tab_sdcard")
                    )
                    Tab(
                        selected = selectedBottomTab == 1,
                        onClick = {
                            selectedBottomTab = 1
                            refreshAll()
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PROJET (${projectsList.size})", fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.testTag("tab_projet")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (selectedBottomTab == 0) {
                        // SDCARD VIEW
                        SdcardBrowserView(
                            sdcardRoot = workspaceManager.sdcardDir
                        )
                    } else {
                        // PROJET VIEW
                        ProjectListView(
                            projects = projectsList,
                            onOpenForgeRepack = { onOpenForge() },
                            onDeleteProject = { projName ->
                                workspaceManager.deleteProject(projName)
                                refreshAll()
                            }
                        )
                    }
                }
            }
        }
    }

    // Plus (+) File Picker Modal (for adding a .labx extension from sdcard)
    if (showPlusLabxPicker) {
        UniversalFilePickerModal(
            initialDir = workspaceManager.sdcardDir,
            allowedExtensions = listOf("labx", "zip", "*"),
            title = "Sélectionner un fichier .labx à installer",
            onFileSelected = { file ->
                showPlusLabxPicker = false
                coroutineScope.launch {
                    try {
                        val installed = labxEngine.installLabx(file)
                        refreshAll()
                        snackbarHostState.showSnackbar("Extension '${installed.name}' installée avec succès !")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("Erreur lors de l'installation : ${e.message}")
                    }
                }
            },
            onDismissRequest = { showPlusLabxPicker = false },
            homeDir = workspaceManager.homeDir,
            sdcardDir = workspaceManager.sdcardDir
        )
    }

    // Settings Modal
    if (showSettingsModal) {
        SettingsModal(
            currentTheme = currentTheme,
            onThemeChange = onThemeChange,
            shellExecutor = shellExecutor,
            isRootActive = isRootActive,
            onRootToggle = { enable ->
                if (enable) {
                    val hasSu = shellExecutor.checkRootAvailable()
                    if (hasSu) {
                        shellExecutor.isRootEnabled = true
                        isRootActive = true
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Binaire 'su' non trouvé sur ce terminal.")
                        }
                    }
                } else {
                    shellExecutor.isRootEnabled = false
                    isRootActive = false
                }
            },
            installedExtensions = installedExtensions,
            labxEngine = labxEngine,
            onExtensionsUpdated = { refreshAll() },
            workspaceManager = workspaceManager,
            onDismissRequest = { showSettingsModal = false }
        )
    }
}

@Composable
private fun FeatureCarouselCard(
    title: String,
    subtitle: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isCore: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCore) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface
        ),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .width(260.dp)
            .height(150.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isCore) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.secondary
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ouvrir",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SdcardBrowserView(sdcardRoot: File) {
    var currentDir by remember { mutableStateOf(sdcardRoot) }
    var fileItems by remember { mutableStateOf<List<FileItem>>(emptyList()) }

    fun refresh(dir: File) {
        currentDir = dir
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val list = dir.listFiles()?.toList() ?: emptyList()
        fileItems = list.map { file ->
            FileItem(
                file = file,
                name = file.name,
                path = file.absolutePath,
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                formattedSize = if (file.isFile) formatFileSize(file.length()) else "Dossier",
                formattedDate = dateFormat.format(Date(file.lastModified())),
                extension = file.extension.lowercase()
            )
        }.sortedWith(
            compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
        )
    }

    LaunchedEffect(currentDir) {
        refresh(currentDir)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Path breadcrumbs
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
                    val p = currentDir.parentFile
                    if (p != null && p.canRead()) refresh(p)
                },
                enabled = currentDir.parentFile != null && currentDir.parentFile?.canRead() == true,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Parent",
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = currentDir.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            if (fileItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Dossier vide ou accès non accordé",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(fileItems) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.isDirectory) refresh(item.file)
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (item.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                            contentDescription = null,
                            tint = if (item.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (item.isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.formattedSize} • ${item.formattedDate}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}

@Composable
private fun ProjectListView(
    projects: List<LaboProject>,
    onOpenForgeRepack: () -> Unit,
    onDeleteProject: (String) -> Unit
) {
    if (projects.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.FolderSpecial,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aucun projet sous home/PROJET",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Utilisez l'option UNPACK de FORGE pour déballer une image système et initialiser un projet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(projects) { proj ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dataset,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = proj.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Partition : ${proj.imgName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Format badge
                            Text(
                                text = proj.fsType.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Path details
                        Text(
                            text = "Unpack : PROJET/${proj.name}/${proj.imgName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Database : PROJET/${proj.name}/database",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Fichiers extraits : ${proj.filesCount} nœuds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            LaboButton(
                                text = "Supprimer",
                                onClick = { onDeleteProject(proj.name) },
                                style = LaboButtonStyle.DANGER,
                                testTag = "delete_project_${proj.name}"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            LaboButton(
                                text = "Repacker dans FORGE",
                                onClick = onOpenForgeRepack,
                                icon = Icons.Default.Build,
                                style = LaboButtonStyle.PRIMARY,
                                testTag = "repack_project_${proj.name}"
                            )
                        }
                    }
                }
            }
        }
    }
}

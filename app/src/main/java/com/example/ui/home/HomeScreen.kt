package com.example.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.LaboProject
import com.example.core.model.LabxManifest
import com.example.core.model.ThemeMode
import com.example.core.system.ShellExecutor
import com.example.core.ui.UniversalFilePickerModal
import com.example.core.workspace.WorkspaceManager
import com.example.feature.labx.LabxEngine
import com.example.ui.drawer.SettingsModal
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workspaceManager: WorkspaceManager,
    shellExecutor: ShellExecutor,
    labxEngine: LabxEngine,
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onOpenForge: () -> Unit,
    onOpenLabx: (LabxManifest) -> Unit,
    onOpenFileManager: (File, String) -> Unit,
    onOpenTrash: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var installedExtensions by remember { mutableStateOf<List<LabxManifest>>(emptyList()) }
    var projectsList by remember { mutableStateOf<List<LaboProject>>(emptyList()) }
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
                modifier = Modifier.width(310.dp)
            ) {
                // Drawer Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "L",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "LABO",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Kitchen & Labx Hub",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "/data/data/com.labo.kitchen/home",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Spacer(modifier = Modifier.height(10.dp))

                // Menu Item 1: Plus (+)
                NavigationDrawerItem(
                    label = { Text("Plus (Ajouter un Labx)", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            showPlusLabxPicker = true
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_plus")
                )

                // Menu Item 2: Corbeille
                NavigationDrawerItem(
                    label = { Text("Corbeille", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            onOpenTrash()
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_trash")
                )

                // Menu Item 3: Paramètres
                NavigationDrawerItem(
                    label = { Text("Paramètres", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch {
                            drawerState.close()
                            showSettingsModal = true
                        }
                    },
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
                    modifier = Modifier.padding(horizontal = 12.dp).testTag("drawer_item_settings")
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

                            // Status badge pill
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                // Section 1: Hero Carousel Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "FONCTIONNALITÉS",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "${1 + installedExtensions.size} actives",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // HERO CAROUSEL
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Hero 1: FORGE
                    item {
                        HeroCarouselCard(
                            title = "FORGE",
                            tagline = "Android Partition Kitchen",
                            description = "Déballage complet (UNPACK) et reconstruction (REPACK) d'images ext4 et erofs sans perte d'information.",
                            icon = Icons.Default.Build,
                            gradientColors = listOf(Color(0xFF007AFF), Color(0xFF0A84FF)),
                            badge = "INTÉGRÉ",
                            onClick = onOpenForge,
                            testTag = "hero_card_forge"
                        )
                    }

                    // Hero 2..N: Extensions Labx
                    itemsIndexed(installedExtensions) { _, ext ->
                        HeroCarouselCard(
                            title = ext.name,
                            tagline = "Extension Labx v${ext.version}",
                            description = ext.description.ifBlank { "Module additionnel LABO" },
                            icon = Icons.Default.Extension,
                            gradientColors = listOf(Color(0xFF5856D6), Color(0xFF34C759)),
                            badge = ext.layout.name,
                            onClick = { onOpenLabx(ext) },
                            testTag = "hero_card_labx_${ext.id}"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Section 2: Storage & Projects Section Title
                Text(
                    text = "ESPACES DE TRAVAIL & STOCKAGE",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // TWO DISTINCT DEDICATED CARDS: Sdcard & PROJET
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Card 1: Sdcard (Mémoire Interne)
                    StorageWorkspaceCard(
                        title = "Sdcard",
                        subtitle = "Mémoire Interne (/sdcard)",
                        description = "Parcourir la mémoire de l'appareil, ouvrir des archives (ZIP, RAR, 7Z, APK) façon ZArchiver, copier et manipuler des fichiers.",
                        icon = Icons.Default.SdCard,
                        iconTint = Color(0xFF007AFF),
                        badgeText = "Stockage",
                        onClick = {
                            onOpenFileManager(workspaceManager.sdcardDir, "Mémoire Interne (Sdcard)")
                        },
                        testTag = "card_workspace_sdcard"
                    )

                    // Card 2: PROJET (Projets Kitchen)
                    StorageWorkspaceCard(
                        title = "PROJET",
                        subtitle = "home/PROJET/ (${projectsList.size} projet(s))",
                        description = "Ouvrir et explorer chaque projet déballé par FORGE, naviguer dans tous les dossiers, examiner et modifier les databases.",
                        icon = Icons.Default.FolderSpecial,
                        iconTint = Color(0xFF34C759),
                        badgeText = "${projectsList.size} PROJET(S)",
                        onClick = {
                            onOpenFileManager(workspaceManager.projectDir, "Projets Kitchen (PROJET)")
                        },
                        testTag = "card_workspace_projet"
                    )
                }
            }
        }
    }

    // Plus (+) File Picker Modal (for installing a .labx file from sdcard)
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

/**
 * Panoramic Hero Carousel Card with iOS-inspired vibrant accenting
 */
@Composable
private fun HeroCarouselCard(
    title: String,
    tagline: String,
    description: String,
    icon: ImageVector,
    gradientColors: List<Color>,
    badge: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .width(290.dp)
            .height(180.dp)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
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
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(gradientColors)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tagline,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lancer",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Large dedicated Workspace Cards for Sdcard and PROJET
 */
@Composable
private fun StorageWorkspaceCard(
    title: String,
    subtitle: String,
    description: String,
    icon: ImageVector,
    iconTint: Color,
    badgeText: String,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(30.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(iconTint.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        color = iconTint
                    )
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

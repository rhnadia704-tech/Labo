package com.example.feature.forge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dataset
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.LaboProject
import com.example.core.system.ImageAnalyzer
import com.example.core.system.ImageFsInfo
import com.example.core.system.ShellExecutor
import com.example.core.ui.LaboButton
import com.example.core.ui.LaboButtonStyle
import com.example.core.ui.LaboCard
import com.example.core.ui.LaboPickerField
import com.example.core.ui.UniversalFilePickerModal
import com.example.core.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgeScreen(
    workspaceManager: WorkspaceManager,
    shellExecutor: ShellExecutor,
    onNavigateBack: () -> Unit,
    onOpenProject: (LaboProject) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val engine = remember { ForgeEngine(workspaceManager, shellExecutor) }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: UNPACK, 1: REPACK

    // UNPACK state
    var selectedImgPath by remember { mutableStateOf("") }
    var projectName by remember { mutableStateOf("") }
    var detectedFsInfo by remember { mutableStateOf<ImageFsInfo?>(null) }
    var showFilePickerForUnpack by remember { mutableStateOf(false) }

    // REPACK state
    var projectsList by remember { mutableStateOf<List<LaboProject>>(emptyList()) }
    var selectedProject by remember { mutableStateOf<LaboProject?>(null) }
    var repackDropdownExpanded by remember { mutableStateOf(false) }

    // Engine operation state
    var opState by remember { mutableStateOf(ForgeOperationState()) }

    fun refreshProjects() {
        val list = workspaceManager.getProjects()
        projectsList = list
        if (selectedProject == null && list.isNotEmpty()) {
            selectedProject = list.first()
        }
    }

    LaunchedEffect(Unit) {
        refreshProjects()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "FORGE",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("forge_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
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
                .padding(horizontal = 16.dp)
        ) {
            // Mode Tabs (UNPACK / REPACK)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UNPACK", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("forge_tab_unpack")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        refreshProjects()
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("REPACK", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("forge_tab_repack")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Content Area based on Tab
            if (selectedTab == 0) {
                // UNPACK VIEW
                UnpackView(
                    selectedImgPath = selectedImgPath,
                    onSelectedImgPathChange = { path ->
                        selectedImgPath = path
                        val f = File(path)
                        if (f.exists()) {
                            projectName = f.nameWithoutExtension
                            detectedFsInfo = ImageAnalyzer.analyzeImage(f)
                        }
                    },
                    projectName = projectName,
                    onProjectNameChange = { projectName = it },
                    detectedFsInfo = detectedFsInfo,
                    onOpenPicker = { showFilePickerForUnpack = true },
                    onStartUnpack = {
                        val file = File(selectedImgPath)
                        if (file.exists() && !opState.isRunning) {
                            coroutineScope.launch {
                                val proj = engine.unpackImage(
                                    imageFile = file,
                                    customProjectName = projectName
                                ) { state ->
                                    opState = state
                                }
                                refreshProjects()
                            }
                        }
                    },
                    isRunning = opState.isRunning
                )
            } else {
                // REPACK VIEW
                RepackView(
                    projectsList = projectsList,
                    selectedProject = selectedProject,
                    expanded = repackDropdownExpanded,
                    onExpandedChange = { repackDropdownExpanded = it },
                    onSelectProject = {
                        selectedProject = it
                        repackDropdownExpanded = false
                    },
                    repackOutputFolder = workspaceManager.repackOutputDir.absolutePath,
                    onStartRepack = {
                        val proj = selectedProject
                        if (proj != null && !opState.isRunning) {
                            coroutineScope.launch {
                                engine.repackProject(proj) { state ->
                                    opState = state
                                }
                            }
                        }
                    },
                    isRunning = opState.isRunning,
                    onOpenProject = onOpenProject
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Operation Progress & Console Logs
            AnimatedVisibility(visible = opState.isRunning || opState.logs.isNotEmpty()) {
                OperationConsole(opState = opState)
            }
        }
    }

    // Universal File Picker Modal (for selecting .img files)
    if (showFilePickerForUnpack) {
        UniversalFilePickerModal(
            initialDir = workspaceManager.sdcardDir,
            allowedExtensions = listOf("img", "bin", "*"),
            title = "Sélectionner une image (.img)",
            onFileSelected = { file ->
                selectedImgPath = file.absolutePath
                projectName = file.nameWithoutExtension
                detectedFsInfo = ImageAnalyzer.analyzeImage(file)
                showFilePickerForUnpack = false
            },
            onDismissRequest = { showFilePickerForUnpack = false },
            homeDir = workspaceManager.homeDir,
            sdcardDir = workspaceManager.sdcardDir
        )
    }
}

@Composable
private fun UnpackView(
    selectedImgPath: String,
    onSelectedImgPathChange: (String) -> Unit,
    projectName: String,
    onProjectNameChange: (String) -> Unit,
    detectedFsInfo: ImageFsInfo?,
    onOpenPicker: () -> Unit,
    onStartUnpack: () -> Unit,
    isRunning: Boolean
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Déballage Intégral d'Image Système",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Extrait 100% du contenu sans perte vers PROJET/ avec génération de la database exhaustive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Uniform Picker Field
            LaboPickerField(
                value = selectedImgPath,
                onValueChange = onSelectedImgPathChange,
                label = "Chemin de l'image .img",
                placeholder = "/sdcard/system.img",
                onOpenPicker = onOpenPicker,
                testTag = "forge_unpack_picker_field"
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Project Name input
            OutlinedTextField(
                value = projectName,
                onValueChange = onProjectNameChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("forge_project_name_input"),
                label = { Text("Nom du PROJET créé") },
                placeholder = { Text("ex: system_pixel7") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Detected FS Info Badge
            if (detectedFsInfo != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Dataset,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Format : ${detectedFsInfo.format.uppercase()} (${if (detectedFsInfo.isSparse) "Sparse" else "Raw"})",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Bloc : ${detectedFsInfo.blockSize} o • Arch : ${detectedFsInfo.architecture} • Magic : ${detectedFsInfo.magicHex}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Uniform Action Button
            LaboButton(
                text = "Déballer l'image",
                onClick = onStartUnpack,
                icon = Icons.Default.PlayArrow,
                isLoading = isRunning,
                enabled = selectedImgPath.isNotBlank() && !isRunning,
                style = LaboButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
                testTag = "forge_unpack_button"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepackView(
    projectsList: List<LaboProject>,
    selectedProject: LaboProject?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectProject: (LaboProject) -> Unit,
    repackOutputFolder: String,
    onStartRepack: () -> Unit,
    isRunning: Boolean,
    onOpenProject: (LaboProject) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Reconstruction d'Image (REPACK)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Reconstruit l'image selon database/ et PROJET/ vers sdcard/LABO/REPACK/.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (projectsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun projet déballé dans PROJET.\nUtilisez d'abord l'option UNPACK.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // Dropdown to pick project
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = onExpandedChange,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedProject?.name ?: "Sélectionner un projet",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Projet à repacker") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("forge_repack_dropdown"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { onExpandedChange(false) }
                    ) {
                        projectsList.forEach { proj ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(proj.name, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Format: ${proj.fsType.uppercase()} • Image: ${proj.imgName}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                },
                                onClick = { onSelectProject(proj) }
                            )
                        }
                    }
                }

                // Project Details Card
                selectedProject?.let { proj ->
                    Spacer(modifier = Modifier.height(10.dp))
                    LaboCard(modifier = Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = proj.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = proj.fsType.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Source img : PROJET/${proj.name}/${proj.imgName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Database : ${if (proj.databaseDir.exists()) "Exhaustive (fs_config, contexts, symlinks, xattrs)" else "Absente"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Destination : $repackOutputFolder/${proj.name}_repacked_${proj.fsType}.img",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Uniform Repack Button
                LaboButton(
                    text = "Lancer le REPACK",
                    onClick = onStartRepack,
                    icon = Icons.Default.Layers,
                    isLoading = isRunning,
                    enabled = selectedProject != null && !isRunning,
                    style = LaboButtonStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "forge_repack_button"
                )
            }
        }
    }
}

@Composable
private fun OperationConsole(opState: ForgeOperationState) {
    val listState = rememberLazyListState()

    LaunchedEffect(opState.logs.size) {
        if (opState.logs.isNotEmpty()) {
            listState.animateScrollToItem(opState.logs.size - 1)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF070B12)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = Color(0xFF0A84FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONSOLE FORGE v2",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0A84FF)
                    )
                }
                if (opState.resultSuccess == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF34C759),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SUCCÈS",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF34C759)
                        )
                    }
                }
            }

            if (opState.isRunning) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { opState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFF0A84FF),
                    trackColor = Color(0xFF141D2D)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(opState.logs) { logLine ->
                    Text(
                        text = "> $logLine",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        ),
                        fontFamily = FontFamily.Monospace,
                        color = if (logLine.contains("succès", ignoreCase = true)) Color(0xFF34C759)
                        else if (logLine.contains("erreur", ignoreCase = true)) Color(0xFFFF453A)
                        else Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

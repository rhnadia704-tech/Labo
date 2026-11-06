package com.example.feature.labx

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import com.example.core.model.LabxAction
import com.example.core.model.LabxLayout
import com.example.core.model.LabxManifest
import com.example.core.system.CommandResult
import com.example.core.ui.LaboButton
import com.example.core.ui.LaboButtonStyle
import com.example.core.ui.LaboPickerField
import com.example.core.ui.UniversalFilePickerModal
import com.example.core.workspace.WorkspaceManager
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabxScreen(
    manifest: LabxManifest,
    engine: LabxEngine,
    workspaceManager: WorkspaceManager,
    onNavigateBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Map of actionId -> selectedFile
    val selectedFiles = remember { mutableStateMapOf<String, String>() }

    // Picker state
    var activeActionForPicker by remember { mutableStateOf<LabxAction?>(null) }

    // Execution state
    var isExecutingActionId by remember { mutableStateOf<String?>(null) }
    var executionResult by remember { mutableStateOf<CommandResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = manifest.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Labx v${manifest.version} • Par ${manifest.author} • Layout ${manifest.layout.name.lowercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("labx_back_button")
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
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
                .padding(16.dp)
        ) {
            // Extension description and privilege tags
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = manifest.description.ifBlank { "Extension dynamique LABX" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (manifest.requiresRoot) {
                                Text(
                                    text = "ROOT REQUIS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (manifest.requiresStorage) {
                                Text(
                                    text = "ACCÈS STOCKAGE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Strictly render either VERTICAL or GRID layout based on manifest!
            if (manifest.layout == LabxLayout.GRID) {
                // GRID LAYOUT
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(manifest.actions) { action ->
                        ActionCard(
                            action = action,
                            selectedFilePath = selectedFiles[action.id] ?: "",
                            onSelectedFileChange = { selectedFiles[action.id] = it },
                            onOpenPicker = { activeActionForPicker = action },
                            onExecute = {
                                isExecutingActionId = action.id
                                coroutineScope.launch {
                                    val sel = selectedFiles[action.id]?.let { File(it) }
                                    val res = engine.executeAction(manifest, action, sel)
                                    executionResult = res
                                    isExecutingActionId = null
                                }
                            },
                            isLoading = isExecutingActionId == action.id,
                            isGridMode = true
                        )
                    }
                }
            } else {
                // VERTICAL LAYOUT
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(manifest.actions) { action ->
                        ActionCard(
                            action = action,
                            selectedFilePath = selectedFiles[action.id] ?: "",
                            onSelectedFileChange = { selectedFiles[action.id] = it },
                            onOpenPicker = { activeActionForPicker = action },
                            onExecute = {
                                isExecutingActionId = action.id
                                coroutineScope.launch {
                                    val sel = selectedFiles[action.id]?.let { File(it) }
                                    val res = engine.executeAction(manifest, action, sel)
                                    executionResult = res
                                    isExecutingActionId = null
                                }
                            },
                            isLoading = isExecutingActionId == action.id,
                            isGridMode = false
                        )
                    }
                }
            }
        }
    }

    // Universal File Picker Modal for Action
    activeActionForPicker?.let { action ->
        UniversalFilePickerModal(
            initialDir = workspaceManager.sdcardDir,
            allowedExtensions = action.allowedExtensions,
            pickDirectoryOnly = action.filePickerTarget == "project" || action.filePickerTarget == "folder",
            title = "Sélectionner pour : ${action.title}",
            onFileSelected = { file ->
                selectedFiles[action.id] = file.absolutePath
                activeActionForPicker = null
            },
            onDismissRequest = { activeActionForPicker = null },
            homeDir = workspaceManager.homeDir,
            sdcardDir = workspaceManager.sdcardDir
        )
    }

    // Execution Result Output Dialog
    executionResult?.let { res ->
        AlertDialog(
            onDismissRequest = { executionResult = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (res.exitCode == 0) "Exécution réussie" else "Erreur d'exécution",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Code de sortie : ${res.exitCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = if (res.output.isNotBlank()) res.output else if (res.error.isNotBlank()) res.error else "Opération terminée sans sortie.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            fontFamily = FontFamily.Monospace,
                            color = if (res.exitCode == 0) Color(0xFF4ADE80) else Color(0xFFF87171)
                        )
                    }
                }
            },
            confirmButton = {
                LaboButton(
                    text = "Fermer",
                    onClick = { executionResult = null },
                    style = LaboButtonStyle.PRIMARY
                )
            }
        )
    }
}

@Composable
private fun ActionCard(
    action: LabxAction,
    selectedFilePath: String,
    onSelectedFileChange: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onExecute: () -> Unit,
    isLoading: Boolean,
    isGridMode: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = action.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (action.description.isNotBlank()) {
                Text(
                    text = action.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isGridMode) 2 else 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (action.requiresFilePicker) {
                Spacer(modifier = Modifier.height(8.dp))
                // Always the uniform LaboPickerField!
                LaboPickerField(
                    value = selectedFilePath,
                    onValueChange = onSelectedFileChange,
                    label = "Cible",
                    placeholder = if (action.filePickerTarget == "project") "Sélectionner projet" else "Sélectionner fichier",
                    onOpenPicker = onOpenPicker,
                    testTag = "labx_action_picker_${action.id}"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Always the uniform LaboButton!
            LaboButton(
                text = "Exécuter",
                onClick = onExecute,
                icon = Icons.Default.PlayArrow,
                isLoading = isLoading,
                enabled = !isLoading && (!action.requiresFilePicker || selectedFilePath.isNotBlank()),
                style = LaboButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
                testTag = "labx_action_button_${action.id}"
            )
        }
    }
}

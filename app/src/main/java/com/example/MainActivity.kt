package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.core.model.LaboProject
import com.example.core.model.LabxManifest
import com.example.core.model.ThemeMode
import com.example.core.system.ShellExecutor
import com.example.core.workspace.WorkspaceManager
import com.example.feature.forge.ForgeScreen
import com.example.feature.labx.LabxEngine
import com.example.feature.labx.LabxScreen
import com.example.ui.home.HomeScreen
import com.example.ui.theme.LaboTheme

sealed class AppScreen {
    object Home : AppScreen()
    object Forge : AppScreen()
    data class LabxDetail(val manifest: LabxManifest) : AppScreen()
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val workspaceManager = WorkspaceManager(applicationContext)
        val shellExecutor = ShellExecutor(workspaceManager.homeDir, workspaceManager.binDir)
        val labxEngine = LabxEngine(applicationContext, workspaceManager, shellExecutor)

        setContent {
            var currentTheme by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }

            // Storage Permission Launcher
            val storagePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { /* Permissions result handled */ }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (!Environment.isExternalStorageManager()) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            startActivity(intent)
                        }
                    }
                } else {
                    storagePermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            }

            LaboTheme(themeMode = currentTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            slideInHorizontally { width -> width } togetherWith slideOutHorizontally { width -> -width }
                        },
                        label = "screen_transition"
                    ) { screen ->
                        when (screen) {
                            is AppScreen.Home -> {
                                HomeScreen(
                                    workspaceManager = workspaceManager,
                                    shellExecutor = shellExecutor,
                                    labxEngine = labxEngine,
                                    currentTheme = currentTheme,
                                    onThemeChange = { currentTheme = it },
                                    onOpenForge = { currentScreen = AppScreen.Forge },
                                    onOpenLabx = { manifest -> currentScreen = AppScreen.LabxDetail(manifest) }
                                )
                            }
                            is AppScreen.Forge -> {
                                BackHandler {
                                    currentScreen = AppScreen.Home
                                }
                                ForgeScreen(
                                    workspaceManager = workspaceManager,
                                    onNavigateBack = { currentScreen = AppScreen.Home },
                                    onOpenProject = { /* project opened */ }
                                )
                            }
                            is AppScreen.LabxDetail -> {
                                BackHandler {
                                    currentScreen = AppScreen.Home
                                }
                                LabxScreen(
                                    manifest = screen.manifest,
                                    engine = labxEngine,
                                    workspaceManager = workspaceManager,
                                    onNavigateBack = { currentScreen = AppScreen.Home }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

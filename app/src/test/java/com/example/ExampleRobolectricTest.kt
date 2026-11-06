package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.system.ImageAnalyzer
import com.example.core.workspace.WorkspaceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("LABO", appName)
  }

  @Test
  fun `workspace manager initializes required kitchen directories`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val workspaceManager = WorkspaceManager(context)

    assertNotNull(workspaceManager.homeDir)
    assertTrue(workspaceManager.binDir.exists())
    assertTrue(workspaceManager.extensionsDir.exists())
    assertTrue(workspaceManager.projectDir.exists())
    assertTrue(workspaceManager.tmpDir.exists())
  }

  @Test
  fun `image analyzer generates valid project database`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val tempDir = File(context.cacheDir, "test_project_db").apply { mkdirs() }
    val databaseDir = File(tempDir, "database").apply { mkdirs() }

    val dummyImage = File(tempDir, "system.img")
    dummyImage.writeBytes(ByteArray(8192))

    val fsInfo = ImageAnalyzer.analyzeImage(dummyImage)
    assertNotNull(fsInfo)

    ImageAnalyzer.generateProjectDatabase(
      databaseDir = databaseDir,
      fsInfo = fsInfo,
      extractedFiles = emptyList(),
      targetPartitionName = "system"
    )

    val infoJson = File(databaseDir, "filesystem_info.json")
    val fsConfig = File(databaseDir, "fs_config")
    val fileContexts = File(databaseDir, "file_contexts")

    assertTrue(infoJson.exists())
    assertTrue(fsConfig.exists())
    assertTrue(fileContexts.exists())
  }
}

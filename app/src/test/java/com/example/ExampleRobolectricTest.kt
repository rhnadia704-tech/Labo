package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.archive.ArchiveManager
import com.example.core.system.ImageAnalyzer
import com.example.core.workspace.WorkspaceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    assertTrue(workspaceManager.trashDir.exists())
  }

  @Test
  fun `workspace trash system moves and restores files`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val workspaceManager = WorkspaceManager(context)

    val testFile = File(context.cacheDir, "test_file.txt")
    testFile.writeText("Contenu test LABO")

    assertTrue(testFile.exists())
    workspaceManager.moveToTrash(testFile)

    assertFalse(testFile.exists())
    val trashItems = workspaceManager.getTrashItems()
    assertTrue(trashItems.isNotEmpty())

    val item = trashItems.first()
    assertEquals("test_file.txt", item.name)

    workspaceManager.restoreFromTrash(item)
    assertTrue(testFile.exists())
  }

  @Test
  fun `archive manager correctly identifies archive extensions`() {
    assertTrue(ArchiveManager.isArchiveFile(File("app-release.apk")))
    assertTrue(ArchiveManager.isArchiveFile(File("rom_update.zip")))
    assertTrue(ArchiveManager.isArchiveFile(File("system.tar")))
    assertFalse(ArchiveManager.isArchiveFile(File("system.img")))
    assertFalse(ArchiveManager.isArchiveFile(File("build.prop")))
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
      extractedRoot = tempDir,
      targetPartitionName = "system"
    )

    val infoJson = File(databaseDir, "filesystem_info.json")
    val fsConfig = File(databaseDir, "fs_config")
    val fileContexts = File(databaseDir, "file_contexts")
    val symlinks = File(databaseDir, "symlinks.json")
    val xattrs = File(databaseDir, "xattrs.json")

    assertTrue(infoJson.exists())
    assertTrue(fsConfig.exists())
    assertTrue(fileContexts.exists())
    assertTrue(symlinks.exists())
    assertTrue(xattrs.exists())
  }

  @Test
  fun `ext4 builder creates valid ext4 image and ext4 extractor unpacks it faithfully`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rootTestDir = File(context.cacheDir, "ext4_roundtrip_test").apply { mkdirs() }
    val srcDir = File(rootTestDir, "source_system").apply { mkdirs() }
    val outImg = File(rootTestDir, "test_ext4.img")
    val extractDir = File(rootTestDir, "extracted_system").apply { mkdirs() }

    // Create realistic system tree
    val binDir = File(srcDir, "bin").apply { mkdirs() }
    val etcDir = File(srcDir, "etc").apply { mkdirs() }
    val appDir = File(srcDir, "system/app").apply { mkdirs() }
    val shFile = File(binDir, "sh").apply { writeText("#!/system/bin/sh\necho Hello LABO\n") }
    val propFile = File(etcDir, "build.prop").apply { writeText("ro.build.version.release=15\nro.product.model=LABO-GSI\n") }
    val apkFile = File(appDir, "DummyApp.apk").apply { writeText("DUMMY_APK_DATA_FOR_LABO_TESTING") }

    val builder = com.example.core.system.ext4.Ext4Builder(
      sourceDir = srcDir,
      outputFile = outImg,
      volumeLabel = "test_system"
    )

    val imageSize = builder.build { _, _ -> }
    assertTrue(outImg.exists())
    assertTrue(imageSize > 0)

    // Verify EXT4 superblock magic (0xEF53 at offset 1080) and standard geometry
    java.io.RandomAccessFile(outImg, "r").use { raf ->
      raf.seek(1080L)
      val magicLow = raf.read()
      val magicHigh = raf.read()
      val magic = (magicHigh shl 8) or magicLow
      assertEquals(0xEF53, magic)

      // Verify blocks_per_group is 32768 (offset 1024 + 32 = 1056)
      val buf32 = ByteArray(4)
      raf.seek(1056L)
      raf.readFully(buf32)
      val blocksPerGroup = java.nio.ByteBuffer.wrap(buf32).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()
      assertEquals(32768, blocksPerGroup)

      // Verify rev_level is 1 (EXT4_DYNAMIC_REV) at offset 1024 + 76 = 1100
      raf.seek(1100L)
      raf.readFully(buf32)
      val revLevel = java.nio.ByteBuffer.wrap(buf32).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt()
      assertEquals(1, revLevel)
    }

    // Now extract with Ext4Extractor
    val extractor = com.example.core.system.ext4.Ext4Extractor(outImg)
    val extractedCount = extractor.extractAll(extractDir) { _, _ -> }
    extractor.close()

    assertTrue(extractedCount >= 3)
    val extractedSh = File(extractDir, "bin/sh")
    val extractedProp = File(extractDir, "etc/build.prop")
    val extractedApk = File(extractDir, "system/app/DummyApp.apk")

    assertTrue("bin/sh should exist", extractedSh.exists())
    assertTrue("etc/build.prop should exist", extractedProp.exists())
    assertTrue("system/app/DummyApp.apk should exist", extractedApk.exists())
    assertEquals(shFile.readText(), extractedSh.readText())
    assertEquals(propFile.readText(), extractedProp.readText())
    assertEquals(apkFile.readText(), extractedApk.readText())
  }
}

package com.example.core.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String
)

class ShellExecutor(
    private val workspaceHome: File,
    private val binDir: File
) {

    var isRootEnabled: Boolean = false

    fun checkRootAvailable(): Boolean {
        val paths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/vendor/bin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }

        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            process.waitFor()
            !line.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun executeCommand(
        command: String,
        workingDir: File = workspaceHome,
        envVars: Map<String, String> = emptyMap(),
        forceRoot: Boolean = false
    ): CommandResult = withContext(Dispatchers.IO) {
        val useRoot = (isRootEnabled || forceRoot) && checkRootAvailable()
        val shellCmd = if (useRoot) "su" else "sh"

        val envList = mutableListOf<String>()
        System.getenv().forEach { (k, v) -> envList.add("$k=$v") }
        envList.add("LABO_HOME=${workspaceHome.absolutePath}")
        envList.add("LABO_BIN=${binDir.absolutePath}")
        envList.add("PATH=${binDir.absolutePath}:/system/bin:/system/xbin:${System.getenv("PATH") ?: ""}")
        envVars.forEach { (k, v) -> envList.add("$k=$v") }

        try {
            val process = Runtime.getRuntime().exec(
                arrayOf(shellCmd, "-c", command),
                envList.toTypedArray(),
                workingDir
            )

            val outputBuffer = StringBuilder()
            val errorBuffer = StringBuilder()

            val stdReader = BufferedReader(InputStreamReader(process.inputStream))
            val errReader = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (stdReader.readLine().also { line = it } != null) {
                outputBuffer.appendLine(line)
            }
            while (errReader.readLine().also { line = it } != null) {
                errorBuffer.appendLine(line)
            }

            val exitCode = process.waitFor()
            CommandResult(
                exitCode = exitCode,
                output = outputBuffer.toString().trim(),
                error = errorBuffer.toString().trim()
            )
        } catch (e: Exception) {
            CommandResult(
                exitCode = -1,
                output = "",
                error = e.localizedMessage ?: "Unknown execution error"
            )
        }
    }
}

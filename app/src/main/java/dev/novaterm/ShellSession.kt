package dev.novaterm

import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShellResult(
    val output: String,
    val exitCode: Int,
    val clearScreen: Boolean = false
)

/**
 * Executes commands through Android's own /system/bin/sh.
 *
 * This is an app-UID shell, not root and not a Linux distribution.
 * Each command is a separate shell process; cd is handled here so the
 * selected working directory persists between commands.
 */
class ShellSession(context: Context) {
    val workspace: File = File(context.filesDir, "workspace").apply {
        if (!exists()) mkdirs()
    }

    private var currentDirectory: File = workspace

    fun prompt(): String = currentDirectory.absolutePath

    suspend fun execute(rawCommand: String): ShellResult = withContext(Dispatchers.IO) {
        val command = rawCommand.trim()
        if (command.isEmpty()) return@withContext ShellResult("", 0)

        when {
            command == "clear" || command == "cls" ->
                return@withContext ShellResult("", 0, clearScreen = true)

            command == "pwd" ->
                return@withContext ShellResult(currentDirectory.absolutePath, 0)

            command == "help" ->
                return@withContext ShellResult(
                    "NovaTerm local shell\n" +
                        "Built-ins: help, clear, pwd, cd [directory]\n" +
                        "Commands run as the Android app user through /system/bin/sh.\n" +
                        "This build does not provide root, apt, proot, or a Debian/Ubuntu userspace.",
                    0
                )

            command == "cd" || command.startsWith("cd ") -> {
                val argument = command.removePrefix("cd").trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                val destination = when {
                    argument.isEmpty() || argument == "~" -> workspace
                    argument.startsWith("~/") -> File(workspace, argument.removePrefix("~/"))
                    else -> File(currentDirectory, argument)
                }
                val canonical = runCatching { destination.canonicalFile }.getOrElse { destination }
                return@withContext if (canonical.exists() && canonical.isDirectory) {
                    currentDirectory = canonical
                    ShellResult("", 0)
                } else {
                    ShellResult("cd: no such directory: $argument", 1)
                }
            }
        }

        if (command.length > 4096) {
            return@withContext ShellResult("Command is too long (maximum 4096 characters).", 2)
        }

        val outputFile = File.createTempFile("novaterm-output-", ".txt")
        try {
            val process = ProcessBuilder("/system/bin/sh", "-c", command)
                .directory(currentDirectory)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .apply {
                    environment()["HOME"] = workspace.absolutePath
                    environment()["PWD"] = currentDirectory.absolutePath
                    environment()["PATH"] = "/system/bin:/system/xbin"
                    environment()["TERM"] = "dumb"
                }
                .start()

            val completed = process.waitFor(45, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }

            val text = runCatching { outputFile.readText() }.getOrDefault("")
            val shortened = if (text.length > 16000) {
                text.take(16000) + "\n… output truncated …"
            } else {
                text
            }

            if (!completed) {
                ShellResult(shortened + "\n[command stopped after 45 seconds]", 124)
            } else {
                ShellResult(shortened.ifBlank { if (process.exitValue() == 0) "" else "(no output)" }, process.exitValue())
            }
        } catch (error: Exception) {
            ShellResult("Execution error: ${error.message ?: error::class.java.simpleName}", 127)
        } finally {
            outputFile.delete()
        }
    }
}

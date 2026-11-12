package dev.novaterm

import android.content.Context
import android.os.Build
import android.system.Os
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

enum class LinuxInstallStage {
    NOT_INSTALLED,
    DOWNLOADING_PROOT,
    DOWNLOADING_ROOTFS,
    VERIFYING,
    EXTRACTING,
    READY,
    FAILED
}

data class LinuxInstallProgress(
    val stage: LinuxInstallStage = LinuxInstallStage.NOT_INSTALLED,
    val fraction: Float = 0f,
    val message: String = "尚未安装 Debian",
    val error: String? = null
)

/**
 * Installs an isolated Debian Bookworm ARM64 root filesystem in app-private storage.
 *
 * PRoot and the Debian rootfs are downloaded from pinned upstream artifacts and
 * SHA-256 verified before extraction. The app never obtains Android root access.
 */
class LinuxEnvironmentManager(context: Context) {
    private val appContext = context.applicationContext
    private val baseDir = File(appContext.filesDir, "linux")
    private val runtimeDir = File(baseDir, "runtime")
    private val rootfsDir = File(baseDir, "debian-bookworm-arm64")
    private val prootZip = File(baseDir, "proot-aarch64.zip")
    private val rootfsArchive = File(baseDir, "debian-bookworm-arm64.tar.gz")
    private val temporaryRootfs = File(baseDir, "debian-bookworm-arm64.installing")
    private val installMutex = Mutex()

    private val _progress = MutableStateFlow(LinuxInstallProgress())
    val progress: StateFlow<LinuxInstallProgress> = _progress.asStateFlow()

    private var linuxWorkingDirectory = "/root"

    private val prootBinary: File
        get() = File(runtimeDir, "proot")

    val isSupported: Boolean
        get() = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }

    val isInstalled: Boolean
        get() = prootBinary.isFile &&
            prootBinary.canExecute() &&
            (File(rootfsDir, "usr/bin/bash").isFile || File(rootfsDir, "bin/bash").exists()) &&
            File(rootfsDir, ".novaterm-rootfs-version").isFile

    val rootfsPath: String
        get() = rootfsDir.absolutePath

    fun prompt(): String = "debian:$linuxWorkingDirectory#"

    suspend fun install(): Result<Unit> = withContext(Dispatchers.IO) {
        installMutex.withLock {
            try {
                if (!isSupported) {
                    error("当前设备没有 arm64-v8a ABI，暂时不支持 Debian ARM64。")
                }

                baseDir.mkdirs()

                if (!prootBinary.isFile || !prootBinary.canExecute()) {
                    _progress.value = LinuxInstallProgress(
                        LinuxInstallStage.DOWNLOADING_PROOT,
                        0f,
                        "正在下载 Android ARM64 PRoot…"
                    )
                    downloadVerified(
                        url = PROOT_URL,
                        expectedSha256 = PROOT_SHA256,
                        destination = prootZip,
                        expectedBytes = null
                    ) { fraction ->
                        _progress.value = LinuxInstallProgress(
                            LinuxInstallStage.DOWNLOADING_PROOT,
                            fraction,
                            "正在下载 PRoot ${(fraction * 100).toInt()}%"
                        )
                    }
                    _progress.value = LinuxInstallProgress(
                        LinuxInstallStage.EXTRACTING,
                        0.08f,
                        "正在安装 PRoot 运行时…"
                    )
                    installProotZip()
                }

                if (!isInstalled) {
                    _progress.value = LinuxInstallProgress(
                        LinuxInstallStage.DOWNLOADING_ROOTFS,
                        0f,
                        "正在下载 Debian Bookworm ARM64（约 48 MB）…"
                    )
                    downloadVerified(
                        url = ROOTFS_URL,
                        expectedSha256 = ROOTFS_SHA256,
                        destination = rootfsArchive,
                        expectedBytes = ROOTFS_BYTES
                    ) { fraction ->
                        _progress.value = LinuxInstallProgress(
                            LinuxInstallStage.DOWNLOADING_ROOTFS,
                            fraction,
                            "正在下载 Debian ${(fraction * 100).toInt()}%"
                        )
                    }

                    _progress.value = LinuxInstallProgress(
                        LinuxInstallStage.VERIFYING,
                        1f,
                        "SHA-256 校验通过，准备解包…"
                    )

                    if (temporaryRootfs.exists()) temporaryRootfs.deleteRecursively()
                    temporaryRootfs.mkdirs()

                    _progress.value = LinuxInstallProgress(
                        LinuxInstallStage.EXTRACTING,
                        0.02f,
                        "正在解压 Debian 文件系统…"
                    )
                    extractRootfs(rootfsArchive, temporaryRootfs)

                    File(temporaryRootfs, ".novaterm-rootfs-version").writeText(ROOTFS_VERSION)
                    val hasBash = File(temporaryRootfs, "usr/bin/bash").isFile ||
                        File(temporaryRootfs, "bin/bash").exists()
                    check(hasBash) { "Debian rootfs 解包不完整：找不到 bash。" }

                    if (rootfsDir.exists()) rootfsDir.deleteRecursively()
                    check(temporaryRootfs.renameTo(rootfsDir)) {
                        "无法将临时文件系统移动到正式目录，请检查可用空间。"
                    }
                    rootfsArchive.delete()
                }

                linuxWorkingDirectory = "/root"
                _progress.value = LinuxInstallProgress(
                    LinuxInstallStage.READY,
                    1f,
                    "Debian Bookworm ARM64 已安装"
                )
                Result.success(Unit)
            } catch (error: kotlinx.coroutines.CancellationException) {
                temporaryRootfs.deleteRecursively()
                throw error
            } catch (error: Exception) {
                temporaryRootfs.deleteRecursively()
                _progress.value = LinuxInstallProgress(
                    LinuxInstallStage.FAILED,
                    0f,
                    "安装失败",
                    error.message ?: error::class.java.simpleName
                )
                Result.failure(error)
            }
        }
    }

    suspend fun execute(rawCommand: String): ShellResult = withContext(Dispatchers.IO) {
        val command = rawCommand.trim()
        if (command.isEmpty()) return@withContext ShellResult("", 0)
        if (!isInstalled) {
            return@withContext ShellResult("Debian 尚未安装。请先到「环境」页面安装 Linux。", 1)
        }
        if (command.length > 4096) {
            return@withContext ShellResult("命令过长（最多 4096 个字符）。", 2)
        }

        when {
            command == "clear" || command == "cls" ->
                return@withContext ShellResult("", 0, clearScreen = true)

            command == "pwd" ->
                return@withContext ShellResult(linuxWorkingDirectory, 0)

            command == "whoami" ->
                return@withContext ShellResult("root", 0)

            command == "help" ->
                return@withContext ShellResult(
                    "NovaTerm Debian shell\n" +
                        "Debian Bookworm ARM64 · PRoot rootless environment\n" +
                        "Built-ins: help, clear, pwd, cd, whoami\n" +
                        "Try: apt update, apt install python3 git openssh-client",
                    0
                )

            command == "cd" || command.startsWith("cd ") -> {
                val argument = command.removePrefix("cd").trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                val destination = when {
                    argument.isEmpty() || argument == "~" -> "/root"
                    argument.startsWith("/") -> argument
                    else -> "$linuxWorkingDirectory/$argument"
                }
                val resolved = normalizeGuestPath(destination)
                val directory = File(rootfsDir, resolved.removePrefix("/"))
                return@withContext if (directory.isDirectory) {
                    linuxWorkingDirectory = resolved
                    ShellResult("", 0)
                } else {
                    ShellResult("cd: no such directory: $argument", 1)
                }
            }
        }

        val outputFile = File.createTempFile("novaterm-linux-", ".log", appContext.cacheDir)
        try {
            val args = mutableListOf(
                prootBinary.absolutePath,
                "-0",
                "--link2symlink",
                "-r", rootfsDir.absolutePath,
                "-b", "/dev",
                "-b", "/proc",
                "-b", "/sys",
                "-w", linuxWorkingDirectory,
                "/usr/bin/env",
                "-i",
                "HOME=/root",
                "USER=root",
                "LOGNAME=root",
                "SHELL=/bin/bash",
                "TERM=xterm-256color",
                "LANG=C.UTF-8",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "/bin/bash",
                "-lc",
                command
            )

            val process = ProcessBuilder(args)
                .directory(appContext.filesDir)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .apply {
                    environment()["PROOT_TMP_DIR"] = File(appContext.cacheDir, "proot-tmp").apply {
                        mkdirs()
                    }.absolutePath
                }
                .start()

            val finished = process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
            }

            val output = runCatching { outputFile.readText() }.getOrDefault("")
            val shortened = if (output.length > 20000) {
                output.take(20000) + "\n… 输出已截断 …"
            } else output

            if (!finished) {
                ShellResult(shortened + "\n[命令在 120 秒后停止]", 124)
            } else {
                ShellResult(shortened.ifBlank { if (process.exitValue() == 0) "" else "(无输出)" }, process.exitValue())
            }
        } catch (error: Exception) {
            ShellResult("PRoot 执行失败：${error.message ?: error::class.java.simpleName}", 127)
        } finally {
            outputFile.delete()
        }
    }

    private suspend fun downloadVerified(
        url: String,
        expectedSha256: String,
        destination: File,
        expectedBytes: Long?,
        onProgress: (Float) -> Unit
    ) {
        val partial = File(destination.parentFile, destination.name + ".part")
        partial.delete()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 90_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "NovaTerm/0.2 Android")
            setRequestProperty("Accept", "*/*")
        }

        try {
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "下载服务器返回 HTTP ${connection.responseCode}"
            }

            val total = connection.contentLengthLong.takeIf { it > 0L } ?: expectedBytes ?: -1L
            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L

            BufferedInputStream(connection.inputStream).use { input ->
                BufferedOutputStream(FileOutputStream(partial)).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        downloaded += count
                        if (total > 0L) {
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }

            if (expectedBytes != null) {
                check(downloaded == expectedBytes) {
                    "文件大小异常：收到 $downloaded bytes，预期 $expectedBytes bytes。"
                }
            }

            val actualSha256 = digest.digest().joinToString("") {
                String.format(Locale.US, "%02x", it.toInt() and 0xff)
            }
            check(actualSha256.equals(expectedSha256, ignoreCase = true)) {
                "SHA-256 校验失败。文件未安装，已拒绝继续。"
            }

            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "无法保存下载文件。" }
            onProgress(1f)
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun installProotZip() {
        val newRuntime = File(baseDir, "runtime.installing")
        if (newRuntime.exists()) newRuntime.deleteRecursively()
        newRuntime.mkdirs()

        ZipInputStream(BufferedInputStream(FileInputStream(prootZip))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = safeArchiveTarget(newRuntime, entry.name)
                if (output == null) {
                    zip.closeEntry()
                    continue
                }
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { stream ->
                        zip.copyTo(stream)
                    }
                    runCatching { Os.chmod(output.absolutePath, 448) }
                    output.setExecutable(true, false)
                }
                zip.closeEntry()
            }
        }

        val binary = File(newRuntime, "proot")
        check(binary.isFile) { "PRoot 压缩包中没有 proot 可执行文件。" }
        runCatching { Os.chmod(binary.absolutePath, 448) }
        binary.setExecutable(true, false)

        if (runtimeDir.exists()) runtimeDir.deleteRecursively()
        check(newRuntime.renameTo(runtimeDir)) { "无法安装 PRoot 运行时。" }
        prootZip.delete()
    }

    private fun extractRootfs(archive: File, destination: File) {
        val canonicalRoot = destination.canonicalFile
        val directoryModes = mutableListOf<Pair<File, Int>>()
        val pendingLinks = mutableListOf<Pair<File, String>>()

        TarArchiveInputStream(
            GzipCompressorInputStream(
                BufferedInputStream(FileInputStream(archive))
            )
        ).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val output = safeArchiveTarget(destination, entry.name) ?: continue

                when {
                    entry.isDirectory -> {
                        output.mkdirs()
                        directoryModes += output to (entry.mode and 0x1ff)
                    }

                    entry.isSymbolicLink -> {
                        output.parentFile?.mkdirs()
                        runCatching {
                            if (output.exists() || java.nio.file.Files.isSymbolicLink(output.toPath())) {
                                output.delete()
                            }
                            java.nio.file.Files.createSymbolicLink(
                                output.toPath(),
                                java.nio.file.Paths.get(entry.linkName)
                            )
                        }
                    }

                    entry.isLink -> {
                        pendingLinks += output to entry.linkName
                    }

                    entry.isFile -> {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { fileOutput ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 8)
                            while (true) {
                                val count = tar.read(buffer)
                                if (count < 0) break
                                fileOutput.write(buffer, 0, count)
                            }
                        }
                        val mode = entry.mode and 0x1ff
                        runCatching { Os.chmod(output.absolutePath, mode) }
                        if ((mode and 0x49) != 0) output.setExecutable(true, false)
                    }

                    else -> {
                        // Device nodes, FIFOs and sockets are provided by Android bind mounts.
                    }
                }
            }
        }

        pendingLinks.forEach { (link, name) ->
            val normalized = name.removePrefix("/").removePrefix("./")
            val target = File(destination, normalized)
            runCatching {
                link.parentFile?.mkdirs()
                if (target.exists() && !link.exists()) {
                    java.nio.file.Files.createLink(link.toPath(), target.toPath())
                }
            }
        }

        directoryModes.sortedByDescending { it.first.absolutePath.length }.forEach { (directory, mode) ->
            runCatching { Os.chmod(directory.absolutePath, mode) }
        }
        check(canonicalRoot.isDirectory) { "Rootfs 目标目录不存在。" }
    }

    private fun safeArchiveTarget(root: File, entryName: String): File? {
        val normalizedName = entryName.replace(92.toChar(), '/').removePrefix("./")
        if (normalizedName.isBlank() || normalizedName.startsWith("/")) return null
        val segments = normalizedName.split('/')
        if (segments.any { it == ".." }) return null
        val rootPath = root.canonicalFile.toPath()
        val targetPath = root.toPath().resolve(normalizedName).normalize()
        if (!targetPath.startsWith(rootPath)) return null
        return targetPath.toFile()
    }

    private fun normalizeGuestPath(path: String): String {
        val parts = mutableListOf<String>()
        path.split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += segment
            }
        }
        return "/" + parts.joinToString("/")
    }

    companion object {
        const val ROOTFS_VERSION = "debian-bookworm-arm64-2026-11-04"

        private const val PROOT_URL =
            "https://github.com/ahmed-alnassif/proot/releases/download/v26.10.08-a179d3e/proot-aarch64.zip"
        private const val PROOT_SHA256 =
            "d621d71fb68bf07accbada37ffc4208cf4a963eed70916ee68f848cbd9555cc3"

        private const val ROOTFS_URL =
            "https://raw.githubusercontent.com/debuerreotype/docker-debian-artifacts/3b26c3995c7fae47d95ba678632a3520e97054f7/bookworm/oci/blobs/sha256/f59e747f549eda321051637e213896378a19033924e1bfa7ae23a6572729c23c"
        private const val ROOTFS_SHA256 =
            "f59e747f549eda321051637e213896378a19033924e1bfa7ae23a6572729c23c"
        private const val ROOTFS_BYTES = 48_412_454L
    }
}

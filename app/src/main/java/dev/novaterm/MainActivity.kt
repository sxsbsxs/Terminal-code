package dev.novaterm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

private val Night = Color(0xFF0A0D12)
private val Panel = Color(0xFF121923)
private val PanelRaised = Color(0xFF1A2430)
private val Mint = Color(0xFF9BEF85)
private val Muted = Color(0xFF91A0B2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NovaTermTheme {
                NovaTermApp()
            }
        }
    }
}

@Composable
private fun NovaTermTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Color(0xFF10200E),
            secondary = Color(0xFF8DC8E8),
            background = Night,
            surface = Panel,
            surfaceVariant = PanelRaised,
            onBackground = Color(0xFFE8EEF5),
            onSurface = Color(0xFFE8EEF5),
            onSurfaceVariant = Muted
        ),
        content = content
    )
}

private enum class AppPage(val title: String) {
    Terminal("终端"),
    Files("文件"),
    Environment("环境"),
    Settings("设置")
}

private data class ConsoleLine(val text: String, val command: Boolean = false, val error: Boolean = false)

@Composable
private fun NovaTermApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val shell = remember { ShellSession(context) }
    val linux = remember { LinuxEnvironmentManager(context) }
    var page by remember { mutableStateOf(AppPage.Terminal) }

    Scaffold(
        containerColor = Night,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = {
            NavigationBar(containerColor = Panel, contentColor = Color.White) {
                AppPage.entries.forEach { destination ->
                    val icon = when (destination) {
                        AppPage.Terminal -> Icons.Default.Terminal
                        AppPage.Files -> Icons.Default.Folder
                        AppPage.Environment -> Icons.Default.Code
                        AppPage.Settings -> Icons.Default.Settings
                    }
                    NavigationBarItem(
                        selected = page == destination,
                        onClick = { page = destination },
                        icon = { Icon(icon, contentDescription = destination.title) },
                        label = { Text(destination.title) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Header(page)
            when (page) {
                AppPage.Terminal -> TerminalPage(shell, linux)
                AppPage.Files -> FilesPage(shell.workspace)
                AppPage.Environment -> EnvironmentPage(linux)
                AppPage.Settings -> SettingsPage(shell.workspace.absolutePath)
            }
        }
    }
}

@Composable
private fun Header(page: AppPage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Night)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Mint, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Terminal, contentDescription = null, tint = Color(0xFF10200E))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("NovaTerm", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(page.title, fontSize = 12.sp, color = Muted)
        }
        AssistChip(
            onClick = {},
            label = { Text("DEV 0.2.0", fontSize = 10.sp) }
        )
    }
}

@Composable
private fun TerminalPage(shell: ShellSession, linux: LinuxEnvironmentManager) {
    var useLinux by remember { mutableStateOf(linux.isInstalled) }
    val lines = remember {
        mutableStateListOf(
            ConsoleLine("NovaTerm 0.2.0  ·  Android + Linux workspace"),
            ConsoleLine("在上方切换 ANDROID 与 DEBIAN 终端环境。"),
            ConsoleLine("输入 help 查看当前环境的可用命令。")
        )
    }
    var input by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val command = input.trim()
        if (command.isEmpty() || running) return
        input = ""
        lines.add(ConsoleLine("$ $command", command = true))
        running = true
        scope.launch {
            val result = if (useLinux) linux.execute(command) else shell.execute(command)
            if (result.clearScreen) {
                lines.clear()
            } else {
                if (result.output.isNotBlank()) {
                    lines.add(ConsoleLine(result.output, error = result.exitCode != 0))
                }
                if (result.exitCode != 0 && result.output.isBlank()) {
                    lines.add(ConsoleLine("Process exited with code ${result.exitCode}", error = true))
                }
            }
            running = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
            .padding(bottom = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(Mint, CircleShape))
            Spacer(Modifier.width(7.dp))
            AssistChip(
                onClick = { useLinux = false },
                label = { Text("ANDROID", fontSize = 10.sp) }
            )
            AssistChip(
                onClick = { useLinux = true },
                enabled = linux.isInstalled,
                label = { Text("DEBIAN", fontSize = 10.sp) }
            )
            Spacer(Modifier.weight(1f))
            Text(if (running) "执行中…" else "就绪", color = Muted, fontSize = 11.sp)
            IconButton(onClick = { lines.clear() }) {
                Text("⌫", color = Muted, fontSize = 20.sp)
            }
        }

        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            color = Color(0xFF070A0E),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF263241))
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(lines) { line ->
                    Text(
                        text = line.text,
                        color = when {
                            line.command -> Mint
                            line.error -> Color(0xFFFF9B91)
                            else -> Color(0xFFD2DBE5)
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
                if (running) {
                    item {
                        Text("…", color = Mint, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = !running,
                placeholder = {
                    Text("输入命令…", color = Muted, fontFamily = FontFamily.Monospace)
                },
                leadingIcon = {
                    Text("›", color = Mint, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                },
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit() })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { submit() },
                enabled = input.isNotBlank() && !running,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Send, contentDescription = "运行")
            }
        }
        Text(
            text = if (useLinux) linux.prompt() else shell.prompt(),
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
            color = Muted,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilesPage(workspace: File) {
    var current by remember { mutableStateOf(workspace) }
    var entries by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(current) {
        entries = current.listFiles()
            ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?.toList()
            ?: emptyList()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(14.dp)) {
                Text("工作区", color = Mint, fontWeight = FontWeight.SemiBold)
                Text(
                    current.absolutePath,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (current != workspace) {
                    TextButton(onClick = { current = current.parentFile ?: workspace }) {
                        Text("↑ 返回上一级")
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("这里还没有文件或文件夹", color = Muted)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(entries) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Panel, RoundedCornerShape(12.dp))
                            .clickable {
                                if (file.isDirectory) current = file else selectedFile = file
                            }
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (file.isDirectory) Icons.Default.Folder else Icons.Default.Code,
                            contentDescription = null,
                            tint = if (file.isDirectory) Mint else Muted
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(file.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (file.isDirectory) "文件夹" else "${file.length()} bytes",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                        Text(if (file.isDirectory) "打开 ›" else "详情", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }

    selectedFile?.let { file ->
        AlertDialog(
            onDismissRequest = { selectedFile = null },
            title = { Text(file.name) },
            text = {
                Text("路径：${file.absolutePath}\n大小：${file.length()} bytes\n\n首版文件浏览器仅管理 NovaTerm 应用工作区。")
            },
            confirmButton = {
                TextButton(onClick = { selectedFile = null }) { Text("关闭") }
            }
        )
    }
}

@Composable
private fun EnvironmentPage(linux: LinuxEnvironmentManager) {
    val progress by linux.progress.collectAsState()
    val scope = rememberCoroutineScope()
    val installed = linux.isInstalled
    val busy = progress.stage == LinuxInstallStage.DOWNLOADING_PROOT ||
        progress.stage == LinuxInstallStage.DOWNLOADING_ROOTFS ||
        progress.stage == LinuxInstallStage.VERIFYING ||
        progress.stage == LinuxInstallStage.EXTRACTING

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("运行环境", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        EnvironmentCard(
            title = "Android Shell",
            description = "使用 /system/bin/sh 执行命令，权限与 NovaTerm 应用一致。",
            status = "可用",
            active = true
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Debian Bookworm", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(
                                when {
                                    installed -> "已安装"
                                    busy -> "安装中"
                                    progress.stage == LinuxInstallStage.FAILED -> "失败"
                                    !linux.isSupported -> "不支持"
                                    else -> "ARM64"
                                },
                                fontSize = 10.sp
                            )
                        }
                    )
                }
                Text(
                    "真正的 Debian ARM64 用户空间，通过 PRoot 在应用内部运行。安装后可使用 apt 安装 Python、Git、SSH 等工具；不会获取 Android Root 权限。",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
                if (busy) {
                    if (progress.stage == LinuxInstallStage.EXTRACTING ||
                        progress.stage == LinuxInstallStage.VERIFYING
                    ) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = progress.fraction.coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(progress.message, color = Mint, fontSize = 11.sp)
                }
                if (progress.stage == LinuxInstallStage.FAILED && progress.error != null) {
                    Text(progress.error, color = Color(0xFFFF9B91), fontSize = 11.sp)
                }
                if (installed) {
                    Text("安装目录：${linux.rootfsPath}", color = Muted, fontSize = 10.sp)
                    Text("进入「终端」，选择 DEBIAN 即可执行 Linux 命令。", color = Mint, fontSize = 11.sp)
                } else {
                    Button(
                        onClick = { scope.launch { linux.install() } },
                        enabled = linux.isSupported && !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (progress.stage == LinuxInstallStage.FAILED) "重试安装 Debian" else "安装 Debian Bookworm")
                    }
                }
            }
        }

        EnvironmentCard(
            title = "开发工具链",
            description = "Debian 安装完成后，可在终端执行 apt update，再安装 python3、git、openssh-client 等软件包。",
            status = if (installed) "可安装" else "等待 Debian",
            active = installed
        )

        Text(
            "首次安装需要联网下载约 48 MB 的 Debian 压缩文件，解压后会占用更多内部存储空间。当前此安装器优先支持 arm64-v8a 设备。",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun EnvironmentCard(
    title: String,
    description: String,
    status: String,
    active: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(status, fontSize = 10.sp) })
            }
            Spacer(Modifier.height(6.dp))
            Text(description, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            if (active) {
                Spacer(Modifier.height(8.dp))
                Text("已启用", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsPage(workspacePath: String) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("应用设置", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        SettingRow("版本", "0.2.0 · Early development")
        SettingRow("Android Shell", "/system/bin/sh")
        SettingRow("Linux 环境", "Debian Bookworm ARM64 · PRoot")
        SettingRow("工作区", workspacePath)
        SettingRow("权限模型", "Android app UID · PRoot 模拟 root")
        Text(
            "Debian 命令运行在应用私有目录中的 rootfs 里；PRoot 的 root 是用户空间模拟，不是 Android 系统 Root。",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun SettingRow(title: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, color = Mint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 13.sp)
        }
    }
}

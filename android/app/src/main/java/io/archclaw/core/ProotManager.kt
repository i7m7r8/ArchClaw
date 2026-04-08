package io.archclaw.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class SetupStep {
    data class DownloadingRootfs(val progress: Int) : SetupStep()
    object ExtractingRootfs : SetupStep()
    object InstallingProot : SetupStep()
    object Bootstrapping : SetupStep()
    object InstallingNodeJS : SetupStep()
    object InstallingPython : SetupStep()
    object InstallingAITools : SetupStep()
    object Complete : SetupStep()
}

data class ProcessResult(val output: String, val exitCode: Int)
data class StorageInfo(val rootfsSizeBytes: Long, val rootfsExists: Boolean, val prootInstalled: Boolean)

class ProotManager(private val context: Context) {

    companion object {
        private const val ROOTFS_URL = "https://eu.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
        private const val PROOT_URL = "https://github.com/proot-me/proot-static-build/releases/download/v5.1.107/proot-aarch64"
    }

    private val rootDir: File = context.filesDir
    val rootfsDir = File(rootDir, "rootfs")
    val sharedDir = File(rootDir, "shared")
    val homeDir = File(rootDir, "home")
    val prootBin = File(rootDir, "proot")

    init {
        rootfsDir.mkdirs()
        sharedDir.mkdirs()
        homeDir.mkdirs()
    }

    fun isReady(): Boolean =
        rootfsDir.exists() &&
        File(rootfsDir, "usr/bin/bash").exists() &&
        prootBin.exists() && prootBin.canExecute()

    fun setupProgress(): Flow<SetupStep> = flow {
        emit(SetupStep.DownloadingRootfs(0))
        if (!File(rootfsDir, "usr/bin/bash").exists()) {
            downloadRootfs { progress -> emit(SetupStep.DownloadingRootfs(progress)) }
            emit(SetupStep.ExtractingRootfs)
            extractRootfs()
        }

        emit(SetupStep.InstallingProot)
        installProotBinary()

        if (!File(rootfsDir, "usr/bin/pacman").exists()) {
            emit(SetupStep.Bootstrapping)
            bootstrapEnvironment()
        }

        if (!File(rootfsDir, "usr/bin/node").exists()) {
            emit(SetupStep.InstallingNodeJS)
            installNodeJS()
        }

        if (!File(rootfsDir, "usr/bin/python").exists()) {
            emit(SetupStep.InstallingPython)
            installPython()
        }

        emit(SetupStep.InstallingAITools)
        installAITools()

        emit(SetupStep.Complete)
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadRootfs(onProgress: suspend (Int) -> Unit) {
        val tarFile = File(context.cacheDir, "archlinux-rootfs.tar.gz")
        if (tarFile.exists()) tarFile.delete()

        val conn = URL(ROOTFS_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        val totalSize = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(tarFile).use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) onProgress((downloaded * 100 / totalSize).toInt())
                }
            }
        }
        conn.disconnect()
    }

    private suspend fun extractRootfs() {
        val tarFile = File(context.cacheDir, "archlinux-rootfs.tar.gz")
        if (!tarFile.exists()) throw Exception("Rootfs tarball not found")

        val process = ProcessBuilder("tar", "xzf", tarFile.absolutePath, "-C", rootfsDir.absolutePath)
            .redirectErrorStream(true).start()
        val exitCode = process.waitFor()
        if (exitCode != 0) throw Exception("Extract failed (exit $exitCode)")
        tarFile.delete()

        File(rootfsDir, "tmp").mkdirs()
        File(rootfsDir, "proc").mkdirs()
        File(rootfsDir, "sys").mkdirs()
        File(rootfsDir, "dev").mkdirs()
        File(rootfsDir, "run").mkdirs()
        homeDir.mkdirs()
    }

    private suspend fun installProotBinary() {
        if (prootBin.exists() && prootBin.canExecute()) return
        URL(PROOT_URL).openStream().use { input ->
            FileOutputStream(prootBin).use { output -> input.copyTo(output) }
        }
        prootBin.setExecutable(true, false)
    }

    private suspend fun bootstrapEnvironment() {
        executeInRootfs("pacman-key --init")
        executeInRootfs("pacman-key --populate archlinuxarm")
        executeInRootfs("pacman -Syu --noconfirm --needed base-devel git curl wget vim nano sudo openssh htop")
        File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
        executeInRootfs("useradd -m -s /bin/bash archclaw || true")
        executeInRootfs("echo 'archclaw ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers || true")
    }

    private suspend fun installNodeJS() {
        executeInRootfs("pacman -S --noconfirm --needed nodejs npm")
        executeInRootfs("npm install -g openclaw")
        executeInRootfs("npm install -g @qwen-code/qwen-code 2>/dev/null || echo 'Qwen Code install skipped'")
    }

    private suspend fun installPython() {
        executeInRootfs("pacman -S --noconfirm --needed python python-pip")
        executeInRootfs("pip install --break-system-packages aider-chat 2>/dev/null || echo 'Aider install skipped'")
    }

    private suspend fun installAITools() {
        val zeroclawBin = File(rootfsDir, "usr/local/bin/zeroclaw")
        if (!zeroclawBin.exists()) {
            try {
                executeInRootfs("curl -fsSL https://github.com/zeroclaw-labs/zeroclaw/releases/latest/download/zeroclaw-aarch64 -o /usr/local/bin/zeroclaw && chmod +x /usr/local/bin/zeroclaw")
            } catch (_: Exception) {
                // ZeroClaw install failed, will retry later
            }
        }
    }

    fun executeInRootfs(command: String): ProcessResult {
        if (!isReady()) return ProcessResult("Error: Environment not ready. Run setup first.", 1)

        val process = ProcessBuilder(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "${sharedDir.absolutePath}:/shared",
            "-b", "${homeDir.absolutePath}:/home/archclaw",
            "-w", "/home/archclaw",
            "-0",
            "--link2symlink",
            "--kill-on-exit",
            "/usr/bin/bash", "-c", command
        ).redirectErrorStream(true).start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        return ProcessResult(output, exitCode)
    }

    fun startInteractiveShell(): Process {
        if (!isReady()) throw Exception("Environment not ready")
        return ProcessBuilder(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "${sharedDir.absolutePath}:/shared",
            "-b", "${homeDir.absolutePath}:/home/archclaw",
            "-w", "/home/archclaw",
            "-0",
            "--link2symlink",
            "--kill-on-exit",
            "/usr/bin/bash", "-l"
        ).redirectInput(ProcessBuilder.Redirect.PIPE)
         .redirectOutput(ProcessBuilder.Redirect.PIPE)
         .redirectError(ProcessBuilder.Redirect.PIPE)
         .start()
    }

    fun launchTool(toolId: String, env: Map<String, String> = emptyMap()): Process {
        if (!isReady()) throw Exception("Environment not ready")

        val command = when (toolId) {
            "qwen" -> "qwen"
            "zeroclaw" -> "zeroclaw start"
            "openclaw" -> "openclaw start"
            "aider" -> "aider"
            else -> throw IllegalArgumentException("Unknown tool: $toolId")
        }

        val args = mutableListOf(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "${sharedDir.absolutePath}:/shared",
            "-b", "${homeDir.absolutePath}:/home/archclaw",
            "-w", "/home/archclaw",
            "-0", "--link2symlink", "--kill-on-exit",
            "env"
        )
        env.forEach { (k, v) -> args.add("$k=$v") }
        args.addAll(listOf("/usr/bin/bash", "-c", command))

        return ProcessBuilder(args)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    }

    fun getStorageUsage(): StorageInfo {
        val size = if (rootfsDir.exists()) rootfsDir.walkTopDown().map { it.length() }.sum() else 0L
        return StorageInfo(size, File(rootfsDir, "usr/bin/bash").exists(), prootBin.exists() && prootBin.canExecute())
    }

    fun wipe() {
        rootfsDir.deleteRecursively()
        sharedDir.deleteRecursively()
        homeDir.deleteRecursively()
        prootBin.delete()
    }
}

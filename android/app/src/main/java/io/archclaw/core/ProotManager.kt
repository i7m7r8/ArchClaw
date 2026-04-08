package io.archclaw.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        private const val TAG = "ProotManager"
        
        // Multiple mirrors - try each until one works
        private val ROOTFS_MIRRORS = listOf(
            "https://mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            "https://eu.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            "https://america.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            "https://asia.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
        )
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
        
        // Try each mirror until one works
        var downloadSuccess = false
        for (mirror in ROOTFS_MIRRORS) {
            try {
                Log.d(TAG, "Trying mirror: $mirror")
                downloadRootfs(mirror) { progress ->
                    emit(SetupStep.DownloadingRootfs(progress))
                }
                emit(SetupStep.ExtractingRootfs)
                extractRootfs()
                downloadSuccess = true
                break
            } catch (e: Exception) {
                Log.w(TAG, "Mirror failed: $mirror - ${e.message}")
                continue
            }
        }
        if (!downloadSuccess) {
            throw Exception("All mirrors failed. Check network connection.")
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

    private suspend fun downloadRootfs(url: String, onProgress: suspend (Int) -> Unit) {
        val tarFile = File(context.cacheDir, "archlinux-rootfs.tar.gz")
        if (tarFile.exists()) tarFile.delete()

        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        val totalSize = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(tarFile).use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                var lastProgress = 0
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) {
                        val progress = (downloaded * 100 / totalSize).toInt()
                        if (progress != lastProgress) {
                            onProgress(progress)
                            lastProgress = progress
                        }
                    }
                }
            }
        }
        conn.disconnect()
        
        if (!tarFile.exists() || tarFile.length() == 0L) {
            throw Exception("Downloaded file is empty")
        }
    }

    private suspend fun extractRootfs() {
        val tarFile = File(context.cacheDir, "archlinux-rootfs.tar.gz")
        if (!tarFile.exists()) throw Exception("Rootfs tarball not found")

        Log.d(TAG, "Extracting rootfs to ${rootfsDir.absolutePath}")
        val process = ProcessBuilder("tar", "xzf", tarFile.absolutePath, "-C", rootfsDir.absolutePath)
            .redirectErrorStream(true).start()
        val exitCode = process.waitFor()
        if (exitCode != 0) throw Exception("Extract failed (exit $exitCode)")
        tarFile.delete()

        // Create required dirs
        listOf("tmp", "proc", "sys", "dev", "dev/pts", "run").forEach {
            File(rootfsDir, it).mkdirs()
        }
        homeDir.mkdirs()
        Log.d(TAG, "Rootfs extracted successfully")
    }

    private suspend fun installProotBinary() {
        if (prootBin.exists() && prootBin.canExecute()) return
        Log.d(TAG, "Downloading proot")
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
        executeInRootfs("npm install -g @qwen-code/qwen-code 2>/dev/null || true")
    }

    private suspend fun installPython() {
        executeInRootfs("pacman -S --noconfirm --needed python python-pip")
        executeInRootfs("pip install --break-system-packages aider-chat 2>/dev/null || true")
    }

    private suspend fun installAITools() {
        val zeroclawBin = File(rootfsDir, "usr/local/bin/zeroclaw")
        if (!zeroclawBin.exists()) {
            try {
                executeInRootfs("curl -fsSL https://github.com/zeroclaw-labs/zeroclaw/releases/latest/download/zeroclaw-aarch64 -o /usr/local/bin/zeroclaw && chmod +x /usr/local/bin/zeroclaw")
            } catch (_: Exception) {}
        }
    }

    fun executeInRootfs(command: String): ProcessResult {
        if (!isReady()) return ProcessResult("Error: Environment not ready.", 1)

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

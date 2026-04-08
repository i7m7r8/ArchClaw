package io.archclaw.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class SetupStep {
    object CheckingEnvironment : SetupStep()
    object DownloadingProot : SetupStep()
    object DownloadingRootfs : SetupStep()
    object ExtractingRootfs : SetupStep()
    object Bootstrapping : SetupStep()
    object InstallingNodeJS : SetupStep()
    object InstallingPython : SetupStep()
    object InstallingAITools : SetupStep()
    object Complete : SetupStep()
}

data class ProcessResult(val output: String, val exitCode: Int)
data class StorageInfo(val sizeBytes: Long, val isReady: Boolean)

/**
 * Production-grade ProotManager - fully self-contained, no proot-distro needed.
 * 
 * Bundles/downloads everything:
 * 1. proot static binary (from GitHub releases)
 * 2. Arch Linux ARM64 rootfs (from official mirrors)
 * 3. Bootstraps pacman, installs Node.js, Python, AI tools
 * 
 * Works as a standalone APK - no Termux required.
 */
class ProotManager(private val context: Context) {

    companion object {
        private const val TAG = "ProotManager"
        
        // Proot static binary
        private const val PROOT_URL = "https://github.com/proot-me/proot-static-build/releases/download/v5.1.107/proot-aarch64"
        
        // Arch Linux ARM rootfs mirrors (try each until one works)
        private val ROOTFS_MIRRORS = listOf(
            "https://mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            "https://eu.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz", 
            "https://america.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
        )
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

    val isReady: Boolean
        get() = rootfsDir.exists() &&
                File(rootfsDir, "usr/bin/bash").exists() &&
                prootBin.exists() && prootBin.canExecute()

    fun setupProgress(): Flow<SetupStep> = flow {
        emit(SetupStep.CheckingEnvironment)
        
        // Install proot binary if missing
        emit(SetupStep.DownloadingProot)
        installProotBinary()

        // Download rootfs if not extracted
        if (!File(rootfsDir, "usr/bin/bash").exists()) {
            emit(SetupStep.DownloadingRootfs)
            var success = false
            for (mirror in ROOTFS_MIRRORS) {
                try {
                    Log.d(TAG, "Trying mirror: $mirror")
                    downloadRootfs(mirror)
                    emit(SetupStep.ExtractingRootfs)
                    extractRootfs()
                    success = true
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Mirror failed: ${e.message}")
                    continue
                }
            }
            if (!success) throw Exception("All rootfs mirrors failed. Check network.")
        }

        // Bootstrap if pacman not present
        if (!File(rootfsDir, "usr/bin/pacman").exists()) {
            emit(SetupStep.Bootstrapping)
            bootstrapEnvironment()
        }

        // Install Node.js
        if (!File(rootfsDir, "usr/bin/node").exists()) {
            emit(SetupStep.InstallingNodeJS)
            execInArch("pacman -S --noconfirm --needed nodejs npm")
            execInArch("npm install -g openclaw")
        }

        // Install Python
        if (!File(rootfsDir, "usr/bin/python").exists()) {
            emit(SetupStep.InstallingPython)
            execInArch("pacman -S --noconfirm --needed python python-pip")
        }

        // Install AI tools
        emit(SetupStep.InstallingAITools)
        execInArch("npm install -g @qwen-code/qwen-code 2>/dev/null || true")
        execInArch("pip install --break-system-packages aider-chat 2>/dev/null || true")

        emit(SetupStep.Complete)
    }.flowOn(Dispatchers.IO)

    private fun installProotBinary() {
        if (prootBin.exists() && prootBin.canExecute()) return
        
        Log.d(TAG, "Downloading proot from $PROOT_URL")
        val conn = URL(PROOT_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.inputStream.use { input ->
            FileOutputStream(prootBin).use { output -> input.copyTo(output) }
        }
        conn.disconnect()
        prootBin.setExecutable(true, false)
        Log.d(TAG, "Proot installed at ${prootBin.absolutePath}")
    }

    private fun downloadRootfs(url: String) {
        val tarFile = File(context.cacheDir, "archlinux-rootfs.tar.gz")
        if (tarFile.exists()) tarFile.delete()

        Log.d(TAG, "Downloading rootfs from $url")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 60000
        conn.readTimeout = 60000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "ArchClaw/1.0")

        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw Exception("HTTP ${conn.responseCode} from $url")
        }

        val totalSize = conn.contentLengthLong
        var downloaded = 0L

        conn.inputStream.use { input ->
            FileOutputStream(tarFile).use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                }
            }
        }
        conn.disconnect()

        if (!tarFile.exists() || tarFile.length() == 0L) {
            throw Exception("Downloaded file is empty")
        }
        Log.d(TAG, "Rootfs downloaded: ${tarFile.length() / 1024 / 1024}MB")
    }

    private fun extractRootfs() {
        val tarFile = File(context.cacheDir, "archlinux-rootfs.tar.gz")
        if (!tarFile.exists()) throw Exception("Rootfs tarball not found")

        Log.d(TAG, "Extracting rootfs...")
        val process = ProcessBuilder("tar", "xzf", tarFile.absolutePath, "-C", rootfsDir.absolutePath)
            .redirectErrorStream(true).start()
        val exitCode = process.waitFor()
        if (exitCode != 0) throw Exception("Extract failed (exit $exitCode)")
        tarFile.delete()

        // Create required directories
        listOf("tmp", "proc", "sys", "dev", "dev/pts", "run").forEach {
            File(rootfsDir, it).mkdirs()
        }
        homeDir.mkdirs()
        
        // Configure DNS
        File(rootfsDir, "etc/resolv.conf").writeText("nameserver 8.8.8.8\nnameserver 8.8.4.4\n")
        
        Log.d(TAG, "Rootfs extracted to ${rootfsDir.absolutePath}")
    }

    private fun bootstrapEnvironment() {
        Log.d(TAG, "Bootstrapping Arch Linux")
        execInArch("pacman-key --init")
        execInArch("pacman-key --populate archlinuxarm")
        execInArch("pacman -Syu --noconfirm --needed base-devel git curl wget vim nano sudo openssh htop")
        execInArch("useradd -m -s /bin/bash archclaw || true")
        execInArch("echo 'archclaw ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers || true")
    }

    fun execInArch(command: String): ProcessResult {
        if (!isReady) return ProcessResult("Error: Environment not ready. Run setup first.", 1)

        return executeCommand(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "${sharedDir.absolutePath}:/shared",
            "-b", "${homeDir.absolutePath}:/home/archclaw",
            "-w", "/home/archclaw",
            "-0",
            "--link2symlink",
            "--kill-on-exit",
            "/usr/bin/bash", "-c", command
        )
    }

    fun executeInRootfs(command: String): ProcessResult {
        return execInArch(command)
    }

    fun startInteractiveShell(): Process {
        if (!isReady) throw Exception("Environment not ready")
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
        if (!isReady) throw Exception("Environment not ready")

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

    private fun executeCommand(vararg command: String): ProcessResult {
        try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            return ProcessResult(output, exitCode)
        } catch (e: Exception) {
            return ProcessResult("Error: ${e.message}", 1)
        }
    }

    fun getStorageUsage(): StorageInfo {
        val size = if (rootfsDir.exists()) rootfsDir.walkTopDown().map { it.length() }.sum() else 0L
        return StorageInfo(size, isReady)
    }

    fun wipe() {
        rootfsDir.deleteRecursively()
        sharedDir.deleteRecursively()
        homeDir.deleteRecursively()
        prootBin.delete()
    }
}

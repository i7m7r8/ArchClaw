package io.archclaw.core

import android.content.Context
import android.os.Build
import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Manages the Arch Linux proot environment
 * Downloads rootfs, extracts it, runs commands via proot
 */
class ProotManager(private val context: Context) {

    companion object {
        // Arch Linux ARM64 rootfs mirror
        private const val ROOTFS_URL = "https://eu.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
        private const val ROOTFS_CHECKSUM = "sha256:CHECKSUM_HERE" // Update with real checksum
        
        // Where everything lives
        private const val ROOTFS_DIR = "rootfs"
        private const val PROOT_BIN = "proot"
        private const val SHARED_DIR = "shared"
        private const val HOME_DIR = "home"
    }

    // Directory structure
    val rootDir: File = context.filesDir
    val rootfsDir: File = File(rootDir, ROOTFS_DIR)
    val sharedDir: File = File(rootDir, SHARED_DIR)
    val homeDir: File = File(rootDir, HOME_DIR)
    val prootBin: File = File(rootDir, PROOT_BIN)

    /**
     * Check if environment is ready
     */
    fun isReady(): Boolean {
        return rootfsDir.exists() &&
               File(rootfsDir, "usr/bin/bash").exists() &&
               prootBin.exists()
    }

    /**
     * Full setup flow with progress updates
     */
    fun setupProgress(): Flow<SetupStep> = flow {
        emit(SetupStep.DownloadingRootfs(0))
        downloadRootfs { progress ->
            emit(SetupStep.DownloadingRootfs(progress))
        }

        emit(SetupStep.ExtractingRootfs)
        extractRootfs()

        emit(SetupStep.InstallingProot)
        installProotBinary()

        emit(SetupStep.Bootstrapping)
        bootstrapEnvironment()

        emit(SetupStep.InstallingNodeJS)
        installNodeJS()

        emit(SetupStep.InstallingPython)
        installPython()

        emit(SetupStep.InstallingAITools)
        installAITools()

        emit(SetupStep.Complete)
    }.flowOn(Dispatchers.IO)

    /**
     * Download Arch Linux rootfs
     */
    private suspend fun downloadRootfs(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (File(rootfsDir, "usr/bin/bash").exists()) {
            return@withContext // Already downloaded
        }

        rootfsDir.mkdirs()
        val tarFile = File(context.cacheDir, "archlinux-rootfs.tar.gz")

        val connection = URL(ROOTFS_URL).openConnection()
        val totalSize = connection.contentLengthLong
        var downloaded = 0L

        connection.getInputStream().use { input ->
            FileOutputStream(tarFile).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) {
                        onProgress((downloaded * 100 / totalSize).toInt())
                    }
                }
            }
        }

        // Verify checksum (optional but recommended)
        // val actualChecksum = tarFile.sha256()
        // if (actualChecksum != ROOTFS_CHECKSUM) throw Exception("Checksum mismatch")
    }

    /**
     * Extract rootfs tarball
     */
    private suspend fun extractRootfs() = withContext(Dispatchers.IO) {
        val tarFile = File(context.cacheDir, "archlinux-rootfs.tar.gz")
        if (!tarFile.exists()) return@withContext

        // Extract using system tar command
        val process = ProcessBuilder("tar", "xzf", tarFile.absolutePath, "-C", rootfsDir.absolutePath)
            .redirectErrorStream(true)
            .start()
        
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw Exception("Failed to extract rootfs: exit code $exitCode")
        }

        // Cleanup tar file
        tarFile.delete()

        // Create necessary directories
        File(rootfsDir, "tmp").mkdirs()
        File(rootfsDir, "proc").mkdirs()
        File(rootfsDir, "sys").mkdirs()
        File(rootfsDir, "dev").mkdirs()
        sharedDir.mkdirs()
        homeDir.mkdirs()
    }

    /**
     * Install proot binary
     */
    private suspend fun installProotBinary() = withContext(Dispatchers.IO) {
        // Try to copy from assets first
        val assetProot = try {
            context.assets.open(PROOT_BIN)
        } catch (e: Exception) {
            null
        }

        if (assetProot != null) {
            assetProot.use { input ->
                FileOutputStream(prootBin).use { output ->
                    input.copyTo(output)
                }
            }
            prootBin.setExecutable(true)
        } else {
            // Download proot from GitHub releases
            val prootUrl = "https://github.com/proot-me/proot-static-build/releases/download/v5.1.107/proot-aarch64"
            val connection = URL(prootUrl).openConnection()
            connection.getInputStream().use { input ->
                FileOutputStream(prootBin).use { output ->
                    input.copyTo(output)
                }
            }
            prootBin.setExecutable(true)
        }
    }

    /**
     * Bootstrap Arch Linux environment
     */
    private suspend fun bootstrapEnvironment() = withContext(Dispatchers.IO) {
        // Initialize pacman keyring
        executeInRootfs("pacman-key --init")
        executeInRootfs("pacman-key --populate archlinuxarm")
        
        // Update system
        executeInRootfs("pacman -Syu --noconfirm")
        
        // Install base packages
        executeInRootfs("pacman -S --noconfirm base-devel git curl wget vim nano sudo")
        
        // Configure sudo for archclaw user
        executeInRootfs("""
            echo "archclaw ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
        """.trimIndent())
    }

    /**
     * Install Node.js
     */
    private suspend fun installNodeJS() = withContext(Dispatchers.IO) {
        executeInRootfs("pacman -S --noconfirm nodejs npm")
        
        // Install global npm packages (AI tools)
        executeInRootfs("npm install -g @qwen-code/qwen-code")
        executeInRootfs("npm install -g openclaw")
    }

    /**
     * Install Python
     */
    private suspend fun installPython() = withContext(Dispatchers.IO) {
        executeInRootfs("pacman -S --noconfirm python python-pip")
        
        // Install Python-based AI tools
        executeInRootfs("pip install --break-system-packages aider-chat")
    }

    /**
     * Install AI tools
     */
    private suspend fun installAITools() = withContext(Dispatchers.IO) {
        // Install ZeroClaw (Rust binary - download from releases)
        val zeroclawUrl = "https://github.com/zeroclaw-labs/zeroclaw/releases/latest/download/zeroclaw-aarch64"
        val zeroclawBin = File(rootfsDir, "usr/local/bin/zeroclaw")
        URL(zeroclawUrl).openStream().use { input ->
            FileOutputStream(zeroclawBin).use { output ->
                input.copyTo(output)
            }
        }
        zeroclawBin.setExecutable(true)
    }

    /**
     * Execute command in rootfs
     */
    fun executeInRootfs(command: String): ProcessResult {
        val prootArgs = listOf(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "${sharedDir.absolutePath}:/shared",
            "-b", "${homeDir.absolutePath}:/home/archclaw",
            "-w", "/home/archclaw",
            "-0", // Run as root
            "--link2symlink",
            "/usr/bin/bash", "-c", command
        )

        val process = ProcessBuilder(prootArgs)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return ProcessResult(output, exitCode)
    }

    /**
     * Start interactive shell
     */
    fun startInteractiveShell(): Process {
        val prootArgs = listOf(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "${sharedDir.absolutePath}:/shared",
            "-b", "${homeDir.absolutePath}:/home/archclaw",
            "-w", "/home/archclaw",
            "-0",
            "--link2symlink",
            "/usr/bin/bash", "-l"
        )

        return ProcessBuilder(prootArgs)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    }

    /**
     * Launch AI tool
     */
    fun launchTool(toolId: String, env: Map<String, String> = Map()): Process {
        val command = when (toolId) {
            "qwen" -> "qwen"
            "zeroclaw" -> "zeroclaw start"
            "openclaw" -> "openclaw start"
            "aider" -> "aider"
            else -> throw IllegalArgumentException("Unknown tool: $toolId")
        }

        val prootArgs = mutableListOf(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-b", "${sharedDir.absolutePath}:/shared",
            "-b", "${homeDir.absolutePath}:/home/archclaw",
            "-w", "/home/archclaw",
            "-0",
            "--link2symlink",
            "env"
        )

        // Add environment variables
        env.forEach { (key, value) ->
            prootArgs.add("$key=$value")
        }

        prootArgs.add("/usr/bin/bash")
        prootArgs.add("-c")
        prootArgs.add(command)

        return ProcessBuilder(prootArgs)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    }

    /**
     * Get storage usage
     */
    fun getStorageUsage(): StorageInfo {
        val rootfsSize = rootfsDir.walkTopDown().map { it.length() }.sum()
        return StorageInfo(
            rootfsSize,
            rootfsDir.exists(),
            prootBin.exists()
        )
    }

    /**
     * Wipe environment (for debugging/reinstall)
     */
    fun wipe() {
        rootfsDir.deleteRecursively()
        sharedDir.deleteRecursively()
        homeDir.deleteRecursively()
        prootBin.delete()
    }
}

/**
 * Setup step for progress reporting
 */
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

/**
 * Process execution result
 */
data class ProcessResult(
    val output: String,
    val exitCode: Int
)

/**
 * Storage information
 */
data class StorageInfo(
    val rootfsSizeBytes: Long,
    val rootfsExists: Boolean,
    val prootInstalled: Boolean
)

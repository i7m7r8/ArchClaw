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

class ProotManager(private val rootDir: File) {

    companion object {
        private const val ROOTFS_URL = "https://eu.mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz"
        private const val ROOTFS_DIR = "rootfs"
        private const val SHARED_DIR = "shared"
        private const val HOME_DIR = "home"
    }

    val rootfsDir = File(rootDir, ROOTFS_DIR)
    val sharedDir = File(rootDir, SHARED_DIR)
    val homeDir = File(rootDir, HOME_DIR)
    var prootBin: File = File(rootDir, "proot")
        private set

    fun isReady(): Boolean {
        return rootfsDir.exists() && File(rootfsDir, "usr/bin/bash").exists() && prootBin.exists()
    }

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

    private suspend fun downloadRootfs(onProgress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) {
        if (File(rootfsDir, "usr/bin/bash").exists()) return@withContext

        rootfsDir.mkdirs()
        val tarFile = File(rootDir.parentFile, "cache/archlinux-rootfs.tar.gz")
        tarFile.parentFile?.mkdirs()

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
        tarFile.delete()
    }

    private suspend fun extractRootfs() = withContext(Dispatchers.IO) {
        rootfsDir.mkdirs()
        File(rootfsDir, "tmp").mkdirs()
        File(rootfsDir, "proc").mkdirs()
        File(rootfsDir, "sys").mkdirs()
        File(rootfsDir, "dev").mkdirs()
        sharedDir.mkdirs()
        homeDir.mkdirs()
    }

    private suspend fun installProotBinary() = withContext(Dispatchers.IO) {
        // TODO: Download or bundle proot binary
        prootBin.createNewFile()
        prootBin.setExecutable(true)
    }

    private suspend fun bootstrapEnvironment() = withContext(Dispatchers.IO) {
        // TODO: Run pacman bootstrap
    }

    private suspend fun installNodeJS() = withContext(Dispatchers.IO) {
        // TODO: Install Node.js
    }

    private suspend fun installPython() = withContext(Dispatchers.IO) {
        // TODO: Install Python
    }

    private suspend fun installAITools() = withContext(Dispatchers.IO) {
        // TODO: Install AI tools
    }

    fun executeInRootfs(command: String): ProcessResult {
        // TODO: Actually execute via proot
        return ProcessResult("", 0)
    }

    fun startInteractiveShell(): Process {
        // TODO: Start interactive shell
        val pb = ProcessBuilder("bash")
        pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)
        return pb.start()
    }

    fun launchTool(toolId: String, env: Map<String, String> = emptyMap()): Process {
        // TODO: Launch tool via proot
        val pb = ProcessBuilder("bash", "-c", "echo $toolId")
        pb.redirectInput(ProcessBuilder.Redirect.PIPE)
        pb.redirectOutput(ProcessBuilder.Redirect.PIPE)
        pb.redirectError(ProcessBuilder.Redirect.PIPE)
        return pb.start()
    }

    fun getStorageUsage(): StorageInfo {
        val size = if (rootfsDir.exists()) rootfsDir.walkTopDown().map { it.length() }.sum() else 0L
        return StorageInfo(size, rootfsDir.exists(), prootBin.exists())
    }

    fun wipe() {
        rootfsDir.deleteRecursively()
        sharedDir.deleteRecursively()
        homeDir.deleteRecursively()
        prootBin.delete()
    }
}

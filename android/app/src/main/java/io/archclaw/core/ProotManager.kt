package io.archclaw.core

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

sealed class SetupStep {
    object CheckingProotDistro : SetupStep()
    object InstallingArchLinux : SetupStep()
    object InstallingDependencies : SetupStep()
    object InstallingNodeJS : SetupStep()
    object InstallingPython : SetupStep()
    object InstallingAITools : SetupStep()
    object Complete : SetupStep()
}

data class ProcessResult(val output: String, val exitCode: Int)
data class StorageInfo(val sizeBytes: Long, val isReady: Boolean)

/**
 * Production-grade ProotManager using proot-distro
 * 
 * Instead of manually downloading rootfs, we use proot-distro
 * which is the standard Android way to manage Linux distros.
 * 
 * proot-distro is already available on Termux and handles:
 * - Rootfs download with retry/resume
 * - Extraction
 * - DNS configuration
 * - Package manager setup
 */
class ProotManager(private val context: Context) {

    companion object {
        private const val TAG = "ProotManager"
    }

    val isReady: Boolean
        get() = runCatching {
            val result = execPd("list")
            result.output.contains("archlinux", ignoreCase = true) &&
            result.output.contains("installed", ignoreCase = true)
        }.getOrDefault(false)

    fun setupProgress(): Flow<SetupStep> = flow {
        emit(SetupStep.CheckingProotDistro)
        checkProotDistroAvailable()

        emit(SetupStep.InstallingArchLinux)
        if (!isReady) {
            execPd("install archlinux")
        }

        emit(SetupStep.InstallingDependencies)
        execInArch("pacman -Syu --noconfirm --needed base-devel git curl wget vim nano sudo openssh htop")

        emit(SetupStep.InstallingNodeJS)
        execInArch("pacman -S --noconfirm --needed nodejs npm")
        execInArch("npm install -g openclaw")

        emit(SetupStep.InstallingPython)
        execInArch("pacman -S --noconfirm --needed python python-pip")

        emit(SetupStep.InstallingAITools)
        execInArch("npm install -g @qwen-code/qwen-code 2>/dev/null || true")
        execInArch("pip install --break-system-packages aider-chat 2>/dev/null || true")

        emit(SetupStep.Complete)
    }.flowOn(Dispatchers.IO)

    private fun checkProotDistroAvailable() {
        val result = execPd("list")
        if (result.exitCode != 0) {
            throw Exception("proot-distro not found. Please install it: pkg install proot-distro")
        }
    }

    private fun execPd(args: String): ProcessResult {
        return executeCommand("proot-distro $args")
    }

    fun execInArch(command: String): ProcessResult {
        return executeCommand("proot-distro login archlinux -- bash -c \"$command\"")
    }

    fun executeInRootfs(command: String): ProcessResult {
        return executeCommand("proot-distro login archlinux -- bash -c \"$command\"")
    }

    fun startInteractiveShell(): Process {
        return ProcessBuilder("proot-distro", "login", "archlinux")
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    }

    fun launchTool(toolId: String, env: Map<String, String> = emptyMap()): Process {
        val command = when (toolId) {
            "qwen" -> "qwen"
            "zeroclaw" -> "zeroclaw start"
            "openclaw" -> "openclaw start"
            "aider" -> "aider"
            else -> throw IllegalArgumentException("Unknown tool: $toolId")
        }

        val envStr = env.map { (k, v) -> "$k=$v" }.joinToString(" ")
        val fullCommand = "proot-distro login archlinux -- env $envStr bash -c \"$command\""
        
        return ProcessBuilder("bash", "-c", fullCommand)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()
    }

    private fun executeCommand(command: String): ProcessResult {
        try {
            val process = ProcessBuilder("bash", "-c", command)
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
        val result = execPd("show archlinux")
        val ready = result.exitCode == 0
        return StorageInfo(0, ready)
    }

    fun wipe() {
        execPd("remove archlinux")
    }
}

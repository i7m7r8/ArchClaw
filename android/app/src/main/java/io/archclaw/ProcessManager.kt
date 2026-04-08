package io.archclaw

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Manages proot process execution, matching proot-distro as closely as possible.
 * Two command modes:
 *   - Install mode: matches proot-distro's run_proot_cmd()
 *   - Gateway mode: matches proot-distro's command_login()
 */
class ProcessManager(
    private val filesDir: String,
    private val nativeLibDir: String
) {
    private val rootfsDir get() = "$filesDir/rootfs/archlinux"
    private val tmpDir get() = "$filesDir/tmp"
    private val homeDir get() = "$filesDir/home"
    private val configDir get() = "$filesDir/config"
    private val libDir get() = "$filesDir/lib"

    companion object {
        const val FAKE_KERNEL_RELEASE = "6.17.0-ArchClaw"
        const val FAKE_KERNEL_VERSION =
            "#1 SMP PREEMPT_DYNAMIC Wed, 08 Apr 2026 00:00:00 +0000"
    }

    fun getProotPath(): String = "$nativeLibDir/libproot.so"

    private fun prootEnv(): Map<String, String> = mapOf(
        "PROOT_TMP_DIR" to tmpDir,
        "PROOT_LOADER" to "$nativeLibDir/libprootloader.so",
        "PROOT_LOADER_32" to "$nativeLibDir/libprootloader32.so",
        "LD_LIBRARY_PATH" to "$libDir:$nativeLibDir",
    )

    private fun ensureResolvConf() {
        val content = "nameserver 8.8.8.8\nnameserver 8.8.4.4\n"
        try {
            val resolvFile = java.io.File(configDir, "resolv.conf")
            if (!resolvFile.exists() || resolvFile.length() == 0L) {
                resolvFile.parentFile?.mkdirs()
                resolvFile.writeText(content)
            }
        } catch (_: Exception) {}
        try {
            val rootfsResolv = java.io.File(rootfsDir, "etc/resolv.conf")
            if (!rootfsResolv.exists() || rootfsResolv.length() == 0L) {
                rootfsResolv.parentFile?.mkdirs()
                rootfsResolv.writeText(content)
            }
        } catch (_: Exception) {}
    }

    private fun commonProotFlags(): List<String> {
        ensureResolvConf()

        val prootPath = getProotPath()
        val procFakes = "$configDir/proc_fakes"
        val sysFakes = "$configDir/sys_fakes"

        return listOf(
            prootPath,
            "--link2symlink",
            "-L",
            "--kill-on-exit",
            "--rootfs=$rootfsDir",
            "--cwd=/root",
            "--bind=/dev",
            "--bind=/dev/urandom:/dev/random",
            "--bind=/proc",
            "--bind=/proc/self/fd:/dev/fd",
            "--bind=/proc/self/fd/0:/dev/stdin",
            "--bind=/proc/self/fd/1:/dev/stdout",
            "--bind=/proc/self/fd/2:/dev/stderr",
            "--bind=/sys",
            "--bind=$procFakes/loadavg:/proc/loadavg",
            "--bind=$procFakes/stat:/proc/stat",
            "--bind=$procFakes/uptime:/proc/uptime",
            "--bind=$procFakes/version:/proc/version",
            "--bind=$procFakes/vmstat:/proc/vmstat",
            "--bind=$procFakes/cap_last_cap:/proc/sys/kernel/cap_last_cap",
            "--bind=$procFakes/max_user_watches:/proc/sys/fs/inotify/max_user_watches",
            "--bind=$procFakes/fips_enabled:/proc/sys/crypto/fips_enabled",
            "--bind=$rootfsDir/tmp:/dev/shm",
            "--bind=$sysFakes/empty:/sys/fs/selinux",
            "--bind=$configDir/resolv.conf:/etc/resolv.conf",
            "--bind=$homeDir:/root/home",
        )
    }

    fun buildInstallCommand(command: String): List<String> {
        val flags = commonProotFlags().toMutableList()
        flags.add(1, "--root-id")
        flags.add(2, "--kernel-release=$FAKE_KERNEL_RELEASE")

        flags.addAll(listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "TMPDIR=/tmp",
            "DEBIAN_FRONTEND=noninteractive",
            "npm_config_cache=/tmp/npm-cache",
            "/bin/bash", "-c",
            command,
        ))

        return flags
    }

    fun buildGatewayCommand(command: String): List<String> {
        val flags = commonProotFlags().toMutableList()
        val arch = ArchUtils.getArch()
        val machine = when (arch) {
            "arm" -> "armv7l"
            else -> arch
        }

        flags.add(1, "--change-id=0:0")
        flags.add(2, "--sysvipc")
        val kernelRelease = "\\Linux\\localhost\\$FAKE_KERNEL_RELEASE" +
            "\\$FAKE_KERNEL_VERSION\\$machine\\localdomain\\-1\\"
        flags.add(3, "--kernel-release=$kernelRelease")

        val nodeOptions = "--require /root/.openclaw/bionic-bypass.js"

        flags.addAll(listOf(
            "/usr/bin/env", "-i",
            "HOME=/root",
            "USER=root",
            "LANG=C.UTF-8",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "TERM=xterm-256color",
            "TMPDIR=/tmp",
            "NODE_OPTIONS=$nodeOptions",
            "CHOKIDAR_USEPOLLING=true",
            "NODE_EXTRA_CA_CERTS=/etc/ssl/certs/ca-certificates.crt",
            "UV_USE_IO_URING=0",
            "/bin/bash", "-c",
            command,
        ))

        return flags
    }

    fun runInProotSync(command: String, timeoutSeconds: Long = 900): String {
        val cmd = buildInstallCommand(command)
        val env = prootEnv()

        val pb = ProcessBuilder(cmd)
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.redirectErrorStream(true)

        val process = pb.start()
        val output = StringBuilder()
        val errorLines = StringBuilder()
        val reader = BufferedReader(InputStreamReader(process.inputStream))

        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val l = line ?: continue
            if (l.contains("proot warning") || l.contains("can't sanitize")) continue
            output.appendLine(l)
            if (!l.startsWith(":: ") && !l.startsWith(":::") &&
                !l.startsWith("warning:") && l.trim().isNotEmpty()) {
                errorLines.appendLine(l)
            }
        }

        val exited = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            throw RuntimeException("Command timed out after ${timeoutSeconds}s")
        }

        val exitCode = process.exitValue()
        if (exitCode != 0) {
            val errorOutput = errorLines.toString().takeLast(3000).ifEmpty {
                output.toString().takeLast(3000)
            }
            throw RuntimeException("Command failed (exit code $exitCode): $errorOutput")
        }

        return output.toString()
    }

    fun startProotProcess(command: String): Process {
        val cmd = buildGatewayCommand(command)
        val env = prootEnv()

        val pb = ProcessBuilder(cmd)
        pb.environment().clear()
        pb.environment().putAll(env)
        pb.redirectErrorStream(false)

        return pb.start()
    }
}

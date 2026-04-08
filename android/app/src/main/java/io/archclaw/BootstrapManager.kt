package io.archclaw

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

class BootstrapManager(
    private val context: Context,
    private val filesDir: String,
    private val nativeLibDir: String
) {
    private val rootfsDir get() = "$filesDir/rootfs/archlinux"
    private val tmpDir get() = "$filesDir/tmp"
    private val homeDir get() = "$filesDir/home"
    private val configDir get() = "$filesDir/config"
    private val libDir get() = "$filesDir/lib"

    fun setupDirectories() {
        listOf(rootfsDir, tmpDir, homeDir, configDir, "$homeDir/.openclaw", libDir).forEach {
            File(it).mkdirs()
        }
        setupLibtalloc()
        setupFakeSysdata()
    }

    private fun setupLibtalloc() {
        val source = File("$nativeLibDir/libtalloc.so")
        val target = File("$libDir/libtalloc.so.2")
        if (source.exists() && !target.exists()) {
            source.copyTo(target)
            target.setExecutable(true)
        }
    }

    fun isBootstrapComplete(): Boolean {
        val rootfs = File(rootfsDir)
        val binBash = File("$rootfsDir/usr/bin/bash")
        val bypass = File("$rootfsDir/root/.openclaw/bionic-bypass.js")
        val node = File("$rootfsDir/usr/local/bin/node")
        val qwen = File("$rootfsDir/usr/local/lib/node_modules/@qwen-code/qwen-code/package.json")
        val openclaw = File("$rootfsDir/usr/local/lib/node_modules/openclaw/package.json")
        return rootfs.exists() && binBash.exists() && bypass.exists()
            && node.exists() && (qwen.exists() || openclaw.exists())
    }

    fun getBootstrapStatus(): Map<String, Any> {
        val rootfsExists = File(rootfsDir).exists()
        val binBashExists = File("$rootfsDir/usr/bin/bash").exists()
        val nodeExists = File("$rootfsDir/usr/local/bin/node").exists()
        val qwenExists = File("$rootfsDir/usr/local/lib/node_modules/@qwen-code/qwen-code/package.json").exists()
        val openclawExists = File("$rootfsDir/usr/local/lib/node_modules/openclaw/package.json").exists()
        val bypassExists = File("$rootfsDir/root/.openclaw/bionic-bypass.js").exists()

        return mapOf(
            "rootfsExists" to rootfsExists,
            "binBashExists" to binBashExists,
            "nodeInstalled" to nodeExists,
            "qwenInstalled" to qwenExists,
            "openclawInstalled" to openclawExists,
            "bypassInstalled" to bypassExists,
            "rootfsPath" to rootfsDir,
            "complete" to (rootfsExists && binBashExists && bypassExists
                && nodeExists && (qwenExists || openclawExists))
        )
    }

    fun extractRootfs(tarPath: String) {
        val rootfs = File(rootfsDir)
        if (rootfs.exists()) deleteRecursively(rootfs)
        rootfs.mkdirs()

        val deferredSymlinks = mutableListOf<Pair<String, String>>()
        var entryCount = 0
        var fileCount = 0
        var symlinkCount = 0
        var extractionError: Exception? = null

        try {
            FileInputStream(tarPath).use { fis ->
                BufferedInputStream(fis, 256 * 1024).use { bis ->
                    XZCompressorInputStream(bis).use { xzis ->
                        TarArchiveInputStream(xzis).use { tis ->
                            var entry: TarArchiveEntry? = tis.nextEntry
                            while (entry != null) {
                                entryCount++
                                val name = entry.name.removePrefix("./").removePrefix("/")

                                if (name.isEmpty() || name.startsWith("dev/") || name == "dev") {
                                    entry = tis.nextEntry
                                    continue
                                }

                                val outFile = File(rootfsDir, name)

                                when {
                                    entry.isDirectory -> outFile.mkdirs()
                                    entry.isSymbolicLink -> {
                                        deferredSymlinks.add(Pair(entry.linkName, outFile.absolutePath))
                                        symlinkCount++
                                    }
                                    entry.isLink -> {
                                        val target = entry.linkName.removePrefix("./").removePrefix("/")
                                        val targetFile = File(rootfsDir, target)
                                        outFile.parentFile?.mkdirs()
                                        try {
                                            if (targetFile.exists()) {
                                                targetFile.copyTo(outFile, overwrite = true)
                                                if (targetFile.canExecute()) outFile.setExecutable(true, false)
                                                fileCount++
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    else -> {
                                        outFile.parentFile?.mkdirs()
                                        FileOutputStream(outFile).use { fos ->
                                            val buf = ByteArray(65536)
                                            var len: Int
                                            while (tis.read(buf).also { len = it } != -1) fos.write(buf, 0, len)
                                        }
                                        outFile.setReadable(true, false)
                                        outFile.setWritable(true, false)
                                        val mode = entry.mode
                                        if (mode == 0 || mode and 0b001_001_001 != 0) {
                                            val path = name.lowercase()
                                            if (mode and 0b001_001_001 != 0 ||
                                                path.contains("/bin/") || path.contains("/sbin/") ||
                                                path.endsWith(".sh") || path.contains("/lib/pacman/")) {
                                                outFile.setExecutable(true, false)
                                            }
                                        }
                                        fileCount++
                                    }
                                }
                                entry = tis.nextEntry
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            extractionError = e
        }

        if (entryCount == 0) throw RuntimeException("Extraction failed: tarball empty or corrupt")
        if (extractionError != null && fileCount < 100) {
            throw RuntimeException("Extraction failed after $entryCount entries ($fileCount files): ${extractionError!!.message}")
        }

        // Phase 2: Create symlinks
        var symlinkErrors = 0
        for ((target, path) in deferredSymlinks) {
            try {
                val file = File(path)
                if (file.exists()) {
                    if (file.isDirectory) {
                        val linkTarget = if (target.startsWith("/")) target.removePrefix("/")
                        else {
                            val parent = file.parentFile?.absolutePath ?: rootfsDir
                            File(parent, target).relativeTo(File(rootfsDir)).path
                        }
                        val realTargetDir = File(rootfsDir, linkTarget)
                        if (realTargetDir.exists() && realTargetDir.isDirectory) {
                            file.listFiles()?.forEach { child ->
                                val dest = File(realTargetDir, child.name)
                                if (!dest.exists()) child.renameTo(dest)
                            }
                        }
                        deleteRecursively(file)
                    } else {
                        file.delete()
                    }
                }
                file.parentFile?.mkdirs()
                Os.symlink(target, path)
            } catch (e: Exception) {
                symlinkErrors++
            }
        }

        // Verify extraction
        if (!File("$rootfsDir/usr/bin/bash").exists()) {
            throw RuntimeException("Extraction failed: bash not found in rootfs")
        }

        configureRootfs()
        File(tarPath).delete()
    }

    private fun configureRootfs() {
        val bypassDir = File("$rootfsDir/root/.openclaw")
        bypassDir.mkdirs()

        // DNS
        val dnsContent = getSystemDnsServers()
        File(rootfsDir, "etc/resolv.conf").apply {
            parentFile?.mkdirs()
            writeText(dnsContent)
        }
        File(configDir, "resolv.conf").apply {
            parentFile?.mkdirs()
            writeText(dnsContent)
        }

        // Timezone
        File("$rootfsDir/etc/localtime").apply {
            parentFile?.mkdirs()
            try { Os.symlink("/usr/share/zoneinfo/Etc/UTC", absolutePath) } catch (_: Exception) {}
        }
        File("$rootfsDir/etc/timezone").writeText("Etc/UTC\n")

        // Proot compatibility patches
        val prootCompatContent = """
// Proot compatibility patches for Node.js on Android
const _os = require('os');
const _origCwd = process.cwd;
process.cwd = function() {
  try { return _origCwd.call(process); }
  catch(e) { return '/root'; }
};
const _origNetworkInterfaces = _os.networkInterfaces;
_os.networkInterfaces = function() {
  try {
    const ifaces = _origNetworkInterfaces.call(_os);
    if (ifaces && Object.keys(ifaces).length > 0) return ifaces;
  } catch (e) {}
  return { lo: [{ address: '127.0.0.1', netmask: '255.0.0.0', family: 'IPv4',
    mac: '00:00:00:00:00:00', internal: true, cidr: '127.0.0.1/8' }] };
};
const _origTotalmem = _os.totalmem;
_os.totalmem = function() { try { return _origTotalmem.call(_os); } catch(e) { return 4 * 1024 * 1024 * 1024; } };
const _origFreemem = _os.freemem;
_os.freemem = function() { try { return _origFreemem.call(_os); } catch(e) { return 2 * 1024 * 1024 * 1024; } };
const _fs = require('fs');
const _origMkdirSync = _fs.mkdirSync;
_fs.mkdirSync = function(p, options) {
  try { return _origMkdirSync.call(_fs, p, options); }
  catch(e) { if (e.code === 'ENOSYS') { const parts = p.split('/').filter(Boolean); let c = ''; for (const part of parts) { c += '/' + part; try { _origMkdirSync.call(_fs, c); } catch(e2) {} } return; } throw e; }
};
const _origRenameSync = _fs.renameSync;
_fs.renameSync = function(oldPath, newPath) {
  try { return _origRenameSync.call(_fs, oldPath, newPath); }
  catch(e) { if (e.code === 'ENOSYS' || e.code === 'EXDEV') { _fs.copyFileSync(oldPath, newPath); try { _fs.unlinkSync(oldPath); } catch(_) {} return; } throw e; }
};
""".trimIndent()
        File(bypassDir, "proot-compat.js").writeText(prootCompatContent)

        // Bionic bypass
        val bypassContent = """
// ArchClaw Bionic Bypass
require('/root/.openclaw/proot-compat.js');
""".trimIndent()
        File(bypassDir, "bionic-bypass.js").writeText(bypassContent)

        // Git config (SSH→HTTPS rewrite for npm git deps)
        File("$rootfsDir/root/.gitconfig").writeText(
            "[url \"https://github.com/\"]\n" +
            "\tinsteadOf = ssh://git@github.com/\n" +
            "\tinsteadOf = git@github.com:\n" +
            "[advice]\n\tdetachedHead = false\n"
        )

        // Bashrc
        val bashrc = File("$rootfsDir/root/.bashrc")
        val exportLine = "export NODE_OPTIONS=\"--require /root/.openclaw/bionic-bypass.js\""
        val existing = if (bashrc.exists()) bashrc.readText() else ""
        if (!existing.contains("bionic-bypass")) {
            bashrc.appendText("\n# ArchClaw Bionic Bypass\n$exportLine\n")
        }
    }

    fun extractNodeTarball(tarPath: String) {
        val rootfs = File(rootfsDir)
        if (!rootfs.exists()) rootfs.mkdirs()

        FileInputStream(tarPath).use { fis ->
            BufferedInputStream(fis).use { bis ->
                XZCompressorInputStream(bis).use { xzis ->
                    TarArchiveInputStream(xzis).use { tis ->
                        var entry: TarArchiveEntry? = tis.nextEntry
                        while (entry != null) {
                            val name = entry.name.removePrefix("./")
                            val outFile = File(rootfsDir, name)

                            if (entry.isDirectory) {
                                outFile.mkdirs()
                            } else if (!entry.isSymbolicLink && !entry.isLink) {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    val buf = ByteArray(65536)
                                    var len: Int
                                    while (tis.read(buf).also { len = it } != -1) fos.write(buf, 0, len)
                                }
                                val path = name.lowercase()
                                if (path.contains("/bin/") || path.endsWith(".sh") || entry.mode and 0b001_001_001 != 0) {
                                    outFile.setExecutable(true, false)
                                }
                            }
                            entry = tis.nextEntry
                        }
                    }
                }
            }
        }

        // Create node/npm symlinks in /usr/local/bin
        val nodeBin = File(rootfsDir, "usr/local/bin/node")
        val npmBin = File(rootfsDir, "usr/local/bin/npm")
        val nodeWrapper = File(rootfsDir, "usr/local/bin/node")

        if (nodeBin.exists()) {
            nodeBin.setExecutable(true, false)
        }
        if (npmBin.exists()) {
            npmBin.setExecutable(true, false)
        }

        File(tarPath).delete()
    }

    fun createBinWrappers(packageName: String) {
        val binDir = File(rootfsDir, "usr/local/bin")
        binDir.mkdirs()
        val wrapper = """#!/bin/bash
export NODE_OPTIONS="--require /root/.openclaw/bionic-bypass.js"
exec /usr/local/bin/node "/usr/local/lib/node_modules/$packageName/bin/$packageName" "\$@"
"""
        File(binDir, packageName).writeText(wrapper)
        File(binDir, packageName).setExecutable(true, false)
    }

    fun installBionicBypass() {
        configureRootfs()
    }

    private fun getSystemDnsServers(): String {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val network = cm.activeNetwork
                if (network != null) {
                    val linkProps: LinkProperties? = cm.getLinkProperties(network)
                    val dnsServers = linkProps?.dnsServers
                    if (dnsServers != null && dnsServers.isNotEmpty()) {
                        val lines = dnsServers.joinToString("\n") { "nameserver ${it.hostAddress}" }
                        return "$lines\nnameserver 8.8.8.8\n"
                    }
                }
            }
        } catch (_: Exception) {}
        return "nameserver 8.8.8.8\nnameserver 8.8.4.4\n"
    }

    fun writeResolvConf() {
        val content = getSystemDnsServers()
        try {
            val dir = File(context.filesDir, "config")
            dir.mkdirs()
            File(dir, "resolv.conf").writeText(content)
        } catch (_: Exception) {
            File(configDir).mkdirs()
            File(configDir, "resolv.conf").writeText(content)
        }
        try {
            val rootfsResolv = File(rootfsDir, "etc/resolv.conf")
            rootfsResolv.parentFile?.mkdirs()
            rootfsResolv.writeText(content)
        } catch (_: Exception) {}
    }

    fun setupFakeSysdata() {
        val procDir = File("$configDir/proc_fakes")
        val sysDir = File("$configDir/sys_fakes")
        procDir.mkdirs()
        sysDir.mkdirs()

        File(procDir, "loadavg").writeText("0.12 0.07 0.02 2/165 765\n")
        File(procDir, "stat").writeText(
            "cpu  1957 0 2877 93280 262 342 254 87 0 0\n" +
            "cpu0 31 0 226 12027 82 10 4 9 0 0\n" +
            "cpu1 45 0 290 11498 21 9 8 7 0 0\n" +
            "cpu2 52 0 401 11730 36 15 6 10 0 0\n" +
            "cpu3 42 0 268 11677 31 12 5 8 0 0\n" +
            "cpu4 789 0 720 11364 26 100 83 18 0 0\n" +
            "cpu5 486 0 438 11685 42 86 60 13 0 0\n" +
            "cpu6 314 0 336 11808 45 68 52 11 0 0\n" +
            "cpu7 198 0 198 11491 25 42 36 11 0 0\n"
        )
        File(procDir, "uptime").writeText("124.08 932.80\n")
        File(procDir, "version").writeText(
            "Linux version ${ProcessManager.FAKE_KERNEL_RELEASE} (proot@archclaw) " +
            "(gcc (GCC) 14.2.1, GNU ld (GNU Binutils) 2.42) " +
            "${ProcessManager.FAKE_KERNEL_VERSION}\n"
        )
        File(procDir, "vmstat").writeText(
            "nr_free_pages 1743136\npgfault 37291463\npgmajfault 6854\n"
        )
        File(procDir, "cap_last_cap").writeText("40\n")
        File(procDir, "max_user_watches").writeText("4096\n")
        File(procDir, "fips_enabled").writeText("0\n")
        File(sysDir, "empty").writeText("")
    }

    fun readRootfsFile(path: String): String? {
        val file = File("$rootfsDir/$path")
        return if (file.exists()) file.readText() else null
    }

    fun writeRootfsFile(path: String, content: String) {
        val file = File("$rootfsDir/$path")
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }
}

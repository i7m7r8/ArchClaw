package io.archclaw

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
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
        return rootfs.exists() && binBash.exists() && bypass.exists() && node.exists()
    }

    fun getBootstrapStatus(): Map<String, Any> {
        val rootfsExists = File(rootfsDir).exists()
        val binBashExists = File("$rootfsDir/usr/bin/bash").exists()
        val nodeExists = File("$rootfsDir/usr/local/bin/node").exists()
        val bypassExists = File("$rootfsDir/root/.openclaw/bionic-bypass.js").exists()

        return mapOf(
            "rootfsExists" to rootfsExists,
            "binBashExists" to binBashExists,
            "nodeInstalled" to nodeExists,
            "bypassInstalled" to bypassExists,
            "rootfsPath" to rootfsDir,
            "complete" to (rootfsExists && binBashExists && bypassExists && nodeExists)
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

        FileInputStream(tarPath).use { fis ->
            BufferedInputStream(fis, 256 * 1024).use { bis ->
                // Auto-detect: XZ magic = FD 37 7A 58 5A 00, GZIP magic = 1F 8B
                bis.mark(6)
                val magic = ByteArray(6)
                val magicRead = bis.read(magic)
                bis.reset()

                val decompressor: InputStream = if (magicRead >= 6 &&
                    magic[0] == 0xFD.toByte() && magic[1] == 0x37.toByte() &&
                    magic[2] == 0x7A.toByte() && magic[3] == 0x58.toByte() &&
                    magic[4] == 0x5A.toByte() && magic[5] == 0x00.toByte()) {
                    XZCompressorInputStream(bis)
                } else {
                    GzipCompressorInputStream(bis)
                }

                try {
                    TarArchiveInputStream(decompressor).use { tis ->
                        var entry: TarArchiveEntry? = tis.nextTarEntry
                        while (entry != null) {
                            entryCount++
                            val name = entry.name.removePrefix("./").removePrefix("/")

                            if (name.isEmpty() || name.startsWith("dev/") || name == "dev") {
                                entry = tis.nextTarEntry
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
                                    if (targetFile.exists()) {
                                        targetFile.copyTo(outFile, overwrite = true)
                                        if (targetFile.canExecute()) outFile.setExecutable(true, false)
                                        fileCount++
                                    }
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
                                    val path = name.lowercase()
                                    if (mode == 0 || mode and 0b001_001_001 != 0 ||
                                        path.contains("/bin/") || path.contains("/sbin/") ||
                                        path.endsWith(".sh") || path.contains("/lib/pacman/")) {
                                        outFile.setExecutable(true, false)
                                    }
                                    fileCount++
                                }
                            }
                            entry = tis.nextTarEntry
                        }
                    }
                } catch (e: Exception) {
                    extractionError = e
                }
            }
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

        if (!File("$rootfsDir/usr/bin/bash").exists()) {
            throw RuntimeException("Extraction failed: bash not found in rootfs. $entryCount entries, $fileCount files, $symlinkCount symlinks ($symlinkErrors errors)")
        }

        configureRootfs()
        File(tarPath).delete()
    }

    private fun configureRootfs() {
        val bypassDir = File("$rootfsDir/root/.openclaw")
        bypassDir.mkdirs()

        val dnsContent = getSystemDnsServers()
        File(rootfsDir, "etc/resolv.conf").apply { parentFile?.mkdirs(); writeText(dnsContent) }
        File(configDir, "resolv.conf").apply { parentFile?.mkdirs(); writeText(dnsContent) }

        val prootCompatContent = """
const _os = require('os');
const _origCwd = process.cwd;
process.cwd = function() { try { return _origCwd.call(process); } catch(e) { return '/root'; } };
const _origNetIf = _os.networkInterfaces;
_os.networkInterfaces = function() {
  try { const ifaces = _origNetIf.call(_os); if (ifaces && Object.keys(ifaces).length > 0) return ifaces; } catch(e) {}
  return { lo: [{ address: '127.0.0.1', netmask: '255.0.0.0', family: 'IPv4', mac: '00:00:00:00:00:00', internal: true, cidr: '127.0.0.1/8' }] };
};
_os.totalmem = function() { try { return _os.totalmem(); } catch(e) { return 4*1024*1024*1024; } };
_os.freemem = function() { try { return _os.freemem(); } catch(e) { return 2*1024*1024*1024; } };
const _fs = require('fs');
const _origMkdirSync = _fs.mkdirSync;
_fs.mkdirSync = function(p, opt) { try { return _origMkdirSync.call(_fs, p, opt); } catch(e) { if (e.code === 'ENOSYS') { p.split('/').filter(Boolean).reduce((a,c) => { const d = a+'/'+c; try { _origMkdirSync.call(_fs, d); } catch(e2) {} return d; }, ''); return; } throw e; } };
const _origRenameSync = _fs.renameSync;
_fs.renameSync = function(o, n) { try { return _origRenameSync.call(_fs, o, n); } catch(e) { if (e.code === 'ENOSYS' || e.code === 'EXDEV') { _fs.copyFileSync(o, n); try { _fs.unlinkSync(o); } catch(_) {} return; } throw e; } };
""".trimIndent()
        File(bypassDir, "proot-compat.js").writeText(prootCompatContent)
        File(bypassDir, "bionic-bypass.js").writeText("require('/root/.openclaw/proot-compat.js');\n")

        File("$rootfsDir/root/.gitconfig").writeText(
            "[url \"https://github.com/\"]\n\tinsteadOf = ssh://git@github.com/\n\tinsteadOf = git@github.com:\n"
        )

        val bashrc = File("$rootfsDir/root/.bashrc")
        val exportLine = "export NODE_OPTIONS=\"--require /root/.openclaw/bionic-bypass.js\""
        val existing = if (bashrc.exists()) bashrc.readText() else ""
        if (!existing.contains("bionic-bypass")) bashrc.appendText("\n# ArchClaw\n$exportLine\n")
    }

    fun extractNodeTarball(tarPath: String) {
        FileInputStream(tarPath).use { fis ->
            BufferedInputStream(fis).use { bis ->
                XZCompressorInputStream(bis).use { xzis ->
                    TarArchiveInputStream(xzis).use { tis ->
                        var entry: TarArchiveEntry? = tis.nextTarEntry
                        while (entry != null) {
                            val name = entry.name.removePrefix("./")
                            val outFile = File(rootfsDir, name)
                            if (entry.isDirectory) outFile.mkdirs()
                            else if (!entry.isSymbolicLink && !entry.isLink) {
                                outFile.parentFile?.mkdirs()
                                FileOutputStream(outFile).use { fos ->
                                    val buf = ByteArray(65536)
                                    var len: Int
                                    while (tis.read(buf).also { len = it } != -1) fos.write(buf, 0, len)
                                }
                                if (name.lowercase().contains("/bin/") || entry.mode and 0b001_001_001 != 0)
                                    outFile.setExecutable(true, false)
                            }
                            entry = tis.nextTarEntry
                        }
                    }
                }
            }
        }
        File(tarPath).delete()
    }

    fun createBinWrappers(packageName: String) {
        val binDir = File(rootfsDir, "usr/local/bin")
        binDir.mkdirs()
        File(binDir, packageName).writeText(
            "#!/bin/bash\nexport NODE_OPTIONS=\"--require /root/.openclaw/bionic-bypass.js\"\n" +
            "exec /usr/local/bin/node /usr/local/lib/node_modules/$packageName/bin/$packageName \"\$@\"\n"
        )
        File(binDir, packageName).setExecutable(true, false)
    }

    fun installBionicBypass() { configureRootfs() }
    fun writeResolvConf() {
        val content = getSystemDnsServers()
        File(configDir, "resolv.conf").apply { parentFile?.mkdirs(); writeText(content) }
        File(rootfsDir, "etc/resolv.conf").apply { parentFile?.mkdirs(); writeText(content) }
    }
    fun readRootfsFile(path: String): String? {
        val f = File("$rootfsDir/$path")
        return if (f.exists()) f.readText() else null
    }
    fun writeRootfsFile(path: String, content: String) {
        val f = File("$rootfsDir/$path")
        f.parentFile?.mkdirs()
        f.writeText(content)
    }

    private fun getSystemDnsServers(): String {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val network = cm.activeNetwork
                if (network != null) {
                    val linkProps = cm.getLinkProperties(network)
                    val dnsServers = linkProps?.dnsServers
                    if (dnsServers != null && dnsServers.isNotEmpty()) {
                        return dnsServers.joinToString("\n") { "nameserver ${it.hostAddress}" } + "\nnameserver 8.8.8.8\n"
                    }
                }
            }
        } catch (_: Exception) {}
        return "nameserver 8.8.8.8\nnameserver 8.8.4.4\n"
    }

    fun setupFakeSysdata() {
        val procDir = File("$configDir/proc_fakes")
        val sysDir = File("$configDir/sys_fakes")
        procDir.mkdirs(); sysDir.mkdirs()
        File(procDir, "loadavg").writeText("0.12 0.07 0.02 2/165 765\n")
        File(procDir, "version").writeText("Linux version ${ProcessManager.FAKE_KERNEL_RELEASE} (proot@archclaw) ${ProcessManager.FAKE_KERNEL_VERSION}\n")
        File(procDir, "fips_enabled").writeText("0\n")
        File(procDir, "cap_last_cap").writeText("40\n")
        File(procDir, "max_user_watches").writeText("4096\n")
        File(sysDir, "empty").writeText("")
    }

    private fun deleteRecursively(file: File) {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        file.delete()
    }
}

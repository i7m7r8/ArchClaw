import 'package:shared_preferences/shared_preferences.dart';
import 'dart:convert';
import 'dart:io';
package io.archclaw

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.os.Build
import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream

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
        // Download proot + libs from Termux if not bundled in APK
        ensureProotBinaries()
        // Create fake /proc and /sys files for proot bind mounts
        setupFakeSysdata()
    }

    /** Download proot + libtalloc + loaders from Termux repo if not bundled.
        Supports resume if interrupted. */
    private fun ensureProotBinaries() {
        val prootFile = File(libDir, "libproot.so")
        val loaderFile = File(libDir, "libprootloader.so")
        val loader32File = File(libDir, "libprootloader32.so")
        val tallocFile = File(libDir, "libtalloc.so")
        val talloc2File = File(libDir, "libtalloc.so.2")

        // If already in nativeLibDir (bundled in APK), copy to libDir
        val nativeProot = File(nativeLibDir, "libproot.so")
        if (nativeProot.exists() && !prootFile.exists()) {
            nativeProot.copyTo(prootFile)
            File(nativeLibDir, "libprootloader.so").takeIf { it.exists() }?.copyTo(loaderFile)
            File(nativeLibDir, "libprootloader32.so").takeIf { it.exists() }?.copyTo(loader32File)
            File(nativeLibDir, "libtalloc.so").takeIf { it.exists() }?.copyTo(tallocFile)
        }
        if (prootFile.exists()) {
            // Ensure libtalloc.so.2 exists (proot needs this SONAME)
            if (tallocFile.exists() && !talloc2File.exists()) {
                tallocFile.copyTo(talloc2File)
                talloc2File.setExecutable(true)
            }
            return
        }

        android.util.Log.i("ArchClaw", "Downloading proot from Termux repo...")
        val repo = "https://packages-cf.termux.dev/apt/termux-main"

        try {
            // Get package filename from repo index
            val pkgs = URL("$repo/dists/stable/main/binary-aarch64/Packages")
                .openStream().bufferedReader().readText()
            val filename = pkgs.lineSequence()
                .dropWhile { it != "Package: proot" }
                .drop(1)
                .firstOrNull { it.startsWith("Filename:") }
                ?.substringAfter("Filename: ")?.trim()
                ?: throw RuntimeException("proot package not found in repo")

            // Download .deb with resume support
            val debFile = File(tmpDir, "proot.deb")
            val debUrl = URL("$repo/$filename")
            downloadWithResume(debUrl, debFile)

            // Extract .deb → data.tar → copy needed files to libDir
            FileInputStream(debFile).use { fis ->
                ArArchiveInputStream(fis).use { ar ->
                    var entry = ar.nextArEntry
                    while (entry != null) {
                        if (entry.name.startsWith("data.tar")) {
                            val decompressor: InputStream = when {
                                entry.name.endsWith(".xz") -> XZCompressorInputStream(ar)
                                entry.name.endsWith(".gz") -> GZIPInputStream(ar)
                                entry.name.endsWith(".zst") -> ZstdCompressorInputStream(ar)
                                else -> ar
                            }
                            TarArchiveInputStream(decompressor).use { tar ->
                                var te = tar.nextTarEntry
                                while (te != null) {
                                    val name = te.name.removePrefix("./")
                                    when {
                                        name.endsWith("/bin/proot") -> {
                                            FileOutputStream(prootFile).use { tar.copyTo(it) }
                                            prootFile.setExecutable(true, false)
                                        }
                                        name.contains("libtalloc.so") && !name.contains(".py") -> {
                                            FileOutputStream(tallocFile).use { tar.copyTo(it) }
                                            tallocFile.setExecutable(true, false)
                                            // Also create libtalloc.so.2 (SONAME that proot needs)
                                            tallocFile.copyTo(talloc2File)
                                            talloc2File.setExecutable(true, false)
                                        }
                                        name.endsWith("/proot/loader") && !name.endsWith("loader32") -> {
                                            FileOutputStream(loaderFile).use { tar.copyTo(it) }
                                            loaderFile.setExecutable(true, false)
                                        }
                                        name.endsWith("/proot/loader32") -> {
                                            FileOutputStream(loader32File).use { tar.copyTo(it) }
                                            loader32File.setExecutable(true, false)
                                        }
                                    }
                                    te = tar.nextTarEntry
                                }
                            }
                            break
                        }
                        entry = ar.nextArEntry
                    }
                }
            }
            debFile.delete()

            // Final verification
            if (!prootFile.exists()) throw RuntimeException("proot extraction failed")
            if (!talloc2File.exists() && tallocFile.exists()) {
                tallocFile.copyTo(talloc2File)
                talloc2File.setExecutable(true)
            }

            android.util.Log.i("ArchClaw", "Proot downloaded to $libDir")
        } catch (e: Exception) {
            android.util.Log.e("ArchClaw", "Failed to download proot: ${e.message}")
            throw e
        }
    }

    /** Download with HTTP resume support. If partial file exists, 
        sends Range header to continue from where it left off. */
    private fun downloadWithResume(url: URL, destFile: File) {
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "ArchClaw/1.0")

        var downloadedBytes = 0L
        // Resume if partial file exists
        if (destFile.exists()) {
            downloadedBytes = destFile.length()
            conn.setRequestProperty("Range", "bytes=$downloadedBytes-")
        }

        val responseCode = conn.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK && 
            responseCode != HttpURLConnection.HTTP_PARTIAL) {
            conn.disconnect()
            throw RuntimeException("Download failed: HTTP $responseCode")
        }

        val totalBytes = conn.contentLengthLong
        val isResume = responseCode == HttpURLConnection.HTTP_PARTIAL

        conn.inputStream.use { input ->
            FileOutputStream(destFile, append = isResume).use { output ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                }
            }
        }
        conn.disconnect()
    }

    /** Setup libtalloc.so.2 symlink/copy.
        Proot links against libtalloc.so.2 but Android packages ship libtalloc.so */
    private fun setupLibtalloc() {
        val talloc2File = File(libDir, "libtalloc.so.2")
        // Check nativeLibDir first (bundled in APK)
        val nativeSource = File(nativeLibDir, "libtalloc.so")
        if (nativeSource.exists() && !talloc2File.exists()) {
            nativeSource.copyTo(talloc2File)
            talloc2File.setExecutable(true)
        }
        // Also check libDir for downloaded libtalloc
        val downloadedSource = File(libDir, "libtalloc.so")
        if (downloadedSource.exists() && !talloc2File.exists()) {
            downloadedSource.copyTo(talloc2File)
            talloc2File.setExecutable(true)
        }
    }

    fun isBootstrapComplete(): Boolean {
        val rootfs = File(rootfsDir)
        val binBash = File("$rootfsDir/usr/bin/bash")
        val bypass = File("$rootfsDir/root/.openclaw/bionic-bypass.js")
        val node = File("$rootfsDir/usr/local/bin/node")
        val openclaw = File("$rootfsDir/usr/local/lib/

  Widget _buildQwenOAuthSection() {
    return Card(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: ListTile(
        leading: const Icon(Icons.login, color: Color(0xFF6366F1)),
        title: const Text('Qwen OAuth'),
        subtitle: const Text('Import Qwen Code OAuth token for AI access'),
        trailing: const Icon(Icons.chevron_right),
        onTap: () {
          Navigator.push(
            context,
            MaterialPageRoute(builder: (context) => const QwenOAuthScreen()),
          );
        },
      ),
    );
  }

package io.archclaw.setup

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.archclaw.ArchClawApp
import io.archclaw.ArchUtils
import io.archclaw.AppConstants
import io.archclaw.BootstrapManager
import io.archclaw.MainActivity
import io.archclaw.ProcessManager
import io.archclaw.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button

    private lateinit var bootstrapManager: BootstrapManager
    private lateinit var processManager: ProcessManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)

        if (ArchClawApp.instance.isSetupComplete()) {
            startActivity(MainActivity.newIntent(this))
            finish()
            return
        }

        val filesDir = applicationContext.filesDir.absolutePath
        val nativeLibDir = applicationContext.applicationInfo.nativeLibraryDir

        bootstrapManager = BootstrapManager(applicationContext, filesDir, nativeLibDir)
        processManager = ProcessManager(filesDir, nativeLibDir)

        // Setup directories on start
        bootstrapManager.setupDirectories()
        bootstrapManager.writeResolvConf()

        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        statusText = findViewById(R.id.statusText)
        startButton = findViewById(R.id.startButton)

        startButton.setOnClickListener {
            startButton.visibility = View.GONE
            startSetup()
        }
    }

    private fun startSetup() {
        lifecycleScope.launch {
            try {
                runFullSetup()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Error: ${e.message}"
                    startButton.visibility = View.VISIBLE
                    startButton.text = "Retry"
                }
            }
        }
    }

    private suspend fun runFullSetup() {
        val filesDir = ArchClawApp.instance.filesDir.absolutePath
        val arch = ArchUtils.getArch()
        val tarPath = "$filesDir/tmp/archlinux-rootfs.tar.gz"

        // Step 1: Download rootfs (0-30%)
        withContext(Dispatchers.Main) {
            progressBar.isIndeterminate = false
            statusText.text = "Downloading Arch Linux..."
        }

        val rootfsMirrors = listOf(
            "https://archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            "https://mirror.archlinuxarm.org/os/ArchLinuxARM-aarch64-latest.tar.gz",
            "https://mirror.sg.gs/archlinuxarm/os/multi/ArchLinuxARM-2026.01-aarch64-rootfs.tar.gz"
        )
        
        var rootfsDownloaded = false
        for (mirror in rootfsMirrors) {
            try {
                downloadFile(mirror, tarPath) { progress ->
                    val overallProgress = (progress * 30).toInt()
                    withContext(Dispatchers.Main) {
                        progressBar.progress = overallProgress
                        progressText.text = "${overallProgress}%"
                    }
                }
                rootfsDownloaded = true
                break
            } catch (e: Exception) {
                // Try next mirror
            }
        }
        if (!rootfsDownloaded) throw Exception("All rootfs mirrors failed")

        // Step 2: Extract rootfs (30-50%)
        withContext(Dispatchers.Main) { statusText.text = "Extracting rootfs..." }

        withContext(Dispatchers.IO) { bootstrapManager.extractRootfs(tarPath) }

        // Step 3: Install Node.js (50-75%)
        withContext(Dispatchers.Main) { statusText.text = "Downloading Node.js..." }

        val nodeTarUrl = AppConstants.getNodeTarballUrl(arch)
        val nodeTarPath = "$filesDir/tmp/nodejs.tar.xz"

        downloadFile(nodeTarUrl, nodeTarPath) { progress ->
            val overallProgress = 50 + (progress * 15).toInt()
            withContext(Dispatchers.Main) {
                progressBar.progress = overallProgress
                progressText.text = "${overallProgress}%"
            }
        }

        withContext(Dispatchers.Main) { statusText.text = "Installing Node.js..." }

        withContext(Dispatchers.IO) {
            bootstrapManager.extractNodeTarball(nodeTarPath)
            bootstrapManager.installBionicBypass()

            processManager.runInProotSync("pacman -Syu --noconfirm --needed base-devel git curl wget vim nano sudo openssh htop")
            processManager.runInProotSync("ln -sf /usr/share/zoneinfo/Etc/UTC /etc/localtime && echo 'Etc/UTC' > /etc/timezone")
            processManager.runInProotSync("useradd -m -s /bin/bash archclaw || true")
            processManager.runInProotSync("echo 'archclaw ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers || true")

            val nodeVersion = processManager.runInProotSync("node --version")
            if (!nodeVersion.trim().startsWith("v")) {
                throw RuntimeException("Node.js verification failed: $nodeVersion")
            }
        }

        // Step 4: Install AI tools (75-95%)
        withContext(Dispatchers.Main) { statusText.text = "Installing OpenClaw + Qwen Code..." }

        withContext(Dispatchers.IO) {
            processManager.runInProotSync("npm install -g openclaw", timeoutSeconds = 1800)
            processManager.runInProotSync("npm install -g @qwen-code/qwen-code 2>/dev/null || true", timeoutSeconds = 600)
            processManager.runInProotSync("pacman -S --noconfirm --needed python python-pip")
            processManager.runInProotSync("pip install --break-system-packages aider-chat 2>/dev/null || true")

            bootstrapManager.createBinWrappers("openclaw")

            processManager.runInProotSync("openclaw --version || echo openclaw_installed")
        }

        // Step 5: Complete
        withContext(Dispatchers.Main) {
            progressBar.progress = 100
            progressText.text = "100%"
            statusText.text = "Setup complete!"
        }

        ArchClawApp.instance.setSetupComplete()

        withContext(Dispatchers.Main) {
            Toast.makeText(this@SetupWizardActivity, "Setup complete!", Toast.LENGTH_LONG).show()
            startActivity(MainActivity.newIntent(this@SetupWizardActivity))
            finish()
        }
    }

    private suspend fun downloadFile(url: String, destPath: String, onProgress: suspend (Double) -> Unit) {
        withContext(Dispatchers.IO) {
            val client = OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "ArchClaw/0.1.0 (Android; proot)")
                .header("Accept", "*/*")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw RuntimeException("HTTP ${response.code} from $url")
            }

            val body = response.body ?: throw RuntimeException("Empty response body")
            val totalBytes = body.contentLength()
            val destFile = File(destPath)
            destFile.parentFile?.mkdirs()

            var downloadedBytes = 0L
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(downloadedBytes.toDouble() / totalBytes.toDouble())
                        }
                    }
                }
            }

            if (!destFile.exists() || destFile.length() == 0L) {
                throw RuntimeException("Downloaded file is empty")
            }
        }
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, SetupWizardActivity::class.java)
    }
}

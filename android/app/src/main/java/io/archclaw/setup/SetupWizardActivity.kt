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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

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
        val rootfsUrl = AppConstants.ROOTFS_URL
        val tarPath = "$filesDir/tmp/archlinux-rootfs.tar.gz"

        // Step 1: Download rootfs (0-30%)
        withContext(Dispatchers.Main) {
            progressBar.isIndeterminate = false
            statusText.text = "Downloading Arch Linux..."
        }

        downloadFile(rootfsUrl, tarPath) { progress ->
            val overallProgress = (progress * 30).toInt()
            withContext(Dispatchers.Main) {
                progressBar.progress = overallProgress
                progressText.text = "${overallProgress}%"
            }
        }

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
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 120000
            connection.readTimeout = 120000
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "ArchClaw/1.0")
            connection.instanceFollowRedirects = true

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                throw RuntimeException("Download failed: HTTP ${connection.responseCode}")
            }

            val totalBytes = connection.contentLengthLong
            val destFile = File(destPath)
            destFile.parentFile?.mkdirs()

            var downloadedBytes = 0L
            connection.inputStream.use { input ->
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
            connection.disconnect()

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

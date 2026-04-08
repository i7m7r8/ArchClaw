package io.archclaw.setup

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.archclaw.ArchClawApp
import io.archclaw.MainActivity
import io.archclaw.R
import io.archclaw.core.SetupStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)

        if (ArchClawApp.instance.isSetupComplete()) {
            startActivity(MainActivity.newIntent(this))
            finish()
            return
        }

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
        statusText.text = "Starting setup..."
        val app = ArchClawApp.instance

        lifecycleScope.launch {
            try {
                app.prootManager.setupProgress().collect { step ->
                    withContext(Dispatchers.Main) {
                        when (step) {
                            is SetupStep.CheckingEnvironment -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Checking environment..."
                            }
                            is SetupStep.DownloadingProot -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Downloading proot..."
                            }
                            is SetupStep.DownloadingRootfs -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Downloading Arch Linux..."
                            }
                            is SetupStep.ExtractingRootfs -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Extracting rootfs..."
                            }
                            is SetupStep.Bootstrapping -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Bootstrapping Arch Linux..."
                            }
                            is SetupStep.InstallingNodeJS -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Installing Node.js + OpenClaw..."
                            }
                            is SetupStep.InstallingPython -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Installing Python..."
                            }
                            is SetupStep.InstallingAITools -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Installing AI tools..."
                            }
                            is SetupStep.Complete -> {
                                progressBar.isIndeterminate = false
                                progressBar.progress = 100
                                progressText.text = "100%"
                                statusText.text = "Setup complete!"
                                app.setSetupComplete()
                                startActivity(MainActivity.newIntent(this@SetupWizardActivity))
                                finish()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Error: ${e.message}"
                    startButton.visibility = View.VISIBLE
                    startButton.text = "Retry"
                }
            }
        }
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, SetupWizardActivity::class.java)
    }
}

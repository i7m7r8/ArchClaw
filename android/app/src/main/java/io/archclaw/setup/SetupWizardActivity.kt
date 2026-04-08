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
import io.archclaw.MainActivity
import io.archclaw.R
import io.archclaw.core.ProotManager
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

        if ((application as ArchClawApp).isSetupComplete()) {
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
        val app = application as ArchClawApp

        lifecycleScope.launch {
            try {
                val prootManager = ProotManager(app.filesDir)
                prootManager.setupProgress().collect { step ->
                    withContext(Dispatchers.Main) {
                        when (step) {
                            is SetupStep.DownloadingRootfs -> {
                                progressBar.isIndeterminate = false
                                progressBar.progress = step.progress
                                progressText.text = "${step.progress}%"
                                statusText.text = "Downloading Arch Linux rootfs..."
                            }
                            is SetupStep.ExtractingRootfs -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Extracting rootfs..."
                            }
                            is SetupStep.InstallingProot -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Installing proot..."
                            }
                            is SetupStep.Bootstrapping -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Bootstrapping Arch Linux..."
                            }
                            is SetupStep.InstallingNodeJS -> {
                                progressBar.isIndeterminate = true
                                statusText.text = "Installing Node.js..."
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
                                onSetupComplete()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusText.text = "Error: ${e.message}"
                    startButton.visibility = View.VISIBLE
                    startButton.text = "Retry"
                    Toast.makeText(this@SetupWizardActivity,
                        "Setup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun onSetupComplete() {
        (application as ArchClawApp).setSetupComplete()
        Toast.makeText(this, "Setup complete!", Toast.LENGTH_LONG).show()
        startActivity(MainActivity.newIntent(this))
        finish()
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, SetupWizardActivity::class.java)
    }
}

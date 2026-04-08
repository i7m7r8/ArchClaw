package io.archclaw.setup

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.archclaw.ArchClawApp
import io.archclaw.R
import io.archclaw.core.SetupStep
import io.archclaw.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Setup Wizard - runs on first launch
 * Downloads Arch Linux rootfs, installs proot, Node.js, Python, AI tools
 */
class SetupWizardActivity : AppCompatActivity() {

    private val viewModel: SetupViewModel by viewModels()
    
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var statusText: TextView
    private lateinit var startButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_wizard)

        // Check if already setup
        if ((application as ArchClawApp).isSetupComplete()) {
            startActivity(MainActivity.newIntent(this))
            finish()
            return
        }

        setupUI()
        observeSetup()
    }

    private fun setupUI() {
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
        
        lifecycleScope.launch {
            try {
                viewModel.setupProgress().collectLatest { step ->
                    when (step) {
                        is SetupStep.DownloadingRootfs -> {
                            progressBar.progress = step.progress
                            progressText.text = "${step.progress}%"
                            statusText.text = "Downloading Arch Linux rootfs..."
                        }
                        is SetupStep.ExtractingRootfs -> {
                            progressBar.isIndeterminate = true
                            statusText.text = "Extracting rootfs..."
                        }
                        is SetupStep.InstallingProot -> {
                            statusText.text = "Installing proot..."
                        }
                        is SetupStep.Bootstrapping -> {
                            statusText.text = "Bootstrapping Arch Linux..."
                        }
                        is SetupStep.InstallingNodeJS -> {
                            statusText.text = "Installing Node.js..."
                        }
                        is SetupStep.InstallingPython -> {
                            statusText.text = "Installing Python..."
                        }
                        is SetupStep.InstallingAITools -> {
                            statusText.text = "Installing AI tools (Qwen Code, ZeroClaw, OpenClaw)..."
                        }
                        is SetupStep.Complete -> {
                            progressBar.progress = 100
                            progressText.text = "100%"
                            statusText.text = "Setup complete!"
                            onSetupComplete()
                        }
                    }
                }
            } catch (e: Exception) {
                statusText.text = "Error: ${e.message}"
                startButton.visibility = View.VISIBLE
                startButton.text = "Retry"
                Toast.makeText(this@SetupWizardActivity, 
                    "Setup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun onSetupComplete() {
        (application as ArchClawApp).markSetupComplete()
        
        Toast.makeText(this, "Setup complete! Welcome to ArchClaw.", Toast.LENGTH_LONG).show()
        
        // Navigate to main app
        startActivity(MainActivity.newIntent(this))
        finish()
    }

    companion object {
        fun newIntent(context: android.content.Context) = 
            android.content.Intent(context, SetupWizardActivity::class.java)
    }
}

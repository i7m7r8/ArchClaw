package io.archclaw

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import io.archclaw.auth.OAuthWebViewActivity
import io.archclaw.core.ProotManager
import io.archclaw.service.ArchClawService
import io.archclaw.setup.SetupWizardActivity
import io.archclaw.terminal.TerminalActivity

class MainActivity : AppCompatActivity() {

    private lateinit var app: ArchClawApp
    private lateinit var setupCard: CardView
    private lateinit var authCard: CardView
    private lateinit var qwenCodeButton: Button
    private lateinit var zeroClawButton: Button
    private lateinit var openClawButton: Button
    private lateinit var aiderButton: Button
    private lateinit var claudeButton: Button
    private lateinit var geminiButton: Button
    private lateinit var terminalButton: Button
    private lateinit var authStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        app = application as ArchClawApp
        setupUI()
        updateUI()

        val serviceIntent = Intent(this, ArchClawService::class.java)
        startForegroundService(serviceIntent)
    }

    private fun setupUI() {
        setupCard = findViewById(R.id.setupCard)
        authCard = findViewById(R.id.authCard)
        qwenCodeButton = findViewById(R.id.qwenCodeButton)
        zeroClawButton = findViewById(R.id.zeroClawButton)
        openClawButton = findViewById(R.id.openClawButton)
        aiderButton = findViewById(R.id.aiderButton)
        claudeButton = findViewById(R.id.claudeButton)
        geminiButton = findViewById(R.id.geminiButton)
        terminalButton = findViewById(R.id.terminalButton)
        authStatusText = findViewById(R.id.authStatusText)

        setupCard.setOnClickListener {
            startActivity(SetupWizardActivity.newIntent(this))
        }

        authCard.setOnClickListener { startOAuthLogin() }

        qwenCodeButton.setOnClickListener { launchTool("qwen") }
        zeroClawButton.setOnClickListener { launchTool("zeroclaw") }
        openClawButton.setOnClickListener { launchTool("openclaw") }
        aiderButton.setOnClickListener { launchTool("aider") }
        claudeButton.setOnClickListener { launchTool("claude") }
        geminiButton.setOnClickListener { launchTool("gemini") }
        terminalButton.setOnClickListener {
            startActivity(TerminalActivity.newIntent(this))
        }
    }

    private fun updateUI() {
        setupCard.visibility = if (app.isSetupComplete()) View.GONE else View.VISIBLE
        updateAuthStatus()
    }

    private fun updateAuthStatus() {
        val token = app.getQwenOAuthToken()
        if (token != null && !app.isQwenOAuthExpired()) {
            authStatusText.text = "✓ Qwen OAuth Active (2,000 req/day free)"
            authStatusText.setTextColor(getColor(R.color.success_green))
        } else {
            authStatusText.text = "Not authenticated - Tap to login"
            authStatusText.setTextColor(getColor(R.color.text_secondary))
        }
    }

    private fun startOAuthLogin() {
        val intent = Intent(this, OAuthWebViewActivity::class.java)
        startActivityForResult(intent, REQUEST_OAUTH)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OAUTH && resultCode == RESULT_OK) {
            updateAuthStatus()
            Toast.makeText(this, "✓ Qwen OAuth authenticated!", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchTool(toolId: String) {
        if (!app.isSetupComplete()) {
            Toast.makeText(this, "Please complete setup first", Toast.LENGTH_SHORT).show()
            startActivity(SetupWizardActivity.newIntent(this))
            return
        }

        val qwenTools = listOf("qwen", "zeroclaw", "openclaw", "aider")
        if (toolId in qwenTools) {
            val token = app.getQwenOAuthToken()
            if (token == null || app.isQwenOAuthExpired()) {
                Toast.makeText(this, "Please login with Qwen OAuth first", Toast.LENGTH_SHORT).show()
                startOAuthLogin()
                return
            }
        }

        try {
            app.prootManager.launchTool(toolId)
            Toast.makeText(this, "Started $toolId", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to launch: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val REQUEST_OAUTH = 1001

        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, MainActivity::class.java)
    }
}

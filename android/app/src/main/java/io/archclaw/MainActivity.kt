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
import io.archclaw.service.ArchClawService
import io.archclaw.setup.SetupWizardActivity
import io.archclaw.terminal.TerminalActivity

class MainActivity : AppCompatActivity() {

    private lateinit var setupCard: CardView
    private lateinit var authCard: CardView
    private lateinit var authStatusText: TextView
    private lateinit var authButton: Button
    private lateinit var qwenCodeButton: Button
    private lateinit var zeroClawButton: Button
    private lateinit var openClawButton: Button
    private lateinit var aiderButton: Button
    private lateinit var claudeButton: Button
    private lateinit var geminiButton: Button
    private lateinit var terminalButton: Button

    private lateinit var bootstrapManager: BootstrapManager
    private lateinit var processManager: ProcessManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val filesDir = applicationContext.filesDir.absolutePath
        val nativeLibDir = applicationContext.applicationInfo.nativeLibraryDir

        bootstrapManager = BootstrapManager(applicationContext, filesDir, nativeLibDir)
        processManager = ProcessManager(filesDir, nativeLibDir)

        // Ensure directories and resolv.conf exist on every app start
        Thread {
            try { bootstrapManager.setupDirectories() } catch (_: Exception) {}
            try { bootstrapManager.writeResolvConf() } catch (_: Exception) {}
        }.start()

        try { startForegroundService(Intent(this, ArchClawService::class.java)) } catch (_: Exception) {}

        setupCard = findViewById(R.id.setupCard)
        authCard = findViewById(R.id.authCard)
        authStatusText = findViewById(R.id.authStatusText)
        authButton = findViewById(R.id.oauthButton)
        qwenCodeButton = findViewById(R.id.qwenCodeButton)
        zeroClawButton = findViewById(R.id.zeroClawButton)
        openClawButton = findViewById(R.id.openClawButton)
        aiderButton = findViewById(R.id.aiderButton)
        claudeButton = findViewById(R.id.claudeButton)
        geminiButton = findViewById(R.id.geminiButton)
        terminalButton = findViewById(R.id.terminalButton)

        setupCard.setOnClickListener { startActivity(Intent(this, SetupWizardActivity::class.java)) }
        authCard.setOnClickListener { startOAuth() }
        authButton.setOnClickListener { startOAuth() }
        terminalButton.setOnClickListener { startActivity(Intent(this, TerminalActivity::class.java)) }

        qwenCodeButton.setOnClickListener { launchTool("qwen") }
        zeroClawButton.setOnClickListener { launchTool("zeroclaw") }
        openClawButton.setOnClickListener { launchTool("openclaw") }
        aiderButton.setOnClickListener { launchTool("aider") }
        claudeButton.setOnClickListener { launchTool("claude") }
        geminiButton.setOnClickListener { launchTool("gemini") }

        updateAuthStatus()
    }

    override fun onResume() {
        super.onResume()
        updateAuthStatus()
        if (ArchClawApp.instance.isSetupComplete()) setupCard.visibility = View.GONE
    }

    private fun startOAuth() {
        startActivityForResult(Intent(this, OAuthWebViewActivity::class.java), 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            Toast.makeText(this, "✓ Authenticated!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchTool(toolId: String) {
        if (!ArchClawApp.instance.isSetupComplete()) {
            Toast.makeText(this, "Complete setup first", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, SetupWizardActivity::class.java))
            return
        }

        val qwenTools = listOf("qwen", "zeroclaw", "openclaw", "aider")
        if (toolId in qwenTools) {
            val token = ArchClawApp.instance.getQwenOAuthToken()
            if (token == null || ArchClawApp.instance.isQwenOAuthExpired()) {
                Toast.makeText(this, "Import token first: run setup-qwen-token.sh in Termux", Toast.LENGTH_LONG).show()
                startOAuth()
                return
            }
        }

        try {
            val env = mutableMapOf<String, String>()
            if (toolId in qwenTools) {
                ArchClawApp.instance.getQwenOAuthToken()?.let { env["QWEN_ACCESS_TOKEN"] = it }
            }
            processManager.startProotProcess(buildToolCommand(toolId, env))
            Toast.makeText(this, "Started $toolId", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildToolCommand(toolId: String, env: Map<String, String>): String {
        val command = when (toolId) {
            "qwen" -> "qwen"
            "zeroclaw" -> "zeroclaw start"
            "openclaw" -> "openclaw start"
            "aider" -> "aider"
            else -> toolId
        }
        val envStr = env.map { (k, v) -> "$k=$v" }.joinToString(" ")
        return "env $envStr bash -c \"$command\""
    }

    private fun updateAuthStatus() {
        val app = ArchClawApp.instance
        val token = app.getQwenOAuthToken()
        if (token != null && !app.isQwenOAuthExpired()) {
            authStatusText.text = "✓ Qwen OAuth Active"
            authStatusText.setTextColor(getColor(R.color.success_green))
            authButton.text = "Re-authenticate"
        } else {
            authStatusText.text = "Not authenticated"
            authStatusText.setTextColor(getColor(R.color.text_secondary))
            authButton.text = "Login with Qwen"
        }
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, MainActivity::class.java)
    }
}

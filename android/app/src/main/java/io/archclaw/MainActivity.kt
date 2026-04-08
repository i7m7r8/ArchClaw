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
    private lateinit var terminalButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Start foreground service
        try {
            startForegroundService(Intent(this, ArchClawService::class.java))
        } catch (_: Exception) {}

        setupCard = findViewById(R.id.setupCard)
        authCard = findViewById(R.id.authCard)
        authStatusText = findViewById(R.id.authStatusText)
        authButton = findViewById(R.id.oauthButton)
        qwenCodeButton = findViewById(R.id.qwenCodeButton)
        terminalButton = findViewById(R.id.terminalButton)

        setupCard.setOnClickListener {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }

        authCard.setOnClickListener { startOAuth() }
        authButton.setOnClickListener { startOAuth() }

        qwenCodeButton.setOnClickListener {
            if (!ArchClawApp.instance.isSetupComplete()) {
                Toast.makeText(this, "Complete setup first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (ArchClawApp.instance.getQwenOAuthToken() == null) {
                Toast.makeText(this, "Login with Qwen first", Toast.LENGTH_SHORT).show()
                startOAuth()
                return@setOnClickListener
            }
            Toast.makeText(this, "Starting Qwen Code...", Toast.LENGTH_SHORT).show()
        }

        terminalButton.setOnClickListener {
            startActivity(Intent(this, TerminalActivity::class.java))
        }

        updateAuthStatus()
    }

    override fun onResume() {
        super.onResume()
        updateAuthStatus()
        // Hide setup card if complete
        if (ArchClawApp.instance.isSetupComplete()) {
            setupCard.visibility = View.GONE
        }
    }

    private fun startOAuth() {
        startActivityForResult(
            Intent(this, OAuthWebViewActivity::class.java),
            1001
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            Toast.makeText(this, "✓ Authenticated!", Toast.LENGTH_SHORT).show()
        }
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
}

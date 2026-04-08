package io.archclaw.terminal

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.archclaw.ArchClawApp
import io.archclaw.R
import io.archclaw.core.ProotManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Built-in Terminal Activity
 * Provides interactive shell access to Arch Linux environment
 */
class TerminalActivity : AppCompatActivity() {

    private lateinit var prootManager: ProotManager
    private lateinit var outputView: TextView
    private lateinit var inputView: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var clearButton: ImageButton
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        prootManager = (application as ArchClawApp).prootManager
        setupUI()
        
        // Show welcome message
        appendOutput("\n🐉 ArchClaw Terminal\n")
        appendOutput("Arch Linux environment ready.\n")
        appendOutput("Type 'exit' to close terminal.\n\n")
    }

    private fun setupUI() {
        outputView = findViewById(R.id.outputView)
        inputView = findViewById(R.id.inputView)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        scrollView = findViewById(R.id.scrollView)

        sendButton.setOnClickListener {
            executeCommand()
        }

        clearButton.setOnClickListener {
            outputView.text = ""
        }

        inputView.setOnEditorActionListener { _, _, _ ->
            executeCommand()
            true
        }
    }

    private fun executeCommand() {
        val command = inputView.text.toString().trim()
        if (command.isEmpty()) return

        inputView.setText("")
        appendOutput("$ ${command}\n")

        if (command == "exit") {
            finish()
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                prootManager.executeInRootfs(command)
            }
            
            if (result.output.isNotEmpty()) {
                appendOutput(result.output)
            }
            
            if (result.exitCode != 0) {
                appendOutput("\n[Exit code: ${result.exitCode}]\n")
            }
        }
    }

    private fun appendOutput(text: String) {
        runOnUiThread {
            outputView.append(text)
            scrollView.post {
                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
            }
        }
    }

    companion object {
        fun newIntent(context: android.content.Context) = 
            android.content.Intent(context, TerminalActivity::class.java)
    }
}

package io.archclaw.terminal

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.archclaw.ArchClawApp
import io.archclaw.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class TerminalActivity : AppCompatActivity() {

    private lateinit var outputView: TextView
    private lateinit var inputView: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var clearButton: ImageButton
    private lateinit var scrollView: ScrollView

    private var shellProcess: Process? = null
    private var shellWriter: OutputStreamWriter? = null
    private var shellReader: BufferedReader? = null
    private var readThread: Thread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        outputView = findViewById(R.id.outputView)
        inputView = findViewById(R.id.inputView)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        scrollView = findViewById(R.id.scrollView)

        sendButton.setOnClickListener { sendCommand() }
        clearButton.setOnClickListener { outputView.text = "" }
        inputView.setOnEditorActionListener { _, _, _ -> sendCommand(); true }

        startShell()
    }

    private fun startShell() {
        try {
            val app = ArchClawApp.instance
            if (!app.prootManager.isReady()) {
                appendOutput("""
🐉 ArchClaw Terminal
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Environment not set up yet.
Please run the setup wizard first.
""")
                return
            }

            shellProcess = app.prootManager.startInteractiveShell()
            shellWriter = OutputStreamWriter(shellProcess!!.outputStream)
            shellReader = BufferedReader(InputStreamReader(shellProcess!!.inputStream))

            appendOutput("🐉 ArchClaw Terminal (Arch Linux)\nType 'exit' to close.\n\n")

            // Start reader thread
            readThread = Thread {
                try {
                    val buffer = CharArray(8192)
                    var charsRead: Int
                    while (shellReader?.read(buffer).also { charsRead = it ?: -1 } != -1) {
                        val output = String(buffer, 0, charsRead)
                        runOnUiThread { appendOutput(output) }
                    }
                } catch (e: Exception) {
                    // Shell closed
                }
            }.apply { start() }

        } catch (e: Exception) {
            appendOutput("Error starting shell: ${e.message}\n")
        }
    }

    private fun sendCommand() {
        val command = inputView.text.toString().trim()
        if (command.isEmpty()) return
        inputView.setText("")

        if (command == "exit") {
            stopShell()
            finish()
            return
        }

        try {
            shellWriter?.write("$command\n")
            shellWriter?.flush()
        } catch (e: Exception) {
            appendOutput("Error: ${e.message}\n")
        }
    }

    private fun appendOutput(text: String) {
        outputView.append(text)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun stopShell() {
        try {
            shellWriter?.write("exit\n")
            shellWriter?.flush()
            shellWriter?.close()
            shellReader?.close()
            shellProcess?.waitFor()
        } catch (_: Exception) {}
        shellProcess?.destroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopShell()
    }

    override fun onBackPressed() {
        stopShell()
        super.onBackPressed()
    }

    companion object {
        fun newIntent(context: android.content.Context) =
            android.content.Intent(context, TerminalActivity::class.java)
    }
}

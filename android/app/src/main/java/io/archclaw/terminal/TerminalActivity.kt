package io.archclaw.terminal

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.archclaw.ArchClawApp
import io.archclaw.BootstrapManager
import io.archclaw.ProcessManager
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

    private lateinit var bootstrapManager: BootstrapManager
    private lateinit var processManager: ProcessManager

    private var shellProcess: Process? = null
    private var shellWriter: OutputStreamWriter? = null
    private var shellReader: BufferedReader? = null
    private var readThread: Thread? = null
    private val outputBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terminal)

        val filesDir = applicationContext.filesDir.absolutePath
        val nativeLibDir = applicationContext.applicationInfo.nativeLibraryDir

        bootstrapManager = BootstrapManager(applicationContext, filesDir, nativeLibDir)
        processManager = ProcessManager(filesDir, nativeLibDir)

        // Ensure directories exist
        bootstrapManager.setupDirectories()
        bootstrapManager.writeResolvConf()

        outputView = findViewById(R.id.outputView)
        inputView = findViewById(R.id.inputView)
        sendButton = findViewById(R.id.sendButton)
        clearButton = findViewById(R.id.clearButton)
        scrollView = findViewById(R.id.scrollView)

        sendButton.setOnClickListener { sendCommand() }
        clearButton.setOnClickListener { outputView.text = ""; outputBuffer.clear() }
        inputView.setOnEditorActionListener { _, _, _ -> sendCommand(); true }

        startShell()
    }

    private fun startShell() {
        try {
            if (!bootstrapManager.isBootstrapComplete()) {
                appendText("""🐉 ArchClaw Terminal
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Environment not set up yet.
Please complete the setup wizard first.

""")
                return
            }

            shellProcess = processManager.startProotProcess("/bin/bash -l")
            shellWriter = OutputStreamWriter(shellProcess!!.outputStream)
            shellReader = BufferedReader(InputStreamReader(shellProcess!!.inputStream))

            appendText("🐉 ArchClaw Terminal (Arch Linux)\nType 'exit' to close.\n\n")

            readThread = Thread {
                try {
                    val buffer = CharArray(8192)
                    var charsRead: Int
                    while (shellReader?.read(buffer).also { charsRead = it ?: -1 } != -1) {
                        val output = String(buffer, 0, charsRead)
                        runOnUiThread { appendText(output) }
                    }
                } catch (e: Exception) {
                    // Shell closed
                }
            }.apply { isDaemon = true; start() }

        } catch (e: Exception) {
            appendText("Error: ${e.message}\n")
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
            appendText("\nError: ${e.message}\n")
        }
    }

    private fun appendText(text: String) {
        outputBuffer.append(text)
        outputView.text = stripAnsi(outputBuffer.toString())
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001B\\[[;\\d]*[a-zA-Z]"), "")
            .replace(Regex("\u001B\\][^\u0007]*\u0007"), "")
            .replace("\u0007", "")
            .replace("\u001B", "")
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
        readThread?.interrupt()
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

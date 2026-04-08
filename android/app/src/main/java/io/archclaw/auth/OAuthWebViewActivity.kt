package io.archclaw.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import io.archclaw.ArchClawApp
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * OAuth Activity - Imports Qwen Code OAuth token
 * 
 * Since Android sandbox prevents reading Termux's private directory,
 * we use Storage Access Framework to let user select oauth_creds.json
 * from Termux home via Android's file picker.
 */
class OAuthWebViewActivity : AppCompatActivity() {

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            readTokenFromUri(uri)
        } else {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Try shared storage first
        if (tryReadFromSharedStorage()) return

        // Launch file picker for oauth_creds.json
        filePicker.launch(arrayOf("application/json"))
    }

    /**
     * Try reading from shared storage location (user copies token here)
     */
    private fun tryReadFromSharedStorage(): Boolean {
        val sharedPath = getExternalFilesDir(null)?.resolve("qwen_oauth.json")
            ?: return false
        if (!sharedPath.exists()) return false

        return try {
            val json = sharedPath.readText()
            parseAndSaveToken(json)
        } catch (_: Exception) {
            false
        }
    }

    private fun readTokenFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: run {
                showError("Cannot open file")
                return
            }
            val json = BufferedReader(InputStreamReader(inputStream)).readText()
            inputStream.close()

            if (parseAndSaveToken(json)) {
                Toast.makeText(this, "✓ Authenticated via Qwen Code!", Toast.LENGTH_LONG).show()
                setResult(Activity.RESULT_OK)
                finish()
            } else {
                showError("Invalid token file")
            }
        } catch (e: Exception) {
            showError("Read error: ${e.message}")
        }
    }

    private fun parseAndSaveToken(json: String): Boolean {
        val gson = Gson()
        val creds = gson.fromJson(json, QwenCreds::class.java)

        if (creds.access_token.isNullOrEmpty()) return false

        val expiryMs = creds.expiry_date ?: 0L
        if (System.currentTimeMillis() >= expiryMs) {
            showError("Token expired. Re-authenticate in Termux: qwen /auth")
            return false
        }

        ArchClawApp.instance.saveQwenOAuthToken(creds.access_token, expiryMs)
        return true
    }

    private fun showError(msg: String) {
        Toast.makeText(this, "✗ $msg", Toast.LENGTH_LONG).show()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    data class QwenCreds(
        val access_token: String?,
        val token_type: String?,
        val refresh_token: String?,
        val resource_url: String?,
        val expiry_date: Long?
    )
}

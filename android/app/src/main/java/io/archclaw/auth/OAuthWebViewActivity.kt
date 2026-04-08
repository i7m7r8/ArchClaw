package io.archclaw.auth

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import io.archclaw.ArchClawApp
import java.io.File

/**
 * OAuth Activity - Reads real Qwen Code OAuth token from Termux
 * 
 * Qwen Code saves OAuth creds at:
 * ~/.qwen/oauth_creds.json
 * 
 * We read it directly and use it for all AI tools.
 */
class OAuthWebViewActivity : AppCompatActivity() {

    companion object {
        private const val QWEN_CREDS_PATH = "/data/data/com.termux/files/home/.qwen/oauth_creds.json"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Try to read existing Qwen Code token first
        if (tryReadQwenCodeToken()) {
            return
        }

        // No token found - show error
        Toast.makeText(this, "No Qwen token found. Run 'qwen' in Termux first.", Toast.LENGTH_LONG).show()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun tryReadQwenCodeToken(): Boolean {
        val credsFile = File(QWEN_CREDS_PATH)
        if (!credsFile.exists()) return false

        try {
            val json = credsFile.readText()
            val gson = Gson()
            val creds = gson.fromJson(json, QwenCreds::class.java)

            if (creds.access_token.isNullOrEmpty()) return false

            val expiryMs = creds.expiry_date ?: 0L
            if (System.currentTimeMillis() >= expiryMs) return false

            // Save to our app storage
            ArchClawApp.instance.saveQwenOAuthToken(creds.access_token, expiryMs)

            Toast.makeText(this, "✓ Authenticated via Qwen Code!", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_OK)
            finish()
            return true

        } catch (e: Exception) {
            return false
        }
    }

    data class QwenCreds(
        val access_token: String?,
        val token_type: String?,
        val refresh_token: String?,
        val resource_url: String?,
        val expiry_date: Long?
    )
}

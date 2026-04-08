package io.archclaw.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.archclaw.ArchClawApp
import io.archclaw.R

class OAuthWebViewActivity : AppCompatActivity() {

    companion object {
        const val OAUTH_URL = "https://qwen.ai/oauth/authorize" +
                "?response_type=token" +
                "&client_id=qwen-code-cli" +
                "&redirect_uri=io.archclaw://oauth/callback" +
                "&scope=openid+profile+qwen-code-api"
        const val CALLBACK_PREFIX = "io.archclaw://oauth/callback"
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url.toString()
                    if (url.startsWith(CALLBACK_PREFIX)) {
                        handleOAuthCallback(url)
                        return true
                    }
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url?.contains("access_token=") == true) {
                        extractTokenFromUrl(url)
                    }
                }
            }
        }

        setContentView(webView)
        webView.loadUrl(OAUTH_URL)
    }

    private fun handleOAuthCallback(url: String) {
        val fragment = url.split("#").getOrNull(1)
        if (fragment != null) extractTokenFromFragment(fragment)
        else {
            Toast.makeText(this, "OAuth failed: no token", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    private fun extractTokenFromUrl(url: String) {
        val parts = url.split("#")
        if (parts.size > 1) extractTokenFromFragment(parts[1])
    }

    private fun extractTokenFromFragment(fragment: String) {
        val params = fragment.split("&").associate {
            val parts = it.split("=")
            if (parts.size == 2) parts[0] to parts[1] else "" to ""
        }

        val accessToken = params["access_token"]
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600

        if (accessToken != null) {
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)
            (application as ArchClawApp).saveQwenOAuthToken(accessToken, expiresAt)
            Toast.makeText(this, "✓ Qwen OAuth authenticated!", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "OAuth failed: no access token", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

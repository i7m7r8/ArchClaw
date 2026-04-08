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

/**
 * Qwen OAuth WebView Activity
 * Opens qwen.ai OAuth login in embedded WebView
 * Captures callback to extract token
 */
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
        setContentView(R.layout.activity_oauth_webview)

        setupWebView()
        loadOAuthPage()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowContentAccess = true
                allowFileAccess = true
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    
                    // Check for OAuth callback
                    if (url.startsWith(CALLBACK_PREFIX)) {
                        handleOAuthCallback(url)
                        return true
                    }
                    
                    return false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Check URL for hash fragment (token in URL)
                    if (url?.contains("access_token=") == true) {
                        extractTokenFromUrl(url)
                    }
                }
            }
        }

        setContentView(webView)
    }

    private fun loadOAuthPage() {
        webView.loadUrl(OAUTH_URL)
    }

    private fun handleOAuthCallback(url: String) {
        // Parse URL: io.archclaw://oauth/callback#access_token=XXX&expires_in=3600
        val fragment = url.split("#").getOrNull(1)
        if (fragment != null) {
            extractTokenFromFragment(fragment)
        } else {
            Toast.makeText(this, "OAuth failed: no token in callback", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun extractTokenFromUrl(url: String) {
        // Handle URL with hash fragment
        val parts = url.split("#")
        if (parts.size > 1) {
            extractTokenFromFragment(parts[1])
        }
    }

    private fun extractTokenFromFragment(fragment: String) {
        val params = fragment.split("&").associate { 
            val (key, value) = it.split("=")
            key to value
        }

        val accessToken = params["access_token"]
        val expiresIn = params["expires_in"]?.toLongOrNull() ?: 3600

        if (accessToken != null) {
            // Save token
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
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}

package com.marueat.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.marueat.app.databinding.ActivityWebviewLoginBinding

class WebViewLoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewLoginBinding
    private var completed = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        window.enterTransition = com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.Z, true)
        window.returnTransition = com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.Z, false)
        super.onCreate(savedInstanceState)

        binding = ActivityWebviewLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            insets
        }
        
        // Make status bar icons light to contrast with colorPrimary background
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.settings.javaScriptEnabled = true
        binding.webView.settings.domStorageEnabled = true
        binding.webView.settings.javaScriptCanOpenWindowsAutomatically = true

        binding.webView.addJavascriptInterface(AuthBridge(), "AndroidAuthBridge")
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
                binding.progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(
                    TAG,
                    "Console[${consoleMessage.messageLevel()}] ${consoleMessage.message()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                )
                return super.onConsoleMessage(consoleMessage)
            }
        }
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.visibility = View.VISIBLE
                binding.errorTextView.visibility = View.GONE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.visibility = View.GONE
                
                // 自动预填
                val user = intent.getStringExtra("PREFILL_USER").orEmpty()
                val pass = intent.getStringExtra("PREFILL_PASS").orEmpty()
                if (user.isNotEmpty() || pass.isNotEmpty()) {
                    binding.webView.evaluateJavascript("window.prefill('$user', '$pass')", null)
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    showError("加载 portal 登录页失败：${error?.description ?: "未知错误"}")
                }
            }
        }

        binding.webView.loadUrl(LOCAL_AUTHING_PAGE_URL)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        binding.webView.removeJavascriptInterface("AndroidAuthBridge")
        binding.webView.destroy()
        super.onDestroy()
    }

    private fun completeWithToken(token: String) {
        val normalized = token.trim()
        if (normalized.isBlank() || completed) {
            return
        }
        completed = true
        Log.i(TAG, "Captured portal token: ${normalized.take(16)}...")
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_AUTH_TOKEN, normalized)
        )
        finish()
    }

    private fun showError(message: String) {
        if (completed) {
            return
        }
        Log.e(TAG, message)
        binding.errorTextView.visibility = View.VISIBLE
        binding.errorTextView.text = message
    }

    inner class AuthBridge {
        private fun getColorHex(attrResId: Int): String {
            val resolvedColor = com.google.android.material.color.MaterialColors.getColor(
                this@WebViewLoginActivity, attrResId, android.graphics.Color.BLACK
            )
            return String.format("#%06X", 0xFFFFFF and resolvedColor)
        }

        @JavascriptInterface
        fun getColors(): String {
            val map = mapOf(
                "--md-sys-color-primary" to getColorHex(com.google.android.material.R.attr.colorPrimary),
                "--md-sys-color-on-primary" to getColorHex(com.google.android.material.R.attr.colorOnPrimary),
                "--md-sys-color-surface" to getColorHex(com.google.android.material.R.attr.colorSurface),
                "--md-sys-color-on-surface" to getColorHex(com.google.android.material.R.attr.colorOnSurface),
                "--md-sys-color-surface-variant" to getColorHex(com.google.android.material.R.attr.colorSurfaceVariant),
                "--md-sys-color-on-surface-variant" to getColorHex(com.google.android.material.R.attr.colorOnSurfaceVariant),
                "--md-sys-color-outline" to getColorHex(com.google.android.material.R.attr.colorOutline),
                "--md-sys-color-error" to getColorHex(com.google.android.material.R.attr.colorError),
                "--md-sys-color-background" to getColorHex(android.R.attr.colorBackground)
            )
            return org.json.JSONObject(map).toString()
        }

        @JavascriptInterface
        fun onToken(token: String) {
            runOnUiThread {
                completeWithToken(token)
            }
        }

        @JavascriptInterface
        fun onError(message: String) {
            runOnUiThread {
                showError(message)
            }
        }

        @JavascriptInterface
        fun onLog(message: String) {
            Log.d(TAG, message)
        }
    }

    companion object {
        private const val TAG = "WebViewLogin"
        private const val LOCAL_AUTHING_PAGE_URL = "file:///android_asset/authing_login.html"
        const val EXTRA_AUTH_TOKEN = "auth_token"
    }
}


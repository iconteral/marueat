package com.marueat.app

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialFade
import com.marueat.app.databinding.ActivityMainBinding
import com.marueat.app.util.QrBitmapFactory

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private var isTokenVisible = false

    private val webViewLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            return@registerForActivityResult
        }
        val token = result.data?.getStringExtra(WebViewLoginActivity.EXTRA_AUTH_TOKEN).orEmpty()
        viewModel.onWebViewTokenReceived(token)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.requestFeature(android.view.Window.FEATURE_ACTIVITY_TRANSITIONS)
        window.exitTransition = com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.Z, true)
        window.reenterTransition = com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.Z, false)
        
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, 0, systemBars.right, 0)
            binding.appBarLayout.setPadding(0, systemBars.top, 0, 0)
            binding.mainContainer.setPadding(
                binding.mainContainer.paddingLeft,
                binding.mainContainer.paddingTop,
                binding.mainContainer.paddingRight,
                systemBars.bottom + 48 // Extra padding for aesthetics
            )
            insets
        }
        
        // Status bar icons should be light (white) since toolbar has colorPrimary background
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        
        setSupportActionBar(binding.toolbar)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        restoreCredentials()
        bindActions()
        observeState()
    }

    private fun bindActions() {
        val scrollInterceptor = android.view.View.OnTouchListener { v, event ->
            if (v.isFocused) {
                v.parent.requestDisallowInterceptTouchEvent(true)
                if ((event.action and android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP || 
                    (event.action and android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_CANCEL) {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
            }
            false
        }
        binding.tokenEditText.setOnTouchListener(scrollInterceptor)
        binding.rawTokenTextView.apply {
            setOnTouchListener(scrollInterceptor)
            movementMethod = android.text.method.ScrollingMovementMethod()
            isFocusable = true
            isFocusableInTouchMode = true
        }

        binding.nativeLoginButton.setOnClickListener {
            val username = binding.usernameEditText.text?.toString().orEmpty()
            val password = binding.passwordEditText.text?.toString().orEmpty()
            saveCredentials(username, password)
            
            val intent = Intent(this, WebViewLoginActivity::class.java).apply {
                putExtra("PREFILL_USER", username)
                putExtra("PREFILL_PASS", password)
            }
            webViewLoginLauncher.launch(intent)
        }

        binding.importTokenButton.setOnClickListener {
            viewModel.onTokenImportRequested(
                binding.tokenEditText.text?.toString().orEmpty().trim()
            )
        }

        binding.refreshButton.setOnClickListener {
            viewModel.onRefreshRequested()
        }

        binding.cardRefreshButton.setOnClickListener {
            viewModel.onRefreshRequested()
        }

        binding.logoutButton.setOnClickListener {
            binding.tokenEditText.text?.clear()
            clearWebLoginState()
            viewModel.onLogoutRequested()
        }

        binding.qrImageView.setOnClickListener {
            val bitmap = (binding.qrImageView.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            if (bitmap != null) {
                showQrPreviewDialog(bitmap)
            }
        }

        binding.toggleTokenVisibilityButton.setOnClickListener {
            isTokenVisible = !isTokenVisible
            binding.toggleTokenVisibilityButton.setIconResource(
                if (isTokenVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
            )
            updateTokenDisplay(viewModel.uiState.value?.portalToken)
        }

        binding.copyTokenButton.setOnClickListener {
            val token = viewModel.uiState.value?.portalToken
            if (!token.isNullOrBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("认证 Token", token)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Token 已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeState() {
        viewModel.uiState.observe(this) { state ->
            TransitionManager.beginDelayedTransition(binding.mainContainer, MaterialFade().apply {
                duration = 200
            })

//            binding.noticeTextView.text = state.featureNotice
            binding.statusTextView.text = state.statusMessage
            binding.sessionTextView.text = state.sessionSummary
            binding.qrTextView.text = state.qrSummary
            
            binding.loadingOverlay.visibility = if (state.isLoading) View.VISIBLE else View.GONE

            val bitmap = state.qrBitmap ?: QrBitmapFactory.placeholderBitmap()
            binding.qrImageView.setImageBitmap(bitmap)

            updateTokenDisplay(state.portalToken)
        }
    }

    private fun updateTokenDisplay(token: String?) {
        if (!token.isNullOrBlank()) {
            binding.rawTokenTextView.text = if (isTokenVisible) token else "•".repeat(token.length.coerceAtMost(100)) + if (token.length > 100) "..." else ""
            binding.rawTokenCardView.visibility = View.VISIBLE
        } else {
            binding.rawTokenCardView.visibility = View.GONE
        }
    }

    private fun saveCredentials(user: String, pass: String) {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("username", user).putString("password", pass).apply()
    }

    private fun restoreCredentials() {
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        binding.usernameEditText.setText(prefs.getString("username", ""))
        binding.passwordEditText.setText(prefs.getString("password", ""))
    }

    private fun clearWebLoginState() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
    }

    private fun showQrPreviewDialog(bitmap: Bitmap) {
        val dialog = Dialog(this, android.R.style.Theme_Light_NoTitleBar_Fullscreen)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.WHITE))
            val lp = attributes
            lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            attributes = lp
            
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(this, false)
            androidx.core.view.WindowInsetsControllerCompat(this, decorView).let { controller ->
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            alpha = 0f
            scaleX = 0.8f
            scaleY = 0.8f
        }
        
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250)
                    .setInterpolator(android.view.animation.OvershootInterpolator()).start()
            }
            override fun onViewDetachedFromWindow(v: View) {}
        })

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        val dismissAction = {
            container.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(200)
                .withEndAction { dialog.dismiss() }.start()
        }

        imageView.setOnClickListener { dismissAction() }

        container.setOnClickListener {
            if (it === container) { dismissAction() }
        }

        container.addView(imageView)
        dialog.setContentView(container)
        dialog.show()
    }
}

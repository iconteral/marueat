package com.marueat.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.marueat.app.data.CafeteriaRepository
import com.marueat.app.data.CafeteriaSnapshot
import com.marueat.app.util.QrBitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FEATURE_NOTICE = "当前版本直接请求线上接口。登录入口已切换为稳定登录页流程；如遇风控可改用 token 导入。"

data class MainUiState(
    val featureNotice: String = FEATURE_NOTICE,
    val statusMessage: String = "客户端已就绪。",
    val sessionSummary: String = "未建立线上会话。",
    val qrSummary: String = "尚未生成二维码。",
    val isLoading: Boolean = false,
    val qrBitmap: android.graphics.Bitmap? = null,
    val portalToken: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CafeteriaRepository(application.applicationContext)

    private val _uiState = MutableLiveData(MainUiState())
    val uiState: LiveData<MainUiState> = _uiState

    init {
        restoreSessionIfNeeded()
    }

    fun onNativeDirectLoginRequested(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            updateStatus("请输入用户名和密码。")
            return
        }

        runRequest("正在尝试原生直连登录...") {
            repository.loginWithNativeCredentials(username, password)
        }
    }

    fun onTokenImportRequested(token: String) {
        if (token.isBlank()) {
            updateStatus("请先粘贴 token。")
            return
        }

        runRequest("正在导入 token...") {
            repository.loginWithImportedToken(token)
        }
    }

    fun onWebViewTokenReceived(token: String) {
        if (token.isBlank()) {
            updateStatus("WebView 未返回 token。")
            return
        }

        runRequest("正在处理 WebView 登录结果...") {
            repository.loginWithBrowserToken(token)
        }
    }

    fun onRefreshRequested() {
        runRequest("正在刷新会话和二维码...") {
            repository.refresh()
        }
    }

    fun onLogoutRequested() {
        repository.logout()
        _uiState.value = MainUiState(statusMessage = "已退出登录。")
    }

    private fun restoreSessionIfNeeded() {
        if (!repository.hasSession()) {
            return
        }
        runRequest("正在恢复已保存会话...") {
            repository.restoreSession() ?: throw IllegalStateException("未找到可恢复的会话")
        }
    }

    private fun runRequest(loadingMessage: String, block: () -> CafeteriaSnapshot) {
        val current = _uiState.value ?: MainUiState()
        // 加载时保留当前的 qrBitmap
        _uiState.value = current.copy(statusMessage = loadingMessage, isLoading = true)

        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { block() }
                // 在后台线程生成 Bitmap
                val qrBitmap = withContext(Dispatchers.Default) {
                    QrBitmapFactory.bitmapFromText(snapshot.qrCode)
                }
                
                val latest = _uiState.value ?: MainUiState()
                _uiState.value = latest.copy(
                    statusMessage = "成功更新。",
                    sessionSummary = (snapshot.session.summaryLines() + snapshot.profile.summaryLines()).joinToString("\n"),
                    qrSummary = snapshot.qrCode,
                    qrBitmap = qrBitmap,
                    portalToken = snapshot.portalToken,
                    isLoading = false
                )
            } catch (error: Exception) {
                val latest = _uiState.value ?: MainUiState()
                _uiState.value = latest.copy(
                    statusMessage = error.message ?: "发生未知错误",
                    sessionSummary = if (error.message.isNullOrBlank()) latest.sessionSummary else error.message!!,
                    isLoading = false
                )
            }
        }
    }

    private fun updateStatus(message: String) {
        val currentState = _uiState.value ?: MainUiState()
        _uiState.value = currentState.copy(statusMessage = message)
    }
}

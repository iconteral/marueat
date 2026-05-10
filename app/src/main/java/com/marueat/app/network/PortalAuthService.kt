package com.marueat.app.network

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val TAG = "PortalAuthService"

private data class NativeLoginAttempt(
    val url: String,
    val httpCode: Int? = null,
    val responsePreview: String = "",
    val businessCode: String = "",
    val businessMessage: String = "",
    val tokenExtracted: Boolean = false,
    val exceptionMessage: String = ""
)

class PortalAuthService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    fun loginByAccount(username: String, password: String): String {
        require(username.isNotBlank()) { "请输入用户名" }
        require(password.isNotBlank()) { "请输入密码" }

        val attempts = mutableListOf<NativeLoginAttempt>()
        for (url in CANDIDATE_URLS) {
            try {
                val payload = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .put("appId", PORTAL_APP_ID)
                    .toString()
                    .toRequestBody(JSON_MEDIA_TYPE)

                val request = Request.Builder()
                    .url(url)
                    .post(payload)
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val token = extractToken(body)
                    val attempt = NativeLoginAttempt(
                        url = url,
                        httpCode = response.code,
                        responsePreview = body.preview(),
                        businessCode = extractBusinessCode(body),
                        businessMessage = extractBusinessMessage(body),
                        tokenExtracted = !token.isNullOrBlank()
                    )
                    attempts += attempt
                    Log.d(TAG, attempt.toLogLine())
                    if (response.isSuccessful && !token.isNullOrBlank()) {
                        Log.i(TAG, "Native direct login succeeded via $url")
                        return token
                    }
                }
            } catch (error: Exception) {
                val attempt = NativeLoginAttempt(
                    url = url,
                    exceptionMessage = error.message ?: error::class.java.simpleName
                )
                attempts += attempt
                Log.e(TAG, attempt.toLogLine(), error)
            }
        }

        val report = buildFailureReport(attempts)
        Log.e(TAG, report)
        throw IllegalStateException(report)
    }

    private fun buildFailureReport(attempts: List<NativeLoginAttempt>): String = buildString {
        appendLine("原生直连换取 token 失败。")
        appendLine("")
        appendLine("结论：")
        appendLine(buildOverallConclusion(attempts))
        appendLine("")
        appendLine("逐接口日志：")
        attempts.forEachIndexed { index, attempt ->
            appendLine("${index + 1}. ${attempt.toLogLine()}")
            appendLine("   判断: ${explainAttempt(attempt)}")
        }
        appendLine("")
        append("建议：如果 WebView 登录能成功而原生直连失败，说明浏览器 SDK 路径可用，但当前这些 REST 登录端点与这个 appId/租户配置并不兼容；这通常不是单纯的密码错误。")
    }

    private fun buildOverallConclusion(attempts: List<NativeLoginAttempt>): String {
        if (attempts.any { it.tokenExtracted }) {
            return "已有候选接口返回 token。"
        }
        if (attempts.any { it.businessMessage.contains("用户池不存在") }) {
            return "候选接口已经打通到 Authing 风格后端，但后端返回\"用户池不存在\"。这更像是 appId 对应的用户池/租户映射不匹配，或该租户不支持这类原生 REST 密码登录。"
        }
        if (attempts.any { it.httpCode == 404 }) {
            return "至少一个候选 URL 在当前 host 上不存在，说明参考里的 REST 路径并不完全适用于当前环境。"
        }
        if (attempts.any { it.httpCode == 401 || it.httpCode == 403 }) {
            return "候选接口拒绝了当前鉴权方式；可能需要浏览器 SDK、额外签名参数，或服务端策略不允许直接密码换 token。"
        }
        return "所有候选原生直连接口都未返回可用 token。更可能是接口形态或租户配置不匹配，而不是 Android 端网络请求代码本身崩溃。"
    }

    private fun explainAttempt(attempt: NativeLoginAttempt): String {
        if (attempt.exceptionMessage.isNotBlank()) {
            return "请求阶段就抛异常：${attempt.exceptionMessage}"
        }
        if (attempt.tokenExtracted) {
            return "该接口返回了可用 token。"
        }
        if (attempt.httpCode == 404) {
            return "这个 URL 不存在，说明它不是当前环境可用的登录入口。"
        }
        if (attempt.businessMessage.contains("用户池不存在")) {
            return "HTTP 已到达服务端，但业务层返回用户池不存在。常见原因是 appId 不属于这个接口期望的用户池，或这个租户只能走浏览器 SDK 登录。"
        }
        if (attempt.httpCode == 200 && attempt.businessMessage.isNotBlank()) {
            return "HTTP 成功，但业务层明确报错，所以不是网络不通，而是服务端拒绝按当前方式签发 token。"
        }
        if (attempt.httpCode == 401 || attempt.httpCode == 403) {
            return "服务端拒绝了当前登录方式或当前凭据。"
        }
        return "接口可达，但没有返回可用 token；需要结合响应体继续判断。"
    }

    private fun NativeLoginAttempt.toLogLine(): String = buildString {
        append(url)
        if (httpCode != null) {
            append(" -> HTTP ")
            append(httpCode)
        }
        if (businessCode.isNotBlank()) {
            append(", code=")
            append(businessCode)
        }
        if (businessMessage.isNotBlank()) {
            append(", message=")
            append(businessMessage)
        }
        if (tokenExtracted) {
            append(", token=present")
        }
        if (exceptionMessage.isNotBlank()) {
            append(", exception=")
            append(exceptionMessage)
        }
        if (responsePreview.isNotBlank()) {
            append(", body=")
            append(responsePreview)
        }
    }

    private fun extractToken(body: String): String? {
        if (body.isBlank()) {
            return null
        }

        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        TOKEN_KEYS.firstNotNullOfOrNull { key ->
            root.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }?.let { return it }

        val nested = root.optJSONObject("data") ?: return null
        return TOKEN_KEYS.firstNotNullOfOrNull { key ->
            nested.optString(key).takeIf { it.isNotBlank() && it != "null" }
        }
    }

    private fun extractBusinessCode(body: String): String {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return ""
        return listOf("code", "apiCode", "statusCode")
            .firstNotNullOfOrNull { key -> root.opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" } }
            .orEmpty()
    }

    private fun extractBusinessMessage(body: String): String {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return ""
        return listOf("message", "msg", "error_description", "error")
            .firstNotNullOfOrNull { key -> root.optString(key).takeIf { it.isNotBlank() && it != "null" } }
            .orEmpty()
    }

    private fun String.preview(limit: Int = 220): String =
        replace("\n", " ")
            .replace("\r", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(limit)

    companion object {
        // TODO: 替换为你的 Authing App ID
        private const val PORTAL_APP_ID = "YOUR_AUTHING_APP_ID"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val TOKEN_KEYS = listOf("token", "id_token", "idToken", "access_token", "accessToken")
        // TODO: 替换为你的认证服务地址
        private val CANDIDATE_URLS = listOf(
            "https://YOUR_AUTH_HOST/api/v2/login/account",
            "https://YOUR_AUTH_HOST/api/v2/signin",
            "https://YOUR_AUTH_HOST/api/v2/users/login"
        )
    }
}

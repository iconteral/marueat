package com.marueat.app.network

import com.marueat.app.data.model.CafeteriaSession
import com.marueat.app.util.LinkThirdAppParser
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class CafeteriaProfile(
    val userName: String,
    val mobile: String,
    val supplierName: String,
    val orgName: String,
    val cashBalance: String,
    val supplementBalanceName: String,
    val className: String,
    val supplierUserId: String,
    val appConfPreview: String
) {
    fun summaryLines(): List<String> = buildList {
        add("姓名: ${userName.ifBlank { "-" }}")
        add("手机号: ${mobile.ifBlank { "-" }}")
        add("供应商: ${supplierName.ifBlank { "-" }}")
        add("组织: ${orgName.ifBlank { "-" }}")
        add("账户余额: ${cashBalance.ifBlank { "-" }}")
        add("补贴账户: ${supplementBalanceName.ifBlank { "-" }}")
        add("卡类型: ${className.ifBlank { "-" }}")
        add("supplierUserId: ${supplierUserId.ifBlank { "-" }}")
        if (appConfPreview.isNotBlank()) {
            add("appConf: $appConfPreview")
        }
    }
}

class CafeteriaApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
) {
    fun bootstrapSession(authToken: String, authUuid: String): CafeteriaSession {
        val code = exchangeFoodCode(authToken)
        val loginUrl = loginToMankebao(authUuid, code)
        val parsed = LinkThirdAppParser.parse(loginUrl)
        return CafeteriaSession(
            authToken = authToken,
            authUuid = authUuid,
            code = code,
            mkbToken = parsed.mkbToken,
            supplierUserId = parsed.supplierUserId,
            supplierId = parsed.supplierId,
            supplierName = parsed.supplierName,
            supplierCode = parsed.supplierCode,
            orderSource = parsed.orderSource
        )
    }

    fun fetchProfile(session: CafeteriaSession): CafeteriaProfile {
        val profileRequest = Request.Builder()
            .url("$MKB_BASE/base/api/v1/getSupplierUserMine")
            .post(EMPTY_BODY)
            .headers(mkbHeaders(session))
            .build()

        val profileText = client.newCall(profileRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("获取用户信息失败：HTTP ${response.code} ${body.take(200)}")
            }
            body
        }

        val profileJson = unwrap(runCatching { JSONObject(profileText) }.getOrElse {
            throw IllegalStateException("用户信息响应不是合法 JSON")
        })

        val appConfRequest = Request.Builder()
            .url("$MKB_BASE/base/api/v1/module/appConf")
            .post(
                FormBody.Builder()
                    .add("supplierCode", session.supplierCode)
                    .add("appType", APP_TYPE)
                    .build()
            )
            .headers(mkbHeaders(session))
            .build()

        val appConfPreview = client.newCall(appConfRequest).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@use ""
            }
            summarizeAppConf(body)
        }

        return CafeteriaProfile(
            userName = stringValue(profileJson, "userName"),
            mobile = stringValue(profileJson, "mobile"),
            supplierName = stringValue(profileJson, "supplierName").ifBlank { session.supplierName },
            orgName = stringValue(profileJson, "orgName"),
            cashBalance = anyValue(profileJson, "cashBalance"),
            supplementBalanceName = stringValue(profileJson, "supplementOneBalanceName"),
            className = stringValue(profileJson, "className"),
            supplierUserId = stringValue(profileJson, "supplierUserId").ifBlank { session.supplierUserId },
            appConfPreview = appConfPreview
        )
    }

    fun fetchQrCode(session: CafeteriaSession): String {
        val request = Request.Builder()
            .url("$MKB_BASE/base/api/v1/supplierUser/getMineQrCode")
            .post(EMPTY_BODY)
            .headers(mkbHeaders(session))
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                throw IllegalStateException("获取二维码失败：HTTP ${response.code} ${body.take(200)}")
            }
            if (body.isBlank()) {
                throw IllegalStateException("二维码响应为空")
            }
            body
        }
    }

    private fun exchangeFoodCode(authToken: String): String {
        val request = Request.Builder()
            .url("$APP_API_BASE/ext/redirect/getCode?clientId=$CLIENT_ID")
            .get()
            .header("Authorization", "Bearer $authToken")
            .header("X-Requested-With", "com.your.app")
            .build()

        return client.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
                if (response.code !in REDIRECT_STATUS_CODES) {
                    val body = response.body?.string().orEmpty()
                    throw IllegalStateException("换取食堂模块 code 失败：HTTP ${response.code} ${body.take(200)}")
                }
                val location = response.header("Location").orEmpty()
                extractQueryValue(location, "code")
                    ?: throw IllegalStateException("未从重定向中提取到 code")
            }
    }

    private fun loginToMankebao(authUuid: String, code: String): String {
        val request = Request.Builder()
            .url("$MKB_BASE/base/api/v1/wkb/loginNew")
            .post(
                FormBody.Builder()
                    .add("tUserId", authUuid)
                    .add("code", code)
                    .build()
            )
            .header("Origin", MKB_BASE)
            .header("Referer", "$MKB_BASE/reserve/")
            .header("X-Requested-With", "com.your.app")
            .build()

        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty().trim()
            if (!response.isSuccessful) {
                throw IllegalStateException("登录食堂子系统失败：HTTP ${response.code} ${body.take(200)}")
            }
            if (!body.contains("mkbToken=")) {
                throw IllegalStateException("食堂登录响应中未包含 mkbToken")
            }
            body
        }
    }

    private fun mkbHeaders(session: CafeteriaSession) = okhttp3.Headers.Builder()
        .add("Authorization", session.mkbToken)
        .add("Origin", MKB_BASE)
        .add("Referer", "$MKB_BASE/reserve/")
        .add("X-Requested-With", "com.your.app")
        .build()

    private fun unwrap(json: JSONObject): JSONObject = json.optJSONObject("data") ?: json

    private fun summarizeAppConf(body: String): String {
        if (body.isBlank()) {
            return ""
        }

        val json = runCatching { JSONObject(body) }.getOrNull() ?: return body.replace("\n", " ").take(120)
        val target = unwrap(json)
        return listOf("supplierCode", "appType", "title", "name")
            .mapNotNull { key -> target.optString(key).takeIf { it.isNotBlank() && it != "null" } }
            .joinToString(" / ")
            .ifBlank { body.replace("\n", " ").take(120) }
    }

    private fun stringValue(json: JSONObject, key: String): String =
        json.optString(key).takeUnless { it == "null" }.orEmpty()

    private fun anyValue(json: JSONObject, key: String): String {
        val value = json.opt(key) ?: return ""
        return if (value == JSONObject.NULL) "" else value.toString()
    }

    private fun extractQueryValue(url: String, key: String): String? {
        if (url.isBlank()) {
            return null
        }
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        val query = parsed.rawQuery.orEmpty()
        if (query.isBlank()) {
            return null
        }
        return query.split('&')
            .map { it.substringBefore('=') to it.substringAfter('=', "") }
            .firstOrNull { it.first == key }
            ?.second
            ?.let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
            ?.takeIf { it.isNotBlank() }
    }

    companion object {
        // TODO: 替换为你的主业务API地址
        private const val APP_API_BASE = "https://YOUR_APP_API_HOST"
        // TODO: 替换为你的食堂子系统地址
        private const val MKB_BASE = "https://YOUR_CAFETERIA_HOST"
        // TODO: 替换为你的客户端ID
        private const val CLIENT_ID = "YOUR_CLIENT_ID"
        private const val APP_TYPE = "YUDING"
        private val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
        private val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
    }
}

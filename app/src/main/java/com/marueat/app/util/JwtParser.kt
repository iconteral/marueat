package com.marueat.app.util

import okio.ByteString.Companion.decodeBase64
import org.json.JSONObject

object JwtParser {
    fun summary(token: String): String {
        if (token.isBlank()) {
            return "empty"
        }
        val preview = token.take(12)
        return "token:$preview..."
    }

    fun extractUserId(token: String): String {
        val parts = token.split('.')
        require(parts.size == 3) { "不是标准 JWT" }

        val payloadText = try {
            val normalized = parts[1]
                .replace('-', '+')
                .replace('_', '/')
                .let { encoded -> encoded + "=".repeat((4 - encoded.length % 4) % 4) }
            normalized.decodeBase64()?.utf8()
                ?: throw IllegalArgumentException("无法解析 token: 非法 base64")
        } catch (error: Exception) {
            throw IllegalArgumentException("无法解析 token: ${error.message}", error)
        }

        val payload = try {
            JSONObject(payloadText)
        } catch (error: Exception) {
            throw IllegalArgumentException("无法解析 token: ${error.message}", error)
        }

        payload.optJSONObject("data")?.let { data ->
            listOf("id", "userId", "_id")
                .firstNotNullOfOrNull { key -> data.optString(key).takeIf { it.isNotBlank() && it != "null" } }
                ?.let { return it }
        }

        listOf("sub", "id", "userId", "uid")
            .firstNotNullOfOrNull { key -> payload.optString(key).takeIf { it.isNotBlank() && it != "null" } }
            ?.let { return it }

        throw IllegalArgumentException("token 中未找到用户 ID")
    }
}


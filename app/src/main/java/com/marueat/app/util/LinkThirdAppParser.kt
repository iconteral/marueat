package com.marueat.app.util

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class LinkThirdAppPayload(
    val mkbToken: String,
    val supplierUserId: String,
    val supplierId: String,
    val supplierName: String,
    val supplierCode: String,
    val orderSource: String
)

object LinkThirdAppParser {
    fun containsMkbToken(url: String): Boolean = url.contains("mkbToken=")

    fun parse(url: String): LinkThirdAppPayload {
        val parsed = try {
            URI(url.trim())
        } catch (error: Exception) {
            throw IllegalArgumentException("食堂登录返回的跳转地址无效", error)
        }

        var query = parsed.rawQuery.orEmpty()
        val fragment = parsed.rawFragment.orEmpty()
        if (fragment.contains("?")) {
            query = fragment.substringAfter("?")
        }

        val values = parseQuery(query)
        val missing = REQUIRED_KEYS.filter { values[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException("食堂跳转参数缺失: ${missing.joinToString(", ")}")
        }

        return LinkThirdAppPayload(
            mkbToken = values.getValue("mkbToken"),
            supplierUserId = values.getValue("supplierUserId"),
            supplierId = values.getValue("supplierId"),
            supplierName = values.getValue("supplierName"),
            supplierCode = values.getValue("supplierCode"),
            orderSource = values.getValue("orderSource")
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) {
            return emptyMap()
        }
        return query.split('&')
            .filter { it.isNotBlank() }
            .associate { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                decode(key) to decode(value)
            }
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private val REQUIRED_KEYS = listOf(
        "mkbToken",
        "supplierUserId",
        "supplierId",
        "supplierName",
        "supplierCode",
        "orderSource"
    )
}


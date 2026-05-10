package com.marueat.app.data.model

data class CafeteriaSession(
    val authToken: String,
    val authUuid: String,
    val code: String,
    val mkbToken: String,
    val supplierUserId: String,
    val supplierId: String,
    val supplierName: String,
    val supplierCode: String,
    val orderSource: String
) {
    fun summaryLines(): List<String> = listOf(
        "authUuid: $authUuid",
        "supplierUserId: $supplierUserId",
        "supplierId: $supplierId",
        "supplierName: $supplierName",
        "supplierCode: $supplierCode",
        "orderSource: $orderSource"
    )
}


package com.marueat.app

import com.marueat.app.util.JwtParser
import com.marueat.app.util.LinkThirdAppParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun jwtSummaryShowsPreview() {
        assertEquals("token:abcdefghijkl...", JwtParser.summary("abcdefghijklmnop"))
    }

    @Test
    fun jwtParserExtractsNestedDataId() {
        val token = "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJkYXRhIjp7ImlkIjoiYXV0aC11dWlkLTEyMyJ9fQ."
        assertEquals("auth-uuid-123", JwtParser.extractUserId(token))
    }

    @Test
    fun linkParserDetectsMkbToken() {
        assertTrue(LinkThirdAppParser.containsMkbToken("https://example.com/#/linkThirdApp?mkbToken=value"))
    }

    @Test
    fun linkParserReadsFragmentQueryPayload() {
        val parsed = LinkThirdAppParser.parse(
            "https://example.com/reserve/#/linkThirdApp?mkbToken=mkb123&supplierUserId=user1&supplierId=sid2&supplierName=%E9%A3%9F%E5%A0%82&supplierCode=stjc&orderSource=YUDING"
        )

        assertEquals("mkb123", parsed.mkbToken)
        assertEquals("user1", parsed.supplierUserId)
        assertEquals("sid2", parsed.supplierId)
        assertEquals("食堂", parsed.supplierName)
        assertEquals("stjc", parsed.supplierCode)
        assertEquals("YUDING", parsed.orderSource)
    }
}


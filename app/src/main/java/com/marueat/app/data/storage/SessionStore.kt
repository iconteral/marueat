package com.marueat.app.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.marueat.app.data.model.CafeteriaSession
import org.json.JSONObject

class SessionStore(context: Context) {

    private val preferences: SharedPreferences = createPreferences(context.applicationContext)

    fun clear() {
        preferences.edit().clear().apply()
    }

    fun hasSession(): Boolean = preferences.contains(KEY_SESSION)

    fun saveSession(session: CafeteriaSession) {
        val payload = JSONObject()
            .put("authToken", session.authToken)
            .put("authUuid", session.authUuid)
            .put("code", session.code)
            .put("mkbToken", session.mkbToken)
            .put("supplierUserId", session.supplierUserId)
            .put("supplierId", session.supplierId)
            .put("supplierName", session.supplierName)
            .put("supplierCode", session.supplierCode)
            .put("orderSource", session.orderSource)
            .toString()

        preferences.edit().putString(KEY_SESSION, payload).apply()
    }

    fun loadSession(): CafeteriaSession? {
        val raw = preferences.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            CafeteriaSession(
                authToken = json.getString("authToken"),
                authUuid = json.getString("authUuid"),
                code = json.getString("code"),
                mkbToken = json.getString("mkbToken"),
                supplierUserId = json.getString("supplierUserId"),
                supplierId = json.getString("supplierId"),
                supplierName = json.getString("supplierName"),
                supplierCode = json.getString("supplierCode"),
                orderSource = json.getString("orderSource")
            )
        }.getOrNull()
    }

    private fun createPreferences(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFERENCES_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val PREFERENCES_NAME = "cafeteria_session"
        private const val KEY_SESSION = "session_json"
    }
}


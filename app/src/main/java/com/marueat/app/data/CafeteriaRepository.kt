package com.marueat.app.data

import android.content.Context
import com.marueat.app.data.model.CafeteriaSession
import com.marueat.app.data.storage.SessionStore
import com.marueat.app.network.CafeteriaApi
import com.marueat.app.network.CafeteriaProfile
import com.marueat.app.network.PortalAuthService
import com.marueat.app.util.JwtParser

data class CafeteriaSnapshot(
    val session: CafeteriaSession,
    val profile: CafeteriaProfile,
    val qrCode: String,
    val portalToken: String? = null
)

class CafeteriaRepository(context: Context) {

    private val sessionStore = SessionStore(context.applicationContext)
    private val cafeteriaApi = CafeteriaApi()
    private val portalAuthService = PortalAuthService()

    fun hasSession(): Boolean = sessionStore.hasSession()

    fun loginWithImportedToken(token: String): CafeteriaSnapshot = bootstrapWithToken(token)

    fun loginWithBrowserToken(token: String): CafeteriaSnapshot = bootstrapWithToken(token)

    fun loginWithNativeCredentials(username: String, password: String): CafeteriaSnapshot {
        val token = portalAuthService.loginByAccount(username, password)
        return bootstrapWithToken(token)
    }

    fun restoreSession(): CafeteriaSnapshot? {
        if (!sessionStore.hasSession()) {
            return null
        }
        return refresh()
    }

    fun refresh(): CafeteriaSnapshot {
        val session = sessionStore.loadSession() ?: throw IllegalStateException("未登录")
        val profile = cafeteriaApi.fetchProfile(session)
        val qrCode = cafeteriaApi.fetchQrCode(session)
        return CafeteriaSnapshot(session, profile, qrCode)
    }

    fun logout() {
        sessionStore.clear()
    }

    private fun bootstrapWithToken(token: String): CafeteriaSnapshot {
        try {
            val authUuid = JwtParser.extractUserId(token)
            val session = cafeteriaApi.bootstrapSession(token, authUuid)
            sessionStore.saveSession(session)
            val profile = cafeteriaApi.fetchProfile(session)
            val qrCode = cafeteriaApi.fetchQrCode(session)
            return CafeteriaSnapshot(session, profile, qrCode, token)
        } catch (error: Exception) {
            sessionStore.clear()
            throw error
        }
    }
}

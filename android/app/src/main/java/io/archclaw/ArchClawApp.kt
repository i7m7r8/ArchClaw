package io.archclaw

import android.app.Application
import io.archclaw.core.ProotManager

class ArchClawApp : Application() {

    companion object {
        private const val PREFS = "archclaw"
        const val KEY_SETUP = "setup_complete"
        const val KEY_TOKEN = "qwen_token"
        const val KEY_EXPIRES = "qwen_expires"

        lateinit var instance: ArchClawApp
            private set
    }

    lateinit var prootManager: ProotManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prootManager = ProotManager(this)
    }

    fun isSetupComplete(): Boolean =
        getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_SETUP, false)

    fun setSetupComplete() =
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_SETUP, true).apply()

    fun getQwenOAuthToken(): String? =
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_TOKEN, null)

    fun saveQwenOAuthToken(token: String, expiresAt: Long) =
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putString(KEY_TOKEN, token).putLong(KEY_EXPIRES, expiresAt).apply()

    fun isQwenOAuthExpired(): Boolean {
        val expires = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_EXPIRES, 0)
        return System.currentTimeMillis() > expires
    }
}

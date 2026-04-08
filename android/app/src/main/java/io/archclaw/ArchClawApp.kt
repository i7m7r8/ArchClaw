package io.archclaw

import android.app.Application
import io.archclaw.core.ProotManager

class ArchClawApp : Application() {

    companion object {
        private const val PREFS_NAME = "archclaw"
        const val KEY_SETUP_COMPLETE = "setup_complete"
        const val KEY_QWEN_OAUTH_TOKEN = "qwen_oauth_token"
        const val KEY_QWEN_OAUTH_EXPIRES = "qwen_oauth_expires"

        lateinit var instance: ArchClawApp
            private set
    }

    lateinit var prootManager: ProotManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prootManager = ProotManager(filesDir)
    }

    fun isSetupComplete(): Boolean {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_SETUP_COMPLETE, false)
    }

    fun setSetupComplete() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETE, true)
            .apply()
    }

    fun getQwenOAuthToken(): String? {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getString(KEY_QWEN_OAUTH_TOKEN, null)
    }

    fun saveQwenOAuthToken(token: String, expiresAt: Long) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_QWEN_OAUTH_TOKEN, token)
            .putLong(KEY_QWEN_OAUTH_EXPIRES, expiresAt)
            .apply()
    }

    fun isQwenOAuthExpired(): Boolean {
        val expiresAt = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getLong(KEY_QWEN_OAUTH_EXPIRES, 0)
        return System.currentTimeMillis() > expiresAt
    }
}

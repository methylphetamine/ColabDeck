package dev.colabdeck.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("colabdeck", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    var email: String
        get() = sp.getString(KEY_EMAIL, "") ?: ""
        set(value) = sp.edit().putString(KEY_EMAIL, value).apply()

    var displayName: String
        get() = sp.getString(KEY_NAME, "") ?: ""
        set(value) = sp.edit().putString(KEY_NAME, value).apply()

    var clientId: String
        get() = sp.getString(KEY_CLIENT_ID, "") ?: ""
        set(value) = sp.edit().putString(KEY_CLIENT_ID, value).apply()

    var tokenExpiresAt: Long
        get() = sp.getLong(KEY_TOKEN_EXPIRY, 0L)
        set(value) = sp.edit().putLong(KEY_TOKEN_EXPIRY, value).apply()

    var fontSize: Int
        get() = sp.getInt(KEY_FONT_SIZE, 14)
        set(value) = sp.edit().putInt(KEY_FONT_SIZE, value).apply()

    var keepScreenOn: Boolean
        get() = sp.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = sp.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    var haptics: Boolean
        get() = sp.getBoolean(KEY_HAPTICS, true)
        set(value) = sp.edit().putBoolean(KEY_HAPTICS, value).apply()

    fun sessionMeta(): MutableMap<String, SessionMeta> {
        val raw = sp.getString(KEY_SESSION_META, null) ?: return mutableMapOf()
        return runCatching {
            json.decodeFromString<Map<String, SessionMeta>>(raw).toMutableMap()
        }.getOrDefault(mutableMapOf())
    }

    fun saveSessionMeta(meta: Map<String, SessionMeta>) {
        sp.edit().putString(KEY_SESSION_META, json.encodeToString(meta)).apply()
    }

    private companion object {
        const val KEY_EMAIL = "email"
        const val KEY_NAME = "display_name"
        const val KEY_CLIENT_ID = "client_id"
        const val KEY_TOKEN_EXPIRY = "token_expiry"
        const val KEY_FONT_SIZE = "font_size"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_HAPTICS = "haptics"
        const val KEY_SESSION_META = "session_meta"
    }
}

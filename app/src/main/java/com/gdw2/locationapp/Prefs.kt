package com.gdw2.locationapp

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Encrypted, app-private storage for enrollment config and the Dawarich API key.
 */
object Prefs {
    private const val FILE = "location_app_secure"

    @Volatile
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        synchronized(this) {
            if (prefs != null) return
            val app = context.applicationContext
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            prefs = EncryptedSharedPreferences.create(
                app,
                FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    private fun p(): SharedPreferences = prefs ?: error("Prefs.init() not called")

    var person: String?
        get() = p().getString("person", null)
        set(v) { p().edit().putString("person", v).apply() }

    var email: String?
        get() = p().getString("email", null)
        set(v) { p().edit().putString("email", v).apply() }

    var dawarichUrl: String?
        get() = p().getString("dawarich_url", null)
        set(v) { p().edit().putString("dawarich_url", v).apply() }

    var dawarichKey: String?
        get() = p().getString("dawarich_key", null)
        set(v) { p().edit().putString("dawarich_key", v).apply() }

    var dondaUrl: String?
        get() = p().getString("donda_url", null)
        set(v) { p().edit().putString("donda_url", v).apply() }

    var deviceSecret: String?
        get() = p().getString("device_secret", null)
        set(v) { p().edit().putString("device_secret", v).apply() }

    var fcmToken: String?
        get() = p().getString("fcm_token", null)
        set(v) { p().edit().putString("fcm_token", v).apply() }

    var lastResult: String?
        get() = p().getString("last_result", null)
        set(v) { p().edit().putString("last_result", v).apply() }

    var lastResultAt: Long
        get() = p().getLong("last_result_at", 0L)
        set(v) { p().edit().putLong("last_result_at", v).apply() }

    var deviceId: String
        get() {
            p().getString("device_id", null)?.let { return it }
            val generated = UUID.randomUUID().toString()
            p().edit().putString("device_id", generated).apply()
            return generated
        }
        set(v) { p().edit().putString("device_id", v).apply() }

    fun isEnrolled(): Boolean = !dawarichUrl.isNullOrBlank() && !dawarichKey.isNullOrBlank()
}

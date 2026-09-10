package com.gdw2.locationapp

import android.content.Context
import org.json.JSONObject

/**
 * Exchanges a one-time setup token (from the locationapp:// deep link) for the
 * Dawarich URL/API key and a per-device secret used for later registration.
 */
object EnrollClient {
    data class Result(val ok: Boolean, val message: String)

    fun enroll(context: Context, dondaUrl: String, setupToken: String, fcmToken: String?): Result {
        Prefs.init(context)
        val base = dondaUrl.trim().trimEnd('/')
        val payload = JSONObject().apply {
            put("token", setupToken)
            put("device_id", Prefs.deviceId)
            if (!fcmToken.isNullOrBlank()) put("fcm_token", fcmToken)
            put("platform", "android")
        }

        return try {
            val res = Http.postJson("$base/devices/enroll", payload.toString())
            if (!res.ok) {
                return Result(false, "Enroll failed (${res.code}): ${res.body.take(200)}")
            }
            val data = JSONObject(res.body)
            Prefs.person = data.optString("person").ifBlank { null }
            Prefs.email = data.optString("email").ifBlank { null }
            Prefs.dawarichUrl = data.getString("dawarich_url")
            Prefs.dawarichKey = data.getString("dawarich_api_key")
            Prefs.deviceSecret = data.optString("device_secret").ifBlank { null }
            Prefs.dondaUrl = base
            if (!fcmToken.isNullOrBlank()) Prefs.fcmToken = fcmToken
            Result(true, "Enrolled as ${Prefs.person ?: "device"}")
        } catch (e: Exception) {
            Result(false, "Enroll error: ${e.message}")
        }
    }
}

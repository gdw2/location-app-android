package com.gdw2.locationapp

import android.content.Context
import org.json.JSONObject

/**
 * Updates the server with the current FCM registration token (token rotation).
 */
object RegisterClient {
    fun register(context: Context, fcmToken: String): Boolean {
        Prefs.init(context)
        val dondaUrl = Prefs.dondaUrl ?: return false
        val secret = Prefs.deviceSecret ?: return false
        val payload = JSONObject().apply {
            put("device_id", Prefs.deviceId)
            put("fcm_token", fcmToken)
        }
        return try {
            val res = Http.postJson(
                "${dondaUrl.trimEnd('/')}/devices/register",
                payload.toString(),
                headers = mapOf("Authorization" to "Bearer $secret")
            )
            if (res.ok) {
                Prefs.fcmToken = fcmToken
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

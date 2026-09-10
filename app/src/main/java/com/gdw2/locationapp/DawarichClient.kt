package com.gdw2.locationapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/**
 * Posts a single location point to the Dawarich batch points API.
 */
object DawarichClient {
    data class Result(val ok: Boolean, val message: String)

    fun postPoint(context: Context, fix: LocationProvider.Fix): Result {
        Prefs.init(context)
        val base = Prefs.dawarichUrl ?: return Result(false, "Not enrolled")
        val key = Prefs.dawarichKey ?: return Result(false, "No Dawarich API key")

        val properties = JSONObject().apply {
            put("timestamp", Instant.ofEpochMilli(fix.timeMillis).toString())
            fix.accuracy?.let { put("horizontal_accuracy", it.toDouble()) }
            put("device_id", Prefs.deviceId)
        }

        val point = JSONObject().apply {
            put("type", "Feature")
            put("geometry", JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(fix.longitude)
                    put(fix.latitude)
                })
            })
            put("properties", properties)
        }

        val body = JSONObject().put("locations", JSONArray().put(point))
        val url = "${base.trimEnd('/')}/api/v1/points?api_key=${URLEncoder.encode(key, "UTF-8")}"

        return try {
            val res = Http.postJson(url, body.toString())
            if (res.ok) {
                val acc = fix.accuracy?.toInt()?.toString() ?: "?"
                Result(true, "Sent (${res.code}) accuracy=${acc}m")
            } else {
                Result(false, "Dawarich ${res.code}: ${res.body.take(200)}")
            }
        } catch (e: Exception) {
            Result(false, "Network error: ${e.message}")
        }
    }
}

package com.gdw2.locationapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

/**
 * Wraps Google's FusedLocationProvider: one fresh fix with a timeout, then a
 * last-known fallback. The fused provider uses GPS + Wi-Fi + cell, so it works
 * indoors too (with coarser accuracy).
 */
object LocationProvider {
    data class Fix(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float?,
        val timeMillis: Long,
        val provider: String?
    )

    @SuppressLint("MissingPermission")
    fun getFix(context: Context, timeoutMs: Long = 5000): Fix? {
        val client = LocationServices.getFusedLocationProviderClient(context)

        var location: Location? = try {
            Tasks.await(
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null),
                timeoutMs,
                TimeUnit.MILLISECONDS
            )
        } catch (e: Exception) {
            null
        }

        if (location == null) {
            location = try {
                Tasks.await(client.lastLocation, 2000, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                null
            }
        }

        val loc = location ?: return null
        return Fix(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
            timeMillis = if (loc.time > 0) loc.time else System.currentTimeMillis(),
            provider = loc.provider
        )
    }
}

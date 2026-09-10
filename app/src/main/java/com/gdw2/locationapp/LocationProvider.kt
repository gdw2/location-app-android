package com.gdw2.locationapp

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Wraps Google's FusedLocationProvider.
 *
 * Uses requestLocationUpdates (not getCurrentLocation) so the device produces a
 * genuinely fresh fix with a new timestamp even when stationary — getCurrentLocation
 * can return a cached fix, which Dawarich de-duplicates. Falls back to last-known.
 * The fused provider uses GPS + Wi-Fi + cell, so it works indoors too.
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

        val latch = CountDownLatch(1)
        val result = AtomicReference<Location?>(null)
        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let {
                    result.set(it)
                    latch.countDown()
                }
            }
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMaxUpdates(1)
            .setDurationMillis(timeoutMs)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            // fall through to last-known
        } finally {
            try {
                client.removeLocationUpdates(callback)
            } catch (e: Exception) {
                // ignore
            }
        }

        var location = result.get()
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

package com.gdw2.locationapp

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Foreground service started by a high-priority FCM "locate" message. Fetches a
 * single fix and posts it to Dawarich, showing a transient notification.
 */
class LocationFetchService : Service() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            Notifications.FOREGROUND_ID,
            Notifications.buildForeground(this, getString(R.string.notif_fetching))
        )

        executor.execute {
            try {
                val fix = LocationProvider.getFix(this, 12000)
                val message = if (fix == null) {
                    "No location fix available"
                } else {
                    DawarichClient.postPoint(this, fix).message
                }
                Log.i(TAG, "locate result: $message")
            } catch (e: Exception) {
                Log.e(TAG, "locate failed", e)
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationFetchService"

        fun start(context: Context, requestId: String?) {
            val intent = Intent(context, LocationFetchService::class.java)
            intent.putExtra("requestId", requestId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

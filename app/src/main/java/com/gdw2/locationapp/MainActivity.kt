package com.gdw2.locationapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gdw2.locationapp.databinding.ActivityMainBinding
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val executor = Executors.newSingleThreadExecutor()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refreshStatus() }

    private val locationPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnNotifications.setOnClickListener { requestNotifications() }
        binding.btnLocation.setOnClickListener { requestLocation() }
        binding.btnBackgroundLocation.setOnClickListener { openBackgroundLocationSettings() }
        binding.btnBattery.setOnClickListener { requestBatteryExemption() }
        binding.btnRegister.setOnClickListener { registerNow() }
        binding.btnSendNow.setOnClickListener { sendNow() }

        handleDeepLink(intent)
        refreshStatus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "locationapp" || data.host != "setup") return

        val api = data.getQueryParameter("api")
        val token = data.getQueryParameter("token")
        if (api.isNullOrBlank() || token.isNullOrBlank()) {
            setResultText("Setup link is missing api or token")
            return
        }

        setResultText("Enrolling…")
        executor.execute {
            val fcmToken = currentFcmToken()
            val result = EnrollClient.enroll(this, api, token, fcmToken)
            runOnUiThread {
                setResultText(result.message)
                refreshStatus()
            }
        }
    }

    private fun currentFcmToken(): String? {
        return try {
            Tasks.await(FirebaseMessaging.getInstance().token, 15, TimeUnit.SECONDS)
        } catch (e: Exception) {
            null
        }
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            toast("Notifications are not required on this Android version")
        }
    }

    private fun requestLocation() {
        locationPermissions.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun openBackgroundLocationSettings() {
        toast("Choose 'Allow all the time' for Location")
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    private fun requestBatteryExemption() {
        val powerManager = getSystemService(PowerManager::class.java)
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            toast("Already exempt from battery optimization")
            return
        }
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun registerNow() {
        setResultText("Registering…")
        executor.execute {
            val token = currentFcmToken()
            val ok = token != null && RegisterClient.register(this, token)
            runOnUiThread {
                setResultText(if (ok) "Registered with server" else "Registration failed")
                refreshStatus()
            }
        }
    }

    private fun sendNow() {
        if (!Prefs.isEnrolled()) {
            setResultText("Not enrolled yet — open a setup link first")
            return
        }
        binding.btnSendNow.isEnabled = false
        setResultText("Getting location…")
        executor.execute {
            val message = try {
                val fix = LocationProvider.getFix(this, 12000)
                if (fix == null) {
                    "No location fix (check permissions and GPS)"
                } else {
                    DawarichClient.postPoint(this, fix).message
                }
            } catch (e: Exception) {
                "Error: ${e.message}"
            }
            runOnUiThread {
                setResultText(message)
                binding.btnSendNow.isEnabled = true
            }
        }
    }

    private fun refreshStatus() {
        binding.tvConfig.text = if (Prefs.isEnrolled()) {
            buildString {
                appendLine("Person: ${Prefs.person ?: "-"}")
                appendLine("Dawarich: ${Prefs.dawarichUrl}")
                appendLine("Server: ${Prefs.dondaUrl ?: "-"}")
                append("Device: ${Prefs.deviceId}")
            }
        } else {
            getString(R.string.config_summary)
        }

        val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            fine
        }
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val battery = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

        binding.tvPermissionStatus.text = buildString {
            appendLine("Notifications: ${yesNo(notifications)}")
            appendLine("Location (while using): ${yesNo(fine || coarse)}")
            appendLine("Location (background): ${yesNo(background)}")
            appendLine("Battery optimization exempt: ${yesNo(battery)}")
            append("FCM registered: ${yesNo(!Prefs.fcmToken.isNullOrBlank())}")
        }
    }

    private fun yesNo(value: Boolean): String =
        getString(if (value) R.string.status_granted else R.string.status_denied)

    private fun setResultText(text: String) {
        binding.tvResult.text = text
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }
}

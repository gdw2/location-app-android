package com.gdw2.locationapp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class LocationMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == "locate") {
            LocationFetchService.start(applicationContext, message.data["requestId"])
        }
    }

    override fun onNewToken(token: String) {
        Prefs.init(applicationContext)
        if (Prefs.isEnrolled()) {
            Thread { RegisterClient.register(applicationContext, token) }.start()
        }
    }
}

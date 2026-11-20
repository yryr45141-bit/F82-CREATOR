package com.example.notifications

import android.util.Log
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FfFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val uid = Firebase.auth.currentUser?.uid ?: return
        try {
            val dbId = getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(dbId)
            db.collection("users").document(uid)
                .collection("private").document("info")
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
        } catch (e: Exception) {
            Log.w("FfMessaging", "Failed to save refreshed FCM token: ${e.message}")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "FF Creator"
        val body = message.notification?.body ?: message.data["body"] ?: return
        LocalNotificationHelper.showNotification(
            context = applicationContext,
            idSeed = message.messageId ?: System.currentTimeMillis().toString(),
            title = title,
            body = body
        )
    }
}

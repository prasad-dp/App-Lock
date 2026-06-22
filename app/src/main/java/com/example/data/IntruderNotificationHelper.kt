package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object IntruderNotificationHelper {
    private const val INTRUDER_CHANNEL_ID = "intruder_detection_channel"
    private const val NOTIFICATION_ID = 404

    fun showIntruderNotification(context: Context, appName: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                INTRUDER_CHANNEL_ID,
                "Intruder Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when unauthorized access is detected"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("SELECTION", "intruder_records")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val detail = if (!appName.isNullOrEmpty()) {
            "Someone tried to access $appName with 3 wrong attempts!"
        } else {
            "3 incorrect security attempts captured! Intruder photo recorded."
        }

        val notification = NotificationCompat.Builder(context, INTRUDER_CHANNEL_ID)
            .setContentTitle("⚠️ Intruder Detected!")
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

package com.burak.zonesilent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {
    const val CHANNEL_ID = "zone_silent_channel"
    private const val CHANNEL_NAME = "ZoneSilent Notifications"
    private const val CHANNEL_DESCRIPTION = "Notifications for ZoneSilent geofence events"
    private var notificationId = 1000
 
     private const val PREFS_NAME = "zonesilent_notifications"
     private const val KEY_LAST_TEXT = "last_text"
     private const val KEY_LAST_TIME_MS = "last_time_ms"
     private const val DEDUPE_WINDOW_MS = 30_000L

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showGeofenceNotification(context: Context, title: String, message: String) {
         // Reduce notification frequency: only notify on zone ENTRY events.
         // Do not change background behavior; only filter notification output here.
         val isEntry = title.contains("active", ignoreCase = true) ||
             message.contains("entered", ignoreCase = true) ||
             message.contains("gird", ignoreCase = true)
 
         if (!isEntry) return
 
         // Basic dedupe/cooldown to prevent repeated notifications in a short time.
         val now = System.currentTimeMillis()
         val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
         val newText = "$title|$message"
         val lastText = prefs.getString(KEY_LAST_TEXT, null)
         val lastTime = prefs.getLong(KEY_LAST_TIME_MS, 0L)
         if (lastText == newText && (now - lastTime) < DEDUPE_WINDOW_MS) return
 
         prefs.edit()
             .putString(KEY_LAST_TEXT, newText)
             .putLong(KEY_LAST_TIME_MS, now)
             .apply()
 
        createNotificationChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        try {
            with(NotificationManagerCompat.from(context)) {
                notify(notificationId++, builder.build())
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}

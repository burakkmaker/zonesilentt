package com.burak.zonesilent

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.media.AudioManager
import android.app.NotificationManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.burak.zonesilent.data.AppDatabase
import com.burak.zonesilent.data.ZoneLocation
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.os.Looper

class ZoneMonitorService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val TAG = "ZoneMonitorService"
        private const val ACTION_REFRESH = "com.burak.zonesilent.action.REFRESH"

        fun start(context: Context) {
            val intent = Intent(context, ZoneMonitorService::class.java)
            try {
                context.startForegroundServiceCompat(intent)
            } catch (e: Exception) {
                // On Android 13/14 some devices restrict starting FGS from background contexts.
                Log.e(TAG, "Failed to start foreground service: ${e.message}")
            }
        }

        fun refresh(context: Context) {
            val intent = Intent(context, ZoneMonitorService::class.java).apply {
                action = ACTION_REFRESH
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request refresh: ${e.message}")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZoneMonitorService::class.java)
            context.stopService(intent)
        }

        private fun Context.startForegroundServiceCompat(intent: Intent) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val fusedClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var zonesCache: List<ZoneLocation> = emptyList()
    private var insideZoneIds: MutableSet<Int> = mutableSetOf()
    private var lastLocation: Location? = null

    private var lastNotifyText: String? = null
    private var lastNotifyAtMs: Long = 0L

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            lastLocation = loc
            handleLocation(loc)
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        startForeground(NOTIFICATION_ID, buildNotification("ZoneSilent çalışıyor"))
        startLocationUpdates()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH) {
            forceRefreshNow()
        }

        // If service is restarted by system, keep monitoring.
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ZoneSilent")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun refreshZones() {
        scope.launch {
            zonesCache = AppDatabase.getDatabase(this@ZoneMonitorService).zoneLocationDao().getAllLocationsList()
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000L)
            .setMinUpdateIntervalMillis(5_000L)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun forceRefreshNow() {
        val loc = lastLocation
        if (loc != null) {
            handleLocation(loc)
            return
        }

        try {
            fusedClient.lastLocation
                .addOnSuccessListener { l ->
                    if (l != null) {
                        lastLocation = l
                        handleLocation(l)
                    }
                }
        } catch (_: SecurityException) {
            // ignore
        }
    }

    private fun handleLocation(location: Location) {
        scope.launch {
            // Always read latest zones (local DB)
            zonesCache = AppDatabase.getDatabase(this@ZoneMonitorService).zoneLocationDao().getAllLocationsList()

            if (zonesCache.isEmpty()) {
                insideZoneIds = mutableSetOf()
                RingerModeHelper.restorePreviousMode(this@ZoneMonitorService)
                maybeUpdateNotification(
                    notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager,
                    text = "Zone yok",
                    force = false
                )
                return@launch
            }

            val nowInside = mutableSetOf<Int>()
            for (z in zonesCache) {
                val dist = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    z.latitude,
                    z.longitude,
                    dist
                )
                if (dist[0] <= z.radius) {
                    nowInside.add(z.id)
                }
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val wasInside = insideZoneIds.isNotEmpty()
            val isInside = nowInside.isNotEmpty()

            insideZoneIds = nowInside

            if (!wasInside && isInside) {
                RingerModeHelper.capturePreviousModeIfNeeded(this@ZoneMonitorService)
            }

            if (!isInside) {
                RingerModeHelper.restorePreviousMode(this@ZoneMonitorService)
                maybeUpdateNotification(
                    notificationManager = notificationManager,
                    text = "Dışarıda",
                    force = false
                )
                return@launch
            }

            val shouldSilent = zonesCache.any { nowInside.contains(it.id) && it.mode == "SILENT" }
            val success = RingerModeHelper.setRingerModeForZones(this@ZoneMonitorService, shouldSilent)

            val title = if (shouldSilent) "Sessiz" else "Titreşim"
            val text = if (!success && shouldSilent) {
                "${nowInside.size} zone içinde • Mod: $title • DND izni ver"
            } else {
                "${nowInside.size} zone içinde • Mod: $title"
            }
            maybeUpdateNotification(notificationManager, text, force = false)
        }
    }

    private fun maybeUpdateNotification(
        notificationManager: NotificationManager,
        text: String,
        force: Boolean
    ) {
        val now = System.currentTimeMillis()
        val shouldUpdate = force ||
            (lastNotifyText != text) ||
            (now - lastNotifyAtMs >= 60_000L)

        if (!shouldUpdate) return

        lastNotifyText = text
        lastNotifyAtMs = now

        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification: ${e.message}")
        }
    }
}

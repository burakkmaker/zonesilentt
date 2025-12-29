package com.burak.zonesilent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.media.AudioManager
import android.util.Log
import com.burak.zonesilent.data.AppDatabase
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        
        if (geofencingEvent == null) {
            Log.e(TAG, "Geofencing event is null")
            pendingResult.finish()
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Geofence error: $errorMessage")
            pendingResult.finish()
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggering = geofencingEvent.triggeringGeofences
        val requestIds = triggering?.mapNotNull { it.requestId } ?: emptyList()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (geofenceTransition) {
                    Geofence.GEOFENCE_TRANSITION_ENTER -> {
                        Log.d(TAG, "Entered geofence area")
                        handleTransition(context, entering = requestIds, exiting = emptyList())
                        NotificationHelper.showGeofenceNotification(
                            context,
                            "ZoneSilent Active",
                            "Entered silent zone. Phone set to silent/vibrate mode."
                        )
                    }
                    Geofence.GEOFENCE_TRANSITION_EXIT -> {
                        Log.d(TAG, "Exited geofence area")
                        handleTransition(context, entering = emptyList(), exiting = requestIds)
                        NotificationHelper.showGeofenceNotification(
                            context,
                            "ZoneSilent Deactivated",
                            "Exited silent zone. Phone set to normal mode."
                        )
                    }
                    else -> {
                        Log.e(TAG, "Invalid transition type: $geofenceTransition")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleTransition(context: Context, entering: List<String>, exiting: List<String>) {
        val prefs = context.getSharedPreferences("zonesilent_prefs", Context.MODE_PRIVATE)
        val active = prefs.getStringSet("active_geofences", emptySet())?.toMutableSet() ?: mutableSetOf()

        val wasEmpty = active.isEmpty()

        entering.forEach { active.add(it) }
        exiting.forEach { active.remove(it) }
        prefs.edit().putStringSet("active_geofences", active).apply()

        Log.d(TAG, "Active geofences after transition: ${active.size} zones")

        // capture previous mode when entering from 0 -> 1
        if (wasEmpty && active.isNotEmpty()) {
            RingerModeHelper.capturePreviousModeIfNeeded(context)
        }

        if (active.isEmpty()) {
            RingerModeHelper.restorePreviousMode(context)
            return
        }

        // Recompute desired mode every time (strict)
        val ids = active.mapNotNull { parseZoneId(it) }
        if (ids.isEmpty()) {
            // No valid IDs -> treat as no active zones
            prefs.edit().putStringSet("active_geofences", emptySet()).apply()
            RingerModeHelper.restorePreviousMode(context)
            return
        }

        try {
            val db = AppDatabase.getDatabase(context)
            val zones = db.zoneLocationDao().getLocationsByIds(ids)
            // Cleanup: remove IDs that no longer exist in DB (zone deleted/changed)
            val existingRequestIds = zones.map { "GEOFENCE_${it.id}" }.toSet()
            val cleaned = active.filter { existingRequestIds.contains(it) }.toSet()
            if (cleaned.size != active.size) {
                prefs.edit().putStringSet("active_geofences", cleaned).apply()
            }

            if (cleaned.isEmpty() || zones.isEmpty()) {
                RingerModeHelper.restorePreviousMode(context)
                return
            }

            val shouldSilent = zones.any { it.mode == "SILENT" }
            val success = RingerModeHelper.setRingerModeForZones(context, shouldSilent)
            
            if (!success && shouldSilent) {
                NotificationHelper.showGeofenceNotification(
                    context,
                    "ZoneSilent",
                    "Sessiz mod için Rahatsız Etmeyin izni gerekli (DND izni ver)."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to recompute zone mode: ${e.message}")
            RingerModeHelper.setRingerModeForZones(context, shouldSilent = false)
        }
    }

    private fun parseZoneId(requestId: String): Int? {
        // requestId format: GEOFENCE_<id>
        val prefix = "GEOFENCE_"
        if (!requestId.startsWith(prefix)) return null
        return requestId.removePrefix(prefix).toIntOrNull()
    }

}


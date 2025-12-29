package com.burak.zonesilent.utils

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.burak.zonesilent.GeofenceBroadcastReceiver
import com.burak.zonesilent.data.ZoneLocation
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private val TAG = "GeofenceManager"

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    @SuppressLint("MissingPermission")
    fun addGeofence(location: ZoneLocation, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val geofence = createGeofence(location)
        val geofencingRequest = createGeofencingRequest(geofence)

        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d(TAG, "Geofence added successfully for ${location.name}")
                onSuccess()
            }
            addOnFailureListener { exception ->
                Log.e(TAG, "Failed to add geofence: ${exception.message}")
                onFailure(exception.message ?: "Unknown error")
            }
        }
    }

    fun removeGeofence(locationId: Int, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        val geofenceId = "GEOFENCE_$locationId"
        geofencingClient.removeGeofences(listOf(geofenceId)).run {
            addOnSuccessListener {
                Log.d(TAG, "Geofence removed successfully: $geofenceId")
                onSuccess()
            }
            addOnFailureListener { exception ->
                Log.e(TAG, "Failed to remove geofence: ${exception.message}")
                onFailure(exception.message ?: "Unknown error")
            }
        }
    }

    fun removeAllGeofences(onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        geofencingClient.removeGeofences(geofencePendingIntent).run {
            addOnSuccessListener {
                Log.d(TAG, "All geofences removed successfully")
                onSuccess()
            }
            addOnFailureListener { exception ->
                Log.e(TAG, "Failed to remove all geofences: ${exception.message}")
                onFailure(exception.message ?: "Unknown error")
            }
        }
    }

    private fun createGeofence(location: ZoneLocation): Geofence {
        return Geofence.Builder()
            .setRequestId("GEOFENCE_${location.id}")
            .setCircularRegion(
                location.latitude,
                location.longitude,
                location.radius
            )
            .setNotificationResponsiveness(2_000)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()
    }

    private fun createGeofencingRequest(geofence: Geofence): GeofencingRequest {
        return GeofencingRequest.Builder().apply {
            setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            addGeofence(geofence)
        }.build()
    }
}

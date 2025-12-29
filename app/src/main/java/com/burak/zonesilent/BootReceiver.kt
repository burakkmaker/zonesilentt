package com.burak.zonesilent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.burak.zonesilent.data.AppDatabase
import com.burak.zonesilent.utils.GeofenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hasFine = ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasFine) return@launch

                val db = AppDatabase.getDatabase(context)
                val zones = db.zoneLocationDao().getAllLocationsList()

                val geofenceManager = GeofenceManager(context)
                zones.forEach { zone ->
                    geofenceManager.addGeofence(
                        zone,
                        onSuccess = { },
                        onFailure = { }
                    )
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

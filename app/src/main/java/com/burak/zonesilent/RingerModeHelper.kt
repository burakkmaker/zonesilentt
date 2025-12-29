package com.burak.zonesilent

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log

object RingerModeHelper {
    private const val TAG = "RingerModeHelper"
    private const val PREFS_NAME = "zonesilent_prefs"
    private const val KEY_PREV_RINGER_MODE = "prev_ringer_mode"
    private const val KEY_INSIDE_ANY_ZONE = "inside_any_zone"

    fun capturePreviousModeIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val wasInside = prefs.getBoolean(KEY_INSIDE_ANY_ZONE, false)
        
        if (wasInside) {
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val current = audioManager.ringerMode
        prefs.edit()
            .putInt(KEY_PREV_RINGER_MODE, current)
            .putBoolean(KEY_INSIDE_ANY_ZONE, true)
            .apply()
        
        Log.d(TAG, "Captured previous ringer mode: $current (${ringerModeToString(current)})")
    }

    fun setRingerModeForZones(context: Context, shouldSilent: Boolean): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val desiredMode = if (shouldSilent) {
            AudioManager.RINGER_MODE_SILENT
        } else {
            AudioManager.RINGER_MODE_VIBRATE
        }

        val canApplySilent = desiredMode != AudioManager.RINGER_MODE_SILENT || 
                             notificationManager.isNotificationPolicyAccessGranted

        if (!canApplySilent) {
            Log.w(TAG, "DND policy access not granted; falling back to VIBRATE instead of SILENT")
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                Log.d(TAG, "Ringer mode set to VIBRATE (fallback from SILENT)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set VIBRATE mode: ${e.message}")
            }
            return false
        }

        try {
            audioManager.ringerMode = desiredMode
            Log.d(TAG, "Ringer mode set to ${ringerModeToString(desiredMode)}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set ringer mode to ${ringerModeToString(desiredMode)}: ${e.message}")
            return false
        }
    }

    fun restorePreviousMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val prevMode = prefs.getInt(KEY_PREV_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
        
        prefs.edit()
            .putBoolean(KEY_INSIDE_ANY_ZONE, false)
            .apply()
        
        try {
            audioManager.ringerMode = prevMode
            Log.d(TAG, "Restored previous ringer mode: ${ringerModeToString(prevMode)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore previous ringer mode: ${e.message}")
        }
    }

    fun isInsideAnyZone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_INSIDE_ANY_ZONE, false)
    }

    private fun ringerModeToString(mode: Int): String {
        return when (mode) {
            AudioManager.RINGER_MODE_SILENT -> "SILENT"
            AudioManager.RINGER_MODE_VIBRATE -> "VIBRATE"
            AudioManager.RINGER_MODE_NORMAL -> "NORMAL"
            else -> "UNKNOWN($mode)"
        }
    }
}

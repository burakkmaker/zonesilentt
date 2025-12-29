package com.burak.zonesilent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "zone_locations")
data class ZoneLocation(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float,
    val mode: String
)

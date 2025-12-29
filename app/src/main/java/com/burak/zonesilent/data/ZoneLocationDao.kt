package com.burak.zonesilent.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface ZoneLocationDao {
    @Query("SELECT * FROM zone_locations ORDER BY id DESC")
    fun getAllLocations(): LiveData<List<ZoneLocation>>

    @Query("SELECT * FROM zone_locations ORDER BY id DESC")
    suspend fun getAllLocationsList(): List<ZoneLocation>

    @Query("SELECT * FROM zone_locations WHERE id = :id")
    suspend fun getLocationById(id: Int): ZoneLocation?

    @Query("SELECT * FROM zone_locations WHERE id IN (:ids)")
    suspend fun getLocationsByIds(ids: List<Int>): List<ZoneLocation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: ZoneLocation): Long

    @Update
    suspend fun update(location: ZoneLocation)

    @Delete
    suspend fun delete(location: ZoneLocation)

    @Query("DELETE FROM zone_locations WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM zone_locations")
    suspend fun deleteAll()
}

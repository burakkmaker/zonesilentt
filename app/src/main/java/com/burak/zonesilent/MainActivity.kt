package com.burak.zonesilent

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.burak.zonesilent.adapter.ZoneLocationAdapter
import com.burak.zonesilent.data.AppDatabase
import com.burak.zonesilent.data.ZoneLocation
import com.burak.zonesilent.databinding.ActivityMainBinding
import com.burak.zonesilent.databinding.DialogZoneListBinding
import com.burak.zonesilent.utils.GeofenceManager
import com.burak.zonesilent.utils.PermissionManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.location.Geocoder
import java.util.Locale

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMainBinding
    private lateinit var googleMap: GoogleMap
    private lateinit var database: AppDatabase
    private lateinit var geofenceManager: GeofenceManager
    
    private var currentMarker: Marker? = null
    private var currentCircle: Circle? = null
    private var selectedLocation: LatLng? = null
    private var currentRadius: Float = 100f

    private val savedMarkers = mutableMapOf<Int, Marker>()
    private val savedCircles = mutableMapOf<Int, Circle>()

    private var selectedSavedZoneId: Int? = null

    private fun syncActiveGeofencesAfterZoneChanged(zoneId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("zonesilent_prefs", MODE_PRIVATE)
            val active = prefs.getStringSet("active_geofences", emptySet())?.toMutableSet() ?: mutableSetOf()
            val requestId = "GEOFENCE_$zoneId"

            val removed = active.remove(requestId)
            if (removed) {
                prefs.edit().putStringSet("active_geofences", active).apply()
            }

            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            if (active.isEmpty()) {
                val prev = prefs.getInt("prev_ringer_mode", AudioManager.RINGER_MODE_NORMAL)
                audioManager.ringerMode = prev
                return@launch
            }

            val ids = active.mapNotNull { it.removePrefix("GEOFENCE_").toIntOrNull() }
            if (ids.isEmpty()) {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                return@launch
            }

            val zones = database.zoneLocationDao().getLocationsByIds(ids)
            val shouldSilent = zones.any { it.mode == "SILENT" }
            audioManager.ringerMode = if (shouldSilent) {
                AudioManager.RINGER_MODE_SILENT
            } else {
                AudioManager.RINGER_MODE_VIBRATE
            }
        }
    }

    private fun setupDatabase() {
        database = AppDatabase.getDatabase(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupDatabase()
        geofenceManager = GeofenceManager(this)

        NotificationHelper.createNotificationChannel(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupUI()
        checkAndRequestPermissions()

        // Avoid stale geofence state after process death / app restart.
        getSharedPreferences("zonesilent_prefs", MODE_PRIVATE)
            .edit()
            .putStringSet("active_geofences", emptySet())
            .apply()

        if (PermissionManager.hasLocationPermission(this) && PermissionManager.hasBackgroundLocationPermission(this)) {
            ZoneMonitorService.start(this)
            ZoneMonitorService.refresh(this)
        }
    }

    private fun setupUI() {
        // SeekBar listener (0-500). Enforce minimum 50m in code.
        binding.radiusSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val adjusted = if (progress < 50) 50 else progress
                currentRadius = adjusted.toFloat()
                updateCircleRadius()
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        binding.saveLocationButton.setOnClickListener { saveLocation() }

        binding.deleteSelectedButton.setOnClickListener {
            deleteSelectedZone()
        }

        binding.searchLayout.setEndIconOnClickListener {
            searchAndMoveMap()
        }

        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchAndMoveMap()
                true
            } else {
                false
            }
        }
    }

    private fun onSavedZoneDragged(zoneId: Int, newCenter: LatLng) {
        lifecycleScope.launch {
            val existing = database.zoneLocationDao().getLocationById(zoneId) ?: return@launch

            val updated = existing.copy(
                latitude = newCenter.latitude,
                longitude = newCenter.longitude
            )
            database.zoneLocationDao().update(updated)

            // Re-register geofence with new center
            geofenceManager.removeGeofence(
                zoneId,
                onSuccess = {
                    syncActiveGeofencesAfterZoneChanged(zoneId)
                    geofenceManager.addGeofence(
                        updated,
                        onSuccess = { },
                        onFailure = { }
                    )

                    ZoneMonitorService.refresh(this@MainActivity)
                },
                onFailure = { }
            )
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.setOnMapClickListener { latLng ->
            onMapClicked(latLng)
        }

        googleMap.setOnMarkerClickListener { marker ->
            val savedId = savedMarkers.entries.firstOrNull { it.value == marker }?.key
            if (savedId != null) {
                selectSavedZone(savedId)
                true
            } else {
                false
            }
        }

        googleMap.setOnMarkerDragListener(object : GoogleMap.OnMarkerDragListener {
            override fun onMarkerDragStart(marker: Marker) {
                val savedId = savedMarkers.entries.firstOrNull { it.value == marker }?.key
                if (savedId != null) {
                    selectSavedZone(savedId)
                    return
                }

                if (marker == currentMarker) {
                    clearSavedSelection()
                }
            }

            override fun onMarkerDrag(marker: Marker) {
                val savedId = savedMarkers.entries.firstOrNull { it.value == marker }?.key
                if (savedId != null) {
                    savedCircles[savedId]?.center = marker.position
                    return
                }

                if (marker == currentMarker) {
                    selectedLocation = marker.position
                    currentCircle?.center = marker.position
                }
            }

            override fun onMarkerDragEnd(marker: Marker) {
                val savedId = savedMarkers.entries.firstOrNull { it.value == marker }?.key
                if (savedId != null) {
                    onSavedZoneDragged(savedId, marker.position)
                    return
                }

                if (marker == currentMarker) {
                    selectedLocation = marker.position
                    currentCircle?.center = marker.position
                }
            }
        })

        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = false
        }

        observeSavedZonesOnMap()

        if (PermissionManager.hasLocationPermission(this)) {
            enableMyLocation()
            moveToMyLocation()
        }

        val defaultLocation = LatLng(41.0082, 28.9784)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))
    }

    private fun onMapClicked(latLng: LatLng) {
        clearSavedSelection()
        selectedLocation = latLng
        
        currentMarker?.remove()
        currentMarker = googleMap.addMarker(
            MarkerOptions()
                .position(latLng)
                .title("New Silent Zone")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                .draggable(true)
        )

        updateCircle(latLng, currentRadius)
    }

    private fun selectSavedZone(id: Int) {
        if (selectedSavedZoneId == id) return
        clearSavedSelection()
        selectedSavedZoneId = id

        savedMarkers[id]?.setIcon(
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
        )

        binding.deleteSelectedButton.isEnabled = true
    }

    private fun clearSavedSelection() {
        val id = selectedSavedZoneId
        if (id != null) {
            savedMarkers[id]?.setIcon(
                BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            )
        }
        selectedSavedZoneId = null
        binding.deleteSelectedButton.isEnabled = false
    }

    private fun deleteSelectedZone() {
        val id = selectedSavedZoneId ?: return

        AlertDialog.Builder(this)
            .setTitle("Delete Zone")
            .setMessage("Seçili alan silinsin mi?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    geofenceManager.removeGeofence(
                        id,
                        onSuccess = {
                            lifecycleScope.launch {
                                database.zoneLocationDao().deleteById(id)
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Zone deleted", Toast.LENGTH_SHORT).show()
                                    clearSavedSelection()
                                }
                                syncActiveGeofencesAfterZoneChanged(id)

                                ZoneMonitorService.refresh(this@MainActivity)
                            }
                        },
                        onFailure = { error ->
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Failed to remove geofence: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun searchAndMoveMap() {
        val query = binding.searchInput.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) {
            Toast.makeText(this, "Arama için bir yer yaz", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(this@MainActivity, Locale.getDefault())
                    val addresses = geocoder.getFromLocationName(query, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val a = addresses[0]
                        LatLng(a.latitude, a.longitude)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }

            if (result == null) {
                Toast.makeText(this@MainActivity, "Yer bulunamadı", Toast.LENGTH_SHORT).show()
                return@launch
            }

            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(result, 15f))
            onMapClicked(result)
        }
    }

    private fun updateCircle(center: LatLng, radius: Float) {
        currentCircle?.remove()
        
        currentCircle = googleMap.addCircle(
            CircleOptions()
                .center(center)
                .radius(radius.toDouble())
                .strokeColor(Color.parseColor("#2196F3"))
                .fillColor(Color.parseColor("#402196F3"))
                .strokeWidth(2f)
        )
    }

    private fun observeSavedZonesOnMap() {
        database.zoneLocationDao().getAllLocations().observe(this) { zones ->
            renderSavedZones(zones)
        }
    }

    private fun renderSavedZones(zones: List<ZoneLocation>) {
        val ids = zones.map { it.id }.toSet()

        // remove visuals for deleted zones
        val markerIter = savedMarkers.iterator()
        while (markerIter.hasNext()) {
            val entry = markerIter.next()
            if (!ids.contains(entry.key)) {
                entry.value.remove()
                markerIter.remove()
            }
        }
        val circleIter = savedCircles.iterator()
        while (circleIter.hasNext()) {
            val entry = circleIter.next()
            if (!ids.contains(entry.key)) {
                entry.value.remove()
                circleIter.remove()
            }
        }

        // add/update
        zones.forEach { zone ->
            val center = LatLng(zone.latitude, zone.longitude)

            val marker = savedMarkers[zone.id]
            if (marker == null) {
                val created = googleMap.addMarker(
                    MarkerOptions()
                        .position(center)
                        .title(zone.name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        .draggable(true)
                )
                if (created != null) {
                    savedMarkers[zone.id] = created
                }
            } else {
                marker.position = center
                marker.title = zone.name
            }

            val circle = savedCircles[zone.id]
            if (circle == null) {
                savedCircles[zone.id] = googleMap.addCircle(
                    CircleOptions()
                        .center(center)
                        .radius(zone.radius.toDouble())
                        .strokeColor(Color.parseColor("#F44336"))
                        .fillColor(Color.parseColor("#40F44336"))
                        .strokeWidth(2f)
                )
            } else {
                circle.center = center
                circle.radius = zone.radius.toDouble()
            }
        }
    }

    private fun updateCircleRadius() {
        selectedLocation?.let { location ->
            updateCircle(location, currentRadius)
        }
    }

    private fun saveLocation() {
        val locationName = binding.locationNameInput.text?.toString()?.trim()
        val location = selectedLocation

        if (locationName.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter a location name", Toast.LENGTH_SHORT).show()
            return
        }

        if (location == null) {
            Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            return
        }

        if (!PermissionManager.hasLocationPermission(this) || 
            !PermissionManager.hasBackgroundLocationPermission(this)) {
            Toast.makeText(this, "Location permissions are required", Toast.LENGTH_SHORT).show()
            return
        }

        val mode = if (binding.modeSilent.isChecked) "SILENT" else "VIBRATE"

        lifecycleScope.launch {
            val zoneLocation = ZoneLocation(
                name = locationName,
                latitude = location.latitude,
                longitude = location.longitude,
                radius = currentRadius,
                mode = mode
            )

            val id = database.zoneLocationDao().insert(zoneLocation).toInt()
            val locationWithId = zoneLocation.copy(id = id)

            geofenceManager.addGeofence(
                locationWithId,
                onSuccess = {
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Zone saved and geofence added",
                            Toast.LENGTH_SHORT
                        ).show()
                        clearSelection()
                    }

                    ZoneMonitorService.refresh(this@MainActivity)
                },
                onFailure = { error ->
                    runOnUiThread {
                        Toast.makeText(
                            this@MainActivity,
                            "Saved but geofence failed: $error",
                            Toast.LENGTH_LONG
                        ).show()
                        clearSelection()
                    }

                    ZoneMonitorService.refresh(this@MainActivity)
                }
            )
        }
    }

    private fun clearSelection() {
        currentMarker?.remove()
        currentCircle?.remove()
        currentMarker = null
        currentCircle = null
        selectedLocation = null
        binding.locationNameInput.text?.clear()
        binding.radiusSeekBar.progress = 100
        currentRadius = 100f
        clearSavedSelection()
    }

    private fun showSavedZonesDialog() {
        val dialogBinding = DialogZoneListBinding.inflate(layoutInflater)
        val adapter = ZoneLocationAdapter { location ->
            deleteZone(location)
        }

        dialogBinding.zonesRecyclerView.layoutManager = LinearLayoutManager(this)
        dialogBinding.zonesRecyclerView.adapter = adapter

        database.zoneLocationDao().getAllLocations().observe(this) { zones ->
            if (zones.isEmpty()) {
                dialogBinding.emptyStateText.visibility = android.view.View.VISIBLE
                dialogBinding.zonesRecyclerView.visibility = android.view.View.GONE
            } else {
                dialogBinding.emptyStateText.visibility = android.view.View.GONE
                dialogBinding.zonesRecyclerView.visibility = android.view.View.VISIBLE
                adapter.submitList(zones)
            }
        }

        AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Close", null)
            .create()
            .show()
    }

    private fun deleteZone(location: ZoneLocation) {
        AlertDialog.Builder(this)
            .setTitle("Delete Zone")
            .setMessage("Are you sure you want to delete ${location.name}?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    geofenceManager.removeGeofence(
                        location.id,
                        onSuccess = {
                            lifecycleScope.launch {
                                database.zoneLocationDao().delete(location)
                                runOnUiThread {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Zone deleted",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                ZoneMonitorService.refresh(this@MainActivity)
                            }
                        },
                        onFailure = { error ->
                            runOnUiThread {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Failed to remove geofence: $error",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (PermissionManager.hasLocationPermission(this)) {
            googleMap.isMyLocationEnabled = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun moveToMyLocation() {
        if (PermissionManager.hasLocationPermission(this)) {
            val fusedLocationClient = com.google.android.gms.location.LocationServices
                .getFusedLocationProviderClient(this)
            
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(it.latitude, it.longitude)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }
            }
        } else {
            Toast.makeText(this, "Location permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAndRequestPermissions() {
        when {
            !PermissionManager.hasLocationPermission(this) -> {
                PermissionManager.requestLocationPermission(this)
            }
            !PermissionManager.hasBackgroundLocationPermission(this) -> {
                PermissionManager.requestBackgroundLocationPermission(this)
            }
            !PermissionManager.hasNotificationPermission(this) -> {
                PermissionManager.requestNotificationPermission(this)
            }
            !PermissionManager.hasDoNotDisturbPermission(this) -> {
                PermissionManager.showDoNotDisturbRationaleDialog(this) {
                    PermissionManager.requestDoNotDisturbPermission(this)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            PermissionManager.LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocation()
                    moveToMyLocation()
                    if (!PermissionManager.hasBackgroundLocationPermission(this)) {
                        PermissionManager.requestBackgroundLocationPermission(this)
                    }

                    if (PermissionManager.hasBackgroundLocationPermission(this)) {
                        ZoneMonitorService.start(this)
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Location permission is required for this app",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            PermissionManager.BACKGROUND_LOCATION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        this,
                        "Background location permission granted!",
                        Toast.LENGTH_SHORT
                    ).show()

                    ZoneMonitorService.start(this)

                    if (!PermissionManager.hasNotificationPermission(this)) {
                        PermissionManager.requestNotificationPermission(this)
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Background location is required for automatic silent mode",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            PermissionManager.NOTIFICATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        this,
                        "Notification permission granted!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
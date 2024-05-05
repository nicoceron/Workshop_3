package com.example.workshop_3

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.content.Intent
import com.google.firebase.auth.FirebaseAuth

import android.Manifest
import android.app.UiModeManager
import android.content.ContentValues.TAG
import android.content.Context

import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log

import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.workshop_3.Firebase.UserManager
import com.example.workshop_3.databinding.ActivityMainBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsResponse
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    data class Location(
        val latitude: Double, val longitude: Double, val name: String
    )

    private lateinit var binding: ActivityMainBinding
    lateinit var locationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private val database = FirebaseDatabase.getInstance()
    val userManager = UserManager(database)
    private lateinit var map: MapView  // Declare map as a global variable
    private var userLocation: GeoPoint? = null


    private val locationSettings =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                // User agreed to make required location settings changes.
                startLocationUpdates()
            } else {
                // User chose not to make required location settings changes.
                Toast.makeText(this, "Location is turned off", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        enableEdgeToEdge()
//
//
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

        //Location
        locationCallback = createLocationCallBack()
        requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        locationClient = LocationServices.getFusedLocationProviderClient(this)
        //Map
        Configuration.getInstance()
            .load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))
        setupMap()
        //JSON Locations
        processJSONLocations("locations.json")
    }

    private fun processJSONLocations(filename: String) {
        val fileStream: InputStream = assets.open(filename)
        val size = fileStream.available()
        val buffer = ByteArray(size)
        fileStream.read(buffer)
        fileStream.close()

        val jsonString = String(buffer)
        val json = JSONObject(jsonString)
        val locationsArray = json.getJSONArray("locationsArray")

        for (i in 0 until locationsArray.length()) {
            val locationString = locationsArray.getJSONObject(i).toString()
            val location = Gson().fromJson(locationString, Location::class.java)

            Log.i("LOCATIONS", locationString)
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            addMarker(geoPoint, location.name)
        }
    }

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permissions granted
            locationSettings() //Check GPS
        } else {
            Toast.makeText(
                applicationContext, "Location permission is required", Toast.LENGTH_LONG
            ).show()
            Log.d(TAG, "Location access denied")
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1
            )
        }
    }

    override fun onResume() {
        super.onResume()
        createLocationRequest()
        map.onResume()
        val uims = getSystemService(
            Context.UI_MODE_SERVICE
        ) as UiModeManager
        if (uims.nightMode == UiModeManager.MODE_NIGHT_YES) {
            map.overlayManager.tilesOverlay.setColorFilter(
                TilesOverlay.INVERT_COLORS
            )
        }
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
        map.onPause()
    }

    fun locationSettings() {
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
        val client: SettingsClient = LocationServices.getSettingsClient(this)
        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
        task.addOnSuccessListener { locationSettingsResponse ->
            startLocationUpdates()
        }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                // Location settings are not satisfied, but this can be fixed
                // by showing the user a dialog.
                try {
                    // Show the dialog by calling startResolutionForResult(),
                    // and check the result in onActivityResult().
                    val isr: IntentSenderRequest = IntentSenderRequest.Builder(
                        exception.resolution
                    ).build()
                    locationSettings.launch(isr)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                    Log.i("LocationSettings", "PendingIntent unable to execute request.")
                }
            }
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            locationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper()
            )
        } else {
            Log.d(TAG, "Location subscription denied")
        }
    }

    fun createLocationCallBack(): LocationCallback {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                super.onLocationResult(result)
                if (result != null) {
                    val location = result.lastLocation!!
                    userLocation = GeoPoint(location.latitude, location.longitude)
                    userManager.writeUserLocation(location.latitude, location.longitude)
                    updateUI(GeoPoint(location.latitude, location.longitude))
                    locationClient.removeLocationUpdates(this) //Moving map only once
                }
            }
        }
        return locationCallback
    }

    private fun updateUI(location: GeoPoint) {
        addMarker(location, "My location")
        map.controller.setZoom(18.0)
        map.controller.setCenter(location)
    }

    //Manage Location
    private fun createLocationRequest(): LocationRequest {
        locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 10000
        ).setWaitForAccurateLocation(true).setMinUpdateIntervalMillis(5000).build()
        return locationRequest
    }

    private fun stopLocationUpdates() {
        locationClient.removeLocationUpdates(locationCallback)
    }

    //Manage Map
    private fun setupMap() {
        map = binding.osmMap
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
    }

    private fun addMarker(
        position: GeoPoint, title: String, icon: Int = R.drawable.location_on_24px
    ): Marker {
        val marker = Marker(map)
        marker.title = title
        val myIcon = AppCompatResources.getDrawable(baseContext, icon)
        marker.icon = myIcon
        marker.position = position
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        map.overlays.add(marker)
        return marker
    }

    //MENU

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater: MenuInflater = menuInflater
        inflater.inflate(R.menu.menu_top, menu)
        super.onCreateOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle item selection.
        return when (item.itemId) {
            R.id.toggleAvailability -> {
                userManager.toggleUserAvailability()
                true
            }

            R.id.logout -> {
                Firebase.auth.signOut()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
                true
            }

            R.id.findUsers -> {
                startActivity(Intent(this, UserList::class.java))
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }


}
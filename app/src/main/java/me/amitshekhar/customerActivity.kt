package me.amitshekhar

import android.Manifest
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


import org.bson.BsonDocument
import org.bson.BsonString
import org.bson.BsonInt64
import org.bson.types.ObjectId
import org.bson.BsonObjectId
import org.bson.json.JsonWriterSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.view.View


import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.runBlocking
import me.amitshekhar.ridesharing.R
import me.amitshekhar.ridesharing.data.network.NetworkService
import me.amitshekhar.ridesharing.databinding.ActivityMapsBinding
import me.amitshekhar.ridesharing.utils.AnimationUtils
import me.amitshekhar.ridesharing.utils.MapUtils
import me.amitshekhar.ridesharing.utils.PermissionUtils
import me.amitshekhar.ridesharing.utils.ViewUtils
import java.net.URI
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth


import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import org.bson.Document
import kotlinx.coroutines.runBlocking
import me.amitshekhar.ridesharing.Login
import me.amitshekhar.ridesharing.databinding.ActivityCustomerBinding
import me.amitshekhar.ridesharing.ui.maps.MapsActivity
import me.amitshekhar.ridesharing.ui.maps.MapsActivity.fleetPosition
import me.amitshekhar.ridesharing.ui.maps.MapsPresenter
import me.amitshekhar.ridesharing.ui.maps.MapsView
import org.bson.BsonTimestamp




class customerActivity : AppCompatActivity(), MapsView, OnMapReadyCallback{

    companion object {
        private const val TAG = "customerActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2
    }


    private lateinit var binding: ActivityCustomerBinding
    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng: LatLng? = null
    private var pickUpLatLng: LatLng? = null
    private var dropLatLng: LatLng? = null
    private val nearbyCabMarkerList = arrayListOf<Marker>()
    private var destinationMarker: Marker? = null
    private var originMarker: Marker? = null
    private var greyPolyLine: Polyline? = null
    private var blackPolyline: Polyline? = null
    private var previousLatLngFromServer: LatLng? = null
    private var currentLatLngFromServer: LatLng? = null
    private var movingCabMarker: Marker? = null




    val connectionStringUri = "mongodb://0.tcp.ap.ngrok.io:16531/" // Replace with your actual connection string

    val settings = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(connectionStringUri))
        .build()

    val mongoClient = MongoClient.create(settings)
    val database = mongoClient.getDatabase("commoride_main") // Use your database name
    val fleetCollection = database.getCollection<fleetPosition>("fleetPosition")
    val unloadCollection = database.getCollection<unloadingPosition>("requestOrder")
    data class unloadingPosition(val unloadingUsername: String, val droplatitude: String, val droplongitude: String,val fleetlatitude: String, val fleetlongitude: String, val capacity: Double,val timestamp: String)
    data class fleetPosition(val fleetUsername: String, val latitude: String, val longitude: String, val capacity: Double,val timestamp: String)
    val user = Firebase.auth.currentUser?.uid
    private fun connectDB(){
        runBlocking {



            try {


                println("Pinged your deployment. You successfully connected to MongoDB!")
            }
            catch (e: Exception) {
                println("Connection failed: $e")
            } finally {
                println("Connection failed")
                mongoClient.close()
            }
        }

    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        //presenter = MapsPresenter(NetworkService())
        //presenter.onAttach(this)
        //connectDB()
        //memangggil untuk fungsi standby ketika di klik
        setUpClickListener()

    }
    private fun setUpClickListener() {
        //origin listener autocomplete
        binding.currentDropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }
        //tujuan listener
        binding.fleetPositionTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        binding.requestCabButton.setOnClickListener {
            binding.statusTextView.visibility = View.VISIBLE
            binding.statusTextView.text = getString(R.string.requesting_your_cab)
            binding.requestCabButton.isEnabled = false
            //binding.currentDropTextView.isEnabled = false
            //binding.fleetPositionTextView.isEnabled = false
            //presenter.requestCab(pickUpLatLng!!, dropLatLng!!)

            runBlocking {

                val nowLocalDateTime= Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                println("Current UTC Instant: $nowLocalDateTime")
                val command = Document("ping", 1)

                val volume: Double? = binding.quantityInputTextView.text.toString().toDoubleOrNull()
                if (volume == null){
                    val doc = unloadingPosition(user.toString(), currentLatLng!!.latitude.toString(), currentLatLng!!.longitude.toString(), dropLatLng!!.latitude.toString(),dropLatLng!!.longitude.toString(), 1000200.00, nowLocalDateTime.toString())
                    val result = unloadCollection.insertOne(doc)
                    println("Document inserted successfully!")
                    println("Inserted ID: $result.insertedId")
                }

            }



        }

//        binding.logoutButton.setOnClickListener {
//            FirebaseAuth.getInstance().signOut()
//            val intent = Intent(this, Login::class.java)
//            startActivity(intent)
//
//        }

//        binding.logoutButton.setOnClickListener {
//            FirebaseAuth.getInstance().signOut()
//            val intent = Intent(this, Login::class.java)
//            startActivity(intent)
//
//        }

    }
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStart() {
        super.onStart()
        // check if get gps
        if (currentLatLng == null) {
            when {
                PermissionUtils.isAccessFineLocationGranted(this) -> {
                    when {
                        PermissionUtils.isLocationEnabled(this) -> {
                            setUpLocationListener()
                        }

                        else -> {
                            PermissionUtils.showGPSNotEnabledDialog(this)
                        }
                    }
                }

                else -> {
                    PermissionUtils.requestAccessFineLocationPermission(
                        this,
                        customerActivity.Companion.LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == customerActivity.Companion.PICKUP_REQUEST_CODE || requestCode == customerActivity.Companion.DROP_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    Log.d(customerActivity.Companion.TAG, "Place: " + place.name + ", " + place.id + ", " + place.latLng)
                    when (requestCode) {
                        customerActivity.Companion.PICKUP_REQUEST_CODE -> {
                            binding.currentDropTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }

                        customerActivity.Companion.DROP_REQUEST_CODE -> {
                            binding.fleetPositionTextView.text = place.name
                            dropLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                    }
                }

                AutocompleteActivity.RESULT_ERROR -> {
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(customerActivity.Companion.TAG, status.statusMessage!!)
                }

                RESULT_CANCELED -> {
                    Log.d(customerActivity.Companion.TAG, "Place Selection Canceled")
                }
            }
        }
    }


    override fun onDestroy() {
        presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }




    private fun moveCamera(latLng: LatLng) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }
    private fun launchLocationAutoCompleteActivity(requestCode: Int) {
        val fields: List<Place.Field> =
            listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(this)
        startActivityForResult(intent, requestCode)
    }

    private fun animateCamera(latLng: LatLng) {
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(80f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    private fun addCarMarkerAndGet(latLng: LatLng): Marker? {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitmap(this))
        return googleMap.addMarker(
            MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
        )
    }

    private fun addOriginDestinationMarkerAndGet(latLng: LatLng): Marker? {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getDestinationBitmap())
        return googleMap.addMarker(
            MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor)
        )
    }


    private fun setCurrentLocationAsDrop() {//lokasi bongkar
        dropLatLng = currentLatLng
        binding.currentDropTextView.text = currentLatLng.toString()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun enableMyLocationOnMap() {
        googleMap.setPadding(0, ViewUtils.dpToPx(48f), 0, 0)
        googleMap.isMyLocationEnabled = true
    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun setUpLocationListener() {
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        // for getting the current location update after every 2 seconds
        val locationRequest = LocationRequest().setInterval(10000).setFastestInterval(10000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

        locationCallback = object : LocationCallback() {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                Log.d("live_location", locationResult.locations.toString())
                //for (location in locationResult.locations) {
                //if (currentLatLng == null) {
                var lat = locationResult.locations[0].latitude
                var long =locationResult.locations[0].longitude
                currentLatLng = LatLng(lat, long)
                Log.d("live_location", lat.toString())
                Log.d("live_location", long.toString())

                setCurrentLocationAsDrop()
                enableMyLocationOnMap()

                moveCamera(currentLatLng!!)
                animateCamera(currentLatLng!!)

                //presenter.requestNearbyCabs(currentLatLng!!)
            }
        }
        fusedLocationProviderClient?.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }

    private fun checkAndShowRequestButton() {
        if (pickUpLatLng !== null && dropLatLng !== null) {
            binding.requestCabButton.visibility = View.VISIBLE
            binding.requestCabButton.isEnabled = true
        }
    }






    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            customerActivity.Companion.LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    when {
                        PermissionUtils.isLocationEnabled(this) -> {
                            setUpLocationListener()

                        }

                        else -> {
                            PermissionUtils.showGPSNotEnabledDialog(this)
                        }
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.location_permission_not_granted),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }


    override fun showNearbyCabs(latLngList: List<LatLng>) {
        TODO("Not yet implemented")
    }

    override fun informCabBooked() {
        TODO("Not yet implemented")
    }

    override fun showPath(latLngList: List<LatLng>) {
        TODO("Not yet implemented")
    }

    override fun updateCabLocation(latLng: LatLng) {
        TODO("Not yet implemented")
    }

    override fun informCabIsArriving() {
        TODO("Not yet implemented")
    }

    override fun informCabArrived() {
        TODO("Not yet implemented")
    }

    override fun informTripStart() {
        TODO("Not yet implemented")
    }

    override fun informTripEnd() {
        TODO("Not yet implemented")
    }

    override fun showRoutesNotAvailableError() {
        TODO("Not yet implemented")
    }

    override fun showDirectionApiFailedError(error: String) {
        TODO("Not yet implemented")
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

}
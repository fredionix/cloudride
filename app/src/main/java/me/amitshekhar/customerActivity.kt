package me.amitshekhar


import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import me.amitshekhar.ridesharing.R
import me.amitshekhar.ridesharing.databinding.ActivityCustomerBinding
import me.amitshekhar.ridesharing.ui.maps.MapsPresenter
import me.amitshekhar.ridesharing.ui.maps.MapsView
import me.amitshekhar.ridesharing.utils.MapUtils
import me.amitshekhar.ridesharing.utils.PermissionUtils
import me.amitshekhar.ridesharing.utils.ViewUtils
import org.bson.Document
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.time.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds


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




    val connectionStringUri = "mongodb://0.tcp.ap.ngrok.io:19819/" // Replace with your actual connection string

    val settings = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(connectionStringUri))
        .build()

    val mongoClient = MongoClient.create(settings)
    val database = mongoClient.getDatabase("commoride_main") // Use your database name
    val fleetCollection = database.getCollection<fleetPosition>("fleetPosition")
    val unloadCollection = database.getCollection<unloadingPosition>("requestOrder")
    data class unloadingPosition(val unloadingUsername: String, val droplatitude: String, val droplongitude: String, val fleetlatitude: String,
                                 val fleetlongitude: String, val capacity: Double, val fleetUsername: String, val estimatedDistance:Long, val estimatedTravelTime: Long,
                                 val status : Int, val currentDate: String , val timestamp: String, val epochMilisecond:Long)
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





    }

    fun getDistanceBetween(
        startLatitude: Double,
        startLongitude: Double,
        destinationLatitude: Double,
        destinationLongitude: Double
    ): Long {
        val locationA = Location("point A").apply {
            latitude = startLatitude
            longitude = startLongitude
        }
        val locationB = Location("point B").apply {
            latitude = destinationLatitude
            longitude = destinationLongitude
        }

        // distanceTo returns the distance in meters
        return locationA.distanceTo(locationB).toLong()
    }
    private fun setUpClickListener() {
        //origin listener autocomplete
        binding.currentDropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }
        //tujuan listener
//        binding.fleetPositionTextView.setOnClickListener {
//            //launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
//            binding.fleetPositionTextView.isEnabled = true
//        }
        binding.requestCabButton.setOnClickListener {
            binding.statusTextView.visibility = View.VISIBLE
            binding.statusTextView.text = getString(R.string.requesting_your_cab)
            binding.requestCabButton.isEnabled = false
            var volumeRequested: Double = 1000.0
            //val volumeRequested2: Double? = binding.quantityInputTextView.text.toString().toDoubleOrNull()
            //binding.currentDropTextView.isEnabled = false
            //binding.fleetPositionTextView.isEnabled = false
            //presenter.requestCab(pickUpLatLng!!, dropLatLng!!)

                // 2. Select with a filter (e.g., field "status" equals "active")
                //val filteredDocs = fleetCollection.find(Filters.eq("cargoType", "CNG")).toList()



            runBlocking {
                //filter only product that requested by customer and at its capacity and on time target
                val filter = Filters.and(Filters.eq("cargoType","CNG" ))
                val fleetList = fleetCollection.find(filter).toList()
                var nearestDistance = 99999999999999999
                var distance: Long = 0L

                var fleetNearestLatitude:String=""
                var fleetNearestLongitude:String=""
                var fleetNearestUsername:String = ""
                for (fleet in fleetList) {
                    Log.d("fleetList", fleet.fleetUsername)
                    Log.d("fleetList", fleet.latitude+","+fleet.longitude.toDouble())
                    distance = getDistanceBetween(fleet.latitude.toDouble(), fleet.longitude.toDouble(), currentLatLng!!.latitude.toDouble(), currentLatLng!!.longitude.toDouble()).toLong()
                    if (distance < 50000  && fleet.capacity >= volumeRequested ) {
                        //get nearest distance
                        if(nearestDistance > distance){
                            nearestDistance = distance
                            fleetNearestUsername = fleet.fleetUsername
                            fleetNearestLatitude = fleet.latitude.toString()
                            fleetNearestLongitude = fleet.longitude.toString()

                        }
                        Log.d("fleetNearest", fleet.fleetUsername+", "+nearestDistance+" M")
                    }
                //end of populating list
                }



                val spinner: Spinner = findViewById(R.id.fleetPositionTextView)
                val fruits = arrayOf("Apple", "Mango", "Banana")
                val itemList = listOf("Option 1", "Option 2", "Option 3", "Option 4")
                val countries = arrayOf("Indonesia", "United States", "United Kingdom", "Australia", "Japan")

                binding.fleetPositionTextView.isEnabled = true

                //update the nearest fleet cargo volume
//                val collection = database.getCollection<Document>("fleetPosition")
//                // Increment the "score" field by 10
//                val result = collection.updateOne(
//                    eq("fleetUsername", fleetNearestUsername),
//                    Updates.inc("capacity", -volumeRequested)
//                )
//                Log.d("fleetNearest", "Documents matched: ${result.matchedCount}")



                val nowLocalDateTime= Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                println("Current UTC Instant: $nowLocalDateTime")
                val epochMilisecond : Long = Clock.System.now().toEpochMilliseconds()



                val simpleDateFormat = SimpleDateFormat("ddMMYYYY", Locale.getDefault())
                val currentDate: String = simpleDateFormat.format(Date())

                val command = Document("ping", 1)
                var estimatedTravelTime : Long = distance/333
                //status dict
                //0 created, antri
                //1 dipickup fleet / on going / otw
                //2 arrive at unloading
                //3 unloading complete / customer confirm


                if (volumeRequested != null || volumeRequested!=0.0){
                    val doc = unloadingPosition(
                        user.toString(),
                        currentLatLng!!.latitude.toString(), currentLatLng!!.longitude.toString(),
                        fleetNearestLatitude,fleetNearestLongitude,
                        volumeRequested,
                        fleetNearestUsername,
                        distance,estimatedTravelTime,
                        0,
                        currentDate,
                        nowLocalDateTime.toString(), epochMilisecond)
                    val result = unloadCollection.insertOne(doc)
                    println("Document inserted successfully!")
                    Log.d("fleetNearest","Inserted ID: $result.insertedId")
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
                            setUpClickListener()
                        }

                        else -> {
                            PermissionUtils.showGPSNotEnabledDialog(this)
                        }
                    }
                }

                else -> {
                    PermissionUtils.requestAccessFineLocationPermission(
                        this,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    Log.d(TAG, "Place: " + place.name + ", " + place.id + ", " + place.latLng)
                    when (requestCode) {
                        PICKUP_REQUEST_CODE -> {
                            binding.currentDropTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }

                        DROP_REQUEST_CODE -> {
                            //binding.fleetPositionTextView.text = place.name
                            //dropLatLng = place.latLng
                            //checkAndShowRequestButton()
                        }
                    }
                }

                AutocompleteActivity.RESULT_ERROR -> {
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(TAG, status.statusMessage!!)
                }

                RESULT_CANCELED -> {
                    Log.d(TAG, "Place Selection Canceled")
                }
            }
        }
    }


    override fun onDestroy() {
        //presenter.onDetach()
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
            LOCATION_PERMISSION_REQUEST_CODE -> {
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
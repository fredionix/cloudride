package me.amitshekhar.ridesharing.ui.maps
import android.Manifest
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
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View

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
import android.widget.Toast
import androidx.annotation.RequiresPermission


import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import org.bson.Document
import kotlinx.coroutines.runBlocking
import org.bson.BsonTimestamp


class MapsActivity : AppCompatActivity(), MapsView, OnMapReadyCallback {

    companion object {
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2
    }

    private lateinit var binding: ActivityMapsBinding
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

    val connectionStringUri = "mongodb://0.tcp.ap.ngrok.io:14959/" // Replace with your actual connection string

    val settings = MongoClientSettings.builder()
        .applyConnectionString(ConnectionString(connectionStringUri))
        .build()

    val mongoClient = MongoClient.create(settings)
    val database = mongoClient.getDatabase("commoride_main") // Use your database name
    val collection = database.getCollection<fleetStatus>("masterFleet")

    data class fleetStatus(val fleetLicensePlate: String, val latitude: String, val longitude: String, val capacity: Double,val timestamp: String)

    private fun connectDB(){
        runBlocking {



            try {


                println("Pinged your deployment. You successfully connected to MongoDB!")
            } catch (e: Exception) {
                println("Connection failed: $e")
            } finally {
                println("Connection failed")
                mongoClient.close()
            }
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //diatas layout
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)
        //connectDB()





        //memangggil untuk fungsi standby ketika di klik
        setUpClickListener()
    }
//    fun getDatabase(): MongoDatabase{
//
//
//        val client = MongoClient.create(connectionString = "mongodb://0.tcp.ap.ngrok.io:14959/")
//        return client.getDatabase(databaseName = "commoride_main")
//    }
    private fun setUpClickListener() {
        //origin listener autocomplete
        binding.pickUpTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }
        //tujuan listener
        binding.dropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        binding.requestCabButton.setOnClickListener {
            binding.statusTextView.visibility = View.VISIBLE
            binding.statusTextView.text = getString(R.string.requesting_your_cab)
            binding.requestCabButton.isEnabled = false
            binding.pickUpTextView.isEnabled = false
            binding.dropTextView.isEnabled = false
            presenter.requestCab(pickUpLatLng!!, dropLatLng!!)
            //Intent intent = new Intent(Intent.ACTION_VIEW)
            //val uri = Uri.parse("http://maps.google.co.in/maps?q=loc:-7.247302, 112.647553")
            //val intent = Intent(Intent.ACTION_VIEW, uri)
            //intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            //startActivity(intent)


        }
        binding.nextRideButton.setOnClickListener {
            reset()
        }
    }

    private fun launchLocationAutoCompleteActivity(requestCode: Int) {
        val fields: List<Place.Field> =
            listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields)
            .build(this)
        startActivityForResult(intent, requestCode)
    }

    private fun moveCamera(latLng: LatLng) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
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

    private fun setCurrentLocationAsPickUp() {
        pickUpLatLng = currentLatLng
        binding.pickUpTextView.text = currentLatLng.toString()
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
        val locationRequest = LocationRequest().setInterval(5000).setFastestInterval(5000)
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



                //val command2 = BsonDocument("dbStats", BsonInt64(1))
                //val commandResult = database.runCommand(command2)
                //println(commandResult.toJson(JsonWriterSettings.builder().indent(true).build()))s



//                val doc = fleetStatus("L 5701 DDY", lat.toString(),long.toString(), 1000.0,Clock.System.now())
//                val result = collection.insertOne(doc)
//                println("Document inserted successfully!")
//                val insertedId = result.insertedId
//                println("Inserted ID: $insertedId")


                setCurrentLocationAsPickUp()
                enableMyLocationOnMap()

                moveCamera(currentLatLng!!)
                animateCamera(currentLatLng!!)
                runBlocking {

                    val now: Instant = Clock.System.now()
                    println("Current UTC Instant: $now")
                    val command = Document("ping", 1)
                    val doc = fleetStatus("L 5701 DDY", lat.toString(), long.toString(), 1000.0, now.toString())
                    val result = collection.insertOne(doc)
                    println("Document inserted successfully!")
                    val insertedId = result.insertedId
                    println("Inserted ID: $insertedId")
                }
                        //presenter.requestNearbyCabs(currentLatLng!!)
                    //}
                //}



                // Few more things we can do here:
                // For example: Update the location of user on server
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

    private fun reset() {
        binding.statusTextView.visibility = View.GONE
        binding.nextRideButton.visibility = View.GONE
        nearbyCabMarkerList.forEach { it.remove() }
        nearbyCabMarkerList.clear()
        previousLatLngFromServer = null
        currentLatLngFromServer = null
        if (currentLatLng != null) {
            moveCamera(currentLatLng!!)
            animateCamera(currentLatLng!!)
            setCurrentLocationAsPickUp()
            presenter.requestNearbyCabs(currentLatLng!!)
        } else {
            binding.pickUpTextView.text = ""
        }
        binding.pickUpTextView.isEnabled = true
        binding.dropTextView.isEnabled = true
        binding.dropTextView.text = ""
        movingCabMarker?.remove()
        greyPolyLine?.remove()
        blackPolyline?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
        dropLatLng = null
        greyPolyLine = null
        blackPolyline = null
        originMarker = null
        destinationMarker = null
        movingCabMarker = null
    }

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
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE) {
            when (resultCode) {
                RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    Log.d(TAG, "Place: " + place.name + ", " + place.id + ", " + place.latLng)
                    when (requestCode) {
                        PICKUP_REQUEST_CODE -> {
                            binding.pickUpTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }

                        DROP_REQUEST_CODE -> {
                            binding.dropTextView.text = place.name
                            dropLatLng = place.latLng
                            checkAndShowRequestButton()
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

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    override fun showNearbyCabs(latLngList: List<LatLng>) {
        nearbyCabMarkerList.clear()
        for (latLng in latLngList) {
            val nearbyCabMarker = addCarMarkerAndGet(latLng)
            nearbyCabMarkerList.add(nearbyCabMarker!!)
        }
    }

    override fun informCabBooked() {
        nearbyCabMarkerList.forEach { it.remove() }
        nearbyCabMarkerList.clear()
        binding.requestCabButton.visibility = View.GONE
        binding.statusTextView.text = getString(R.string.your_cab_is_booked)
    }

    override fun showPath(latLngList: List<LatLng>) {
        val builder = LatLngBounds.Builder()
        for (latLng in latLngList) {
            builder.include(latLng)
        }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 2))
        val polylineOptions = PolylineOptions()
        polylineOptions.color(Color.GRAY)
        polylineOptions.width(5f)
        polylineOptions.addAll(latLngList)
        greyPolyLine = googleMap.addPolyline(polylineOptions)

        val blackPolylineOptions = PolylineOptions()
        blackPolylineOptions.width(5f)
        blackPolylineOptions.color(Color.BLACK)
        blackPolyline = googleMap.addPolyline(blackPolylineOptions)

        originMarker = addOriginDestinationMarkerAndGet(latLngList[0])
        originMarker?.setAnchor(0.5f, 0.5f)
        destinationMarker = addOriginDestinationMarkerAndGet(latLngList[latLngList.size - 1])
        destinationMarker?.setAnchor(0.5f, 0.5f)

        val polylineAnimator = AnimationUtils.polyLineAnimator()
        polylineAnimator.addUpdateListener { valueAnimator ->
            val percentValue = (valueAnimator.animatedValue as Int)
            val index = (greyPolyLine?.points!!.size * (percentValue / 100.0f)).toInt()
            blackPolyline?.points = greyPolyLine?.points!!.subList(0, index)
        }
        polylineAnimator.start()
    }
//membuuat marker berjalan
    //input latlng update
    override fun updateCabLocation(latLng: LatLng) {
        if (movingCabMarker == null) {
            movingCabMarker = addCarMarkerAndGet(latLng)
        }
        if (previousLatLngFromServer == null) {
            currentLatLngFromServer = latLng //assign current latest potition
            previousLatLngFromServer = currentLatLngFromServer //assign sama
            movingCabMarker?.position = currentLatLngFromServer!! //assign sama posisi
            movingCabMarker?.setAnchor(0.5f, 0.5f) // gambar
            animateCamera(currentLatLngFromServer!!) // animasi
        } else {
            previousLatLngFromServer = currentLatLngFromServer // current yang sebelumnya disimpan, sebagai previous sebelum di overwrite
            currentLatLngFromServer = latLng //assign new latest latLng
            val valueAnimator = AnimationUtils.cabAnimator()//animate
            valueAnimator.addUpdateListener { va ->
                if (currentLatLngFromServer != null && previousLatLngFromServer != null) {
                    val multiplier = va.animatedFraction
                    // buat animasi sebenernya ini ditarik dari server sesuai posisi driver
                    // next location latitude longitude dari posisi broadcast driver
                    val nextLocation = LatLng(
                        multiplier * currentLatLngFromServer!!.latitude + (1 - multiplier) * previousLatLngFromServer!!.latitude,
                        multiplier * currentLatLngFromServer!!.longitude + (1 - multiplier) * previousLatLngFromServer!!.longitude
                    )
                    movingCabMarker?.position = nextLocation
                    movingCabMarker?.setAnchor(0.5f, 0.5f)
                    val rotation = MapUtils.getRotation(previousLatLngFromServer!!, nextLocation)
                    if (!rotation.isNaN()) {
                        movingCabMarker?.rotation = rotation
                    }
                    animateCamera(nextLocation)
                }
            }
            valueAnimator.start()
        }
    }

    override fun informCabIsArriving() {
        binding.statusTextView.text = getString(R.string.your_cab_is_arriving)
    }

    override fun informCabArrived() {
        binding.statusTextView.text = getString(R.string.your_cab_has_arrived)
        greyPolyLine?.remove()
        blackPolyline?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun informTripStart() {
        binding.statusTextView.text = getString(R.string.you_are_on_a_trip)
        previousLatLngFromServer = null
    }

    override fun informTripEnd() {
        binding.statusTextView.text = getString(R.string.trip_end)
        binding.nextRideButton.visibility = View.VISIBLE
        greyPolyLine?.remove()
        blackPolyline?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun showRoutesNotAvailableError() {
        val error = getString(R.string.route_not_available_choose_different_locations)
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        reset()
    }

    override fun showDirectionApiFailedError(error: String) {
        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        reset()
    }

}

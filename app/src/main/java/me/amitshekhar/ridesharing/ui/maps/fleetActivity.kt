package me.amitshekhar.ridesharing.ui.maps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
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
import me.amitshekhar.ridesharing.R
import me.amitshekhar.ridesharing.data.network.NetworkService
import me.amitshekhar.ridesharing.databinding.ActivityMapsBinding
import me.amitshekhar.ridesharing.utils.AnimationUtils
import me.amitshekhar.ridesharing.utils.MapUtils
import me.amitshekhar.ridesharing.utils.PermissionUtils
import me.amitshekhar.ridesharing.utils.ViewUtils

class fleetActivity : AppCompatActivity(), OnMapReadyCallback {


    companion object {
        private const val TAG = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2
    }



    private lateinit var binding: ActivityMapsBinding
    private lateinit var presenter: MapsPresenter
    var userLat: Double?=null
    var userLong: Double?=null


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

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        enableMyLocationOnMap()
        googleMap.setOnMapClickListener(GoogleMap.OnMapClickListener(){

        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //diatas layout
        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewUtils.enableTransparentStatusBar(window)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        if(mapFragment!=null){
            mapFragment.getMapAsync(this)
        }



        //presenter = MapsPresenter(NetworkService())
        //presenter.onAttach(this)
        //memangggil untuk fungsi standby ketika di klik
        setUpClickListener()
    }

    private fun setUpClickListener() {
        //origin listener autocomplete
//        binding.pickUpTextView.setOnClickListener {
//            //launchLocationAutoCompleteActivity(MapsActivity.Companion.PICKUP_REQUEST_CODE)
//        }
//        //tujuan listener
//        binding.dropTextView.setOnClickListener {
//            //launchLocationAutoCompleteActivity(MapsActivity.Companion.DROP_REQUEST_CODE)
//        }
        binding.requestCabButton.setOnClickListener {
            binding.statusTextView.visibility = View.VISIBLE
            binding.statusTextView.text = getString(R.string.requesting_your_cab)
            binding.requestCabButton.isEnabled = false
            binding.pickUpTextView.isEnabled = false
            binding.dropTextView.isEnabled = false
            presenter.requestCab(pickUpLatLng!!, dropLatLng!!)

        }
        binding.nextRideButton.setOnClickListener {
            //reset()
        }
    }

    private fun moveCamera(latLng: LatLng) {

        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    private fun animateCamera(latLng: LatLng) {
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(25f).build()
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

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun enableMyLocationOnMap() {
        googleMap.setPadding(0, ViewUtils.dpToPx(48f), 0, 0)
        googleMap.isMyLocationEnabled = true
        googleMap.uiSettings.isZoomControlsEnabled = true
    }

    //to database  remote
    private fun broadcastLocation() {
        //currentLatLng
        //remote database insert
        //end close

    }


    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun setUpLocationListener() {
        fusedLocationProviderClient = FusedLocationProviderClient(this)

        // for getting the current location update after every 2 seconds
        val locationRequest = LocationRequest().setInterval(3000).setFastestInterval(3000)
            .setPriority(PRIORITY_HIGH_ACCURACY)

        locationCallback = object : LocationCallback() {
            @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                if (currentLatLng == null) {
                    for (location in locationResult.locations) {
                        //if (currentLatLng == null) {
                        currentLatLng = LatLng(location.latitude, location.longitude)
                        //setCurrentLocationAsPickUp()

                        //moveCamera(currentLatLng!!)
                        animateCamera(currentLatLng!!)
                        addCarMarkerAndGet(currentLatLng!!)
                        //presenter.requestNearbyCabs(currentLatLng!!)
                        //}

                    }

                }

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

    fun getListOfLocations(): ArrayList<LatLng> {
        val locationList = ArrayList<LatLng>()
        locationList.add(LatLng(28.436970000000002, 77.11272000000001))
        locationList.add(LatLng(28.43635, 77.11289000000001))
        locationList.add(LatLng(28.4353, 77.11317000000001))
        locationList.add(LatLng(28.435280000000002, 77.11332))
        locationList.add(LatLng(28.435350000000003, 77.11368))
        locationList.add(LatLng(28.4356, 77.11498))
        locationList.add(LatLng(28.435660000000002, 77.11519000000001))
        locationList.add(LatLng(28.43568, 77.11521))
        locationList.add(LatLng(28.436580000000003, 77.11499))
        locationList.add(LatLng(28.436590000000002, 77.11507))
        return locationList
    }

    fun updateCabLocation(latLng: LatLng) {
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
            if (currentLatLngFromServer != null && previousLatLngFromServer != null) {
                movingCabMarker?.position = latLng
                movingCabMarker?.setAnchor(0.5f, 0.5f)
            }



        }
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
                        fleetActivity.Companion.LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    override fun onDestroy() {
       // presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }









}
package me.amitshekhar.ridesharing.simulator

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.maps.DirectionsApiRequest
import com.google.maps.GeoApiContext
import com.google.maps.PendingResult
import com.google.maps.model.DirectionsResult
import com.google.maps.model.LatLng
import com.google.maps.model.TravelMode
import org.json.JSONArray
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask

object Simulator {

    private const val TAG = "Simulator"
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null
    lateinit var geoApiContext: GeoApiContext
    private lateinit var currentLocation: LatLng//lokasi saat ini
    private lateinit var pickUpLocation: LatLng //lokasi penjemputan
    private lateinit var dropLocation: LatLng // lokasi tujuan
    private var nearbyCabLocations = arrayListOf<LatLng>()//wadah untuk membuat pool mobil terdekat
    private var pickUpPath = arrayListOf<LatLng>()//path untuk jalan pickup digenerate sama google map
    private var tripPath = arrayListOf<LatLng>()//rute untuk perjalanan di generate sama google map
    private val mainThread = Handler(Looper.getMainLooper())

    fun getFakeNearbyCabLocations(latitude: Double, longitude: Double, webSocketListener: WebSocketListener) {
        nearbyCabLocations.clear()//delete semua dulu reset
        currentLocation = LatLng(latitude, longitude)//assign dari gps mobil pelanggan
        val size = (4..10).random() //lebih dari 4 kurang dari 10
        //pembentukan random lat long 10 max fake taxi
        for (i in 1..size) {
            val randomOperatorForLat = (0..1).random()
            val randomOperatorForLng = (0..1).random()
            var randomDeltaForLat = (10..50).random() / 10000.00
            var randomDeltaForLng = (10..50).random() / 10000.00
            if (randomOperatorForLat == 1) {
                randomDeltaForLat *= -1
            }
            if (randomOperatorForLng == 1) {
                randomDeltaForLng *= -1
            }
            val randomLatitude = (latitude + randomDeltaForLat).coerceAtMost(90.00)
            val randomLongitude = (longitude + randomDeltaForLng).coerceAtMost(180.00)
            nearbyCabLocations.add(LatLng(randomLatitude, randomLongitude))//masukan dalam list nearby taxi
            //kalau pake sesungguhnya perlu populate dari database realtime
        }

        //buat json object untuk masing masing posisi cabs terdekat dari array yang di populate
        val jsonObjectToPush = JSONObject()//buat json container
        jsonObjectToPush.put("type", "nearByCabs")
        val jsonArray = JSONArray()
        for (location in nearbyCabLocations) {
            val jsonObjectLatLng = JSONObject()
            jsonObjectLatLng.put("lat", location.lat)
            jsonObjectLatLng.put("lng", location.lng)
            jsonArray.put(jsonObjectLatLng)
        }
        jsonObjectToPush.put("locations", jsonArray)
        //masukan sebagai tipe nearby cabs dan locations list latlng
        mainThread.post {
            webSocketListener.onMessage(jsonObjectToPush.toString())
        }
    }

    fun requestCab(
        pickUpLocation: LatLng,
        dropLocation: LatLng,
        webSocketListener: WebSocketListener
    ) {
        //assign pickup sama drop location
        this.pickUpLocation = pickUpLocation
        this.dropLocation = dropLocation
        //dibuat lagi random bukan dari hitungan fake taxi function location
        val randomOperatorForLat = (0..1).random()
        val randomOperatorForLng = (0..1).random()

        var randomDeltaForLat = (5..30).random() / 10000.00
        var randomDeltaForLng = (5..30).random() / 10000.00

        if (randomOperatorForLat == 1) {
            randomDeltaForLat *= -1
        }
        if (randomOperatorForLng == 1) {
            randomDeltaForLng *= -1
        }
        //simpan posisi paling dekat
        val latFakeNearby = (pickUpLocation.lat + randomDeltaForLat).coerceAtMost(90.00)
        val lngFakeNearby = (pickUpLocation.lng + randomDeltaForLng).coerceAtMost(180.00)
        //dari fake di assign ke object lat lng google map
        val bookedCabCurrentLocation = LatLng(latFakeNearby, lngFakeNearby)
        //buat context object direction api
        val directionsApiRequest = DirectionsApiRequest(geoApiContext)
        //assing travel mode mobil, origin dari object latlng fake , destination dari pickup location
        directionsApiRequest.mode(TravelMode.DRIVING)
        directionsApiRequest.origin(bookedCabCurrentLocation)
        directionsApiRequest.destination(this.pickUpLocation)

        directionsApiRequest.setCallback(object : PendingResult.Callback<DirectionsResult> {
            override fun onResult(result: DirectionsResult) {
                Log.d(TAG, "onResult establishment: ${result.geocodedWaypoints[0].types[0]}")
                Log.d(TAG, "onResult point of interest: ${result.geocodedWaypoints[0].types[1]}")
                val jsonObjectCabBooked = JSONObject()
                jsonObjectCabBooked.put("type", "cabBooked")
                // masukan sebagai tipe cabbooked tanpa latitude longitude
                mainThread.post {
                    webSocketListener.onMessage(jsonObjectCabBooked.toString())
                }


                pickUpPath.clear()
                val routeList = result.routes
                // Actually it will have zero or 1 route as we haven't asked Google API for multiple paths

                if (routeList.isEmpty()) {//jika kosong rutenya g ada, maka bilang error rood not availaable
                    val jsonObjectFailure = JSONObject()
                    jsonObjectFailure.put("type", "routesNotAvailable")
                    mainThread.post {
                        webSocketListener.onError(jsonObjectFailure.toString())
                    }
                } else {//jika ketemu
                    //loop route list array object rout
                    for (route in routeList) {
                        val path = route.overviewPolyline.decodePath()//setiap belokan
                        pickUpPath.addAll(path)//di assign di rute pickup
                    }

                    val jsonObject = JSONObject()
                    jsonObject.put("type", "pickUpPath")
                    val jsonArray = JSONArray()
                    //loop pickup path yang di save
                    for (pickUp in pickUpPath) {
                        val jsonObjectLatLng = JSONObject()
                        jsonObjectLatLng.put("lat", pickUp.lat)
                        jsonObjectLatLng.put("lng", pickUp.lng)
                        jsonArray.put(jsonObjectLatLng)
                    }
                    jsonObject.put("path", jsonArray)
                    mainThread.post {
                        webSocketListener.onMessage(jsonObject.toString())
                    }

                    startTimerForPickUp(webSocketListener)
                }


            }
            //jika ggagal bilang gagal
            override fun onFailure(e: Throwable) {
                Log.d(TAG, "onFailure : ${e.message}")
                val jsonObjectFailure = JSONObject()
                jsonObjectFailure.put("type", "directionApiFailed")
                jsonObjectFailure.put("error", e.message)
                mainThread.post {
                    webSocketListener.onError(jsonObjectFailure.toString())
                }
            }
        })
    }

    fun startTimerForPickUp(webSocketListener: WebSocketListener) {
        val delay = 2000L
        val period = 3000L
        val size = pickUpPath.size
        var index = 0
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                val jsonObject = JSONObject()
                jsonObject.put("type", "location")
                jsonObject.put("lat", pickUpPath[index].lat)
                jsonObject.put("lng", pickUpPath[index].lng)
                mainThread.post {
                    webSocketListener.onMessage(jsonObject.toString())
                }

                if (index == size - 1) {
                    stopTimer()
                    val jsonObjectCabIsArriving = JSONObject()
                    jsonObjectCabIsArriving.put("type", "cabIsArriving")
                    mainThread.post {
                        webSocketListener.onMessage(jsonObjectCabIsArriving.toString())
                    }
                    startTimerForWaitDuringPickUp(webSocketListener)
                }

                index++
            }
        }

        timer?.schedule(timerTask, delay, period)
    }

    fun startTimerForWaitDuringPickUp(webSocketListener: WebSocketListener) {
        val delay = 3000L
        val period = 3000L
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                stopTimer()
                val jsonObjectCabArrived = JSONObject()
                jsonObjectCabArrived.put("type", "cabArrived")
                mainThread.post {
                    webSocketListener.onMessage(jsonObjectCabArrived.toString())
                }
                val directionsApiRequest = DirectionsApiRequest(geoApiContext)
                directionsApiRequest.mode(TravelMode.DRIVING)

                //buat path untuk ke pickup dan drop
                directionsApiRequest.origin(pickUpLocation)
                directionsApiRequest.destination(dropLocation)
                directionsApiRequest.setCallback(object :
                    PendingResult.Callback<DirectionsResult> {
                    override fun onResult(result: DirectionsResult) {
                        Log.d(TAG, "hasil rute : ${result.toString()}")
                        tripPath.clear()
                        val routeList = result.routes
                        // Actually it will have zero or 1 route as we haven't asked Google API for multiple paths

                        if (routeList.isEmpty()) {
                            val jsonObjectFailure = JSONObject()
                            jsonObjectFailure.put("type", "routesNotAvailable")
                            mainThread.post {
                                webSocketListener.onError(jsonObjectFailure.toString())
                            }
                        } else {
                            for (route in routeList) {
                                val path = route.overviewPolyline.decodePath()
                                tripPath.addAll(path)
                            }
                            startTimerForTrip(webSocketListener)
                        }

                    }

                    override fun onFailure(e: Throwable) {
                        Log.d(TAG, "onFailure : ${e.message}")
                        val jsonObjectFailure = JSONObject()
                        jsonObjectFailure.put("type", "directionApiFailed")
                        jsonObjectFailure.put("error", e.message)
                        mainThread.post {
                            webSocketListener.onError(jsonObjectFailure.toString())
                        }
                    }
                })

            }
        }
        timer?.schedule(timerTask, delay, period)
    }

    fun startTimerForTrip(webSocketListener: WebSocketListener) {
        val delay = 5000L
        val period = 3000L
        val size = tripPath.size
        var index = 0
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {

                if (index == 0) {
                    val jsonObjectTripStart = JSONObject()
                    jsonObjectTripStart.put("type", "tripStart")
                    mainThread.post {
                        webSocketListener.onMessage(jsonObjectTripStart.toString())
                    }

                    val jsonObject = JSONObject()
                    jsonObject.put("type", "tripPath")
                    val jsonArray = JSONArray()
                    for (trip in tripPath) {
                        val jsonObjectLatLng = JSONObject()
                        jsonObjectLatLng.put("lat", trip.lat)
                        jsonObjectLatLng.put("lng", trip.lng)
                        jsonArray.put(jsonObjectLatLng)
                    }
                    jsonObject.put("path", jsonArray)
                    mainThread.post {
                        webSocketListener.onMessage(jsonObject.toString())
                    }
                }

                val jsonObject = JSONObject()
                jsonObject.put("type", "location")
                jsonObject.put("lat", tripPath[index].lat)
                jsonObject.put("lng", tripPath[index].lng)
                mainThread.post {
                    webSocketListener.onMessage(jsonObject.toString())
                }

                if (index == size - 1) {
                    stopTimer()
                    startTimerForTripEndEvent(webSocketListener)
                }

                index++
            }
        }
        timer?.schedule(timerTask, delay, period)
    }

    fun startTimerForTripEndEvent(webSocketListener: WebSocketListener) {
        val delay = 3000L
        val period = 3000L
        timer = Timer()
        timerTask = object : TimerTask() {
            override fun run() {
                stopTimer()
                val jsonObjectTripEnd = JSONObject()
                jsonObjectTripEnd.put("type", "tripEnd")
                mainThread.post {
                    webSocketListener.onMessage(jsonObjectTripEnd.toString())
                }
            }
        }
        timer?.schedule(timerTask, delay, period)
    }

    fun stopTimer() {
        if (timer != null) {
            timer?.cancel()
            timer = null
        }
    }

}
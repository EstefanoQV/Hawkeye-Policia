package com.example.policia

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.*
import java.util.*

class MainActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnInfoWindowClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var database: FirebaseDatabase
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var policeLocationReference: DatabaseReference
    private lateinit var clientLocationReference: DatabaseReference
    private var clientMarkers = HashMap<String, Marker>()
    private lateinit var deviceId: String

    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        database = FirebaseDatabase.getInstance()

        deviceId = generateDeviceId()

        // Inicializar la referencia a Firebase usando el ID único del dispositivo
        policeLocationReference = FirebaseDatabase.getInstance().getReference("policia").child(deviceId)
        clientLocationReference = FirebaseDatabase.getInstance().getReference("clientes")

        // Inicializar FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Configurar el LocationCallback
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    sendLocation(location.latitude, location.longitude)
                }
            }
        }

        // Iniciar actualizaciones de ubicación
        getLocationPermission()
    }

    private fun generateDeviceId(): String {
        val sharedPrefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        var id = sharedPrefs.getString("deviceId", null)

        if (id == null) {
            // Generar un nuevo ID único
            id = UUID.randomUUID().toString()

            // Guardar el ID único en SharedPreferences para futuras aperturas de la app
            with(sharedPrefs.edit()) {
                putString("deviceId", id)
                apply()
            }
        }

        return id ?: ""
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setInfoWindowAdapter(CustomInfoWindowAdapter(this))
        mMap.setOnInfoWindowClickListener(this)
        getLocationPermission()
        listenClientLocation()

        try {
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    this, R.raw.map_style
                )
            )
            if (!success) {
                Log.e("MainActivity", "Fallo al cargar el estilo del mapa.")
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error al cargar el estilo del mapa: ${e.message}")
        }
    }

    override fun onInfoWindowClick(marker: Marker) {
        val conductor = marker.title
        val placa = marker.snippet

        val clientId = marker.tag as? String // Asegúrate de que el tag del marcador contenga el clientId

        if (clientId != null) {
            clientLocationReference.child(clientId).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {
                    val origen = dataSnapshot.child("origen").getValue(String::class.java)
                    val destino = dataSnapshot.child("destino").getValue(String::class.java)

                    val bottomSheetFragment = NewActivity()
                    val bundle = Bundle().apply {
                        putString("conductor", conductor)
                        putString("placa", placa)
                        putString("origen", origen)
                        putString("destino", destino)
                    }
                    bottomSheetFragment.arguments = bundle
                    bottomSheetFragment.show(supportFragmentManager, bottomSheetFragment.tag)
                }

                override fun onCancelled(databaseError: DatabaseError) {
                    Log.e("MainActivity", "Error al obtener los datos del cliente: ${databaseError.message}")
                }
            })
        } else {
            Log.e("MainActivity", "El marcador no tiene un clientId asociado.")
        }
    }



    private fun getLocationPermission() {
        if (::mMap.isInitialized && ContextCompat.checkSelfPermission(
                this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            startLocationUpdates()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                mMap.isMyLocationEnabled = true
                startLocationUpdates()
            }
        }
    }

    private fun sendLocation(latitude: Double, longitude: Double) {
        val locationData = mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "estado" to "Activo"
        )
        policeLocationReference.setValue(locationData)
            .addOnFailureListener {
                Log.e("MainActivity", "Error al enviar ubicación: ${it.message}")
            }
    }

    private fun listenClientLocation() {
        clientLocationReference.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(dataSnapshot: DataSnapshot, previousChildName: String?) {
                updateClientMarker(dataSnapshot)
            }

            override fun onChildChanged(dataSnapshot: DataSnapshot, previousChildName: String?) {
                updateClientMarker(dataSnapshot)
            }

            override fun onChildRemoved(dataSnapshot: DataSnapshot) {
                val clientId = dataSnapshot.key ?: return
                removeClientMarker(clientId)
            }

            override fun onChildMoved(dataSnapshot: DataSnapshot, previousChildName: String?) {
                // No se necesita hacer nada en este caso
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Log.e("MainActivity", "Error de Firebase: ${databaseError.message}")
            }
        })
    }

    private fun updateClientMarker(dataSnapshot: DataSnapshot) {
        val clientLocation = dataSnapshot.getValue(ClienteLocation::class.java)
        clientLocation?.let {
            val clientId = dataSnapshot.key ?: return@let
            val location = LatLng(clientLocation.latitude, clientLocation.longitude)

            // Especificar el tamaño deseado para el marcador
            val markerWidth = 125
            val markerHeight = 130

            // Obtener icono del marcador según el estado del cliente y tamaño deseado
            val markerIcon = getMarkerIcon(clientLocation.estado, markerWidth, markerHeight)

            // Verificar si ya existe un marcador para este cliente y actualizarlo
            if (clientMarkers.containsKey(clientId)) {
                val existingMarker = clientMarkers[clientId]
                existingMarker?.position = location
                existingMarker?.setIcon(markerIcon)
                existingMarker?.title = "Conductor: ${clientLocation.nombreConductor}"
                existingMarker?.snippet = "Placa: ${clientLocation.placa}"
            } else {
                val marker = mMap.addMarker(
                    MarkerOptions()
                        .position(location)
                        .title("Conductor: ${clientLocation.nombreConductor}")
                        .snippet("Placa: ${clientLocation.placa}")
                        .icon(markerIcon)
                )
                marker?.tag = clientId // Aquí establecemos el clientId en el tag del marcador
                clientMarkers[clientId] = marker!!
            }
        }
    }



    private fun removeClientMarker(clientId: String) {
        clientMarkers.remove(clientId)?.remove()
    }

    class CustomInfoWindowAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {

        override fun getInfoWindow(marker: Marker): View? {
            return null
        }

        override fun getInfoContents(marker: Marker): View {
            val view = LayoutInflater.from(context).inflate(R.layout.custom_info_window, null)

            val titleTextView = view.findViewById<TextView>(R.id.titleTextView)
            val snippetTextView = view.findViewById<TextView>(R.id.snippetTextView)

            titleTextView.text = marker.title
            snippetTextView.text = marker.snippet

            // Aquí configuramos el clic en el Info Window
            view.setOnClickListener {
                val intent = Intent(context, NewActivity::class.java).apply {
                    putExtra("markerTitle", marker.title)
                    putExtra("markerSnippet", marker.snippet)
                }
                context.startActivity(intent)
            }

            return view
        }
    }

    private fun getMarkerIcon(state: String, width: Int, height: Int): BitmapDescriptor {
        val drawableId = when (state.toLowerCase()) {
            "verde" -> R.drawable.marker_verde
            "naranja" -> R.drawable.marker_naranja
            "rojo" -> R.drawable.marker_rojo
            else -> R.drawable.marker_verde
        }

        // Cargar la imagen como Bitmap
        val bitmap = BitmapFactory.decodeResource(resources, drawableId)

        // Redimensionar el Bitmap al tamaño deseado
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, width, height, false)

        // Convertir el Bitmap redimensionado en BitmapDescriptor y retornarlo
        return BitmapDescriptorFactory.fromBitmap(resizedBitmap)
    }

    override fun onPause() {
        super.onPause()
        // Marcar al policía como inactivo en la base de datos solo si ya está activo
        if (::policeLocationReference.isInitialized) {
            policeLocationReference.child("estado").setValue("Inactivo")
        }
    }

    override fun onStop() {
        super.onStop()
        // Marcar al policía como inactivo en la base de datos solo si ya está activo
        if (::policeLocationReference.isInitialized) {
            policeLocationReference.child("estado").setValue("Inactivo")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Marcar al policía como inactivo en la base de datos solo si ya está activo
        if (::policeLocationReference.isInitialized) {
            policeLocationReference.child("estado").setValue("Inactivo")
            Log.d("MainActivity", "onDestroy llamado, estableciendo estado de policía a Inactivo")
        }
    }
}
package com.example

import android.Manifest
import android.content.Context
import android.util.Log
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCoral
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.RadarSlate700
import com.example.ui.theme.RadarSlate800
import com.example.ui.theme.RadarSlate900
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// --- ENUMS & DATA MODELS ---

enum class Screen {
    MENU,
    CREATE_ROOM,
    JOIN_ROOM,
    ACTIVE_ROOM
}

enum class Role {
    MONITOR,
    PARTICIPANT
}

data class ParticipantState(
    val nickname: String,
    val distanceInMeters: Float,
    val isPresent: Boolean = false,
    val isSimulated: Boolean = false,
    val angle: Float = (0..359).random().toFloat(),
    val lastUpdated: Long = System.currentTimeMillis()
)

// --- SHARED IN-MEMORY SESSION ---
// Enables seamless cross-role testing on a single device/emulator
object RoomSession {
    var roomCode by mutableStateOf("")
    var centerLatitude by mutableDoubleStateOf(40.416775) // Default center (Madrid)
    var centerLongitude by mutableDoubleStateOf(-3.703790)
    var monitorName by mutableStateOf("Monitor")
    
    // Map of active nicknames -> participant state
    val participants = mutableStateMapOf<String, ParticipantState>()
    var isCallActive by mutableStateOf(false)
    var callTimestamp by mutableLongStateOf(0L)

    fun reset() {
        roomCode = ""
        participants.clear()
        isCallActive = false
        callTimestamp = 0L
    }
}

class MainActivity : ComponentActivity() {
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    // Real GPS Position for distance calculations
    private val currentLatitude = mutableDoubleStateOf(40.416775)
    private val currentLongitude = mutableDoubleStateOf(-3.703790)
    private val isGpsAvailable = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                var permissionGranted by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                // GPS location listener setup
                LaunchedEffect(permissionGranted) {
                    if (permissionGranted) {
                        try {
                            isGpsAvailable.value = true
                            locationListener = object : LocationListener {
                                override fun onLocationChanged(location: Location) {
                                    currentLatitude.doubleValue = location.latitude
                                    currentLongitude.doubleValue = location.longitude
                                }
                                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                                override fun onProviderEnabled(provider: String) {}
                                override fun onProviderDisabled(provider: String) {}
                            }
                            
                            // Query initial last known location
                            val lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                            lastKnown?.let {
                                currentLatitude.doubleValue = it.latitude
                                currentLongitude.doubleValue = it.longitude
                            }

                            locationManager?.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                1000L,
                                1f,
                                locationListener!!
                            )
                        } catch (e: SecurityException) {
                            isGpsAvailable.value = false
                        }
                    }
                }

                // Request permissions launcher
                val requestPermissionsLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    permissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (permissionGranted) {
                        Toast.makeText(context, "Permisos GPS concedidos 📡", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "GPS inactivo. Usando modo simulador de rango.", Toast.LENGTH_LONG).show()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RadarAppContent(
                        currentLat = currentLatitude.doubleValue,
                        currentLon = currentLongitude.doubleValue,
                        gpsActive = isGpsAvailable.value,
                        requestGpsPermission = {
                            requestPermissionsLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationListener?.let {
            locationManager?.removeUpdates(it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarAppContent(
    currentLat: Double,
    currentLon: Double,
    gpsActive: Boolean,
    requestGpsPermission: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screenState by remember { mutableStateOf(Screen.MENU) }
    var userRole by remember { mutableStateOf(Role.MONITOR) }
    var nicknameInput by remember { mutableStateOf("") }
    var roomCodeInput by remember { mutableStateOf("") }

    // Client-side simulated distance slider (for testing without walking around!)
    var simulatedDistance by remember { mutableFloatStateOf(6f) }
    var useSimulatedLocation by remember { mutableStateOf(!gpsActive) }

    // Trigger local beep and vibration alerts when Call/Llamada is activated
    LaunchedEffect(RoomSession.isCallActive) {
        if (RoomSession.isCallActive) {
            // Check if user is a participant and outside the 10m range
            val isParticipant = userRole == Role.PARTICIPANT
            val myDistance = if (useSimulatedLocation) simulatedDistance else {
                calculateDistance(currentLat, currentLon, RoomSession.centerLatitude, RoomSession.centerLongitude)
            }
            val isOutside = myDistance > 10f

            if (isParticipant && isOutside) {
                // Play notification alert sound
                try {
                    val toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
                } catch (e: Exception) {
                    // Fallback
                }

                // Vibrate the device
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let { v ->
                    if (v.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val pattern = longArrayOf(0, 300, 100, 300, 100, 300)
                            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
                        } else {
                            @Suppress("DEPRECATION")
                            v.vibrate(500)
                        }
                    }
                }
            }
        }
    }

    // Monitor: Keep listening to local session changes
    LaunchedEffect(screenState) {
        if (screenState == Screen.MENU) {
            nicknameInput = ""
            roomCodeInput = ""
        }
    }

    // Automatically check GPS state changes
    LaunchedEffect(gpsActive) {
        useSimulatedLocation = !gpsActive
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar Icon",
                            tint = NeonEmerald,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Presente",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    if (screenState != Screen.MENU) {
                        IconButton(onClick = {
                            if (screenState == Screen.ACTIVE_ROOM) {
                                if (userRole == Role.PARTICIPANT && FirestoreManager.isAvailable && RoomSession.roomCode.isNotEmpty()) {
                                    FirestoreManager.removeParticipant(RoomSession.roomCode, nicknameInput)
                                }
                                // Reset session when leaving room
                                RoomSession.reset()
                            }
                            screenState = Screen.MENU
                        }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    if (screenState == Screen.ACTIVE_ROOM) {
                        // Quick role toggle for easy multi-role testing in emulator
                        TextButton(onClick = {
                            userRole = if (userRole == Role.MONITOR) Role.PARTICIPANT else Role.MONITOR
                            Toast.makeText(context, "Vista de ${if (userRole == Role.MONITOR) "Monitor" else "Participante"}", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(
                                imageVector = if (userRole == Role.MONITOR) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                contentDescription = "Role",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (userRole == Role.MONITOR) "MONITOR" else "PARTIC.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeonCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = RadarSlate900
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(RadarSlate900)
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            when (screenState) {
                Screen.MENU -> {
                    MenuScreen(
                        onCreateClick = {
                            userRole = Role.MONITOR
                            screenState = Screen.CREATE_ROOM
                        },
                        onJoinClick = {
                            userRole = Role.PARTICIPANT
                            screenState = Screen.JOIN_ROOM
                        },
                        requestGpsPermission = requestGpsPermission,
                        gpsActive = gpsActive
                    )
                }

                Screen.CREATE_ROOM -> {
                    CreateRoomScreen(
                        nicknameInput = nicknameInput,
                        onNicknameChange = { nicknameInput = it },
                        onCreateRoom = { nick ->
                            val generatedCode = (1000..9999).random().toString()
                            RoomSession.roomCode = generatedCode
                            RoomSession.centerLatitude = currentLat
                            RoomSession.centerLongitude = currentLon
                            RoomSession.monitorName = nick.ifBlank { "Monitor" }
                            
                            userRole = Role.MONITOR
                            screenState = Screen.ACTIVE_ROOM
                            
                            // Synchronize room creation with Firestore
                            if (FirestoreManager.isAvailable) {
                                FirestoreManager.createRoom(
                                    roomCode = generatedCode,
                                    monitorName = RoomSession.monitorName,
                                    latitude = RoomSession.centerLatitude,
                                    longitude = RoomSession.centerLongitude,
                                    onSuccess = {
                                        Toast.makeText(context, "Sala $generatedCode sincronizada en Firestore 🌐", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = {
                                        Toast.makeText(context, "Usando modo local (Firestore no disponible)", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            } else {
                                Toast.makeText(context, "Sala creada: $generatedCode 👑", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }

                Screen.JOIN_ROOM -> {
                    JoinRoomScreen(
                        nicknameInput = nicknameInput,
                        onNicknameChange = { nicknameInput = it },
                        roomCodeInput = roomCodeInput,
                        onRoomCodeChange = { roomCodeInput = it },
                        onJoinRoom = { nick, code ->
                            val isFirestoreRoom = FirestoreManager.isAvailable
                            
                            // Validate code matching local session if Firestore is unconfigured
                            if (!isFirestoreRoom && RoomSession.roomCode.isNotEmpty() && code != RoomSession.roomCode) {
                                Toast.makeText(context, "¡Código incorrecto o sala inactiva!", Toast.LENGTH_SHORT).show()
                            } else {
                                // Set up or join active session
                                if (RoomSession.roomCode.isEmpty()) {
                                    RoomSession.roomCode = code
                                    RoomSession.centerLatitude = currentLat
                                    RoomSession.centerLongitude = currentLon
                                }
                                val finalNick = nick.ifBlank { "Jugador_${(10..99).random()}" }
                                nicknameInput = finalNick
                                
                                val distance = if (useSimulatedLocation) simulatedDistance else {
                                    calculateDistance(currentLat, currentLon, RoomSession.centerLatitude, RoomSession.centerLongitude)
                                }
                                
                                val newParticipant = ParticipantState(
                                    nickname = finalNick,
                                    distanceInMeters = distance,
                                    isPresent = false,
                                    isSimulated = useSimulatedLocation
                                )
                                RoomSession.participants[finalNick] = newParticipant
                                userRole = Role.PARTICIPANT
                                screenState = Screen.ACTIVE_ROOM
                                
                                // Push initial participant details to Firestore
                                if (FirestoreManager.isAvailable) {
                                    FirestoreManager.updateParticipantState(
                                        roomCode = code,
                                        nickname = finalNick,
                                        distanceInMeters = distance,
                                        isPresent = false,
                                        isSimulated = useSimulatedLocation,
                                        angle = newParticipant.angle
                                    )
                                    Toast.makeText(context, "Unido a la sala #$code (Sincronizado) 🌐", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Te has unido a la sala #$code (Local)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }

                Screen.ACTIVE_ROOM -> {
                    ActiveRoomScreen(
                        role = userRole,
                        nickname = nicknameInput.ifBlank { "Anónimo" },
                        roomCode = RoomSession.roomCode,
                        currentLat = currentLat,
                        currentLon = currentLon,
                        simulatedDistance = simulatedDistance,
                        onSimDistanceChange = { simulatedDistance = it },
                        useSimulatedLocation = useSimulatedLocation,
                        onUseSimLocationChange = { useSimulatedLocation = it },
                        gpsActive = gpsActive
                    )
                }
            }
        }
    }
}

// --- SCREEN COMPOSABLES ---

@Composable
fun MenuScreen(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    requestGpsPermission: () -> Unit,
    gpsActive: Boolean
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Futuristic Banner Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_presente_banner),
                        contentDescription = "Presente Header Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Gradient overlay to blend bottom
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        RadarSlate900.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    // Overlay logo badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                            .background(RadarSlate900.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                            .border(1.dp, NeonEmerald.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = "Active Radar",
                                tint = NeonEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SATELLITE SYNC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonEmerald,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Welcome Text & Mission Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RadarSlate800.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, RadarSlate700)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PRESENTE",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "Control de Asistencia Radar",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = NeonCyan,
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Administra o asiste de forma segura. La geolocalización de precisión sincroniza de manera automática tu estado de presencia a menos de 10 metros en tiempo real.",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Action Buttons Row or Column
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Create Room Button (Monitor)
                Button(
                    onClick = onCreateClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Crear",
                        tint = RadarSlate900,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "CREAR NUEVA SALA (MONITOR)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = RadarSlate900,
                        letterSpacing = 0.5.sp
                    )
                }

                // Join Room Button (Participant)
                Button(
                    onClick = onJoinClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RadarSlate800),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.5.dp, NeonCyan),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GroupAdd,
                        contentDescription = "Unirse",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "UNIRSE A SALA (PARTICIPANTE)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = NeonCyan,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // GPS Permission Checklist Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = RadarSlate800.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (gpsActive) NeonEmerald.copy(alpha = 0.25f) else RadarSlate700)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (gpsActive) NeonEmerald.copy(alpha = 0.15f) else NeonCoral.copy(alpha = 0.15f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (gpsActive) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                            contentDescription = "GPS Status",
                            tint = if (gpsActive) NeonEmerald else NeonCoral,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (gpsActive) "GPS de alta precisión activo" else "GPS no autorizado o inactivo",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (gpsActive) "Coordenadas reales del satélite activadas." else "Se usará el simulador interactivo para pruebas.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp
                        )
                    }

                    if (!gpsActive) {
                        Button(
                            onClick = requestGpsPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = RadarSlate700),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Activar", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CreateRoomScreen(
    nicknameInput: String,
    onNicknameChange: (String) -> Unit,
    onCreateRoom: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = RadarSlate800.copy(alpha = 0.75f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.25f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glow badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(NeonEmerald.copy(alpha = 0.1f), CircleShape)
                        .border(1.5.dp, NeonEmerald.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Configuración de Monitor",
                        tint = NeonEmerald,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "ROL: MONITOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonEmerald,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Configurar Nueva Sala",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Escribe tu nickname de monitor. El sistema creará una sala segura con un código de radar único.",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Monitor Nickname Input
                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = onNicknameChange,
                    label = { Text("Tu Identificador", color = Color.LightGray) },
                    placeholder = { Text("Ej. Supervisor Juan", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonEmerald,
                        unfocusedBorderColor = RadarSlate700,
                        focusedLabelColor = NeonEmerald,
                        unfocusedLabelColor = Color.LightGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonEmerald,
                        focusedContainerColor = RadarSlate900.copy(alpha = 0.4f),
                        unfocusedContainerColor = RadarSlate900.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = "Nick",
                            tint = NeonEmerald
                        )
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { onCreateRoom(nicknameInput) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MeetingRoom,
                        contentDescription = "Iniciar",
                        tint = RadarSlate900,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "INICIAR SALA DE CONTROL 👑",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = RadarSlate900,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun JoinRoomScreen(
    nicknameInput: String,
    onNicknameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    onJoinRoom: (String, String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = RadarSlate800.copy(alpha = 0.75f)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.25f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Glow badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(NeonCyan.copy(alpha = 0.1f), CircleShape)
                        .border(1.5.dp, NeonCyan.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Participante",
                        tint = NeonCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "ROL: PARTICIPANTE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Unirse a una Sala",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Escribe tu nombre de participante y el código de 4 dígitos proporcionado por tu monitor.",
                    fontSize = 13.sp,
                    color = Color.LightGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Nickname Input
                OutlinedTextField(
                    value = nicknameInput,
                    onValueChange = onNicknameChange,
                    label = { Text("Tu Nombre Completo", color = Color.LightGray) },
                    placeholder = { Text("Ej. Carlos Ramos", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = RadarSlate700,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = Color.LightGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonCyan,
                        focusedContainerColor = RadarSlate900.copy(alpha = 0.4f),
                        unfocusedContainerColor = RadarSlate900.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = "Nick Icon",
                            tint = NeonCyan
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Room Code Input
                OutlinedTextField(
                    value = roomCodeInput,
                    onValueChange = onRoomCodeChange,
                    label = { Text("Código de Sala (4 dígitos)", color = Color.LightGray) },
                    placeholder = { Text("Ej. 4279", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = RadarSlate700,
                        focusedLabelColor = NeonCyan,
                        unfocusedLabelColor = Color.LightGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonCyan,
                        focusedContainerColor = RadarSlate900.copy(alpha = 0.4f),
                        unfocusedContainerColor = RadarSlate900.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = "Code Icon",
                            tint = NeonCyan
                        )
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = {
                        if (roomCodeInput.isNotBlank()) {
                            onJoinRoom(nicknameInput, roomCodeInput)
                        } else {
                            onJoinRoom(nicknameInput, "1234") // Fallback test room code
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Map,
                        contentDescription = "Ubicación",
                        tint = RadarSlate900,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "CONECTAR AL RADAR 🗺️",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        color = RadarSlate900,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveRoomScreen(
    role: Role,
    nickname: String,
    roomCode: String,
    currentLat: Double,
    currentLon: Double,
    simulatedDistance: Float,
    onSimDistanceChange: (Float) -> Unit,
    useSimulatedLocation: Boolean,
    onUseSimLocationChange: (Boolean) -> Unit,
    gpsActive: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Retrieve live distance based on selection (real GPS vs Simulated)
    val computedDistance = if (useSimulatedLocation) {
        simulatedDistance
    } else {
        calculateDistance(currentLat, currentLon, RoomSession.centerLatitude, RoomSession.centerLongitude)
    }

    // Attach real-time Firestore listeners
    DisposableEffect(roomCode) {
        if (FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
            FirestoreManager.startListeningToRoom(
                roomCode = roomCode,
                onRoomUpdate = { monitor, centerLat, centerLon, isCall, callTime ->
                    RoomSession.monitorName = monitor
                    RoomSession.centerLatitude = centerLat
                    RoomSession.centerLongitude = centerLon
                    RoomSession.isCallActive = isCall
                    RoomSession.callTimestamp = callTime
                },
                onParticipantsUpdate = { participantsList ->
                    participantsList.forEach { p ->
                        if (role == Role.PARTICIPANT && p.nickname == nickname) {
                            val local = RoomSession.participants[nickname]
                            if (local != null) {
                                // Only update presence if it has been toggled by the monitor
                                if (local.isPresent != p.isPresent) {
                                    RoomSession.participants[nickname] = local.copy(isPresent = p.isPresent)
                                }
                            } else {
                                RoomSession.participants[p.nickname] = p
                            }
                        } else {
                            RoomSession.participants[p.nickname] = p
                        }
                    }
                    // Remove local cache items if they are no longer in Firestore
                    val currentKeys = RoomSession.participants.keys.toList()
                    val remoteNames = participantsList.map { it.nickname }.toSet()
                    currentKeys.forEach { key ->
                        if (key != nickname && !remoteNames.contains(key)) {
                            RoomSession.participants.remove(key)
                        }
                    }
                },
                onError = { e ->
                    Log.e("ActiveRoomScreen", "Firestore listen error: ${e.message}")
                }
            )
        }
        onDispose {
            FirestoreManager.stopListening()
        }
    }

    // Automatically synchronize the current real/simulated participant coordinates in the session and Firestore
    LaunchedEffect(computedDistance, nickname, role, useSimulatedLocation) {
        if (role == Role.PARTICIPANT) {
            val currentPartState = RoomSession.participants[nickname]
            val currentAngle = currentPartState?.angle ?: (0..359).random().toFloat()
            val currentPresent = currentPartState?.isPresent ?: false
            
            RoomSession.participants[nickname] = ParticipantState(
                nickname = nickname,
                distanceInMeters = computedDistance,
                isPresent = currentPresent,
                angle = currentAngle,
                isSimulated = useSimulatedLocation
            )

            if (FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
                FirestoreManager.updateParticipantState(
                    roomCode = roomCode,
                    nickname = nickname,
                    distanceInMeters = computedDistance,
                    isPresent = currentPresent,
                    isSimulated = useSimulatedLocation,
                    angle = currentAngle
                )
            }
        }
    }

    // Periodic Presence Heartbeat for active participants
    LaunchedEffect(role, roomCode, nickname) {
        if (role == Role.PARTICIPANT && FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
            while (true) {
                delay(4000)
                val currentPartState = RoomSession.participants[nickname]
                if (currentPartState != null) {
                    FirestoreManager.updateParticipantState(
                        roomCode = roomCode,
                        nickname = nickname,
                        distanceInMeters = currentPartState.distanceInMeters,
                        isPresent = currentPartState.isPresent,
                        isSimulated = currentPartState.isSimulated,
                        angle = currentPartState.angle
                    )
                }
            }
        }
    }

    // Visual warning glow state for alarm calls
    val isAlertTriggered = RoomSession.isCallActive && (role == Role.PARTICIPANT) && (computedDistance > 10f)
    val alertBgColor by animateColorAsState(
        targetValue = if (isAlertTriggered) NeonCoral.copy(alpha = 0.25f) else RadarSlate900,
        animationSpec = repeatOf(isAlertTriggered, 6),
        label = "alertBg"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(alertBgColor)
    ) {
        // --- Header Info Panel ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RadarSlate800),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, RadarSlate700)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (role == Role.MONITOR) NeonEmerald else NeonCyan, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sala #${roomCode.ifBlank { "1234" }}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Rol: ${if (role == Role.MONITOR) "Monitor (Creador)" else "Participante"}",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (role == Role.MONITOR) NeonEmerald.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.15f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = nickname,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (role == Role.MONITOR) NeonEmerald else NeonCyan
                    )
                }
            }
        }

        // --- Firestore Cloud Sync Status Banner ---
        val firestoreAvailable = FirestoreManager.isAvailable
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (firestoreAvailable) NeonEmerald.copy(alpha = 0.1f) else RadarSlate800.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (firestoreAvailable) NeonEmerald.copy(alpha = 0.25f) else RadarSlate700)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (firestoreAvailable) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                    contentDescription = "Sync Status Icon",
                    tint = if (firestoreAvailable) NeonEmerald else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = if (firestoreAvailable) "Sincronización en la Nube Activa (Firestore)" else "Modo de Sincronización Local (Offline)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (firestoreAvailable) NeonEmerald else Color.LightGray
                    )
                    Text(
                        text = if (firestoreAvailable) "Las salas, coordenadas y asistencia se sincronizan en la nube en tiempo real." else "Sube tu google-services.json en AI Studio para conectar Firestore. El simulador funciona localmente.",
                        fontSize = 9.sp,
                        color = Color.Gray,
                        lineHeight = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Custom Radar Canvas Map ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, RadarSlate700, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Draw real radar sweep & interactive dots
            RadarCanvas(
                centerLabel = RoomSession.monitorName,
                participants = RoomSession.participants.values.toList(),
                isCallActive = RoomSession.isCallActive
            )

            // Overlap visual status on top-right of the radar
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                val totalParts = RoomSession.participants.size
                val insideCount = RoomSession.participants.values.count { it.distanceInMeters <= 10f }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = RadarSlate900.copy(alpha = 0.85f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Dentro: $insideCount / $totalParts",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonEmerald
                        )
                        Text(
                            text = "Fuera: ${totalParts - insideCount}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonCoral
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Active Warning Siren Banner ---
        if (isAlertTriggered) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = NeonCoral),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alarma",
                        tint = RadarSlate900,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "¡ALERTA DE LLAMADA RECIBIDA!",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = RadarSlate900
                        )
                        Text(
                            text = "El monitor te llama. Estás fuera del rango de 10m.",
                            fontSize = 12.sp,
                            color = RadarSlate900
                        )
                    }
                }
            }
        }

        // --- Role Specific Panels ---
        if (role == Role.MONITOR) {
            MonitorPanel(
                roomCode = roomCode,
                onCallAction = {
                    scope.launch {
                        RoomSession.isCallActive = true
                        RoomSession.callTimestamp = System.currentTimeMillis()
                        Toast.makeText(context, "📢 ¡Llamada de alerta enviada!", Toast.LENGTH_SHORT).show()
                        
                        // Sync call activation to Firestore
                        if (FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
                            FirestoreManager.triggerCall(roomCode, true)
                        }

                        // Keeps call active for 4 seconds
                        delay(4000)
                        RoomSession.isCallActive = false
                        
                        // Sync call deactivation to Firestore
                        if (FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
                            FirestoreManager.triggerCall(roomCode, false)
                        }
                    }
                }
            )
        } else {
            ParticipantPanel(
                nickname = nickname,
                distance = computedDistance,
                isPresent = RoomSession.participants[nickname]?.isPresent ?: false,
                onPresentClick = {
                    val current = RoomSession.participants[nickname]
                    if (current != null) {
                        RoomSession.participants[nickname] = current.copy(isPresent = true)
                        Toast.makeText(context, "✅ ¡Marcado como PRESENTADO!", Toast.LENGTH_SHORT).show()
                        
                        // Sync presence status to Firestore
                        if (FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
                            FirestoreManager.updateAttendance(roomCode, nickname, true)
                        }
                    }
                },
                simulatedDistance = simulatedDistance,
                onSimDistanceChange = onSimDistanceChange,
                useSimulatedLocation = useSimulatedLocation,
                onUseSimLocationChange = onUseSimLocationChange,
                gpsActive = gpsActive
            )
        }
    }
}

// --- SUB-COMPOSABLES & PANELS ---

@Composable
fun MonitorPanel(
    roomCode: String,
    onCallAction: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Dialog input state for simulated participants
    var showAddMockDialog by remember { mutableStateOf(false) }
    var mockNickname by remember { mutableStateOf("") }
    var mockDistance by remember { mutableFloatStateOf(8f) }

    // Real-time time tracker for presence/disconnect evaluations
    var systemTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            systemTime = System.currentTimeMillis()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Participantes y Asistencia",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Button to quickly add mock participants for testing
            Button(
                onClick = { showAddMockDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = RadarSlate700),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddCircleOutline,
                    contentDescription = "Sim",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Añadir Prueba", fontSize = 12.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Monitor Alarm Buzzer Button
        Button(
            onClick = onCallAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (RoomSession.isCallActive) NeonCoral else NeonCoral.copy(alpha = 0.85f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Llamar",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (RoomSession.isCallActive) "¡ENVIANDO ALERTA SONORA! 🔊" else "Llamar a los de fuera 📢",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- Attendance Dashboard Filters ---
        val totalCount = RoomSession.participants.size
        val presentCount = RoomSession.participants.values.count { it.isPresent }
        val missingCount = totalCount - presentCount
        var selectedFilter by remember { mutableStateOf("TODOS") } // "TODOS", "FALTAN", "PRESENTES"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Chip TODOS
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedFilter == "TODOS") NeonCyan.copy(alpha = 0.15f) else RadarSlate800)
                    .border(
                        1.dp,
                        if (selectedFilter == "TODOS") NeonCyan else RadarSlate700,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { selectedFilter = "TODOS" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Todos", fontSize = 11.sp, color = Color.LightGray)
                    Text("$totalCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selectedFilter == "TODOS") NeonCyan else Color.White)
                }
            }

            // Chip FALTAN (Pendientes)
            Box(
                modifier = Modifier
                    .weight(1.5f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedFilter == "FALTAN") NeonCoral.copy(alpha = 0.15f) else RadarSlate800)
                    .border(
                        1.dp,
                        if (selectedFilter == "FALTAN") NeonCoral else RadarSlate700,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { selectedFilter = "FALTAN" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Faltan por Llegar", fontSize = 11.sp, color = Color.LightGray)
                    Text("$missingCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selectedFilter == "FALTAN") NeonCoral else Color.White)
                }
            }

            // Chip PRESENTES
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selectedFilter == "PRESENTES") NeonEmerald.copy(alpha = 0.15f) else RadarSlate800)
                    .border(
                        1.dp,
                        if (selectedFilter == "PRESENTES") NeonEmerald else RadarSlate700,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { selectedFilter = "PRESENTES" }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Presentes", fontSize = 11.sp, color = Color.LightGray)
                    Text("$presentCount", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selectedFilter == "PRESENTES") NeonEmerald else Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Checklist of participants
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(min = 150.dp, max = 260.dp),
            colors = CardDefaults.cardColors(containerColor = RadarSlate800),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, RadarSlate700)
        ) {
            val fullList = RoomSession.participants.values.toList()
            val filteredList = fullList.filter { p ->
                when (selectedFilter) {
                    "PRESENTES" -> p.isPresent
                    "FALTAN" -> !p.isPresent
                    else -> true
                }
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = when (selectedFilter) {
                                "PRESENTES" -> Icons.Default.CheckCircleOutline
                                "FALTAN" -> Icons.Default.SentimentSatisfiedAlt
                                else -> Icons.Default.Group
                            },
                            contentDescription = "Empty Status",
                            tint = Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when (selectedFilter) {
                                "PRESENTES" -> "Nadie ha marcado presente todavía."
                                "FALTAN" -> "¡Todos los participantes han llegado! 🎉"
                                else -> "Esperando participantes..."
                            },
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        if (fullList.isEmpty()) {
                            Text(
                                text = "Código de acceso: $roomCode",
                                color = NeonCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList) { p ->
                        val isOffline = FirestoreManager.isAvailable && !p.isSimulated && (systemTime - p.lastUpdated > 12000)
                        val isNear = p.distanceInMeters <= 10f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RadarSlate900.copy(alpha = if (isOffline) 0.35f else 0.6f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Green/Red Indicator depending on distance (or Gray if offline)
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            if (isOffline) Color.Gray 
                                            else if (isNear) NeonEmerald 
                                            else NeonCoral, 
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = p.nickname,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isOffline) Color.Gray else Color.White
                                        )
                                        if (isOffline) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Desconectado",
                                                fontSize = 9.sp,
                                                color = Color.LightGray,
                                                modifier = Modifier
                                                    .background(Color.Gray.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        } else if (p.isSimulated) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Sim",
                                                fontSize = 9.sp,
                                                color = NeonCyan,
                                                modifier = Modifier
                                                    .background(NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isOffline) "Desconectado" else if (isNear) "Dentro" else "Fuera",
                                            fontSize = 11.sp,
                                            color = if (isOffline) Color.Gray else if (isNear) NeonEmerald else NeonCoral,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = " • ${"%.1f".format(p.distanceInMeters)}m",
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (p.isSimulated) {
                                    // Control slider so Monitor can pull mock participants in and out of the range
                                    Slider(
                                        value = p.distanceInMeters,
                                        onValueChange = { newVal ->
                                            val updated = p.copy(distanceInMeters = newVal)
                                            RoomSession.participants[p.nickname] = updated
                                            if (FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
                                                FirestoreManager.updateParticipantState(
                                                    roomCode = roomCode,
                                                    nickname = p.nickname,
                                                    distanceInMeters = newVal,
                                                    isPresent = p.isPresent,
                                                    isSimulated = p.isSimulated,
                                                    angle = p.angle
                                                )
                                            }
                                        },
                                        valueRange = 1f..25f,
                                        modifier = Modifier.width(80.dp),
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = NeonCyan,
                                            thumbColor = NeonCyan
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                // Interactive Present / Missing toggle button
                                Button(
                                    onClick = {
                                        val updated = p.copy(isPresent = !p.isPresent)
                                        RoomSession.participants[p.nickname] = updated
                                        if (FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
                                            FirestoreManager.updateAttendance(roomCode, p.nickname, updated.isPresent)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isOffline) Color.Gray.copy(alpha = 0.1f) else if (p.isPresent) NeonEmerald.copy(alpha = 0.2f) else RadarSlate700
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOffline) Icons.Default.WifiOff else if (p.isPresent) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                        contentDescription = "Present Status Indicator",
                                        tint = if (isOffline) Color.Gray else if (p.isPresent) NeonEmerald else NeonCoral,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isOffline) "DESCONECTADO" else if (p.isPresent) "PRESENTE" else "FALTA",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isOffline) Color.Gray else if (p.isPresent) NeonEmerald else NeonCoral
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal popup dialog to add mock participants
    if (showAddMockDialog) {
        AlertDialog(
            onDismissRequest = { showAddMockDialog = false },
            title = { Text("Añadir Participante de Prueba", color = Color.White) },
            containerColor = RadarSlate800,
            text = {
                Column {
                    Text(
                        "Crea un participante simulado para verificar de inmediato el radar de 10m y los cambios de verde/rojo.",
                        fontSize = 13.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = mockNickname,
                        onValueChange = { mockNickname = it },
                        label = { Text("Nombre del participante", color = Color.LightGray) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = NeonCyan
                        ),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Distancia inicial: ${"%.1f".format(mockDistance)} metros", color = Color.White)
                    Slider(
                        value = mockDistance,
                        onValueChange = { mockDistance = it },
                        valueRange = 1f..25f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = NeonCyan,
                            thumbColor = NeonCyan
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = mockNickname.ifBlank { "Prueba_${(10..99).random()}" }
                        val newParticipant = ParticipantState(
                            nickname = name,
                            distanceInMeters = mockDistance,
                            isPresent = false,
                            isSimulated = true
                        )
                        RoomSession.participants[name] = newParticipant
                        
                        if (FirestoreManager.isAvailable && roomCode.isNotEmpty()) {
                            FirestoreManager.updateParticipantState(
                                roomCode = roomCode,
                                nickname = name,
                                distanceInMeters = mockDistance,
                                isPresent = false,
                                isSimulated = true,
                                angle = newParticipant.angle
                            )
                        }
                        
                        showAddMockDialog = false
                        mockNickname = ""
                        mockDistance = 8f
                        Toast.makeText(context, "$name añadido de prueba", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Text("Añadir", color = RadarSlate900, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMockDialog = false }) {
                    Text("Cancelar", color = Color.LightGray)
                }
            }
        )
    }
}

@Composable
fun ParticipantPanel(
    nickname: String,
    distance: Float,
    isPresent: Boolean,
    onPresentClick: () -> Unit,
    simulatedDistance: Float,
    onSimDistanceChange: (Float) -> Unit,
    useSimulatedLocation: Boolean,
    onUseSimLocationChange: (Boolean) -> Unit,
    gpsActive: Boolean
) {
    val context = LocalContext.current
    val isInside = distance <= 10f

    Column(modifier = Modifier.fillMaxWidth()) {
        // --- Status Banner ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isInside) NeonEmerald.copy(alpha = 0.15f) else NeonCoral.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.dp, if (isInside) NeonEmerald else NeonCoral),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (isInside) NeonEmerald else NeonCoral, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isInside) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = "Status",
                        tint = RadarSlate900,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (isInside) "DENTRO DEL RANGO (VERDE)" else "FUERA DEL RANGO (ROJO)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isInside) NeonEmerald else NeonCoral
                    )
                    Text(
                        text = "Estás a ${"%.1f".format(distance)} metros de distancia del monitor.",
                        fontSize = 12.sp,
                        color = Color.LightGray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Present Notification Button ---
        Button(
            onClick = onPresentClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isPresent) RadarSlate700 else NeonEmerald
            ),
            shape = RoundedCornerShape(14.dp),
            enabled = !isPresent
        ) {
            Icon(
                imageVector = if (isPresent) Icons.Default.CheckCircleOutline else Icons.Default.Handshake,
                contentDescription = "Presente",
                tint = if (isPresent) Color.LightGray else RadarSlate900
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPresent) "¡YA REGISTRADO PRESENTE! ✅" else "Dar al Botón Presente 🙋‍♂️",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = if (isPresent) Color.LightGray else RadarSlate900
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Simulated Range Controller ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = RadarSlate800),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, RadarSlate700)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Simulador de Distancia (Pruebas)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    
                    // Switch to toggle real GPS vs simulation
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Simular",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = useSimulatedLocation,
                            onCheckedChange = {
                                if (it || gpsActive) {
                                    onUseSimLocationChange(it)
                                } else {
                                    Toast.makeText(context, "GPS no activo. Activando simulación.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonCyan,
                                checkedTrackColor = NeonCyan.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }

                Text(
                    text = if (useSimulatedLocation) {
                        "Arrastra el control para simular acercarte o alejarte del monitor:"
                    } else {
                        "Utilizando ubicación real. Cambia a modo Simular para probar el radar en el ordenador."
                    },
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                if (useSimulatedLocation) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "1m",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                        
                        Slider(
                            value = simulatedDistance,
                            onValueChange = onSimDistanceChange,
                            valueRange = 1f..25f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                activeTrackColor = NeonCyan,
                                thumbColor = NeonCyan
                            )
                        )

                        Text(
                            "25m",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }

                    Text(
                        text = "Ubicación Simulada: ${"%.1f".format(simulatedDistance)} metros",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

// --- CANVAS RADAR MAP PAINT DRAWING ---

@Composable
fun RadarCanvas(
    centerLabel: String,
    participants: List<ParticipantState>,
    isCallActive: Boolean,
    modifier: Modifier = Modifier
) {
    // Pulse and sweep animations
    val infiniteTransition = rememberInfiniteTransition(label = "radarAnimation")
    
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarSweep"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radarPulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(RadarSlate900)
    ) {
        val width = size.width
        val height = size.height
        val center = Offset(width / 2f, height / 2f)
        val maxRadius = minOf(width, height) / 2f * 0.85f

        // Grid scale: let's map 20 meters to maxRadius in pixels
        val scaleMeterToPixel = maxRadius / 20f
        val radius5m = 5f * scaleMeterToPixel
        val radius10m = 10f * scaleMeterToPixel
        val radius15m = 15f * scaleMeterToPixel

        // 1. Concentric circles (Grids)
        // Draw deep radar base background
        drawCircle(
            color = Color(0xFF0B111E),
            radius = maxRadius,
            center = center
        )

        // 5-meter circle
        drawCircle(
            color = RadarSlate700.copy(alpha = 0.3f),
            radius = radius5m,
            center = center,
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f))
        )

        // 15-meter circle
        drawCircle(
            color = RadarSlate700.copy(alpha = 0.3f),
            radius = radius15m,
            center = center,
            style = Stroke(width = 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f))
        )

        // 10-METER BOUNDARY (The magic line!)
        // In-range filled glowing zone
        drawCircle(
            color = NeonEmerald.copy(alpha = 0.04f),
            radius = radius10m,
            center = center
        )
        // Glowing halo pulsing line
        drawCircle(
            color = NeonEmerald.copy(alpha = 0.12f * (2f - pulseScale)),
            radius = radius10m * pulseScale,
            center = center,
            style = Stroke(width = 2.5f)
        )
        // Sharp boundary circle
        drawCircle(
            color = NeonEmerald.copy(alpha = 0.9f),
            radius = radius10m,
            center = center,
            style = Stroke(width = 3.5f)
        )

        // Outer limit circle (20m)
        drawCircle(
            color = RadarSlate700.copy(alpha = 0.5f),
            radius = maxRadius,
            center = center,
            style = Stroke(width = 2f)
        )

        // Crosshairs lines
        drawLine(
            color = RadarSlate700.copy(alpha = 0.4f),
            start = Offset(center.x - maxRadius, center.y),
            end = Offset(center.x + maxRadius, center.y),
            strokeWidth = 1.5f
        )
        drawLine(
            color = RadarSlate700.copy(alpha = 0.4f),
            start = Offset(center.x, center.y - maxRadius),
            end = Offset(center.x, center.y + maxRadius),
            strokeWidth = 1.5f
        )

        // Sweep beam gradient rotation
        val sweepRad = Math.toRadians(sweepAngle.toDouble())
        val sweepEndX = center.x + maxRadius * cos(sweepRad).toFloat()
        val sweepEndY = center.y + maxRadius * sin(sweepRad).toFloat()
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.7f), NeonCyan.copy(alpha = 0.0f)),
                start = center,
                end = Offset(sweepEndX, sweepEndY)
            ),
            start = center,
            end = Offset(sweepEndX, sweepEndY),
            strokeWidth = 4.5f
        )

        // Radial scale distance labels
        drawContext.canvas.nativeCanvas.apply {
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#4B5563")
                textSize = 24f
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            drawText("5m", center.x + radius5m, center.y - 8f, labelPaint)
            drawText("10 RANGO", center.x + radius10m, center.y - 8f, labelPaint)
            drawText("15m", center.x + radius15m, center.y - 8f, labelPaint)
        }

        // 2. Draw active participants
        participants.forEach { p ->
            val angleRad = Math.toRadians(p.angle.toDouble())
            // Cap visual range at 21 meters so dots don't fly outside the grid
            val cappedDistance = minOf(p.distanceInMeters, 20.5f)
            val pX = center.x + (cappedDistance * scaleMeterToPixel) * cos(angleRad).toFloat()
            val pY = center.y + (cappedDistance * scaleMeterToPixel) * sin(angleRad).toFloat()

            val isOffline = FirestoreManager.isAvailable && !p.isSimulated && (System.currentTimeMillis() - p.lastUpdated > 12000)
            val isInside = p.distanceInMeters <= 10f
            val themeColor = if (isOffline) Color.Gray else if (isInside) NeonEmerald else NeonCoral

            // If monitor called and this participant is outside, draw a high-intensity blinking alert halo
            if (RoomSession.isCallActive && !isInside && !isOffline) {
                drawCircle(
                    color = NeonCoral.copy(alpha = 0.45f * pulseScale),
                    radius = 24f * pulseScale,
                    center = Offset(pX, pY)
                )
            }

            // Glow aura
            drawCircle(
                color = themeColor.copy(alpha = if (isOffline) 0.1f else 0.2f),
                radius = 16f,
                center = Offset(pX, pY)
            )

            // Inner core dot
            drawCircle(
                color = themeColor,
                radius = 9f,
                center = Offset(pX, pY)
            )

            // Draw Participant Name and present indicator
            drawContext.canvas.nativeCanvas.apply {
                val nickPaint = android.graphics.Paint().apply {
                    color = if (isOffline) android.graphics.Color.GRAY else android.graphics.Color.WHITE
                    textSize = 28f
                    isAntiAlias = true
                    isFakeBoldText = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    // Drop shadow for crisp readability
                    setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
                }
                
                val baseTag = if (p.isPresent) "🙋 ${p.nickname}" else p.nickname
                val nameTag = if (isOffline) "$baseTag 💤" else baseTag
                drawText(nameTag, pX, pY - 18f, nickPaint)
            }
        }

        // 3. Central Monitor Anchor Point
        // Outer pulsing wave
        drawCircle(
            color = NeonCyan.copy(alpha = 0.15f * (2f - pulseScale)),
            radius = 28f * pulseScale,
            center = center
        )

        // Anchor ring
        drawCircle(
            color = NeonCyan,
            radius = 10f,
            center = center
        )

        // Central visual crown text label
        drawContext.canvas.nativeCanvas.apply {
            val centerPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#06B6D4")
                textSize = 26f
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = android.graphics.Paint.Align.CENTER
                setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
            }
            drawText("👑 $centerLabel", center.x, center.y + 36f, centerPaint)
        }
    }
}

// --- COORDINATES HELPER MATH ---

/**
 * Calculates distance between two latitude/longitude coordinates in meters using the Haversine formula
 */
fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val results = FloatArray(1)
    try {
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
    } catch (e: Exception) {
        // Fallback standard math if Android Location is missing/stubbed in specific CI tests
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return (r * c).toFloat()
    }
}

// Custom scale extension removed in favor of standard androidx.compose.ui.draw.scale

/**
 * Helper to generate simple animation loops
 */
private fun repeatOf(condition: Boolean, times: Int): AnimationSpec<Color> {
    return if (condition) {
        infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    } else {
        snap()
    }
}

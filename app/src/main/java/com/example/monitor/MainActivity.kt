package com.example.monitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import androidx.compose.ui.layout.ContentScale

// --- MANAJEMEN KONEKSI ---
private const val ESP32_BASE_URL = "http://192.168.10.10"

object WifiNetworkManager {
    @Volatile var network: Network? = null
}

fun getHttpClient(): OkHttpClient {
    val baseBuilder = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)

    return try {
        WifiNetworkManager.network?.let { net ->
            baseBuilder.socketFactory(net.socketFactory).build()
        } ?: baseBuilder.build()
    } catch (_: Exception) {
        baseBuilder.build()
    }
}

// --- DATA & VIEWMODEL UNTUK HISTORI (DENGAN STATEFLOW) ---
data class LogHistory(
    val time: String,
    val message: String
)

class HistoryViewModel : ViewModel() {
    private val _logs = MutableStateFlow<List<LogHistory>>(emptyList())
    val logs: StateFlow<List<LogHistory>> = _logs.asStateFlow()

    var lastStatusMessage by mutableStateOf("Menunggu Aktivitas...")
    var lastStatusTime by mutableStateOf("-")
    
    var lastDistance1 by mutableFloatStateOf(0f)
    var lastDistance2 by mutableFloatStateOf(0f)
    var lastLdr by mutableFloatStateOf(0f)

    var isGateOpen by mutableStateOf(false)
        private set
    var isManualMode by mutableStateOf(false)
        private set

    fun addLog(message: String) {
        val currentTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newLog = LogHistory(currentTime, message)
        
        val updatedList = mutableListOf(newLog).apply {
            addAll(_logs.value)
        }

        if (updatedList.size > 50) {
            _logs.value = updatedList.subList(0, 50)
        } else {
            _logs.value = updatedList
        }

        lastStatusMessage = message
        lastStatusTime = currentTime
    }

    fun clearLogs() {
        _logs.value = emptyList()
        lastStatusMessage = "Riwayat Dihapus"
        lastStatusTime = "-"
    }
    
    fun updateDataAndCheckNotification(context: Context, data: GateSensorData) {
        lastDistance1 = data.distance1
        lastDistance2 = data.distance2
        lastLdr = data.ldr
        isManualMode = data.manualMode

        if (data.gateOpen && !isGateOpen) {
            val msg = if (data.manualMode) "Palang Terbuka (Manual)" else "Palang Terbuka (Otomatis)"
            addLog(msg)
            NotificationHelper.showNotification(context, "Sistem Palang Pintu", msg)
        } else if (!data.gateOpen && isGateOpen) {
            val msg = if (data.manualMode) "Palang Tertutup (Manual)" else "Palang Tertutup (Otomatis)"
            addLog(msg)
        }

        isGateOpen = data.gateOpen
    }

    fun logManualCommand(success: Boolean, opening: Boolean) {
        val action = if (opening) "Buka" else "Tutup"
        addLog(if (success) "Perintah $action Manual Terkirim" else "Gagal Kirim Perintah $action Manual")
    }
}

// --- NOTIFICATION HELPER ---
object NotificationHelper {
    const val CHANNEL_ID = "palang_pintu_channel"
    
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Notifikasi Palang Pintu"
            val descriptionText = "Memberi tahu saat palang terbuka"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, title: String, message: String) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) 
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }
}

// --- FUNGSI FETCH DATA ---
// --- FUNGSI FETCH DATA ---
data class GateSensorData(
    val distance1: Float,
    val distance2: Float,
    val ldr: Float,
    val gateOpen: Boolean,
    val manualMode: Boolean
)

suspend fun fetchSensorData(url: String): GateSensorData? {
    return suspendCancellableCoroutine { cont ->
        try {
            val request = Request.Builder().url(url).header("Connection", "close").build()
            val call = getHttpClient().newCall(request)
            cont.invokeOnCancellation { try { call.cancel() } catch (_: Exception) { } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            if (!response.isSuccessful) {
                                if (cont.isActive) cont.resume(null)
                                return
                            }
                            val body = it.body?.string() ?: ""
                            try {
                                val json = JSONObject(body)
                                val data = GateSensorData(
                                    distance1 = json.optDouble("sensor1", 0.0).toFloat(),
                                    distance2 = json.optDouble("sensor2", 0.0).toFloat(),
                                    ldr = json.optDouble("ldr", 0.0).toFloat(),
                                    gateOpen = json.optBoolean("gate", false),
                                    manualMode = json.optBoolean("manualMode", false)
                                )
                                if (cont.isActive) cont.resume(data)
                            } catch (_: Exception) { if (cont.isActive) cont.resume(null) }
                        }
                    } catch (_: Exception) { if (cont.isActive) cont.resume(null) }
                }
            })
        } catch (e: Exception) { if (cont.isActive) cont.resume(null) }
    }
}

suspend fun fetchCameraImage(url: String): ByteArray? {
    return suspendCancellableCoroutine { cont ->
        try {
            val request = Request.Builder()
                .url(url)
                .header("Connection", "close")
                .build()

            val call = getHttpClient().newCall(request)

            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!response.isSuccessful) {
                            if (cont.isActive) cont.resume(null)
                            return
                        }

                        if (cont.isActive) {
                            cont.resume(it.body?.bytes())
                        }
                    }
                }
            })
        } catch (_: Exception) {
            if (cont.isActive) cont.resume(null)
        }
    }
}

suspend fun sendOpenGate(): Boolean = sendGateCommand("/OPEN")

suspend fun sendCloseGate(): Boolean = sendGateCommand("/CLOSE")

private suspend fun sendGateCommand(path: String): Boolean {
    return suspendCancellableCoroutine { cont ->

        try {

            val request = Request.Builder()
                .url("$ESP32_BASE_URL$path")
                .header("Connection", "close")
                .build()

            val call = getHttpClient().newCall(request)

            cont.invokeOnCancellation {
                call.cancel()
            }

            call.enqueue(object : Callback {

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resume(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (cont.isActive) {
                            cont.resume(it.isSuccessful)
                        }
                    }
                }
            })

        } catch (_: Exception) {
            if (cont.isActive) cont.resume(false)
        }
    }
}

suspend fun recognizeText(bitmap: Bitmap): String {

    val recognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )

    val image = InputImage.fromBitmap(bitmap, 0)

    return try {
        recognizer.process(image).await().text
    } catch (_: Exception) {
        ""
    }
}

private val allowedPlates = setOf(
    "A234AZA",
    "B1234ABC",
    "D5678XYZ",
    "H2123YH"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        NotificationHelper.createNotificationChannel(this)

        try {
            setupWifiMonitor()
        } catch (e: Exception) {
            Log.e("SensorData", "Gagal setup Wi-Fi monitor: ${e.message}")
        }
        
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val historyViewModel: HistoryViewModel = viewModel()
                AppNavigation(historyViewModel)
            }
        }
    }

    private fun setupWifiMonitor() {
        try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) return
            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
            connectivityManager.requestNetwork(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    WifiNetworkManager.network = network
                    try { connectivityManager.bindProcessToNetwork(network) } catch (_: Exception) {}
                }
                
                override fun onLost(network: Network) {
                    super.onLost(network)
                    if (WifiNetworkManager.network == network) WifiNetworkManager.network = null
                }
            })
        } catch (_: Exception) { }
    }
}

@Composable
fun AppNavigation(viewModel: HistoryViewModel) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "First") {
        composable("First") { FirstScreen(navController) }
        composable("Second") { SecondScreen(navController, viewModel) }
        composable("Sensor") { SensorScreen(viewModel) }
        composable("Camera") { CameraScreen() }
    }
}

@Preview (showBackground = true)
@Composable
fun FirstScreenPreview(){
    FirstScreen(navController = rememberNavController())
}

@Composable
fun FirstScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(painter = painterResource(id = R.drawable.logo), contentDescription = "Logo", modifier = Modifier.size(120.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Sistem Palang Pintu Otomatis", fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = { navController.navigate("Second") }, modifier = Modifier.fillMaxWidth(0.6f).height(40.dp)) {
            Text(text = "Mulai", fontSize = 20.sp)
        }
    }
}

@Composable
fun ManualGateControl(viewModel: HistoryViewModel) {
    val scope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (viewModel.isManualMode) "Mode: Manual" else "Mode: Otomatis",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (viewModel.isManualMode) Color(0xFFEF6C00) else Color(0xFF4CAF50)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    if (!isSending) {
                        isSending = true
                        scope.launch {
                            val success = sendOpenGate()
                            viewModel.logManualCommand(success, opening = true)
                            isSending = false
                        }
                    }
                },
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Text("Buka Manual")
            }

            Button(
                onClick = {
                    if (!isSending) {
                        isSending = true
                        scope.launch {
                            val success = sendCloseGate()
                            viewModel.logManualCommand(success, opening = false)
                            isSending = false
                        }
                    }
                },
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("Tutup Manual")
            }
        }
    }
}

@Composable
fun SecondScreen(navController : NavController, viewModel: HistoryViewModel) {
    val context = LocalContext.current
    val logs by viewModel.logs.collectAsState()
    var isProcessingScan by remember { mutableStateOf(false) }
    var hasScannedCurrentVehicle by remember { mutableStateOf(false) }    
    
    LaunchedEffect(Unit) {
        val url = ESP32_BASE_URL

        while (true) {
            try {
                val data = fetchSensorData(url)

                if (data != null) {
                    viewModel.updateDataAndCheckNotification(context, data)

                    val sensorInDetected = data.distance1 > 0 && data.distance1 <= 50

                    if (!sensorInDetected) {
                        hasScannedCurrentVehicle = false
                    }

                    if (sensorInDetected && !isProcessingScan && !hasScannedCurrentVehicle) {
                        isProcessingScan = true

                        val imageBytes = fetchCameraImage("http://192.168.10.20/capture")
                        val bitmap = imageBytes?.let {
                            BitmapFactory.decodeByteArray(it, 0, it.size)
                        }

                        val scanText = bitmap?.let {
                            recognizeText(it)
                        }.orEmpty()

                        val normalizedPlate = scanText
                            .uppercase()
                            .replace(" ", "")

                        if (normalizedPlate.isNotBlank()) {
                            viewModel.addLog("Plat Terdeteksi: $normalizedPlate")
                        }

                        if (allowedPlates.contains(normalizedPlate)) {
                            sendOpenGate()
                        }

                        hasScannedCurrentVehicle = true
                        isProcessingScan = false
                    }
                }
            } catch (_: Exception) {
                isProcessingScan = false
            }

            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 40.dp, bottom = 40.dp, start = 24.dp, end = 24.dp), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Sistem Palang Pintu Otomatis", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Monitoring Sensor", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    navController.navigate("Sensor")
                }
            ) {
                Text("Monitor & Notifikasi")
            }

            Button(
                onClick = {
                    navController.navigate("Camera")
                }
            ) {
                Text("Lihat Kamera")
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        ManualGateControl(viewModel = viewModel)
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Status Terkini", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(60.dp).background(Color(0xFFE3F2FD)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = viewModel.lastStatusMessage, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(text = "Waktu: ${viewModel.lastStatusTime}", fontSize = 12.sp, color = Color.Gray)
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(text = "Riwayat Aktivitas", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp).background(Color(0xFFF5F5F5))) {
            items(logs) { log ->
                Row(modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.White).padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = log.message, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(text = log.time, fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Button(onClick = { viewModel.clearLogs() }, modifier = Modifier.fillMaxWidth(0.6f).height(45.dp)) {
            Text(text = "Hapus Riwayat", fontSize = 18.sp)
        }
    }
}

@Composable
fun SensorScreen(viewModel: HistoryViewModel) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Mencoba Koneksi...") }
    var statusColor by remember { mutableStateOf(Color.Gray) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val url = ESP32_BASE_URL

        while (true) {
            try {
                val data = fetchSensorData(url)
                if (data != null) {
                    viewModel.updateDataAndCheckNotification(context, data)
                    status = "Terhubung"
                    statusColor = Color(0xFF4CAF50)
                } else {
                    if (WifiNetworkManager.network == null) {
                         status = "Mencari Wi-Fi (Cek Koneksi HP)..."
                         statusColor = Color(0xFFFFA000) 
                    } else {
                         status = "Timeout / Gagal Ambil Data"
                         statusColor = Color.Red
                    }
                }
            } catch (_: Exception) {
                status = "Error Koneksi"
                statusColor = Color.Red
            }
            delay(1000)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, bottom = 40.dp, start = 16.dp, end = 16.dp)
                .background(if (statusColor == Color.Red) Color(0xFFFFEBEE) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = status,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFBBDEFB)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Jarak 1 (Masuk)", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = String.format(Locale.US,"%.2f cm", viewModel.lastDistance1), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color.Blue)
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFFFF9C4)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Sensor Cahaya", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = String.format(Locale.US,"%.2f lx", viewModel.lastLdr), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD600))
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFFE0F7FA)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Jarak 2 (Keluar)", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = String.format(Locale.US,"%.2f cm", viewModel.lastDistance2), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF006064))
            }
        }
    }
}

@Composable
fun CameraScreen() {

    var imageBytes by remember {
        mutableStateOf<ByteArray?>(null)
    }

    var recognizedText by remember {
        mutableStateOf("")
    }

    LaunchedEffect(Unit) {

        imageBytes = fetchCameraImage(
            "http://192.168.10.20/capture"
        )

        imageBytes?.let {

            val bitmap = BitmapFactory.decodeByteArray(
                it,
                0,
                it.size
            )

            recognizedText = recognizeText(bitmap)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        imageBytes?.let {

            val bitmap = BitmapFactory.decodeByteArray(
                it,
                0,
                it.size
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "ESP32 Camera",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = recognizedText.ifBlank {
                        "Belum ada teks"
                    }
                )
            }

        } ?: Text("Mengambil gambar...")
    }
}
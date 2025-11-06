package com.example.bhereucf

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.security.MessageDigest
import java.util.UUID

class TeacherClassDetailActivity : ComponentActivity() {

    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }
    private val bluetoothLeAdvertiser by lazy { bluetoothAdapter?.bluetoothLeAdvertiser }

    private var isAdvertising = false
    private var deviceName: String? = null
    private var userId: String? = null
    private var classId: String? = null
    private var classCode: String? = null
    private var section: String? = null
    private var originalBluetoothName: String? = null
    private var broadcastSecret: String? = null
    private var broadcastName: String? = null

    private lateinit var advertisingStatusText: TextView

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            startAdvertisingFlow()
        } else {
            Toast.makeText(this, "Permissions are required.", Toast.LENGTH_LONG).show()
        }
    }

    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startAdvertisingFlow()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to advertise.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teacher_class_detail)

        // Get data from intent
        classId = intent.getStringExtra("CLASS_ID")
        deviceName = intent.getStringExtra("DEVICE_NAME")
        userId = intent.getStringExtra("USER_ID")
        classCode = intent.getStringExtra("CLASS_CODE")
        section = intent.getStringExtra("SECTION")

        advertisingStatusText = findViewById(R.id.advertising_status_text)

        val takeAttendanceButton: Button = findViewById(R.id.take_attendance_button)
        takeAttendanceButton.setOnClickListener {
            if (isAdvertising) {
                stopAdvertising()
            } else {
                // Before starting, try to end any existing broadcast first if prepareBroadcast fails
                requestPermissionsAndStartAdvertising()
            }
        }
    }

    private fun requestPermissionsAndStartAdvertising() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            )
        } else {
            requestPermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    private fun startAdvertisingFlow() {
        // Validate required data
        if (userId.isNullOrEmpty() || classId.isNullOrEmpty()) {
            Toast.makeText(this, "Missing class information.", Toast.LENGTH_LONG).show()
            Log.e("Broadcast", "Missing userId or classId - userId: $userId, classId: $classId")
            return
        }
        
        // Call API to prepare broadcast before starting Bluetooth advertising
        prepareBroadcastAPI()
    }
    
    private fun prepareBroadcastAPI() {
        // Validate all required fields
        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: User ID is missing", Toast.LENGTH_LONG).show()
            Log.e("Broadcast", "User ID is null or empty")
            return
        }
        
        if (classId.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Class ID is missing", Toast.LENGTH_LONG).show()
            Log.e("Broadcast", "Class ID is null or empty")
            return
        }
        
        if (classCode.isNullOrEmpty() || section.isNullOrEmpty()) {
            Toast.makeText(this, "Error: Class code or section is missing", Toast.LENGTH_LONG).show()
            Log.e("Broadcast", "Class code or section is null or empty")
            return
        }
        
        Log.d("Broadcast", "Sending prepareBroadcast - userId: $userId, objectId: $classId")
        
        val request = PrepareBroadcastRequest(
            userId = userId!!,
            objectId = classId!!
        )
        
        val call = RetrofitClient.apiService.prepareBroadcast(request)
        call.enqueue(object : Callback<PrepareBroadcastResponse> {
            override fun onResponse(call: Call<PrepareBroadcastResponse>, response: Response<PrepareBroadcastResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val broadcastResponse = response.body()!!
                    
                    if (broadcastResponse.error.isEmpty()) {
                        Log.d("Broadcast", "PrepareBroadcast successful, now calling newSecret")
                        // Now call newSecret to set the secret
                        newSecretAPI()
                    } else {
                        val errorMsg = broadcastResponse.error
                        Toast.makeText(this@TeacherClassDetailActivity, "Error: $errorMsg", Toast.LENGTH_SHORT).show()
                        Log.e("Broadcast", "Failed to prepare broadcast: $errorMsg")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("Broadcast", "HTTP ${response.code()} - Error body: $errorBody")
                    Log.e("Broadcast", "Request sent - userId: $userId, objectId: $classId")
                    
                    // Parse error message to provide helpful feedback
                    val errorMsg = try {
                        if (errorBody != null && errorBody.contains("\"error\"")) {
                            val jsonObject = org.json.JSONObject(errorBody)
                            jsonObject.optString("error", errorBody)
                        } else {
                            errorBody ?: "Failed to prepare broadcast (HTTP ${response.code()})"
                        }
                    } catch (e: Exception) {
                        errorBody ?: "Failed to prepare broadcast (HTTP ${response.code()})"
                    }
                    
                    Log.d("Broadcast", "Parsed error message: '$errorMsg'")
                    
                    // Check if attendance is already active - match various error message formats
                    val isAttendanceActiveError = errorMsg.contains("already active", ignoreCase = true) || 
                        errorMsg.contains("ATTENDANCE_ACTIVE", ignoreCase = true) ||
                        errorMsg.contains("attendance session", ignoreCase = true) && errorMsg.contains("active", ignoreCase = true)
                    
                    if (isAttendanceActiveError) {
                        Log.d("Broadcast", "Attendance is already active - detected: '$errorMsg'")
                        Log.d("Broadcast", "Automatically ending existing session and retrying...")
                        Toast.makeText(this@TeacherClassDetailActivity, "Ending existing session...", Toast.LENGTH_SHORT).show()
                        endBroadcastAPI { success ->
                            if (success) {
                                Log.d("Broadcast", "Previous session ended successfully, retrying prepareBroadcast...")
                                // Retry prepareBroadcast after a short delay
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    prepareBroadcastAPI()
                                }, 500)
                            } else {
                                Log.e("Broadcast", "Failed to end existing session")
                                Toast.makeText(this@TeacherClassDetailActivity, "Failed to end existing session. Please try again.", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        Toast.makeText(this@TeacherClassDetailActivity, errorMsg, Toast.LENGTH_LONG).show()
                        Log.e("Broadcast", "Error message (not attendance active): $errorMsg")
                    }
                }
            }
            
            override fun onFailure(call: Call<PrepareBroadcastResponse>, t: Throwable) {
                Toast.makeText(this@TeacherClassDetailActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("Broadcast", "Error preparing broadcast", t)
            }
        })
    }
    
    private fun newSecretAPI() {
        // Generate a secret hash for this broadcast session
        val secret = generateSecretHash()
        
        // Format broadcast name as: {ClassCode}{Section}-{SecretHash}
        val formattedBroadcastName = "${classCode}${section}-$secret"
        
        // Store locally
        broadcastName = formattedBroadcastName
        broadcastSecret = secret
        
        Log.d("Broadcast", "Generated secret: $secret, broadcast name: $formattedBroadcastName")
        
        val request = NewSecretRequest(
            userId = userId!!,
            objectId = classId!!,
            secret = secret
        )
        
        val call = RetrofitClient.apiService.newSecret(request)
        call.enqueue(object : Callback<NewSecretResponse> {
            override fun onResponse(call: Call<NewSecretResponse>, response: Response<NewSecretResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val secretResponse = response.body()!!
                    
                    if (secretResponse.error.isEmpty()) {
                        Log.d("Broadcast", "Secret set successfully, starting broadcast")
                        // Start Bluetooth advertising with the formatted broadcast name
                        startAdvertising(formattedBroadcastName)
                    } else {
                        val errorMsg = secretResponse.error
                        Toast.makeText(this@TeacherClassDetailActivity, "Error setting secret: $errorMsg", Toast.LENGTH_SHORT).show()
                        Log.e("Broadcast", "Failed to set secret: $errorMsg")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("Broadcast", "HTTP ${response.code()} - Error body: $errorBody")
                    
                    val errorMsg = errorBody ?: "Failed to set secret (HTTP ${response.code()})"
                    Toast.makeText(this@TeacherClassDetailActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
            
            override fun onFailure(call: Call<NewSecretResponse>, t: Throwable) {
                Toast.makeText(this@TeacherClassDetailActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("Broadcast", "Error setting secret", t)
            }
        })
    }
    
    private fun generateSecretHash(): String {
        // Generate a unique secret hash using UUID and timestamp
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        val combined = "$timestamp$uuid${classCode}${section}${userId}"
        
        // Create a short hash (8 characters) from the combined string
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(combined.toByteArray())
        
        // Convert to hex and take first 8 characters
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        return hexString.substring(0, 8)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            val broadcastDisplayName = broadcastName ?: deviceName ?: "Unknown"
            val status = "Status: Advertising\nName: $broadcastDisplayName"
            advertisingStatusText.text = status
            Log.d("BleAdvertiser", "Advertising started with name: $broadcastDisplayName")
            Toast.makeText(this@TeacherClassDetailActivity, "Broadcast started", Toast.LENGTH_SHORT).show()
        }
        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val errorMessage = when (errorCode) {
                AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising already started"
                AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE -> "Advertisement data too large"
                AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE advertising not supported"
                AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Advertise failed: $errorCode"
            }
            advertisingStatusText.text = "Status: Not Advertising\nError: $errorMessage"
            Log.e("BleAdvertiser", errorMessage)
            Toast.makeText(this@TeacherClassDetailActivity, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(name: String) {
        val localBluetoothAdapter = bluetoothAdapter ?: run {
            advertisingStatusText.text = "Bluetooth adapter not available"
            return
        }

        val localBluetoothLeAdvertiser = bluetoothLeAdvertiser ?: run {
            advertisingStatusText.text = "BLE advertiser not available"
            return
        }

        if (!localBluetoothAdapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        try {
            originalBluetoothName = localBluetoothAdapter.name
            localBluetoothAdapter.name = name
            Log.d("BleAdvertiser", "Device name set to: $name")
        } catch (se: SecurityException) {
            Log.w("BleAdvertiser", "Could not set device name (this is common on modern Android). Continuing with advertising.", se)
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            localBluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback)
            Log.d("BleAdvertiser", "Advertising request sent")
        } catch (e: Exception) {
            val errorMessage = "Failed to start advertising: ${e.message}"
            Log.e("BleAdvertiser", errorMessage, e)
            advertisingStatusText.text = errorMessage
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        val localBluetoothLeAdvertiser = bluetoothLeAdvertiser ?: return
        if (!isAdvertising) return
        
        // Stop Bluetooth advertising first
        try {
            localBluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
            originalBluetoothName?.let { bluetoothAdapter?.name = it }
            isAdvertising = false
            advertisingStatusText.text = "Status: Not Advertising"
        } catch (se: SecurityException) {
            Log.e("BleAdvertiser", "stopAdvertising SecurityException", se)
        }
        
        // Call API to end broadcast
        if (!userId.isNullOrEmpty() && !classId.isNullOrEmpty()) {
            endBroadcastAPI()
        } else {
            Toast.makeText(this, "Broadcast stopped", Toast.LENGTH_SHORT).show()
            Log.w("Broadcast", "Cannot end broadcast - missing userId or classId. userId: $userId, classId: $classId")
        }
    }
    
    private fun endBroadcastAPI(onComplete: ((Boolean) -> Unit)? = null) {
        val request = EndBroadcastRequest(
            userId = userId!!,
            objectId = classId!!
        )
        
        Log.d("Broadcast", "Ending broadcast - userId: $userId, objectId: $classId")
        
        val call = RetrofitClient.apiService.endBroadcast(request)
        call.enqueue(object : Callback<Unit> {
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@TeacherClassDetailActivity, "Broadcast stopped", Toast.LENGTH_SHORT).show()
                    Log.d("Broadcast", "Broadcast ended successfully")
                    onComplete?.invoke(true)
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("Broadcast", "Failed to end broadcast: HTTP ${response.code()}, error: $errorBody")
                    Toast.makeText(this@TeacherClassDetailActivity, "Broadcast stopped (API error: ${response.code()})", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke(false)
                }
            }
            
            override fun onFailure(call: Call<Unit>, t: Throwable) {
                Toast.makeText(this@TeacherClassDetailActivity, "Broadcast stopped (network error)", Toast.LENGTH_SHORT).show()
                Log.e("Broadcast", "Error ending broadcast", t)
                onComplete?.invoke(false)
            }
        })
    }
}


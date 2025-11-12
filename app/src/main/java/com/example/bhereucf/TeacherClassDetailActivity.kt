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
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog
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
    private var classId: String? = null
    private var classCode: String? = null
    private var section: String? = null
    private var originalBluetoothName: String? = null
    private var broadcastSecret: String? = null
    private var broadcastName: String? = null

    private lateinit var advertisingStatusText: TextView
    private lateinit var classInfoText: TextView
    private lateinit var startClassButton: Button
    private lateinit var deleteClassButton: Button
    private lateinit var viewHistoryButton: Button

    companion object {
        const val REQUEST_CODE_ACTIVE_SESSION = 1003
    }

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
        // userId is now in JWT token - not passed via Intent
        classCode = intent.getStringExtra("CLASS_CODE")
        section = intent.getStringExtra("SECTION")

            // Setup views
            classInfoText = findViewById(R.id.class_info_text)
        advertisingStatusText = findViewById(R.id.advertising_status_text)
        startClassButton = findViewById(R.id.start_class_button)
        deleteClassButton = findViewById(R.id.delete_class_button)
        viewHistoryButton = findViewById(R.id.view_history_button)
        
        Log.d("DeleteClass", "=== ACTIVITY CREATED ===")
        Log.d("DeleteClass", "deleteClassButton initialized: ${deleteClassButton != null}")
        Log.d("DeleteClass", "classId: $classId")

        // Update class info text
        val className = intent.getStringExtra("CLASS_NAME") ?: ""
        val classInfo = if (classCode != null && section != null) {
            "$classCode-$section"
        } else {
            className
        }
        classInfoText.text = classInfo

        // Start Class button
        startClassButton.setOnClickListener {
            if (isAdvertising) {
                // If already advertising, navigate to active session
                val intent = Intent(this, ActiveClassSessionActivity::class.java).apply {
                    putExtra("CLASS_ID", classId)
                    // userId is now in JWT token - not passed via Intent
                    putExtra("CLASS_CODE", classCode)
                    putExtra("SECTION", section)
                    putExtra("BROADCAST_NAME", broadcastName)
                    putExtra("BROADCAST_SECRET", broadcastSecret)
                }
                startActivityForResult(intent, REQUEST_CODE_ACTIVE_SESSION)
            } else {
                requestPermissionsAndStartAdvertising()
            }
        }

        // Delete Class button
        Log.d("DeleteClass", "Setting up delete button click listener")
        deleteClassButton.setOnClickListener {
            Log.d("DeleteClass", "=== DELETE BUTTON CLICKED ===")
            Log.d("DeleteClass", "Button is clickable: ${deleteClassButton.isClickable}")
            Log.d("DeleteClass", "Button is enabled: ${deleteClassButton.isEnabled}")
            showDeleteConfirmationDialog()
        }
        Log.d("DeleteClass", "Delete button click listener set up")

            // View History button
            viewHistoryButton.setOnClickListener {
                viewHistory()
            }

            // Initialize advertise callback
            advertiseCallback = createAdvertiseCallback()
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
        if (classId.isNullOrEmpty()) {
            Toast.makeText(this, "Missing class information.", Toast.LENGTH_LONG).show()
            Log.e("Broadcast", "Missing classId - classId: $classId")
            return
        }
        
        // Call API to prepare broadcast before starting Bluetooth advertising
        prepareBroadcastAPI()
    }
    
    private fun prepareBroadcastAPI() {
        // Validate all required fields
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
        
        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_LONG).show()
            Log.e("Broadcast", "Failed to extract userId from JWT token")
            return
        }
        Log.d("Broadcast", "Sending prepareBroadcast - userId: $actualUserId, objectId: $classId")
        
        val request = PrepareBroadcastRequest(
            userId = actualUserId,
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
                    Log.e("Broadcast", "Request sent - objectId: $classId")
                    
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
        
        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_LONG).show()
            Log.e("Broadcast", "Failed to extract userId from JWT token")
            return
        }
        Log.d("Broadcast", "Generated secret: $secret, broadcast name: $formattedBroadcastName")
        
        val request = NewSecretRequest(
            userId = actualUserId,
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
        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this) ?: ""
        // Generate a unique secret hash using UUID and timestamp
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        val combined = "$timestamp$uuid${classCode}${section}$actualUserId"
        
        // Create a short hash (8 characters) from the combined string
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(combined.toByteArray())
        
        // Convert to hex and take first 8 characters
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        return hexString.substring(0, 8)
    }

        private lateinit var advertiseCallback: AdvertiseCallback
        
        private fun createAdvertiseCallback(): AdvertiseCallback {
            return object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    isAdvertising = true
                    val broadcastDisplayName = broadcastName ?: deviceName ?: "Unknown"
                    Log.d("BleAdvertiser", "Advertising started with name: $broadcastDisplayName")
                    
                    // Stop advertising here (ActiveClassSessionActivity will restart it)
                    @SuppressLint("MissingPermission")
                    val localBluetoothLeAdvertiser = bluetoothLeAdvertiser
                    if (localBluetoothLeAdvertiser != null && ::advertiseCallback.isInitialized) {
                        try {
                            localBluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
                        } catch (e: Exception) {
                            Log.e("BleAdvertiser", "Error stopping advertising", e)
                        }
                    }
                    isAdvertising = false
                    
                    // Navigate to active class session activity
                    val intent = Intent(this@TeacherClassDetailActivity, ActiveClassSessionActivity::class.java).apply {
                        putExtra("CLASS_ID", classId)
                        // userId is now in JWT token - not passed via Intent
                        putExtra("CLASS_CODE", classCode)
                        putExtra("SECTION", section)
                        putExtra("BROADCAST_NAME", broadcastDisplayName)
                        putExtra("BROADCAST_SECRET", broadcastSecret)
                    }
                    startActivityForResult(intent, REQUEST_CODE_ACTIVE_SESSION)
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
                    advertisingStatusText.visibility = android.view.View.VISIBLE
                    startClassButton.text = "Start Class"
                    Log.e("BleAdvertiser", errorMessage)
                    Toast.makeText(this@TeacherClassDetailActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
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
            advertisingStatusText.visibility = android.view.View.GONE
            startClassButton.text = "Start Class"
        } catch (se: SecurityException) {
            Log.e("BleAdvertiser", "stopAdvertising SecurityException", se)
        }
        
        // Call API to end broadcast
        if (!classId.isNullOrEmpty()) {
            endBroadcastAPI()
        } else {
            Toast.makeText(this, "Broadcast stopped", Toast.LENGTH_SHORT).show()
            Log.w("Broadcast", "Cannot end broadcast - missing classId. classId: $classId")
        }
    }
    
    private fun endBroadcastAPI(onComplete: ((Boolean) -> Unit)? = null) {
        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_LONG).show()
            Log.e("Broadcast", "Failed to extract userId from JWT token")
            onComplete?.invoke(false)
            return
        }

        val request = EndBroadcastRequest(
            userId = actualUserId,
            objectId = classId!!
        )
        
        Log.d("Broadcast", "Ending broadcast - userId: $actualUserId, objectId: $classId")
        
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

    private fun showDeleteConfirmationDialog() {
        Log.d("DeleteClass", "=== SHOW DELETE DIALOG ===")
        Log.d("DeleteClass", "classId: $classId")
        Log.d("DeleteClass", "=========================")
        
        AlertDialog.Builder(this)
            .setTitle("Delete Class")
            .setMessage("Are you sure you want to delete this class? This will remove all students from the class and delete all attendance records. This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                Log.d("DeleteClass", "=== DELETE CONFIRMED IN DIALOG ===")
                deleteClass()
            }
            .setNegativeButton("Cancel") { _, _ ->
                Log.d("DeleteClass", "Delete cancelled by user")
            }
            .setOnDismissListener {
                Log.d("DeleteClass", "Dialog dismissed")
            }
            .show()
    }

    private fun deleteClass() {
        if (classId.isNullOrEmpty()) {
            Toast.makeText(this, "Missing class information", Toast.LENGTH_SHORT).show()
            Log.e("DeleteClass", "Missing data - classId: $classId")
            return
        }

        val trimmedClassId = classId!!.trim()

        Log.d("DeleteClass", "=== DELETE CLASS REQUEST ===")
        Log.d("DeleteClass", "classId: '$trimmedClassId'")
        Log.d("DeleteClass", "=========================")

        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_LONG).show()
            Log.e("DeleteClass", "Failed to extract userId from JWT token")
            return
        }

        val request = DeleteClassRequest(
            userId = actualUserId,
            classId = trimmedClassId
        )

        Log.d("DeleteClass", "Making API call with request: userId='$actualUserId', classId='$trimmedClassId'")
        
        val call = RetrofitClient.apiService.deleteClass(request)
        call.enqueue(object : Callback<DeleteClassResponse> {
            override fun onResponse(call: Call<DeleteClassResponse>, response: Response<DeleteClassResponse>) {
                Log.d("DeleteClass", "Response received - code: ${response.code()}, isSuccessful: ${response.isSuccessful}")
                
                if (response.isSuccessful) {
                    val deleteResponse = response.body()
                    if (deleteResponse != null) {
                        Log.d("DeleteClass", "Response body: error='${deleteResponse.error}'")
                        if (deleteResponse.error.isEmpty()) {
                            Log.d("DeleteClass", "Class deleted successfully")
                            Log.d("DeleteClass", "Setting RESULT_OK and finishing activity")
                            Toast.makeText(this@TeacherClassDetailActivity, "Class deleted successfully", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        } else {
                            Log.e("DeleteClass", "Error in response: ${deleteResponse.error}")
                            Toast.makeText(this@TeacherClassDetailActivity, "Error: ${deleteResponse.error}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        // Response body is null, but status is successful - might be empty response
                        Log.d("DeleteClass", "Response body is null, but status is successful - treating as success")
                        Log.d("DeleteClass", "Setting RESULT_OK and finishing activity")
                        Toast.makeText(this@TeacherClassDetailActivity, "Class deleted successfully", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    Log.e("DeleteClass", "HTTP ${response.code()} - Error body: $errorBody")
                    
                    val errorMsg = try {
                        if (errorBody != null && errorBody.contains("\"error\"")) {
                            val jsonObject = org.json.JSONObject(errorBody)
                            jsonObject.optString("error", errorBody)
                        } else {
                            errorBody ?: "Failed to delete class (HTTP ${response.code()})"
                        }
                    } catch (e: Exception) {
                        Log.e("DeleteClass", "Error parsing error body", e)
                        errorBody ?: "Failed to delete class (HTTP ${response.code()})"
                    }
                    Log.e("DeleteClass", "Error message: $errorMsg")
                    Toast.makeText(this@TeacherClassDetailActivity, errorMsg, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<DeleteClassResponse>, t: Throwable) {
                Log.e("DeleteClass", "Network error: ${t.message}", t)
                Toast.makeText(this@TeacherClassDetailActivity, "Network error: ${t.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun viewHistory() {
        if (classId.isNullOrEmpty()) {
            Toast.makeText(this, "Missing class information", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, TeacherHistoryActivity::class.java).apply {
            putExtra("CLASS_ID", classId)
            putExtra("CLASS_CODE", classCode)
            putExtra("SECTION", section)
        }
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_ACTIVE_SESSION && resultCode == RESULT_OK) {
            // Class session ended, update UI
            isAdvertising = false
            startClassButton.text = "Start Class"
            advertisingStatusText.visibility = android.view.View.GONE
        }
    }
}


package com.example.bhereucf

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class StudentAttendanceActivity : ComponentActivity() {

    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }
    private val bluetoothLeScanner: BluetoothLeScanner? by lazy { bluetoothAdapter?.bluetoothLeScanner }

    private var isScanning = false
    private var foundDevice = false
    private var attendanceMarked = false // Flag to prevent multiple marks
    private var userId: String? = null
    private var classId: String? = null
    private var classCode: String? = null
    private var section: String? = null
    private var className: String? = null
    private var expectedDeviceName: String? = null
    private var broadcastSecret: String? = null

    private lateinit var classInfoText: TextView
    private lateinit var viewHistoryButton: Button
    private lateinit var leaveClassButton: Button
    private lateinit var statusText: TextView

    private val scanHandler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000 // 10 seconds

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            startScanning()
        } else {
            Toast.makeText(this, "Permissions are required to scan for attendance.", Toast.LENGTH_LONG).show()
        }
    }

    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startScanning()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to scan.", Toast.LENGTH_LONG).show()
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val deviceName = result.device?.name
            Log.d("BLEScanner", "Found device: $deviceName, ClassCode: $classCode, Section: $section")
            
            // Match broadcast format: {ClassCode}{Section}-{SecretHash}
            // Format: ClassCode + Section + "-" + SecretHash
            if (deviceName != null && classCode != null && section != null && !attendanceMarked) {
                val expectedPrefix = "$classCode$section-"
                Log.d("BLEScanner", "Checking device: '$deviceName' against prefix: '$expectedPrefix'")
                
                if (deviceName.startsWith(expectedPrefix, ignoreCase = true)) {
                    // Extract the secret from the broadcast name
                    // Format is: {ClassCode}{Section}-{SecretHash}
                    // substringAfter gets everything after the FIRST occurrence of "-"
                    val secret = deviceName.substringAfter("-", "")
                    
                    if (secret.isNotEmpty()) {
                        foundDevice = true
                        broadcastSecret = secret
                        Log.d("BLEScanner", "Matched broadcast: $deviceName")
                        Log.d("BLEScanner", "Extracted secret: '$secret' (length: ${secret.length})")
                        Log.d("BLEScanner", "ClassCode: '$classCode', Section: '$section', ClassId: '$classId', UserId: '$userId'")
                        
                        // Automatically mark attendance
                        markPresentAutomatically(secret)
                    } else {
                        Log.w("BLEScanner", "Found matching prefix but secret extraction resulted in empty string")
                    }
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            for (result in results) {
                onScanResult(0, result) // Use 0 as callback type for batch results
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLEScanner", "Scan failed with error: $errorCode")
            statusText.text = "Scan failed. Please try again."
            statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_red_dark))
            isScanning = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_attendance_layout)

        // Get data from intent - trim userId to ensure consistency with backend
        userId = intent.getStringExtra("USER_ID")?.trim()
        classId = intent.getStringExtra("CLASS_ID")?.trim()
        classCode = intent.getStringExtra("CLASS_CODE")?.trim()
        section = intent.getStringExtra("SECTION")?.trim()
        className = intent.getStringExtra("CLASS_NAME")?.trim()
        expectedDeviceName = intent.getStringExtra("DEVICE_NAME")?.trim()
        
        // Log received values for debugging
        Log.d("StudentAttendance", "=== RECEIVED FROM INTENT ===")
        Log.d("StudentAttendance", "userId: '$userId'")
        Log.d("StudentAttendance", "classId: '$classId'")
        Log.d("StudentAttendance", "classCode: '$classCode', section: '$section'")
        Log.d("StudentAttendance", "============================")

        // Initialize views
        val backButton: ImageView = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish()
        }

        classInfoText = findViewById(R.id.class_info_text)
        viewHistoryButton = findViewById(R.id.view_history_button)
        leaveClassButton = findViewById(R.id.leave_class_button)
        statusText = findViewById(R.id.status_text)

        // Set class info
        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        val classInfo = "$classCode-$section - $currentDate"
        classInfoText.text = classInfo

        statusText.text = "Scanning for teacher broadcast..."
        statusText.setTextColor(ContextCompat.getColor(this, R.color.white))

        viewHistoryButton.setOnClickListener {
            // Navigate to history page
            val intent = Intent(this, StudentAttendanceHistoryActivity::class.java).apply {
                putExtra("USER_ID", userId)
                putExtra("CLASS_CODE", classCode)
                putExtra("SECTION", section)
                putExtra("CLASS_NAME", className)
            }
            startActivity(intent)
        }

        leaveClassButton.setOnClickListener {
            // Show confirmation dialog or directly leave
            leaveClass()
        }

        // Start scanning when activity starts
        requestPermissionsAndStartScanning()
    }

    private fun requestPermissionsAndStartScanning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
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

    @SuppressLint("MissingPermission")
    private fun startScanning() {
        val localBluetoothAdapter = bluetoothAdapter ?: run {
            statusText.text = "Bluetooth adapter not available"
            return
        }

        val localScanner = bluetoothLeScanner ?: run {
            statusText.text = "BLE scanner not available"
            return
        }

        if (!localBluetoothAdapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        if (isScanning) {
            return
        }

        foundDevice = false
        isScanning = true
        statusText.text = "Scanning for teacher broadcast..."
        statusText.setTextColor(ContextCompat.getColor(this, R.color.white))

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            localScanner.startScan(null, settings, scanCallback)
            Log.d("BLEScanner", "Started scanning")

                // Stop scanning after SCAN_PERIOD
                scanHandler.postDelayed({
                    if (isScanning) {
                        stopScanning()
                        if (!foundDevice && !attendanceMarked) {
                            statusText.text = "Teacher broadcast not found.\nPlease wait and scan again."
                            statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                            // Restart scanning after a delay
                            scanHandler.postDelayed({
                                if (!attendanceMarked) {
                                    requestPermissionsAndStartScanning()
                                }
                            }, 2000)
                        }
                    }
                }, SCAN_PERIOD)
        } catch (e: Exception) {
            Log.e("BLEScanner", "Error starting scan", e)
            statusText.text = "Error starting scan: ${e.message}"
            isScanning = false
        }
    }

        @SuppressLint("MissingPermission")
        private fun stopScanning() {
            if (!isScanning) return
            bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            scanHandler.removeCallbacksAndMessages(null)
            Log.d("BLEScanner", "Stopped scanning")
        }

    private fun markPresentAutomatically(secret: String) {
        val currentUserId = userId
        val currentClassId = classId

        if (currentUserId == null || currentClassId == null) {
            Log.e("MarkAttendance", "Missing info - userId: $currentUserId, classId: $currentClassId")
            return
        }

        // Prevent multiple marks
        if (attendanceMarked) {
            Log.d("MarkAttendance", "Attendance already marked, skipping")
            return
        }

        attendanceMarked = true
        stopScanning()
        
        // Update status
        statusText.text = "Teacher broadcast found!\nMarking attendance..."
        statusText.setTextColor(ContextCompat.getColor(this, R.color.white))

        // Trim and validate before sending
        val trimmedUserId = currentUserId.trim()
        val trimmedClassId = currentClassId.trim()
        val trimmedSecret = secret.trim()

        // Log the values being sent for debugging (with exact values)
        Log.d("MarkAttendance", "=== MARK ATTENDANCE REQUEST ===")
        Log.d("MarkAttendance", "userId (before trim): '$currentUserId' (length: ${currentUserId?.length})")
        Log.d("MarkAttendance", "userId (after trim): '$trimmedUserId' (length: ${trimmedUserId.length})")
        Log.d("MarkAttendance", "objectId (before trim): '$currentClassId' (length: ${currentClassId?.length})")
        Log.d("MarkAttendance", "objectId (after trim): '$trimmedClassId' (length: ${trimmedClassId.length})")
        Log.d("MarkAttendance", "secret: '$trimmedSecret' (length: ${trimmedSecret.length})")
        Log.d("MarkAttendance", "classCode: '$classCode', section: '$section'")
        Log.d("MarkAttendance", "===============================")

        val request = MarkAttendanceRequest(
            userId = trimmedUserId,
            objectId = trimmedClassId,
            secret = trimmedSecret
        )

        val call = RetrofitClient.apiService.markAttendance(request)
        call.enqueue(object : Callback<MarkAttendanceResponse> {
            override fun onResponse(call: Call<MarkAttendanceResponse>, response: Response<MarkAttendanceResponse>) {

                Log.d("MarkAttendance", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful && response.body() != null) {
                    val markResponse = response.body()!!
                    Log.d("MarkAttendance", "Response body - success: ${markResponse.success}, error: '${markResponse.error}'")
                    
                    // Consider success if: success is true OR error is empty
                    val isSuccess = markResponse.success || markResponse.error.isEmpty()
                    
                    if (isSuccess) {
                        statusText.text = "Attendance marked successfully!"
                        statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_green_dark))
                        Toast.makeText(this@StudentAttendanceActivity, "Attendance marked successfully!", Toast.LENGTH_SHORT).show()
                        
                        // Show confirmation screen after a short delay
                        scanHandler.postDelayed({
                            val intent = Intent(this@StudentAttendanceActivity, StudentAttendanceConfirmationActivity::class.java).apply {
                                putExtra("CLASS_CODE", classCode)
                                putExtra("SECTION", section)
                                putExtra("CLASS_NAME", className)
                            }
                            startActivity(intent)
                            finish()
                        }, 1000)
                    } else {
                        val backendError = markResponse.error
                        val errorMessage = if (backendError.isNotEmpty()) {
                            backendError
                        } else {
                            "Failed to mark attendance. Please try again."
                        }
                        
                        // Provide helpful message if student is not enrolled
                        val finalErrorMessage = if (backendError.contains("not enrolled", ignoreCase = true) || 
                            backendError.contains("NOT_IN_CLASS", ignoreCase = true)) {
                            "You are not enrolled in this class. Please join the class first from your class list."
                        } else {
                            errorMessage
                        }
                        
                        statusText.text = "Error: $finalErrorMessage"
                        statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_red_dark))
                        Log.e("MarkAttendance", "Failed to mark attendance: $errorMessage")
                        Log.e("MarkAttendance", "Backend error: $backendError")
                        Toast.makeText(this@StudentAttendanceActivity, finalErrorMessage, Toast.LENGTH_LONG).show()
                        // Reset flag so they can try again
                        attendanceMarked = false
                        // Don't restart scanning if not enrolled - they need to join the class
                        if (!backendError.contains("not enrolled", ignoreCase = true) && 
                            !backendError.contains("NOT_IN_CLASS", ignoreCase = true)) {
                            requestPermissionsAndStartScanning()
                        }
                    }
                } else {
                    // HTTP error response - try to read error body
                    val errorBody = response.errorBody()?.string()
                    Log.e("MarkAttendance", "=== HTTP ERROR RESPONSE ===")
                    Log.e("MarkAttendance", "HTTP status code: ${response.code()}")
                    Log.e("MarkAttendance", "Raw error body: $errorBody")
                    Log.e("MarkAttendance", "Request was - userId: '$trimmedUserId', objectId: '$trimmedClassId', secret: '$trimmedSecret'")
                    
                    // Try to parse the error message from the JSON response
                    val parsedError = try {
                        if (errorBody != null && errorBody.isNotEmpty()) {
                            // Try to extract error message from JSON like {"error": "message"}
                            val jsonObject = org.json.JSONObject(errorBody)
                            val errorMsg = jsonObject.optString("error", errorBody)
                            Log.e("MarkAttendance", "Parsed error message: '$errorMsg'")
                            errorMsg
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("MarkAttendance", "Failed to parse error JSON: ${e.message}")
                        errorBody
                    }
                    
                    val errorMessage = parsedError ?: when (response.code()) {
                        404 -> "Attendance session not found. The teacher may have stopped broadcasting."
                        400 -> "Invalid request. Please check your input."
                        403 -> "You do not have permission to mark attendance for this class."
                        else -> "Failed to mark attendance (HTTP ${response.code()}). Please try again."
                    }
                    
                    // Provide helpful message if student is not enrolled
                    val finalErrorMessage = if (errorMessage.contains("not enrolled", ignoreCase = true) || 
                        errorMessage.contains("NOT_IN_CLASS", ignoreCase = true) ||
                        parsedError?.contains("not enrolled", ignoreCase = true) == true) {
                        "You are not enrolled in this class. Please join the class first from your class list."
                    } else {
                        errorMessage
                    }
                    
                    Log.e("MarkAttendance", "Final error message: $finalErrorMessage")
                    Log.e("MarkAttendance", "Raw error from backend: $errorMessage")
                    statusText.text = "Error: $finalErrorMessage"
                    statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_red_dark))
                    Toast.makeText(this@StudentAttendanceActivity, finalErrorMessage, Toast.LENGTH_LONG).show()
                    // Reset flag so they can try again
                    attendanceMarked = false
                    // Don't restart scanning if not enrolled - they need to join the class
                    if (!errorMessage.contains("not enrolled", ignoreCase = true) && 
                        !errorMessage.contains("NOT_IN_CLASS", ignoreCase = true)) {
                        requestPermissionsAndStartScanning()
                    }
                }
            }

            override fun onFailure(call: Call<MarkAttendanceResponse>, t: Throwable) {
                statusText.text = "Network error. Please try again."
                statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_red_dark))
                Toast.makeText(this@StudentAttendanceActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("MarkAttendance", "Error marking attendance", t)
                // Reset flag so they can try again
                attendanceMarked = false
                // Restart scanning
                requestPermissionsAndStartScanning()
            }
        })
    }

    private fun leaveClass() {
        val currentUserId = userId
        val currentClassCode = classCode
        val currentSection = section

        if (currentUserId == null || currentClassCode == null || currentSection == null) {
            Toast.makeText(this, "Missing class information", Toast.LENGTH_SHORT).show()
            Log.e("LeaveClass", "Missing info - userId: $currentUserId, classCode: $currentClassCode, section: $currentSection")
            return
        }

        // Log the values being sent for debugging
        Log.d("LeaveClass", "Attempting to leave class - userId: $currentUserId, classCode: '$currentClassCode', section: '$currentSection'")

        leaveClassButton.isEnabled = false
        leaveClassButton.text = "Leaving..."

        val request = LeaveClassRequest(
            userId = currentUserId,
            classCode = currentClassCode,
            section = currentSection
        )

        val call = RetrofitClient.apiService.leaveClass(request)
        call.enqueue(object : Callback<LeaveClassResponse> {
            override fun onResponse(call: Call<LeaveClassResponse>, response: Response<LeaveClassResponse>) {
                leaveClassButton.isEnabled = true
                leaveClassButton.text = "Leave Class"

                Log.d("LeaveClass", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful && response.body() != null) {
                    val leaveResponse = response.body()!!
                    Log.d("LeaveClass", "Response body - success: ${leaveResponse.success}, error: '${leaveResponse.error}'")
                    
                    // Consider success if: success is true OR error is empty
                    val isSuccess = leaveResponse.success || leaveResponse.error.isEmpty()
                    
                    if (isSuccess) {
                        Toast.makeText(this@StudentAttendanceActivity, "Successfully left class", Toast.LENGTH_SHORT).show()
                        // Set result to indicate class was left, so parent activity can refresh
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        val errorMessage = if (leaveResponse.error.isNotEmpty()) {
                            leaveResponse.error
                        } else {
                            "Failed to leave class. Please try again."
                        }
                        Log.e("LeaveClass", "Failed to leave: $errorMessage")
                        Toast.makeText(this@StudentAttendanceActivity, "Error: $errorMessage", Toast.LENGTH_LONG).show()
                    }
                } else {
                    // HTTP error response - try to read error body
                    val errorBody = response.errorBody()?.string()
                    Log.e("LeaveClass", "HTTP error ${response.code()}, error body: $errorBody")
                    
                    val errorMessage = when (response.code()) {
                        404 -> {
                            // 404 could mean endpoint doesn't exist or class not found
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Error: $errorBody"
                            } else {
                                "Class not found or endpoint not available. Please check if the leave class API is implemented."
                            }
                        }
                        400 -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Invalid request: $errorBody"
                            } else {
                                "Invalid request. Please check your input."
                            }
                        }
                        403 -> "You do not have permission to leave this class."
                        else -> {
                            if (errorBody != null && errorBody.isNotEmpty()) {
                                "Failed to leave class: $errorBody"
                            } else {
                                "Failed to leave class (HTTP ${response.code()}). Please try again."
                            }
                        }
                    }
                    Toast.makeText(this@StudentAttendanceActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            }

            override fun onFailure(call: Call<LeaveClassResponse>, t: Throwable) {
                leaveClassButton.isEnabled = true
                leaveClassButton.text = "Leave Class"
                Toast.makeText(this@StudentAttendanceActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("LeaveClass", "Error leaving class", t)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
        scanHandler.removeCallbacksAndMessages(null)
    }
}


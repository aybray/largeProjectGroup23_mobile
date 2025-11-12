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
import android.content.SharedPreferences
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
    private var lastUsedBroadcastName: String? = null // Track the last broadcast name that was used for marking
    private val markingLock = Any() // Lock for synchronizing attendance marking
    
    // SharedPreferences key for persisting lastUsedBroadcastName per class
    private fun getLastUsedBroadcastKey(): String {
        return "lastUsedBroadcast_${classId ?: "unknown"}"
    }
    
    private fun getSharedPreferences(): SharedPreferences {
        return getSharedPreferences("StudentAttendancePrefs", Context.MODE_PRIVATE)
    }
    
    private fun loadLastUsedBroadcastName() {
        val prefs = getSharedPreferences()
        val key = getLastUsedBroadcastKey()
        lastUsedBroadcastName = prefs.getString(key, null)
        Log.d("StudentAttendance", "📂 Loaded lastUsedBroadcastName from SharedPreferences: '$lastUsedBroadcastName' (key: '$key')")
    }
    
    private fun saveLastUsedBroadcastName(broadcastName: String) {
        val prefs = getSharedPreferences()
        val key = getLastUsedBroadcastKey()
        prefs.edit().putString(key, broadcastName).apply()
        Log.d("StudentAttendance", "💾 Saved lastUsedBroadcastName to SharedPreferences: '$broadcastName' (key: '$key')")
    }
    
    private fun clearLastUsedBroadcastName() {
        val prefs = getSharedPreferences()
        val key = getLastUsedBroadcastKey()
        prefs.edit().remove(key).apply()
        Log.d("StudentAttendance", "🗑️ Cleared lastUsedBroadcastName from SharedPreferences (key: '$key')")
    }
    private var classId: String? = null
    private var classCode: String? = null
    private var section: String? = null
    private var className: String? = null
    private var expectedDeviceName: String? = null
    private var currentBroadcastName: String? = null // Current detected broadcast name

    private lateinit var classInfoText: TextView
    private lateinit var markHereButton: Button
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
            if (deviceName != null && classCode != null && section != null) {
                val expectedPrefix = "$classCode$section-"
                Log.d("BLEScanner", "Checking device: '$deviceName' against prefix: '$expectedPrefix'")
                
                if (deviceName.startsWith(expectedPrefix, ignoreCase = true)) {
                    // Store the full broadcast name (e.g., "COP4331001-a1b2c3d4")
                    val trimmedBroadcastName = deviceName.trim()
                    
                    Log.d("BLEScanner", "=== BROADCAST DETECTED ===")
                    Log.d("BLEScanner", "📡 Detected broadcast name: '$trimmedBroadcastName'")
                    Log.d("BLEScanner", "📋 Last used broadcast name: '$lastUsedBroadcastName'")
                    Log.d("BLEScanner", "📋 Current broadcast name (stored): '$currentBroadcastName'")
                    Log.d("BLEScanner", "📋 Attendance marked flag: $attendanceMarked")
                    Log.d("BLEScanner", "🔍 Comparison: lastUsed='$lastUsedBroadcastName' vs detected='$trimmedBroadcastName'")
                    Log.d("BLEScanner", "🔍 Are they equal? ${lastUsedBroadcastName == trimmedBroadcastName}")
                    
                    if (trimmedBroadcastName.isNotEmpty()) {
                        // Store the current broadcast name and enable the "Mark Here" button
                        synchronized(markingLock) {
                            // Check if this broadcast name has already been used
                            if (lastUsedBroadcastName != null && lastUsedBroadcastName == trimmedBroadcastName) {
                                Log.d("BLEScanner", "❌ DUPLICATE: Broadcast name '$trimmedBroadcastName' already used for this ping")
                                Log.d("BLEScanner", "   Last used: '$lastUsedBroadcastName'")
                                Log.d("BLEScanner", "   Current: '$trimmedBroadcastName'")
                                // Update UI to show they've already been marked
                                runOnUiThread {
                                    statusText.text = "You've already been marked present for this ping."
                                    statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_orange_dark))
                                    markHereButton.isEnabled = false
                                }
                                return@onScanResult
                            }
                            
                            // This is a NEW broadcast name (different from the last used one)
                            // Reset attendanceMarked to allow marking for this new ping
                            attendanceMarked = false
                            
                            // Store the current broadcast name
                            currentBroadcastName = trimmedBroadcastName
                            foundDevice = true
                            
                            Log.d("BLEScanner", "✅ NEW BROADCAST: '$trimmedBroadcastName'")
                            Log.d("BLEScanner", "   Last used: '$lastUsedBroadcastName'")
                            Log.d("BLEScanner", "   Current: '$trimmedBroadcastName'")
                            Log.d("BLEScanner", "   ClassCode: '$classCode', Section: '$section'")
                            Log.d("BLEScanner", "   Resetting attendanceMarked to allow marking")
                            
                            // Enable the "Mark Here" button only if this is a new broadcast
                            runOnUiThread {
                                // Double-check: make sure we're not enabling for an already-used broadcast
                                synchronized(markingLock) {
                                    if (lastUsedBroadcastName != null && lastUsedBroadcastName == trimmedBroadcastName) {
                                        Log.d("BLEScanner", "❌ Double-check failed: Broadcast already used - keeping button disabled")
                                        statusText.text = "You've already been marked present for this ping."
                                        statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_orange_dark))
                                        markHereButton.isEnabled = false
                                    } else {
                                        Log.d("BLEScanner", "✅ Enabling 'Mark Here' button for broadcast: '$trimmedBroadcastName'")
                                        statusText.text = "Teacher broadcast found! Press 'Mark Here' to mark attendance."
                                        statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_green_dark))
                                        markHereButton.isEnabled = true
                                    }
                                }
                            }
                        }
                    } else {
                        Log.w("BLEScanner", "Found matching prefix but broadcast name is empty")
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

        // Get data from intent - userId is now in JWT token, not passed via Intent
        classId = intent.getStringExtra("CLASS_ID")?.trim()
        classCode = intent.getStringExtra("CLASS_CODE")?.trim()
        section = intent.getStringExtra("SECTION")?.trim()
        className = intent.getStringExtra("CLASS_NAME")?.trim()
        expectedDeviceName = intent.getStringExtra("DEVICE_NAME")?.trim()
        
        // Load persisted lastUsedBroadcastName from SharedPreferences
        loadLastUsedBroadcastName()
        
        // Log received values for debugging
        Log.d("StudentAttendance", "=== ACTIVITY CREATED (STUDENT CLICKED CLASS) ===")
        // userId is now in JWT token - not passed via Intent
        Log.d("StudentAttendance", "classId: '$classId'")
        Log.d("StudentAttendance", "classCode: '$classCode', section: '$section'")
        Log.d("StudentAttendance", "Initial state:")
        Log.d("StudentAttendance", "  - currentBroadcastName: '$currentBroadcastName'")
        Log.d("StudentAttendance", "  - lastUsedBroadcastName: '$lastUsedBroadcastName' (loaded from SharedPreferences)")
        Log.d("StudentAttendance", "  - attendanceMarked: $attendanceMarked")
        Log.d("StudentAttendance", "Expected broadcast format: '${classCode}${section}-{SECRET}'")
        Log.d("StudentAttendance", "============================")

        // Initialize views
        classInfoText = findViewById(R.id.class_info_text)
        markHereButton = findViewById(R.id.mark_here_button)
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

        // Set up "Mark Here" button click listener
        markHereButton.setOnClickListener {
            Log.d("MarkHere", "═══════════════════════════════════════════════════")
            Log.d("MarkHere", "🔘 MARK HERE BUTTON PRESSED")
            Log.d("MarkHere", "═══════════════════════════════════════════════════")
            
            val currentBroadcast = currentBroadcastName
            Log.d("MarkHere", "📡 Current broadcast name from variable: '$currentBroadcast'")
            
            if (currentBroadcast == null) {
                Toast.makeText(this, "No broadcast detected. Please wait for teacher broadcast.", Toast.LENGTH_SHORT).show()
                Log.w("MarkHere", "❌ Button pressed but currentBroadcastName is null")
                return@setOnClickListener
            }
            
            val trimmedBroadcastName = currentBroadcast.trim()
            
            Log.d("MarkHere", "═══════════════════════════════════════════════════")
            Log.d("MarkHere", "🔍 CHECKING BROADCAST NAME FOR DUPLICATES")
            Log.d("MarkHere", "═══════════════════════════════════════════════════")
            Log.d("MarkHere", "📋 Current broadcast name: '$trimmedBroadcastName'")
            Log.d("MarkHere", "📋 Last used broadcast name: '$lastUsedBroadcastName'")
            Log.d("MarkHere", "📋 Attendance marked flag: $attendanceMarked")
            Log.d("MarkHere", "🔍 Comparison: lastUsed='$lastUsedBroadcastName' vs current='$trimmedBroadcastName'")
            Log.d("MarkHere", "🔍 Are they equal? ${lastUsedBroadcastName == trimmedBroadcastName}")
            
            // Check if this broadcast name has already been used
            synchronized(markingLock) {
                // CRITICAL CHECK: Compare current broadcast to last used broadcast
                // If they match, this is a duplicate and should be blocked
                if (lastUsedBroadcastName != null && lastUsedBroadcastName == trimmedBroadcastName) {
                    Log.d("MarkHere", "═══════════════════════════════════════════════════")
                    Log.d("MarkHere", "❌ DUPLICATE DETECTED - BLOCKING MARK")
                    Log.d("MarkHere", "═══════════════════════════════════════════════════")
                    Log.d("MarkHere", "   Last used: '$lastUsedBroadcastName'")
                    Log.d("MarkHere", "   Current: '$trimmedBroadcastName'")
                    Log.d("MarkHere", "   Match: ${lastUsedBroadcastName == trimmedBroadcastName}")
                    Log.d("MarkHere", "   ⚠️ Student already marked for this ping!")
                    Toast.makeText(this, "You've already been marked present for this ping.", Toast.LENGTH_LONG).show()
                    statusText.text = "You've already been marked present for this ping."
                    statusText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                    markHereButton.isEnabled = false
                    return@setOnClickListener
                }
                
                Log.d("MarkHere", "═══════════════════════════════════════════════════")
                Log.d("MarkHere", "✅ Broadcast name is NEW - ALLOWING MARK")
                Log.d("MarkHere", "═══════════════════════════════════════════════════")
                Log.d("MarkHere", "   Last used: '$lastUsedBroadcastName'")
                Log.d("MarkHere", "   Current: '$trimmedBroadcastName'")
                Log.d("MarkHere", "   Match: ${lastUsedBroadcastName == trimmedBroadcastName}")
                
                // Mark this broadcast name as used and set flag BEFORE making API call
                // This prevents race conditions if the button is pressed multiple times quickly
                val oldLastUsed = lastUsedBroadcastName
                lastUsedBroadcastName = trimmedBroadcastName
                attendanceMarked = true
                // Persist to SharedPreferences so it survives activity recreation
                saveLastUsedBroadcastName(trimmedBroadcastName)
                Log.d("MarkHere", "✅ Updated state:")
                Log.d("MarkHere", "   Old lastUsedBroadcastName: '$oldLastUsed'")
                Log.d("MarkHere", "   New lastUsedBroadcastName: '$lastUsedBroadcastName'")
                Log.d("MarkHere", "   attendanceMarked: $attendanceMarked")
                Log.d("MarkHere", "   💾 Persisted to SharedPreferences")
            }
            
            // Extract secret from broadcast name for API call
            // Format: {ClassCode}{Section}-{SecretHash}
            val secret = trimmedBroadcastName.substringAfter("-", "").trim()
            Log.d("MarkHere", "🔑 Extracted secret from '$trimmedBroadcastName': '$secret'")
            Log.d("MarkHere", "📤 Proceeding to mark attendance with secret...")
            
            // Mark attendance
            markPresentAutomatically(secret, trimmedBroadcastName)
        }

        viewHistoryButton.setOnClickListener {
            // Navigate to history page
            val intent = Intent(this, StudentAttendanceHistoryActivity::class.java).apply {
                // userId is now in JWT token - not passed via Intent
                putExtra("CLASS_ID", classId)
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
        currentBroadcastName = null // Reset current broadcast name when starting new scan
        isScanning = true
        markHereButton.isEnabled = false // Disable button until broadcast is found
        
        Log.d("BLEScanner", "=== STARTING SCAN ===")
        Log.d("BLEScanner", "Current broadcast name: '$currentBroadcastName'")
        Log.d("BLEScanner", "Last used broadcast name: '$lastUsedBroadcastName'")
        Log.d("BLEScanner", "Looking for broadcast matching: '${classCode}${section}-*'")
        
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
                            markHereButton.isEnabled = false
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

    private fun markPresentAutomatically(secret: String, broadcastName: String) {
        val currentClassId = classId

        if (currentClassId == null) {
            Log.e("MarkAttendance", "Missing info - classId: $currentClassId")
            // Reset flags if we can't proceed
            synchronized(markingLock) {
                attendanceMarked = false
                if (lastUsedBroadcastName == broadcastName) {
                    lastUsedBroadcastName = null
                    clearLastUsedBroadcastName()
                }
            }
            return
        }

        // Extract userId from JWT token only
        val currentUserId = JwtTokenManager.getUserIdFromToken(this)
        if (currentUserId == null) {
            Log.e("MarkAttendance", "Failed to extract userId from JWT token")
            // Reset flags if we can't proceed
            synchronized(markingLock) {
                attendanceMarked = false
                if (lastUsedBroadcastName == broadcastName) {
                    lastUsedBroadcastName = null
                    clearLastUsedBroadcastName()
                }
            }
            return
        }

        // Broadcast name is already stored in lastUsedBroadcastName in button click handler
        // The flag is already set, so we can proceed with the API call
        val trimmedSecret = secret.trim()
        
        stopScanning()
        
        // Disable the button and update status
        markHereButton.isEnabled = false
        statusText.text = "Marking attendance..."
        statusText.setTextColor(ContextCompat.getColor(this, R.color.white))

        // Trim and validate before sending
        val trimmedUserId = currentUserId.trim()
        val trimmedClassId = currentClassId!!.trim()
        // trimmedSecret already declared above

        // Log the values being sent for debugging (with exact values)
        Log.d("MarkAttendance", "=== MARK ATTENDANCE REQUEST ===")
        Log.d("MarkAttendance", "objectId (before trim): '$currentClassId' (length: ${currentClassId?.length})")
        Log.d("MarkAttendance", "objectId (after trim): '$trimmedClassId' (length: ${trimmedClassId.length})")
        Log.d("MarkAttendance", "secret: '$trimmedSecret' (length: ${trimmedSecret.length})")
        Log.d("MarkAttendance", "classCode: '$classCode', section: '$section'")
        Log.d("MarkAttendance", "===============================")

        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_LONG).show()
            Log.e("MarkAttendance", "Failed to extract userId from JWT token")
            attendanceMarked = false
            markHereButton.isEnabled = currentBroadcastName != null
            return
        }

        val request = MarkAttendanceRequest(
            userId = actualUserId,
            objectId = trimmedClassId,
            secret = trimmedSecret
        )

        val call = RetrofitClient.apiService.markAttendance(request)
        // Store broadcast name in outer scope for use in callbacks
        val finalBroadcastName = broadcastName
        call.enqueue(object : Callback<MarkAttendanceResponse> {
            override fun onResponse(call: Call<MarkAttendanceResponse>, response: Response<MarkAttendanceResponse>) {

                Log.d("MarkAttendance", "Response code: ${response.code()}, isSuccessful: ${response.isSuccessful}")

                if (response.isSuccessful && response.body() != null) {
                    val markResponse = response.body()!!
                    Log.d("MarkAttendance", "Response body - success: ${markResponse.success}, error: '${markResponse.error}'")
                    
                    // Consider success if: success is true OR error is empty
                    val isSuccess = markResponse.success || markResponse.error.isEmpty()
                    
                    if (isSuccess) {
                        // Mark attendance as successful - NEVER reset flags or remove broadcast name
                        // This ensures the broadcast name can never be used again, even if activity is recreated
                        Log.d("MarkAttendance", "Attendance marked successfully - broadcast name '$finalBroadcastName' will remain in used set")
                        statusText.text = "Attendance marked successfully!"
                        statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_green_dark))
                        Toast.makeText(this@StudentAttendanceActivity, "Attendance marked successfully!", Toast.LENGTH_SHORT).show()
                        
                        // Ensure scanning is stopped and won't restart
                        stopScanning()
                        
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
                        
                        // Check if error indicates attendance was already marked
                        val isAlreadyMarkedError = backendError.contains("already", ignoreCase = true) ||
                            backendError.contains("duplicate", ignoreCase = true)
                        
                        // Only reset flags if it's a retryable error AND not already marked
                        // If backend says "already marked", we should keep the secret in used set
                        val isRetryableError = !backendError.contains("not enrolled", ignoreCase = true) && 
                            !backendError.contains("NOT_IN_CLASS", ignoreCase = true) &&
                            !isAlreadyMarkedError
                        
                        if (isRetryableError) {
                                // Reset flag and remove broadcast name from used set so they can try again
                                synchronized(markingLock) {
                                    attendanceMarked = false
                                    if (lastUsedBroadcastName == finalBroadcastName) {
                                        lastUsedBroadcastName = null
                                        clearLastUsedBroadcastName()
                                    }
                                }
                                Log.d("MarkAttendance", "Retryable error - reset flags and removed broadcast name '$finalBroadcastName' from used set")
                            // Re-enable button if broadcast is still available
                            if (currentBroadcastName == finalBroadcastName) {
                                markHereButton.isEnabled = true
                            }
                            // Restart scanning for retryable errors
                            requestPermissionsAndStartScanning()
                        } else {
                            // For non-retryable errors (not enrolled, already marked), keep the flags set
                            // to prevent further attempts with the same broadcast name
                            if (isAlreadyMarkedError) {
                                Log.d("MarkAttendance", "Already marked error - keeping broadcast name '$finalBroadcastName' in used set permanently")
                            } else {
                                Log.d("MarkAttendance", "Non-retryable error - keeping broadcast name '$finalBroadcastName' in used set")
                            }
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
                    
                    // Check if error indicates attendance was already marked
                    val isAlreadyMarkedError = errorMessage.contains("already", ignoreCase = true) ||
                        errorMessage.contains("duplicate", ignoreCase = true)
                    
                    // Only reset flags if it's a retryable error AND not already marked
                    val isRetryableError = !errorMessage.contains("not enrolled", ignoreCase = true) && 
                        !errorMessage.contains("NOT_IN_CLASS", ignoreCase = true) &&
                        !isAlreadyMarkedError
                    
                    if (isRetryableError) {
                        // Reset flag and remove broadcast name from used set so they can try again
                        synchronized(markingLock) {
                            attendanceMarked = false
                            if (lastUsedBroadcastName == finalBroadcastName) {
                                lastUsedBroadcastName = null
                            }
                            Log.d("MarkAttendance", "Retryable error - reset flags and removed broadcast name '$finalBroadcastName' from used set")
                        }
                        // Re-enable button if broadcast is still available
                        if (currentBroadcastName == finalBroadcastName) {
                            markHereButton.isEnabled = true
                        }
                        // Restart scanning for retryable errors
                        scanHandler.postDelayed({
                            if (!attendanceMarked) {
                                requestPermissionsAndStartScanning()
                            }
                        }, 2000)
                    } else {
                        // For non-retryable errors (not enrolled, already marked), keep the flags set
                        // to prevent further attempts with the same broadcast name
                        if (isAlreadyMarkedError) {
                            Log.d("MarkAttendance", "Already marked error - keeping broadcast name '$finalBroadcastName' in used set permanently")
                        } else {
                            Log.d("MarkAttendance", "Non-retryable error - keeping broadcast name '$finalBroadcastName' in used set")
                        }
                    }
                }
            }

            override fun onFailure(call: Call<MarkAttendanceResponse>, t: Throwable) {
                statusText.text = "Network error. Please try again."
                statusText.setTextColor(ContextCompat.getColor(this@StudentAttendanceActivity, android.R.color.holo_red_dark))
                Toast.makeText(this@StudentAttendanceActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("MarkAttendance", "Error marking attendance", t)
                
                // For network errors, we can retry, but we need to be careful
                // Only remove secret if we're sure the request never reached the server
                // However, since we can't know for sure, we'll keep the secret in used set
                // to prevent duplicate marks if the request actually succeeded
                // The backend should handle duplicate prevention anyway
                synchronized(markingLock) {
                    // Keep broadcast name in used set to prevent potential duplicates
                    // If backend received the request, it will prevent duplicate
                    // If backend didn't receive it, student can try again with a new broadcast name (new ping)
                    Log.d("MarkAttendance", "Network error - keeping broadcast name '$finalBroadcastName' in used set to prevent duplicates")
                    // Don't reset attendanceMarked or remove broadcast name - let backend handle duplicates
                    // Student will need to wait for next ping (new broadcast name) to try again
                }
            }
        })
    }

    private fun leaveClass() {
        val currentClassId = classId

        if (currentClassId == null) {
            Toast.makeText(this, "Missing class information", Toast.LENGTH_SHORT).show()
            Log.e("LeaveClass", "Missing info - classId: $currentClassId")
            return
        }

        // Log the values being sent for debugging
        // userId is now in JWT token - not passed via Intent
        Log.d("LeaveClass", "Attempting to leave class - classId: '$currentClassId'")

        leaveClassButton.isEnabled = false
        leaveClassButton.text = "Leaving..."

        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_LONG).show()
            Log.e("LeaveClass", "Failed to extract userId from JWT token")
            leaveClassButton.isEnabled = true
            leaveClassButton.text = "Leave Class"
            return
        }

        val request = LeaveClassRequest(
            userId = actualUserId,
            classId = currentClassId.trim()
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
                        Log.d("LeaveClass", "Successfully left class - setting RESULT_OK and finishing")
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


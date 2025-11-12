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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ActiveClassSessionActivity : ComponentActivity() {

    private val bluetoothManager by lazy { getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager.adapter }
    private val bluetoothLeAdvertiser by lazy { bluetoothAdapter?.bluetoothLeAdvertiser }

    private var isAdvertising = false
    private var classId: String? = null
    private var classCode: String? = null
    private var section: String? = null
    private var originalBluetoothName: String? = null
    private var broadcastSecret: String? = null
    private var broadcastName: String? = null
    private var isPinging = false

    private lateinit var broadcastNameText: TextView
    private lateinit var statusText: TextView
    private lateinit var pingButton: Button
    private lateinit var endClassButton: Button
    private lateinit var studentsRecyclerView: RecyclerView
    private lateinit var noStudentsText: TextView
    private var attendancePollHandler: Handler? = null
    private var attendancePollRunnable: Runnable? = null
    private var isPolling = false

    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            startAdvertising()
        } else {
            Toast.makeText(this, "Bluetooth must be enabled to advertise.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.active_class_session_layout)

        // Get data from intent
        classId = intent.getStringExtra("CLASS_ID")
        // userId is now in JWT token - not passed via Intent
        classCode = intent.getStringExtra("CLASS_CODE")
        section = intent.getStringExtra("SECTION")
        broadcastName = intent.getStringExtra("BROADCAST_NAME")
        broadcastSecret = intent.getStringExtra("BROADCAST_SECRET")

        // Setup views
        // Back button removed - use Android system back button

        val classInfoText: TextView = findViewById(R.id.class_info_text)
        val classInfo = if (classCode != null && section != null) {
            "$classCode-$section"
        } else {
            "Class Session"
        }
        classInfoText.text = classInfo

        broadcastNameText = findViewById(R.id.broadcast_name_text)
        statusText = findViewById(R.id.status_text)
        pingButton = findViewById(R.id.ping_button)
        endClassButton = findViewById(R.id.end_class_button)
        studentsRecyclerView = findViewById(R.id.students_recycler_view)
        noStudentsText = findViewById(R.id.no_students_text)
        
        // Setup RecyclerView
        studentsRecyclerView.layoutManager = LinearLayoutManager(this)

        // Update broadcast name display
        broadcastName?.let {
            broadcastNameText.text = it
        }

        // Ping button
        pingButton.setOnClickListener {
            sendPing()
        }

        // End Class button
        endClassButton.setOnClickListener {
            endClass()
        }

        // Start advertising if we have the broadcast name
        broadcastName?.let {
            startAdvertising()
            // Start polling for attendance records
            startPollingAttendance()
        } ?: run {
            Toast.makeText(this, "Missing broadcast information", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        stopPollingAttendance()
    }

    override fun onResume() {
        super.onResume()
        if (classId != null) {
            startPollingAttendance()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPollingAttendance()
    }

    private fun startPollingAttendance() {
        if (isPolling) {
            return // Already polling
        }
        
        isPolling = true
        // Poll immediately
        fetchCurrentAttendance()
        
        // Set up periodic polling every 3 seconds
        attendancePollHandler = Handler(Looper.getMainLooper())
        attendancePollRunnable = object : Runnable {
            override fun run() {
                fetchCurrentAttendance()
                attendancePollHandler?.postDelayed(this, 3000) // Poll every 3 seconds
            }
        }
        attendancePollHandler?.postDelayed(attendancePollRunnable!!, 3000)
    }

    private fun stopPollingAttendance() {
        isPolling = false
        attendancePollRunnable?.let {
            attendancePollHandler?.removeCallbacks(it)
        }
        attendancePollHandler = null
        attendancePollRunnable = null
    }

    private fun fetchCurrentAttendance() {
        if (classId.isNullOrEmpty()) {
            return
        }

        val request = FetchTeacherRecordsRequest(objectId = classId!!)
        val call = RetrofitClient.apiService.fetchTeacherRecords(request)

        call.enqueue(object : Callback<FetchTeacherRecordsResponse> {
            override fun onResponse(
                call: Call<FetchTeacherRecordsResponse>,
                response: Response<FetchTeacherRecordsResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val recordsResponse = response.body()!!
                    if (recordsResponse.error.isEmpty()) {
                        val records = recordsResponse.records ?: emptyList()
                        
                        // Find the most recent record (current active session)
                        val currentRecord = records.maxByOrNull { record ->
                            parseDate(record.startTime)
                        }
                        
                        if (currentRecord != null && currentRecord.pingsCollected != null) {
                            // Extract students who have marked attendance (pings > 0)
                            val students = currentRecord.pingsCollected
                                .filter { it.value > 0 }
                                .map { (userId, pings) ->
                                    StudentAttendanceItem(userId = userId, pings = pings)
                                }
                                .sortedBy { it.userId } // Sort by userId for consistent display
                            
                            updateStudentsList(students)
                        } else {
                            updateStudentsList(emptyList())
                        }
                    } else {
                        Log.e("AttendancePoll", "Error fetching records: ${recordsResponse.error}")
                    }
                } else {
                    Log.e("AttendancePoll", "Failed to fetch records - HTTP ${response.code()}")
                }
            }

            override fun onFailure(call: Call<FetchTeacherRecordsResponse>, t: Throwable) {
                Log.e("AttendancePoll", "Error fetching attendance records", t)
            }
        })
    }

    private fun updateStudentsList(students: List<StudentAttendanceItem>) {
        runOnUiThread {
            if (students.isNotEmpty()) {
                studentsRecyclerView.adapter = StudentAttendanceAdapter(students)
                studentsRecyclerView.visibility = android.view.View.VISIBLE
                noStudentsText.visibility = android.view.View.GONE
            } else {
                studentsRecyclerView.adapter = null
                studentsRecyclerView.visibility = android.view.View.GONE
                noStudentsText.visibility = android.view.View.VISIBLE
            }
        }
    }

    private fun parseDate(dateString: String): Long {
        // Try multiple date formats
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "EEE MMM dd HH:mm:ss zzz yyyy"
        )
        
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val date = sdf.parse(dateString)
                return date?.time ?: 0L
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        // If all formats fail, return 0
        Log.w("DateParse", "Failed to parse date: $dateString")
        return 0L
    }

    private fun generateSecretHash(): String {
        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this) ?: ""
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().replace("-", "")
        val combined = "$timestamp$uuid${classCode}${section}$actualUserId"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(combined.toByteArray())
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }
        return hexString.substring(0, 8)
    }

    private fun sendPing() {
        if (classId.isNullOrEmpty() || isPinging) {
            return
        }

        isPinging = true
        pingButton.isEnabled = false
        pingButton.text = "Pinging..."

        // First remove the secret, then set a new one (which increments totalPings)
        removeSecretThenPing()
    }

    private fun removeSecretThenPing() {
        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_SHORT).show()
            Log.e("ActiveSession", "Failed to extract userId from JWT token")
            isPinging = false
            return
        }

        val request = RemoveSecretRequest(
            userId = actualUserId,
            objectId = classId!!
        )

        val call = RetrofitClient.apiService.removeSecret(request)
        call.enqueue(object : Callback<RemoveSecretResponse> {
            override fun onResponse(call: Call<RemoveSecretResponse>, response: Response<RemoveSecretResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val removeResponse = response.body()!!
                    if (removeResponse.error.isEmpty()) {
                        // Now set a new secret (which increments totalPings)
                        setNewSecretForPing()
                    } else {
                        isPinging = false
                        pingButton.isEnabled = true
                        pingButton.text = "Ping"
                        Toast.makeText(this@ActiveClassSessionActivity, "Error: ${removeResponse.error}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    isPinging = false
                    pingButton.isEnabled = true
                    pingButton.text = "Ping"
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = try {
                        if (errorBody != null && errorBody.contains("\"error\"")) {
                            val jsonObject = org.json.JSONObject(errorBody)
                            jsonObject.optString("error", errorBody)
                        } else {
                            errorBody ?: "Failed to ping (HTTP ${response.code()})"
                        }
                    } catch (e: Exception) {
                        errorBody ?: "Failed to ping (HTTP ${response.code()})"
                    }
                    Toast.makeText(this@ActiveClassSessionActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<RemoveSecretResponse>, t: Throwable) {
                isPinging = false
                pingButton.isEnabled = true
                pingButton.text = "Ping"
                Toast.makeText(this@ActiveClassSessionActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("Ping", "Error removing secret", t)
            }
        })
    }

    private fun setNewSecretForPing() {
        // Generate a new secret
        val newSecret = generateSecretHash()
        val formattedBroadcastName = "${classCode}${section}-$newSecret"

        broadcastSecret = newSecret
        broadcastName = formattedBroadcastName

        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_SHORT).show()
            Log.e("ActiveSession", "Failed to extract userId from JWT token")
            isPinging = false
            return
        }

        val request = NewSecretRequest(
            userId = actualUserId,
            objectId = classId!!,
            secret = newSecret
        )

        val call = RetrofitClient.apiService.newSecret(request)
        call.enqueue(object : Callback<NewSecretResponse> {
            override fun onResponse(call: Call<NewSecretResponse>, response: Response<NewSecretResponse>) {
                isPinging = false
                pingButton.isEnabled = true
                pingButton.text = "Ping"

                if (response.isSuccessful && response.body() != null) {
                    val secretResponse = response.body()!!
                    if (secretResponse.error.isEmpty()) {
                        // Update broadcast name display
                        broadcastNameText.text = formattedBroadcastName
                        // Update BLE advertising with new name
                        updateAdvertising(formattedBroadcastName)
                        Toast.makeText(this@ActiveClassSessionActivity, "Ping sent", Toast.LENGTH_SHORT).show()
                        // Refresh attendance list after ping
                        fetchCurrentAttendance()
                    } else {
                        Toast.makeText(this@ActiveClassSessionActivity, "Error: ${secretResponse.error}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = errorBody ?: "Failed to ping (HTTP ${response.code()})"
                    Toast.makeText(this@ActiveClassSessionActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<NewSecretResponse>, t: Throwable) {
                isPinging = false
                pingButton.isEnabled = true
                pingButton.text = "Ping"
                Toast.makeText(this@ActiveClassSessionActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                Log.e("Ping", "Error setting new secret", t)
            }
        })
    }

    private fun endClass() {
        stopAdvertising()
        endBroadcastAPI()
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val localBluetoothAdapter = bluetoothAdapter ?: run {
            statusText.text = "Bluetooth adapter not available"
            return
        }

        val localBluetoothLeAdvertiser = bluetoothLeAdvertiser ?: run {
            statusText.text = "BLE advertiser not available"
            return
        }

        if (!localBluetoothAdapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        val name = broadcastName ?: return

        try {
            originalBluetoothName = localBluetoothAdapter.name
            localBluetoothAdapter.name = name
            Log.d("BleAdvertiser", "Device name set to: $name")
        } catch (se: SecurityException) {
            Log.w("BleAdvertiser", "Could not set device name. Continuing with advertising.", se)
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
            statusText.text = errorMessage
            Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateAdvertising(newName: String) {
        // Stop current advertising
        val localBluetoothLeAdvertiser = bluetoothLeAdvertiser ?: return
        if (isAdvertising) {
            try {
                localBluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
            } catch (e: Exception) {
                Log.e("BleAdvertiser", "Error stopping advertising", e)
            }
        }

        // Update device name
        try {
            bluetoothAdapter?.name = newName
            Log.d("BleAdvertiser", "Device name updated to: $newName")
        } catch (se: SecurityException) {
            Log.w("BleAdvertiser", "Could not update device name", se)
        }

        // Restart advertising with new name
        Handler(Looper.getMainLooper()).postDelayed({
            startAdvertising()
        }, 500)
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        val localBluetoothLeAdvertiser = bluetoothLeAdvertiser ?: return
        if (!isAdvertising) return

        try {
            localBluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
            originalBluetoothName?.let { bluetoothAdapter?.name = it }
            isAdvertising = false
            statusText.text = "Status: Not Broadcasting"
        } catch (se: SecurityException) {
            Log.e("BleAdvertiser", "stopAdvertising SecurityException", se)
        }
    }

    private fun endBroadcastAPI() {
        // Extract userId from JWT token only
        val actualUserId = JwtTokenManager.getUserIdFromToken(this)
        if (actualUserId == null) {
            Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_SHORT).show()
            Log.e("ActiveSession", "Failed to extract userId from JWT token")
            return
        }

        val request = EndBroadcastRequest(
            userId = actualUserId,
            objectId = classId!!
        )

        val call = RetrofitClient.apiService.endBroadcast(request)
        call.enqueue(object : Callback<Unit> {
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@ActiveClassSessionActivity, "Class ended", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    Toast.makeText(this@ActiveClassSessionActivity, "Class ended (API error: ${response.code()})", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }

            override fun onFailure(call: Call<Unit>, t: Throwable) {
                Toast.makeText(this@ActiveClassSessionActivity, "Class ended (network error)", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            statusText.text = "Status: Broadcasting"
            Log.d("BleAdvertiser", "Advertising started")
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
            statusText.text = "Status: Error - $errorMessage"
            Log.e("BleAdvertiser", errorMessage)
            Toast.makeText(this@ActiveClassSessionActivity, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    override fun onBackPressed() {
        // Don't allow going back - must end class first
        Toast.makeText(this, "Please end the class before going back", Toast.LENGTH_SHORT).show()
    }
}


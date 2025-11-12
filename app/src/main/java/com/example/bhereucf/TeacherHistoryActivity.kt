package com.example.bhereucf

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class TeacherHistoryActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noRecordsText: TextView
    private var classId: String? = null
    private var classCode: String? = null
    private var section: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.teacher_history_layout)

        classId = intent.getStringExtra("CLASS_ID")
        classCode = intent.getStringExtra("CLASS_CODE")
        section = intent.getStringExtra("SECTION")

        val classInfoText: TextView = findViewById(R.id.class_info_text)
        val classInfo = if (classCode != null && section != null) {
            "$classCode-$section"
        } else {
            "Class History"
        }
        classInfoText.text = classInfo

        recyclerView = findViewById(R.id.records_recycler_view)
        noRecordsText = findViewById(R.id.no_records_text)
        recyclerView.layoutManager = LinearLayoutManager(this)

        if (classId != null) {
            fetchHistory(classId!!)
        } else {
            Toast.makeText(this, "Missing class information", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun fetchHistory(classId: String) {
        val request = FetchTeacherRecordsRequest(objectId = classId)
        val call = RetrofitClient.apiService.fetchTeacherRecords(request)

        call.enqueue(object : Callback<FetchTeacherRecordsResponse> {
            override fun onResponse(
                call: Call<FetchTeacherRecordsResponse>,
                response: Response<FetchTeacherRecordsResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val historyResponse = response.body()!!
                    if (historyResponse.error.isEmpty()) {
                        val records = historyResponse.records ?: emptyList()
                        // Sort records by startTime (newest first)
                        val sortedRecords = records.sortedByDescending { record ->
                            parseDate(record.startTime)
                        }
                        if (sortedRecords.isNotEmpty()) {
                            recyclerView.adapter = TeacherHistoryAdapter(sortedRecords)
                            noRecordsText.visibility = View.GONE
                        } else {
                            noRecordsText.visibility = View.VISIBLE
                        }
                    } else {
                        Toast.makeText(this@TeacherHistoryActivity, "Error: ${historyResponse.error}", Toast.LENGTH_LONG).show()
                        noRecordsText.visibility = View.VISIBLE
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = try {
                        if (errorBody != null && errorBody.contains("\"error\"")) {
                            val jsonObject = org.json.JSONObject(errorBody)
                            jsonObject.optString("error", errorBody)
                        } else {
                            errorBody ?: "Failed to fetch history (HTTP ${response.code()})"
                        }
                    } catch (e: Exception) {
                        errorBody ?: "Failed to fetch history (HTTP ${response.code()})"
                    }
                    Toast.makeText(this@TeacherHistoryActivity, errorMsg, Toast.LENGTH_LONG).show()
                    noRecordsText.visibility = View.VISIBLE
                }
            }

            override fun onFailure(call: Call<FetchTeacherRecordsResponse>, t: Throwable) {
                Toast.makeText(this@TeacherHistoryActivity, "Network error: ${t.message}", Toast.LENGTH_LONG).show()
                Log.e("TeacherHistory", "Error fetching history", t)
                noRecordsText.visibility = View.VISIBLE
            }
        })
    }

    private fun parseDate(dateString: String): Long {
        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        )
        dateFormats.forEach { it.timeZone = TimeZone.getTimeZone("UTC") }
        
        return try {
            var parsedDate: Date? = null
            for (format in dateFormats) {
                try {
                    parsedDate = format.parse(dateString)
                    break
                } catch (e: Exception) {
                    // Try next format
                }
            }
            parsedDate?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}


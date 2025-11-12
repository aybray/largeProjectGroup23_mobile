package com.example.bhereucf

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StudentClassListActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noClassesText: TextView
    private var isFirstResume = true

    companion object {
        const val REQUEST_CODE_JOIN_CLASS = 2001
        const val REQUEST_CODE_ATTENDANCE = 2002
    }

    private val joinClassLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Refresh the class list after joining a class
            fetchClasses()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_class_list_layout)

        recyclerView = findViewById(R.id.class_list_recycler_view)
        noClassesText = findViewById(R.id.no_classes_text)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // userId, firstName, lastName, and role are now in JWT token - not passed via Intent

        val joinClassButton: Button = findViewById(R.id.join_class_button)
        joinClassButton.setOnClickListener {
            // Get current class list for duplicate checking
            val currentClasses = (recyclerView.adapter as? StudentClassListAdapter)?.getClassList() ?: emptyList()
            val intent = Intent(this, JoinClassActivity::class.java).apply {
                // No need to pass userId - JoinClassActivity will get it from JWT
                // Pass current classes as serializable ArrayList
                putParcelableArrayListExtra("CURRENT_CLASSES", ArrayList(currentClasses))
            }
            joinClassLauncher.launch(intent)
        }

        // Fetch classes using JWT token only
        fetchClasses()
        
        // Handle back button to navigate to login
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Navigate to login (MainActivity) when back is pressed
                // Clear the entire task stack and start MainActivity fresh
                val intent = Intent(this@StudentClassListActivity, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Note: We don't refresh on resume to avoid race conditions
        // Refresh happens via onActivityResult when returning from child activities
        if (isFirstResume) {
            isFirstResume = false
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("StudentClassList", "onActivityResult called - requestCode: $requestCode, resultCode: $resultCode")
        if (requestCode == REQUEST_CODE_ATTENDANCE && resultCode == RESULT_OK) {
            // Refresh the class list when returning from StudentAttendanceActivity after leaving a class
            Log.d("StudentClassList", "Refreshing after leaving class")
            fetchClasses()
        }
    }

    private fun fetchClasses() {
        try {
            // Extract userId from JWT token only
            val actualUserId = JwtTokenManager.getUserIdFromToken(this)
            if (actualUserId == null) {
                Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_SHORT).show()
                Log.e("StudentClassList", "Failed to extract userId from JWT token")
                noClassesText.visibility = View.VISIBLE
                return
            }
            Log.d("StudentClassList", "Fetching classes for userId: $actualUserId")
            val request = FetchClassesRequest(actualUserId)
            val call = RetrofitClient.apiService.fetchClasses(request)

            call.enqueue(object : Callback<FetchClassesResponse> {
                override fun onResponse(call: Call<FetchClassesResponse>, response: Response<FetchClassesResponse>) {
                    try {
                        if (response.isSuccessful && response.body() != null) {
                            val classes = response.body()!!.classes
                            Log.d("StudentClassList", "Received ${classes.size} classes")
                            if (classes.isNotEmpty()) {
                                recyclerView.adapter = StudentClassListAdapter(classes, actualUserId, this@StudentClassListActivity)
                                noClassesText.visibility = View.GONE
                            } else {
                                recyclerView.adapter = null // Clear adapter
                                noClassesText.visibility = View.VISIBLE
                            }
                        } else {
                            Log.e("StudentClassList", "Failed to fetch classes - HTTP ${response.code()}")
                            Toast.makeText(this@StudentClassListActivity, "Failed to fetch classes", Toast.LENGTH_SHORT).show()
                            noClassesText.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        Log.e("StudentClassList", "Error displaying classes", e)
                        Toast.makeText(this@StudentClassListActivity, "Error displaying classes: ${e.message}", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                }

                override fun onFailure(call: Call<FetchClassesResponse>, t: Throwable) {
                    Log.e("StudentClassList", "Network error fetching classes", t)
                    Toast.makeText(this@StudentClassListActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                    noClassesText.visibility = View.VISIBLE
                    t.printStackTrace()
                }
            })
        } catch (e: Exception) {
            Log.e("StudentClassList", "Error fetching classes", e)
            Toast.makeText(this, "Error fetching classes: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}


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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeacherClassListActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noClassesText: TextView
    
    companion object {
        const val REQUEST_CODE_ADD_CLASS = 1001
        const val REQUEST_CODE_CLASS_DETAIL = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.teacher_class_list_layout)

        try {
            recyclerView = findViewById(R.id.class_list_recycler_view)
            noClassesText = findViewById(R.id.no_classes_text)
            recyclerView.layoutManager = LinearLayoutManager(this)

            // userId, firstName, lastName, and role are now in JWT token - not passed via Intent

            val addClassButton: Button = findViewById(R.id.add_class_button)
            addClassButton.setOnClickListener {
                // No need to pass userId - AddClassActivity will get it from JWT
                val intent = Intent(this, AddClassActivity::class.java)
                startActivityForResult(intent, REQUEST_CODE_ADD_CLASS)
            }

            // Fetch classes using JWT token only
            fetchClasses()
            
            // Handle back button to navigate to login
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Navigate to login (MainActivity) when back is pressed
                    // Clear the entire task stack and start MainActivity fresh
                    val intent = Intent(this@TeacherClassListActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)
                    finish()
                }
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading page: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    private fun fetchClasses() {
        try {
            // Extract userId from JWT token only
            val actualUserId = JwtTokenManager.getUserIdFromToken(this)
            if (actualUserId == null) {
                Toast.makeText(this, "Error: Unable to get user ID from token", Toast.LENGTH_SHORT).show()
                Log.e("TeacherClassList", "Failed to extract userId from JWT token")
                noClassesText.visibility = View.VISIBLE
                return
            }
            Log.d("TeacherClassList", "Fetching classes for userId: $actualUserId")
            val request = FetchClassesRequest(actualUserId)
            val call = RetrofitClient.apiService.fetchClasses(request)

            call.enqueue(object : Callback<FetchClassesResponse> {
                override fun onResponse(call: Call<FetchClassesResponse>, response: Response<FetchClassesResponse>) {
                    try {
                        if (response.isSuccessful && response.body() != null) {
                            val classes = response.body()!!.classes
                            Log.d("TeacherClassList", "Received ${classes.size} classes")
                            if (classes.isNotEmpty()) {
                                recyclerView.adapter = ClassListAdapter(classes, actualUserId, this@TeacherClassListActivity)
                                noClassesText.visibility = View.GONE
                            } else {
                                recyclerView.adapter = null // Clear adapter
                                noClassesText.visibility = View.VISIBLE
                            }
                        } else {
                            Log.e("TeacherClassList", "Failed to fetch classes - HTTP ${response.code()}")
                            Toast.makeText(this@TeacherClassListActivity, "Failed to fetch classes", Toast.LENGTH_SHORT).show()
                            noClassesText.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        Log.e("TeacherClassList", "Error displaying classes", e)
                        Toast.makeText(this@TeacherClassListActivity, "Error displaying classes: ${e.message}", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                }

                override fun onFailure(call: Call<FetchClassesResponse>, t: Throwable) {
                    Log.e("TeacherClassList", "Network error fetching classes", t)
                    Toast.makeText(this@TeacherClassListActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                    noClassesText.visibility = View.VISIBLE
                    t.printStackTrace()
                }
            })
        } catch (e: Exception) {
            Log.e("TeacherClassList", "Error fetching classes", e)
            Toast.makeText(this, "Error fetching classes: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // Note: We don't refresh on resume to avoid race conditions
        // Refresh happens via onActivityResult when returning from child activities
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d("TeacherClassList", "onActivityResult called - requestCode: $requestCode, resultCode: $resultCode")
        if (requestCode == REQUEST_CODE_ADD_CLASS && resultCode == RESULT_OK) {
            // Refresh the class list when returning from AddClassActivity
            Log.d("TeacherClassList", "Refreshing after adding class")
            fetchClasses()
        } else if (requestCode == REQUEST_CODE_CLASS_DETAIL && resultCode == RESULT_OK) {
            // Refresh the class list when returning from TeacherClassDetailActivity (e.g., after deleting a class)
            Log.d("TeacherClassList", "Refreshing after class detail (delete)")
            fetchClasses()
        }
    }
}


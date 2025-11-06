package com.example.bhereucf

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
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
    private var userId: String? = null
    private var isFirstResume = true

    private val joinClassLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            // Refresh the class list after joining a class
            val currentUserId = userId
            if (currentUserId != null) {
                fetchClasses(currentUserId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.student_class_list_layout)

        recyclerView = findViewById(R.id.class_list_recycler_view)
        noClassesText = findViewById(R.id.no_classes_text)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val backButton: ImageView = findViewById(R.id.back_button)
        backButton.setOnClickListener {
            finish()
        }

        userId = intent.getStringExtra("USER_ID")
        val firstName = intent.getStringExtra("FIRST_NAME")
        val lastName = intent.getStringExtra("LAST_NAME")

        val joinClassButton: Button = findViewById(R.id.join_class_button)
        joinClassButton.setOnClickListener {
            val currentUserId = userId
            if (currentUserId != null) {
                // Get current class list for duplicate checking
                val currentClasses = (recyclerView.adapter as? StudentClassListAdapter)?.getClassList() ?: emptyList()
                val intent = Intent(this, JoinClassActivity::class.java).apply {
                    putExtra("USER_ID", currentUserId)
                    // Pass current classes as serializable ArrayList
                    putParcelableArrayListExtra("CURRENT_CLASSES", ArrayList(currentClasses))
                }
                joinClassLauncher.launch(intent)
            } else {
                Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            }
        }

        val currentUserId = userId
        if (currentUserId != null && currentUserId.isNotEmpty()) {
            fetchClasses(currentUserId)
        } else {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh class list when returning to this activity (e.g., after leaving a class)
        // Skip refresh on first resume since onCreate already fetches classes
        if (!isFirstResume) {
            val currentUserId = userId
            if (currentUserId != null && currentUserId.isNotEmpty()) {
                fetchClasses(currentUserId)
            }
        } else {
            isFirstResume = false
        }
    }

    private fun fetchClasses(userId: String) {
        try {
            val request = FetchClassesRequest(userId = userId)
            val call = RetrofitClient.apiService.fetchClasses(request)

            call.enqueue(object : Callback<FetchClassesResponse> {
                override fun onResponse(call: Call<FetchClassesResponse>, response: Response<FetchClassesResponse>) {
                    try {
                        if (response.isSuccessful && response.body() != null) {
                            val classes = response.body()!!.classes
                            if (classes.isNotEmpty()) {
                                recyclerView.adapter = StudentClassListAdapter(classes, userId)
                                noClassesText.visibility = View.GONE
                            } else {
                                noClassesText.visibility = View.VISIBLE
                            }
                        } else {
                            Toast.makeText(this@StudentClassListActivity, "Failed to fetch classes", Toast.LENGTH_SHORT).show()
                            noClassesText.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@StudentClassListActivity, "Error displaying classes: ${e.message}", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                }

                override fun onFailure(call: Call<FetchClassesResponse>, t: Throwable) {
                    Toast.makeText(this@StudentClassListActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
                    noClassesText.visibility = View.VISIBLE
                    t.printStackTrace()
                }
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Error fetching classes: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}


package com.example.bhereucf

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class TeacherClassListActivity : ComponentActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var noClassesText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.teacher_class_list_layout)

        try {
            recyclerView = findViewById(R.id.class_list_recycler_view)
            noClassesText = findViewById(R.id.no_classes_text)
            recyclerView.layoutManager = LinearLayoutManager(this)

            val userId = intent.getStringExtra("USER_ID")
            val firstName = intent.getStringExtra("FIRST_NAME")
            val lastName = intent.getStringExtra("LAST_NAME")

            val addClassButton: ImageButton = findViewById(R.id.add_class_button)
            addClassButton.setOnClickListener {
                // For now, just show a toast - AddClassActivity can be added later if needed
                Toast.makeText(this, "Add class functionality not yet implemented", Toast.LENGTH_SHORT).show()
            }

            if (userId != null && userId.isNotEmpty()) {
                fetchClasses(userId)
            } else {
                Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading page: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
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
                                recyclerView.adapter = ClassListAdapter(classes, userId)
                                noClassesText.visibility = View.GONE
                            } else {
                                noClassesText.visibility = View.VISIBLE
                            }
                        } else {
                            Toast.makeText(this@TeacherClassListActivity, "Failed to fetch classes", Toast.LENGTH_SHORT).show()
                            noClassesText.visibility = View.VISIBLE
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@TeacherClassListActivity, "Error displaying classes: ${e.message}", Toast.LENGTH_SHORT).show()
                        e.printStackTrace()
                    }
                }

                override fun onFailure(call: Call<FetchClassesResponse>, t: Throwable) {
                    Toast.makeText(this@TeacherClassListActivity, "Network error: ${t.message}", Toast.LENGTH_SHORT).show()
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


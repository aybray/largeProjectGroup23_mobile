package com.example.bhereucf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class StudentAttendanceItem(
    val userId: String,
    val pings: Int
)

class StudentAttendanceAdapter(private val students: List<StudentAttendanceItem>) :
    RecyclerView.Adapter<StudentAttendanceAdapter.StudentViewHolder>() {

    class StudentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val studentIdText: TextView = itemView.findViewById(R.id.student_id_text)
        val pingsText: TextView = itemView.findViewById(R.id.pings_text)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StudentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.student_attendance_item_layout, parent, false)
        return StudentViewHolder(view)
    }

    override fun onBindViewHolder(holder: StudentViewHolder, position: Int) {
        val student = students[position]
        holder.studentIdText.text = student.userId
        holder.pingsText.text = "Pings: ${student.pings}"
    }

    override fun getItemCount(): Int = students.size
}

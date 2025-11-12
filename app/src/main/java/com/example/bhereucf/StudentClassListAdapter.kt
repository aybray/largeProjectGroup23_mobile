package com.example.bhereucf

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.recyclerview.widget.RecyclerView

class StudentClassListAdapter(private val classList: List<Class>, private val userId: String?, private val activity: ComponentActivity) : RecyclerView.Adapter<StudentClassListAdapter.ClassViewHolder>() {

    fun getClassList(): List<Class> = classList

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClassViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.class_item_layout, parent, false)
        return ClassViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClassViewHolder, position: Int) {
        val classItem = classList[position]
        holder.className.text = classItem.name
        holder.classDetails.text = "${classItem.classCode} - ${classItem.section} | ${classItem.daysOffered} | ${classItem.startTime} - ${classItem.endTime}"

        holder.itemView.setOnClickListener {
            val intent = Intent(activity, StudentAttendanceActivity::class.java).apply {
                putExtra("CLASS_ID", classItem._id)
                putExtra("CLASS_CODE", classItem.classCode)
                putExtra("SECTION", classItem.section)
                putExtra("USER_ID", userId)
                putExtra("CLASS_NAME", classItem.name)
                putExtra("DEVICE_NAME", classItem.deviceName)
            }
            // Use startActivityForResult to detect when a class is left
            activity.startActivityForResult(intent, StudentClassListActivity.REQUEST_CODE_ATTENDANCE)
        }
    }

    override fun getItemCount() = classList.size

    class ClassViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val className: TextView = itemView.findViewById(R.id.class_name_text)
        val classDetails: TextView = itemView.findViewById(R.id.class_details_text)
    }
}


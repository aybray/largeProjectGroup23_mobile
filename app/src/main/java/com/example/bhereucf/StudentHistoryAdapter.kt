package com.example.bhereucf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class StudentHistoryAdapter(private val records: List<StudentAttendanceRecord>) : RecyclerView.Adapter<StudentHistoryAdapter.RecordViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.student_history_item_layout, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]

        // Parse date - try multiple formats
        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        )
        dateFormats.forEach { it.timeZone = TimeZone.getTimeZone("UTC") }
        
        val date = try {
            var parsedDate: Date? = null
            for (format in dateFormats) {
                try {
                    parsedDate = format.parse(record.startTime)
                    break
                } catch (e: Exception) {
                    // Try next format
                }
            }
            parsedDate ?: Date()
        } catch (e: Exception) {
            Date()
        }

        val dateStr = SimpleDateFormat("MMMM d, yyyy", Locale.US).format(date)
        val timeStr = SimpleDateFormat("h:mm a", Locale.US).format(date)

        holder.dateText.text = dateStr
        holder.timeText.text = timeStr
        holder.attendanceText.text = "Attendance: ${record.studentPings} / ${record.totalPings}"
        
        val percentage = if (record.totalPings > 0) {
            ((record.studentPings.toFloat() / record.totalPings) * 100).toInt()
        } else {
            0
        }
        holder.percentageText.text = "$percentage%"
    }

    override fun getItemCount() = records.size

    class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.date_text)
        val timeText: TextView = itemView.findViewById(R.id.time_text)
        val attendanceText: TextView = itemView.findViewById(R.id.attendance_text)
        val percentageText: TextView = itemView.findViewById(R.id.percentage_text)
    }
}


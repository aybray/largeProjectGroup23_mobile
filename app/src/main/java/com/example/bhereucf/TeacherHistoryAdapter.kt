package com.example.bhereucf

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class TeacherHistoryAdapter(private val records: List<AttendanceRecord>) : RecyclerView.Adapter<TeacherHistoryAdapter.RecordViewHolder>() {

    private val expandedItems = mutableSetOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.teacher_history_item_layout, parent, false)
        return RecordViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordViewHolder, position: Int) {
        val record = records[position]
        val recordId = record._id ?: position.toString()
        val isExpanded = expandedItems.contains(recordId)

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
        holder.totalPingsText.text = record.totalPings.toString()

        // Setup expand/collapse
        holder.expandButton.text = if (isExpanded) "▼ Hide Students" else "▶ Show Students"
        holder.studentListContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE

        holder.expandButton.setOnClickListener {
            if (isExpanded) {
                expandedItems.remove(recordId)
            } else {
                expandedItems.add(recordId)
            }
            notifyItemChanged(position)
        }

        // Populate student list
        if (isExpanded && record.pingsCollected != null) {
            val studentList = record.pingsCollected.entries.sortedByDescending { it.value }
            holder.studentListContainer.removeAllViews()

            studentList.forEach { (studentId, pings) ->
                val percentage = if (record.totalPings > 0) {
                    ((pings.toFloat() / record.totalPings) * 100).toInt()
                } else {
                    0
                }

                val studentView = LayoutInflater.from(holder.itemView.context)
                    .inflate(R.layout.teacher_history_student_item_layout, holder.studentListContainer, false)

                val studentIdText: TextView = studentView.findViewById(R.id.student_id_text)
                val pingsText: TextView = studentView.findViewById(R.id.pings_text)
                val percentageText: TextView = studentView.findViewById(R.id.percentage_text)

                studentIdText.text = studentId
                pingsText.text = pings.toString()
                percentageText.text = "$percentage%"

                holder.studentListContainer.addView(studentView)
            }
        }
    }

    override fun getItemCount() = records.size

    class RecordViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateText: TextView = itemView.findViewById(R.id.date_text)
        val timeText: TextView = itemView.findViewById(R.id.time_text)
        val totalPingsText: TextView = itemView.findViewById(R.id.total_pings_text)
        val expandButton: Button = itemView.findViewById(R.id.expand_button)
        val studentListContainer: ViewGroup = itemView.findViewById(R.id.student_list_container)
    }
}


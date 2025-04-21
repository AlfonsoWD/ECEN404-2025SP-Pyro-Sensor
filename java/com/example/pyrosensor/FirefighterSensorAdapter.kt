package com.example.pyrosensor

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale

class FirefighterSensorAdapter : ListAdapter<Sensor, FirefighterSensorAdapter.SensorViewHolder>(SensorDiffCallback()) {
    private val TAG = "FirefighterSensorAdapter"
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnables = mutableMapOf<String, Runnable>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sensor, parent, false)
        return SensorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        val sensor = getItem(position)
        holder.bind(sensor)
    }

    inner class SensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val sensorName: TextView = view.findViewById(R.id.sensor_name)
        private val sensorStatus: TextView = view.findViewById(R.id.sensor_value)
        private val batteryStatus: TextView = view.findViewById(R.id.sensor_battery)
        private val sensorState: TextView = view.findViewById(R.id.sensor_state)
        private val stateIndicator: View = view.findViewById(R.id.stateIndicator)
        private val alarmTime: TextView = view.findViewById(R.id.alarm_time)
        private var blinkAnimator: ValueAnimator? = null

        fun bind(sensor: Sensor) {
            sensorName.text = sensor.name
            sensorStatus.text = sensor.status
            batteryStatus.text = sensor.battery
            sensorState.text = sensor.state

            // Stop any existing animation
            blinkAnimator?.cancel()

            // Set the color of the state indicator based on the state
            val color = when (sensor.state) {
                "Safe" -> Color.parseColor("#4CAF50") // Green
                "Warning" -> Color.parseColor("#FFC107") // Yellow
                "Alarm" -> Color.parseColor("#F44336") // Red
                else -> Color.parseColor("#4CAF50") // Default to green
            }
            
            // Create a new circle drawable with the appropriate color
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            stateIndicator.background = drawable

            // Start blinking animation for Warning and Alarm states
            if (sensor.state == "Warning" || sensor.state == "Alarm") {
                blinkAnimator = ValueAnimator.ofFloat(1f, 0f, 1f).apply {
                    duration = 1000 // 1 second for a complete cycle (blink on and off)
                    repeatCount = ValueAnimator.INFINITE
                    addUpdateListener { animation ->
                        val alpha = animation.animatedValue as Float
                        stateIndicator.alpha = alpha
                    }
                    start()
                }
            } else {
                stateIndicator.alpha = 1f
            }

            // Handle alarm timer
            if (sensor.state == "Alarm") {
                sensor.alarmStartTime?.let { timeString ->
                    parseFirebaseTime(timeString)?.let { startTime ->
                        startTimer(alarmTime, sensor.name, startTime)
                    }
                }
            } else {
                stopTimer(sensor.name)
                alarmTime.visibility = View.GONE
            }

            // Add elevation animation when state changes
            val elevation = when (sensor.state) {
                "Alarm" -> 8f
                "Warning" -> 4f
                else -> 2f
            }
            ObjectAnimator.ofFloat(itemView, "elevation", elevation).apply {
                duration = 300
                start()
            }
        }
    }

    private fun parseFirebaseTime(timeString: String): Long? {
        return try {
            val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH)
            val date = sdf.parse(timeString)
            date?.time?.div(1000) // Convert milliseconds to seconds
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time: ${e.message}")
            null
        }
    }

    private fun formatElapsedTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format("%d:%02d", minutes, secs)
            else -> String.format("0:%02d", secs)
        }
    }

    private fun startTimer(timerView: TextView, sensorName: String, startTime: Long) {
        // Stop existing timer if any
        stopTimer(sensorName)

        // Create new timer runnable
        val updateTimerRunnable = object : Runnable {
            override fun run() {
                val elapsedTime = System.currentTimeMillis() / 1000 - startTime
                timerView.text = "Time Elapsed Since Fire Detected: ${formatElapsedTime(elapsedTime)}"
                timerView.visibility = View.VISIBLE
                handler.postDelayed(this, 1000) // Update every second
            }
        }

        // Store runnable reference
        timerRunnables[sensorName] = updateTimerRunnable

        // Start timer
        handler.post(updateTimerRunnable)
    }

    private fun stopTimer(sensorName: String) {
        timerRunnables[sensorName]?.let { runnable ->
            handler.removeCallbacks(runnable)
            timerRunnables.remove(sensorName)
        }
    }

    fun cleanup() {
        timerRunnables.keys.toList().forEach { sensorName ->
            stopTimer(sensorName)
        }
    }

    private class SensorDiffCallback : DiffUtil.ItemCallback<Sensor>() {
        override fun areItemsTheSame(oldItem: Sensor, newItem: Sensor): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: Sensor, newItem: Sensor): Boolean {
            return oldItem == newItem
        }
    }
} 
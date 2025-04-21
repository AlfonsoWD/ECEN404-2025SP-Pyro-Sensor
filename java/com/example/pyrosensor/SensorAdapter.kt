package com.example.pyrosensor

import android.animation.ObjectAnimator
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
import com.google.firebase.database.DataSnapshot
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Locale
//TODO: Alarm message sent to Twilio count
//TODO: Implement Alarm Notification System
class SensorAdapter(
    private val context: android.content.Context,
    private val uid: String,
    private val workspaceId: String
) : ListAdapter<DataSnapshot, SensorAdapter.SensorViewHolder>(SensorDiffCallback()) {

    private val TAG = "SensorAdapter"
    private var lastDeactivatedStates = mutableMapOf<String, String>()
    private var lastDeactivationTimes = mutableMapOf<String, Long>()
    private val twilioService = TwilioService()
    private var currentSensors = mutableListOf<DataSnapshot>()
    private val handler = Handler(Looper.getMainLooper())
    private val timerRunnables = mutableMapOf<String, Runnable>()

    inner class SensorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.sensor_name)
        val statusText: TextView = view.findViewById(R.id.sensor_value)
        val lastUpdateText: TextView = view.findViewById(R.id.sensor_battery)
        val stateText: TextView = view.findViewById(R.id.sensor_state)
        val stateIndicator: View = view.findViewById(R.id.stateIndicator)
        private val alarmTime: TextView = view.findViewById(R.id.alarm_time)
        private var blinkAnimator: ValueAnimator? = null

        fun bind(sensor: DataSnapshot) {
            val macAddress = sensor.key ?: return
            val name = sensor.child("Name").child("Sensor").getValue(String::class.java) ?: "Unknown"
            val status = sensor.child("Alarm").child("message").getValue(String::class.java) ?: "Unknown"
            val state = sensor.child("Alarm").child("alarm_status").getValue(String::class.java) ?: "Safe"
            val battery = sensor.child("Battery").child("message").getValue(String::class.java) ?: "Unknown"
            val lastUpdate = sensor.child("Alarm").child("last_update").getValue(String::class.java) ?: "Unknown"
            val lastDeactivated = sensor.child("Alarm").child("last_deactivated").getValue(String::class.java) ?: "Unknown"

            nameText.text = name
            statusText.text = "Status: $status"
            lastUpdateText.text = "Battery: $battery"
            stateText.text = state

            // Stop any existing animation
            blinkAnimator?.cancel()

        // Set the color of the state indicator based on the state
            val color = when (state) {
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
            if (state == "Warning" || state == "Alarm") {
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
            if (state == "Alarm") {
                val alarmTimeString = sensor.child("Alarm").child("alarmTime").getValue(String::class.java)
                if (alarmTimeString != null) {
                    parseFirebaseTime(alarmTimeString)?.let { startTime ->
                        startTimer(alarmTime, macAddress, startTime)
                    }
                } else {
                    alarmTime.visibility = View.VISIBLE
                    alarmTime.text = "Time Elapsed Since Fire Detected: Unknown"
                }
            } else {
                stopTimer(macAddress)
                alarmTime.visibility = View.GONE
            }

            // Set click listener for the entire card
            itemView.setOnClickListener {
                val intent = Intent(context, SensorDetailActivity::class.java).apply {
                    putExtra("macAddress", macAddress)
                    putExtra("uid", uid)
                }
                context.startActivity(intent)
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

    private fun startTimer(timerView: TextView, sensorId: String, startTime: Long) {
        // Stop existing timer if any
        stopTimer(sensorId)

        // Create new timer runnable
        val updateTimerRunnable = object : Runnable {
            override fun run() {
                val elapsedTime = System.currentTimeMillis() / 1000 - startTime
                timerView.text = "Time Elapsed Since Start of Fire: ${formatElapsedTime(elapsedTime)}"
                timerView.visibility = View.VISIBLE
                handler.postDelayed(this, 1000) // Update every second
            }
        }

        // Store runnable reference
        timerRunnables[sensorId] = updateTimerRunnable

        // Start timer
        handler.post(updateTimerRunnable)
    }

    private fun stopTimer(sensorId: String) {
        timerRunnables[sensorId]?.let { runnable ->
            handler.removeCallbacks(runnable)
            timerRunnables.remove(sensorId)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sensor, parent, false)
        return SensorViewHolder(view)
    }

    override fun onBindViewHolder(holder: SensorViewHolder, position: Int) {
        val sensor = getItem(position)
        holder.bind(sensor)
    }

    fun updateSensors(newSensors: List<DataSnapshot>) {
        val currentTime = System.currentTimeMillis()
        Log.d(TAG, "updateSensors called with ${newSensors.size} sensors")

        // Update current sensors list
        currentSensors = newSensors.toMutableList()

        // Update states and track deactivation times
        newSensors.forEach { sensor ->
            val macAddress = sensor.key ?: return@forEach
            val currentState = sensor.child("Alarm").child("alarm_status").getValue(String::class.java) ?: "Safe"
            val lastState = lastDeactivatedStates[macAddress]
            
            Log.d(TAG, "Sensor $macAddress - Current State: $currentState, Last State: $lastState")
            
            // If state changed from Safe to Alarm, send notification
            if (lastState == "Safe" && currentState == "Alarm") {
                val sensorName = sensor.child("Name").child("Sensor").getValue(String::class.java) ?: "Unknown"
                
                Log.d(TAG, "Alarm triggered for sensor: $sensorName")
                Log.d(TAG, "Workspace ID: $workspaceId")
                
                Log.d(TAG, "Attempting to send notification for sensor: $sensorName in workspace: $workspaceId")
                try {
                    twilioService.sendAlarmNotification(workspaceId, sensorName, uid)
                    Log.d(TAG, "Notification sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending notification: ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // If state changed from Alarm/Warning to Safe, record the time
            if ((lastState == "Alarm" || lastState == "Warning") && currentState == "Safe") {
                Log.d(TAG, "Sensor $macAddress deactivated from $lastState to Safe")
                lastDeactivationTimes[macAddress] = currentTime
            }
            lastDeactivatedStates[macAddress] = currentState
        }

        // Sort sensors based on state priority and last deactivation time
        val sortedSensors = newSensors.sortedWith(
            compareByDescending<DataSnapshot> { 
                it.child("Alarm").child("alarm_status").getValue(String::class.java) == "Alarm" 
            }
            .thenByDescending { 
                it.child("Alarm").child("alarm_status").getValue(String::class.java) == "Warning" 
            }
            .thenByDescending { snapshot ->
                val macAddress = snapshot.key ?: ""
                val currentState = snapshot.child("Alarm").child("alarm_status").getValue(String::class.java) ?: "Safe"
                val lastState = lastDeactivatedStates[macAddress]
                // If current state is Safe and last state was Alarm/Warning, keep it at the top
                currentState == "Safe" && (lastState == "Alarm" || lastState == "Warning")
            }
            .thenByDescending { snapshot ->
                val macAddress = snapshot.key ?: ""
                lastDeactivationTimes[macAddress] ?: 0L
            }
        )

        submitList(sortedSensors)
    }

    fun updateSensorName(macAddress: String, newName: String) {
        Log.d(TAG, "Updating sensor name for $macAddress to $newName")
        val updatedSensors = currentSensors.map { sensor ->
            if (sensor.key == macAddress) {
                // Create a new DataSnapshot with updated name
                val updatedSensor = sensor.child("Name").child("Sensor").ref.setValue(newName)
                // Return the original sensor for now, the update will come through the ValueEventListener
                sensor
            } else {
                sensor
            }
        }
        currentSensors = updatedSensors.toMutableList()
    }

    fun cleanup() {
        timerRunnables.keys.toList().forEach { sensorId ->
            stopTimer(sensorId)
        }
    }

    private class SensorDiffCallback : DiffUtil.ItemCallback<DataSnapshot>() {
        override fun areItemsTheSame(oldItem: DataSnapshot, newItem: DataSnapshot): Boolean {
            return oldItem.key == newItem.key
        }

        override fun areContentsTheSame(oldItem: DataSnapshot, newItem: DataSnapshot): Boolean {
            val oldState = oldItem.child("Alarm").child("alarm_status").getValue(String::class.java)
            val newState = newItem.child("Alarm").child("alarm_status").getValue(String::class.java)
            val oldName = oldItem.child("Name").child("Sensor").getValue(String::class.java)
            val newName = newItem.child("Name").child("Sensor").getValue(String::class.java)
            return oldState == newState && oldName == newName
        }
    }
}


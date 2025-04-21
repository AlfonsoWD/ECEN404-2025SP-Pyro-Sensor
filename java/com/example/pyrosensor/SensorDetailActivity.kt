package com.example.pyrosensor

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Locale

class SensorDetailActivity : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    private lateinit var uid: String
    private lateinit var macAddress: String
    private lateinit var sensorName: String
    private lateinit var tvSensorName: TextView
    private lateinit var statusText: TextView
    private lateinit var stateText: TextView
    private lateinit var stateIndicator: View
    private lateinit var batteryText: TextView
    private lateinit var lastUpdateText: TextView
    private lateinit var alarmTimeText: TextView
    private lateinit var btnEditName: ImageButton
    private var blinkAnimator: ValueAnimator? = null
    private var sensorId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_detail)

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Sensor Details"
        }

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()

        // Get data from intent
        uid = intent.getStringExtra("uid") ?: return finish()
        macAddress = intent.getStringExtra("macAddress") ?: return finish()
        sensorName = intent.getStringExtra("sensorName") ?: "Unknown Sensor"

        // Initialize views
        tvSensorName = findViewById(R.id.tvSensorName)
        statusText = findViewById(R.id.statusText)
        stateText = findViewById(R.id.stateText)
        stateIndicator = findViewById(R.id.stateIndicator)
        batteryText = findViewById(R.id.batteryText)
        lastUpdateText = findViewById(R.id.lastUpdateText)
        alarmTimeText = findViewById(R.id.alarm_time_detail)
        btnEditName = findViewById(R.id.btnEditName)
        val deleteButton = findViewById<Button>(R.id.deleteButton)
        val resetButton = findViewById<Button>(R.id.resetButton)

        // Set initial name
        tvSensorName.text = sensorName

        // Set up edit name button
        btnEditName.setOnClickListener {
            showEditNameDialog()
        }

        // Set up delete button
        deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
        
        // Set up reset button
        resetButton.setOnClickListener {
            showResetConfirmationDialog()
        }

        // Load sensor data
        loadSensorData()
    }

    private fun showEditNameDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_name, null)
        val editText = dialogView.findViewById<EditText>(R.id.etNewName)
        editText.setText(sensorName)
        editText.setSelection(editText.length())

        AlertDialog.Builder(this)
            .setTitle("Edit Sensor Name")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateSensorName(newName)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadSensorData() {
        val sensorRef = database.getReference("users").child(uid).child("sensors").child(macAddress)
        sensorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    // Update name if it exists in the database
                    val dbName = snapshot.child("Name").child("Sensor").getValue(String::class.java)
                    if (!dbName.isNullOrEmpty()) {
                        tvSensorName.text = dbName
                        sensorName = dbName
                    }

                    // Update status
                    val status = snapshot.child("Alarm").child("message").getValue(String::class.java) ?: "Unknown"
                    statusText.text = "Status: $status"

                    // Update state
                    val state = snapshot.child("Alarm").child("alarm_status").getValue(String::class.java) ?: "Safe"
                    stateText.text = state

                    // Set state indicator color
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

                    // Update battery status
                    val battery = snapshot.child("Battery").child("message").getValue(String::class.java) ?: "Unknown"
                    batteryText.text = "Battery: $battery"

                    // Update last update time
                    val lastUpdate = snapshot.child("LastUpdate").getValue(String::class.java) ?: "Never"
                    lastUpdateText.text = "Last Update: $lastUpdate"

                    // Handle alarm timer
                    if (state == "Alarm") {
                        val alarmTimeString = snapshot.child("Alarm").child("alarmTime").getValue(String::class.java)
                        if (alarmTimeString != null) {
                            parseFirebaseTime(alarmTimeString)?.let { startTime ->
                                startTimer(startTime)
                            }
                        } else {
                            alarmTimeText.visibility = View.VISIBLE
                            alarmTimeText.text = "Time Elapsed Since Fire Detected: Unknown"
                        }
                    } else {
                        stopTimer()
                        alarmTimeText.visibility = View.GONE
                    }

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
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@SensorDetailActivity, "Error loading sensor data: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun parseFirebaseTime(timeString: String): Long? {
        return try {
            val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.ENGLISH)
            val date = sdf.parse(timeString)
            date?.time?.div(1000) // Convert milliseconds to seconds
        } catch (e: Exception) {
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

    private fun startTimer(startTime: Long) {
        // Stop existing timer if any
        stopTimer()

        // Create new timer runnable
        timerRunnable = object : Runnable {
            override fun run() {
                val elapsedTime = System.currentTimeMillis() / 1000 - startTime
                alarmTimeText.text = "Time Elapsed Since Fire Detected: ${formatElapsedTime(elapsedTime)}"
                alarmTimeText.visibility = View.VISIBLE
                handler.postDelayed(this, 1000) // Update every second
            }
        }

        // Start timer
        timerRunnable?.let { handler.post(it) }
    }

    private fun stopTimer() {
        timerRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            timerRunnable = null
        }
    }

    private fun updateSensorName(newName: String) {
        val sensorRef = database.getReference("users").child(uid).child("sensors").child(macAddress)
        sensorRef.child("Name").child("Sensor").setValue(newName)
            .addOnSuccessListener {
                Toast.makeText(this, "Sensor name updated successfully", Toast.LENGTH_SHORT).show()
                sensorName = newName
                tvSensorName.text = newName
                
                // Update the adapter in SensorsFragment
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (fragment is SensorsFragment) {
                    fragment.updateSensorName(macAddress, newName)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update sensor name: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Sensor")
            .setMessage("Are you sure you want to delete this sensor? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSensor()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSensor() {
        val sensorRef = database.getReference("users").child(uid).child("sensors").child(macAddress)
        // Instead of removing the node, update the Delete_Sensor flag to "Yes"
        sensorRef.child("Delete").child("Delete_Sensor").setValue("Yes")
            .addOnSuccessListener {
                Toast.makeText(this, "Sensor marked for deletion", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete sensor: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Reset Sensor")
            .setMessage("Are you sure you want to reset this sensor? This will clear any active alarms.")
            .setPositiveButton("Reset") { _, _ ->
                resetSensor()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetSensor() {
        val sensorRef = database.getReference("users").child(uid).child("sensors").child(macAddress)
        // Change the Reset_Peripherals flag to "Yes"
        sensorRef.child("Reset").child("Reset_Peripherals").setValue("Yes")
            .addOnSuccessListener {
                Toast.makeText(this, "Sensor reset command sent", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to reset sensor: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any running animations when the activity is destroyed
        blinkAnimator?.cancel()
        // Stop timer
        stopTimer()
    }
} 
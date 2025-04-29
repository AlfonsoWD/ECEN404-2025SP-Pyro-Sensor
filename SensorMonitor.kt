package com.example.pyrosensor

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SensorMonitor(private val uid: String, private val workspaceId: String) {
    private val TAG = "SensorMonitor"
    private val database = FirebaseDatabase.getInstance()
    private val sensors = mutableMapOf<String, Sensor>()

    fun startMonitoring() {
        Log.d(TAG, "Starting sensor monitoring for user: $uid, workspace: $workspaceId")
        
        // Listen for sensor data changes
        database.getReference("users/$uid/sensors")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    Log.d(TAG, "Sensor data changed. Number of sensors: ${snapshot.children.count()}")
                    
                    for (sensorSnapshot in snapshot.children) {
                        val sensorId = sensorSnapshot.key ?: continue
                        val name = sensorSnapshot.child("Name").child("Sensor").getValue(String::class.java) ?: "Unknown"
                        val status = sensorSnapshot.child("Alarm").child("message").getValue(String::class.java) ?: "Unknown"
                        val state = sensorSnapshot.child("Alarm").child("alarm_status").getValue(String::class.java) ?: "Safe"
                        
                        Log.d(TAG, "Processing sensor: $sensorId")
                        Log.d(TAG, "Name: $name, Status: $status, State: $state")
                        
                        // Store sensor info
                        sensors[sensorId] = Sensor(name, status, state)
                        
                        // Check for alarm/warning conditions
                        if (state == "Alarm") {
                            Log.d(TAG, "ALARM detected for sensor: $name")
                            Log.d(TAG, "Sending alarm notification with message: $status")
                            NotificationSender.sendAlarmNotification(name, status)
                        } else if (state == "Warning") {
                            Log.d(TAG, "WARNING detected for sensor: $name")
                            Log.d(TAG, "Sending warning notification with message: $status")
                            NotificationSender.sendWarningNotification(name, status)
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error monitoring sensors: ${error.message}", error.toException())
                }
            })
    }

    data class Sensor(
        val name: String,
        val status: String,
        val state: String
    )
} 
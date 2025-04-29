package com.example.pyrosensor

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class NotificationSender {
    companion object {
        private const val TAG = "NotificationSender"
        private val database = FirebaseDatabase.getInstance()

        fun sendAlarmNotification(sensorName: String, message: String) {
            Log.d(TAG, "Attempting to send alarm notification for sensor: $sensorName")
            updateSensorState(sensorName, message, "Alarm")
        }

        fun sendWarningNotification(sensorName: String, message: String) {
            Log.d(TAG, "Attempting to send warning notification for sensor: $sensorName")
            updateSensorState(sensorName, message, "Warning")
        }

        private fun updateSensorState(sensorName: String, message: String, state: String) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Log.e(TAG, "Cannot update sensor state: No user logged in")
                return
            }

            Log.d(TAG, "Updating sensor state for user: ${currentUser.uid}")
            
            // Find the sensor with the matching name
            database.getReference("users/${currentUser.uid}/sensors")
                .get()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        for (sensorSnapshot in task.result?.children ?: return@addOnCompleteListener) {
                            val sensorData = sensorSnapshot.getValue(SensorData::class.java)
                            if (sensorData?.name == sensorName) {
                                // Update the sensor state
                                val updates = mapOf(
                                    "Alarm/alarm_status" to state,
                                    "Alarm/message" to message
                                )
                                
                                sensorSnapshot.ref.updateChildren(updates)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "Sensor state updated successfully")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Failed to update sensor state: ${e.message}", e)
                                    }
                                break
                            }
                        }
                    } else {
                        Log.e(TAG, "Failed to get sensors: ${task.exception?.message}", task.exception)
                    }
                }
        }

        data class SensorData(
            val name: String? = null
        )
    }
} 
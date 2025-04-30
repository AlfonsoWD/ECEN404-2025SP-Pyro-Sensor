package com.example.pyrosensor

import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class TwilioService {
    private val TAG = "TwilioService"
    private val database = FirebaseDatabase.getInstance().reference
    private val client = OkHttpClient()
    private val accountSid = "Enter Own account"
    private val authToken = "Enter own auth Token"
    private val fromNumber = "Enter own Phone"
    private val baseUrl = "https://api.twilio.com/2010-04-01/Accounts/$accountSid/Messages.json"
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun sendAlarmNotification(workspaceId: String, sensorName: String, userId: String) {
        Log.d(TAG, "Starting alarm notification process for workspace: $workspaceId, sensor: $sensorName")
        
        // Get the workspace details from the user's workspaces
        database.child("users").child(userId).child("workspaces").child(workspaceId)
            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    Log.d(TAG, "Received workspace data")
                    if (snapshot.exists()) {
                        // Get workspace name and type
                        val workspaceName = snapshot.child("name").getValue(String::class.java) ?: "Unknown Location"
                        val workspaceType = snapshot.child("type").getValue(String::class.java) ?: ""
                        
                        // Get address components
                        val addressSnapshot = snapshot.child("address")
                        val street = addressSnapshot.child("street").getValue(String::class.java) ?: ""
                        val addressLine2 = addressSnapshot.child("addressLine2").getValue(String::class.java) ?: ""
                        val city = addressSnapshot.child("city").getValue(String::class.java) ?: ""
                        val state = addressSnapshot.child("state").getValue(String::class.java) ?: ""
                        val zipcode = addressSnapshot.child("zipcode").getValue(String::class.java) ?: ""
                        
                        // Format the full address
                        val fullAddress = buildString {
                            append(street)
                            if (addressLine2.isNotEmpty()) append(", $addressLine2")
                            append(", $city, $state $zipcode")
                        }
                        
                        Log.d(TAG, "Workspace name: $workspaceName")
                        Log.d(TAG, "Workspace type: $workspaceType")
                        Log.d(TAG, "Formatted address: $fullAddress")

                        // Get the access code from the user's node
                        database.child("users").child(userId).child("access_code")
                            .addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
                                override fun onDataChange(accessCodeSnapshot: com.google.firebase.database.DataSnapshot) {
                                    val accessCode = accessCodeSnapshot.getValue(String::class.java) ?: "Unknown Code"
                                    Log.d(TAG, "Retrieved access code: $accessCode")

                                    // For testing, use a hardcoded phone number
                                    val firstResponderPhone = "+18777804236"

                                    if (firstResponderPhone.isNotEmpty()) {
                                        val messageBody = "ALARM TRIGGERED!\n" +
                                                "Location: $workspaceName\n" +
                                                "Address: $fullAddress\n" +
                                                "Sensor: $sensorName\n" +
                                                "Access Code: $accessCode"

                                        Log.d(TAG, "Attempting to send SMS to: $firstResponderPhone")
                                        Log.d(TAG, "Message body: $messageBody")

                                        // Launch coroutine in IO dispatcher for network operation
                                        coroutineScope.launch {
                                            try {
                                                val formBody = FormBody.Builder()
                                                    .add("To", firstResponderPhone)
                                                    .add("From", fromNumber)
                                                    .add("Body", messageBody)
                                                    .build()

                                                val request = Request.Builder()
                                                    .url(baseUrl)
                                                    .addHeader("Authorization", Credentials.basic(accountSid, authToken))
                                                    .post(formBody)
                                                    .build()

                                                Log.d(TAG, "Making request to Twilio API...")
                                                client.newCall(request).execute().use { response ->
                                                    val responseBody = response.body?.string()
                                                    Log.d(TAG, "Response code: ${response.code}")
                                                    Log.d(TAG, "Response body: $responseBody")
                                                    
                                                    if (!response.isSuccessful) {
                                                        Log.e(TAG, "Failed to send SMS: ${response.code} - $responseBody")
                                                    } else {
                                                        Log.d(TAG, "SMS sent successfully")
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                Log.e(TAG, "Exception while sending SMS: ${e.message}")
                                                Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
                                                e.printStackTrace()
                                            }
                                        }
                                    } else {
                                        Log.e(TAG, "No first responder phone number available")
                                    }
                                }

                                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                                    Log.e(TAG, "Failed to get access code: ${error.message}")
                                }
                            })
                    } else {
                        Log.e(TAG, "Workspace data not found for ID: $workspaceId under user: $userId")
                    }
                }

                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                    Log.e(TAG, "Failed to get workspace details: ${error.message}")
                }
            })
    }
} 

package com.example.pyrosensor

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.security.SecureRandom

class AccessCodeManager {
    private val TAG = "AccessCodeManager"
    private val db = FirebaseDatabase.getInstance().reference
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    // Function to generate a random 6-character access code
    private fun generateAccessCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = SecureRandom()
        val code = StringBuilder()

        for (i in 0 until 6) {
            val randomIndex = random.nextInt(chars.length)
            code.append(chars[randomIndex])
        }

        return code.toString()
    }

    // Function to check if an access code already exists
    private fun checkAccessCodeExists(accessCode: String, completion: (Boolean) -> Unit) {
        db.child("userCodes").child(accessCode).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                completion(snapshot.exists())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to check access code existence: ${error.message}")
                completion(true) // Assume code exists on error to be safe
            }
        })
    }

    // Function to generate a unique access code
    private fun generateUniqueAccessCode(attempts: Int = 0, maxAttempts: Int = 10, completion: (String?) -> Unit) {
        if (attempts >= maxAttempts) {
            Log.e(TAG, "Failed to generate unique access code after $maxAttempts attempts")
            completion(null)
            return
        }

        val accessCode = generateAccessCode()
        checkAccessCodeExists(accessCode) { exists ->
            if (!exists) {
                completion(accessCode)
            } else {
                // If code exists, try again
                generateUniqueAccessCode(attempts + 1, maxAttempts, completion)
            }
        }
    }

    // Function to generate and store an access code in Realtime Database
    fun generateAndStoreAccessCode(userId: String, completion: (Boolean, String?) -> Unit) {
        // Check if user is authenticated
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated")
            completion(false, null)
            return
        }

        // Verify the userId matches the authenticated user
        if (currentUser.uid != userId) {
            Log.e(TAG, "Provided userId does not match authenticated user")
            completion(false, null)
            return
        }

        // Generate a unique access code
        generateUniqueAccessCode { accessCode ->
            if (accessCode == null) {
                completion(false, null)
                return@generateUniqueAccessCode
            }

            val userRef = db.child("users").child(userId)
            val userCodesRef = db.child("userCodes").child(accessCode)
            
            // First get existing data
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val existingData = if (snapshot.exists()) {
                        snapshot.value as? Map<String, Any> ?: mapOf()
                    } else {
                        mapOf()
                    }
                    
                    // Merge existing data with new access code data
                    val updatedData = existingData + mapOf(
                        "access_code" to accessCode
                    )
                    
                    // Update both locations in the database
                    val updates = mapOf(
                        "users/$userId" to updatedData,
                        "userCodes/$accessCode/uid" to userId
                    )
                    
                    // Update all locations at once
                    db.updateChildren(updates)
                        .addOnSuccessListener {
                            completion(true, accessCode)
                        }
                        .addOnFailureListener { exception ->
                            Log.e(TAG, "Failed to store access code: ${exception.message}")
                            completion(false, null)
                        }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to read existing data: ${error.message}")
                    completion(false, null)
                }
            })
        }
    }

    // Function to verify an access code
    fun verifyAccessCode(accessCode: String, completion: (Boolean) -> Unit) {
        // Check if user is authenticated
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(TAG, "User is not authenticated")
            completion(false)
            return
        }

        // Get the user's data
        val userRef = db.child("users").child(currentUser.uid)
        
        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val storedCode = snapshot.child("access_code").getValue(String::class.java)
                    completion(storedCode == accessCode)
                } else {
                    completion(false)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to verify access code: ${error.message}")
                completion(false)
            }
        })
    }
}

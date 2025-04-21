package com.example.pyrosensor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class WorkspaceDisplayActivity : AppCompatActivity() {
    private val TAG = "WorkspaceDisplayActivity"
    private lateinit var database: FirebaseDatabase
    private lateinit var tvUserName: TextView
    private lateinit var btnUserInfo: Button
    private lateinit var btnSensorDisplay: Button
    private lateinit var btnLogout: Button
    private var uid: String? = null
    private var workspaceId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace_display)

        Log.d(TAG, "onCreate: Starting WorkspaceDisplayActivity")

        // Initialize Firebase
        database = FirebaseDatabase.getInstance()

        // Get user ID and workspace ID from intent
        uid = intent.getStringExtra("uid")
        workspaceId = intent.getStringExtra("workspaceId")

        Log.d(TAG, "Received data - UID: $uid, WorkspaceID: $workspaceId")

        if (uid == null) {
            Log.e(TAG, "Error: User ID not found in intent")
            Toast.makeText(this, "Error: User ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (workspaceId == null) {
            Log.e(TAG, "Error: Workspace ID not found in intent")
            Toast.makeText(this, "Error: Workspace ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        tvUserName = findViewById(R.id.tvWorkspaceName)
        btnUserInfo = findViewById(R.id.btnUserInfo)
        btnSensorDisplay = findViewById(R.id.btnSensorDisplay)
        btnLogout = findViewById(R.id.btnLogout)

        Log.d(TAG, "Views initialized successfully")

        // Load user's name
        loadUserName(uid!!)

        // Set up button click listeners
        btnUserInfo.setOnClickListener {
            Log.d(TAG, "User Info button clicked")
            Log.d(TAG, "Starting UserInfoActivity with UID: $uid, WorkspaceID: $workspaceId")
            val intent = Intent(this, UserInfoActivity::class.java)
            intent.putExtra("uid", uid)
            intent.putExtra("workspaceId", workspaceId)
            startActivity(intent)
        }

        btnSensorDisplay.setOnClickListener {
            Log.d(TAG, "Sensor Display button clicked")
            Log.d(TAG, "Starting SensorDisplayActivity with UID: $uid, WorkspaceID: $workspaceId")
            val intent = Intent(this, SensorDisplayActivity::class.java)
            intent.putExtra("uid", uid)
            intent.putExtra("workspaceId", workspaceId)
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            Log.d(TAG, "Logout button clicked")
            // Navigate back to FirefighterActivity
            val intent = Intent(this, FirefighterActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    private fun loadUserName(uid: String) {
        Log.d(TAG, "Loading workspace name for UID: $uid")
        val workspaceRef = database.reference.child("users").child(uid).child("workspaces").child(workspaceId!!)
        
        // First get the workspace type
        workspaceRef.child("type").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Log.e(TAG, "Workspace type not found in database")
                    Toast.makeText(this@WorkspaceDisplayActivity, "Workspace type not found", Toast.LENGTH_SHORT).show()
                    return
                }

                val workspaceType = snapshot.value.toString()
                Log.d(TAG, "Workspace type: $workspaceType")

                // Based on the type, get the appropriate name
                when (workspaceType) {
                    "Apartment" -> {
                        // For apartments, get the name from apartmentInfo/apartmentName
                        workspaceRef.child("name")
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        val apartmentName = snapshot.value.toString()
                                        Log.d(TAG, "Apartment name loaded: $apartmentName")
                                        tvUserName.text = apartmentName
                                    } else {
                                        Log.e(TAG, "Apartment name not found in database")
                                        Toast.makeText(this@WorkspaceDisplayActivity, "Apartment name not found", Toast.LENGTH_SHORT).show()
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.e(TAG, "Failed to load apartment name: ${error.message}")
                                    Toast.makeText(this@WorkspaceDisplayActivity, "Failed to load apartment name", Toast.LENGTH_SHORT).show()
                                }
                            })
                    }
                    else -> {
                        // For House, Hotel, and Business, get the name directly
                        workspaceRef.child("name").addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (snapshot.exists()) {
                                    val workspaceName = snapshot.value.toString()
                                    Log.d(TAG, "Workspace name loaded: $workspaceName")
                                    tvUserName.text = workspaceName
                                } else {
                                    Log.e(TAG, "Workspace name not found in database")
                                    Toast.makeText(this@WorkspaceDisplayActivity, "Workspace name not found", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e(TAG, "Failed to load workspace name: ${error.message}")
                                Toast.makeText(this@WorkspaceDisplayActivity, "Failed to load workspace name", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load workspace type: ${error.message}")
                Toast.makeText(this@WorkspaceDisplayActivity, "Failed to load workspace type", Toast.LENGTH_SHORT).show()
            }
        })
    }
} 
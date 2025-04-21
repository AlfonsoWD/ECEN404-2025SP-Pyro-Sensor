package com.example.pyrosensor

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.database.*
import android.content.Intent
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import android.util.Log
import android.widget.Toast

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private lateinit var fabAddSensor: FloatingActionButton
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) //initializes activity and sets saved state
        setContentView(R.layout.activity_main) //sets layout for the main activity

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        fabAddSensor = findViewById(R.id.fab_add_sensor) //finds the FAB
        bottomNavigation = findViewById(R.id.bottom_navigation) //finds the bottom navigation view
        
        // Check if opened from notification to stop alarm
        val action = intent.action
        if (action == "STOP_ALARM") {
            Log.d(TAG, "Activity started with STOP_ALARM action, stopping alarm service")
            AlarmService.stopService(applicationContext)
        }
        
        fabAddSensor.setOnClickListener {
            // Navigate to the screen for adding sensors
            startActivity(Intent(this, AddSensorActivity::class.java))
        }

        // Set the initial fragment and selected item
        val uid = auth.currentUser?.uid
        if (uid != null) {
            handleUserLogin(uid)
        }
        bottomNavigation.selectedItemId = R.id.nav_sensors

        bottomNavigation.setOnItemSelectedListener { menuItem -> //handles item selection in navigation
            when (menuItem.itemId) { //checks the selected item id
                R.id.nav_sensors -> {
                    val currentUid = auth.currentUser?.uid
                    if (currentUid != null) {
                        checkCurrentUser()
                    }
                    true
                }
                R.id.nav_profile -> {
                    loadFragment(ProfileFragment()) //loads the profile fragment
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment()) //loads the settings fragment
                    true
                }
                else -> false //returns false for unhandled cases
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
            
        // Show FAB only when SensorsFragment is displayed
        fabAddSensor.visibility = if (fragment is SensorsFragment) View.VISIBLE else View.GONE
    }

    private fun handleUserLogin(uid: String) {
        // Get the first workspace ID for this user
        database.reference.child("users").child(uid).child("workspaces")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val firstWorkspaceId = snapshot.children.firstOrNull()?.key
                        if (firstWorkspaceId != null) {
                            loadFragment(SensorsFragment.newInstance(uid, firstWorkspaceId))
                        } else {
                            // No workspaces found, go to workspace setup
                            startActivity(Intent(this@MainActivity, WorkspaceSetupActivity::class.java).apply {
                                putExtra("uid", uid)
                            })
                        }
                    } else {
                        // No workspaces found, go to workspace setup
                        startActivity(Intent(this@MainActivity, WorkspaceSetupActivity::class.java).apply {
                            putExtra("uid", uid)
                        })
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error loading workspaces: ${error.message}")
                    Toast.makeText(this@MainActivity, "Error loading workspaces", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun checkCurrentUser() {
        val currentUid = auth.currentUser?.uid
        if (currentUid != null) {
            // Get the first workspace ID for this user
            database.reference.child("users").child(currentUid).child("workspaces")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            val firstWorkspaceId = snapshot.children.firstOrNull()?.key
                            if (firstWorkspaceId != null) {
                                loadFragment(SensorsFragment.newInstance(currentUid, firstWorkspaceId))
                            } else {
                                // No workspaces found, go to workspace setup
                                startActivity(Intent(this@MainActivity, WorkspaceSetupActivity::class.java).apply {
                                    putExtra("uid", currentUid)
                                })
                            }
                        } else {
                            // No workspaces found, go to workspace setup
                            startActivity(Intent(this@MainActivity, WorkspaceSetupActivity::class.java).apply {
                                putExtra("uid", currentUid)
                            })
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "Error loading workspaces: ${error.message}")
                        Toast.makeText(this@MainActivity, "Error loading workspaces", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    private fun handleLogout() {
        // Sign out from Firebase
        auth.signOut()
        
        // Create intent with flags to clear the activity stack
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    //+18333643693
}


package com.example.pyrosensor

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener


class WelcomeActivity : AppCompatActivity() { //activity for the welcome screen
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var loginButton: Button
    private lateinit var registerButton: Button
    private lateinit var firefighterButton: Button
    private lateinit var welcomeText: TextView
    private val TAG = "WelcomeActivity"

    override fun onCreate(savedInstanceState: Bundle?) { //called when the activity is created
        super.onCreate(savedInstanceState) //initializes the activity
        setContentView(R.layout.activity_welcome) //sets the layout for the welcome activity

        // Initialize Firebase services
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        // Initialize views
        loginButton = findViewById(R.id.btn_sign_in) //finds the login button in the layout
        registerButton = findViewById(R.id.btn_create_account) //finds the register button in the layout
        firefighterButton = findViewById(R.id.btn_firefighter) //finds the firefighter button in the layout
        welcomeText = findViewById(R.id.welcome_subtitle)
        
        // Check if opened from notification to stop alarm
        val action = intent.action
        if (action == "STOP_ALARM") {
            Log.d(TAG, "Activity started with STOP_ALARM action, stopping alarm service")
            AlarmService.stopService(applicationContext)
        }

        // Set up the auth state listener
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed in
                Log.d(TAG, "User is signed in: ${user.uid}")
                navigateToMainActivity()
            }
            // Don't navigate to login if user is signed out - stay on welcome screen
        }

        // Set up button click listeners
        loginButton.setOnClickListener { //listener for login button click
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        registerButton.setOnClickListener { //listener for register button click
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        firefighterButton.setOnClickListener { //listener for firefighter login button click
            val intent = Intent(this, FirefighterActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        // Check if user is signed in
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already signed in: ${currentUser.uid}")
            navigateToMainActivity()
        }
        // Don't navigate to login if user is signed out - stay on welcome screen
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun checkAuthState() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // User is signed in, verify workspace access
            val uid = currentUser.uid
            database.reference.child("users").child(uid).child("workspaces")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                            // User has at least one workspace, redirect to MainActivity
                            startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
                            finish()
                        } else {
                            // No workspaces found, redirect to workspace setup
                            startActivity(Intent(this@WelcomeActivity, WorkspaceSetupActivity::class.java).apply {
                                putExtra("uid", uid)
                            })
                            finish()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        // If there's an error, show the welcome screen
                        showWelcomeScreen()
                    }
                })
        } else {
            // No user is signed in, show the welcome screen
            showWelcomeScreen()
        }
    }

    private fun showWelcomeScreen() {
        // Make sure the welcome UI is visible
        loginButton.visibility = View.VISIBLE
        registerButton.visibility = View.VISIBLE
        firefighterButton.visibility = View.VISIBLE
        welcomeText.visibility = View.VISIBLE
    }
}


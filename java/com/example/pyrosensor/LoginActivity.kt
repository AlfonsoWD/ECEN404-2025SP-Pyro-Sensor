package com.example.pyrosensor //defines the package name for the application

import android.content.Intent //imports the Intent class for navigation between activities
import android.os.Bundle //imports the Bundle class for saving activity state
import android.util.Log //imports the Log class for logging
import android.widget.Button //imports the Button widget for UI interaction
import android.widget.EditText //imports the EditText widget for text input fields
import android.widget.Toast //imports the Toast class for displaying brief messages
import androidx.appcompat.app.AppCompatActivity //imports AppCompatActivity for activity behavior
import com.google.firebase.auth.FirebaseAuth //imports FirebaseAuth for authentication functionality
import com.google.firebase.database.FirebaseDatabase //imports FirebaseDatabase for database functionality
import com.google.firebase.messaging.FirebaseMessaging //imports FirebaseMessaging for FCM functionality

class LoginActivity : AppCompatActivity() { //defines the LoginActivity class that extends AppCompatActivity

    private lateinit var auth: FirebaseAuth //declares a FirebaseAuth instance for user authentication
    private val TAG = "LoginActivity" //defines a TAG for logging

    override fun onCreate(savedInstanceState: Bundle?) { //overrides the onCreate method, entry point of the activity
        super.onCreate(savedInstanceState) //calls the parent class's onCreate method
        setContentView(R.layout.activity_login) //sets the layout for the activity

        auth = FirebaseAuth.getInstance() //initializes the FirebaseAuth instance

        val loginButton = findViewById<Button>(R.id.loginButton) //finds the login button from the layout
        val emailField = findViewById<EditText>(R.id.loginEmail) //finds the email input field from the layout
        val passwordField = findViewById<EditText>(R.id.loginPassword) //finds the password input field from the layout
        val devLoginButton = findViewById<Button>(R.id.devLoginButton) //finds the developer login button from the layout

        loginButton.setOnClickListener { //sets a click listener on the login button
            val email = emailField.text.toString() //gets the text from the email field as a string
            val password = passwordField.text.toString() //gets the text from the password field as a string

            if (email.isNotEmpty() && password.isNotEmpty()) { //checks if both fields are not empty
                signIn(email, password) //calls the signIn method with the email and password
            } else {
                Toast.makeText(this, "Please enter both email and password", Toast.LENGTH_SHORT).show() //shows a message for empty fields
            }
        }

        devLoginButton.setOnClickListener {
            // Hardcoded developer credentials
            val devEmail = "missrev@gmail.com"
            val devPassword = "Test123"

            signIn(devEmail, devPassword) //calls the signIn method with the developer email and password
        }
    }

    private fun signIn(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Get FCM token and store it
                    FirebaseMessaging.getInstance().token.addOnCompleteListener { tokenTask ->
                        if (tokenTask.isSuccessful) {
                            val token = tokenTask.result
                            // Store the token in the database
                            val currentUser = auth.currentUser
                            if (currentUser != null) {
                                val userRef = FirebaseDatabase.getInstance().getReference("users/${currentUser.uid}")
                                userRef.child("fcmToken").setValue(token)
                                    .addOnSuccessListener {
                                        Log.d(TAG, "FCM token stored successfully")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "Error storing FCM token", e)
                                    }
                            }
                        }
                    }

                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    Toast.makeText(baseContext, "Authentication successful.", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(baseContext, "Authentication failed.", Toast.LENGTH_SHORT).show()
                }
            }
    }
}

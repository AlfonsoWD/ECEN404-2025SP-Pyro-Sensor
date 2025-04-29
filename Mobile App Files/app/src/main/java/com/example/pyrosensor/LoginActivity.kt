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
import com.google.android.material.dialog.MaterialAlertDialogBuilder //imports MaterialAlertDialogBuilder for showing dialogs
import com.google.android.material.textfield.TextInputEditText //imports TextInputEditText for text input fields in dialogs
import android.widget.TextView //imports the TextView widget for displaying text

class LoginActivity : AppCompatActivity() { //defines the LoginActivity class that extends AppCompatActivity

    // Firebase authentication instance and logging tag
    private lateinit var auth: FirebaseAuth
    private val TAG = "LoginActivity"

    // Initialize activity and set up login functionality
    override fun onCreate(savedInstanceState: Bundle?) { //overrides the onCreate method, entry point of the activity
        super.onCreate(savedInstanceState) //calls the parent class's onCreate method
        setContentView(R.layout.activity_login) //sets the layout for the activity

        // Check if user is already logged in
        if (FirebaseAuth.getInstance().currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        auth = FirebaseAuth.getInstance() //initializes the FirebaseAuth instance

        // Set up click listeners
        findViewById<Button>(R.id.loginButton).setOnClickListener {
            val email = findViewById<EditText>(R.id.loginEmail).text.toString()
            val password = findViewById<EditText>(R.id.loginPassword).text.toString()
            signIn(email, password)
        }

        findViewById<Button>(R.id.devLoginButton).setOnClickListener {
            val devEmail = "ironman@gmail.com"
            val devPassword = "Test123"
            signIn(devEmail, devPassword)
        }

        findViewById<TextView>(R.id.tvRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            showForgotPasswordDialog()
        }
    }

    // Display dialog for password reset
    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etEmail)

        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setView(dialogView)
            .setPositiveButton("Send") { dialog, _ ->
                val email = etEmail.text.toString()
                if (email.isBlank()) {
                    Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                sendPasswordResetEmail(email)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // Send password reset email to user
    private fun sendPasswordResetEmail(email: String) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "If email account exist, password reset email was sent. Please check your inbox.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to send reset email: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // Handle user sign in with email and password
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

package com.example.pyrosensor //defines the package name for the application

import android.content.Intent //imports the Intent class for navigation between activities
import android.os.Bundle //imports the Bundle class for saving activity state
import android.text.Editable //imports the Editable class for text editing
import android.text.TextWatcher //imports the TextWatcher class for text change listeners
import android.util.Log //imports the Log class for logging messages
import android.widget.Button //imports the Button widget for UI interaction
import android.widget.EditText //imports the EditText widget for text input fields
import android.widget.Toast //imports the Toast class for displaying brief messages
import androidx.appcompat.app.AppCompatActivity //imports AppCompatActivity for activity behavior
import com.google.firebase.database.FirebaseDatabase //imports FirebaseDatabase for database functionality
import com.google.firebase.database.DataSnapshot //imports DataSnapshot for reading data from the database
import com.google.firebase.database.DatabaseError //imports DatabaseError for handling database errors
import com.google.firebase.database.ValueEventListener //imports ValueEventListener for listening to data changes

class FirefighterActivity : AppCompatActivity() { //defines the FirefighterActivity class that extends AppCompatActivity
    private lateinit var accessCodeInput: EditText
    private lateinit var submitButton: Button
    private lateinit var devLoginButton: Button
    private lateinit var database: FirebaseDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_firefighter) // sets the layout for the activity

        database = FirebaseDatabase.getInstance() //initializes FirebaseDatabase
        accessCodeInput = findViewById(R.id.access_code)
        submitButton = findViewById(R.id.btn_submit_firefighter)
        devLoginButton = findViewById(R.id.devLoginButton)

        // Set up text watcher for access code input
        accessCodeInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Convert to uppercase and remove any non-alphanumeric characters
                val filtered = s?.toString()?.filter { it.isLetterOrDigit() }?.uppercase()
                if (filtered != s?.toString()) {
                    accessCodeInput.setText(filtered)
                    accessCodeInput.setSelection(filtered?.length ?: 0)
                }
            }
        })

        submitButton.setOnClickListener {
            val accessCode = accessCodeInput.text.toString() // gets the access code from the input field
            if (accessCode.length == 6) {
                verifyAccessCode(accessCode) // calls the verifyAccessCode function with the access code
            } else {
                Toast.makeText(this, "Please enter a valid 6-character code", Toast.LENGTH_SHORT).show()
            }
        }

        devLoginButton.setOnClickListener {
            // Hardcoded developer access code
            val devAccessCode = "QHEH0V"
            verifyAccessCode(devAccessCode)
        }
    }

    private fun verifyAccessCode(accessCode: String) {
        Log.d("FirefighterActivity", "Verifying access code: $accessCode")
        
        // Query the userCodes node directly using the access code
        database.reference.child("userCodes")
            .child(accessCode)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Access code found
                        Log.d("FirefighterActivity", "Access code verified")
                        // Get both uid and workspaceId from the userCodes node
                        val uid = snapshot.child("uid").getValue(String::class.java)
                        val workspaceId = snapshot.child("workspaceId").getValue(String::class.java)
                        
                        if (uid != null && workspaceId != null) {
                            // Navigate to WorkspaceDisplayActivity with both IDs
                            val intent = Intent(this@FirefighterActivity, WorkspaceDisplayActivity::class.java)
                            intent.putExtra("uid", uid)
                            intent.putExtra("workspaceId", workspaceId)
                            startActivity(intent)
                            finish()
                        } else {
                            Log.e("FirefighterActivity", "Missing required data: uid=${uid != null}, workspaceId=${workspaceId != null}")
                            Toast.makeText(this@FirefighterActivity, "Error: Missing required data", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // Access code not found
                        Log.e("FirefighterActivity", "Invalid access code")
                        Toast.makeText(this@FirefighterActivity, "Invalid access code", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirefighterActivity", "Database error: ${error.message}")
                    Toast.makeText(this@FirefighterActivity, "Error verifying access code", Toast.LENGTH_SHORT).show()
                }
            })
    }
}

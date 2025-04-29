package com.example.pyrosensor

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.google.firebase.database.FirebaseDatabase

class SensorDisplayActivity : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    private var workspaceId: String? = null
    private var uid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensor_display)

        database = FirebaseDatabase.getInstance()
        workspaceId = intent.getStringExtra("workspaceId")
        uid = intent.getStringExtra("uid")

        if (workspaceId == null || uid == null) {
            Log.e("SensorDisplayActivity", "Missing required data: workspaceId=${workspaceId != null}, uid=${uid != null}")
            Toast.makeText(this, "Error: Missing required data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        displaySensors()
    }

    private fun displaySensors() {
        val fragmentManager: FragmentManager = supportFragmentManager
        val fragmentTransaction: FragmentTransaction = fragmentManager.beginTransaction()

        // Create and add the FirefighterSensorFragment with both workspaceId and uid
        val sensorFragment = FirefighterSensorFragment.newInstance(workspaceId!!, uid!!)
        fragmentTransaction.add(R.id.fragmentContainer, sensorFragment)
        fragmentTransaction.commit()
    }
} 
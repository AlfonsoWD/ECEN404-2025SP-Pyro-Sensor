package com.example.pyrosensor

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import androidx.appcompat.widget.Toolbar

class FirefighterSensorFragment : Fragment() {
    private val TAG = "FirefighterSensorFragment"
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FirefighterSensorAdapter
    private var workspaceId: String? = null
    private lateinit var database: FirebaseDatabase
    private var lastAlarmState: MutableMap<String, String> = mutableMapOf()
    private var lastTriggerTime: MutableMap<String, Long> = mutableMapOf()
    private var lastDeactivationTime: MutableMap<String, Long> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance()
        workspaceId = arguments?.getString("workspaceId")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_firefighter_sensor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set up toolbar
        val toolbar = view.findViewById<Toolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = FirefighterSensorAdapter()
        recyclerView.adapter = adapter

        // Load sensors for the workspace
        loadSensors()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::adapter.isInitialized) {
            adapter.cleanup()
        }
    }

    private fun loadSensors() {
        if (workspaceId == null) return

        val uid = arguments?.getString("uid") ?: return
        val sensorRef = database.reference.child("users").child(uid).child("sensors")

        sensorRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sensorList = mutableListOf<Sensor>()
                if (snapshot.exists()) {
                    Log.d(TAG, "Received sensor data update")
                    snapshot.children.forEach { sensorSnapshot ->
                        val macAddress = sensorSnapshot.key ?: "Unknown MAC"
                        val sensorName = sensorSnapshot.child("Name").child("Sensor").getValue(String::class.java) ?: "Unknown"
                        val sensorStatus = sensorSnapshot.child("Alarm").child("message").getValue(String::class.java) ?: "Unknown"
                        val batteryStatus = sensorSnapshot.child("Battery").child("message").getValue(String::class.java) ?: "Unknown"
                        val sensorState = sensorSnapshot.child("Alarm").child("alarm_status").getValue(String::class.java) ?: "Safe"
                        val alarmStartTime = sensorSnapshot.child("Alarm").child("alarmTime").getValue(String::class.java)
                        
                        Log.d(TAG, "Sensor: $sensorName, State: $sensorState, Previous State: ${lastAlarmState[macAddress]}")
                        
                        // Check if alarm state has changed
                        val previousState = lastAlarmState[macAddress]
                        if (previousState != sensorState) {
                            if (sensorState == "Alarm" || sensorState == "Warning") {
                                Log.d(TAG, "Alarm/Warning triggered for sensor: $sensorName")
                                lastTriggerTime[macAddress] = System.currentTimeMillis()
                            } else if (sensorState == "Safe" && (previousState == "Alarm" || previousState == "Warning")) {
                                Log.d(TAG, "Sensor deactivated: $sensorName")
                                lastDeactivationTime[macAddress] = System.currentTimeMillis()
                            }
                        }
                        lastAlarmState[macAddress] = sensorState
                        
                        // Create sensor with trigger and deactivation times
                        val lastTriggered = lastTriggerTime[macAddress] ?: 0
                        val lastDeactivated = lastDeactivationTime[macAddress] ?: 0
                        sensorList.add(Sensor(sensorName, sensorStatus, batteryStatus, sensorState, lastTriggered, lastDeactivated, alarmStartTime))
                    }

                    // Sort the list based on current state and last deactivation time
                    sensorList.sortWith(compareByDescending<Sensor> { it.state == "Alarm" }
                        .thenByDescending { it.state == "Warning" }
                        .thenByDescending { it.lastDeactivated })

                    adapter.submitList(sensorList)
                } else {
                    Log.w(TAG, "No sensors available")
                    adapter.submitList(emptyList())
                    Toast.makeText(context, "No sensors available.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to load sensors: ${error.message}")
                Toast.makeText(context, "Failed to load sensors: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    companion object {
        fun newInstance(workspaceId: String, uid: String): FirefighterSensorFragment {
            val fragment = FirefighterSensorFragment()
            val args = Bundle()
            args.putString("workspaceId", workspaceId)
            args.putString("uid", uid)
            fragment.arguments = args
            return fragment
        }
    }
} 
package com.example.pyrosensor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class SensorsFragment : Fragment() {
    // UI components and data
    private lateinit var adapter: SensorAdapter
    private lateinit var database: FirebaseDatabase
    private var uid: String? = null
    private var workspaceId: String? = null
    private lateinit var homeNameText: TextView
    private lateinit var sensorMonitor: SensorMonitor

    // Initialize fragment and start sensor monitoring
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance()
        uid = arguments?.getString("uid")
        workspaceId = arguments?.getString("workspaceId")
        
        // Initialize sensor monitor
        if (uid != null && workspaceId != null) {
            sensorMonitor = SensorMonitor(uid!!, workspaceId!!)
            sensorMonitor.startMonitoring()
        }
    }

    // Create and return the fragment's view
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_sensors, container, false)

        // Initialize views
        homeNameText = view.findViewById(R.id.homeName)
        val recyclerView = view.findViewById<RecyclerView>(R.id.sensorDisplay)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        if (uid != null && workspaceId != null) {
            adapter = SensorAdapter(requireContext(), uid!!, workspaceId!!)
            recyclerView.adapter = adapter

            // Load workspace name and sensors
            loadWorkspaceName()
            loadSensors()
        }

        return view
    }

    // Called after view creation
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    // Clean up resources when fragment is destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        if (::adapter.isInitialized) {
            adapter.cleanup()
        }
    }

    // Load and display workspace name
    private fun loadWorkspaceName() {
        if (uid == null) return

        database.reference.child("users").child(uid!!).child("workspaces")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        // Get the first workspace's name
                        val firstWorkspace = snapshot.children.firstOrNull()
                        val workspaceName = firstWorkspace?.child("name")?.getValue(String::class.java)
                        homeNameText.text = workspaceName ?: "No Workspace"
                    } else {
                        homeNameText.text = "No Workspace"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    homeNameText.text = "Error Loading Workspace"
                }
            })
    }

    // Load and display sensor list
    private fun loadSensors() {
        if (uid == null) return

        database.reference.child("users").child(uid!!).child("sensors")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val sensors = snapshot.children.toList()
                    adapter.updateSensors(sensors)
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error
                }
            })
    }

    // Update sensor name in the adapter
    fun updateSensorName(macAddress: String, newName: String) {
        adapter.updateSensorName(macAddress, newName)
    }

    // Factory method to create new instance with arguments
    companion object {
        fun newInstance(uid: String, workspaceId: String): SensorsFragment {
            return SensorsFragment().apply {
                arguments = Bundle().apply {
                    putString("uid", uid)
                    putString("workspaceId", workspaceId)
                }
            }
        }
    }
} 
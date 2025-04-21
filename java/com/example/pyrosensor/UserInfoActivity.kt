package com.example.pyrosensor

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.GenericTypeIndicator


class UserInfoActivity : AppCompatActivity() {
    private lateinit var database: FirebaseDatabase
    private var workspaceId: String? = null
    private var uid: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_info)

        // Set up toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        database = FirebaseDatabase.getInstance()
        workspaceId = intent.getStringExtra("workspaceId")
        uid = intent.getStringExtra("uid")

        if (workspaceId == null || uid == null) {
            Log.e("UserInfoActivity", "Missing required data: workspaceId=${workspaceId != null}, uid=${uid != null}")
            Toast.makeText(this, "Error: Missing required data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadWorkspaceInfo()
    }

    private fun loadWorkspaceInfo() {
        database.reference.child("users").child(uid!!).child("workspaces").child(workspaceId!!)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val buildingType = snapshot.child("type").getValue(String::class.java)
                        val buildingName = snapshot.child("name").getValue(String::class.java)
                        val address = snapshot.child("address").getValue(object : GenericTypeIndicator<Map<String, String>>() {})
                        val totOccupants = snapshot.child("totOccupants").getValue(Int::class.java)
                        val totPets = snapshot.child("totPets").getValue(Int::class.java)
                        val occupants = snapshot.child("occupants").getValue(object : GenericTypeIndicator<Map<String, Map<String, String>>>() {})
                        val pets = snapshot.child("pets").getValue(object : GenericTypeIndicator<Map<String, Map<String, String>>>() {})
                        val floorNum = snapshot.child("apartmentInfo").child("floorNum").getValue(String::class.java)
                        val apartmentName = snapshot.child("apartmentInfo").child("apartmentName").getValue(String::class.java)
                        // Update building information
                        val buildingTypeText = findViewById<TextView>(R.id.tvBuildingType)
                        when (buildingType) {
                            "Apartment" -> {
                                buildingTypeText.text = "Type: Apartment\nComplex: $apartmentName\nFloor: $floorNum"
                            }
                            "Hotel", "Business" -> {
                                buildingTypeText.text = "Type: $buildingType\nName: $buildingName"
                            }
                            else -> {
                                buildingTypeText.text = "Type: $buildingType"
                            }
                        }
                        
                        findViewById<TextView>(R.id.tvAddress).text = buildAddressString(address)

                        // Show/hide appropriate sections based on building type
                        when (buildingType) {
                            "House", "Apartment" -> {
                                findViewById<View>(R.id.cardOccupants).visibility = View.VISIBLE
                                findViewById<View>(R.id.cardOwnerManager).visibility = View.GONE
                                
                                // Update occupants information (add 1 to include the main user)
                                findViewById<TextView>(R.id.tvTotalOccupants).text = "Total Occupants: ${(totOccupants ?: 0) + 1}"
                                displayOccupants(occupants)
                                
                                // Update pets information - only show if there are pets
                                if (pets != null && pets.isNotEmpty() && totPets != null && totPets > 0) {
                                    findViewById<View>(R.id.cardPets).visibility = View.VISIBLE
                                    findViewById<TextView>(R.id.tvTotalPets).text = "Total Pets: $totPets"
                                    displayPets(pets)
                                } else {
                                    findViewById<View>(R.id.cardPets).visibility = View.GONE
                                }
                            }
                            "Hotel", "Business" -> {
                                findViewById<View>(R.id.cardOccupants).visibility = View.GONE
                                findViewById<View>(R.id.cardPets).visibility = View.GONE
                                findViewById<View>(R.id.cardOwnerManager).visibility = View.VISIBLE
                                
                                // Load and display owner/manager information
                                loadOwnerInfo(uid!!)
                            }
                        }
                    } else {
                        Log.e("UserInfoActivity", "Workspace not found")
                        Toast.makeText(this@UserInfoActivity, "Workspace not found", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("UserInfoActivity", "Database error: ${error.message}")
                    Toast.makeText(this@UserInfoActivity, "Error loading workspace information", Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun buildAddressString(address: Map<String, String>?): String {
        if (address == null) return "Address not available"
        
        val street = address["street"] ?: ""
        val addressLine2 = address["addressLine2"] ?: ""
        val city = address["city"] ?: ""
        val state = address["state"] ?: ""
        val zipCode = address["zipCode"] ?: ""
        
        return buildString {
            append(street)
            if (addressLine2.isNotEmpty()) {
                append("\n$addressLine2")
            }
            append("\n$city, $state $zipCode")
        }
    }

    private fun displayOccupants(occupants: Map<String, Map<String, String>>?) {
        val layout = findViewById<LinearLayout>(R.id.layoutOccupantsList)
        layout.removeAllViews()
        
        // First, load and display the account owner's information
        database.reference.child("users").child(uid!!).child("userInfo")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                        val dob = snapshot.child("dob").getValue(String::class.java) ?: ""
                        val age = snapshot.child("age").getValue(Int::class.java) ?: 0
                        
                        // Create and add the owner's card
                        val ownerView = LayoutInflater.from(this@UserInfoActivity).inflate(R.layout.item_occupant, layout, false)
                        ownerView.findViewById<TextView>(R.id.tvOccupantName).text = "Name: $firstName $lastName (Owner)"
                        ownerView.findViewById<TextView>(R.id.tvOccupantDOB).text = "Date of Birth: $dob"
                        ownerView.findViewById<TextView>(R.id.tvOccupantAge).text = "Age: $age"
                        layout.addView(ownerView)
                        
                        // Then display other occupants if they exist
                        if (occupants != null) {
                            occupants.forEach { (_, occupant) ->
                                val occupantView = LayoutInflater.from(this@UserInfoActivity).inflate(R.layout.item_occupant, layout, false)
                                occupantView.findViewById<TextView>(R.id.tvOccupantName).text = "Name: ${occupant["name"]}"
                                occupantView.findViewById<TextView>(R.id.tvOccupantDOB).text = "Date of Birth: ${occupant["dob"]}"
                                occupantView.findViewById<TextView>(R.id.tvOccupantAge).text = "Age: ${occupant["age"]}"
                                layout.addView(occupantView)
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("UserInfoActivity", "Error loading owner info: ${error.message}")
                }
            })
    }

    private fun displayPets(pets: Map<String, Map<String, String>>?) {
        if (pets == null || pets.isEmpty()) {
            findViewById<View>(R.id.cardPets).visibility = View.GONE
            return
        }
        
        val layout = findViewById<LinearLayout>(R.id.layoutPetsList)
        layout.removeAllViews()
        
        pets.forEach { (_, pet) ->
            val petView = LayoutInflater.from(this).inflate(R.layout.item_pet, layout, false)
            petView.findViewById<TextView>(R.id.tvPetName).text = "Name: ${pet["name"]}"
            petView.findViewById<TextView>(R.id.tvPetColor).text = "Color: ${pet["color"]}"
            petView.findViewById<TextView>(R.id.tvPetBreed).text = "Breed: ${pet["breed"]}"
            layout.addView(petView)
        }
    }

    private fun loadOwnerInfo(uid: String) {
        database.reference.child("users").child(uid).child("userInfo")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val firstName = snapshot.child("firstName").getValue(String::class.java) ?: ""
                        val lastName = snapshot.child("lastName").getValue(String::class.java) ?: ""
                        val dob = snapshot.child("dob").getValue(String::class.java) ?: ""
                        val age = snapshot.child("age").getValue(Int::class.java) ?: 0
                        
                        findViewById<TextView>(R.id.tvOwnerName).text = "Name: $firstName $lastName"
                        findViewById<TextView>(R.id.tvOwnerDOB).text = "Date of Birth: $dob"
                        findViewById<TextView>(R.id.tvOwnerAge).text = "Age: $age"
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("UserInfoActivity", "Error loading owner info: ${error.message}")
                }
            })
    }
} 
package com.example.pyrosensor

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.database.GenericTypeIndicator
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.example.pyrosensor.databinding.FragmentProfileBinding
import java.util.*
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.common.api.Status

//TODO: Add a way to manage/change the information/picture stored in the database
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var profileName: TextView
    private lateinit var profileEmail: TextView
    private lateinit var profileAddress: TextView
    private lateinit var profileBuildingType: TextView
    private lateinit var profileOccupantsCount: TextView
    private lateinit var profilePetsCount: TextView
    private lateinit var occupantsLayout: LinearLayout
    private lateinit var petsLayout: LinearLayout
    private lateinit var petsCard: View
    private lateinit var profilePicture: ImageView
    private lateinit var storage: FirebaseStorage
    
    private var userId: String = ""
    private var workspaceId: String = ""
    private var userFirstName: String = ""
    private var userLastName: String = ""
    private var buildingType: String = ""
    private var hasPets: Boolean = false
    
    private var occupants: List<Occupant> = emptyList()
    private var pets: List<Pet> = emptyList()
    private var currentDialog: androidx.appcompat.app.AlertDialog? = null
    private var userDOB: String = ""
    private var userAge: Int = 0

    data class Occupant(
        val id: String = "",
        var name: String = "",
        var dob: String = "",
        var age: Int = 0
    )

    data class Pet(
        val id: String = "",
        var name: String = "",
        var color: String = "",
        var breed: String = ""
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupEditButtons()
        fetchProfileData()
    }

    private fun setupViews() {
        profileName = binding.profileName
        profileEmail = binding.profileEmail
        profileAddress = binding.profileAddress
        profileBuildingType = binding.profileBuildingType
        profileOccupantsCount = binding.profileOccupantsCount
        profilePetsCount = binding.profilePetsCount
        occupantsLayout = binding.layoutOccupantsList
        petsLayout = binding.layoutPetsList
        petsCard = binding.cardPetsInfo
        profilePicture = binding.profilePicture
        storage = FirebaseStorage.getInstance()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    data?.let { intent ->
                        try {
                            val place = Autocomplete.getPlaceFromIntent(intent)
                            val address = place.address
                            if (address != null) {
                                // Find the current dialog and update its address field
                                val dialog = currentDialog
                                if (dialog != null) {
                                    val etAddress = dialog.findViewById<TextInputEditText>(R.id.etAddress)
                                    etAddress?.setText(address)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error processing place result: ${e.message}")
                            Toast.makeText(context, "Error processing place result: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    data?.let { intent ->
                        try {
                            val status = Autocomplete.getStatusFromIntent(intent)
                            Toast.makeText(context, "Error: ${status.statusMessage}", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Log.e("ProfileFragment", "Error getting status: ${e.message}")
                            Toast.makeText(context, "Error getting status: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                Activity.RESULT_CANCELED -> {
                    // The user canceled the operation
                }
            }
        }
    }

    private fun fetchProfileData() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            userId = currentUser.uid
            Log.d("ProfileFragment", "Fetching data for user ID: $userId")
            
            // Get user data from users/{uid}
            val userRef = FirebaseDatabase.getInstance().reference.child("users").child(userId)
            userRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        Log.d("ProfileFragment", "User data snapshot exists")
                        
                        // Debug: print all keys at top level
                        val keys = mutableListOf<String>()
                        snapshot.children.forEach { keys.add(it.key ?: "null") }
                        Log.d("ProfileFragment", "User data keys: ${keys.joinToString(", ")}")
                        
                        // Get workspaceId
                        workspaceId = snapshot.child("workspaceId").getValue(String::class.java) ?: ""
                        Log.d("ProfileFragment", "Workspace ID: '$workspaceId'")
                        
                        // Get user info
                        val userInfoSnapshot = snapshot.child("userInfo")
                        Log.d("ProfileFragment", "UserInfo exists: ${userInfoSnapshot.exists()}")
                        
                        userFirstName = userInfoSnapshot.child("firstName").getValue(String::class.java) ?: ""
                        userLastName = userInfoSnapshot.child("lastName").getValue(String::class.java) ?: ""
                        userDOB = userInfoSnapshot.child("dob").getValue(String::class.java) ?: ""
                        userAge = userInfoSnapshot.child("age").getValue(Int::class.java) ?: 0
                        val email = userInfoSnapshot.child("email").getValue(String::class.java) ?: ""
                        
                        Log.d("ProfileFragment", "User info: firstName='$userFirstName', lastName='$userLastName', dob='$userDOB', age='$userAge', email='$email'")

                        // Load profile picture
                        val profilePictureUrl = snapshot.child("profilePictureUrl").getValue(String::class.java)
                        Log.d("ProfileFragment", "Profile picture URL: $profilePictureUrl")
                        if (!profilePictureUrl.isNullOrEmpty()) {
                            loadProfilePicture(profilePictureUrl)
                        }
                        
                        // Set personal information
                        profileName.text = "Name: $userFirstName $userLastName"
                        profileEmail.text = "Email: $email"
                        
                        // If we have a workspace ID, fetch workspace data
                        if (workspaceId.isNotEmpty()) {
                            fetchWorkspaceData(workspaceId)
                        } else {
                            Log.e("ProfileFragment", "No workspace ID found")
                            
                            // Try to check if there's another way to get workspace ID
                            val accessCode = snapshot.child("access_code").getValue(String::class.java)
                            Log.d("ProfileFragment", "Access code: $accessCode")
                            
                            // If we have an access code, we can try to get the workspace ID from userCodes
                            if (!accessCode.isNullOrEmpty()) {
                                fetchWorkspaceIdFromAccessCode(accessCode)
                            }
                        }
                    } else {
                        Log.e("ProfileFragment", "No user data found")
                        Toast.makeText(context, "Failed to fetch user data.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ProfileFragment", "Database error: ${error.message}")
                    Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            Log.e("ProfileFragment", "User not authenticated")
            Toast.makeText(context, "User not authenticated.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchWorkspaceIdFromAccessCode(accessCode: String) {
        Log.d("ProfileFragment", "Trying to get workspace ID from access code: $accessCode")
        val userCodesRef = FirebaseDatabase.getInstance().reference.child("userCodes").child(accessCode)
        userCodesRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d("ProfileFragment", "UserCodes snapshot exists for access code: $accessCode")
                    
                    // Get the workspace ID from the access code
                    workspaceId = snapshot.child("workspaceId").getValue(String::class.java) ?: ""
                    Log.d("ProfileFragment", "Found workspace ID from access code: $workspaceId")
                    
                    if (workspaceId.isNotEmpty()) {
                        fetchWorkspaceData(workspaceId)
                    } else {
                        Log.e("ProfileFragment", "No workspace ID found in access code")
                    }
                } else {
                    Log.e("ProfileFragment", "No user code found for access code: $accessCode")
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileFragment", "Database error: ${error.message}")
            }
        })
    }

    private fun fetchWorkspaceData(workspaceId: String) {
        Log.d("ProfileFragment", "Fetching workspace data for ID: $workspaceId")
        val workspaceRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("workspaces").child(workspaceId)
        workspaceRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    Log.d("ProfileFragment", "Workspace snapshot exists")
                    
                    // Debug: print all keys at top level
                    val keys = mutableListOf<String>()
                    snapshot.children.forEach { keys.add(it.key ?: "null") }
                    Log.d("ProfileFragment", "Workspace keys: ${keys.joinToString(", ")}")
                    
                    // Get building type and name
                    buildingType = snapshot.child("type").getValue(String::class.java) ?: ""
                    val buildingName = snapshot.child("name").getValue(String::class.java) ?: ""
                    Log.d("ProfileFragment", "Building type: '$buildingType', name: '$buildingName'")
                    
                    // Get address components
                    val addressSnapshot = snapshot.child("address")
                    Log.d("ProfileFragment", "Address exists: ${addressSnapshot.exists()}")
                    
                    val street = addressSnapshot.child("street").getValue(String::class.java) ?: ""
                    val addressLine2 = addressSnapshot.child("addressLine2").getValue(String::class.java) ?: ""
                    val city = addressSnapshot.child("city").getValue(String::class.java) ?: ""
                    val state = addressSnapshot.child("state").getValue(String::class.java) ?: ""
                    val zipCode = addressSnapshot.child("zipCode").getValue(String::class.java) ?: ""
                    
                    Log.d("ProfileFragment", "Address components: street='$street', city='$city', state='$state', zip='$zipCode'")
                    
                    // Build address string
                    val address = buildString {
                        append(street)
                        if (addressLine2.isNotEmpty()) {
                            append("\n$addressLine2")
                        }
                        append("\n$city, $state $zipCode")
                    }
                    
                    // Set building type information
                    when (buildingType) {
                        "Apartment" -> {
                            val apartmentInfo = snapshot.child("apartmentInfo")
                            val floorNum = apartmentInfo.child("floorNum").getValue(String::class.java) ?: ""
                            val apartmentName = apartmentInfo.child("apartmentName").getValue(String::class.java) ?: ""
                            profileBuildingType.text = "Type: Apartment\nComplex: $apartmentName\nFloor: $floorNum"
                        }
                        "House" -> {
                            profileBuildingType.text = "Type: House\nName: $buildingName"
                        }
                        "Hotel" -> {
                            profileBuildingType.text = "Type: Hotel\nName: $buildingName"
                        }
                        "Business" -> {
                            profileBuildingType.text = "Type: Business\nName: $buildingName"
                        }
                        else -> {
                            profileBuildingType.text = "Type: $buildingType"
                        }
                    }
                    
                    // Set address text
                    profileAddress.text = "Address: $address"
                    
                    // Get occupants count
                    val totOccupants = snapshot.child("totOccupants").getValue(Int::class.java) ?: 0
                    profileOccupantsCount.text = "Total Occupants: ${totOccupants + 1}" // +1 for owner
                    
                    // Get occupants
                    val occupantsData = ArrayList<Occupant>()
                    val occupantsSnapshot = snapshot.child("occupants")
                    for (occupantSnapshot in occupantsSnapshot.children) {
                        val id = occupantSnapshot.key ?: continue
                        val name = occupantSnapshot.child("name").getValue(String::class.java) ?: ""
                        val dob = occupantSnapshot.child("dob").getValue(String::class.java) ?: ""
                        val age = occupantSnapshot.child("age").getValue(String::class.java)?.toIntOrNull() ?: 0
                        
                        occupantsData.add(Occupant(id, name, dob, age))
                    }
                    occupants = occupantsData
                    displayOccupants()
                    
                    // Get pets
                    val totPets = snapshot.child("totPets").getValue(Int::class.java) ?: 0
                    val petsData = ArrayList<Pet>()
                    val petsSnapshot = snapshot.child("pets")
                    for (petSnapshot in petsSnapshot.children) {
                        val id = petSnapshot.key ?: continue
                        val name = petSnapshot.child("name").getValue(String::class.java) ?: ""
                        val color = petSnapshot.child("color").getValue(String::class.java) ?: ""
                        val breed = petSnapshot.child("breed").getValue(String::class.java) ?: ""
                        
                        petsData.add(Pet(id, name, color, breed))
                    }
                    pets = petsData
                    hasPets = pets.isNotEmpty()
                    
                    // Show pets section if needed
                    if (hasPets) {
                        petsCard.visibility = View.VISIBLE
                        profilePetsCount.text = "Total Pets: $totPets"
                        displayPets()
                    } else {
                        petsCard.visibility = View.GONE
                    }
                } else {
                    Log.e("ProfileFragment", "Workspace snapshot does not exist for ID: $workspaceId")
                    Toast.makeText(context, "No workspace data found", Toast.LENGTH_SHORT).show()
                }
            }
            
            override fun onCancelled(error: DatabaseError) {
                Log.e("ProfileFragment", "Database error: ${error.message}")
                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun displayOccupants() {
        occupantsLayout.removeAllViews()
        
        // First add the owner's information
        val ownerView = LayoutInflater.from(requireContext()).inflate(R.layout.item_occupant, occupantsLayout, false)
        val containerOwner = ownerView.findViewById<LinearLayout>(R.id.containerOccupant)
        
        // Modify orientation to horizontal for the container
        containerOwner.orientation = LinearLayout.HORIZONTAL
        
        // Create vertical layout for owner details
        val detailsLayout = LinearLayout(requireContext())
        detailsLayout.orientation = LinearLayout.VERTICAL
        detailsLayout.layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
        )
        
        // Remove existing TextViews from the container
        containerOwner.removeAllViews()
        
        // Create TextViews for owner details
        val tvName = TextView(requireContext())
        tvName.text = "Name: $userFirstName $userLastName (Owner)"
        tvName.setTextColor(resources.getColor(R.color.primary_text_color, null))
        tvName.textSize = 16f
        tvName.setTypeface(null, android.graphics.Typeface.BOLD)
        
        // Use the actual DOB and age from Firebase
        val tvDOB = TextView(requireContext())
        tvDOB.text = "Date of Birth: $userDOB"
        tvDOB.setTextColor(resources.getColor(R.color.secondary_text_color, null))
        tvDOB.textSize = 14f
        tvDOB.setPadding(0, 4, 0, 0)
        
        val tvAge = TextView(requireContext())
        tvAge.text = "Age: $userAge"
        tvAge.setTextColor(resources.getColor(R.color.secondary_text_color, null))
        tvAge.textSize = 14f
        tvAge.setPadding(0, 4, 0, 0)
        
        // Add TextViews to details layout
        detailsLayout.addView(tvName)
        detailsLayout.addView(tvDOB)
        detailsLayout.addView(tvAge)
        
        // Add details layout to container
        containerOwner.addView(detailsLayout)
        
        occupantsLayout.addView(ownerView)
        
        // Add other occupants
        occupants.forEach { occupant ->
            val occupantView = LayoutInflater.from(requireContext()).inflate(R.layout.item_occupant, occupantsLayout, false)
            val container = occupantView.findViewById<LinearLayout>(R.id.containerOccupant)
            
            // Modify orientation to horizontal for the container
            container.orientation = LinearLayout.HORIZONTAL
            
            // Create vertical layout for occupant details
            val detailsLayout = LinearLayout(requireContext())
            detailsLayout.orientation = LinearLayout.VERTICAL
            detailsLayout.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            
            // Remove existing TextViews from the container
            container.removeAllViews()
            
            // Create TextViews for occupant details
            val tvName = TextView(requireContext())
            tvName.text = "Name: ${occupant.name}"
            tvName.setTextColor(resources.getColor(R.color.primary_text_color, null))
            tvName.textSize = 16f
            tvName.setTypeface(null, android.graphics.Typeface.BOLD)
            
            val tvDOB = TextView(requireContext())
            tvDOB.text = "Date of Birth: ${occupant.dob}"
            tvDOB.setTextColor(resources.getColor(R.color.secondary_text_color, null))
            tvDOB.textSize = 14f
            tvDOB.setPadding(0, 4, 0, 0)
            
            val tvAge = TextView(requireContext())
            tvAge.text = "Age: ${occupant.age}"
            tvAge.setTextColor(resources.getColor(R.color.secondary_text_color, null))
            tvAge.textSize = 14f
            tvAge.setPadding(0, 4, 0, 0)
            
            // Add TextViews to details layout
            detailsLayout.addView(tvName)
            detailsLayout.addView(tvDOB)
            detailsLayout.addView(tvAge)
            
            // Add details layout to container
            container.addView(detailsLayout)
            
            // Add edit button for each occupant
            val editButton = ImageView(requireContext())
            editButton.setImageResource(android.R.drawable.ic_menu_edit)
            editButton.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                setMargins(16, 0, 0, 0)
            }
            
            // Set click listener for edit button
            editButton.setOnClickListener {
                showEditOccupantDialog(occupant)
            }
            
            // Add edit button to container
            container.addView(editButton)
            
            occupantsLayout.addView(occupantView)
        }
    }

    private fun displayPets() {
        petsLayout.removeAllViews()
        
        pets.forEach { pet ->
            val petView = LayoutInflater.from(requireContext()).inflate(R.layout.item_pet, petsLayout, false)
            val container = petView.findViewById<LinearLayout>(R.id.containerPet)
            
            // Modify orientation to horizontal for the container
            container.orientation = LinearLayout.HORIZONTAL
            
            // Create vertical layout for pet details
            val detailsLayout = LinearLayout(requireContext())
            detailsLayout.orientation = LinearLayout.VERTICAL
            detailsLayout.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            
            // Remove existing TextViews from the container
            container.removeAllViews()
            
            // Create TextViews for pet details
            val tvName = TextView(requireContext())
            tvName.text = "Name: ${pet.name}"
            tvName.setTextColor(resources.getColor(R.color.primary_text_color, null))
            tvName.textSize = 16f
            tvName.setTypeface(null, android.graphics.Typeface.BOLD)
            
            val tvColor = TextView(requireContext())
            tvColor.text = "Color: ${pet.color}"
            tvColor.setTextColor(resources.getColor(R.color.secondary_text_color, null))
            tvColor.textSize = 14f
            tvColor.setPadding(0, 4, 0, 0)
            
            val tvBreed = TextView(requireContext())
            tvBreed.text = "Breed: ${pet.breed}"
            tvBreed.setTextColor(resources.getColor(R.color.secondary_text_color, null))
            tvBreed.textSize = 14f
            tvBreed.setPadding(0, 4, 0, 0)
            
            // Add TextViews to details layout
            detailsLayout.addView(tvName)
            detailsLayout.addView(tvColor)
            detailsLayout.addView(tvBreed)
            
            // Add details layout to container
            container.addView(detailsLayout)
            
            // Add edit button for each pet
            val editButton = ImageView(requireContext())
            editButton.setImageResource(android.R.drawable.ic_menu_edit)
            editButton.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                setMargins(16, 0, 0, 0)
            }
            
            // Set click listener for edit button
            editButton.setOnClickListener {
                showEditPetDialog(pet)
            }
            
            // Add edit button to container
            container.addView(editButton)
            
            petsLayout.addView(petView)
        }
    }

    private fun loadProfilePicture(imageUrl: String) {
        Log.d("ProfileFragment", "Loading profile picture from URL: $imageUrl")
        try {
            // Convert gs:// URL to direct download URL with token
            val directUrl = "https://firebasestorage.googleapis.com/v0/b/pyro-sensor.firebasestorage.app/o/Rev_Socia_1.jpg?alt=media&token=ebd9fb46-14fb-42fd-970a-6c4b062fba6e"
            Log.d("ProfileFragment", "Direct URL: $directUrl")
            
            Glide.with(this)
                .load(directUrl)
                .circleCrop()
                .into(profilePicture)
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error loading image: ${e.message}")
        }
    }

    private fun setupEditButtons() {
        binding.btnEditPersonalInfo.setOnClickListener {
            showEditPersonalInfoDialog()
        }

        binding.btnEditBuildingInfo.setOnClickListener {
            showEditBuildingInfoDialog()
        }

        binding.btnEditOccupants.setOnClickListener {
            showAddOccupantDialog()
        }

        binding.btnEditPets.setOnClickListener {
            showAddPetDialog()
        }
    }

    private fun showEditPersonalInfoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_personal_info, null)
        val etFirstName = dialogView.findViewById<TextInputEditText>(R.id.etFirstName)
        val etLastName = dialogView.findViewById<TextInputEditText>(R.id.etLastName)

        // Pre-fill current values
        etFirstName.setText(userFirstName)
        etLastName.setText(userLastName)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Personal Information")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newFirstName = etFirstName.text.toString()
                val newLastName = etLastName.text.toString()

                if (newFirstName.isNotBlank() && newLastName.isNotBlank()) {
                    updatePersonalInfo(newFirstName, newLastName)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showEditBuildingInfoDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_building_info, null)
        val etAddress = dialogView.findViewById<TextInputEditText>(R.id.etAddress)
        val tilAddress = dialogView.findViewById<TextInputLayout>(R.id.tilAddress)
        val etAddressLine2 = dialogView.findViewById<TextInputEditText>(R.id.etAddressLine2)
        val etBuildingName = dialogView.findViewById<TextInputEditText>(R.id.etBuildingName)

        // Pre-fill current values
        etAddress.setText(buildAddressString())
        etBuildingName.setText(profileBuildingType.text.toString().substringAfter("Name: "))

        // Set up Places API for address search
        tilAddress.setEndIconOnClickListener {
            launchPlacesAutocomplete()
        }
        
        // Add click listener to the address field itself
        etAddress.setOnClickListener {
            launchPlacesAutocomplete()
        }

        // Set up address line 2 field
        etAddressLine2.hint = "Apartment, suite, unit, etc. (optional)"

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Building Information")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newAddress = etAddress.text.toString()
                val newAddressLine2 = etAddressLine2.text.toString()
                val newBuildingName = etBuildingName.text.toString()

                if (newAddress.isNotBlank() && newBuildingName.isNotBlank()) {
                    updateBuildingAddress(newAddress, newAddressLine2, newBuildingName)
                } else {
                    Toast.makeText(requireContext(), "Please fill in all required fields", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        // Store the dialog reference
        currentDialog = dialog

        dialog.show()
    }

    private fun updateBuildingAddress(newAddress: String, newAddressLine2: String, newBuildingName: String) {
        Log.d("ProfileFragment", "Original address: $newAddress")
        
        // Parse the address string from Places API
        val addressParts = newAddress.split(", ")
        Log.d("ProfileFragment", "Split address parts: ${addressParts.joinToString(", ")}")
        
        var street = ""
        var city = ""
        var state = ""
        var zipCode = ""
        
        if (addressParts.isNotEmpty()) {
            // First part is the street address
            street = addressParts[0]
            Log.d("ProfileFragment", "Street: $street")
            
            if (addressParts.size >= 3) {
                // Second part is the city
                city = addressParts[1]
                Log.d("ProfileFragment", "City: $city")
                
                // Third part contains state and ZIP code
                val stateZipPart = addressParts[2]
                Log.d("ProfileFragment", "State/ZIP part: $stateZipPart")
                
                // Split state and ZIP code
                val stateZipParts = stateZipPart.split(" ")
                if (stateZipParts.size >= 2) {
                    state = stateZipParts[0]
                    zipCode = stateZipParts[1]
                    Log.d("ProfileFragment", "State: $state, ZIP: $zipCode")
                }
            }
        }
        
        // Final check of all values before database update
        Log.d("ProfileFragment", "Final values - Street: '$street', City: '$city', State: '$state', ZIP: '$zipCode'")
        
        val workspaceRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("workspaces").child(workspaceId)
        val updates = mapOf(
            "name" to newBuildingName,
            "address/street" to street,
            "address/addressLine2" to newAddressLine2,
            "address/city" to city,
            "address/state" to state,
            "address/zipCode" to zipCode
        )

        Log.d("ProfileFragment", "Database updates: $updates")

        workspaceRef.updateChildren(updates)
            .addOnSuccessListener {
                // Build the display address string
                val displayAddress = buildString {
                    append(street)
                    if (newAddressLine2.isNotEmpty()) {
                        append("\n$newAddressLine2")
                    }
                    append("\n$city, $state $zipCode")
                }
                
                binding.profileAddress.text = "Address: $displayAddress"
                binding.profileBuildingType.text = "Type: $buildingType\nName: $newBuildingName"
                Toast.makeText(context, "Building information updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Database update failed: ${e.message}")
                Toast.makeText(context, "Failed to update building information: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun launchPlacesAutocomplete() {
        try {
            // Initialize Places API if not already initialized
            if (!Places.isInitialized()) {
                Places.initialize(requireContext(), getString(R.string.google_maps_key))
            }

            val fields = listOf(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG
            )
            
            val intent = Autocomplete.IntentBuilder(
                AutocompleteActivityMode.OVERLAY,
                fields
            ).build(requireContext())
            
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE)
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error launching Places API: ${e.message}")
            Toast.makeText(context, "Error launching Places API: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEditOccupantDialog(occupant: Occupant) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_occupant, null)
        val etOccupantName = dialogView.findViewById<TextInputEditText>(R.id.etOccupantName)
        val etDOB = dialogView.findViewById<TextInputEditText>(R.id.etDOB)
        val tilDOB = dialogView.findViewById<TextInputLayout>(R.id.tilDOB)

        // Pre-fill with occupant data
        etOccupantName.setText(occupant.name)
        etDOB.setText(occupant.dob)

        // Set up date picker
        tilDOB.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar)
        tilDOB.setEndIconOnClickListener {
            showDatePickerDialog(etDOB)
        }
        
        // Add click listener to the DOB field itself
        etDOB.setOnClickListener {
            showDatePickerDialog(etDOB)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Occupant Information")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newName = etOccupantName.text.toString()
                val newDOB = etDOB.text.toString()

                if (newName.isNotBlank() && newDOB.isNotBlank()) {
                    updateOccupantInfo(occupant.id, newName, newDOB)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showAddOccupantDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_occupant, null)
        val etOccupantName = dialogView.findViewById<TextInputEditText>(R.id.etOccupantName)
        val etDOB = dialogView.findViewById<TextInputEditText>(R.id.etDOB)
        val tilDOB = dialogView.findViewById<TextInputLayout>(R.id.tilDOB)

        // Set up date picker
        tilDOB.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar)
        tilDOB.setEndIconOnClickListener {
            showDatePickerDialog(etDOB)
        }
        
        // Add click listener to the DOB field itself
        etDOB.setOnClickListener {
            showDatePickerDialog(etDOB)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Occupant")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newName = etOccupantName.text.toString()
                val newDOB = etDOB.text.toString()

                if (newName.isNotBlank() && newDOB.isNotBlank()) {
                    addNewOccupant(newName, newDOB)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showAddPetDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_pet, null)
        val etPetName = dialogView.findViewById<TextInputEditText>(R.id.etPetName)
        val etPetColor = dialogView.findViewById<TextInputEditText>(R.id.etPetColor)
        val etPetBreed = dialogView.findViewById<TextInputEditText>(R.id.etPetBreed)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Pet")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newName = etPetName.text.toString()
                val newColor = etPetColor.text.toString()
                val newBreed = etPetBreed.text.toString()

                if (newName.isNotBlank() && newColor.isNotBlank() && newBreed.isNotBlank()) {
                    addNewPet(newName, newColor, newBreed)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun showEditPetDialog(pet: Pet) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_pet, null)
        val etPetName = dialogView.findViewById<TextInputEditText>(R.id.etPetName)
        val etPetColor = dialogView.findViewById<TextInputEditText>(R.id.etPetColor)
        val etPetBreed = dialogView.findViewById<TextInputEditText>(R.id.etPetBreed)

        // Pre-fill with pet data
        etPetName.setText(pet.name)
        etPetColor.setText(pet.color)
        etPetBreed.setText(pet.breed)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Edit Pet Information")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newName = etPetName.text.toString()
                val newColor = etPetColor.text.toString()
                val newBreed = etPetBreed.text.toString()

                if (newName.isNotBlank() && newColor.isNotBlank() && newBreed.isNotBlank()) {
                    updatePetInfo(pet.id, newName, newColor, newBreed)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialog.show()
    }

    private fun updatePersonalInfo(newFirstName: String, newLastName: String) {
        val userRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("userInfo")
        val updates = mapOf(
            "firstName" to newFirstName,
            "lastName" to newLastName
        )

        userRef.updateChildren(updates)
            .addOnSuccessListener {
                userFirstName = newFirstName
                userLastName = newLastName
                binding.profileName.text = "Name: $newFirstName $newLastName"
                // Refresh occupants display to update owner name
                displayOccupants()
                Toast.makeText(context, "Personal information updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update personal information: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateOccupantInfo(occupantId: String, newName: String, newDOB: String) {
        val occupantsRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("workspaces").child(workspaceId).child("occupants")
        val age = calculateAge(newDOB)

        val updates = mapOf(
            "$occupantId/name" to newName,
            "$occupantId/dob" to newDOB,
            "$occupantId/age" to age.toString()
        )

        occupantsRef.updateChildren(updates)
            .addOnSuccessListener {
                // Update local data
                val index = occupants.indexOfFirst { it.id == occupantId }
                if (index != -1) {
                    val updatedOccupants = occupants.toMutableList()
                    updatedOccupants[index] = Occupant(occupantId, newName, newDOB, age)
                    occupants = updatedOccupants
                }
                
                displayOccupants()
                Toast.makeText(context, "Occupant information updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update occupant information: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updatePetInfo(petId: String, newName: String, newColor: String, newBreed: String) {
        val petsRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("workspaces").child(workspaceId).child("pets")

        val updates = mapOf(
            "$petId/name" to newName,
            "$petId/color" to newColor,
            "$petId/breed" to newBreed
        )

        petsRef.updateChildren(updates)
            .addOnSuccessListener {
                // Update local data
                val index = pets.indexOfFirst { it.id == petId }
                if (index != -1) {
                    val updatedPets = pets.toMutableList()
                    updatedPets[index] = Pet(petId, newName, newColor, newBreed)
                    pets = updatedPets
                }
                
                displayPets()
                Toast.makeText(context, "Pet information updated successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update pet information: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePickerDialog(etDOB: TextInputEditText) {
        try {
            val calendar = Calendar.getInstance()
            
            // If there's an existing date, parse it
            val existingDate = etDOB.text.toString()
            if (existingDate.isNotEmpty()) {
                try {
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val date = dateFormat.parse(existingDate)
                    if (date != null) {
                        calendar.time = date
                    }
                } catch (e: Exception) {
                    Log.e("ProfileFragment", "Error parsing existing date: ${e.message}")
                }
            }
            
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            
            val datePickerDialog = DatePickerDialog(
                requireContext(),
                { _, selectedYear, selectedMonth, selectedDay ->
                    val selectedCalendar = Calendar.getInstance()
                    selectedCalendar.set(selectedYear, selectedMonth, selectedDay)
                    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    etDOB.setText(dateFormat.format(selectedCalendar.time))
                },
                year,
                month,
                day
            )
            
            // Set maximum date to today
            datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
            
            // Show the dialog
            datePickerDialog.show()
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error showing date picker: ${e.message}")
            Toast.makeText(context, "Error showing date picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun calculateAge(dob: String): Int {
        try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val birthDate = dateFormat.parse(dob) ?: return 0
            val birthCalendar = Calendar.getInstance()
            birthCalendar.time = birthDate
            
            val today = Calendar.getInstance()

            var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            return if (age < 0) 0 else age
        } catch (e: Exception) {
            Log.e("ProfileFragment", "Error calculating age: ${e.message}")
            return 0
        }
    }

    private fun addNewOccupant(name: String, dob: String) {
        val age = calculateAge(dob)
        
        // Generate a new occupant ID (occupant1, occupant2, etc.)
        val occupantId = "occupant${occupants.size + 1}"
        
        val workspaceRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("workspaces").child(workspaceId)
        val occupantRef = workspaceRef.child("occupants").child(occupantId)
        
        val newOccupant = mapOf(
            "name" to name,
            "dob" to dob,
            "age" to age.toString()
        )
        
        occupantRef.setValue(newOccupant)
            .addOnSuccessListener {
                // Update total occupants count
                workspaceRef.child("totOccupants").setValue(occupants.size + 1)
                
                // Update local data
                val updatedOccupants = occupants.toMutableList()
                updatedOccupants.add(Occupant(occupantId, name, dob, age))
                occupants = updatedOccupants
                
                // Update UI
                displayOccupants()
                profileOccupantsCount.text = "Total Occupants: ${occupants.size + 1}" // +1 for owner
                
                Toast.makeText(context, "New occupant added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to add new occupant: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addNewPet(name: String, color: String, breed: String) {
        // Generate a new pet ID (pet1, pet2, etc.)
        val petId = "pet${pets.size + 1}"
        
        val workspaceRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("workspaces").child(workspaceId)
        val petRef = workspaceRef.child("pets").child(petId)
        
        val newPet = mapOf(
            "name" to name,
            "color" to color,
            "breed" to breed
        )
        
        petRef.setValue(newPet)
            .addOnSuccessListener {
                // Update total pets count
                workspaceRef.child("totPets").setValue(pets.size + 1)
                
                // Update local data
                val updatedPets = pets.toMutableList()
                updatedPets.add(Pet(petId, name, color, breed))
                pets = updatedPets
                
                // Update UI
                if (!hasPets) {
                    hasPets = true
                    petsCard.visibility = View.VISIBLE
                }
                
                displayPets()
                profilePetsCount.text = "Total Pets: ${pets.size}"
                
                Toast.makeText(context, "New pet added successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to add new pet: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Add this method to extract the address from the TextView
    private fun buildAddressString(): String {
        // Extract address from the profileAddress TextView
        val addressText = profileAddress.text.toString()
        // Remove the "Address: " prefix if it exists
        return if (addressText.startsWith("Address: ")) {
            addressText.substring(9)
        } else {
            addressText
        }
    }

    private fun getWorkspaceId(): String? {
        return workspaceId.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1
        private const val TAG = "ProfileFragment"
    }
}

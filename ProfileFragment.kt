package com.example.pyrosensor

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.EditText
import androidx.core.content.FileProvider
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
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.common.api.Status
import android.app.AlertDialog


class ProfileFragment : Fragment() {

    // View binding and UI elements
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    
    // Profile UI components
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
    
    // User and workspace data
    private var userId: String = ""
    private var workspaceId: String = ""
    private var userFirstName: String = ""
    private var userLastName: String = ""
    private var buildingType: String = ""
    private var hasPets: Boolean = false
    
    // Data models for occupants and pets
    private var occupants: List<Occupant> = emptyList()
    private var pets: List<Pet> = emptyList()
    private var currentDialog: androidx.appcompat.app.AlertDialog? = null
    private var userDOB: String = ""
    private var userAge: Int = 0
    private var currentImageId: String = ""
    private var currentIsOccupant: Boolean = true
    private var currentPhotoPath: String? = null
    private var newOccupantImageUri: Uri? = null
    private var newPetImageUri: Uri? = null

    // Data class for occupant information
    data class Occupant(
        val id: String = "",
        var name: String = "",
        var dob: String = "",
        var age: Int = 0,
        var imageUrl: String? = null
    )

    // Data class for pet information
    data class Pet(
        val id: String = "",
        var name: String = "",
        var color: String = "",
        var breed: String = "",
        var imageUrl: String? = null
    )

    // Create and return the fragment's view
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Clean up view binding when fragment is destroyed
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Initialize views and fetch profile data
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupViews()
        setupEditButtons()
        fetchProfileData()
    }

    // Set up UI components and click listeners
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

        // Add click listener to profile picture
        profilePicture.setOnClickListener {
            showImageSelectionDialog()
        }
    }

    // Handle activity results for image selection and place autocomplete
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    data?.data?.let { uri ->
                        uploadProfilePicture(uri)
                    }
                }
                TAKE_PICTURE_REQUEST -> {
                    data?.extras?.get("data")?.let { bitmap ->
                        val uri = getImageUri(bitmap as Bitmap)
                        uploadProfilePicture(uri)
                    }
                }
                GALLERY_REQUEST -> {
                    data?.data?.let { uri ->
                        if (currentImageId == "new_occupant") {
                            newOccupantImageUri = uri
                            // Update the preview in the dialog
                            currentDialog?.findViewById<ImageView>(R.id.ivOccupantPreview)?.let { imageView ->
                                Glide.with(this)
                                    .load(uri)
                                    .circleCrop()
                                    .into(imageView)
                            }
                        } else if (currentImageId == "new_pet") {
                            newPetImageUri = uri
                            // Update the preview in the dialog
                            currentDialog?.findViewById<ImageView>(R.id.ivPetPreview)?.let { imageView ->
                                Glide.with(this)
                                    .load(uri)
                                    .circleCrop()
                                    .into(imageView)
                            }
                        } else {
                            uploadImage(uri, currentImageId, currentIsOccupant)
                        }
                    }
                }
                CAMERA_REQUEST -> {
                    currentPhotoPath?.let { path ->
                        val file = File(path)
                        val uri = FileProvider.getUriForFile(
                            requireContext(),
                            "${requireContext().packageName}.provider",
                            file
                        )
                        if (currentImageId == "new_occupant") {
                            newOccupantImageUri = uri
                            // Update the preview in the dialog
                            currentDialog?.findViewById<ImageView>(R.id.ivOccupantPreview)?.let { imageView ->
                                Glide.with(this)
                                    .load(uri)
                                    .circleCrop()
                                    .into(imageView)
                            }
                        } else if (currentImageId == "new_pet") {
                            newPetImageUri = uri
                            // Update the preview in the dialog
                            currentDialog?.findViewById<ImageView>(R.id.ivPetPreview)?.let { imageView ->
                                Glide.with(this)
                                    .load(uri)
                                    .circleCrop()
                                    .into(imageView)
                            }
                        } else {
                            uploadImage(uri, currentImageId, currentIsOccupant)
                        }
                    }
                }
                PLACE_AUTOCOMPLETE_REQUEST_CODE -> {
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
        }
    }

    private fun fetchProfileData() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e("ProfileFragment", "User not authenticated")
            return
        }

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

                    // Load profile picture from userInfo
                    val profilePictureUrl = userInfoSnapshot.child("profilePictureUrl").getValue(String::class.java)
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
                        val imageUrl = occupantSnapshot.child("imageUrl").getValue(String::class.java)
                        
                        occupantsData.add(Occupant(id, name, dob, age, imageUrl))
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
                        val imageUrl = petSnapshot.child("imageUrl").getValue(String::class.java)
                        
                        petsData.add(Pet(id, name, color, breed, imageUrl))
                    }
                    pets = petsData
                    hasPets = pets.isNotEmpty()
                    
                    // Always show pets section
                    petsCard.visibility = View.VISIBLE
                    profilePetsCount.text = "Total Pets: $totPets"
                    displayPets()
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
        val ownerView = LayoutInflater.from(requireContext()).inflate(R.layout.item_occupant_profile, occupantsLayout, false)
        val containerOwner = ownerView.findViewById<LinearLayout>(R.id.containerOccupant)
        containerOwner.gravity = android.view.Gravity.CENTER_VERTICAL
        
        // Create vertical layout for owner details
        val detailsLayout = LinearLayout(requireContext())
        detailsLayout.orientation = LinearLayout.VERTICAL
        detailsLayout.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        detailsLayout.gravity = android.view.Gravity.CENTER_VERTICAL
        
        // Create TextViews for owner details
        val tvName = TextView(requireContext())
        tvName.text = "Name: $userFirstName $userLastName (Owner)"
        tvName.setTextColor(resources.getColor(R.color.primary_text_color, null))
        tvName.textSize = 16f
        tvName.setTypeface(null, android.graphics.Typeface.BOLD)
        
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
            val occupantView = LayoutInflater.from(requireContext()).inflate(R.layout.item_occupant_profile, occupantsLayout, false)
            val container = occupantView.findViewById<LinearLayout>(R.id.containerOccupant)
            container.gravity = android.view.Gravity.CENTER_VERTICAL
            
            // Create horizontal layout for occupant details
            val horizontalLayout = LinearLayout(requireContext())
            horizontalLayout.orientation = LinearLayout.HORIZONTAL
            horizontalLayout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            horizontalLayout.gravity = android.view.Gravity.CENTER_VERTICAL
            horizontalLayout.setPadding(16, 16, 16, 16)
            
            // Create image view
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(100, 100).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(android.R.drawable.ic_menu_gallery)
                
                // Load occupant image if available
                occupant.imageUrl?.let { url ->
                    Glide.with(this@ProfileFragment)
                        .load(url)
                        .circleCrop()
                        .into(this)
                }
            }
            
            // Create vertical layout for occupant details
            val detailsLayout = LinearLayout(requireContext())
            detailsLayout.orientation = LinearLayout.VERTICAL
            detailsLayout.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            detailsLayout.gravity = android.view.Gravity.CENTER_VERTICAL
            detailsLayout.setPadding(16, 0, 16, 0)
            
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
            
            // Add image view and details layout to horizontal layout
            horizontalLayout.addView(imageView)
            horizontalLayout.addView(detailsLayout)
            
            // Add edit button
            val editButton = ImageView(requireContext())
            editButton.setImageResource(R.drawable.ic_edit)
            editButton.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            editButton.setOnClickListener {
                showEditOccupantDialog(occupant)
            }
            
            horizontalLayout.addView(editButton)
            
            // Add horizontal layout to container
            container.addView(horizontalLayout)
            
            occupantsLayout.addView(occupantView)
        }
    }

    private fun displayPets() {
        petsLayout.removeAllViews()
        
        pets.forEach { pet ->
            val petView = LayoutInflater.from(requireContext()).inflate(R.layout.item_pet_profile, petsLayout, false)
            val container = petView.findViewById<LinearLayout>(R.id.containerPet)
            container.gravity = android.view.Gravity.CENTER_VERTICAL
            
            // Create horizontal layout for pet details
            val horizontalLayout = LinearLayout(requireContext())
            horizontalLayout.orientation = LinearLayout.HORIZONTAL
            horizontalLayout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            horizontalLayout.gravity = android.view.Gravity.CENTER_VERTICAL
            horizontalLayout.setPadding(16, 16, 16, 16)
            
            // Create image view
            val imageView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(100, 100).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(android.R.drawable.ic_menu_gallery)
                
                // Load pet image if available
                pet.imageUrl?.let { url ->
                    Glide.with(this@ProfileFragment)
                        .load(url)
                        .circleCrop()
                        .into(this)
                }
            }
            
            // Create vertical layout for pet details
            val detailsLayout = LinearLayout(requireContext())
            detailsLayout.orientation = LinearLayout.VERTICAL
            detailsLayout.layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1.0f
            )
            detailsLayout.gravity = android.view.Gravity.CENTER_VERTICAL
            detailsLayout.setPadding(16, 0, 16, 0)
            
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
            tvBreed.text = "Pet Type: ${pet.breed}"
            tvBreed.setTextColor(resources.getColor(R.color.secondary_text_color, null))
            tvBreed.textSize = 14f
            tvBreed.setPadding(0, 4, 0, 0)
            
            // Add TextViews to details layout
            detailsLayout.addView(tvName)
            detailsLayout.addView(tvColor)
            detailsLayout.addView(tvBreed)
            
            // Add image view and details layout to horizontal layout
            horizontalLayout.addView(imageView)
            horizontalLayout.addView(detailsLayout)
            
            // Add edit button
            val editButton = ImageView(requireContext())
            editButton.setImageResource(R.drawable.ic_edit)
            editButton.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            
            editButton.setOnClickListener {
                showEditPetDialog(pet)
            }
            
            horizontalLayout.addView(editButton)
            
            // Add horizontal layout to container
            container.addView(horizontalLayout)
            
            petsLayout.addView(petView)
        }
    }

    private fun loadProfilePicture(imageUrl: String) {
        Log.d("ProfileFragment", "Loading profile picture from URL: $imageUrl")
        try {
            Glide.with(this)
                .load(imageUrl)
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
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Occupant")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        // Store the dialog reference
        currentDialog = dialog

        // Set up image preview
        val imageView = dialogView.findViewById<ImageView>(R.id.ivOccupantPreview)
        occupant.imageUrl?.let { url ->
            Glide.with(this)
                .load(url)
                .into(imageView)
        }

        val etOccupantName = dialogView.findViewById<TextInputEditText>(R.id.etOccupantName)
        val etDOB = dialogView.findViewById<TextInputEditText>(R.id.etDOB)
        val tilDOB = dialogView.findViewById<TextInputLayout>(R.id.tilDOB)

        // Set up date picker
        tilDOB.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar)
        tilDOB.setEndIconOnClickListener {
            showDatePickerDialog(etDOB)
        }
        
        // Make DOB field read-only and add click listener
        etDOB.isFocusable = false
        etDOB.isClickable = true
        etDOB.setOnClickListener {
            showDatePickerDialog(etDOB)
        }

        etOccupantName.setText(occupant.name)
        etDOB.setText(occupant.dob)

        dialogView.findViewById<Button>(R.id.btnChangeImage).setOnClickListener {
            currentImageId = occupant.id
            currentIsOccupant = true
            showImageSelectionDialog(occupant.id, true)
        }

        // Set up delete button
        dialogView.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Occupant")
                .setMessage("Are you sure you want to delete this occupant?")
                .setPositiveButton("Delete") { _, _ ->
                    deleteOccupant(occupant.id)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val name = etOccupantName.text.toString()
                val dob = etDOB.text.toString()

                if (name.isBlank()) {
                    dialogView.findViewById<TextInputLayout>(R.id.tilName).error = "Name is required"
                    return@setOnClickListener
                }

                updateOccupantInfo(occupant.id, name, dob)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showAddOccupantDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_occupant, null)
        val etOccupantName = dialogView.findViewById<TextInputEditText>(R.id.etOccupantName)
        val etDOB = dialogView.findViewById<TextInputEditText>(R.id.etDOB)
        val tilDOB = dialogView.findViewById<TextInputLayout>(R.id.tilDOB)
        val imageView = dialogView.findViewById<ImageView>(R.id.ivOccupantPreview)

        // Set up date picker
        tilDOB.setEndIconDrawable(android.R.drawable.ic_menu_my_calendar)
        tilDOB.setEndIconOnClickListener {
            showDatePickerDialog(etDOB)
        }
        
        // Make DOB field read-only and add click listener
        etDOB.isFocusable = false
        etDOB.isClickable = true
        etDOB.setOnClickListener {
            showDatePickerDialog(etDOB)
        }

        // Set up image selection
        dialogView.findViewById<Button>(R.id.btnChangeImage).setOnClickListener {
            currentImageId = "new_occupant"
            currentIsOccupant = true
            showImageSelectionDialog("new_occupant", true)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Occupant")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newName = etOccupantName.text.toString()
                val newDOB = etDOB.text.toString()

                if (newName.isNotBlank() && newDOB.isNotBlank()) {
                    addNewOccupant(newName, newDOB, newOccupantImageUri)
                    newOccupantImageUri = null
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                newOccupantImageUri = null
                dialog.dismiss()
            }
            .create()

        // Store the dialog reference
        currentDialog = dialog

        dialog.show()
    }

    private fun showAddPetDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_pet, null)
        val etPetName = dialogView.findViewById<TextInputEditText>(R.id.etPetName)
        val etPetColor = dialogView.findViewById<TextInputEditText>(R.id.etPetColor)
        val etPetBreed = dialogView.findViewById<TextInputEditText>(R.id.etPetBreed)
        val imageView = dialogView.findViewById<ImageView>(R.id.ivPetPreview)

        // Set up image selection
        dialogView.findViewById<Button>(R.id.btnChangeImage).setOnClickListener {
            currentImageId = "new_pet"
            currentIsOccupant = false
            showImageSelectionDialog("new_pet", false)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Add New Pet")
            .setView(dialogView)
            .setPositiveButton("Save") { dialog, _ ->
                val newName = etPetName.text.toString()
                val newColor = etPetColor.text.toString()
                val newBreed = etPetBreed.text.toString()

                if (newName.isNotBlank() && newColor.isNotBlank() && newBreed.isNotBlank()) {
                    addNewPet(newName, newColor, newBreed, newPetImageUri)
                    newPetImageUri = null
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                newPetImageUri = null
                dialog.dismiss()
            }
            .create()

        // Store the dialog reference
        currentDialog = dialog

        dialog.show()
    }

    private fun showEditPetDialog(pet: Pet) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_pet, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Edit Pet")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        // Store the dialog reference
        currentDialog = dialog

        // Set up image preview
        val imageView = dialogView.findViewById<ImageView>(R.id.ivPetPreview)
        pet.imageUrl?.let { url ->
            Glide.with(this)
                .load(url)
                .into(imageView)
        }

        dialogView.findViewById<TextInputEditText>(R.id.etPetName).setText(pet.name)
        dialogView.findViewById<TextInputEditText>(R.id.etPetColor).setText(pet.color)
        dialogView.findViewById<TextInputEditText>(R.id.etPetBreed).setText(pet.breed)

        dialogView.findViewById<Button>(R.id.btnChangeImage).setOnClickListener {
            currentImageId = pet.id
            currentIsOccupant = false
            showImageSelectionDialog(pet.id, false)
        }

        // Set up delete button
        dialogView.findViewById<Button>(R.id.btnDelete).setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete Pet")
                .setMessage("Are you sure you want to delete this pet?")
                .setPositiveButton("Delete") { _, _ ->
                    deletePet(pet.id)
                    dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val name = dialogView.findViewById<TextInputEditText>(R.id.etPetName).text.toString()
                val color = dialogView.findViewById<TextInputEditText>(R.id.etPetColor).text.toString()
                val breed = dialogView.findViewById<TextInputEditText>(R.id.etPetBreed).text.toString()

                if (name.isBlank()) {
                    dialogView.findViewById<TextInputLayout>(R.id.tilName).error = "Name is required"
                    return@setOnClickListener
                }

                updatePetInfo(pet.id, name, color, breed)
                dialog.dismiss()
            }
        }

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

        // Find the existing occupant to preserve the image URL
        val existingOccupant = occupants.find { it.id == occupantId }
        val imageUrl = existingOccupant?.imageUrl

        val updates = mapOf(
            "$occupantId/name" to newName,
            "$occupantId/dob" to newDOB,
            "$occupantId/age" to age.toString(),
            "$occupantId/imageUrl" to (imageUrl ?: "")
        )

        occupantsRef.updateChildren(updates)
            .addOnSuccessListener {
                // Update local data
                val index = occupants.indexOfFirst { it.id == occupantId }
                if (index != -1) {
                    val updatedOccupants = occupants.toMutableList()
                    updatedOccupants[index] = Occupant(occupantId, newName, newDOB, age, imageUrl)
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

        // Find the existing pet to preserve the image URL
        val existingPet = pets.find { it.id == petId }
        val imageUrl = existingPet?.imageUrl

        val updates = mapOf(
            "$petId/name" to newName,
            "$petId/color" to newColor,
            "$petId/breed" to newBreed,
            "$petId/imageUrl" to (imageUrl ?: "")
        )

        petsRef.updateChildren(updates)
            .addOnSuccessListener {
                // Update local data
                val index = pets.indexOfFirst { it.id == petId }
                if (index != -1) {
                    val updatedPets = pets.toMutableList()
                    updatedPets[index] = Pet(petId, newName, newColor, newBreed, imageUrl)
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
                    val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
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
                    val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
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
            val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
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

    private fun addNewOccupant(name: String, dob: String, imageUri: Uri?) {
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
                // If there's an image, upload it
                imageUri?.let { uri ->
                    uploadImage(uri, occupantId, true)
                }
                
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

    private fun addNewPet(name: String, color: String, breed: String, imageUri: Uri?) {
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
                // If there's an image, upload it
                imageUri?.let { uri ->
                    uploadImage(uri, petId, false)
                }
                
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

    private fun showImageSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Change Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                    2 -> { /* Cancel, do nothing */ }
                }
            }
            .show()
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun openCamera() {
        val intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(intent, TAKE_PICTURE_REQUEST)
    }

    private fun getImageUri(bitmap: Bitmap): Uri {
        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(requireContext().contentResolver, bitmap, "Title", null)
        return Uri.parse(path)
    }

    private fun uploadProfilePicture(uri: Uri) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Delete old profile picture if exists
        val oldUrl = profilePicture.tag as? String
        if (!oldUrl.isNullOrEmpty()) {
            val oldRef = storage.getReferenceFromUrl(oldUrl)
            oldRef.delete().addOnSuccessListener {
                Log.d(TAG, "Old profile picture deleted")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error deleting old profile picture: ${e.message}")
            }
        }

        // Upload new profile picture
        val storageRef = storage.reference.child("profile_pictures/${currentUser.uid}.jpg")
        val uploadTask = storageRef.putFile(uri)

        uploadTask.addOnSuccessListener { taskSnapshot ->
            taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUrl ->
                // Update profile picture URL in database
                val userRef = FirebaseDatabase.getInstance().reference
                    .child("users")
                    .child(currentUser.uid)
                    .child("userInfo")
                    .child("profilePictureUrl")

                userRef.setValue(downloadUrl.toString())
                    .addOnSuccessListener {
                        // Update UI
                        profilePicture.tag = downloadUrl.toString()
                        loadProfilePicture(downloadUrl.toString())
                        Toast.makeText(context, "Profile picture updated successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Failed to update profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
        }.addOnFailureListener { e ->
            Toast.makeText(context, "Failed to upload profile picture: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showImageSelectionDialog(id: String, isOccupant: Boolean) {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera(id, isOccupant)
                    1 -> openGallery(id, isOccupant)
                }
            }
            .show()
    }

    private fun openGallery(id: String, isOccupant: Boolean) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
        }
        startActivityForResult(intent, GALLERY_REQUEST)
    }

    private fun openCamera(id: String, isOccupant: Boolean) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            val photoFile = createImageFile()
            if (photoFile != null) {
                val photoURI = FileProvider.getUriForFile(
                    requireContext(),
                    "${requireContext().packageName}.provider",
                    photoFile
                )
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                startActivityForResult(intent, CAMERA_REQUEST)
            }
        }
    }

    private fun createImageFile(): File? {
        // Implementation of createImageFile method
        return null
    }

    private fun uploadImage(uri: Uri, id: String, isOccupant: Boolean) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            showMessage("User not authenticated")
            return
        }

        val storageRef = FirebaseStorage.getInstance().reference
        val path = if (isOccupant) 
            "users/${currentUser.uid}/occupants/$id.jpg" 
        else 
            "users/${currentUser.uid}/pets/$id.jpg"
        
        val imageRef = storageRef.child(path)

        imageRef.putFile(uri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { downloadUri ->
                    if (isOccupant) {
                        updateOccupantImage(id, downloadUri.toString())
                    } else {
                        updatePetImage(id, downloadUri.toString())
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error uploading image: ${e.message}")
                showMessage("Failed to upload image")
            }
    }

    private fun updateOccupantImage(id: String, imageUrl: String) {
        val db = FirebaseDatabase.getInstance().reference
        val occupantRef = db.child("users").child(userId).child("workspaces").child(workspaceId).child("occupants").child(id)
        val updates = mapOf("imageUrl" to imageUrl)
        
        occupantRef.updateChildren(updates)
            .addOnSuccessListener {
                showMessage("Image updated successfully")
                // Update the preview in the dialog
                currentDialog?.findViewById<ImageView>(R.id.ivOccupantPreview)?.let { imageView ->
                    Glide.with(this)
                        .load(imageUrl)
                        .into(imageView)
                }
                fetchProfileData()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating occupant image: ${e.message}")
                showMessage("Failed to update image")
            }
    }

    private fun updatePetImage(id: String, imageUrl: String) {
        val db = FirebaseDatabase.getInstance().reference
        val petRef = db.child("users").child(userId).child("workspaces").child(workspaceId).child("pets").child(id)
        val updates = mapOf("imageUrl" to imageUrl)
        
        petRef.updateChildren(updates)
            .addOnSuccessListener {
                showMessage("Image updated successfully")
                // Update the preview in the dialog
                currentDialog?.findViewById<ImageView>(R.id.ivPetPreview)?.let { imageView ->
                    Glide.with(this)
                        .load(imageUrl)
                        .into(imageView)
                }
                fetchProfileData()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating pet image: ${e.message}")
                showMessage("Failed to update image")
            }
    }

    private fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun deleteOccupant(occupantId: String) {
        val workspaceRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("workspaces").child(workspaceId)
        val occupantRef = workspaceRef.child("occupants").child(occupantId)
        
        // Delete the occupant
        occupantRef.removeValue()
            .addOnSuccessListener {
                // Update total occupants count
                workspaceRef.child("totOccupants").get().addOnSuccessListener { snapshot ->
                    val currentCount = snapshot.getValue(Int::class.java) ?: 0
                    workspaceRef.child("totOccupants").setValue(currentCount - 1)
                        .addOnSuccessListener {
                            // Update local data
                            occupants = occupants.filter { it.id != occupantId }
                            displayOccupants()
                            profileOccupantsCount.text = "Total Occupants: ${occupants.size + 1}" // +1 for owner
                            Toast.makeText(context, "Occupant deleted successfully", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to delete occupant: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deletePet(petId: String) {
        val workspaceRef = FirebaseDatabase.getInstance().reference.child("users").child(userId).child("workspaces").child(workspaceId)
        val petRef = workspaceRef.child("pets").child(petId)
        
        // Delete the pet
        petRef.removeValue()
            .addOnSuccessListener {
                // Update total pets count
                workspaceRef.child("totPets").get().addOnSuccessListener { snapshot ->
                    val currentCount = snapshot.getValue(Int::class.java) ?: 0
                    workspaceRef.child("totPets").setValue(currentCount - 1)
                        .addOnSuccessListener {
                            // Update local data
                            pets = pets.filter { it.id != petId }
                            if (pets.isEmpty()) {
                                hasPets = false
                                petsCard.visibility = View.GONE
                            }
                            displayPets()
                            profilePetsCount.text = "Total Pets: ${pets.size}"
                            Toast.makeText(context, "Pet deleted successfully", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to delete pet: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    companion object {
        private const val PLACE_AUTOCOMPLETE_REQUEST_CODE = 1
        private const val PICK_IMAGE_REQUEST = 2
        private const val TAKE_PICTURE_REQUEST = 3
        private const val GALLERY_REQUEST = 4
        private const val CAMERA_REQUEST = 5
        private const val TAG = "ProfileFragment"
    }
}

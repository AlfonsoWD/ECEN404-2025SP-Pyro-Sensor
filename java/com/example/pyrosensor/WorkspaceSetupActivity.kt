package com.example.pyrosensor

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.pyrosensor.databinding.ActivityWorkspaceSetupBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color
import com.google.android.material.snackbar.Snackbar
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.gms.common.api.Status

//TODO: Adding images for occupants and pets to firebase
//TODO: Make Spinners look better

class WorkspaceSetupActivity : AppCompatActivity() { //activity for setting up a workspace

    private lateinit var binding: ActivityWorkspaceSetupBinding //binding for the layout views
    private val database = FirebaseDatabase.getInstance().reference //reference to firebase database
    private val accessCodeManager = AccessCodeManager()
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    private var currentPhotoPath: String? = null
    private val PICK_IMAGE_REQUEST = 1
    private val CAMERA_REQUEST = 2
    private val PERMISSION_REQUEST_CODE = 3
    private val occupantImageUris = mutableMapOf<Int, Uri>()
    private val petImageUris = mutableMapOf<Int, Uri>()
    
    // Store the selected place details
    private var selectedPlace: Place? = null

    private fun showMessage(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.view
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        
        textView.setTextColor(if (isError) Color.RED else Color.BLACK)
        snackbarView.setBackgroundColor(Color.WHITE)
        textView.textSize = 16f
        
        val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
        params.setMargins(16, 16, 16, 16)
        snackbarView.layoutParams = params
        
        snackbarView.setPadding(24, 16, 24, 16)
        textView.maxLines = 10
        textView.isSingleLine = false
        
        snackbar.show()
    }

    private fun calculateAge(dob: String): Int {
        try {
            val birthDate = dateFormat.parse(dob)
            val today = Calendar.getInstance()
            val birth = Calendar.getInstance()
            birth.time = birthDate

            var age = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            return age
        } catch (e: Exception) {
            Log.e("WorkspaceSetupActivity", "Error calculating age: ${e.message}")
            return 0
        }
    }

    private fun updateAgeDisplay(dob: String, ageTextView: TextView) {
        val age = calculateAge(dob)
        ageTextView.text = "Age: $age"
    }

    override fun onCreate(savedInstanceState: Bundle?) { //called when the activity is created
        super.onCreate(savedInstanceState) //initializes the activity
        binding = ActivityWorkspaceSetupBinding.inflate(layoutInflater) //sets up view binding
        setContentView(binding.root) //sets the root layout for the activity

        // Initialize Places SDK
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, "AIzaSyCTc7r5aqdkqxYmY1uyt_lNyGrDvUreN44")
        }

        setupPlacesAutocomplete()
        setupWorkspaceTypeSpinner() //initializes workspace type spinner functionality
        setupPetSwitch() //initializes pet switch functionality
        setupOccupantsSpinner()
        setupPetsSpinner()
        setupSubmitButton() //initializes submit button functionality
    }

    private fun setupPlacesAutocomplete() {
        // Initialize the AutocompleteSupportFragment
        val autocompleteFragment = supportFragmentManager.findFragmentById(R.id.autocomplete_fragment)
                as? AutocompleteSupportFragment

        // Hide the fragment's view
        autocompleteFragment?.view?.visibility = View.GONE

        // Specify the types of place data to return
        autocompleteFragment?.setPlaceFields(listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.LAT_LNG,
            Place.Field.ADDRESS_COMPONENTS
        ))

        // Set up a PlaceSelectionListener to handle the response
        autocompleteFragment?.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                selectedPlace = place
                // Extract just the street address (part before the first comma)
                val fullAddress = place.address ?: ""
                val streetAddress = fullAddress.split(",").firstOrNull()?.trim() ?: fullAddress
                binding.etAddress.setText(streetAddress)
                
                // Show business name field for business type workspaces
                if (binding.spinnerWorkspaceType.selectedItem.toString() == "Business") {
                    binding.businessNameLayout.visibility = View.VISIBLE
                    binding.etBusinessName.setText(place.name)
                }
            }

            override fun onError(status: Status) {
                Log.e(TAG, "An error occurred: $status")
                showMessage("Error selecting place: ${status.statusMessage}", true)
            }
        })

        // Set up click listener for the address field
        binding.etAddress.setOnClickListener {
            try {
                autocompleteFragment?.let { fragment ->
                    // Trigger the Places Autocomplete dialog
                    fragment.requireView().findViewById<View>(com.google.android.libraries.places.R.id.places_autocomplete_search_button)?.performClick()
                } ?: run {
                    showMessage("Places Autocomplete is not available", true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error launching Places Autocomplete: ${e.message}")
                showMessage("Error launching address selection", true)
            }
        }
    }

    private fun setupWorkspaceTypeSpinner() {
        val workspaceTypes = arrayOf("House", "Apartment", "Hotel", "Business")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, workspaceTypes)
        binding.spinnerWorkspaceType.adapter = adapter

        binding.spinnerWorkspaceType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedType = workspaceTypes[position]
                when (selectedType) {
                    "House" -> {
                        binding.tvExpectedOccupants.visibility = View.VISIBLE
                        binding.spinnerExpectedOccupants.visibility = View.VISIBLE
                        binding.switchPets.visibility = View.VISIBLE
                        binding.etWorkspaceName.hint = "Home Name (EX. John's House)"
                        binding.layoutApartmentFields.visibility = View.GONE
                    }
                    "Apartment" -> {
                        binding.tvExpectedOccupants.visibility = View.VISIBLE
                        binding.spinnerExpectedOccupants.visibility = View.VISIBLE
                        binding.switchPets.visibility = View.VISIBLE
                        binding.etWorkspaceName.hint = "Apartment Name (EX. John's Apartment)"
                        binding.layoutApartmentFields.visibility = View.VISIBLE
                    }
                    "Hotel" -> {
                        binding.tvExpectedOccupants.visibility = View.GONE
                        binding.spinnerExpectedOccupants.visibility = View.GONE
                        binding.switchPets.visibility = View.GONE
                        binding.etWorkspaceName.hint = "Hotel's Name (EX. Marriot Las Vegas)"
                        binding.layoutApartmentFields.visibility = View.GONE
                    }
                    "Business" -> {
                        binding.tvExpectedOccupants.visibility = View.GONE
                        binding.spinnerExpectedOccupants.visibility = View.GONE
                        binding.switchPets.visibility = View.GONE
                        binding.etWorkspaceName.hint = "Business's Name (EX. Target)"
                        binding.layoutApartmentFields.visibility = View.GONE
                    }
                }
                binding.layoutOccupantsDetails.removeAllViews()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun setupPetSwitch() { //handles logic for the pet switch
        binding.switchPets.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvExpectedPets.visibility = View.VISIBLE
                binding.spinnerExpectedPets.visibility = View.VISIBLE
                // Trigger the spinner's onItemSelected to show fields for the current selection
                binding.spinnerExpectedPets.performClick()
            } else {
                binding.tvExpectedPets.visibility = View.GONE
                binding.spinnerExpectedPets.visibility = View.GONE
                binding.layoutPetsDetails.removeAllViews()
            }
        }
    }

    private fun setupOccupantsSpinner() {
        binding.spinnerExpectedOccupants.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val count = (position + 1).toString().toInt()
                if (count > 1) {
                    updateOccupantFields(count - 1) // Subtract 1 because the first occupant is the user
                } else {
                    binding.layoutOccupantsDetails.removeAllViews()
                    binding.layoutOccupantsDetails.visibility = View.GONE
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                binding.layoutOccupantsDetails.removeAllViews()
                binding.layoutOccupantsDetails.visibility = View.GONE
            }
        }
    }

    private fun setupPetsSpinner() {
        binding.spinnerExpectedPets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val count = (position + 1).toString().toInt()
                updatePetFields(count)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                binding.layoutPetsDetails.removeAllViews()
            }
        }
    }

    private fun updateOccupantFields(count: Int) {
        binding.layoutOccupantsDetails.removeAllViews()
        binding.layoutOccupantsDetails.visibility = View.VISIBLE
        
        for (i in 0 until count) {
            val occupantLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }

            // Add name field
            occupantLayout.addView(EditText(this).apply {
                hint = "Occupant ${i + 1} Name"
                inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Add date of birth field
            val dobField = EditText(this).apply {
                hint = "Occupant ${i + 1} Date of Birth"
                inputType = android.text.InputType.TYPE_NULL
                isFocusable = false
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // Add age display
            val ageTextView = TextView(this).apply {
                text = "Age: "
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 4, 0, 8)
                }
            }

            // Add image view and button
            val imageLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(100, 100)
                setImageResource(android.R.drawable.ic_menu_gallery)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val selectImageButton = Button(this).apply {
                text = "Select Photo"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 0, 0)
                }
                setOnClickListener {
                    occupantImageUris[i]?.let { uri ->
                        imageView.setImageURI(uri)
                    }
                    showImageSelectionDialog(i, true)
                }
            }

            imageLayout.addView(imageView)
            imageLayout.addView(selectImageButton)

            // Set up date picker
            dobField.setOnClickListener {
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                DatePickerDialog(
                    this,
                    { _, selectedYear, selectedMonth, selectedDay ->
                        calendar.set(selectedYear, selectedMonth, selectedDay)
                        val formattedDate = dateFormat.format(calendar.time)
                        dobField.setText(formattedDate)
                        updateAgeDisplay(formattedDate, ageTextView)
                    },
                    year,
                    month,
                    day
                ).apply {
                    datePicker.maxDate = System.currentTimeMillis()
                    calendar.add(Calendar.YEAR, -100)
                    datePicker.minDate = calendar.timeInMillis
                    show()
                }
            }

            occupantLayout.addView(dobField)
            occupantLayout.addView(ageTextView)
            occupantLayout.addView(imageLayout)
            binding.layoutOccupantsDetails.addView(occupantLayout)
        }
    }

    private fun updatePetFields(count: Int) {
        binding.layoutPetsDetails.removeAllViews()
        binding.layoutPetsDetails.visibility = View.VISIBLE
        
        for (i in 0 until count) {
            val petLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }

            // Add name field
            petLayout.addView(EditText(this).apply {
                hint = "Pet ${i + 1} Name"
                inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Add color field
            petLayout.addView(EditText(this).apply {
                hint = "Pet ${i + 1} Color"
                inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Add breed field
            petLayout.addView(EditText(this).apply {
                hint = "Pet ${i + 1} Breed"
                inputType = android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            })

            // Add image view and button
            val imageLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(100, 100)
                setImageResource(android.R.drawable.ic_menu_gallery)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            val selectImageButton = Button(this).apply {
                text = "Select Photo"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 0, 0)
                }
                setOnClickListener {
                    petImageUris[i]?.let { uri ->
                        imageView.setImageURI(uri)
                    }
                    showImageSelectionDialog(i, false)
                }
            }

            imageLayout.addView(imageView)
            imageLayout.addView(selectImageButton)

            petLayout.addView(imageLayout)
            binding.layoutPetsDetails.addView(petLayout)
        }
    }

    private fun showImageSelectionDialog(index: Int, isOccupant: Boolean) {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Photo")
            .setItems(options) { _, item ->
                when (item) {
                    0 -> {
                        if (checkCameraPermission()) {
                            openCamera(index, isOccupant)
                        }
                    }
                    1 -> {
                        if (checkStoragePermission()) {
                            openGallery(index, isOccupant)
                        }
                    }
                }
            }
            .show()
    }

    private fun checkCameraPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.CAMERA),
                PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    private fun checkStoragePermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE)
            return false
        }
        return true
    }

    private fun openCamera(index: Int, isOccupant: Boolean) {
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val photoFile = createImageFile()
            photoFile?.let { file ->
                val photoUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                currentPhotoPath = file.absolutePath
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                intent.putExtra("index", index)
                intent.putExtra("isOccupant", isOccupant)
                startActivityForResult(intent, CAMERA_REQUEST)
            }
        } catch (e: Exception) {
            showMessage("Error opening camera: ${e.message}", true)
        }
    }

    private fun openGallery(index: Int, isOccupant: Boolean) {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.putExtra("index", index)
        intent.putExtra("isOccupant", isOccupant)
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun createImageFile(): File? {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            return File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
            )
        } catch (e: Exception) {
            showMessage("Error creating image file: ${e.message}", true)
            return null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    val index = data?.getIntExtra("index", -1) ?: -1
                    val isOccupant = data?.getBooleanExtra("isOccupant", true) ?: true
                    val uri = data?.data
                    if (uri != null && index != -1) {
                        if (isOccupant) {
                            occupantImageUris[index] = uri
                        } else {
                            petImageUris[index] = uri
                        }
                        updateImageViews()
                    }
                }
                CAMERA_REQUEST -> {
                    currentPhotoPath?.let { path ->
                        val uri = Uri.fromFile(File(path))
                        val index = data?.getIntExtra("index", -1) ?: -1
                        val isOccupant = data?.getBooleanExtra("isOccupant", true) ?: true
                        if (index != -1) {
                            if (isOccupant) {
                                occupantImageUris[index] = uri
                            } else {
                                petImageUris[index] = uri
                            }
                            updateImageViews()
                        }
                    }
                }
            }
        }
    }

    private fun updateImageViews() {
        // Update occupant images
        for (i in 0 until binding.layoutOccupantsDetails.childCount) {
            val occupantLayout = binding.layoutOccupantsDetails.getChildAt(i) as LinearLayout
            val imageLayout = occupantLayout.getChildAt(2) as LinearLayout
            val imageView = imageLayout.getChildAt(0) as ImageView
            occupantImageUris[i]?.let { uri ->
                imageView.setImageURI(uri)
            }
        }

        // Update pet images
        for (i in 0 until binding.layoutPetsDetails.childCount) {
            val petLayout = binding.layoutPetsDetails.getChildAt(i) as LinearLayout
            val imageLayout = petLayout.getChildAt(3) as LinearLayout
            val imageView = imageLayout.getChildAt(0) as ImageView
            petImageUris[i]?.let { uri ->
                imageView.setImageURI(uri)
            }
        }
    }

    private fun collectOccupantsData(): Map<String, Map<String, String>> {
        val occupants = mutableMapOf<String, Map<String, String>>()
        if (binding.spinnerWorkspaceType.selectedItem.toString() in listOf("House", "Apartment")) {
            for (i in 0 until binding.layoutOccupantsDetails.childCount) {
                val occupantLayout = binding.layoutOccupantsDetails.getChildAt(i) as LinearLayout
                val name = (occupantLayout.getChildAt(0) as EditText).text.toString()
                val dob = (occupantLayout.getChildAt(1) as EditText).text.toString()
                if (name.isNotEmpty() && dob.isNotEmpty()) {
                    val age = calculateAge(dob)
                    occupants["occupant${i + 1}"] = mapOf(
                        "name" to name,
                        "dob" to dob,
                        "age" to age.toString()
                    )
                }
            }
        }
        return occupants
    }

    private fun collectPetsData(): Map<String, Map<String, String>> {
        val pets = mutableMapOf<String, Map<String, String>>()
        if (binding.switchPets.isChecked) {
            for (i in 0 until binding.layoutPetsDetails.childCount) {
                val petLayout = binding.layoutPetsDetails.getChildAt(i) as LinearLayout
                val name = (petLayout.getChildAt(0) as EditText).text.toString()
                val color = (petLayout.getChildAt(1) as EditText).text.toString()
                val breed = (petLayout.getChildAt(2) as EditText).text.toString()
                if (name.isNotEmpty() && color.isNotEmpty() && breed.isNotEmpty()) {
                    pets["pet${i + 1}"] = mapOf(
                        "name" to name,
                        "color" to color,
                        "breed" to breed
                    )
                }
            }
        }
        return pets
    }

    private fun setupSubmitButton() {
        binding.btnSubmit.setOnClickListener {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                showMessage("User not authenticated", true)
                return@setOnClickListener
            }

            if (selectedPlace == null) {
                showMessage("Please select a valid address", true)
                return@setOnClickListener
            }

            val uid = currentUser.uid
            val workspaceType = binding.spinnerWorkspaceType.selectedItem.toString()
            val workspaceName = binding.etWorkspaceName.text.toString()
            val apartmentSuite = binding.etApartmentSuite.text.toString()
            val businessName = binding.etBusinessName.text.toString()

            // Add apartment-specific fields
            val apartmentComplexName = binding.etApartmentComplexName.text.toString()
            val floorNumber = binding.etFloorNumber.text.toString()

            if (workspaceName.isEmpty()) {
                showMessage("Please enter a workspace name", true)
                return@setOnClickListener
            }

            // Validate apartment-specific fields if apartment is selected
            if (workspaceType == "Apartment" && (apartmentComplexName.isEmpty() || floorNumber.isEmpty())) {
                showMessage("Please fill in apartment complex name and floor number", true)
                return@setOnClickListener
            }

            // Extract address components from the selected place
            val addressComponents = selectedPlace?.addressComponents
            val fullAddress = selectedPlace?.address ?: ""
            val street = fullAddress.split(",").firstOrNull()?.trim() ?: fullAddress
            val city = addressComponents?.asList()?.find { it.types.contains("locality") }?.name ?: ""
            val state = addressComponents?.asList()?.find { it.types.contains("administrative_area_level_1") }?.shortName ?: ""
            val zipCode = addressComponents?.asList()?.find { it.types.contains("postal_code") }?.name ?: ""

            // Collect occupants and pets data
            val occupants = collectOccupantsData()
            val pets = collectPetsData()

            val workspaceData = mapOf(
                "type" to workspaceType,
                "name" to workspaceName,
                "address" to mapOf(
                    "street" to street,
                    "addressLine2" to apartmentSuite,
                    "city" to city,
                    "state" to state,
                    "zipCode" to zipCode,
                    "latitude" to (selectedPlace?.latLng?.latitude ?: 0.0),
                    "longitude" to (selectedPlace?.latLng?.longitude ?: 0.0)
                ),
                "occupants" to occupants,
                "pets" to pets,
                "totOccupants" to if (workspaceType == "House" || workspaceType == "Apartment") 
                    binding.spinnerExpectedOccupants.selectedItemPosition else 0,
                "totPets" to if (binding.switchPets.isChecked) binding.spinnerExpectedPets.selectedItemPosition else 0
            )

            // Save workspace data
            val workspaceRef = database.child("users").child(uid).child("workspaces").push()
            workspaceRef.setValue(workspaceData)
                .addOnSuccessListener {
                    val workspaceId = workspaceRef.key
                    if (workspaceId != null) {
                        if (workspaceType == "Apartment") {
                            val apartmentInfo = mapOf(
                                "apartmentName" to apartmentComplexName,
                                "floorNum" to floorNumber
                            )
                            workspaceRef.child("apartmentInfo").setValue(apartmentInfo)
                                .addOnSuccessListener {
                                    generateAndStoreAccessCode(workspaceId, uid)
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Failed to store apartment info: ${e.message}")
                                    showMessage("Failed to store apartment information", true)
                                }
                        } else {
                            generateAndStoreAccessCode(workspaceId, uid)
                        }
                    } else {
                        showMessage("Failed to generate workspace ID", true)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save workspace: ${e.message}")
                    showMessage("Failed to save workspace setup", true)
                }
        }
    }

    private fun generateAndStoreAccessCode(workspaceId: String, uid: String) {
                        accessCodeManager.generateAndStoreAccessCode(uid) { success, accessCode ->
                            if (success && accessCode != null) {
                                // Update the userCodes node with the workspace ID
                                database.child("userCodes")
                                    .child(accessCode)
                                    .child("workspaceId")
                                    .setValue(workspaceId)
                                    .addOnSuccessListener {
                        showMessage("Workspace setup successful!")
                                        
                                        // Navigate to MainActivity
                                        val intent = Intent(this, MainActivity::class.java)
                                        startActivity(intent)
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to store workspace ID: ${e.message}")
                        showMessage("Workspace saved but failed to store workspace ID", true)
                    }
            } else {
                showMessage("Workspace saved but failed to generate access code", true)
            }
        }
    }

    companion object {
        private const val TAG = "WorkspaceSetupActivity"
    }
}



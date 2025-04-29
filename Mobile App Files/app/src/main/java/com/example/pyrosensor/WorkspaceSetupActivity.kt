package com.example.pyrosensor

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.android.material.snackbar.Snackbar
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import com.google.firebase.storage.FirebaseStorage


class WorkspaceSetupActivity : AppCompatActivity() { //activity for setting up a workspace

    // View binding and database reference
    private lateinit var binding: ActivityWorkspaceSetupBinding
    private val database = FirebaseDatabase.getInstance().reference //reference to firebase database
    private val accessCodeManager = AccessCodeManager()
    
    // Date formatting and image handling
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    private var currentPhotoPath: String? = null
    private val PICK_IMAGE_REQUEST = 1
    private val CAMERA_REQUEST = 2
    private val PERMISSION_REQUEST_CODE = 3
    
    // Image storage for occupants and pets
    private val occupantImageUris = mutableMapOf<Int, Uri>()
    private val petImageUris = mutableMapOf<Int, Uri>()
    private val occupantImageUrls = mutableMapOf<Int, String>()
    private val petImageUrls = mutableMapOf<Int, String>()
    private val storage = FirebaseStorage.getInstance()
    
    // Place selection and image tracking
    private var selectedPlace: Place? = null
    private var currentImageIndex: Int = -1
    private var currentIsOccupant: Boolean = true

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
        // Set up pet spinner
        val petAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.pet_count,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerExpectedPets.adapter = adapter
        }

        // Set up pet spinner listener
        binding.spinnerExpectedPets.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val count = (position + 1).toString().toInt()
                updatePetFields(count)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                binding.layoutPetsDetails.removeAllViews()
            }
        }

        // Set up pet switch
        binding.switchPets.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                binding.tvExpectedPets.visibility = View.VISIBLE
                binding.spinnerExpectedPets.visibility = View.VISIBLE
                binding.layoutPetsDetails.visibility = View.VISIBLE
                // Set default selection to "1 pet" (index 0)
                binding.spinnerExpectedPets.setSelection(0, false)
                updatePetFields(1)
            } else {
                binding.tvExpectedPets.visibility = View.GONE
                binding.spinnerExpectedPets.visibility = View.GONE
                binding.layoutPetsDetails.visibility = View.GONE
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
                hint = "Pet ${i + 1} Pet Type: EX. Dog"
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
        currentImageIndex = index
        currentIsOccupant = isOccupant
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Photo")
            .setItems(options) { _, item ->
                when (item) {
                    0 -> {
                        if (checkCameraPermission()) {
                            openCamera()
                        }
                    }
                    1 -> {
                        if (checkStoragePermission()) {
                            openGallery()
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

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    private fun openCamera() {
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
                startActivityForResult(intent, CAMERA_REQUEST)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera: ${e.message}", e)
            showMessage("Error opening camera: ${e.message}", true)
        }
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
        Log.d(TAG, "onActivityResult called with requestCode: $requestCode, resultCode: $resultCode")
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    Log.d(TAG, "Processing gallery image selection")
                    val uri = data?.data
                    Log.d(TAG, "Selected image URI from gallery: $uri")
                    
                    if (uri != null && currentImageIndex != -1) {
                        try {
                            // Get a local file from the URI
                            val localFile = getLocalFileFromUri(uri)
                            if (localFile != null) {
                                val localUri = Uri.fromFile(localFile)
                                if (currentIsOccupant) {
                                    occupantImageUris[currentImageIndex] = localUri
                                    Log.d(TAG, "Stored occupant image URI for index $currentImageIndex: $localUri")
                                    // Update the image view
                                    val occupantLayout = binding.layoutOccupantsDetails.getChildAt(currentImageIndex) as LinearLayout
                                    // Find the image layout and image view
                                    var imageView: ImageView? = null
                                    for (i in 0 until occupantLayout.childCount) {
                                        val child = occupantLayout.getChildAt(i)
                                        if (child is LinearLayout) {
                                            for (j in 0 until child.childCount) {
                                                val grandChild = child.getChildAt(j)
                                                if (grandChild is ImageView) {
                                                    imageView = grandChild
                                                    break
                                                }
                                            }
                                        }
                                        if (imageView != null) break
                                    }
                                    imageView?.let {
                                        it.setImageURI(localUri)
                                        it.scaleType = ImageView.ScaleType.CENTER_CROP
                                    } ?: run {
                                        Log.e(TAG, "Could not find image view in occupant layout")
                                    }
                                } else {
                                    petImageUris[currentImageIndex] = localUri
                                    Log.d(TAG, "Stored pet image URI for index $currentImageIndex: $localUri")
                                    // Update the image view
                                    val petLayout = binding.layoutPetsDetails.getChildAt(currentImageIndex) as LinearLayout
                                    // Find the image layout and image view
                                    var imageView: ImageView? = null
                                    for (i in 0 until petLayout.childCount) {
                                        val child = petLayout.getChildAt(i)
                                        if (child is LinearLayout) {
                                            for (j in 0 until child.childCount) {
                                                val grandChild = child.getChildAt(j)
                                                if (grandChild is ImageView) {
                                                    imageView = grandChild
                                                    break
                                                }
                                            }
                                        }
                                        if (imageView != null) break
                                    }
                                    imageView?.let {
                                        it.setImageURI(localUri)
                                        it.scaleType = ImageView.ScaleType.CENTER_CROP
                                    } ?: run {
                                        Log.e(TAG, "Could not find image view in pet layout")
                                    }
                                }
                            } else {
                                Log.e(TAG, "Failed to get local file from URI")
                                showMessage("Failed to process selected image", true)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing image: ${e.message}", e)
                            showMessage("Error processing image: ${e.message}", true)
                        }
                    } else {
                        Log.e(TAG, "Invalid image selection data - Index: $currentImageIndex, URI: $uri")
                    }
                }
                CAMERA_REQUEST -> {
                    Log.d(TAG, "Processing camera image capture")
                    currentPhotoPath?.let { path ->
                        Log.d(TAG, "Camera photo path: $path")
                        val uri = Uri.fromFile(File(path))
                        
                        if (currentImageIndex != -1) {
                            Log.d(TAG, "Storing camera image. Index: $currentImageIndex, IsOccupant: $currentIsOccupant")
                            if (currentIsOccupant) {
                                occupantImageUris[currentImageIndex] = uri
                                Log.d(TAG, "Stored occupant image URI for index $currentImageIndex: $uri")
                                // Update the image view
                                val occupantLayout = binding.layoutOccupantsDetails.getChildAt(currentImageIndex) as LinearLayout
                                // Find the image layout and image view
                                var imageView: ImageView? = null
                                for (i in 0 until occupantLayout.childCount) {
                                    val child = occupantLayout.getChildAt(i)
                                    if (child is LinearLayout) {
                                        for (j in 0 until child.childCount) {
                                            val grandChild = child.getChildAt(j)
                                            if (grandChild is ImageView) {
                                                imageView = grandChild
                                                break
                                            }
                                        }
                                    }
                                    if (imageView != null) break
                                }
                                imageView?.let {
                                    it.setImageURI(uri)
                                    it.scaleType = ImageView.ScaleType.CENTER_CROP
                                } ?: run {
                                    Log.e(TAG, "Could not find image view in occupant layout")
                                }
                            } else {
                                petImageUris[currentImageIndex] = uri
                                Log.d(TAG, "Stored pet image URI for index $currentImageIndex: $uri")
                                // Update the image view
                                val petLayout = binding.layoutPetsDetails.getChildAt(currentImageIndex) as LinearLayout
                                // Find the image layout and image view
                                var imageView: ImageView? = null
                                for (i in 0 until petLayout.childCount) {
                                    val child = petLayout.getChildAt(i)
                                    if (child is LinearLayout) {
                                        for (j in 0 until child.childCount) {
                                            val grandChild = child.getChildAt(j)
                                            if (grandChild is ImageView) {
                                                imageView = grandChild
                                                break
                                            }
                                        }
                                    }
                                    if (imageView != null) break
                                }
                                imageView?.let {
                                    it.setImageURI(uri)
                                    it.scaleType = ImageView.ScaleType.CENTER_CROP
                                } ?: run {
                                    Log.e(TAG, "Could not find image view in pet layout")
                                }
                            }
                        } else {
                            Log.e(TAG, "Invalid index for camera image")
                        }
                    } ?: run {
                        Log.e(TAG, "No photo path available from camera")
                    }
                }
            }
        } else {
            Log.e(TAG, "Image selection cancelled or failed with resultCode: $resultCode")
        }
    }

    private fun getLocalFileFromUri(uri: Uri): File? {
        try {
            Log.d(TAG, "Getting local file from URI: $uri")
            
            val tempFile = File.createTempFile("upload_", ".jpg", cacheDir)
            Log.d(TAG, "Created temp file: ${tempFile.absolutePath}")
            
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream from URI")
                return null
            }
            
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.use { input ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        totalBytes += read
                    }
                    outputStream.flush()
                    Log.d(TAG, "Copied $totalBytes bytes to temp file")
                }
            }
            
            Log.d(TAG, "Successfully copied content to local file: ${tempFile.absolutePath}")
            Log.d(TAG, "Final file size: ${tempFile.length()} bytes")
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error creating local file from URI", e)
            return null
        }
    }

    private fun updateImageViews() {
        Log.d(TAG, "Updating image views")
        Log.d(TAG, "Occupant images: $occupantImageUris")
        Log.d(TAG, "Pet images: $petImageUris")
        
        // Update occupant images
        for (i in 0 until binding.layoutOccupantsDetails.childCount) {
            val occupantLayout = binding.layoutOccupantsDetails.getChildAt(i) as LinearLayout
            val imageLayout = occupantLayout.getChildAt(2) as LinearLayout
            val imageView = imageLayout.getChildAt(0) as ImageView
            
            occupantImageUris[i]?.let { uri ->
                Log.d(TAG, "Setting occupant image for index $i: $uri")
                try {
                    imageView.setImageURI(uri)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting occupant image: ${e.message}")
                }
            }
        }

        // Update pet images
        for (i in 0 until binding.layoutPetsDetails.childCount) {
            val petLayout = binding.layoutPetsDetails.getChildAt(i) as LinearLayout
            val imageLayout = petLayout.getChildAt(3) as LinearLayout
            val imageView = imageLayout.getChildAt(0) as ImageView
            
            petImageUris[i]?.let { uri ->
                Log.d(TAG, "Setting pet image for index $i: $uri")
                try {
                    imageView.setImageURI(uri)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting pet image: ${e.message}")
                }
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
                Log.e(TAG, "User not authenticated")
                showMessage("User not authenticated", true)
                return@setOnClickListener
            }

            if (selectedPlace == null) {
                Log.e(TAG, "No place selected")
                showMessage("Please select a valid address", true)
                return@setOnClickListener
            }

            Log.d(TAG, "Starting workspace setup process")
            Log.d(TAG, "Occupant images to upload: ${occupantImageUris.size}")
            Log.d(TAG, "Pet images to upload: ${petImageUris.size}")

            // First upload all occupant images
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val occupantImageUrls = mutableMapOf<Int, String>()
                    val petImageUrls = mutableMapOf<Int, String>()
                    
                    // Upload occupant images
                    for ((index, uri) in occupantImageUris) {
                        Log.d(TAG, "Uploading occupant image for index: $index")
                        Log.d(TAG, "Image URI: $uri")
                        val fileName = "users/${currentUser.uid}/occupants/occupant${index + 1}.jpg"
                        val downloadUrl = uploadImageToFirebaseStorage(uri, fileName)
                        if (downloadUrl != null) {
                            Log.d(TAG, "Successfully uploaded occupant image for index: $index")
                            Log.d(TAG, "Download URL: $downloadUrl")
                            occupantImageUrls[index] = downloadUrl
                        } else {
                            Log.e(TAG, "Failed to upload occupant image for index: $index")
                        }
                    }
                    
                    // Upload pet images
                    for ((index, uri) in petImageUris) {
                        Log.d(TAG, "Uploading pet image for index: $index")
                        Log.d(TAG, "Image URI: $uri")
                        val fileName = "users/${currentUser.uid}/pets/pet${index + 1}.jpg"
                        val downloadUrl = uploadImageToFirebaseStorage(uri, fileName)
                        if (downloadUrl != null) {
                            Log.d(TAG, "Successfully uploaded pet image for index: $index")
                            Log.d(TAG, "Download URL: $downloadUrl")
                            petImageUrls[index] = downloadUrl
                        } else {
                            Log.e(TAG, "Failed to upload pet image for index: $index")
                        }
                    }
                    
                    Log.d(TAG, "All images uploaded. Starting workspace setup...")
                    Log.d(TAG, "Occupant image URLs: $occupantImageUrls")
                    Log.d(TAG, "Pet image URLs: $petImageUrls")
                    
                    // Continue with workspace setup after all images are uploaded
                    withContext(Dispatchers.Main) {
                        completeWorkspaceSetup(currentUser.uid, occupantImageUrls, petImageUrls)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in image upload process: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        showMessage("Error uploading images: ${e.message}", true)
                    }
                }
            }
        }
    }

    private fun completeWorkspaceSetup(uid: String, occupantImageUrls: Map<Int, String>, petImageUrls: Map<Int, String>) {
        val workspaceType = binding.spinnerWorkspaceType.selectedItem.toString()
        val workspaceName = binding.etWorkspaceName.text.toString()
        val apartmentSuite = binding.etApartmentSuite.text.toString()
        val businessName = binding.etBusinessName.text.toString()

        val apartmentComplexName = binding.etApartmentComplexName.text.toString()
        val floorNumber = binding.etFloorNumber.text.toString()

        if (workspaceName.isEmpty()) {
            showMessage("Please enter a workspace name", true)
            return
        }

        if (workspaceType == "Apartment" && (apartmentComplexName.isEmpty() || floorNumber.isEmpty())) {
            showMessage("Please fill in apartment complex name and floor number", true)
            return
        }

        val addressComponents = selectedPlace?.addressComponents
        val fullAddress = selectedPlace?.address ?: ""
        val street = fullAddress.split(",").firstOrNull()?.trim() ?: fullAddress
        val city = addressComponents?.asList()?.find { it.types.contains("locality") }?.name ?: ""
        val state = addressComponents?.asList()?.find { it.types.contains("administrative_area_level_1") }?.shortName ?: ""
        val zipCode = addressComponents?.asList()?.find { it.types.contains("postal_code") }?.name ?: ""

        val occupants = collectOccupantsData(occupantImageUrls)
        val pets = collectPetsData(petImageUrls)

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
            "totPets" to if (binding.switchPets.isChecked) binding.spinnerExpectedPets.selectedItemPosition + 1 else 0
        )

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

    private fun collectOccupantsData(occupantImageUrls: Map<Int, String>): Map<String, Map<String, String>> {
        val occupants = mutableMapOf<String, Map<String, String>>()
        if (binding.spinnerWorkspaceType.selectedItem.toString() in listOf("House", "Apartment")) {
            for (i in 0 until binding.layoutOccupantsDetails.childCount) {
                val occupantLayout = binding.layoutOccupantsDetails.getChildAt(i) as LinearLayout
                val name = (occupantLayout.getChildAt(0) as EditText).text.toString()
                val dob = (occupantLayout.getChildAt(1) as EditText).text.toString()
                if (name.isNotEmpty() && dob.isNotEmpty()) {
                    val age = calculateAge(dob)
                    val occupantData = mutableMapOf(
                        "name" to name,
                        "dob" to dob,
                        "age" to age.toString()
                    )
                    
                    // Add image URL if available
                    occupantImageUrls[i]?.let { url ->
                        occupantData["imageUrl"] = url
                    }
                    
                    occupants["occupant${i + 1}"] = occupantData
                }
            }
        }
        return occupants
    }

    private fun collectPetsData(petImageUrls: Map<Int, String>): Map<String, Map<String, String>> {
        val pets = mutableMapOf<String, Map<String, String>>()
        if (binding.switchPets.isChecked) {
            for (i in 0 until binding.layoutPetsDetails.childCount) {
                val petLayout = binding.layoutPetsDetails.getChildAt(i) as LinearLayout
                val name = (petLayout.getChildAt(0) as EditText).text.toString()
                val color = (petLayout.getChildAt(1) as EditText).text.toString()
                val breed = (petLayout.getChildAt(2) as EditText).text.toString()
                if (name.isNotEmpty() && color.isNotEmpty() && breed.isNotEmpty()) {
                    val petData = mutableMapOf(
                        "name" to name,
                        "color" to color,
                        "breed" to breed
                    )
                    
                    // Add image URL if available
                    petImageUrls[i]?.let { url ->
                        petData["imageUrl"] = url
                    }
                    
                    pets["pet${i + 1}"] = petData
                }
            }
        }
        return pets
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

    private fun uploadImageToFirebaseStorage(imageUri: Uri, fileName: String): String? {
        return runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting upload process for file: $fileName")
                    Log.d(TAG, "Source URI: $imageUri")
                    
                    // Get local file from URI
                    val localFile = getLocalFileFromUri(imageUri)
                    if (localFile == null) {
                        Log.e(TAG, "Failed to get local file from URI")
                        return@withContext null
                    }
                    
                    Log.d(TAG, "Local file obtained: ${localFile.absolutePath}")
                    Log.d(TAG, "File exists: ${localFile.exists()}")
                    Log.d(TAG, "File size: ${localFile.length()} bytes")
                    
                    // Compress the image
                    val compressedImage = compressImage(localFile)
                    if (compressedImage == null) {
                        Log.e(TAG, "Failed to compress image")
                        return@withContext null
                    }
                    
                    Log.d(TAG, "Image compressed successfully, size: ${compressedImage.size} bytes")
                    
                    // Create storage reference with timestamp to avoid collisions
                    val timestamp = System.currentTimeMillis()
                    val uniqueFileName = "${fileName.substringBeforeLast(".")}_${timestamp}.${fileName.substringAfterLast(".")}"
                    val storageRef = storage.reference.child(uniqueFileName)
                    
                    Log.d(TAG, "Storage reference path: ${storageRef.path}")
                    Log.d(TAG, "Uploading byte array of size: ${compressedImage.size}")
                    
                    // Upload using ByteArrayInputStream for better reliability
                    val inputStream = ByteArrayInputStream(compressedImage)
                    val uploadTask = storageRef.putStream(inputStream)
                    
                    // Monitor upload progress (logging only)
                    uploadTask.addOnProgressListener { taskSnapshot ->
                        val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                        Log.d(TAG, "Upload progress: $progress%")
                    }
                    
                    // Add success and failure listeners for better error tracking
                    uploadTask.addOnSuccessListener {
                        Log.d(TAG, "Upload succeeded. Getting download URL...")
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "Upload failed: ${e.message}", e)
                        runOnUiThread {
                            showMessage("Upload failed: ${e.message}", true)
                        }
                    }
                    
                    // Wait for upload to complete
                    Log.d(TAG, "Waiting for upload to complete...")
                    val uploadResult = uploadTask.await()
                    Log.d(TAG, "Upload completed successfully, metadata: ${uploadResult.metadata}")
                    
                    // Give Firebase a moment to register the new file
                    delay(100)
                    
                    // Get download URL
                    Log.d(TAG, "Getting download URL...")
                    val downloadUrl = storageRef.downloadUrl.await().toString()
                    Log.d(TAG, "Download URL obtained: $downloadUrl")
                    
                    downloadUrl
                } catch (e: Exception) {
                    Log.e(TAG, "Error uploading image: ${e.message}", e)
                    runOnUiThread {
                        showMessage("Error uploading image: ${e.message}", true)
                    }
                    null
                }
            }
        }
    }

    private fun compressImage(imageFile: File): ByteArray? {
        try {
            Log.d(TAG, "Starting image compression")
            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
            
            var inSampleSize = 1
            val maxDimension = 1200
            
            if (decodeOptions.outHeight > maxDimension || decodeOptions.outWidth > maxDimension) {
                val halfHeight = decodeOptions.outHeight / 2
                val halfWidth = decodeOptions.outWidth / 2
                
                while (halfHeight / inSampleSize >= maxDimension && 
                       halfWidth / inSampleSize >= maxDimension) {
                    inSampleSize *= 2
                }
            }
            
            Log.d(TAG, "Image dimensions before compression: ${decodeOptions.outWidth}x${decodeOptions.outHeight}")
            Log.d(TAG, "Using sample size: $inSampleSize")
            
            val options = BitmapFactory.Options().apply {
                inSampleSize = inSampleSize
                inJustDecodeBounds = false
            }
            
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath, options)
            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from file")
                return null
            }
            
            Log.d(TAG, "Bitmap decoded successfully, dimensions: ${bitmap.width}x${bitmap.height}")
            
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val compressedData = outputStream.toByteArray()
            
            Log.d(TAG, "Compression complete. Original size: ${imageFile.length()} bytes, Compressed size: ${compressedData.size} bytes")
            
            return compressedData
        } catch (e: Exception) {
            Log.e(TAG, "Error compressing image: ${e.message}", e)
            return null
        }
    }

    companion object {
        private const val TAG = "WorkspaceSetupActivity"
    }
}



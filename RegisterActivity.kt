package com.example.pyrosensor

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.FileProvider
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

class RegisterActivity : AppCompatActivity() {


    private lateinit var auth: FirebaseAuth //initializes firebase authentication
    private lateinit var database: FirebaseDatabase //initializes firebase database
    private lateinit var storage: FirebaseStorage //initializes firebase storage
    private var selectedImageUri: Uri? = null //initializes selected image URI
    private var currentPhotoPath: String? = null //initializes current photo path
    private val PICK_IMAGE_REQUEST = 1 //defines PICK_IMAGE_REQUEST constant
    private val CAMERA_REQUEST = 2 //defines CAMERA_REQUEST constant
    private val PERMISSION_REQUEST_CODE = 3 //defines PERMISSION_REQUEST_CODE constant
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    
    // Dedicated TAG for logging
    private val TAG = "RegisterActivity"

    private fun showMessage(message: String, isError: Boolean = false) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.view
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        
        // Set text color based on whether it's an error or not
        textView.setTextColor(if (isError) Color.RED else Color.BLACK)
        
        // Set background color to white
        snackbarView.setBackgroundColor(Color.WHITE)
        
        // Make text size larger
        textView.textSize = 16f
        
        // Add padding to make the Snackbar bigger
        val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
        params.setMargins(16, 16, 16, 16)
        snackbarView.layoutParams = params
        
        // Add padding inside the Snackbar
        snackbarView.setPadding(24, 16, 24, 16)
        
        // Make the Snackbar wrap content vertically
        textView.maxLines = 10
        textView.isSingleLine = false
        
        // Show the snackbar
        snackbar.show()
    }

    private fun validateFields(firstName: String, lastName: String, dob: String, email: String, password: String, passwordConfirm: String): String? {
        val emptyFields = mutableListOf<String>()
        
        if (firstName.isEmpty()) emptyFields.add("First Name")
        if (lastName.isEmpty()) emptyFields.add("Last Name")
        if (dob.isEmpty()) emptyFields.add("Date of Birth")
        if (email.isEmpty()) emptyFields.add("Email")
        if (password.isEmpty()) emptyFields.add("Password")
        if (passwordConfirm.isEmpty()) emptyFields.add("Password Confirmation")
        
        if (emptyFields.isNotEmpty()) {
            return "Please fill in the following fields: ${emptyFields.joinToString(", ")}"
        }
        
        if (password != passwordConfirm) {
            return "Passwords do not match"
        }
        
        return null
    }

    private fun calculateAge(dob: Date): Int {
        val today = Calendar.getInstance()
        val birthDate = Calendar.getInstance().apply {
            time = dob
        }
        
        var age = today.get(Calendar.YEAR) - birthDate.get(Calendar.YEAR)
        if (today.get(Calendar.DAY_OF_YEAR) < birthDate.get(Calendar.DAY_OF_YEAR)) {
            age--
        }
        return age
    }

    private fun updateAgeDisplay(dobString: String) {
        val ageTextView = findViewById<TextView>(R.id.tvAge)
        if (dobString.isNotEmpty()) {
            try {
                val dob = dateFormat.parse(dobString)
                dob?.let {
                    val age = calculateAge(it)
                    ageTextView.text = "Age: $age"
                }
            } catch (e: Exception) {
                ageTextView.text = "Age: "
            }
        } else {
            ageTextView.text = "Age: "
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) //sets up activity state
        setContentView(R.layout.activity_signup) //sets the layout for the signup activity

        auth = FirebaseAuth.getInstance() //gets instance of firebase authentication
        database = FirebaseDatabase.getInstance() //gets instance of firebase database
        storage = FirebaseStorage.getInstance() //gets instance of firebase storage
        
        // Log Firebase Storage reference information to verify configuration
        Log.d(TAG, "Firebase Storage reference: ${storage.reference}")

        val profileImageView = findViewById<ImageView>(R.id.ivProfilePicture) //finds profile image view in layout
        val selectImageButton = findViewById<Button>(R.id.btnSelectImage) //finds select image button in layout
        val registerButton = findViewById<Button>(R.id.btnRegister) //finds register button in layout
        val firstNameField = findViewById<EditText>(R.id.etFirstName) //finds first name field in layout
        val lastNameField = findViewById<EditText>(R.id.etLastName) //finds last name field in layout
        val dobField = findViewById<EditText>(R.id.etDOB) //finds date of birth field in layout
        val emailField = findViewById<EditText>(R.id.etEmail) //finds email field in layout
        val passwordField = findViewById<EditText>(R.id.etPassword) //finds password field in layout
        val passwordConfirmField = findViewById<EditText>(R.id.etPasswordConfirm) //finds password confirmation field in layout

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
                    updateAgeDisplay(formattedDate)
                },
                year,
                month,
                day
            ).apply {
                // Set maximum date to today (prevent future dates)
                datePicker.maxDate = System.currentTimeMillis()
                // Set minimum date to 100 years ago
                calendar.add(Calendar.YEAR, -100)
                datePicker.minDate = calendar.timeInMillis
                show()
            }
        }

        // Make the field read-only since we're using the date picker
        dobField.isFocusable = false

        selectImageButton.setOnClickListener { //sets a click listener on the select image button
            showImageSelectionDialog() //calls method to show image selection dialog
        }

        registerButton.setOnClickListener {
            val firstName = firstNameField.text.toString()
            val lastName = lastNameField.text.toString()
            val dob = dobField.text.toString()
            val email = emailField.text.toString()
            val password = passwordField.text.toString()
            val passwordConfirm = passwordConfirmField.text.toString()

            val validationError = validateFields(firstName, lastName, dob, email, password, passwordConfirm)
            if (validationError != null) {
                showMessage(validationError, true)
                return@setOnClickListener
            }

            registerUser(firstName, lastName, dob, email, password)
        }
    }
    
    // New dedicated method for image upload to Firebase Storage
    private fun compressImage(imageFile: File): ByteArray? {
        try {
            Log.d(TAG, "Starting image compression")
            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            // First decode to get dimensions
            BitmapFactory.decodeFile(imageFile.absolutePath, decodeOptions)
            
            // Calculate inSampleSize
            var inSampleSize = 1
            val maxDimension = 1200 // Maximum dimension for the compressed image
            
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
            
            // Decode with calculated sample size
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
            
            // Compress the bitmap
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

    private fun uploadImageToFirebaseStorage(imageUri: Uri, fileName: String): String? {
        return runBlocking {
            withContext(Dispatchers.IO) {
                try {
                    Log.d(TAG, "Starting upload process for file: $fileName")
                    
                    // Get local file from URI
                    val localFile = getLocalFileFromUri(imageUri)
                    if (localFile == null) {
                        Log.e(TAG, "Failed to get local file from URI")
                        return@withContext null
                    }
                    
                    Log.d(TAG, "Local file obtained: ${localFile.absolutePath}")
                    
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
                    val storageRef = FirebaseStorage.getInstance().reference
                        .child("users")
                        .child(fileName.substringBeforeLast("/"))
                        .child(uniqueFileName)
                    Log.d(TAG, "Uploading to path: ${storageRef.path}")
                    Log.d(TAG, "Uploading byte array of size: ${compressedImage.size}")
                    
                    // Upload using ByteArrayInputStream for better reliability
                    val inputStream = ByteArrayInputStream(compressedImage)
                    val uploadTask = storageRef.putStream(inputStream)
                    
                    // Monitor upload progress
                    uploadTask.addOnProgressListener { taskSnapshot ->
                        val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                        Log.d(TAG, "Upload progress: $progress%")
                        runOnUiThread {
                            showMessage("Upload progress: ${progress.toInt()}%")
                        }
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

    // Helper method to get a local file from any URI, including Google Photos
    private fun getLocalFileFromUri(uri: Uri): File? {
        try {
            Log.d(TAG, "Getting local file from URI: $uri")
            
            // Create a temporary file to store the image
            val tempFile = File.createTempFile("upload_", ".jpg", cacheDir)
            
            // Open an input stream from the URI
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream from URI")
                return null
            }
            
            // Write the image to the temporary file
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.use { input ->
                    val buffer = ByteArray(4 * 1024) // 4K buffer
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    outputStream.flush()
                }
            }
            
            Log.d(TAG, "Successfully copied content to local file: ${tempFile.absolutePath}")
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error creating local file from URI", e)
            return null
        }
    }

    private fun showImageSelectionDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Cancel")
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Add Profile Picture")
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

    private fun openCamera() {
        try {
            Log.d(TAG, "Opening camera")
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            
            // Create the photo file
            val photoFile = createImageFile()
            photoFile?.let { file ->
                Log.d(TAG, "Photo file created: ${file.absolutePath}")
                
                // Get the URI using FileProvider
                val photoUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    file
                )
                
                currentPhotoPath = file.absolutePath
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                
                // Try to start the camera activity
                try {
                    startActivityForResult(intent, CAMERA_REQUEST)
                    Log.d(TAG, "Camera activity started")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera activity", e)
                    showMessage("Failed to open camera: ${e.message}", true)
                }
            } ?: run {
                Log.e(TAG, "Failed to create photo file")
                showMessage("Failed to create photo file", true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            showMessage("Error opening camera: ${e.message}", true)
        }
    }

    private fun createImageFile(): File? {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            Log.d(TAG, "Creating image file in directory: ${storageDir?.absolutePath}")
            
            return File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
            ).also { file ->
                Log.d(TAG, "Image file created: ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating image file", e)
            return null
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png"))
        }
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult called with requestCode: $requestCode, resultCode: $resultCode")
        
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                PICK_IMAGE_REQUEST -> {
                    Log.d(TAG, "Processing gallery image selection")
                    selectedImageUri = data?.data
                    Log.d(TAG, "Selected image URI from gallery: $selectedImageUri")
                    if (selectedImageUri != null) {
                        findViewById<ImageView>(R.id.ivProfilePicture).setImageURI(selectedImageUri)
                        Log.d(TAG, "Gallery image set to ImageView")

                    } else {
                        Log.e(TAG, "No image URI received from gallery")
                    }
                }
                CAMERA_REQUEST -> {
                    Log.d(TAG, "Processing camera image capture")
                    currentPhotoPath?.let { path ->
                        Log.d(TAG, "Camera photo path: $path")
                        selectedImageUri = Uri.fromFile(File(path))
                        Log.d(TAG, "Selected image URI from camera: $selectedImageUri")
                        findViewById<ImageView>(R.id.ivProfilePicture).setImageURI(selectedImageUri)
                        Log.d(TAG, "Camera image set to ImageView")
                    } ?: run {
                        Log.e(TAG, "No photo path available from camera")
                    }
                }
            }
        } else {
            Log.e(TAG, "Image selection cancelled or failed with resultCode: $resultCode")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "Permission result: requestCode=$requestCode, grantResults=${grantResults.contentToString()}")
        
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission granted")
                    openCamera()
                } else {
                    Log.e(TAG, "Camera permission denied")
                    showMessage("Camera permission is required to take photos", true)
                }
            }
        }
    }

    private fun registerUser(firstName: String, lastName: String, dob: String, email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Starting registration process")
                Log.d(TAG, "Selected image URI before registration: $selectedImageUri")
                
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid ?: throw Exception("User ID is null")
                Log.d(TAG, "User created with ID: $userId")
                
                // Create the user document first
                val userRef = database.reference.child("users").child(userId)
                Log.d(TAG, "User reference created: ${userRef.key}")
                
                // Calculate age from DOB
                val age = try {
                    val dobDate = dateFormat.parse(dob)
                    dobDate?.let { calculateAge(it) } ?: 0
                } catch (e: Exception) {
                    Log.e(TAG, "Error calculating age", e)
                    0
                }
                
                // Upload profile picture if selected
                val profilePictureUrl = selectedImageUri?.let { uri ->
                    try {
                        // Use our new dedicated upload method
                        val fileName = "profile_pictures/${userId}.jpg"
                        uploadImageToFirebaseStorage(uri, fileName)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to upload profile picture", e)
                        null
                    }
                } ?: run {
                    Log.d(TAG, "No profile picture selected (selectedImageUri is null)")
                    null
                }

                val user = User(firstName, lastName, dob, email, age, profilePictureUrl)
                Log.d(TAG, "User object created: $user")
                
                Log.d(TAG, "Saving user data to database")
                userRef.child("userInfo").setValue(user).await()
                Log.d(TAG, "User data saved successfully")
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Registration successful, transitioning to MainActivity")
                    showMessage("Registration successful")
                    val intent = Intent(this@RegisterActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering user", e)
                withContext(Dispatchers.Main) {
                    showMessage("Registration failed: ${e.message}", true)
                }
            }
        }
    }

    data class User(
        val firstName: String,
        val lastName: String,
        val dob: String,
        val email: String,
        val age: Int,
        val profilePictureUrl: String? = null
    )
}

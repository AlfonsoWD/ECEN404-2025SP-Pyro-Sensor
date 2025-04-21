package com.example.pyrosensor

import android.app.Activity
import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import java.util.*


//TODO: Fix "Ready To Scan" message
//TODO: Sensor name suggestion depending on type of building
//

class AddSensorActivity : AppCompatActivity() {

    private val REQUEST_ENABLE_BT = 1
    private val REQUEST_PERMISSIONS = 2
    private val TAG = "AddSensorActivity"
    private val PYRO_SERVER_NAME = "PYRO_SERVER"
    private val SERVICE_UUID = UUID.fromString("6143a485-078d-4bab-87a2-c417de199fd6")
    private val SSID_UUID = UUID.fromString("7c46db3d-0c3e-c087-7640-312db62a77f9")
    private val PASSWORD_UUID = UUID.fromString("6fae9d82-7db9-f7b1-6a40-f698f3a87f31")
    private val USERID_UUID = UUID.fromString("99b9dfef-d4a5-029c-db45-326cda2ff206")
    private val USERID_SECOND_HALF_UUID = UUID.fromString("634bd4b2-088f-c0a2-4744-0b53ef670a0d")
    private val SENSOR_NAME_UUID = UUID.fromString("3f37ad58-150c-308e-af48-c4d2bf7adb80")

    private val SCAN_TIMEOUT = 10000L // 10 seconds
    private val CONNECT_TIMEOUT = 10000L // 10 seconds

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isScanning = false

    private lateinit var statusText: TextView
    private lateinit var scanButton: Button
    private lateinit var progressBar: ProgressBar
    private val handler = Handler(Looper.getMainLooper())

    private var networkSSID: String? = null
    private var networkPassword: String? = null
    private var sensorName: String? = null
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private lateinit var wifiManager: WifiManager
    private var wifiScanReceiver: BroadcastReceiver? = null
    
    // Keep a reference to the current credentials dialog
    private var currentCredentialsDialog: AlertDialog? = null
    private var currentCredentialsView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.sensor_add)

        // Initialize WifiManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Initialize UI components
        statusText = findViewById(R.id.status_text)
        scanButton = findViewById(R.id.scan_button)
        progressBar = findViewById(R.id.progress_bar)
        
        val fabCloseWindow: FloatingActionButton = findViewById(R.id.fab_close_window)
        val testDialogButton: Button = findViewById(R.id.test_dialog_button)
        
        scanButton.setOnClickListener {
            if (!isScanning) {
                checkPermissionsAndStartScan()
            } else {
                stopScanning()
            }
        }

        testDialogButton.setOnClickListener {
            showNetworkCredentialsDialog(null)
        }

        fabCloseWindow.setOnClickListener {
            finish()
        }

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Only check permissions, don't start scanning
        checkPermissions()
    }

    private fun checkPermissions() {
        try {
            val permissions = mutableListOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_SCAN)
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }

            // First check if Bluetooth is supported and enabled
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Check if Bluetooth is enabled
            if (!bluetoothAdapter?.isEnabled!!) {
                updateStatus("Bluetooth is disabled")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                try {
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                    return
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException when enabling Bluetooth: ${e.message}")
                    updateStatus("Permission denied to enable Bluetooth")
                    return
                }
            }

            // Check and request permissions if needed
            val permissionsToRequest = permissions.filter {
                try {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException when checking permission $it: ${e.message}")
                    true // If we get an exception, we'll request the permission
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                try {
                    ActivityCompat.requestPermissions(
                        this,
                        permissionsToRequest.toTypedArray(),
                        REQUEST_PERMISSIONS
                    )
                } catch (e: SecurityException) {
                    Log.e(TAG, "SecurityException when requesting permissions: ${e.message}")
                    updateStatus("Unable to request required permissions")
                    Toast.makeText(
                        this,
                        "Permission request failed. Please enable permissions manually.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            // Removed automatic startScanning() call
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException in checkPermissions: ${e.message}")
            updateStatus("Security error checking permissions")
            Toast.makeText(this, "Security error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkPermissionsAndStartScan() {
        if (!bluetoothAdapter?.isEnabled!!) {
            updateStatus("Requesting Bluetooth enable...")
            if (!checkBluetoothPermission()) {
                updateStatus("Bluetooth permission required")
                return
            }
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }
        startScanning()
    }

    private fun checkBluetoothPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    REQUEST_PERMISSIONS
                )
                false
            } else {
                true
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADMIN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.BLUETOOTH,
                        Manifest.permission.BLUETOOTH_ADMIN
                    ),
                    REQUEST_PERMISSIONS
                )
                false
            } else {
                true
            }
        }
    }

    private fun startScanning() {
        try {
            if (isScanning) return

            if (!checkBluetoothPermission()) {
                updateStatus("Bluetooth permission required")
                return
            }

            updateStatus("Scanning for PYRO SENSOR...")
            progressBar.visibility = View.VISIBLE
            scanButton.text = "Stop Scan"
            isScanning = true

            // Set up scan filters
            val scanFilter = ScanFilter.Builder()
                .setDeviceName(PYRO_SERVER_NAME)
                .build()

            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // Start scan with timeout
            bluetoothAdapter?.bluetoothLeScanner?.startScan(
                listOf(scanFilter),
                scanSettings,
                scanCallback
            )

            handler.postDelayed({
                if (isScanning) {
                    stopScanning()
                    updateStatus("Scan timeout - device not found")
                }
            }, SCAN_TIMEOUT)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when starting scan: ${e.message}")
            updateStatus("Permission denied to scan for devices")
            isScanning = false
            progressBar.visibility = View.INVISIBLE
            scanButton.text = "Start Scan"
        }
    }

    private fun stopScanning() {
        try {
            if (!isScanning) return

            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            isScanning = false
            progressBar.visibility = View.INVISIBLE
            scanButton.text = "Start Scan"
            
            // Update status sequence
            updateStatus("Scan Stopped")
            handler.postDelayed({
                updateStatus("Ready to scan")
            }, 3000)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when stopping scan: ${e.message}")
            updateStatus("Permission denied to stop scanning")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // Check which permissions were requested
                    val hasBluetoothPermissions = permissions.any { 
                        it == Manifest.permission.BLUETOOTH || 
                        it == Manifest.permission.BLUETOOTH_ADMIN ||
                        it == Manifest.permission.BLUETOOTH_SCAN ||
                        it == Manifest.permission.BLUETOOTH_CONNECT 
                    }
                    
                    val hasWifiPermissions = permissions.any {
                        it == Manifest.permission.ACCESS_WIFI_STATE ||
                        it == Manifest.permission.CHANGE_WIFI_STATE ||
                        it == Manifest.permission.ACCESS_FINE_LOCATION
                    }

                    // Handle Bluetooth permissions
                    if (hasBluetoothPermissions) {
                        try {
                            if (bluetoothAdapter?.isEnabled == true) {
                                startScanning()
                            } else {
                                updateStatus("Bluetooth is required for scanning")
                                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                            }
                        } catch (e: SecurityException) {
                            Log.e(TAG, "SecurityException when checking Bluetooth state: ${e.message}")
                            updateStatus("Permission denied to access Bluetooth")
                        }
                    }

                    // Handle WiFi permissions
                    if (hasWifiPermissions) {
                        startWifiScan()
                    }
                } else {
                    updateStatus("Required permissions not granted")
                    Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    startScanning()
                } else {
                    updateStatus("Bluetooth is required for device setup")
                    Toast.makeText(this, "Bluetooth is required for device setup", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                if (!checkBluetoothPermission()) {
                    updateStatus("Bluetooth permission required")
                    return
                }
                
                if (device.name == PYRO_SERVER_NAME) {
                    stopScanning()
                    updateStatus("Found PYRO_SERVER, connecting...")
                    connectToDevice(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e(TAG, "Scan failed with error: $errorCode")
            updateStatus("Scan failed: ${getScanErrorMessage(errorCode)}")
            stopScanning()
        }
    }

    private fun getScanErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            ScanCallback.SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
            ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE not supported"
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
            else -> "Unknown error"
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        Log.d(TAG, "Starting connection to device: ${device.name}")
        if (!checkBluetoothPermission()) {
            Log.e(TAG, "Bluetooth permission required for connection")
            updateStatus("Bluetooth permission required")
            return
        }

        try {
            Log.d(TAG, "Initiating GATT connection with LE transport...")
            // Use transport parameter 2 for LE
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            updateStatus("Connecting to device...")
            
            // Add connection timeout - only disconnect if we haven't connected within 5 seconds
            handler.postDelayed({
                if (bluetoothGatt?.device?.bondState == BluetoothDevice.BOND_NONE && 
                    bluetoothGatt?.device?.type == BluetoothDevice.DEVICE_TYPE_UNKNOWN) {
                    Log.e(TAG, "Connection timeout - no response from device")
                    updateStatus("Connection timeout - device not responding")
                    disconnectGatt()
                }
            }, 5000) // 5 second timeout
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during connection: ${e.message}")
            updateStatus("Connection failed: ${e.message}")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG, "onConnectionStateChange - Status: $status, New State: $newState")
            
            if (!checkBluetoothPermission()) {
                Log.e(TAG, "Bluetooth permission required in onConnectionStateChange")
                updateStatus("Bluetooth permission required")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server successfully")
                    updateStatus("Connected to PYRO SENSOR")
                    Log.d(TAG, "Starting service discovery...")
                    // Add a small delay before service discovery
                    handler.postDelayed({
                        try {
                            val success = gatt?.discoverServices() ?: false
                            Log.d(TAG, "discoverServices call result: $success")
                            if (!success) {
                                Log.e(TAG, "Failed to start service discovery")
                                updateStatus("Failed to discover services")
                                disconnectGatt()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Exception during service discovery: ${e.message}")
                            updateStatus("Error discovering services")
                            disconnectGatt()
                        }
                    }, 500)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server")
                    Log.d(TAG, "Disconnection reason - Status: $status")
                    updateStatus("Disconnected from PYRO SENSOR")
                    // Only disconnect if we're not in the middle of writing characteristics
                    if (networkSSID == null) {
                        disconnectGatt()
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            Log.d(TAG, "onServicesDiscovered - Status: $status")
            
            if (!checkBluetoothPermission()) {
                Log.e(TAG, "Bluetooth permission required in onServicesDiscovered")
                updateStatus("Bluetooth permission required")
                return
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered successfully. Number of services: ${gatt?.services?.size ?: 0}")
                gatt?.services?.forEach { service ->
                    Log.d(TAG, "Service UUID: ${service.uuid}")
                    service.characteristics.forEach { characteristic ->
                        Log.d(TAG, "  Characteristic UUID: ${characteristic.uuid}")
                    }
                }
                updateStatus("Services discovered, requesting network credentials...")
                // Show dialog on main thread
                runOnUiThread {
                    showNetworkCredentialsDialog(gatt)
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
                updateStatus("Service discovery failed")
                disconnectGatt()
            }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "onCharacteristicWrite - Status: $status, Characteristic: ${characteristic?.uuid}")
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Characteristic written successfully: ${characteristic?.uuid}")
                // Write next characteristic based on the current one
                when (characteristic?.uuid) {
                    USERID_UUID -> {
                        Log.d(TAG, "Writing second half of USERID characteristic...")
                        currentUser?.let { user ->
                            val uid = user.uid
                            val halfLength = uid.length / 2
                            val secondHalf = uid.substring(halfLength)
                            Log.d(TAG, "Second half of UID: $secondHalf")
                            writeCharacteristic(gatt, USERID_SECOND_HALF_UUID, secondHalf)
                        }
                    }
                    USERID_SECOND_HALF_UUID -> {
                        Log.d(TAG, "Writing sensor name characteristic...")
                        Log.d(TAG, "Sensor name to write: $sensorName")
                        writeCharacteristic(gatt, SENSOR_NAME_UUID, sensorName ?: "DefaultSensor")
                    }
                    SENSOR_NAME_UUID -> {
                        Log.d(TAG, "Writing SSID characteristic...")
                        Log.d(TAG, "SSID to write: $networkSSID")
                        writeCharacteristic(gatt, SSID_UUID, networkSSID ?: "")
                    }
                    SSID_UUID -> {
                        Log.d(TAG, "Writing PASSWORD characteristic...")
                        Log.d(TAG, "Password to write: $networkPassword")
                        writeCharacteristic(gatt, PASSWORD_UUID, networkPassword ?: "")
                    }
                    PASSWORD_UUID -> {
                        Log.d(TAG, "All characteristics written successfully")
                        updateStatus("Configuration complete")
                        handler.postDelayed({
                            disconnectGatt()
                            finish()
                        }, 1000)
                    }
                }
            } else {
                Log.e(TAG, "Characteristic write failed with status: $status")
                updateStatus("Failed to write characteristic")
                disconnectGatt()
            }
        }
    }

    private fun writeCharacteristic(gatt: BluetoothGatt?, uuid: UUID, value: String) {
        Log.d(TAG, "Attempting to write characteristic: $uuid with value: $value")
        
        if (!checkBluetoothPermission()) {
            Log.e(TAG, "Bluetooth permission required for writing characteristic")
            updateStatus("Bluetooth permission required")
            return
        }

        val service = gatt?.getService(SERVICE_UUID)
        Log.d(TAG, "Service found: ${service != null}")
        
        if (service == null) {
            Log.e(TAG, "Service not found: $SERVICE_UUID")
            updateStatus("Service not found")
            return
        }
        
        val characteristic = service.getCharacteristic(uuid)
        Log.d(TAG, "Characteristic found: ${characteristic != null}")
        
        if (characteristic == null) {
            Log.e(TAG, "Required characteristic not found: $uuid")
            updateStatus("Required characteristic not found")
            return
        }

        try {
            characteristic.value = value.toByteArray(Charsets.UTF_8)
            Log.d(TAG, "Writing characteristic value: '$value' (${value.toByteArray(Charsets.UTF_8).size} bytes)")
            val writeSuccess = gatt.writeCharacteristic(characteristic)
            Log.d(TAG, "writeCharacteristic call result: $writeSuccess")
            
            if (!writeSuccess) {
                Log.e(TAG, "Failed to initiate characteristic write")
                updateStatus("Failed to write to device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during characteristic write: ${e.message}")
            updateStatus("Error writing to device: ${e.message}")
        }
    }

    private fun showNetworkCredentialsDialog(gatt: BluetoothGatt?, preSelectedSsid: String? = null, networkInfo: android.net.wifi.ScanResult? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_network_credentials, null)
        currentCredentialsView = dialogView
        
        val ssidInput = dialogView.findViewById<EditText>(R.id.ssid_input)
        val passwordInput = dialogView.findViewById<EditText>(R.id.password_input)
        val sensorNameInput = dialogView.findViewById<EditText>(R.id.sensor_name_input)
        val passwordLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.password_layout)
        
        // Create error text view
        val errorText = TextView(this).apply {
            setTextColor(Color.RED)
            textSize = 14f
            visibility = View.GONE
            setPadding(0, 8, 0, 8)
        }
        (dialogView as ViewGroup).addView(errorText, 0)

        // Set pre-selected SSID if available
        preSelectedSsid?.let {
            ssidInput.setText(it)
        }

        // Check if the network is open and configure UI accordingly
        updatePasswordVisibility(passwordLayout, networkInfo)
        
        // Store whether the current network is open for validation later
        val isOpenNet = networkInfo != null && isOpenNetwork(networkInfo)
        
        // Restore sensor name if we have it
        if (this.sensorName != null) {
            sensorNameInput.setText(this.sensorName)
            Log.d(TAG, "Restored previous sensor name: ${this.sensorName}")
        }

        // Set up click listener for SSID input to show WiFi networks
        ssidInput.setOnClickListener {
            if (checkWifiPermissions()) {
                // Save current sensor name input
                this.sensorName = sensorNameInput.text.toString().trim()
                Log.d(TAG, "Saved sensor name before WiFi scan: ${this.sensorName}")
                startWifiScan()
            }
        }

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Enter Network Credentials")
            .setView(dialogView)
            .setPositiveButton("Connect", null) // Set to null initially to prevent automatic dismiss
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                if (gatt != null) {
                    disconnectGatt()
                }
            }
            .setCancelable(false)
            .create()
            
        // Override the onClick to properly validate without dismissing on error
        alertDialog.setOnShowListener { dialog ->
            val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                // Validate inputs
                val sensorName = sensorNameInput.text.toString().trim()
                val ssid = ssidInput.text.toString().trim()
                
                // Check if this network is currently marked as open based on UI state
                val currentOpenState = passwordLayout.visibility == View.GONE
                
                val password = if (currentOpenState) "\\0" else passwordInput.text.toString().trim()

                // Check for empty fields
                when {
                    ssid.isEmpty() -> {
                        errorText.text = "Please select a network"
                        errorText.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    sensorName.isEmpty() -> {
                        errorText.text = "Please enter a sensor name"
                        errorText.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                    !currentOpenState && password.isEmpty() -> {
                        errorText.text = "Please enter a password"
                        errorText.visibility = View.VISIBLE
                        return@setOnClickListener
                    }
                }

                // If validation passes, proceed with the connection
                networkSSID = ssid
                networkPassword = password
                this@AddSensorActivity.sensorName = sensorName  // Save the sensor name to the class property
                
                // For testing purposes, just show the entered values
                val message = """
                    Sensor Name: $sensorName
                    Network SSID: $ssid
                    Password: ${if (currentOpenState) "Open Network" else password}
                """.trimIndent()
                Toast.makeText(this@AddSensorActivity, message, Toast.LENGTH_LONG).show()
                
                // Debug logs
                Log.d(TAG, "Network credentials captured - SSID: $networkSSID, Password length: ${networkPassword?.length ?: 0}")
                Log.d(TAG, "Sensor name captured: ${this@AddSensorActivity.sensorName}")
                Log.d(TAG, "Is open network: $currentOpenState")
                
                // Close dialog
                dialog.dismiss()
                
                // If we have a real GATT connection, proceed with the normal flow
                if (gatt != null) {
                    updateStatus("Writing configuration...")
                    currentUser?.let { user ->
                        val uid = user.uid
                        val halfLength = uid.length / 2
                        val firstHalf = uid.substring(0, halfLength)
                        Log.d(TAG, "Starting with first half of UID: $firstHalf")
                        
                        // Small delay to ensure dialog is dismissed before starting BLE operations
                        handler.postDelayed({
                            writeCharacteristic(gatt, USERID_UUID, firstHalf)
                        }, 100)
                    } ?: run {
                        Log.e(TAG, "No user logged in")
                        updateStatus("Error: No user logged in")
                        disconnectGatt()
                    }
                }
            }
        }
        
        currentCredentialsDialog = alertDialog
        alertDialog.show()
    }
    
    // Helper function to check if a network is open
    private fun isOpenNetwork(networkInfo: android.net.wifi.ScanResult): Boolean {
        return networkInfo.capabilities?.contains("WEP") == false && 
               networkInfo.capabilities?.contains("PSK") == false &&
               networkInfo.capabilities?.contains("EAP") == false
    }
    
    // Helper function to update password visibility based on network type
    private fun updatePasswordVisibility(passwordLayout: com.google.android.material.textfield.TextInputLayout, networkInfo: android.net.wifi.ScanResult?) {
        val openNetworkTagId = 1001 // Unique tag ID for the open network text
        
        // Check if the network is open
        val isOpenNetwork = networkInfo != null && isOpenNetwork(networkInfo)
        
        if (isOpenNetwork) {
            // Hide password field and show open network message
            passwordLayout.visibility = View.GONE
            
            // Check if we already added the open network message
            val parent = passwordLayout.parent as? ViewGroup
            if (parent != null) {
                // Look for an existing open network message
                var openNetworkText: TextView? = null
                for (i in 0 until parent.childCount) {
                    val child = parent.getChildAt(i)
                    if (child is TextView && child.tag == openNetworkTagId) {
                        openNetworkText = child
                        break
                    }
                }
                
                // If no open network message exists, create and add it
                if (openNetworkText == null) {
                    val newOpenNetworkText = TextView(this).apply {
                        text = "Open Network Detected -- No Password Needed"
                        textAlignment = View.TEXT_ALIGNMENT_CENTER
                        setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
                        setPadding(0, 16, 0, 16)
                        tag = openNetworkTagId
                    }
                    
                    val index = parent.indexOfChild(passwordLayout)
                    parent.addView(newOpenNetworkText, index)
                }
            }
        } else {
            // Show password field
            passwordLayout.visibility = View.VISIBLE
            
            // Remove open network message if it exists
            val parent = passwordLayout.parent as? ViewGroup
            if (parent != null) {
                // Find and remove any open network text
                for (i in parent.childCount - 1 downTo 0) {
                    val child = parent.getChildAt(i)
                    if (child is TextView && child.tag == openNetworkTagId) {
                        parent.removeView(child)
                        break
                    }
                }
            }
        }
    }

    private fun updateStatus(message: String) {
        runOnUiThread {
            statusText.text = message
            Log.d(TAG, message)
        }
    }

    private fun disconnectGatt() {
        if (!checkBluetoothPermission()) {
            updateStatus("Bluetooth permission required")
            return
        }

        try {
            Log.d(TAG, "Disconnecting and closing GATT connection...")
            bluetoothGatt?.disconnect()
            // Add a small delay before closing to ensure disconnect completes
            handler.postDelayed({
                bluetoothGatt?.close()
                bluetoothGatt = null
            }, 100)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException during disconnect: ${e.message}")
        }
    }

    private fun setupWifiScanReceiver() {
        wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                try {
                    if (success) {
                        scanSuccess()
                    } else {
                        scanFailure()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing WiFi scan results: ${e.message}")
                }
            }
        }
    }

    private fun scanSuccess() {
        try {
            if (!checkBluetoothPermission()) {
                updateStatus("Bluetooth permission required")
                return
            }
            val results = wifiManager.scanResults
            showWifiNetworksDialog(results)
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanSuccess: ${e.message}")
        }
    }

    private fun scanFailure() {
        try {
            // Handle scan failure - maybe show cached results
            if (!checkBluetoothPermission()) {
                updateStatus("Bluetooth permission required")
                return
            }
            val results = wifiManager.scanResults
            showWifiNetworksDialog(results)
        } catch (e: Exception) {
            Log.e(TAG, "Error in scanFailure: ${e.message}")
        }
    }

    private fun showWifiNetworksDialog(results: List<android.net.wifi.ScanResult>) {
        try {
            // Check if activity is finishing to prevent window leaks
            if (isFinishing) {
                Log.d(TAG, "Activity is finishing, not showing WiFi dialog")
                return
            }
            
            val networks = results
                .map { it.SSID }
                .distinct()
                .filter { it.isNotEmpty() }
                .toTypedArray()

            if (networks.isEmpty()) {
                Toast.makeText(this, "No WiFi networks found", Toast.LENGTH_SHORT).show()
                return
            }

            val builder = AlertDialog.Builder(this)
            builder.setTitle("Select WiFi Network")

            builder.setItems(networks) { dialog, which ->
                val selectedNetwork = networks[which]
                val networkInfo = results.find { it.SSID == selectedNetwork }
                
                // Update the current credentials dialog instead of creating a new one
                currentCredentialsView?.let { view ->
                    // Update the SSID input
                    val ssidInput = view.findViewById<EditText>(R.id.ssid_input)
                    ssidInput?.setText(selectedNetwork)
                    
                    // Update password visibility
                    val passwordLayout = view.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.password_layout)
                    if (passwordLayout != null && networkInfo != null) {
                        updatePasswordVisibility(passwordLayout, networkInfo)
                    }
                }
                
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

            builder.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing WiFi networks dialog: ${e.message}")
        }
    }

    private fun startWifiScan() {
        setupWifiScanReceiver()
        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)

        val success = wifiManager.startScan()
        if (!success) {
            // Scan start failed. Try using cached results
            scanFailure()
        }
    }

    private fun checkWifiPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                REQUEST_PERMISSIONS
            )
            return false
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            wifiScanReceiver?.let {
                try {
                    unregisterReceiver(it)
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering receiver: ${e.message}")
                }
            }
            wifiScanReceiver = null
            
            if (checkBluetoothPermission()) {
                stopScanning()
                disconnectGatt()
            }
            handler.removeCallbacksAndMessages(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy: ${e.message}")
        }
    }
}

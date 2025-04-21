package com.example.pyrosensor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.pyrosensor.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

//TODO: Implement the notification shutoff ability
//TODO: Implement new settings
class SettingsFragment : Fragment() { // Fragment for app settings

    private lateinit var binding: FragmentSettingsBinding
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var notificationManager: NotificationManager
    
    private val TAG = "SettingsFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize SharedPreferences - make sure this name matches NotificationService
        sharedPreferences = requireContext().getSharedPreferences("PyroSensorPrefs", Context.MODE_PRIVATE)
        
        // Initialize NotificationManager
        notificationManager = requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Log current preferences
        logCurrentPreferences()
        
        // Set up notification settings
        setupNotificationSettings()

        // Listener for logout button click
        binding.logoutButton.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh settings when returning to this fragment
        updateSwitchesFromPreferences()
        logCurrentPreferences()
    }
    
    private fun updateSwitchesFromPreferences() {
        // Read the current preferences
        val alarmEnabled = sharedPreferences.getBoolean("notifications_alarm_enabled", true)
        val warningEnabled = sharedPreferences.getBoolean("notifications_warning_enabled", true)
        val alarmSoundEnabled = sharedPreferences.getBoolean("notifications_alarm_sound_enabled", true)
        val warningSoundEnabled = sharedPreferences.getBoolean("notifications_warning_sound_enabled", true)
        val vibrationEnabled = sharedPreferences.getBoolean("notifications_vibration_enabled", true)
        
        // Update the UI switches without triggering listeners
        binding.switchAlarmNotifications.setOnCheckedChangeListener(null)
        binding.switchWarningNotifications.setOnCheckedChangeListener(null)
        binding.switchAlarmSound.setOnCheckedChangeListener(null)
        binding.switchWarningSound.setOnCheckedChangeListener(null)
        binding.switchVibration.setOnCheckedChangeListener(null)
        
        // Set the checked state
        binding.switchAlarmNotifications.isChecked = alarmEnabled
        binding.switchWarningNotifications.isChecked = warningEnabled
        binding.switchAlarmSound.isChecked = alarmSoundEnabled
        binding.switchWarningSound.isChecked = warningSoundEnabled
        binding.switchVibration.isChecked = vibrationEnabled
        
        // Reattach listeners
        setupSwitchListeners()
        
        Log.d(TAG, "Updated UI switches from preferences")
    }
    
    private fun saveSetting(key: String, value: Boolean) {
        // This method immediately commits a single setting change
        try {
            // Use commit() for immediate update
            val success = sharedPreferences.edit()
                .putBoolean(key, value)
                .commit()
            
            if (success) {
                Log.d(TAG, "Successfully saved setting: $key = $value")
                updateNotificationChannels()
                
                // Show brief toast to confirm setting was saved
                Toast.makeText(requireContext(), "Setting updated", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Failed to save setting: $key = $value")
            }
            
            logCurrentPreferences()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving setting: ${e.message}", e)
        }
    }
    
    private fun logCurrentPreferences() {
        Log.d(TAG, "==== CURRENT PREFERENCE VALUES IN SETTINGS ====")
        Log.d(TAG, "notifications_alarm_enabled: ${sharedPreferences.getBoolean("notifications_alarm_enabled", true)}")
        Log.d(TAG, "notifications_warning_enabled: ${sharedPreferences.getBoolean("notifications_warning_enabled", true)}")
        Log.d(TAG, "notifications_alarm_sound_enabled: ${sharedPreferences.getBoolean("notifications_alarm_sound_enabled", true)}")
        Log.d(TAG, "notifications_warning_sound_enabled: ${sharedPreferences.getBoolean("notifications_warning_sound_enabled", true)}")
        Log.d(TAG, "notifications_vibration_enabled: ${sharedPreferences.getBoolean("notifications_vibration_enabled", true)}")
        Log.d(TAG, "==== END PREFERENCE VALUES ====")
    }

    private fun setupNotificationSettings() {
        // Get current notification settings
        val alarmEnabled = sharedPreferences.getBoolean("notifications_alarm_enabled", true)
        val warningEnabled = sharedPreferences.getBoolean("notifications_warning_enabled", true)
        val alarmSoundEnabled = sharedPreferences.getBoolean("notifications_alarm_sound_enabled", true)
        val warningSoundEnabled = sharedPreferences.getBoolean("notifications_warning_sound_enabled", true)
        val vibrationEnabled = sharedPreferences.getBoolean("notifications_vibration_enabled", true)

        // Set initial states
        binding.switchAlarmNotifications.isChecked = alarmEnabled
        binding.switchWarningNotifications.isChecked = warningEnabled
        binding.switchAlarmSound.isChecked = alarmSoundEnabled
        binding.switchWarningSound.isChecked = warningSoundEnabled
        binding.switchVibration.isChecked = vibrationEnabled
        
        // Set up listeners for immediate saving
        setupSwitchListeners()
    }
    
    private fun setupSwitchListeners() {
        // Set up listeners that immediately save the setting when changed
        binding.switchAlarmNotifications.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Alarm notifications setting changed to: $isChecked")
            saveSetting("notifications_alarm_enabled", isChecked)
        }

        binding.switchWarningNotifications.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Warning notifications setting changed to: $isChecked")
            saveSetting("notifications_warning_enabled", isChecked)
        }

        binding.switchAlarmSound.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Alarm sound setting changed to: $isChecked")
            saveSetting("notifications_alarm_sound_enabled", isChecked)
        }

        binding.switchWarningSound.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Warning sound setting changed to: $isChecked")
            saveSetting("notifications_warning_sound_enabled", isChecked)
        }

        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            Log.d(TAG, "Vibration setting changed to: $isChecked")
            saveSetting("notifications_vibration_enabled", isChecked)
        }
    }

    private fun updateNotificationChannels() {
        Log.d(TAG, "Updating notification channels with new settings")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Update alarm channel
            val alarmChannel = NotificationChannel(
                NotificationService.CHANNEL_ID_ALARM,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority notifications for fire alarms"
                enableVibration(sharedPreferences.getBoolean("notifications_vibration_enabled", true))
                setSound(
                    if (sharedPreferences.getBoolean("notifications_alarm_sound_enabled", true))
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    else null,
                    null
                )
            }

            // Update warning channel
            val warningChannel = NotificationChannel(
                NotificationService.CHANNEL_ID_WARNING,
                "Warning Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for sensor warnings"
                enableVibration(sharedPreferences.getBoolean("notifications_vibration_enabled", true))
                setSound(
                    if (sharedPreferences.getBoolean("notifications_warning_sound_enabled", true))
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    else null,
                    null
                )
            }

            try {
                // Try to delete existing channels first
                notificationManager.deleteNotificationChannel(NotificationService.CHANNEL_ID_ALARM)
                notificationManager.deleteNotificationChannel(NotificationService.CHANNEL_ID_WARNING)
                
                // Create new channels with updated settings
                notificationManager.createNotificationChannel(alarmChannel)
                notificationManager.createNotificationChannel(warningChannel)
                
                Log.d(TAG, "Notification channels updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating notification channels: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Device is running Android < 8.0, notification channels not supported")
        }
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                logout()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun logout() {
        // Sign out from Firebase
        FirebaseAuth.getInstance().signOut()

        // Clear stored preferences
        sharedPreferences.edit().clear().apply()

        // Navigate to WelcomeActivity and clear back stack
        val intent = Intent(requireContext(), WelcomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        requireActivity().finish()
    }
}



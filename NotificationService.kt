package com.example.pyrosensor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.io.FileDescriptor
import java.io.IOException

class NotificationService : FirebaseMessagingService() {
    // Notification channel IDs and constants
    companion object {
        const val CHANNEL_ID_ALARM = "alarm_channel"
        const val CHANNEL_ID_WARNING = "warning_channel"
        const val NOTIFICATION_ID_ALARM = 1
        const val NOTIFICATION_ID_WARNING = 2
        private const val TAG = "NotificationService"
        
        // This service doesn't need to maintain an AlarmSoundPlayer instance
        // since we're using AlarmService for alarms
    }

    // Shared preferences for notification settings
    private lateinit var sharedPreferences: SharedPreferences

    // Initialize service and preferences
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NotificationService created")
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("PyroSensorPrefs", Context.MODE_PRIVATE)
        
        // Debug: Log all preferences
        logAllPreferences()
    }
    
    // Log current preference values for debugging
    private fun logAllPreferences() {
        Log.d(TAG, "==== CURRENT PREFERENCE VALUES ====")
        Log.d(TAG, "notifications_alarm_enabled: ${sharedPreferences.getBoolean("notifications_alarm_enabled", true)}")
        Log.d(TAG, "notifications_warning_enabled: ${sharedPreferences.getBoolean("notifications_warning_enabled", true)}")
        Log.d(TAG, "notifications_alarm_sound_enabled: ${sharedPreferences.getBoolean("notifications_alarm_sound_enabled", true)}")
        Log.d(TAG, "notifications_warning_sound_enabled: ${sharedPreferences.getBoolean("notifications_warning_sound_enabled", true)}")
        Log.d(TAG, "notifications_vibration_enabled: ${sharedPreferences.getBoolean("notifications_vibration_enabled", true)}")
        Log.d(TAG, "==== END PREFERENCE VALUES ====")
    }

    // Handle incoming Firebase Cloud Messages
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Log received message data
        Log.d(TAG, "Received message: ${remoteMessage.data}")
        
        // Debug: Log all preferences when a message is received
        logAllPreferences()

        // Check if user is signed in
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.d(TAG, "User not signed in, ignoring notification")
            return
        }

        // Get notification type and message
        val type = remoteMessage.data["type"] ?: return
        val message = remoteMessage.data["message"] ?: return
        val sensorName = remoteMessage.data["sensorName"] ?: "Unknown Sensor"
        val customSound = remoteMessage.data["sound"]
        
        Log.d(TAG, "Processing notification - Type: $type, Sound: $customSound")

        // Check notification settings - EXPLICITLY RELOAD preferences to ensure most up-to-date values
        sharedPreferences = getSharedPreferences("PyroSensorPrefs", Context.MODE_PRIVATE)
        val alarmsEnabled = sharedPreferences.getBoolean("notifications_alarm_enabled", true)
        val warningsEnabled = sharedPreferences.getBoolean("notifications_warning_enabled", true)
        
        Log.d(TAG, "Notification settings - Alarms enabled: $alarmsEnabled, Warnings enabled: $warningsEnabled")
        
        // Create notification based on type and settings
        when (type) {
            "alarm" -> {
                if (!alarmsEnabled) {
                    Log.d(TAG, "⛔ Alarm notifications are DISABLED in settings - notification BLOCKED")
                    return
                }
                
                Log.d(TAG, "✅ Alarm notifications are ENABLED, creating notification")
                createAlarmNotification(message, sensorName, customSound)
            }
            "warning" -> {
                if (!warningsEnabled) {
                    Log.d(TAG, "⛔ Warning notifications are DISABLED in settings - notification BLOCKED")
                    return
                }
                
                Log.d(TAG, "✅ Warning notifications are ENABLED, creating notification")
                createWarningNotification(message, sensorName)
            }
            else -> Log.d(TAG, "Unknown notification type: $type")
        }
    }

    // Create and show alarm notification
    private fun createAlarmNotification(message: String, sensorName: String, customSound: String?) {
        Log.d(TAG, "Creating alarm notification with sound: $customSound")
        
        // EXPLICITLY RELOAD preferences to ensure most up-to-date values
        sharedPreferences = getSharedPreferences("PyroSensorPrefs", Context.MODE_PRIVATE)
        
        // Double-check if alarm notifications are enabled (defensive coding)
        if (!sharedPreferences.getBoolean("notifications_alarm_enabled", true)) {
            Log.d(TAG, "⛔ Last minute check: Alarm notifications are disabled in settings")
            return
        }
        
        // Check if alarm sound is enabled in settings
        val alarmSoundEnabled = sharedPreferences.getBoolean("notifications_alarm_sound_enabled", true)
        val vibrationEnabled = sharedPreferences.getBoolean("notifications_vibration_enabled", true)
        
        Log.d(TAG, "User settings - Sound: $alarmSoundEnabled, Vibration: $vibrationEnabled")
        
        // List all raw resources to debug
        try {
            val fields = R.raw::class.java.fields
            Log.d(TAG, "Available raw resources: ${fields.joinToString { it.name }}")
            for (field in fields) {
                Log.d(TAG, "Raw resource: ${field.name} = ${field.getInt(null)}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing raw resources: ${e.message}")
        }
        
        // Get the sound URI if sound is enabled
        val soundUri = if (alarmSoundEnabled) {
            try {
                // Check if the raw resource exists
                val resourceId = R.raw.alarm
                Log.d(TAG, "Alarm sound resource ID: $resourceId")
                
                // Verify the file actually exists
                try {
                    val afd = applicationContext.resources.openRawResourceFd(resourceId)
                    val fileSize = afd.length
                    afd.close()
                    Log.d(TAG, "Successfully opened alarm sound file, size: $fileSize bytes")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open raw resource: ${e.message}")
                }
                
                if (resourceId > 0) {
                    Uri.parse("android.resource://$packageName/$resourceId")
                } else {
                    Log.e(TAG, "Resource ID is invalid, falling back to default")
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading custom sound: ${e.message}", e)
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
        } else {
            Log.d(TAG, "Alarm sound is disabled in settings")
            null
        }
        
        Log.d(TAG, "Using sound URI: $soundUri")

        // Delete existing channel to recreate with new settings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            deleteNotificationChannel(CHANNEL_ID_ALARM)
        }
        
        // Create notification channel for alarm with custom sound
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(
                CHANNEL_ID_ALARM, 
                "Alarm Notifications", 
                "High priority notifications for fire alarms",
                soundUri
            )
        }

        // Create intent for when notification is clicked
        val intent = Intent(this, WelcomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            action = "STOP_ALARM"
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Create broadcast intent to stop the alarm service
        val stopIntent = Intent(this, AlarmService::class.java).apply {
            action = "STOP_ALARM"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_ALARM)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Fire Alarm - $sensorName")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_notification, "Stop Alarm", stopPendingIntent)
            
        // Set vibration if enabled
        if (vibrationEnabled) {
            notificationBuilder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
        }

        // Use unique ID to avoid overwriting
        val notificationId = System.currentTimeMillis().toInt()
        
        // Show notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notificationBuilder.build())
        Log.d(TAG, "Alarm notification sent with ID: $notificationId")
        
        // Start the foreground service to play sound if enabled
        if (alarmSoundEnabled) {
            Log.d(TAG, "Starting alarm service to play sound continuously")
            AlarmService.startService(applicationContext, sensorName)
        } else {
            Log.d(TAG, "Not starting alarm service as sound is disabled in settings")
        }
    }

    // Create and show warning notification
    private fun createWarningNotification(message: String, sensorName: String) {
        Log.d(TAG, "Creating warning notification")
        
        // EXPLICITLY RELOAD preferences to ensure most up-to-date values
        sharedPreferences = getSharedPreferences("PyroSensorPrefs", Context.MODE_PRIVATE)
        
        // Double-check if warning notifications are enabled (defensive coding)
        if (!sharedPreferences.getBoolean("notifications_warning_enabled", true)) {
            Log.d(TAG, "⛔ Last minute check: Warning notifications are disabled in settings")
            return
        }
        
        // Check if warning sound is enabled in settings
        val warningSoundEnabled = sharedPreferences.getBoolean("notifications_warning_sound_enabled", true)
        val vibrationEnabled = sharedPreferences.getBoolean("notifications_vibration_enabled", true)
        
        Log.d(TAG, "User settings - Sound: $warningSoundEnabled, Vibration: $vibrationEnabled")
        
        // Determine sound URI based on settings
        val soundUri = if (warningSoundEnabled) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        } else {
            null
        }
        
        // Create notification channel for warnings
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel(
                CHANNEL_ID_WARNING, 
                "Warning Notifications", 
                "Notifications for sensor warnings",
                soundUri
            )
        }

        // Create intent for when notification is clicked
        val intent = Intent(this, WelcomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_WARNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Warning - $sensorName")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            
        // Add sound if enabled for APIs < 26
        if (warningSoundEnabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            notificationBuilder.setSound(soundUri)
        }
        
        // Add vibration if enabled
        if (vibrationEnabled) {
            notificationBuilder.setVibrate(longArrayOf(500, 500, 500))
        }

        // Show notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_WARNING, notificationBuilder.build())
        Log.d(TAG, "Warning notification sent with ID: $NOTIFICATION_ID_WARNING")
    }
    
    private fun deleteNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                notificationManager.deleteNotificationChannel(channelId)
                Log.d(TAG, "Deleted notification channel: $channelId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting notification channel: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel(
        channelId: String, 
        channelName: String, 
        channelDescription: String,
        soundUri: Uri? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = channelDescription
                enableVibration(true)
                
                // Set custom sound if provided
                if (soundUri != null) {
                    try {
                        val audioAttributes = AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                        setSound(soundUri, audioAttributes)
                        Log.d(TAG, "Set custom sound for channel $channelId: $soundUri")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error setting sound for channel: ${e.message}")
                    }
                }
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Created notification channel: $channelId")
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NotificationService being destroyed")
        // We're not using AlarmSoundPlayer in this service anymore, so no need to release it
    }
} 
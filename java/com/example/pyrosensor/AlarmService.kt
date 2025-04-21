package com.example.pyrosensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.random.Random

/**
 * Foreground service to keep the alarm sound playing
 */
class AlarmService : Service() {
    companion object {
        private const val TAG = "AlarmService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "alarm_service_channel"
        
        fun startService(context: Context, sensorName: String) {
            Log.d(TAG, "startService called for sensor: $sensorName")
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra("sensorName", sensorName)
                // Add a random number to ensure the intent is unique
                putExtra("random", Random.nextInt())
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                    Log.d(TAG, "startForegroundService called successfully")
                } else {
                    context.startService(intent)
                    Log.d(TAG, "startService called successfully")
                }
                Log.d(TAG, "Alarm service start requested")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service: ${e.message}", e)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Alarm service stop requested")
        }
    }
    
    private var alarmSoundPlayer: AlarmSoundPlayer? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var sharedPreferences: SharedPreferences
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService created")
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("PyroSensorPrefs", Context.MODE_PRIVATE)
        // Create a dedicated instance for this service, not the shared singleton
        alarmSoundPlayer = AlarmSoundPlayer.getServiceInstance(applicationContext)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService started with startId: $startId")
        
        val sensorName = intent?.getStringExtra("sensorName") ?: "Unknown Sensor"
        
        // Check user preferences
        val alarmSoundEnabled = sharedPreferences.getBoolean("notifications_alarm_sound_enabled", true)
        Log.d(TAG, "User preference for alarm sound: $alarmSoundEnabled")
        
        // Create notification channel
        createNotificationChannel()
        
        // Start as foreground service with high priority
        try {
            val notification = createNotification(sensorName)
            
            // Start foreground service within 5 seconds of onCreate
            Log.d(TAG, "Starting foreground service with notification")
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "Started as foreground service successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}", e)
        }
        
        // Play the alarm sound - try both approaches, but only if sound is enabled
        if (alarmSoundEnabled) {
            try {
                // First try to use our AlarmSoundPlayer with looping enabled for service
                alarmSoundPlayer?.playAlarmSound(repeatCount = 0, isLooping = true)
                
                // As a backup, also try direct MediaPlayer approach
                if (mediaPlayer == null) {
                    Log.d(TAG, "Creating backup MediaPlayer")
                    try {
                        val resourceId = R.raw.alarm
                        mediaPlayer = MediaPlayer.create(applicationContext, resourceId).apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .build()
                            )
                            isLooping = true
                            setVolume(1.0f, 1.0f)
                            Log.d(TAG, "Backup MediaPlayer created successfully, duration: ${duration}ms")
                            start()
                            Log.d(TAG, "Backup MediaPlayer started")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error with backup MediaPlayer: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error playing alarm sound: ${e.message}", e)
            }
        } else {
            Log.d(TAG, "Not playing alarm sound because it's disabled in user settings")
        }
        
        // Keep running until explicitly stopped
        return START_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarm Service"
            val descriptionText = "Keeps the alarm sound playing"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                
                // Check if vibration is enabled
                val vibrationEnabled = sharedPreferences.getBoolean("notifications_vibration_enabled", true)
                enableVibration(vibrationEnabled)
                
                // Don't set sound for this channel - we're playing it manually
                setSound(null, null)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Created notification channel for foreground service")
        }
    }
    
    private fun createNotification(sensorName: String): Notification {
        // Create an intent for when the notification is tapped
        val contentIntent = Intent(this, WelcomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE
        )
        
        // Check if vibration is enabled
        val vibrationEnabled = sharedPreferences.getBoolean("notifications_vibration_enabled", true)
        
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔥 Fire Alarm Active 🔥")
            .setContentText("Alarm active for $sensorName")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            
        // Add vibration if enabled
        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(1000, 1000, 1000, 1000))
        }
        
        return builder.build()
    }
    
    override fun onDestroy() {
        Log.d(TAG, "AlarmService being destroyed")
        try {
            // Stop and release both media players
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
            
            alarmSoundPlayer?.stopSound()
            alarmSoundPlayer?.release()
            alarmSoundPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error during onDestroy: ${e.message}", e)
        }
        super.onDestroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 
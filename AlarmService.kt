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
    // Service constants and companion object methods
    companion object {
        private const val TAG = "AlarmService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "alarm_service_channel"
        private const val MAX_LOOPS = 2
        
        // Start the alarm service with sensor name
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
        
        // Stop the alarm service
        fun stopService(context: Context) {
            val intent = Intent(context, AlarmService::class.java)
            context.stopService(intent)
            Log.d(TAG, "Alarm service stop requested")
        }
    }
    
    // Service components and state
    private var alarmSoundPlayer: AlarmSoundPlayer? = null
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var alarmSoundEnabled: Boolean = true
    private var loopCount: Int = 0
    
    // Initialize service and components
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AlarmService created")
        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("PyroSensorPrefs", Context.MODE_PRIVATE)
        // Create a dedicated instance for this service, not the shared singleton
        alarmSoundPlayer = AlarmSoundPlayer.getServiceInstance(applicationContext)
    }
    
    // Handle service start command
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmService started with startId: $startId")
        
        val sensorName = intent?.getStringExtra("sensorName") ?: "Unknown Sensor"
        
        // Check user preferences
        alarmSoundEnabled = sharedPreferences.getBoolean("notifications_alarm_sound_enabled", true)
        Log.d(TAG, "User preference for alarm sound: $alarmSoundEnabled")
        
        // Play the alarm sound - try both approaches, but only if sound is enabled
        if (alarmSoundEnabled) {
            playAlarmSound()
        } else {
            Log.d(TAG, "Alarm sound is disabled in settings, stopping service")
            stopSelf()
        }
        
        return START_STICKY
    }
    
    // Play alarm sound using both players
    private fun playAlarmSound() {
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
                            
                            // Add completion listener to track loops
                            setOnCompletionListener {
                                loopCount++
                                Log.d(TAG, "Alarm loop completed. Count: $loopCount")
                                if (loopCount >= MAX_LOOPS) {
                                    Log.d(TAG, "Maximum loops reached, stopping service")
                                    stopSelf()
                                }
                            }
                            
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
    }
    
    // Clean up resources when service is destroyed
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
    
    // Service binding (not used)
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
} 
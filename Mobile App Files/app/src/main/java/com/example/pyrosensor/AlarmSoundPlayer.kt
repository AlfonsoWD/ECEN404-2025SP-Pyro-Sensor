package com.example.pyrosensor

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Dedicated class to handle playing the alarm sound correctly
 */
class AlarmSoundPlayer(private val context: Context) {
    companion object {
        private const val TAG = "AlarmSoundPlayer"
        private const val ALARM_DURATION_MS = 30000L // 30 seconds
        private const val DEFAULT_REPEATS = 2 // Play twice by default
        
        @Volatile
        private var instance: AlarmSoundPlayer? = null
        
        fun getInstance(context: Context): AlarmSoundPlayer {
            return instance ?: synchronized(this) {
                instance ?: AlarmSoundPlayer(context.applicationContext).also { instance = it }
            }
        }
        
        // New method to get a separate instance for the AlarmService
        fun getServiceInstance(context: Context): AlarmSoundPlayer {
            // Create a new instance specifically for the service
            return AlarmSoundPlayer(context.applicationContext)
        }
    }
    
    private var mediaPlayer: MediaPlayer? = null
    private var isSoundPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var repeatsLeft = 0 // Counter to track how many times to repeat the sound
    
    /**
     * Play the alarm sound directly from raw resources
     * @param repeatCount Number of times to play the sound (default is 2)
     * @param isLooping Whether to loop continuously (overrides repeatCount if true)
     */
    fun playAlarmSound(repeatCount: Int = DEFAULT_REPEATS, isLooping: Boolean = false) {
        Log.d(TAG, "Playing alarm sound from raw resource (repeatCount=$repeatCount, isLooping=$isLooping)")
        
        if (isSoundPlaying) {
            Log.d(TAG, "Alarm sound already playing")
            return
        }
        
        try {
            stopSound()
            
            // Set the number of repeats
            repeatsLeft = repeatCount
            
            // Use direct resource file descriptor to avoid URI issues
            val resourceId = R.raw.alarm
            
            Log.d(TAG, "Using raw resource ID: $resourceId")
            
            // Print detailed information about available sound files
            listAllSoundFiles()
            
            try {
                // Read and log file info and first few bytes to validate it's the right file
                val afd = context.resources.openRawResourceFd(resourceId)
                val fileSize = afd.length
                Log.d(TAG, "Successfully opened raw resource, size: $fileSize bytes")
                
                // Read first 16 bytes of the file to identify it
                context.resources.openRawResource(resourceId).use { inputStream ->
                    val bytes = ByteArray(Math.min(fileSize.toInt(), 1024))
                    val bytesRead = inputStream.read(bytes)
                    Log.d(TAG, "File signature: ${bytesToHex(bytes.sliceArray(0 until Math.min(16, bytesRead)))}")
                    Log.d(TAG, "MD5 hash of first 1KB: ${calculateMD5(bytes)}")
                }
                
                Log.d(TAG, "Creating MediaPlayer directly from raw resource")
                
                // Create new media player directly from resource ID
                mediaPlayer = MediaPlayer.create(context, resourceId).apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    
                    // Only set to looping if explicitly requested
                    this.isLooping = isLooping
                    setVolume(1.0f, 1.0f)
                    
                    // Get the duration without creating a shadowing variable
                    Log.d(TAG, "MediaPlayer prepared, sound duration: ${this.duration}ms")
                    
                    // Start playback
                    isSoundPlaying = true
                    start()
                    Log.d(TAG, "Alarm sound started playing")
                    
                    // Schedule stop after duration - only in non-service usage
                    // Service should manage its own lifecycle
                    handler.postDelayed({
                        Log.d(TAG, "Sound duration timer expired")
                        stopSound()
                    }, ALARM_DURATION_MS)
                    
                    setOnCompletionListener {
                        Log.d(TAG, "MediaPlayer completed playback")
                        if (isLooping) {
                            // Do nothing, the sound will loop automatically
                            Log.d(TAG, "Sound is set to loop continuously")
                        } else if (repeatsLeft > 1) {
                            // We still have repeats left, decrement and play again
                            repeatsLeft--
                            Log.d(TAG, "Repeating sound, $repeatsLeft plays remaining")
                            it.seekTo(0)
                            it.start()
                        } else {
                            // No more repeats, stop the sound
                            Log.d(TAG, "No more repeats left, stopping sound")
                            stopSound()
                        }
                    }
                    
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                        stopSound()
                        false
                    }
                }
                
                // Close the asset file descriptor - MediaPlayer.create doesn't need it
                afd.close()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading alarm sound: ${e.message}", e)
            }
        } catch (e: IOException) {
            Log.e(TAG, "IO error playing alarm sound: ${e.message}", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error playing alarm sound: ${e.message}", e)
        }
    }
    
    private fun listAllSoundFiles() {
        try {
            // List all raw resources 
            val rawFields = R.raw::class.java.fields
            Log.d(TAG, "All raw resources (${rawFields.size}): ${rawFields.joinToString { it.name }}")
            
            // Log size of each resource file
            for (field in rawFields) {
                val resourceId = field.getInt(null)
                try {
                    val afd = context.resources.openRawResourceFd(resourceId)
                    Log.d(TAG, "Resource ${field.name} (ID: $resourceId): ${afd.length} bytes")
                    afd.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking resource ${field.name}: ${e.message}")
                }
            }
            
            // List all notification sounds from system
            val notificationsDir = "/system/media/audio/notifications"
            Log.d(TAG, "System notification sounds in: $notificationsDir")
            
            // List all alarm sounds from system
            val alarmsDir = "/system/media/audio/alarms"
            Log.d(TAG, "System alarm sounds in: $alarmsDir")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error listing sound files: ${e.message}", e)
        }
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789ABCDEF".toCharArray()
        val result = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val i = byte.toInt() and 0xff
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0f])
        }
        return result.toString()
    }
    
    private fun calculateMD5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        md.update(bytes)
        return bytesToHex(md.digest())
    }
    
    /**
     * Stop the alarm sound
     */
    fun stopSound() {
        Log.d(TAG, "Stopping alarm sound - caller: ${Exception().stackTrace[1]}")
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.reset()
                it.release()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping media player: ${e.message}", e)
            }
        }
        mediaPlayer = null
        isSoundPlaying = false
        handler.removeCallbacksAndMessages(null)
    }
    
    /**
     * Called when the service is destroyed
     */
    fun release() {
        Log.d(TAG, "Releasing AlarmSoundPlayer - caller: ${Exception().stackTrace[1]}")
        stopSound()
        // Only clear the singleton if this is the singleton instance
        if (instance == this) {
            instance = null
        }
    }
} 
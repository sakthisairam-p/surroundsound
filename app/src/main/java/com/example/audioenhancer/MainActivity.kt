package com.example.audioenhancer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var trackInfo: android.widget.TextView
    private lateinit var nowPlayingTitle: android.widget.TextView
    
    private val metadataReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "TRACK_METADATA_UPDATE") {
                val track = intent.getStringExtra("track") ?: "Unknown"
                val artist = intent.getStringExtra("artist") ?: "Unknown"
                nowPlayingTitle.text = "NOW PLAYING ON SPOTIFY"
                trackInfo.text = "$track - $artist"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
        startAudioService()
        setupUI()
        setupSpotifyControls()
        
        // Register metadata listener
        val filter = android.content.IntentFilter("TRACK_METADATA_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(metadataReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(metadataReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(metadataReceiver)
    }

    private fun setupSpotifyControls() {
        trackInfo = findViewById(R.id.trackInfo)
        nowPlayingTitle = findViewById(R.id.nowPlayingTitle)
        
        Toast.makeText(this, "Enable 'Device Broadcast Status' in Spotify Settings for track info", Toast.LENGTH_LONG).show()

        val btnPrev = findViewById<android.widget.Button>(R.id.btnPrev)
        val btnPlayPause = findViewById<android.widget.Button>(R.id.btnPlayPause)
        val btnNext = findViewById<android.widget.Button>(R.id.btnNext)

        btnPrev.setOnClickListener { sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        btnNext.setOnClickListener { sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) }
        btnPlayPause.setOnClickListener { 
            sendMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            btnPlayPause.text = if (btnPlayPause.text == "▶") "⏸" else "▶"
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val eventDown = android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, keyCode)
        val eventUp = android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, keyCode)
        audioManager.dispatchMediaKeyEvent(eventDown)
        audioManager.dispatchMediaKeyEvent(eventUp)
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun startAudioService() {
        val serviceIntent = Intent(this, AudioService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun broadcastSettingsUpdate() {
        val intent = Intent(this, AudioService::class.java).apply {
            action = "UPDATE_SETTINGS"
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupUI() {
        val bassSlider = findViewById<Slider>(R.id.bassSlider)
        val surroundSlider = findViewById<Slider>(R.id.surroundSlider)
        val volumeSlider = findViewById<Slider>(R.id.volumeSlider)
        val powerSwitch = findViewById<SwitchMaterial>(R.id.powerSwitch)
        
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val prefs = getSharedPreferences("AudioProPrefs", Context.MODE_PRIVATE)

        // Load Last Saved Status Or Initialize Default Settings
        powerSwitch.isChecked = prefs.getBoolean("power_switch", true)
        bassSlider.value = prefs.getInt("bass_value", 0).toFloat()
        surroundSlider.value = prefs.getInt("surround_value", 0).toFloat()

        // 1. Sync Volume Slider with System Master Volume
        val maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
        val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        volumeSlider.valueTo = maxVol.toFloat()
        volumeSlider.value = currentVol.toFloat()

        volumeSlider.addOnChangeListener { _, value, _ ->
            audioManager.setStreamVolume(
                android.media.AudioManager.STREAM_MUSIC,
                value.toInt(),
                0 // 0 means don't show the system volume popup 
            )
        }

        // 2. Bass Slider
        bassSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.edit().putInt("bass_value", value.toInt()).apply()
                broadcastSettingsUpdate()
            }
        }

        // 3. Surround Slider
        surroundSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                prefs.edit().putInt("surround_value", value.toInt()).apply()
                broadcastSettingsUpdate()
            }
        }

        // 4. Power Switch 
        powerSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("power_switch", isChecked).apply()
            broadcastSettingsUpdate()
            Toast.makeText(this, "System Effects: ${if(isChecked) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }
    }
}

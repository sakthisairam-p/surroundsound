package com.example.audioenhancer

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.media.audiofx.AudioEffect
import android.os.*
import android.util.Log

class AudioService : Service() {

    private var globalDynamics: DynamicsProcessing? = null
    private var visualizer: android.media.audiofx.Visualizer? = null
    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    data class AudioSessionEffects(
        var equalizer: Equalizer? = null,
        var bassBoost: BassBoost? = null,
        var virtualizer: Virtualizer? = null,
        var loudness: android.media.audiofx.LoudnessEnhancer? = null
    ) {
        fun release() {
            try { equalizer?.release() } catch (e: Exception) {}
            try { bassBoost?.release() } catch (e: Exception) {}
            try { virtualizer?.release() } catch (e: Exception) {}
            try { loudness?.release() } catch (e: Exception) {}
        }
    }

    private val activeSessions = mutableMapOf<Int, AudioSessionEffects>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        
        // Key fix: Attach a visualizer to session 0 to keep the system audio hook alive
        setupGlobalHook()
        
        applyEffectsToSession(0)
    }

    private fun setupGlobalHook() {
        try {
            // Priority 1000 to be on top of other effects
            visualizer = android.media.audiofx.Visualizer(0).apply {
                enabled = false
                captureSize = android.media.audiofx.Visualizer.getCaptureSizeRange()[1]
                enabled = true
            }
            
            loudnessEnhancer = android.media.audiofx.LoudnessEnhancer(0).apply {
                enabled = true
            }
            
            applyGlobalDynamicsFallback()
            
            Log.d("SYSTEM_AUDIO", "Global Hardware Hook Established (Session 0)")
        } catch (e: Exception) {
            Log.e("SYSTEM_AUDIO", "Global hook failed: ${e.message}")
        }
    }

    private fun startForegroundService() {
        val channelId = "AUDIO_PRO_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "3D Audio Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
        } else {
            Notification.Builder(this)
        }
            .setContentTitle("3D Surround Pro is ACTIVE")
            .setContentText("Controlling system audio in real-time...")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(1, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val sessionId = intent?.getIntExtra("SESSION_ID", -1) ?: -1

        if (action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION && sessionId != -1) {
            Log.d("SYSTEM_AUDIO", "Latching to App Session: $sessionId")
            applyEffectsToSession(sessionId)
        } else if (action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION && sessionId != -1) {
            activeSessions.remove(sessionId)?.release()
        } else if (action == "UPDATE_SETTINGS") {
            updateAllSessions()
        }

        return START_STICKY
    }

    private fun applyGlobalDynamicsFallback() {
        if (globalDynamics == null) {
            try {
                val builder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.Config.VARIANT_FAVORITE_PRESET,
                    2, true, 4, true, 4, true, 4, true
                )
                globalDynamics = DynamicsProcessing(1000, 0, builder.build())
                globalDynamics?.enabled = true
            } catch (e: Exception) {
                Log.e("SYSTEM_AUDIO", "Failed to start global dynamics: ${e.message}")
            }
        }
    }

    private fun applyEffectsToSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) return

        try {
            val effects = AudioSessionEffects()
            
            effects.equalizer = Equalizer(1000, sessionId)
            effects.bassBoost = BassBoost(1000, sessionId)
            effects.virtualizer = Virtualizer(1000, sessionId)
            effects.loudness = android.media.audiofx.LoudnessEnhancer(sessionId)
            
            activeSessions[sessionId] = effects
            updateSessionSettings(effects)
            
            Log.d("SYSTEM_AUDIO", "Attached controls to session $sessionId")
        } catch (e: Exception) {
            Log.e("SYSTEM_AUDIO", "Failed to attach to $sessionId: ${e.message}")
        }
    }

    private fun updateAllSessions() {
        val prefs = getSharedPreferences("AudioProPrefs", Context.MODE_PRIVATE)
        val power = prefs.getBoolean("power_switch", true)
        
        try { 
            globalDynamics?.enabled = power 
            loudnessEnhancer?.enabled = power
        } catch (e: Exception) {}

        for ((_, effects) in activeSessions) {
            updateSessionSettings(effects)
        }
    }

    private fun updateSessionSettings(effects: AudioSessionEffects) {
        val prefs = getSharedPreferences("AudioProPrefs", Context.MODE_PRIVATE)
        val power = prefs.getBoolean("power_switch", true)
        val bass = prefs.getInt("bass_value", 0)
        val surround = prefs.getInt("surround_value", 0)

        try {
            effects.equalizer?.enabled = power
            effects.loudness?.enabled = power
            
            effects.bassBoost?.let {
                it.enabled = power
                if (it.strengthSupported && power) {
                    it.setStrength(bass.toShort())
                }
            }
            
            effects.virtualizer?.let {
                it.enabled = power
                if (it.strengthSupported && power) {
                    it.setStrength(surround.toShort())
                }
            }
        } catch (e: Exception) {
            Log.e("SYSTEM_AUDIO", "Update error: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for ((_, effects) in activeSessions) {
            effects.release()
        }
        activeSessions.clear()
        try { 
            globalDynamics?.release() 
            visualizer?.release()
            loudnessEnhancer?.release()
        } catch(e: Exception){}
    }
}

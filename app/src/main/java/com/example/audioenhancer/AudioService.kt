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

    data class AudioSessionEffects(
        var equalizer: Equalizer? = null,
        var bassBoost: BassBoost? = null,
        var virtualizer: Virtualizer? = null
    ) {
        fun release() {
            try { equalizer?.release() } catch (e: Exception) {}
            try { bassBoost?.release() } catch (e: Exception) {}
            try { virtualizer?.release() } catch (e: Exception) {}
        }
    }

    private val activeSessions = mutableMapOf<Int, AudioSessionEffects>()
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        applyGlobalDynamicsFallback()
        applyEffectsToSession(0)
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
            .setContentText("Enhancing background audio in real-time...")
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
            Log.d("SYSTEM_AUDIO", "Opening Session: $sessionId")
            applyEffectsToSession(sessionId)
        } else if (action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION && sessionId != -1) {
            Log.d("SYSTEM_AUDIO", "Closing Session: $sessionId")
            activeSessions.remove(sessionId)?.release()
        } else if (action == "UPDATE_SETTINGS") {
            Log.d("SYSTEM_AUDIO", "Updating active sessions...")
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
                Log.d("SYSTEM_AUDIO", "Latched to Hardware Fallback (DynamicsProcessing)")
            } catch (e: Exception) {
                Log.e("SYSTEM_AUDIO", "Failed to start global dynamics: ${e.message}")
            }
        }
    }

    private fun applyEffectsToSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) return

        try {
            val effects = AudioSessionEffects()
            
            // Priority 1000 ensures our app takes control
            effects.equalizer = Equalizer(1000, sessionId)
            effects.bassBoost = BassBoost(1000, sessionId)
            effects.virtualizer = Virtualizer(1000, sessionId)
            
            activeSessions[sessionId] = effects
            updateSessionSettings(effects)
            
            Log.d("SYSTEM_AUDIO", "Successfully attached effects to session $sessionId")
        } catch (e: Exception) {
            Log.e("SYSTEM_AUDIO", "Failed to attach to $sessionId: ${e.message}")
        }
    }

    private fun updateAllSessions() {
        // Update global fallback
        val prefs = getSharedPreferences("AudioProPrefs", Context.MODE_PRIVATE)
        val power = prefs.getBoolean("power_switch", true)
        try { globalDynamics?.enabled = power } catch (e: Exception) {}

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
            Log.e("SYSTEM_AUDIO", "Error updating settings: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for ((_, effects) in activeSessions) {
            effects.release()
        }
        activeSessions.clear()
        try { globalDynamics?.release() } catch(e: Exception){}
    }
}

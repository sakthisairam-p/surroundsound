package com.example.audioenhancer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log

class AudioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)

        if (sessionId != -1 && 
            (action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION || 
             action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
            
            Log.d("SYSTEM_AUDIO", "Global Audio Session intent received: $action (ID: $sessionId)")
            
            val serviceIntent = Intent(context, AudioService::class.java).apply {
                this.action = action
                putExtra("SESSION_ID", sessionId)
            }
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e("SYSTEM_AUDIO", "Failed to start service from receiver: ${e.message}")
            }
        }
    }
}

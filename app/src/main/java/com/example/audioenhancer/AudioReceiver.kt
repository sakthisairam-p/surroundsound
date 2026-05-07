package com.example.audioenhancer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log

class AudioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        // 1. Handle Audio Session (for EQ effects)
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        if (sessionId != -1 && 
            (action == AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION || 
             action == AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)) {
            
            Log.d("SYSTEM_AUDIO", "Audio Session intent received: $action (ID: $sessionId)")
            sendToService(context, action, sessionId)
        }

        // 2. Handle Spotify Metadata (for track info)
        if (action == "com.spotify.music.metadatachanged") {
            val track = intent.getStringExtra("track")
            val artist = intent.getStringExtra("artist")
            val album = intent.getStringExtra("album")
            Log.d("SYSTEM_AUDIO", "Spotify Playing: $track by $artist")
            
            // Forward metadata to service or activity
            val metaIntent = Intent("TRACK_METADATA_UPDATE").apply {
                putExtra("track", track)
                putExtra("artist", artist)
                putExtra("album", album)
            }
            context.sendBroadcast(metaIntent)
        }
    }

    private fun sendToService(context: Context, action: String, sessionId: Int) {
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
            Log.e("SYSTEM_AUDIO", "Failed to start service: ${e.message}")
        }
    }
}

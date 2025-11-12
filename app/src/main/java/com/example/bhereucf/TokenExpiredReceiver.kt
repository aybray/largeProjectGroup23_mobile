package com.example.bhereucf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.activity.ComponentActivity

/**
 * BroadcastReceiver helper for handling JWT token expiration.
 * Activities can use this to automatically redirect to login when token expires.
 */
class TokenExpiredReceiver(
    private val activity: ComponentActivity,
    private val onTokenExpired: () -> Unit
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "TokenExpiredReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == JwtInterceptor.ACTION_TOKEN_EXPIRED) {
            Log.d(TAG, "Token expired - redirecting to login")
            onTokenExpired()
        }
    }

    /**
     * Register this receiver to listen for token expiration events.
     * Should be called in onCreate() and unregistered in onDestroy().
     */
    fun register() {
        val filter = IntentFilter(JwtInterceptor.ACTION_TOKEN_EXPIRED)
        activity.registerReceiver(this, filter)
    }

    /**
     * Unregister this receiver.
     * Should be called in onDestroy().
     */
    fun unregister() {
        try {
            activity.unregisterReceiver(this)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered, ignore
            Log.d(TAG, "Receiver not registered, ignoring unregister")
        }
    }
}


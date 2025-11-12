package com.example.bhereucf

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import org.json.JSONObject

/**
 * Manages JWT token storage and retrieval using SharedPreferences.
 * Provides secure storage for authentication tokens across app sessions.
 */
object JwtTokenManager {
    private const val PREFS_NAME = "JWT_PREFS"
    private const val KEY_JWT_TOKEN = "jwt_token"
    private const val TAG = "JwtTokenManager"

    /**
     * Save JWT token to SharedPreferences
     * @param context Application or Activity context
     * @param token JWT token string to store
     */
    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JWT_TOKEN, token)
            .apply()
    }

    /**
     * Retrieve JWT token from SharedPreferences
     * @param context Application or Activity context
     * @return JWT token string, or null if not found
     */
    fun getToken(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_JWT_TOKEN, null)
    }

    /**
     * Clear JWT token from SharedPreferences (logout)
     * @param context Application or Activity context
     */
    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_JWT_TOKEN)
            .apply()
    }

    /**
     * Check if a JWT token exists
     * @param context Application or Activity context
     * @return true if token exists, false otherwise
     */
    fun hasToken(context: Context): Boolean {
        return getToken(context) != null
    }

    /**
     * Extract userId from JWT token payload (without verification).
     * This is safe because we're just extracting data to send to the backend,
     * which will verify the token.
     * @param context Application or Activity context
     * @return userId string, or null if token is missing or invalid
     */
    fun getUserIdFromToken(context: Context): String? {
        val token = getToken(context) ?: return null
        
        try {
            // JWT format: header.payload.signature
            val parts = token.split(".")
            if (parts.size != 3) {
                Log.e(TAG, "Invalid JWT format")
                return null
            }
            
            // Decode the payload (second part)
            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            
            // Parse JSON payload
            val jsonObject = JSONObject(decodedString)
            val userId = jsonObject.getString("id")
            
            Log.d(TAG, "Extracted userId from JWT: $userId")
            return userId
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting userId from JWT", e)
            return null
        }
    }
}


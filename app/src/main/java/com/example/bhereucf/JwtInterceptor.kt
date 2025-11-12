package com.example.bhereucf

import android.content.Context
import android.content.Intent
import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor that automatically adds JWT token to all API requests.
 * Also handles 401 Unauthorized responses (token expired/invalid) by clearing
 * the token and broadcasting an intent to notify activities.
 */
class JwtInterceptor(private val context: Context) : Interceptor {
    
    companion object {
        private const val TAG = "JwtInterceptor"
        const val ACTION_TOKEN_EXPIRED = "com.example.bhereucf.TOKEN_EXPIRED"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Get JWT token from storage
        val token = JwtTokenManager.getToken(context)
        
        // Add Authorization header if token exists
        val newRequest = if (token != null) {
            Log.d(TAG, "Adding JWT token to request: ${originalRequest.url}")
            Log.d(TAG, "Token (first 20 chars): ${token.take(20)}...")
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            Log.w(TAG, "No JWT token found for request: ${originalRequest.url}")
            originalRequest
        }
        
        // Proceed with the request
        val response = chain.proceed(newRequest)
        
        // Handle 401 Unauthorized responses (token expired or invalid)
        if (response.code == 401) {
            Log.w(TAG, "Received 401 Unauthorized - token may be expired or invalid")
            JwtTokenManager.clearToken(context)
            
            // Broadcast intent to notify activities that token has expired
            val intent = Intent(ACTION_TOKEN_EXPIRED)
            context.sendBroadcast(intent)
        }
        
        return response
    }
}


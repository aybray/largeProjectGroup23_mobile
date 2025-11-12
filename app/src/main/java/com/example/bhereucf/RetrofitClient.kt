package com.example.bhereucf

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Base URL - must end with / for Retrofit to properly combine with endpoints
    private const val BASE_URL = "https://lp.ilovenarwhals.xyz/"

    // Application context for JWT interceptor
    private var applicationContext: Context? = null

    /**
     * Initialize RetrofitClient with application context.
     * Should be called from Application class or MainActivity onCreate.
     */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        Log.d("Retrofit", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.HEADERS  // Changed to HEADERS to see Authorization header
    }

    private val jwtInterceptor: JwtInterceptor? by lazy {
        applicationContext?.let { JwtInterceptor(it) }
    }

    private val okHttpClient: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
        
        // Add JWT interceptor FIRST (it will execute last, adding header before logging)
        jwtInterceptor?.let { builder.addInterceptor(it) }
        
        // Add logging interceptor LAST (it will execute first, logging after header is added)
        builder.addInterceptor(loggingInterceptor)
        
        builder.build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}


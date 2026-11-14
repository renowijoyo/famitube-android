package com.kidstube.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    // 10.0.2.2 is the default IP address to connect to host computer's localhost from Android Emulator
    const val DEFAULT_BASE_URL = "http://10.0.2.2:4000/api/"

    private var currentBaseUrl = DEFAULT_BASE_URL
    private var currentAuthToken: String = ""
    private var apiService: KidsTubeApiService? = null

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val reqBuilder = chain.request().newBuilder()
                if (currentAuthToken.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $currentAuthToken")
                }
                chain.proceed(reqBuilder.build())
            }
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun updateAuthToken(token: String) {
        currentAuthToken = token.trim()
    }

    fun getApiService(baseUrl: String = currentBaseUrl): KidsTubeApiService {
        val formattedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        if (apiService == null || currentBaseUrl != formattedUrl) {
            currentBaseUrl = formattedUrl
            apiService = Retrofit.Builder()
                .baseUrl(formattedUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(KidsTubeApiService::class.java)
        }
        return apiService!!
    }

    fun updateBaseUrl(newUrl: String) {
        val formatted = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        currentBaseUrl = formatted
        apiService = null
    }

    fun getCurrentBaseUrl(): String = currentBaseUrl
}

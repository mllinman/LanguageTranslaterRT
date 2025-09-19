package com.mllinman.languagetranslator.api

import android.util.Log
import com.mllinman.languagetranslator.BuildConfig
import com.mllinman.languagetranslator.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * API client for backend communication
 * Handles all subscription-related API calls and authentication
 */
class ApiClient {
    
    companion object {
        private const val TAG = "ApiClient"
        private const val TIMEOUT_SECONDS = 30L
        private const val CONTENT_TYPE = "application/json"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val client: OkHttpClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        
        // Add logging in debug builds
        if (BuildConfig.DEBUG_LOGGING) {
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                Log.d(TAG, message)
            }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }
        
        // Add API key header if needed
        builder.addInterceptor { chain ->
            val originalRequest = chain.request()
            val newRequest = originalRequest.newBuilder()
                .header("Content-Type", CONTENT_TYPE)
                .header("User-Agent", "LanguageTranslator-Android/${BuildConfig.VERSION_NAME}")
                .build()
            chain.proceed(newRequest)
        }
        
        builder.build()
    }
    
    /**
     * Create a checkout session for subscription
     */
    suspend fun createCheckoutSession(request: CreateCheckoutSessionRequest): CreateCheckoutSessionResponse = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.DEMO_MODE) {
                Log.i(TAG, "Demo mode: Simulating checkout session creation")
                return@withContext CreateCheckoutSessionResponse(
                    isSuccessful = true,
                    sessionId = "demo_session_${System.currentTimeMillis()}",
                    checkoutUrl = "https://checkout.stripe.com/demo"
                )
            }
            
            val requestBody = json.encodeToString(request)
                .toRequestBody(CONTENT_TYPE.toMediaTypeOrNull())
            
            val httpRequest = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/checkout/create-session")
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                json.decodeFromString<CreateCheckoutSessionResponse>(responseBody)
            } else {
                Log.e(TAG, "Checkout session creation failed: ${response.code} - $responseBody")
                CreateCheckoutSessionResponse(
                    isSuccessful = false,
                    error = "Failed to create checkout session: HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating checkout session", e)
            CreateCheckoutSessionResponse(
                isSuccessful = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Get subscription status for a user
     */
    suspend fun getSubscriptionStatus(userId: String): SubscriptionStatusResponse = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.DEMO_MODE) {
                Log.i(TAG, "Demo mode: Simulating subscription status check")
                return@withContext SubscriptionStatusResponse(
                    isSuccessful = true,
                    tier = "FREE",
                    status = "ACTIVE"
                )
            }
            
            val url = "${BuildConfig.BACKEND_BASE_URL}/subscription/status?userId=$userId"
            val httpRequest = Request.Builder()
                .url(url)
                .get()
                .build()
            
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                json.decodeFromString<SubscriptionStatusResponse>(responseBody)
            } else {
                Log.e(TAG, "Subscription status check failed: ${response.code} - $responseBody")
                SubscriptionStatusResponse(
                    isSuccessful = false,
                    tier = "FREE",
                    error = "Failed to check subscription status: HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking subscription status", e)
            SubscriptionStatusResponse(
                isSuccessful = false,
                tier = "FREE",
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Confirm subscription after successful checkout
     */
    suspend fun confirmSubscription(userId: String, sessionId: String): ConfirmSubscriptionResponse = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.DEMO_MODE) {
                Log.i(TAG, "Demo mode: Simulating subscription confirmation")
                return@withContext ConfirmSubscriptionResponse(
                    isSuccessful = true,
                    tier = "PRO_MONTHLY",
                    subscriptionId = "demo_sub_${System.currentTimeMillis()}"
                )
            }
            
            val request = ConfirmSubscriptionRequest(userId, sessionId)
            val requestBody = json.encodeToString(request)
                .toRequestBody(CONTENT_TYPE.toMediaTypeOrNull())
            
            val httpRequest = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/subscription/confirm")
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                json.decodeFromString<ConfirmSubscriptionResponse>(responseBody)
            } else {
                Log.e(TAG, "Subscription confirmation failed: ${response.code} - $responseBody")
                ConfirmSubscriptionResponse(
                    isSuccessful = false,
                    error = "Failed to confirm subscription: HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error confirming subscription", e)
            ConfirmSubscriptionResponse(
                isSuccessful = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Cancel subscription
     */
    suspend fun cancelSubscription(userId: String): CancelSubscriptionResponse = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.DEMO_MODE) {
                Log.i(TAG, "Demo mode: Simulating subscription cancellation")
                return@withContext CancelSubscriptionResponse(isSuccessful = true)
            }
            
            val request = CancelSubscriptionRequest(userId)
            val requestBody = json.encodeToString(request)
                .toRequestBody(CONTENT_TYPE.toMediaTypeOrNull())
            
            val httpRequest = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/subscription/cancel")
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                json.decodeFromString<CancelSubscriptionResponse>(responseBody)
            } else {
                Log.e(TAG, "Subscription cancellation failed: ${response.code} - $responseBody")
                CancelSubscriptionResponse(
                    isSuccessful = false,
                    error = "Failed to cancel subscription: HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling subscription", e)
            CancelSubscriptionResponse(
                isSuccessful = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Record usage analytics
     */
    suspend fun recordUsage(userId: String, action: String, metadata: Map<String, String> = emptyMap()): RecordUsageResponse = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.DEMO_MODE) {
                Log.d(TAG, "Demo mode: Recording usage - User: $userId, Action: $action")
                return@withContext RecordUsageResponse(isSuccessful = true)
            }
            
            val request = RecordUsageRequest(userId, action, metadata)
            val requestBody = json.encodeToString(request)
                .toRequestBody(CONTENT_TYPE.toMediaTypeOrNull())
            
            val httpRequest = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/usage/record")
                .post(requestBody)
                .build()
            
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                json.decodeFromString<RecordUsageResponse>(responseBody)
            } else {
                Log.w(TAG, "Usage recording failed: ${response.code} - $responseBody")
                RecordUsageResponse(
                    isSuccessful = false,
                    error = "Failed to record usage: HTTP ${response.code}"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error recording usage", e)
            RecordUsageResponse(
                isSuccessful = false,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Health check endpoint
     */
    suspend fun healthCheck(): HealthCheckResponse? = withContext(Dispatchers.IO) {
        try {
            if (BuildConfig.DEMO_MODE) {
                return@withContext HealthCheckResponse(
                    status = "healthy",
                    timestamp = System.currentTimeMillis(),
                    version = "demo"
                )
            }
            
            val httpRequest = Request.Builder()
                .url("${BuildConfig.BACKEND_BASE_URL}/health")
                .get()
                .build()
            
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (response.isSuccessful) {
                json.decodeFromString<HealthCheckResponse>(responseBody)
            } else {
                Log.w(TAG, "Health check failed: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Health check error", e)
            null
        }
    }
}
package com.mllinman.languagetranslator

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.mllinman.languagetranslator.api.ApiClient
import com.mllinman.languagetranslator.models.*
import com.stripe.android.*
import com.stripe.android.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.UUID

/**
 * Production-ready SubscriptionManager with environment-based configuration
 */
class SubscriptionManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val apiClient = ApiClient()
    
    companion object {
        private const val TAG = "SubscriptionManager"
        private const val PREFS_NAME = "subscription_prefs"
        private const val KEY_SUBSCRIPTION_STATUS = "subscription_status"
        private const val KEY_DAILY_USAGE = "daily_usage"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_SUBSCRIPTION_ID = "subscription_id"
        private const val KEY_USER_ID = "user_id"
        
        // Production configuration - Load from BuildConfig
        val STRIPE_PUBLISHABLE_KEY = BuildConfig.STRIPE_PUBLISHABLE_KEY
        val PRO_MONTHLY_PRICE_ID = BuildConfig.PRO_MONTHLY_PRICE_ID
        val PRO_ANNUAL_PRICE_ID = BuildConfig.PRO_ANNUAL_PRICE_ID
        val BACKEND_BASE_URL = BuildConfig.BACKEND_BASE_URL
        
        // Free tier limits - can be made configurable via remote config
        const val FREE_TIER_DAILY_LIMIT = 15  // Reduced for better conversion
        const val FREE_TIER_HOURLY_LIMIT = 3   // Additional hourly limit
        
        // Production mode - load from BuildConfig
        val DEMO_MODE = BuildConfig.DEMO_MODE
    }
    
    enum class SubscriptionTier {
        FREE, 
        PRO_MONTHLY, 
        PRO_ANNUAL,
        ENTERPRISE
    }
    
    data class SubscriptionPlan(
        val tier: SubscriptionTier,
        val priceId: String,
        val price: Double,
        val duration: String,
        val features: List<String>,
        val savings: String? = null
    )
    
    private val availablePlans = listOf(
        SubscriptionPlan(
            tier = SubscriptionTier.PRO_MONTHLY,
            priceId = PRO_MONTHLY_PRICE_ID,
            price = 9.99,
            duration = "monthly",
            features = listOf(
                "Unlimited translations",
                "Priority processing", 
                "Offline language packs",
                "Advanced voice recognition",
                "24/7 support"
            )
        ),
        SubscriptionPlan(
            tier = SubscriptionTier.PRO_ANNUAL,
            priceId = PRO_ANNUAL_PRICE_ID,
            price = 99.99,
            duration = "annual",
            features = listOf(
                "Unlimited translations",
                "Priority processing", 
                "Offline language packs",
                "Advanced voice recognition",
                "24/7 support",
                "Exclusive language updates"
            ),
            savings = "Save 17% compared to monthly!"
        )
    )
    
    private var stripe: Stripe? = null
    
    init {
        initializeStripe()
        ensureUserIdExists()
    }
    
    private fun initializeStripe() {
        try {
            if (!DEMO_MODE) {
                PaymentConfiguration.init(context, STRIPE_PUBLISHABLE_KEY)
                stripe = Stripe(context, STRIPE_PUBLISHABLE_KEY)
                Log.i(TAG, "Stripe initialized for production")
            } else {
                Log.i(TAG, "Running in demo mode - Stripe integration disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stripe", e)
        }
    }
    
    private fun ensureUserIdExists() {
        if (getUserId().isEmpty()) {
            val newUserId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, newUserId).apply()
            Log.i(TAG, "Generated new user ID: $newUserId")
        }
    }
    
    private fun getUserId(): String {
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }
    
    /**
     * Get current subscription tier
     */
    fun getSubscriptionTier(): SubscriptionTier {
        val status = prefs.getString(KEY_SUBSCRIPTION_STATUS, SubscriptionTier.FREE.name)
        return try {
            SubscriptionTier.valueOf(status ?: SubscriptionTier.FREE.name)
        } catch (e: Exception) {
            SubscriptionTier.FREE
        }
    }
    
    /**
     * Get available subscription plans
     */
    fun getAvailablePlans(): List<SubscriptionPlan> = availablePlans
    
    /**
     * Check if user has pro subscription
     */
    fun isProUser(): Boolean {
        val tier = getSubscriptionTier()
        return tier == SubscriptionTier.PRO_MONTHLY || 
               tier == SubscriptionTier.PRO_ANNUAL || 
               tier == SubscriptionTier.ENTERPRISE
    }
    
    /**
     * Get daily usage count for free tier users
     */
    fun getDailyUsage(): Int {
        resetDailyUsageIfNeeded()
        return prefs.getInt(KEY_DAILY_USAGE, 0)
    }
    
    /**
     * Get hourly usage count for free tier users
     */
    fun getHourlyUsage(): Int {
        resetHourlyUsageIfNeeded()
        return prefs.getInt("hourly_usage", 0)
    }
    
    /**
     * Check if user can make a translation (considering tier limits)
     */
    fun canMakeTranslation(): Boolean {
        return when (getSubscriptionTier()) {
            SubscriptionTier.PRO_MONTHLY, SubscriptionTier.PRO_ANNUAL, SubscriptionTier.ENTERPRISE -> true
            SubscriptionTier.FREE -> {
                val dailyUsage = getDailyUsage()
                val hourlyUsage = getHourlyUsage()
                dailyUsage < FREE_TIER_DAILY_LIMIT && hourlyUsage < FREE_TIER_HOURLY_LIMIT
            }
        }
    }
    
    /**
     * Record a translation usage (for free tier tracking)
     */
    fun recordTranslationUsage() {
        if (getSubscriptionTier() == SubscriptionTier.FREE) {
            resetDailyUsageIfNeeded()
            resetHourlyUsageIfNeeded()
            
            val currentDailyUsage = prefs.getInt(KEY_DAILY_USAGE, 0)
            val currentHourlyUsage = prefs.getInt("hourly_usage", 0)
            
            prefs.edit()
                .putInt(KEY_DAILY_USAGE, currentDailyUsage + 1)
                .putInt("hourly_usage", currentHourlyUsage + 1)
                .apply()
        }
        
        // Record usage analytics
        recordUsageAnalytics("translation_completed")
    }
    
    /**
     * Get remaining translations for free tier
     */
    fun getRemainingTranslations(): Int {
        return if (isProUser()) {
            -1 // Unlimited
        } else {
            val dailyRemaining = maxOf(0, FREE_TIER_DAILY_LIMIT - getDailyUsage())
            val hourlyRemaining = maxOf(0, FREE_TIER_HOURLY_LIMIT - getHourlyUsage())
            minOf(dailyRemaining, hourlyRemaining)
        }
    }
    
    /**
     * Reset daily usage if it's a new day
     */
    private fun resetDailyUsageIfNeeded() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastResetDay = prefs.getInt(KEY_LAST_RESET_DATE, -1)
        
        if (today != lastResetDay) {
            prefs.edit()
                .putInt(KEY_DAILY_USAGE, 0)
                .putInt(KEY_LAST_RESET_DATE, today)
                .apply()
        }
    }
    
    /**
     * Reset hourly usage if it's a new hour
     */
    private fun resetHourlyUsageIfNeeded() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val lastResetHour = prefs.getInt("last_reset_hour", -1)
        
        if (currentHour != lastResetHour) {
            prefs.edit()
                .putInt("hourly_usage", 0)
                .putInt("last_reset_hour", currentHour)
                .apply()
        }
    }
    
    /**
     * Update subscription status
     */
    fun updateSubscriptionStatus(tier: SubscriptionTier, subscriptionId: String? = null) {
        prefs.edit()
            .putString(KEY_SUBSCRIPTION_STATUS, tier.name)
            .putString(KEY_SUBSCRIPTION_ID, subscriptionId)
            .apply()
        
        Log.i(TAG, "Subscription status updated to: $tier")
        recordUsageAnalytics("subscription_updated", mapOf("tier" to tier.name))
    }
    
    /**
     * Verify subscription status with backend
     */
    suspend fun verifySubscriptionStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (DEMO_MODE) {
                // In demo mode, keep existing status
                return@withContext isProUser()
            }
            
            val userId = getUserId()
            val response = apiClient.getSubscriptionStatus(userId)
            
            if (response.isSuccessful) {
                val tier = try {
                    SubscriptionTier.valueOf(response.tier)
                } catch (e: Exception) {
                    SubscriptionTier.FREE
                }
                updateSubscriptionStatus(tier, response.subscriptionId)
                return@withContext tier != SubscriptionTier.FREE
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify subscription", e)
            return@withContext false
        }
    }
    
    /**
     * Create checkout session for Pro subscription
     */
    suspend fun createCheckoutSession(plan: SubscriptionPlan): String? = withContext(Dispatchers.IO) {
        try {
            if (DEMO_MODE) {
                Log.i(TAG, "Demo mode: Simulating checkout session creation for ${plan.tier}")
                return@withContext "demo_session_${System.currentTimeMillis()}"
            }
            
            val userId = getUserId()
            val request = CreateCheckoutSessionRequest(
                userId = userId,
                priceId = plan.priceId,
                successUrl = "languagetranslator://subscription/success",
                cancelUrl = "languagetranslator://subscription/cancel"
            )
            
            val response = apiClient.createCheckoutSession(request)
            
            if (response.isSuccessful) {
                Log.i(TAG, "Checkout session created: ${response.sessionId}")
                return@withContext response.sessionId
            } else {
                Log.e(TAG, "Failed to create checkout session: ${response.error}")
                return@withContext null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error creating checkout session", e)
            return@withContext null
        }
    }
    
    /**
     * Handle successful subscription (production-ready)
     */
    suspend fun handleSuccessfulSubscription(sessionId: String): Boolean {
        return try {
            if (DEMO_MODE) {
                // Demo mode - simulate success
                updateSubscriptionStatus(SubscriptionTier.PRO_MONTHLY, "demo_sub_123")
                return true
            }
            
            val userId = getUserId()
            val response = apiClient.confirmSubscription(userId, sessionId)
            
            if (response.isSuccessful) {
                val tier = try {
                    SubscriptionTier.valueOf(response.tier ?: "FREE")
                } catch (e: Exception) {
                    SubscriptionTier.PRO_MONTHLY
                }
                updateSubscriptionStatus(tier, response.subscriptionId)
                Log.i(TAG, "Successfully upgraded to ${response.tier}")
                true
            } else {
                Log.e(TAG, "Failed to confirm subscription: ${response.error}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling successful subscription", e)
            false
        }
    }
    
    /**
     * Cancel subscription
     */
    suspend fun cancelSubscription(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (DEMO_MODE) {
                updateSubscriptionStatus(SubscriptionTier.FREE)
                return@withContext true
            }
            
            val userId = getUserId()
            val response = apiClient.cancelSubscription(userId)
            
            if (response.isSuccessful) {
                // Keep Pro features until end of billing period
                Log.i(TAG, "Subscription cancelled - will remain active until period end")
                return@withContext true
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling subscription", e)
            return@withContext false
        }
    }
    
    /**
     * Record usage analytics
     */
    private fun recordUsageAnalytics(action: String, metadata: Map<String, String> = emptyMap()) {
        if (BuildConfig.DEBUG_LOGGING) {
            Log.d(TAG, "Recording usage: $action with metadata: $metadata")
        }
        
        // Use coroutine to avoid blocking main thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userId = getUserId()
                apiClient.recordUsage(userId, action, metadata)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record usage analytics", e)
            }
        }
    }
    
    /**
     * Get subscription summary for UI display
     */
    fun getSubscriptionSummary(): SubscriptionSummary {
        val tier = getSubscriptionTier()
        val remaining = getRemainingTranslations()
        val dailyUsage = getDailyUsage()
        val hourlyUsage = getHourlyUsage()
        
        return SubscriptionSummary(
            tier = tier,
            remainingTranslations = remaining,
            dailyUsage = dailyUsage,
            dailyLimit = if (tier == SubscriptionTier.FREE) FREE_TIER_DAILY_LIMIT else -1,
            hourlyUsage = hourlyUsage,
            hourlyLimit = if (tier == SubscriptionTier.FREE) FREE_TIER_HOURLY_LIMIT else -1
        )
    }
    
    data class SubscriptionSummary(
        val tier: SubscriptionTier,
        val remainingTranslations: Int, // -1 for unlimited
        val dailyUsage: Int,
        val dailyLimit: Int, // -1 for unlimited
        val hourlyUsage: Int,
        val hourlyLimit: Int // -1 for unlimited
    )
    
    // Demo methods for testing - only work in demo mode
    fun simulateNearDailyLimit() {
        if (DEMO_MODE && getSubscriptionTier() == SubscriptionTier.FREE) {
            prefs.edit().putInt(KEY_DAILY_USAGE, FREE_TIER_DAILY_LIMIT - 3).apply()
            Log.i(TAG, "Demo: Set usage to near daily limit")
        }
    }
    
    fun simulateReachedDailyLimit() {
        if (DEMO_MODE && getSubscriptionTier() == SubscriptionTier.FREE) {
            prefs.edit().putInt(KEY_DAILY_USAGE, FREE_TIER_DAILY_LIMIT).apply()
            Log.i(TAG, "Demo: Set usage to daily limit")
        }
    }
    
    fun resetDailyUsage() {
        if (DEMO_MODE) {
            prefs.edit()
                .putInt(KEY_DAILY_USAGE, 0)
                .putInt("hourly_usage", 0)
                .apply()
            Log.i(TAG, "Demo: Reset usage counters")
        }
    }
}
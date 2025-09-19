package com.mllinman.languagetranslator

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.stripe.android.*
import com.stripe.android.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/**
 * Manages subscription tiers and Stripe integration
 */
class SubscriptionManager(private val context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val TAG = "SubscriptionManager"
        private const val PREFS_NAME = "subscription_prefs"
        private const val KEY_SUBSCRIPTION_STATUS = "subscription_status"
        private const val KEY_DAILY_USAGE = "daily_usage"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_SUBSCRIPTION_ID = "subscription_id"
        
        // Stripe configuration - In production, these would be environment-specific
        const val STRIPE_PUBLISHABLE_KEY = "pk_test_your_publishable_key_here" // Replace with your actual key
        const val PRO_PRICE_ID = "price_pro_monthly" // Replace with your actual price ID
        
        // Free tier limits
        const val FREE_TIER_DAILY_LIMIT = 100
    }
    
    enum class SubscriptionTier {
        FREE, PRO
    }
    
    private var stripe: Stripe? = null
    
    init {
        initializeStripe()
    }
    
    private fun initializeStripe() {
        try {
            PaymentConfiguration.init(context, STRIPE_PUBLISHABLE_KEY)
            stripe = Stripe(context, STRIPE_PUBLISHABLE_KEY)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stripe", e)
        }
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
     * Check if user has pro subscription
     */
    fun isProUser(): Boolean {
        return getSubscriptionTier() == SubscriptionTier.PRO
    }
    
    /**
     * Get daily usage count for free tier users
     */
    fun getDailyUsage(): Int {
        resetDailyUsageIfNeeded()
        return prefs.getInt(KEY_DAILY_USAGE, 0)
    }
    
    /**
     * Check if user can make a translation (considering tier limits)
     */
    fun canMakeTranslation(): Boolean {
        return when (getSubscriptionTier()) {
            SubscriptionTier.PRO -> true
            SubscriptionTier.FREE -> {
                val usage = getDailyUsage()
                usage < FREE_TIER_DAILY_LIMIT
            }
        }
    }
    
    /**
     * Record a translation usage (for free tier tracking)
     */
    fun recordTranslationUsage() {
        if (getSubscriptionTier() == SubscriptionTier.FREE) {
            resetDailyUsageIfNeeded()
            val currentUsage = prefs.getInt(KEY_DAILY_USAGE, 0)
            prefs.edit().putInt(KEY_DAILY_USAGE, currentUsage + 1).apply()
        }
    }
    
    /**
     * Get remaining translations for free tier
     */
    fun getRemainingTranslations(): Int {
        return if (isProUser()) {
            -1 // Unlimited
        } else {
            maxOf(0, FREE_TIER_DAILY_LIMIT - getDailyUsage())
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
     * Update subscription status (would be called after successful Stripe webhook)
     */
    fun updateSubscriptionStatus(tier: SubscriptionTier, subscriptionId: String? = null) {
        prefs.edit()
            .putString(KEY_SUBSCRIPTION_STATUS, tier.name)
            .putString(KEY_SUBSCRIPTION_ID, subscriptionId)
            .apply()
        
        Log.i(TAG, "Subscription status updated to: $tier")
    }
    
    /**
     * Get subscription summary for UI display
     */
    fun getSubscriptionSummary(): SubscriptionSummary {
        val tier = getSubscriptionTier()
        val remaining = getRemainingTranslations()
        val dailyUsage = getDailyUsage()
        
        return SubscriptionSummary(
            tier = tier,
            remainingTranslations = remaining,
            dailyUsage = dailyUsage,
            dailyLimit = if (tier == SubscriptionTier.FREE) FREE_TIER_DAILY_LIMIT else -1
        )
    }
    
    /**
     * Create checkout session for Pro subscription
     * In a real app, this would call your backend server
     */
    suspend fun createCheckoutSession(): String? = withContext(Dispatchers.IO) {
        try {
            // This is a placeholder - in production, you would call your backend
            // which would create a Stripe checkout session and return the session ID
            Log.i(TAG, "Creating checkout session for Pro subscription")
            
            // For demo purposes, return a mock session ID
            // In real implementation:
            // 1. Call your backend API
            // 2. Backend creates Stripe checkout session
            // 3. Return session ID to redirect to Stripe Checkout
            return@withContext "mock_checkout_session_id"
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create checkout session", e)
            return@withContext null
        }
    }
    
    /**
     * Handle successful subscription (mock implementation)
     */
    fun handleSuccessfulSubscription(subscriptionId: String) {
        updateSubscriptionStatus(SubscriptionTier.PRO, subscriptionId)
        Log.i(TAG, "Successfully upgraded to Pro subscription")
    }
    
    data class SubscriptionSummary(
        val tier: SubscriptionTier,
        val remainingTranslations: Int, // -1 for unlimited
        val dailyUsage: Int,
        val dailyLimit: Int // -1 for unlimited
    )
}
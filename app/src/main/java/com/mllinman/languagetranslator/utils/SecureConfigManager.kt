package com.mllinman.languagetranslator.utils

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.mllinman.languagetranslator.BuildConfig

/**
 * Secure configuration manager for sensitive data
 * Uses Android Security library for encrypted storage
 */
object SecureConfigManager {
    
    private const val TAG = "SecureConfigManager"
    private const val ENCRYPTED_PREFS_NAME = "secure_config"
    
    private var encryptedPrefs: android.content.SharedPreferences? = null
    
    @Synchronized
    fun initialize(context: Context) {
        if (encryptedPrefs != null) return
        
        try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            
            encryptedPrefs = EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            
            Log.d(TAG, "Secure configuration initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize secure configuration", e)
            // Fallback to regular SharedPreferences
            encryptedPrefs = context.getSharedPreferences(ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    /**
     * Store sensitive configuration value
     */
    fun storeSecureValue(key: String, value: String) {
        try {
            encryptedPrefs?.edit()?.putString(key, value)?.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to store secure value for key: $key", e)
        }
    }
    
    /**
     * Retrieve sensitive configuration value
     */
    fun getSecureValue(key: String, defaultValue: String = ""): String {
        return try {
            encryptedPrefs?.getString(key, defaultValue) ?: defaultValue
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve secure value for key: $key", e)
            defaultValue
        }
    }
    
    /**
     * Check if configuration is in demo mode
     */
    fun isDemoMode(): Boolean = BuildConfig.DEMO_MODE
    
    /**
     * Get Stripe publishable key (production or test based on build config)
     */
    fun getStripePublishableKey(): String {
        return if (isDemoMode()) {
            BuildConfig.STRIPE_PUBLISHABLE_KEY
        } else {
            // In production, prefer environment variable or secure storage
            getSecureValue("stripe_publishable_key", BuildConfig.STRIPE_PUBLISHABLE_KEY)
        }
    }
    
    /**
     * Validate configuration for production readiness
     */
    fun validateProductionConfig(): List<String> {
        val issues = mutableListOf<String>()
        
        if (isDemoMode()) {
            issues.add("App is still in DEMO_MODE - must be set to false for production")
        }
        
        val stripeKey = getStripePublishableKey()
        if (stripeKey.startsWith("pk_test_") && !isDemoMode()) {
            issues.add("Using test Stripe key in production build")
        }
        
        if (stripeKey == "pk_test_your_publishable_key_here" || 
            stripeKey == "REPLACE_WITH_PRODUCTION_KEY" ||
            stripeKey.contains("replace_with_production_key")) {
            issues.add("Stripe publishable key not configured")
        }
        
        if (BuildConfig.PRO_MONTHLY_PRICE_ID == "price_pro_monthly" ||
            BuildConfig.PRO_MONTHLY_PRICE_ID == "REPLACE_WITH_PRODUCTION_ID") {
            issues.add("Stripe price IDs not configured")
        }
        
        if (BuildConfig.BACKEND_BASE_URL == "https://api.yourdomain.com/v1" ||
            BuildConfig.BACKEND_BASE_URL.contains("REPLACE-WITH-PRODUCTION-API")) {
            issues.add("Backend URL not configured")
        }
        
        return issues
    }
    
    /**
     * Log configuration status (safe for production - no sensitive data)
     */
    fun logConfigurationStatus() {
        val issues = validateProductionConfig()
        
        Log.i(TAG, "Configuration Status:")
        Log.i(TAG, "- Demo Mode: ${isDemoMode()}")
        Log.i(TAG, "- Debug Logging: ${BuildConfig.DEBUG_LOGGING}")
        Log.i(TAG, "- Backend URL configured: ${!BuildConfig.BACKEND_BASE_URL.contains("yourdomain")}")
        Log.i(TAG, "- Stripe configured: ${!getStripePublishableKey().contains("your_publishable_key_here")}")
        
        if (issues.isNotEmpty()) {
            Log.w(TAG, "Configuration Issues Found:")
            issues.forEach { issue ->
                Log.w(TAG, "- $issue")
            }
        } else {
            Log.i(TAG, "Configuration appears ready for production")
        }
    }
}
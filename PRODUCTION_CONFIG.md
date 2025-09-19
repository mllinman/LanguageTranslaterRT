# Production Configuration Guide

## 🔧 Step-by-Step Production Setup

### 1. Environment Configuration

Create a `production.properties` file for secure configuration management:

```properties
# production.properties (DO NOT commit this file)
stripe.publishable.key=pk_live_YOUR_ACTUAL_LIVE_KEY
stripe.price.id.pro.monthly=price_YOUR_ACTUAL_PRICE_ID
stripe.price.id.pro.annual=price_YOUR_ANNUAL_PRICE_ID
demo.mode=false
backend.base.url=https://your-backend-api.com/api/v1
analytics.enabled=true
```

### 2. Modified SubscriptionManager.kt for Production

```kotlin
/**
 * Production-ready SubscriptionManager with environment-based configuration
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
        private const val KEY_USER_ID = "user_id"
        
        // Production configuration - Load from BuildConfig or environment
        val STRIPE_PUBLISHABLE_KEY = BuildConfig.STRIPE_PUBLISHABLE_KEY
        val PRO_MONTHLY_PRICE_ID = BuildConfig.PRO_MONTHLY_PRICE_ID  
        val PRO_ANNUAL_PRICE_ID = BuildConfig.PRO_ANNUAL_PRICE_ID
        val BACKEND_BASE_URL = BuildConfig.BACKEND_BASE_URL
        
        // Free tier limits
        const val FREE_TIER_DAILY_LIMIT = 100
        
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
        val features: List<String>
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
                "Voice-to-voice translation",
                "No advertisements",
                "Translation history",
                "Export capabilities"
            )
        ),
        SubscriptionPlan(
            tier = SubscriptionTier.PRO_ANNUAL,
            priceId = PRO_ANNUAL_PRICE_ID,
            price = 99.99,
            duration = "annual",
            features = listOf(
                "All Pro Monthly features",
                "17% savings ($20 off yearly)",
                "Priority customer support",
                "Early access to new features"
            )
        )
    )
    
    private var stripe: Stripe? = null
    private val apiClient = ApiClient(BACKEND_BASE_URL)
    
    init {
        initializeStripe()
        initializeUser()
    }
    
    /**
     * Production-ready Stripe initialization
     */
    private fun initializeStripe() {
        try {
            if (!DEMO_MODE && STRIPE_PUBLISHABLE_KEY.isNotEmpty()) {
                PaymentConfiguration.init(context, STRIPE_PUBLISHABLE_KEY)
                stripe = Stripe(context, STRIPE_PUBLISHABLE_KEY)
                Log.i(TAG, "Stripe initialized for production")
            } else {
                Log.i(TAG, "Running in demo mode - Stripe integration disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Stripe", e)
            // Fallback to demo mode if Stripe fails
            DEMO_MODE = true
        }
    }
    
    /**
     * Initialize user session with backend
     */
    private fun initializeUser() {
        val userId = getUserId()
        if (userId.isEmpty()) {
            val newUserId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, newUserId).apply()
        }
    }
    
    /**
     * Create Stripe checkout session via backend
     */
    suspend fun createCheckoutSession(plan: SubscriptionPlan): CheckoutResult = withContext(Dispatchers.IO) {
        try {
            if (DEMO_MODE) {
                // Demo mode - simulate success
                return@withContext CheckoutResult.Success("demo_session_id")
            }
            
            val userId = getUserId()
            val response = apiClient.createCheckoutSession(
                userId = userId,
                priceId = plan.priceId,
                successUrl = "translator://subscription/success",
                cancelUrl = "translator://subscription/cancel"
            )
            
            return@withContext if (response.isSuccessful) {
                CheckoutResult.Success(response.sessionId)
            } else {
                CheckoutResult.Error(response.error ?: "Failed to create checkout session")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create checkout session", e)
            return@withContext CheckoutResult.Error("Network error: ${e.message}")
        }
    }
    
    /**
     * Verify subscription status with backend
     */
    suspend fun verifySubscriptionStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (DEMO_MODE) return@withContext true
            
            val userId = getUserId()
            val response = apiClient.getSubscriptionStatus(userId)
            
            if (response.isSuccessful) {
                updateSubscriptionStatus(response.tier, response.subscriptionId)
                return@withContext response.tier != SubscriptionTier.FREE
            }
            
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify subscription", e)
            return@withContext false
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
                updateSubscriptionStatus(response.tier, response.subscriptionId)
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
            Log.e(TAG, "Failed to cancel subscription", e)
            return@withContext false
        }
    }
    
    /**
     * Get available subscription plans
     */
    fun getAvailablePlans(): List<SubscriptionPlan> = availablePlans
    
    /**
     * Get user ID for backend communication
     */
    private fun getUserId(): String = prefs.getString(KEY_USER_ID, "") ?: ""
    
    /**
     * Track usage with backend for analytics
     */
    suspend fun recordTranslationUsage() {
        if (getSubscriptionTier() == SubscriptionTier.FREE) {
            // Update local usage
            resetDailyUsageIfNeeded()
            val currentUsage = prefs.getInt(KEY_DAILY_USAGE, 0)
            prefs.edit().putInt(KEY_DAILY_USAGE, currentUsage + 1).apply()
        }
        
        // Always send to backend for analytics (async)
        if (!DEMO_MODE) {
            try {
                val userId = getUserId()
                apiClient.recordUsage(userId, "translation")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record usage analytics", e)
                // Don't fail the translation if analytics fail
            }
        }
    }
    
    sealed class CheckoutResult {
        data class Success(val sessionId: String) : CheckoutResult()
        data class Error(val message: String) : CheckoutResult()
    }
    
    // ... rest of existing methods remain the same
}
```

### 3. Backend API Client

```kotlin
/**
 * API client for backend communication
 */
class ApiClient(private val baseUrl: String) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    suspend fun createCheckoutSession(
        userId: String,
        priceId: String,
        successUrl: String,
        cancelUrl: String
    ): CheckoutSessionResponse = withContext(Dispatchers.IO) {
        val requestBody = CheckoutSessionRequest(
            userId = userId,
            priceId = priceId,
            successUrl = successUrl,
            cancelUrl = cancelUrl
        )
        
        val request = Request.Builder()
            .url("$baseUrl/create-checkout-session")
            .post(requestBody.toJson().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (response.isSuccessful) {
            val body = response.body?.string() ?: ""
            json.decodeFromString<CheckoutSessionResponse>(body)
        } else {
            CheckoutSessionResponse(
                isSuccessful = false,
                error = "HTTP ${response.code}: ${response.message}"
            )
        }
    }
    
    suspend fun getSubscriptionStatus(userId: String): SubscriptionStatusResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/subscription/status/$userId")
            .get()
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (response.isSuccessful) {
            val body = response.body?.string() ?: ""
            json.decodeFromString<SubscriptionStatusResponse>(body)
        } else {
            SubscriptionStatusResponse(
                isSuccessful = false,
                tier = SubscriptionManager.SubscriptionTier.FREE
            )
        }
    }
    
    // Additional API methods...
}

@Serializable
data class CheckoutSessionRequest(
    val userId: String,
    val priceId: String,
    val successUrl: String,
    val cancelUrl: String
)

@Serializable
data class CheckoutSessionResponse(
    val isSuccessful: Boolean,
    val sessionId: String? = null,
    val error: String? = null
)

@Serializable
data class SubscriptionStatusResponse(
    val isSuccessful: Boolean,
    val tier: SubscriptionManager.SubscriptionTier,
    val subscriptionId: String? = null,
    val error: String? = null
)
```

### 4. Build Configuration (app/build.gradle)

```gradle
android {
    // ... existing configuration
    
    buildTypes {
        debug {
            buildConfigField "String", "STRIPE_PUBLISHABLE_KEY", "\"pk_test_demo_key\""
            buildConfigField "String", "PRO_MONTHLY_PRICE_ID", "\"price_demo_monthly\""
            buildConfigField "String", "PRO_ANNUAL_PRICE_ID", "\"price_demo_annual\""
            buildConfigField "String", "BACKEND_BASE_URL", "\"http://localhost:3000/api/v1\""
            buildConfigField "boolean", "DEMO_MODE", "true"
        }
        
        release {
            // Load from environment variables or properties file
            buildConfigField "String", "STRIPE_PUBLISHABLE_KEY", "\"${System.getenv('STRIPE_PUBLISHABLE_KEY') ?: 'pk_live_default'}\""
            buildConfigField "String", "PRO_MONTHLY_PRICE_ID", "\"${System.getenv('PRO_MONTHLY_PRICE_ID') ?: 'price_default'}\""
            buildConfigField "String", "PRO_ANNUAL_PRICE_ID", "\"${System.getenv('PRO_ANNUAL_PRICE_ID') ?: 'price_default'}\""
            buildConfigField "String", "BACKEND_BASE_URL", "\"${System.getenv('BACKEND_BASE_URL') ?: 'https://api.yourdomain.com/v1'}\""
            buildConfigField "boolean", "DEMO_MODE", "false"
            
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    // ... existing dependencies
    
    // Add for HTTP client
    implementation 'com.squareup.okhttp3:okhttp:4.11.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1'
    
    // Add for analytics
    implementation 'com.google.firebase:firebase-analytics:21.3.0'
    implementation 'com.google.firebase:firebase-config:21.4.1'
}
```

### 5. Environment Variables Setup

```bash
# For production deployment
export STRIPE_PUBLISHABLE_KEY="pk_live_your_actual_key"
export PRO_MONTHLY_PRICE_ID="price_your_monthly_id"  
export PRO_ANNUAL_PRICE_ID="price_your_annual_id"
export BACKEND_BASE_URL="https://your-api.com/v1"

# Build production APK
./gradlew assembleRelease
```

### 6. Security Considerations

#### Secure Key Storage
```kotlin
// Use Android Keystore for sensitive data
class SecurePreferences(context: Context) {
    private val keyAlias = "subscription_keys"
    
    fun storeSecurely(key: String, value: String) {
        // Implementation using Android Keystore
        // This ensures keys are encrypted and secure
    }
    
    fun retrieveSecurely(key: String): String? {
        // Secure retrieval implementation
    }
}
```

#### ProGuard Rules (proguard-rules.pro)
```
# Keep Stripe classes
-keep class com.stripe.** { *; }

# Keep subscription model classes
-keep class com.mllinman.languagetranslator.SubscriptionManager** { *; }
-keep class com.mllinman.languagetranslator.ApiClient** { *; }

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
```

## 🚀 Deployment Checklist

### Pre-Production Testing
- [ ] Test with real Stripe test keys
- [ ] Verify webhook handling
- [ ] Test subscription cancellation
- [ ] Validate analytics tracking
- [ ] Test offline mode gracefully handles API failures
- [ ] Verify all error cases are handled

### Production Deployment
- [ ] Set up production backend infrastructure
- [ ] Configure Stripe webhooks
- [ ] Set up monitoring and alerting
- [ ] Configure database backups
- [ ] Set up SSL certificates
- [ ] Configure CDN for static assets
- [ ] Set up logging and analytics

### Post-Launch Monitoring
- [ ] Monitor subscription conversion rates
- [ ] Track app crashes and errors
- [ ] Monitor API response times
- [ ] Watch for subscription churn
- [ ] Monitor daily active users
- [ ] Track revenue metrics

This production configuration provides a robust foundation for monetizing your Language Translator app with proper security, error handling, and scalability considerations.
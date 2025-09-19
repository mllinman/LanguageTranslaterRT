package com.mllinman.languagetranslator.api

import com.mllinman.languagetranslator.models.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ApiClient
 * Note: These tests validate the demo mode behavior
 * For production testing, integration tests with actual backend are recommended
 */
class ApiClientTest {

    private lateinit var apiClient: ApiClient

    @Before
    fun setup() {
        apiClient = ApiClient()
    }

    @Test
    fun `test health check returns demo response`() = runTest {
        val response = apiClient.healthCheck()
        
        assertNotNull(response)
        assertEquals("healthy", response?.status)
        assertEquals("demo", response?.version)
        assertTrue((response?.timestamp ?: 0) > 0)
    }

    @Test
    fun `test create checkout session in demo mode`() = runTest {
        val request = CreateCheckoutSessionRequest(
            userId = "test_user_123",
            priceId = "price_test",
            successUrl = "app://success",
            cancelUrl = "app://cancel"
        )
        
        val response = apiClient.createCheckoutSession(request)
        
        assertTrue(response.isSuccessful)
        assertNotNull(response.sessionId)
        assertTrue(response.sessionId?.startsWith("demo_session_") == true)
        assertEquals("https://checkout.stripe.com/demo", response.checkoutUrl)
        assertNull(response.error)
    }

    @Test
    fun `test get subscription status in demo mode`() = runTest {
        val response = apiClient.getSubscriptionStatus("test_user_123")
        
        assertTrue(response.isSuccessful)
        assertEquals("FREE", response.tier)
        assertEquals("ACTIVE", response.status)
        assertNull(response.error)
    }

    @Test
    fun `test confirm subscription in demo mode`() = runTest {
        val response = apiClient.confirmSubscription("test_user_123", "demo_session_123")
        
        assertTrue(response.isSuccessful)
        assertEquals("PRO_MONTHLY", response.tier)
        assertNotNull(response.subscriptionId)
        assertTrue(response.subscriptionId?.startsWith("demo_sub_") == true)
        assertNull(response.error)
    }

    @Test
    fun `test cancel subscription in demo mode`() = runTest {
        val response = apiClient.cancelSubscription("test_user_123")
        
        assertTrue(response.isSuccessful)
        assertNull(response.error)
    }

    @Test
    fun `test record usage in demo mode`() = runTest {
        val metadata = mapOf(
            "action_type" to "translation",
            "language_pair" to "es_to_en"
        )
        
        val response = apiClient.recordUsage("test_user_123", "translation_completed", metadata)
        
        assertTrue(response.isSuccessful)
        assertNull(response.error)
    }
}

/**
 * Integration tests for ApiClient
 * These would test against a real backend in a testing environment
 */
class ApiClientIntegrationTest {
    
    // Note: These tests would require a test backend environment
    // and would be run separately from unit tests
    
    // @Test
    // fun `test real backend health check`() = runTest {
    //     // Test against actual backend
    // }
    
    // @Test 
    // fun `test real stripe integration`() = runTest {
    //     // Test actual Stripe integration
    // }
}
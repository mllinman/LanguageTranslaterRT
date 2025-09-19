package com.mllinman.languagetranslator

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * Unit tests for SubscriptionManager
 */
class SubscriptionManagerTest {

    private lateinit var context: Context
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var subscriptionManager: SubscriptionManager

    @Before
    fun setup() {
        context = mockk()
        sharedPreferences = mockk()
        editor = mockk(relaxed = true)
        
        every { context.getSharedPreferences(any(), any()) } returns sharedPreferences
        every { sharedPreferences.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putInt(any(), any()) } returns editor
        every { editor.apply() } returns Unit
        
        // Mock default values
        every { sharedPreferences.getString("subscription_status", any()) } returns "FREE"
        every { sharedPreferences.getString("user_id", any()) } returns ""
        every { sharedPreferences.getInt("daily_usage", 0) } returns 0
        every { sharedPreferences.getInt("last_reset_date", -1) } returns Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        every { sharedPreferences.getInt("hourly_usage", 0) } returns 0
        every { sharedPreferences.getInt("last_reset_hour", -1) } returns Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        
        subscriptionManager = SubscriptionManager(context)
    }

    @Test
    fun `test free tier user initially`() {
        assertEquals(SubscriptionManager.SubscriptionTier.FREE, subscriptionManager.getSubscriptionTier())
        assertFalse(subscriptionManager.isProUser())
    }

    @Test
    fun `test free tier daily limit`() {
        every { sharedPreferences.getInt("daily_usage", 0) } returns 0
        assertTrue(subscriptionManager.canMakeTranslation())
        assertEquals(15, subscriptionManager.getRemainingTranslations())
    }

    @Test
    fun `test free tier near daily limit`() {
        every { sharedPreferences.getInt("daily_usage", 0) } returns 12
        every { sharedPreferences.getInt("hourly_usage", 0) } returns 2
        assertTrue(subscriptionManager.canMakeTranslation())
        assertEquals(3, subscriptionManager.getRemainingTranslations())
    }

    @Test
    fun `test free tier daily limit reached`() {
        every { sharedPreferences.getInt("daily_usage", 0) } returns 15
        every { sharedPreferences.getInt("hourly_usage", 0) } returns 0
        assertFalse(subscriptionManager.canMakeTranslation())
        assertEquals(0, subscriptionManager.getRemainingTranslations())
    }

    @Test
    fun `test free tier hourly limit reached`() {
        every { sharedPreferences.getInt("daily_usage", 0) } returns 5
        every { sharedPreferences.getInt("hourly_usage", 0) } returns 3
        assertFalse(subscriptionManager.canMakeTranslation())
        assertEquals(0, subscriptionManager.getRemainingTranslations())
    }

    @Test
    fun `test pro user unlimited translations`() {
        every { sharedPreferences.getString("subscription_status", any()) } returns "PRO_MONTHLY"
        
        val proSubscriptionManager = SubscriptionManager(context)
        
        assertTrue(proSubscriptionManager.isProUser())
        assertTrue(proSubscriptionManager.canMakeTranslation())
        assertEquals(-1, proSubscriptionManager.getRemainingTranslations())
    }

    @Test
    fun `test record translation usage`() {
        subscriptionManager.recordTranslationUsage()
        
        verify { editor.putInt("daily_usage", 1) }
        verify { editor.putInt("hourly_usage", 1) }
        verify { editor.apply() }
    }

    @Test
    fun `test subscription upgrade`() {
        subscriptionManager.updateSubscriptionStatus(
            SubscriptionManager.SubscriptionTier.PRO_MONTHLY,
            "sub_123"
        )
        
        verify { editor.putString("subscription_status", "PRO_MONTHLY") }
        verify { editor.putString("subscription_id", "sub_123") }
        verify { editor.apply() }
    }

    @Test
    fun `test available plans`() {
        val plans = subscriptionManager.getAvailablePlans()
        
        assertEquals(2, plans.size)
        assertTrue(plans.any { it.tier == SubscriptionManager.SubscriptionTier.PRO_MONTHLY })
        assertTrue(plans.any { it.tier == SubscriptionManager.SubscriptionTier.PRO_ANNUAL })
        
        val monthlyPlan = plans.first { it.tier == SubscriptionManager.SubscriptionTier.PRO_MONTHLY }
        assertEquals(9.99, monthlyPlan.price)
        assertEquals("monthly", monthlyPlan.duration)
        
        val annualPlan = plans.first { it.tier == SubscriptionManager.SubscriptionTier.PRO_ANNUAL }
        assertEquals(99.99, annualPlan.price)
        assertEquals("annual", annualPlan.duration)
        assertTrue(annualPlan.savings?.contains("17%") == true)
    }

    @Test
    fun `test subscription summary for free user`() {
        every { sharedPreferences.getInt("daily_usage", 0) } returns 5
        every { sharedPreferences.getInt("hourly_usage", 0) } returns 2
        
        val summary = subscriptionManager.getSubscriptionSummary()
        
        assertEquals(SubscriptionManager.SubscriptionTier.FREE, summary.tier)
        assertEquals(10, summary.remainingTranslations)
        assertEquals(5, summary.dailyUsage)
        assertEquals(15, summary.dailyLimit)
        assertEquals(2, summary.hourlyUsage)
        assertEquals(3, summary.hourlyLimit)
    }

    @Test
    fun `test subscription summary for pro user`() {
        every { sharedPreferences.getString("subscription_status", any()) } returns "PRO_ANNUAL"
        
        val proSubscriptionManager = SubscriptionManager(context)
        val summary = proSubscriptionManager.getSubscriptionSummary()
        
        assertEquals(SubscriptionManager.SubscriptionTier.PRO_ANNUAL, summary.tier)
        assertEquals(-1, summary.remainingTranslations)
        assertEquals(-1, summary.dailyLimit)
        assertEquals(-1, summary.hourlyLimit)
    }
}
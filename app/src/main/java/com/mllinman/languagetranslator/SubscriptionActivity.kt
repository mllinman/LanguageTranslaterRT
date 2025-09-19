package com.mllinman.languagetranslator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mllinman.languagetranslator.databinding.ActivitySubscriptionBinding
import kotlinx.coroutines.launch

class SubscriptionActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivitySubscriptionBinding
    private lateinit var subscriptionManager: SubscriptionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        subscriptionManager = SubscriptionManager(this)
        
        setupUI()
        setupClickListeners()
        updateSubscriptionInfo()
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Subscription"
    }
    
    private fun setupClickListeners() {
        binding.upgradeButton.setOnClickListener {
            lifecycleScope.launch {
                upgradeToProSubscription()
            }
        }
        
        binding.restorePurchasesButton.setOnClickListener {
            // In a real app, this would restore purchases from Stripe
            restorePurchases()
        }
    }
    
    private fun updateSubscriptionInfo() {
        val summary = subscriptionManager.getSubscriptionSummary()
        
        when (summary.tier) {
            SubscriptionManager.SubscriptionTier.FREE -> {
                binding.currentTierText.text = "Free Tier"
                binding.usageText.text = "Daily translations: ${summary.dailyUsage}/${summary.dailyLimit}"
                binding.remainingText.text = "Remaining today: ${summary.remainingTranslations}"
                
                binding.upgradeButton.isEnabled = true
                binding.upgradeButton.text = "Upgrade to Pro - $9.99/month"
                
                binding.proFeaturesCard.visibility = android.view.View.VISIBLE
            }
            
            SubscriptionManager.SubscriptionTier.PRO -> {
                binding.currentTierText.text = "Pro Tier"
                binding.usageText.text = "Unlimited translations"
                binding.remainingText.text = "Thank you for being a Pro subscriber!"
                
                binding.upgradeButton.isEnabled = false
                binding.upgradeButton.text = "Already Pro Member"
                
                binding.proFeaturesCard.visibility = android.view.View.GONE
            }
        }
    }
    
    private suspend fun upgradeToProSubscription() {
        try {
            binding.upgradeButton.isEnabled = false
            binding.upgradeButton.text = "Processing..."
            
            val sessionId = subscriptionManager.createCheckoutSession()
            
            if (sessionId != null) {
                // In a real app, you would redirect to Stripe Checkout
                // For this demo, we'll simulate a successful subscription
                simulateSuccessfulSubscription()
            } else {
                showError("Failed to create checkout session")
            }
        } catch (e: Exception) {
            showError("Error upgrading subscription: ${e.message}")
        } finally {
            binding.upgradeButton.isEnabled = true
            binding.upgradeButton.text = "Upgrade to Pro - $9.99/month"
        }
    }
    
    private fun simulateSuccessfulSubscription() {
        // This simulates a successful Stripe subscription
        // In production, this would be handled by Stripe webhooks
        subscriptionManager.handleSuccessfulSubscription("sub_mock_123456")
        
        Toast.makeText(this, "Successfully upgraded to Pro!", Toast.LENGTH_LONG).show()
        updateSubscriptionInfo()
        
        // Return result to MainActivity
        setResult(RESULT_OK)
    }
    
    private fun restorePurchases() {
        // In a real app, this would check with Stripe for existing subscriptions
        // For demo purposes, we'll just check current status
        Toast.makeText(this, "Checking for existing subscriptions...", Toast.LENGTH_SHORT).show()
        
        // Simulate checking and potentially restoring a subscription
        // In production, you would call Stripe's API to verify active subscriptions
        updateSubscriptionInfo()
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onResume() {
        super.onResume()
        updateSubscriptionInfo()
    }
}
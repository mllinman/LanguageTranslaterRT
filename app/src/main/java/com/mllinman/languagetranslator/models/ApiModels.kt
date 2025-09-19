package com.mllinman.languagetranslator.models

import kotlinx.serialization.Serializable

/**
 * Data models for API communication
 */

@Serializable
data class CreateCheckoutSessionRequest(
    val userId: String,
    val priceId: String,
    val successUrl: String,
    val cancelUrl: String
)

@Serializable
data class CreateCheckoutSessionResponse(
    val isSuccessful: Boolean,
    val sessionId: String? = null,
    val checkoutUrl: String? = null,
    val error: String? = null
)

@Serializable
data class SubscriptionStatusRequest(
    val userId: String
)

@Serializable
data class SubscriptionStatusResponse(
    val isSuccessful: Boolean,
    val tier: String,
    val subscriptionId: String? = null,
    val status: String? = null,
    val currentPeriodEnd: Long? = null,
    val error: String? = null
)

@Serializable
data class ConfirmSubscriptionRequest(
    val userId: String,
    val sessionId: String
)

@Serializable
data class ConfirmSubscriptionResponse(
    val isSuccessful: Boolean,
    val tier: String? = null,
    val subscriptionId: String? = null,
    val error: String? = null
)

@Serializable
data class CancelSubscriptionRequest(
    val userId: String
)

@Serializable
data class CancelSubscriptionResponse(
    val isSuccessful: Boolean,
    val error: String? = null
)

@Serializable
data class RecordUsageRequest(
    val userId: String,
    val action: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class RecordUsageResponse(
    val isSuccessful: Boolean,
    val error: String? = null
)

@Serializable
data class HealthCheckResponse(
    val status: String,
    val timestamp: Long,
    val version: String? = null
)

enum class SubscriptionTier {
    FREE,
    PRO_MONTHLY,
    PRO_ANNUAL,
    ENTERPRISE
}

enum class SubscriptionStatus {
    ACTIVE,
    INACTIVE,
    CANCELLED,
    PAST_DUE,
    PAYMENT_FAILED
}
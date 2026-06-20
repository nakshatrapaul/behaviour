package com.behaviour.spacedrepetition.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BillingRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("billing_prefs", Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(prefs.getBoolean("is_premium", false))
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    fun setPremiumStatus(premium: Boolean) {
        prefs.edit().putBoolean("is_premium", premium).apply()
        _isPremium.value = premium
    }

    fun isPremium(): Boolean {
        return _isPremium.value
    }

    companion object {
        // Switch to true for production payments, false for sandbox testing
        private const val IS_PRODUCTION = true
        
        // Your real Paddle Production Price ID (starts with "pri_")
        private const val PRODUCT_ID = "YOUR_PRODUCTION_PRICE_ID"

        // Your real Paddle Production Client Side Token (starts with "live_")
        private const val CLIENT_TOKEN = "YOUR_PRODUCTION_CLIENT_TOKEN"

        // Custom checkout page hosted on GitHub Pages or locally
        private const val CHECKOUT_BASE_URL = "https://nakshatrapaul.github.io/behaviour/checkout.html"

        val PADDLE_CHECKOUT_URL = "$CHECKOUT_BASE_URL?price_id=$PRODUCT_ID" +
                if (CLIENT_TOKEN.isNotEmpty()) "&client_token=$CLIENT_TOKEN" else ""
    }
}

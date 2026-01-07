package com.pawpanic.app

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.view.LayoutInflater
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pawpanic.app.databinding.DialogConsentBinding

/**
 * Manages GDPR consent for personalized ads.
 * Uses SharedPreferences ONLY for consent status (required for compliance).
 * No other user data is stored.
 */
class ConsentManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "pawpanic_consent"
        private const val KEY_CONSENT_GIVEN = "consent_given"
        private const val KEY_PERSONALIZED_ADS = "personalized_ads"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Check if user has already provided consent (either way).
     */
    fun hasConsentBeenGiven(): Boolean {
        return prefs.contains(KEY_CONSENT_GIVEN)
    }

    /**
     * Check if user consented to personalized ads.
     */
    fun canShowPersonalizedAds(): Boolean {
        return prefs.getBoolean(KEY_PERSONALIZED_ADS, false)
    }

    /**
     * Show the consent dialog if consent hasn't been given yet.
     * @param activity The activity context
     * @param onConsentComplete Callback when consent process is complete
     */
    fun showConsentDialogIfNeeded(
        activity: Activity,
        onConsentComplete: (personalizedAds: Boolean) -> Unit
    ) {
        if (hasConsentBeenGiven()) {
            // Already have consent, just proceed
            onConsentComplete(canShowPersonalizedAds())
            return
        }

        val binding = DialogConsentBinding.inflate(LayoutInflater.from(activity))

        val dialog = MaterialAlertDialogBuilder(activity, com.google.android.material.R.style.MaterialAlertDialog_Material3)
            .setView(binding.root)
            .setCancelable(false) // User must make a choice
            .create()

        binding.btnConsentAgree.setOnClickListener {
            saveConsent(personalizedAds = true)
            dialog.dismiss()
            onConsentComplete(true)
        }

        binding.btnConsentDecline.setOnClickListener {
            saveConsent(personalizedAds = false)
            dialog.dismiss()
            onConsentComplete(false)
        }

        dialog.show()
    }

    private fun saveConsent(personalizedAds: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CONSENT_GIVEN, true)
            .putBoolean(KEY_PERSONALIZED_ADS, personalizedAds)
            .apply()
    }

    /**
     * Configure Mobile Ads SDK based on consent.
     */
    fun configureAds(personalizedAds: Boolean) {
        val configuration = RequestConfiguration.Builder()

        if (!personalizedAds) {
            // For non-personalized ads, we set the tag for under age of consent
            // This ensures GDPR-compliant non-personalized ads
            configuration.setTagForUnderAgeOfConsent(RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE)
        }

        MobileAds.setRequestConfiguration(configuration.build())
    }

    /**
     * Reset consent (for testing or if user wants to change their choice).
     */
    fun resetConsent() {
        prefs.edit().clear().apply()
    }
}

package com.beam.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.beam.R

/**
 * Single-activity architecture.
 * All screens are Fragments swapped in/out of the fragment_container.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            // Check if API key is configured
            val prefs = getSharedPreferences("beam_secure_prefs", MODE_PRIVATE)
            val hasKey = prefs.contains(SettingsFragment.PREF_API_KEY)

            val startFragment = if (hasKey) {
                HomeFragment()
            } else {
                // First launch → go straight to settings
                SettingsFragment()
            }

            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, startFragment)
                .commit()
        }
    }

    override fun onBackPressed() {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            super.onBackPressed()
        }
    }
}

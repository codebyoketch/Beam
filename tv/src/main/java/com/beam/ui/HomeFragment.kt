package com.beam.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.leanback.widget.*
import com.beam.R
import com.beam.model.Site

/**
 * Home screen showing saved sites and a URL input field.
 * The user can type/paste a URL and hit "Beam It" to analyze the site.
 */
class HomeFragment : Fragment() {

    private val savedSites = mutableListOf(
        // Some starter examples (user can add their own)
        Site(name = "Example: Archive.org", url = "https://archive.org/details/movies"),
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val urlInput = view.findViewById<EditText>(R.id.urlInput)
        val beamButton = view.findViewById<Button>(R.id.beamButton)
        val settingsButton = view.findViewById<Button>(R.id.settingsButton)

        beamButton.setOnClickListener {
            val url = urlInput.text.toString().trim()
            if (url.isNotBlank()) {
                val normalizedUrl = if (url.startsWith("http")) url else "https://$url"
                openBrowse(normalizedUrl)
            }
        }

        settingsButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        // Show saved sites
        savedSites.forEach { site ->
            // TODO: Render saved site cards using Leanback VerticalGridFragment
            // For now, tapping Beam It with a URL is the entry point
        }
    }

    private fun openBrowse(url: String) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, BrowseFragment.newInstance(url))
            .addToBackStack(null)
            .commit()
    }
}

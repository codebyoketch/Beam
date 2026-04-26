package com.beam.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.beam.R
import com.beam.model.StreamType

/**
 * Fullscreen playback fragment using ExoPlayer (Media3).
 * Handles HLS, MP4, and DASH streams automatically.
 */
@UnstableApi
class PlaybackFragment : Fragment() {

    companion object {
        private const val ARG_STREAM_URL = "stream_url"
        private const val ARG_STREAM_TYPE = "stream_type"
        private const val ARG_TITLE = "title"

        fun newInstance(url: String, type: StreamType, title: String): PlaybackFragment {
            return PlaybackFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_STREAM_URL, url)
                    putString(ARG_STREAM_TYPE, type.name)
                    putString(ARG_TITLE, title)
                }
            }
        }
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    private val streamUrl by lazy { arguments?.getString(ARG_STREAM_URL) ?: "" }
    private val streamType by lazy {
        StreamType.valueOf(arguments?.getString(ARG_STREAM_TYPE) ?: StreamType.UNKNOWN.name)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_playback, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        playerView = view.findViewById(R.id.playerView)
        initializePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(requireContext()).build().also { exoPlayer ->
            playerView.player = exoPlayer

            val mediaItem = MediaItem.fromUri(Uri.parse(streamUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        player?.play()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
    }
}

package com.example.media

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/**
 * Manages a lightweight pool of ExoPlayer instances for smooth TikTok-style vertical scrolling
 * with instant preloading of the next video and fast play/pause + mute controls.
 */
class FeedPlayerPool(context: Context) {
    private val appContext = context.applicationContext
    private val players = mutableMapOf<String, ExoPlayer>()
    private val playerUrls = mutableMapOf<String, String>()

    var isMuted by mutableStateOf(false)
        private set

    private fun buildPlayer(): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 400, 900)
            .setBackBuffer(5000, true)
            .build()

        return ExoPlayer.Builder(appContext)
            .setLoadControl(loadControl)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                volume = if (isMuted) 0f else 1f
            }
    }

    fun getOrPreparePlayer(videoId: String, videoUrl: String): ExoPlayer {
        val existing = players[videoId]
        val prevUrl = playerUrls[videoId]
        if (existing != null && prevUrl == videoUrl) {
            existing.volume = if (isMuted) 0f else 1f
            return existing
        }

        val player = existing ?: buildPlayer().also { players[videoId] = it }
        playerUrls[videoId] = videoUrl
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
        player.prepare()
        player.volume = if (isMuted) 0f else 1f
        return player
    }

    fun syncActivePage(videos: List<Pair<String, String>>, activeIndex: Int, isScreenVisible: Boolean) {
        if (videos.isEmpty() || activeIndex !in videos.indices) {
            pauseAll()
            return
        }

        val keepIds = mutableSetOf<String>()
        val windowIndices = listOf(activeIndex - 1, activeIndex, activeIndex + 1)
            .filter { it in videos.indices }

        windowIndices.forEach { idx ->
            val (id, url) = videos[idx]
            keepIds.add(id)
            val player = getOrPreparePlayer(id, url)
            if (idx == activeIndex && isScreenVisible) {
                player.playWhenReady = true
                player.play()
            } else {
                player.playWhenReady = false
                player.pause()
                if (idx != activeIndex) {
                    player.seekTo(0L)
                }
            }
        }

        // Release off-window players to conserve memory and decoders
        val toRemove = players.keys.filter { it !in keepIds }
        toRemove.forEach { id ->
            players.remove(id)?.release()
            playerUrls.remove(id)
        }
    }

    fun togglePlayPause(videoId: String): Boolean {
        val player = players[videoId] ?: return false
        return if (player.isPlaying) {
            player.playWhenReady = false
            player.pause()
            false
        } else {
            player.playWhenReady = true
            player.play()
            true
        }
    }

    fun toggleMute(): Boolean {
        isMuted = !isMuted
        val targetVolume = if (isMuted) 0f else 1f
        players.values.forEach { it.volume = targetVolume }
        return isMuted
    }

    fun pauseAll() {
        players.values.forEach {
            it.playWhenReady = false
            it.pause()
        }
    }

    fun releaseAll() {
        players.values.forEach { it.release() }
        players.clear()
        playerUrls.clear()
    }
}

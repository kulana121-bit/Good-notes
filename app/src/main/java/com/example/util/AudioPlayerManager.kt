package com.example.util

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private var currentPlayingUri: String? = null

    fun playOrToggle(audioUriString: String, onFinished: () -> Unit = {}) {
        if (currentPlayingUri == audioUriString && mediaPlayer != null) {
            if (_isPlaying.value) {
                pause()
            } else {
                resume()
            }
            return
        }

        stop()
        try {
            val player = MediaPlayer()
            val file = File(audioUriString)
            if (file.exists()) {
                player.setDataSource(file.absolutePath)
            } else {
                player.setDataSource(context, Uri.parse(audioUriString))
            }
            player.prepare()
            player.setOnCompletionListener {
                _isPlaying.value = false
                _currentPositionMs.value = 0
                progressJob?.cancel()
                onFinished()
            }
            player.start()
            mediaPlayer = player
            currentPlayingUri = audioUriString
            _durationMs.value = player.duration
            _isPlaying.value = true
            startProgressTracking()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Failed to play audio $audioUriString", e)
            stop()
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _isPlaying.value = false
                progressJob?.cancel()
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _isPlaying.value = true
            startProgressTracking()
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let {
            it.seekTo(positionMs)
            _currentPositionMs.value = positionMs
        }
    }

    fun stop() {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
            currentPlayingUri = null
            _isPlaying.value = false
            _currentPositionMs.value = 0
        }
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _currentPositionMs.value = it.currentPosition
                    }
                }
                delay(100)
            }
        }
    }

    fun release() {
        stop()
    }
}

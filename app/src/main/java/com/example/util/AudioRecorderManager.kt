package com.example.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.util.UUID

class AudioRecorderManager {
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimestamp: Long = 0L
    private var isRecording: Boolean = false

    fun startRecording(context: Context): Result<File> {
        return try {
            val audioDir = File(context.filesDir, "voice_notes").apply { mkdirs() }
            val audioFile = File(audioDir, "voice_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.m4a")
            currentOutputFile = audioFile

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            startTimestamp = System.currentTimeMillis()
            isRecording = true
            Result.success(audioFile)
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error starting audio recording", e)
            mediaRecorder?.release()
            mediaRecorder = null
            currentOutputFile = null
            isRecording = false
            Result.failure(e)
        }
    }

    fun stopRecording(): Long {
        if (!isRecording || mediaRecorder == null) return 0L
        val durationMs = (System.currentTimeMillis() - startTimestamp).coerceAtLeast(0L)
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderManager", "Error stopping recorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return durationMs
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
        } finally {
            mediaRecorder = null
            isRecording = false
            currentOutputFile?.let {
                if (it.exists()) it.delete()
            }
            currentOutputFile = null
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun isRecordingActive(): Boolean = isRecording
}

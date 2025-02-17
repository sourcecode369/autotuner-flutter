package com.example.audio_recorder

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.IOException

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.example.audio_recorder/audio"
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var recordingFile: File? = null
    private var autotunedFile: File? = null
    private lateinit var channel: MethodChannel
    private lateinit var audioProcessor: AudioProcessor

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        audioProcessor = AudioProcessor()
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
        
        channel.setMethodCallHandler { call, result ->
            try {
                when (call.method) {
                    "startRecording" -> {
                        if (checkPermission()) {
                            startRecording(result)
                        } else {
                            requestPermission()
                            result.error("PERMISSION_DENIED", "Audio recording permission not granted", null)
                        }
                    }
                    "stopRecording" -> {
                        stopRecording(result)
                    }
                    "startPlaying" -> {
                        val useAutotuned = call.argument<Boolean>("useAutotuned") ?: true
                        startPlaying(result, useAutotuned)
                    }
                    "stopPlaying" -> {
                        stopPlaying(result)
                    }
                    else -> {
                        result.notImplemented()
                    }
                }
            } catch (e: Exception) {
                println("Error in method channel handler: ${e.message}")
                e.printStackTrace()
                result.error("UNEXPECTED_ERROR", e.message, e.stackTraceToString())
            }
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun startRecording(result: MethodChannel.Result) {
        try {
            println("Starting recording...")
            mediaPlayer?.release()
            mediaPlayer = null
            mediaRecorder?.release()
            mediaRecorder = null

            recordingFile = File(context.cacheDir, "audio_record.mp3")
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(recordingFile?.absolutePath)
                prepare()
                start()
            }
            result.success(recordingFile?.absolutePath)
        } catch (e: Exception) {
            result.error("RECORDING_ERROR", e.message, null)
        }
    }

    private fun stopRecording(result: MethodChannel.Result) {
        try {
            // Add a small delay before stopping to capture any trailing audio
            Thread.sleep(100)
            
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            
            // Process with auto-tune
            processRecordingWithAutotune()
            
            result.success(null)
        } catch (e: Exception) {
            result.error("RECORDING_ERROR", e.message, null)
        }
    }

    private fun startPlaying(result: MethodChannel.Result, useAutotuned: Boolean) {
        mediaPlayer?.release()
        mediaPlayer = null

        try {
            val fileToPlay = if (useAutotuned && autotunedFile?.exists() == true) {
                println("Playing auto-tuned version")
                autotunedFile
            } else {
                println("Playing original version")
                recordingFile
            }
            
            if (fileToPlay?.exists() != true) {
                result.error("FILE_NOT_FOUND", "Audio file not found", null)
                return
            }
            
            println("Starting playback from: ${fileToPlay.absolutePath}")
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(fileToPlay.absolutePath)
                setOnCompletionListener {
                    channel.invokeMethod("onPlaybackComplete", null)
                }
                prepare()
                start()
            }
            result.success(null)
        } catch (e: Exception) {
            println("Error in startPlaying: ${e.message}")
            e.printStackTrace()
            result.error("PLAYBACK_ERROR", e.message, null)
        }
    }

    private fun stopPlaying(result: MethodChannel.Result) {
        try {
            println("Stopping playback...")
            mediaPlayer?.apply {
                stop()
                reset()
                release()
            }
            mediaPlayer = null
            result.success(null)
        } catch (e: Exception) {
            result.error("PLAYBACK_ERROR", e.message, null)
        }
    }
     
    private fun processRecordingWithAutotune() {
        try {
            // Read the recorded file
            val inputFile = recordingFile?.absolutePath ?: return
            val audioData = File(inputFile).readBytes() // Read the entire file

            // Convert to short array (ensure even number of bytes)
            val shortArraySize = audioData.size / 2
            val shortArray = ShortArray(shortArraySize)
            for (i in 0 until shortArraySize) {
                shortArray[i] = (audioData[i * 2].toInt() and 0xFF or 
                            (audioData[i * 2 + 1].toInt() shl 8)).toShort()
            }

            println("Processing ${shortArray.size} samples")

            // Pad the array to a multiple of FRAME_SIZE (1024)
            val frameSize = 1024
            val paddedSize = (shortArray.size + frameSize - 1) / frameSize * frameSize
            val paddedArray = ShortArray(paddedSize) { if (it < shortArray.size) shortArray[it] else 0 }

            // Process the audio
            val processedData = audioProcessor.autotune(paddedArray)

            // Save processed audio
            autotunedFile = File(context.cacheDir, "autotuned_audio.raw")
            val byteArray = ByteArray(processedData.size * 2)
            for (i in processedData.indices) {
                byteArray[i * 2] = (processedData[i].toInt() and 0xFF).toByte()
                byteArray[i * 2 + 1] = (processedData[i].toInt() shr 8).toByte()
            }

            autotunedFile?.outputStream()?.use { 
                it.write(byteArray)
                it.flush()
            }

            println("Saved autotuned file: ${autotunedFile?.absolutePath}, size: ${byteArray.size} bytes")

        } catch (e: Exception) {
            println("Error in auto-tune processing: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        mediaRecorder?.release()
        mediaPlayer?.release()
        mediaRecorder = null
        mediaPlayer = null
        super.onDestroy()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
    }
}
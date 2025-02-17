package com.example.audio_recorder

class AudioProcessor {
    companion object {
        init {
            try {
                System.loadLibrary("autotuner")
            } catch (e: Exception) {
                println("Failed to load autotuner library: ${e.message}")
            }
        }
    }

    external fun processAudio(input: ShortArray, output: ShortArray)

    fun autotune(inputData: ShortArray): ShortArray {
        val output = ShortArray(inputData.size)
        processAudio(inputData, output)
        return output
    }
}
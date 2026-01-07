package com.pawpanic.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.sin

/**
 * Generates pure sine wave tones at specified frequencies.
 * Lightweight implementation that doesn't store any data.
 */
class FrequencyGenerator {

    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private var playbackThread: Thread? = null

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val AMPLITUDE = 0.8 // 80% volume to be safe for pets
    }

    /**
     * Start playing a tone at the specified frequency.
     * @param frequencyHz The frequency in Hertz (1000-22000 recommended)
     */
    fun play(frequencyHz: Int) {
        stop() // Stop any existing playback

        isPlaying = true

        playbackThread = Thread {
            try {
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack?.play()

                val buffer = ShortArray(bufferSize / 2)
                var phase = 0.0
                val phaseIncrement = 2.0 * Math.PI * frequencyHz / SAMPLE_RATE

                while (isPlaying) {
                    for (i in buffer.indices) {
                        buffer[i] = (sin(phase) * AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
                        phase += phaseIncrement
                        if (phase > 2.0 * Math.PI) {
                            phase -= 2.0 * Math.PI
                        }
                    }
                    audioTrack?.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                releaseAudioTrack()
            }
        }

        playbackThread?.start()
    }

    /**
     * Stop the currently playing tone.
     */
    fun stop() {
        isPlaying = false
        playbackThread?.join(100)
        playbackThread = null
        releaseAudioTrack()
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Check if a tone is currently playing.
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * Release all resources. Call this when done with the generator.
     */
    fun release() {
        stop()
    }
}

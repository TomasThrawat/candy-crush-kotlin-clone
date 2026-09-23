package com.tomasthrawat.candycrush.view

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.tomasthrawat.candycrush.R

class GameSoundManager(context: Context) {

    private val soundPool: SoundPool
    private val loadedSounds = HashSet<Int>()
    private val swapSoundId: Int
    private val matchSoundId: Int

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(audioAttributes)
            .setMaxStreams(4)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSounds += sampleId
            }
        }

        swapSoundId = soundPool.load(context, R.raw.swap, 1)
        matchSoundId = soundPool.load(context, R.raw.match, 1)
    }

    fun playSwap(rate: Float = 1f) {
        if (swapSoundId in loadedSounds) {
            soundPool.play(
                swapSoundId,
                0.68f,
                0.68f,
                1,
                0,
                rate.coerceIn(0.5f, 2f)
            )
        }
    }

    fun playMatch() {
        if (matchSoundId in loadedSounds) {
            soundPool.play(matchSoundId, 0.82f, 0.82f, 2, 0, 1f)
        }
    }

    fun release() {
        loadedSounds.clear()
        soundPool.release()
    }
}

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
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setAudioAttributes(attributes)
            .setMaxStreams(10)
            .build()

        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSounds += sampleId
        }

        swapSoundId = soundPool.load(context, R.raw.swap, 1)
        matchSoundId = soundPool.load(context, R.raw.match, 1)
    }

    fun playSwap(rate: Float = 1f, volume: Float = 0.65f) {
        if (swapSoundId in loadedSounds) {
            soundPool.play(
                swapSoundId,
                volume.coerceIn(0f, 1f),
                volume.coerceIn(0f, 1f),
                1,
                0,
                rate.coerceIn(0.55f, 1.8f)
            )
        }
    }

    fun playMatch(combo: Int = 1) {
        if (matchSoundId in loadedSounds) {
            val rate = (0.92f + combo.coerceIn(0, 5) * 0.08f).coerceAtMost(1.35f)
            soundPool.play(matchSoundId, 0.84f, 0.84f, 2, 0, rate)
        }
    }

    fun playReward() {
        if (matchSoundId in loadedSounds) {
            soundPool.play(matchSoundId, 0.95f, 0.95f, 3, 0, 1.55f)
        }
    }

    fun release() {
        loadedSounds.clear()
        soundPool.release()
    }
}

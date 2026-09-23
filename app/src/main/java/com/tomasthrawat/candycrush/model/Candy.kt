package com.tomasthrawat.candycrush.model

import android.graphics.Color

data class Candy(
    val type: Int,
    val row: Int,
    val col: Int,
    val specialDirection: Int = 0
) {
    val color: Int get() = PALETTE[type % PALETTE.size]

    companion object {
        const val NUM_TYPES = 6
        const val BOMB_TYPE = NUM_TYPES
        const val ROCKET_TYPE = NUM_TYPES + 1

        val PALETTE = intArrayOf(
            Color.rgb(195, 51, 134),
            Color.rgb(76, 166, 163),
            Color.rgb(71, 128, 168),
            Color.rgb(242, 163, 111),
            Color.rgb(85, 219, 75),
            Color.rgb(248, 229, 102)
        )
    }
}

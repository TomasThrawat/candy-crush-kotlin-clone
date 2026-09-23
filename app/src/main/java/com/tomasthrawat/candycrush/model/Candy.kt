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
            Color.rgb(255, 79, 163),
            Color.rgb(54, 201, 194),
            Color.rgb(76, 141, 255),
            Color.rgb(255, 154, 77),
            Color.rgb(99, 212, 85),
            Color.rgb(255, 216, 77)
        )
    }
}

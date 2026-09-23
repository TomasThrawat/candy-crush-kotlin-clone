package com.tomasthrawat.candycrush.model

import android.graphics.Color

/**
 * Represents a single candy tile.
 *
 * @param type  the candy type/color id (0..NUM_TYPES-1)
 * @param row   row index in the board
 * @param col   column index in the board
 */
data class Candy(
    val type: Int,
    val row: Int,
    val col: Int
) {
    val color: Int get() = PALETTE[type % PALETTE.size]

    companion object {
        const val NUM_TYPES = 6
        const val BOMB_TYPE = NUM_TYPES

        val PALETTE = intArrayOf(
            Color.parseColor("#FFF8E8"),
            Color.parseColor("#9DD3F7"),
            Color.parseColor("#F4C95D"),
            Color.parseColor("#FFD8AD"),
            Color.parseColor("#C3E1F8"),
            Color.parseColor("#E8CA89")
        )
    }
}

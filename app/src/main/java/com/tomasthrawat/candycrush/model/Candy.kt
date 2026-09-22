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

        val PALETTE = intArrayOf(
            Color.parseColor("#FF3B30"), // red
            Color.parseColor("#FFCC00"), // yellow
            Color.parseColor("#34C759"), // green
            Color.parseColor("#007AFF"), // blue
            Color.parseColor("#AF52DE"), // purple
            Color.parseColor("#FF9500")  // orange
        )
    }
}

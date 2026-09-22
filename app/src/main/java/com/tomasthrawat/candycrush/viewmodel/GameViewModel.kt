package com.tomasthrawat.candycrush.viewmodel

import androidx.lifecycle.ViewModel
import com.tomasthrawat.candycrush.model.GameBoard

/**
 * Lightweight ViewModel for state retention across configuration changes.
 */
class GameViewModel : ViewModel() {
    val board: GameBoard = GameBoard(8, 8)

    fun reset() = board.refill()
}

package com.tomasthrawat.candycrush.viewmodel

import androidx.lifecycle.ViewModel
import com.tomasthrawat.candycrush.model.GameBoard

class GameViewModel : ViewModel() {
    val board = GameBoard(8, 8)
    var coins: Int = 150
    var currentLevel: Long = 1L
    var highestUnlockedLevel: Long = 1L
    val helperCounts = intArrayOf(2, 1, 1)
    val bestScores = HashMap<Long, Int>()
    var loadedFromStorage: Boolean = false
}

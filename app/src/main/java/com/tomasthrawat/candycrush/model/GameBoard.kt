package com.tomasthrawat.candycrush.model

import kotlin.random.Random

/**
 * Pure-Kotlin game logic for a match-3 board.
 *
 * No Android imports — fully unit-testable.
 */
class GameBoard(val rows: Int = 8, val cols: Int = 8) {

    private val board: Array<Array<Candy?>> = Array(rows) { arrayOfNulls<Candy>(cols) }
    var score: Int = 0
        private set
    var movesLeft: Int = 20
        private set

    init { refill() }

    /** Randomly fills the board making sure no initial matches exist. */
    fun refill() {
        for (r in 0 until rows) for (c in 0 until cols) {
            board[r][c] = Candy(Random.nextInt(Candy.NUM_TYPES), r, c)
        }
        // re-roll any pre-existing matches
        var safety = 0
        while (findMatches().isNotEmpty() && safety++ < 200) {
            for (m in findMatches()) {
                board[m.row][m.col] = Candy(Random.nextInt(Candy.NUM_TYPES), m.row, m.col)
            }
        }
    }

    fun get(row: Int, col: Int): Candy? = board[row][col]

    private fun set(row: Int, col: Int, candy: Candy?) {
        board[row][col] = candy?.let { Candy(it.type, row, col) }
    }

    /** Try to swap (r1,c1) and (r2,c2). Returns true if a match resulted. */
    fun swap(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        if (!isAdjacent(r1, c1, r2, c2)) return false
        val a = board[r1][c1] ?: return false
        val b = board[r2][c2] ?: return false
        board[r1][c1] = Candy(b.type, r1, c1)
        board[r2][c2] = Candy(a.type, r2, c2)

        val matches = findMatches()
        if (matches.isEmpty()) {
            // revert
            board[r1][c1] = a
            board[r2][c2] = b
            return false
        }
        movesLeft--
        resolveMatches(matches)
        return true
    }

    private fun isAdjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        val dr = kotlin.math.abs(r1 - r2)
        val dc = kotlin.math.abs(c1 - c2)
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1)
    }

    /** Returns all matching candy positions (3+ in a row, horizontal or vertical). */
    fun findMatches(): Set<Pair<Int, Int>> {
        val result = mutableSetOf<Pair<Int, Int>>()

        // horizontal
        for (r in 0 until rows) {
            var run = 1
            for (c in 1 until cols) {
                val a = board[r][c - 1]?.type
                val b = board[r][c]?.type
                if (a != null && a == b) {
                    run++
                } else {
                    if (run >= 3) for (k in (c - run) until c) result += r to k
                    run = 1
                }
            }
            if (run >= 3) for (k in (cols - run) until cols) result += r to k
        }
        // vertical
        for (c in 0 until cols) {
            var run = 1
            for (r in 1 until rows) {
                val a = board[r - 1][c]?.type
                val b = board[r][c]?.type
                if (a != null && a == b) {
                    run++
                } else {
                    if (run >= 3) for (k in (r - run) until r) result += k to c
                    run = 1
                }
            }
            if (run >= 3) for (k in (rows - run) until rows) result += k to c
        }
        return result
    }

    /** Remove matched candies, drop existing ones, spawn new candies at top. */
    private fun resolveMatches(matches: Set<Pair<Int, Int>>) {
        score += matches.size * 10

        // remove matched
        for ((r, c) in matches) board[r][c] = null

        // drop candies
        for (c in 0 until cols) {
            var write = rows - 1
            for (r in rows - 1 downTo 0) {
                val cell = board[r][c]
                if (cell != null) {
                    board[write][c] = Candy(cell.type, write, c)
                    if (write != r) board[r][c] = null
                    write--
                }
            }
            // fill empty top with new candies
            for (r in write downTo 0) {
                board[r][c] = Candy(Random.nextInt(Candy.NUM_TYPES), r, c)
            }
        }

        // chain reactions
        val cascade = findMatches()
        if (cascade.isNotEmpty()) resolveMatches(cascade)
    }

    fun isGameOver(): Boolean = movesLeft <= 0
}

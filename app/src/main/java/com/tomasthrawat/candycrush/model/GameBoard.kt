package com.tomasthrawat.candycrush.model

import kotlin.random.Random

data class FallingCandy(
    val type: Int,
    val column: Int,
    val startRow: Float,
    val endRow: Float
)

data class ResolveStep(
    val matchedCount: Int,
    val fallingCandies: List<FallingCandy>,
    val bombCell: Pair<Int, Int>? = null
)

data class BombDetonation(
    val explosionCells: List<Pair<Int, Int>>,
    val fallingCandies: List<FallingCandy>
)

class GameBoard(val rows: Int = 8, val cols: Int = 8) {
    private val board: Array<Array<Candy?>> = Array(rows) { arrayOfNulls<Candy>(cols) }

    var score: Int = 0
        private set

    var movesLeft: Int = 20
        private set

    private var pendingMatches: Set<Pair<Int, Int>> = emptySet()
    private var pendingBombCell: Pair<Int, Int>? = null

    init {
        reset()
    }

    fun reset(moves: Int = 20) {
        score = 0
        movesLeft = moves.coerceAtLeast(1)
        pendingMatches = emptySet()
        pendingBombCell = null
        refill()
    }

    fun refill() {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                board[r][c] = Candy(Random.nextInt(Candy.NUM_TYPES), r, c)
            }
        }

        var safety = 0
        while (findMatches().isNotEmpty() && safety++ < 200) {
            for (m in findMatches()) {
                board[m.first][m.second] =
                    Candy(Random.nextInt(Candy.NUM_TYPES), m.first, m.second)
            }
        }
    }

    fun get(row: Int, col: Int): Candy? = board[row][col]

    fun swap(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        if (!isAdjacent(r1, c1, r2, c2)) return false
        if (pendingMatches.isNotEmpty() || pendingBombCell != null) return false

        val a = board[r1][c1] ?: return false
        val b = board[r2][c2] ?: return false

        board[r1][c1] = Candy(b.type, r1, c1)
        board[r2][c2] = Candy(a.type, r2, c2)

        val matches = findMatches()
        if (matches.isEmpty()) {
            board[r1][c1] = a
            board[r2][c2] = b
            return false
        }

        movesLeft--
        pendingMatches = matches
        pendingBombCell = if (matches.size >= 5) {
            chooseBombCell(matches, r2, c2)
        } else {
            null
        }
        return true
    }

    fun prepareCascade(): Boolean {
        if (pendingMatches.isNotEmpty()) return true

        val matches = findMatches()
        if (matches.isEmpty()) return false

        pendingMatches = matches
        pendingBombCell = if (matches.size >= 5) {
            chooseBombCell(matches, -1, -1)
        } else {
            null
        }
        return true
    }

    fun resolveNextStep(): ResolveStep? {
        if (pendingMatches.isEmpty()) {
            if (!prepareCascade()) return null
        }

        val matches = pendingMatches
        val bombCell = pendingBombCell
        pendingMatches = emptySet()
        pendingBombCell = null

        score += matches.size * 10

        if (bombCell != null && bombCell in matches) {
            for ((r, c) in matches) {
                if (r != bombCell.first || c != bombCell.second) {
                    board[r][c] = null
                }
            }
            board[bombCell.first][bombCell.second] =
                Candy(Candy.BOMB_TYPE, bombCell.first, bombCell.second)

            return ResolveStep(
                matchedCount = matches.size,
                fallingCandies = emptyList(),
                bombCell = bombCell
            )
        }

        for ((r, c) in matches) {
            board[r][c] = null
        }

        return ResolveStep(
            matchedCount = matches.size,
            fallingCandies = compactAndRefill(),
            bombCell = null
        )
    }

    fun detonateBomb(row: Int, col: Int): BombDetonation? {
        val bomb = board.getOrNull(row)?.getOrNull(col) ?: return null
        if (bomb.type != Candy.BOMB_TYPE) return null

        val explosionCells = mutableListOf<Pair<Int, Int>>()
        var affectedCount = 0

        for (r in row - 1..row + 1) {
            for (c in col - 1..col + 1) {
                if (r in 0 until rows && c in 0 until cols) {
                    explosionCells += r to c
                    if (board[r][c] != null) affectedCount++
                    board[r][c] = null
                }
            }
        }

        score += affectedCount * 5

        return BombDetonation(
            explosionCells = explosionCells,
            fallingCandies = compactAndRefill()
        )
    }

    private fun compactAndRefill(): List<FallingCandy> {
        val falling = mutableListOf<FallingCandy>()

        for (c in 0 until cols) {
            var write = rows - 1

            for (r in rows - 1 downTo 0) {
                val cell = board[r][c]
                if (cell != null) {
                    if (write != r) {
                        falling += FallingCandy(cell.type, c, r.toFloat(), write.toFloat())
                    }
                    board[write][c] = Candy(cell.type, write, c)
                    if (write != r) {
                        board[r][c] = null
                    }
                    write--
                }
            }

            val missing = write + 1
            for (r in write downTo 0) {
                val finalRow = r
                val startRow = -(missing - r).toFloat()
                val type = Random.nextInt(Candy.NUM_TYPES)
                board[finalRow][c] = Candy(type, finalRow, c)
                falling += FallingCandy(type, c, startRow, finalRow.toFloat())
            }
        }

        return falling
    }

    private fun chooseBombCell(
        matches: Set<Pair<Int, Int>>,
        preferredRow: Int,
        preferredCol: Int
    ): Pair<Int, Int> {
        val preferred = preferredRow to preferredCol
        if (preferredRow >= 0 && preferred in matches) return preferred

        return matches
            .sortedWith(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
            .elementAt(matches.size / 2)
    }

    private fun isMatchable(candy: Candy?): Boolean {
        return candy != null && candy.type in 0 until Candy.NUM_TYPES
    }

    private fun isAdjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        val dr = kotlin.math.abs(r1 - r2)
        val dc = kotlin.math.abs(c1 - c2)
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1)
    }

    fun findMatches(): Set<Pair<Int, Int>> {
        val result = mutableSetOf<Pair<Int, Int>>()

        for (r in 0 until rows) {
            var run = 1
            for (c in 1 until cols) {
                val left = board[r][c - 1]
                val current = board[r][c]
                if (isMatchable(left) && isMatchable(current) && left!!.type == current!!.type) {
                    run++
                } else {
                    if (run >= 3) {
                        for (k in (c - run) until c) result += r to k
                    }
                    run = 1
                }
            }
            if (run >= 3) {
                for (k in (cols - run) until cols) result += r to k
            }
        }

        for (c in 0 until cols) {
            var run = 1
            for (r in 1 until rows) {
                val above = board[r - 1][c]
                val current = board[r][c]
                if (isMatchable(above) && isMatchable(current) && above!!.type == current!!.type) {
                    run++
                } else {
                    if (run >= 3) {
                        for (k in (r - run) until r) result += k to c
                    }
                    run = 1
                }
            }
            if (run >= 3) {
                for (k in (rows - run) until rows) result += k to c
            }
        }

        return result
    }

    fun isGameOver(): Boolean = movesLeft <= 0
}

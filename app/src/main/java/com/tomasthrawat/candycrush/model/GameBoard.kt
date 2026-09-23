package com.tomasthrawat.candycrush.model

import kotlin.random.Random

data class FallingCandy(
    val type: Int,
    val column: Int,
    val startRow: Float,
    val endRow: Float,
    val specialDirection: Int = 0
)

data class ResolveStep(
    val matchedCount: Int,
    val fallingCandies: List<FallingCandy>,
    val specialCell: Pair<Int, Int>? = null,
    val specialType: Int = -1,
    val specialDirection: Int = 0
)

data class BombDetonation(
    val explosionCells: List<Pair<Int, Int>>,
    val fallingCandies: List<FallingCandy>,
    val affectedCount: Int
)

data class RocketLaunch(
    val startCell: Pair<Int, Int>,
    val targetCell: Pair<Int, Int>,
    val fallingCandies: List<FallingCandy>,
    val affectedCount: Int
)

class GameBoard(val rows: Int = 8, val cols: Int = 8) {
    private val board: Array<Array<Candy?>> = Array(rows) { arrayOfNulls<Candy>(cols) }

    var score: Int = 0
        private set

    var movesLeft: Int = Int.MAX_VALUE
        private set

    private var pendingMatches: Set<Pair<Int, Int>> = emptySet()
    private var pendingSpecialCell: Pair<Int, Int>? = null
    private var pendingSpecialType: Int = -1
    private var pendingSpecialDirection: Int = 0

    init {
        reset()
    }

    fun reset(_moves: Int = 20) {
        score = 0
        movesLeft = Int.MAX_VALUE
        pendingMatches = emptySet()
        pendingSpecialCell = null
        pendingSpecialType = -1
        pendingSpecialDirection = 0
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
        if (pendingMatches.isNotEmpty()) return false

        val a = board[r1][c1] ?: return false
        val b = board[r2][c2] ?: return false

        board[r1][c1] = Candy(b.type, r1, c1, b.specialDirection)
        board[r2][c2] = Candy(a.type, r2, c2, a.specialDirection)

        val matches = findMatches()
        if (matches.isEmpty()) {
            board[r1][c1] = a
            board[r2][c2] = b
            return false
        }

        pendingMatches = matches

        val special = chooseSpecial(matches, r2, c2)
        pendingSpecialCell = special?.first
        pendingSpecialType = special?.second ?: -1
        pendingSpecialDirection = special?.third ?: 0
        return true
    }

    fun prepareCascade(): Boolean {
        if (pendingMatches.isNotEmpty()) return true

        val matches = findMatches()
        if (matches.isEmpty()) return false

        pendingMatches = matches
        val special = chooseSpecial(matches, -1, -1)
        pendingSpecialCell = special?.first
        pendingSpecialType = special?.second ?: -1
        pendingSpecialDirection = special?.third ?: 0
        return true
    }

    fun resolveNextStep(): ResolveStep? {
        if (pendingMatches.isEmpty()) {
            if (!prepareCascade()) return null
        }

        val matches = pendingMatches
        val specialCell = pendingSpecialCell
        val specialType = pendingSpecialType
        val specialDirection = pendingSpecialDirection

        pendingMatches = emptySet()
        pendingSpecialCell = null
        pendingSpecialType = -1
        pendingSpecialDirection = 0

        score += matches.size * 30

        if (specialCell != null && specialType >= 0) {
            for ((r, c) in matches) {
                board[r][c] = null
            }

            val fallingCandies = compactAndRefill()
            board[specialCell.first][specialCell.second] =
                Candy(specialType, specialCell.first, specialCell.second, specialDirection)

            return ResolveStep(
                matchedCount = matches.size,
                fallingCandies = fallingCandies,
                specialCell = specialCell,
                specialType = specialType,
                specialDirection = specialDirection
            )
        }

        for ((r, c) in matches) {
            board[r][c] = null
        }

        return ResolveStep(
            matchedCount = matches.size,
            fallingCandies = compactAndRefill()
        )
    }

    fun detonateBomb(row: Int, col: Int, directionRow: Int, directionCol: Int): BombDetonation? {
        val bomb = board.getOrNull(row)?.getOrNull(col) ?: return null
        if (bomb.type != Candy.BOMB_TYPE) return null

        val dr = directionRow.coerceIn(-1, 1)
        val dc = directionCol.coerceIn(-1, 1)
        if (dr == 0 && dc == 0) return null

        val explosionCells = mutableListOf<Pair<Int, Int>>()
        var r = row
        var c = col

        while (r in 0 until rows && c in 0 until cols) {
            explosionCells += r to c
            r += dr
            c += dc
        }

        var affectedCount = 0
        for ((er, ec) in explosionCells) {
            if (board[er][ec] != null) {
                affectedCount++
                board[er][ec] = null
            }
        }

        score += affectedCount * 24

        return BombDetonation(
            explosionCells = explosionCells,
            fallingCandies = compactAndRefill(),
            affectedCount = affectedCount
        )
    }

    fun prepareRocketLaunch(row: Int, col: Int): Pair<Int, Int>? {
        val rocket = board.getOrNull(row)?.getOrNull(col) ?: return null
        if (rocket.type != Candy.ROCKET_TYPE) return null

        val candidates = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (r == row && c == col) continue
                val candy = board[r][c] ?: continue
                if (candy.type in 0 until Candy.NUM_TYPES) {
                    candidates += r to c
                }
            }
        }

        return candidates.randomOrNull()
    }

    fun finishRocketLaunch(
        startRow: Int,
        startCol: Int,
        targetRow: Int,
        targetCol: Int
    ): RocketLaunch? {
        val rocket = board.getOrNull(startRow)?.getOrNull(startCol) ?: return null
        if (rocket.type != Candy.ROCKET_TYPE) return null

        if (targetRow !in 0 until rows || targetCol !in 0 until cols) return null

        val affectedCount = if (board[targetRow][targetCol] != null) 1 else 0
        board[startRow][startCol] = null
        board[targetRow][targetCol] = null
        score += 135

        return RocketLaunch(
            startCell = startRow to startCol,
            targetCell = targetRow to targetCol,
            fallingCandies = compactAndRefill(),
            affectedCount = affectedCount
        )
    }

    fun removeCell(row: Int, col: Int): List<FallingCandy> {
        if (row !in 0 until rows || col !in 0 until cols) return emptyList()
        if (board[row][col] == null) return emptyList()
        board[row][col] = null
        score += 45
        return compactAndRefill()
    }

    fun clearCross(row: Int, col: Int): List<FallingCandy> {
        if (row !in 0 until rows || col !in 0 until cols) return emptyList()
        var cleared = 0
        for (c in 0 until cols) {
            if (board[row][c] != null) {
                board[row][c] = null
                cleared++
            }
        }
        for (r in 0 until rows) {
            if (r != row && board[r][col] != null) {
                board[r][col] = null
                cleared++
            }
        }
        score += cleared * 12
        return compactAndRefill()
    }

    fun shuffle() {
        val values = mutableListOf<Int>()
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val candy = board[r][c]
                if (candy != null && candy.type in 0 until Candy.NUM_TYPES) {
                    values += candy.type
                }
            }
        }

        values.shuffle()
        var index = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                board[r][c] = Candy(
                    values.getOrNull(index) ?: Random.nextInt(Candy.NUM_TYPES),
                    r,
                    c
                )
                index++
            }
        }

        var safety = 0
        while (findMatches().isNotEmpty() && safety++ < 100) {
            val matches = findMatches()
            for ((r, c) in matches) {
                board[r][c] = Candy(Random.nextInt(Candy.NUM_TYPES), r, c)
            }
        }
    }

    fun addMoves(_amount: Int) {
        movesLeft = Int.MAX_VALUE
    }

    private fun compactAndRefill(): List<FallingCandy> {
        val falling = mutableListOf<FallingCandy>()

        for (c in 0 until cols) {
            var write = rows - 1

            for (r in rows - 1 downTo 0) {
                val cell = board[r][c]
                if (cell != null) {
                    if (write != r) {
                        falling += FallingCandy(
                            type = cell.type,
                            column = c,
                            startRow = r.toFloat(),
                            endRow = write.toFloat(),
                            specialDirection = cell.specialDirection
                        )
                    }
                    board[write][c] =
                        Candy(cell.type, write, c, cell.specialDirection)
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
                falling += FallingCandy(
                    type = type,
                    column = c,
                    startRow = startRow,
                    endRow = finalRow.toFloat()
                )
            }
        }

        return falling
    }

    private fun chooseSpecial(
        matches: Set<Pair<Int, Int>>,
        preferredRow: Int,
        preferredCol: Int
    ): Triple<Pair<Int, Int>, Int, Int>? {
        if (matches.isEmpty()) return null

        var bestLength = 0
        var bestHorizontal = true
        var bestCells: List<Pair<Int, Int>> = emptyList()

        for (r in 0 until rows) {
            var start = 0
            while (start < cols) {
                val type = board[r][start]?.type
                if (type !in 0 until Candy.NUM_TYPES) {
                    start++
                    continue
                }

                var end = start + 1
                while (end < cols && board[r][end]?.type == type) end++
                val length = end - start
                if (length >= 4 && length > bestLength) {
                    bestLength = length
                    bestHorizontal = true
                    bestCells = (start until end).map { r to it }
                }
                start = end
            }
        }

        for (c in 0 until cols) {
            var start = 0
            while (start < rows) {
                val type = board[start][c]?.type
                if (type !in 0 until Candy.NUM_TYPES) {
                    start++
                    continue
                }

                var end = start + 1
                while (end < rows && board[end][c]?.type == type) end++
                val length = end - start
                if (length >= 5 && length > bestLength) {
                    bestLength = length
                    bestHorizontal = false
                    bestCells = (start until end).map { it to c }
                } else if (length >= 4 && length > bestLength) {
                    bestLength = length
                    bestHorizontal = false
                    bestCells = (start until end).map { it to c }
                }
                start = end
            }
        }

        if (bestLength < 4) return null

        var cell = if (preferredRow >= 0 && preferredRow to preferredCol in bestCells) {
            preferredRow to preferredCol
        } else {
            bestCells[bestCells.size / 2]
        }

        val specialType =
            if (bestLength >= 5) Candy.BOMB_TYPE else Candy.ROCKET_TYPE

        val direction = if (bestHorizontal) 0 else 1

        if (specialType == Candy.BOMB_TYPE && preferredRow >= 0) {
            cell = if (preferredRow to preferredCol in bestCells) {
                preferredRow to preferredCol
            } else {
                bestCells[bestCells.size / 2]
            }
        }

        return Triple(cell, specialType, direction)
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

    fun isGameOver(): Boolean = false
}

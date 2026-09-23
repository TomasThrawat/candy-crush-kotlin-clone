package com.tomasthrawat.candycrush.model

import kotlin.math.abs
import kotlin.random.Random

private const val UNLIMITED_MOVES = Int.MAX_VALUE
private const val SCORE_MULTIPLIER = 5

data class FallingCandy(
    val type: Int,
    val column: Int,
    val startRow: Float,
    val endRow: Float,
    val specialDirection: Int = 0
)

data class ResolveStep(
    val matchedCount: Int,
    val matchedCells: Set<Pair<Int, Int>>,
    val fallingCandies: List<FallingCandy>,
    val specialCell: Pair<Int, Int>? = null,
    val specialType: Int = -1,
    val specialDirection: Int = 0,
    val specialActivated: Boolean = false
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

data class ColorBombDetonation(
    val centerCell: Pair<Int, Int>,
    val targetType: Int,
    val clearedCells: List<Pair<Int, Int>>,
    val fallingCandies: List<FallingCandy>,
    val affectedCount: Int
)

class GameBoard(val rows: Int = 8, val cols: Int = 8) {
    private val board = Array(rows) { arrayOfNulls<Candy>(cols) }

    var score: Int = 0
        private set

    var movesLeft: Int = 24
        private set

    var targetScore: Int = 650
        private set

    private var pendingMatches: Set<Pair<Int, Int>> = emptySet()
    private var pendingSpecialCell: Pair<Int, Int>? = null
    private var pendingSpecialType: Int = -1
    private var pendingSpecialDirection: Int = 0
    private var pendingSpecialActivated: Boolean = false
    private var pendingColorBombTargetType: Int = -1

    init { reset() }

    fun reset(@Suppress("UNUSED_PARAMETER") moves: Int = 24, target: Int = 650) {
        score = 0
        movesLeft = UNLIMITED_MOVES
        targetScore = target
        pendingMatches = emptySet()
        pendingSpecialCell = null
        pendingSpecialType = -1
        pendingSpecialDirection = 0
        pendingSpecialActivated = false
        pendingColorBombTargetType = -1
        fillFreshPlayableBoard()
    }

    fun get(row: Int, col: Int): Candy? =
        if (row in 0 until rows && col in 0 until cols) board[row][col] else null

    fun pendingMatchCells(): Set<Pair<Int, Int>> = pendingMatches

    fun snapshotTypes(): IntArray {
        val snapshot = IntArray(rows * cols)
        var i = 0
        for (r in 0 until rows) for (c in 0 until cols) snapshot[i++] = board[r][c]?.type ?: -1
        return snapshot
    }

    fun restoreTypes(snapshot: IntArray, savedScore: Int, @Suppress("UNUSED_PARAMETER") savedMoves: Int, savedTarget: Int) {
        if (snapshot.size != rows * cols) {
            reset(UNLIMITED_MOVES, savedTarget.coerceAtLeast(1))
            return
        }
        var i = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val type = snapshot[i++]
                board[r][c] = if (type in 0..Candy.COLOR_BOMB_TYPE) Candy(type, r, c) else Candy(Random.nextInt(Candy.NUM_TYPES), r, c)
            }
        }
        score = savedScore.coerceAtLeast(0)
        movesLeft = UNLIMITED_MOVES
        targetScore = savedTarget.coerceAtLeast(1)
        pendingMatches = emptySet()
        pendingSpecialCell = null
        pendingSpecialType = -1
        pendingSpecialDirection = 0
        pendingSpecialActivated = false
        pendingColorBombTargetType = -1
        if (findMatches().isNotEmpty() || !hasPossibleMove()) {
            fillFreshPlayableBoard()
            score = savedScore.coerceAtLeast(0)
            movesLeft = UNLIMITED_MOVES
            targetScore = savedTarget.coerceAtLeast(1)
        }
    }

    fun swap(r1: Int, c1: Int, r2: Int, c2: Int, requestedRocketDirection: Int = 0): Boolean {
        if (!isAdjacent(r1, c1, r2, c2)) return false
        if (pendingMatches.isNotEmpty()) return false

        val a = get(r1, c1) ?: return false
        val b = get(r2, c2) ?: return false

        val aIsColorBomb = a.type == Candy.COLOR_BOMB_TYPE
        val bIsColorBomb = b.type == Candy.COLOR_BOMB_TYPE
        if (aIsColorBomb || bIsColorBomb) {
            board[r1][c1] = Candy(b.type, r1, c1, b.specialDirection)
            board[r2][c2] = Candy(a.type, r2, c2, a.specialDirection)

            val bombRow = if (aIsColorBomb) r2 else r1
            val bombCol = if (aIsColorBomb) c2 else c1
            val target = if (aIsColorBomb) b.type else a.type

            val targetType = if (target in 0 until Candy.NUM_TYPES) {
                target
            } else {
                -1
            }

            pendingSpecialCell = bombRow to bombCol
            pendingSpecialType = Candy.COLOR_BOMB_TYPE
            pendingSpecialDirection = targetType
            pendingSpecialActivated = true
            pendingColorBombTargetType = targetType
            pendingMatches = collectColorBombCells(bombRow, bombCol, targetType)
            return true
        }

        board[r1][c1] = Candy(b.type, r1, c1, b.specialDirection)
        board[r2][c2] = Candy(a.type, r2, c2, a.specialDirection)

        val matches = findMatches()
        if (matches.isEmpty()) {
            board[r1][c1] = Candy(a.type, r1, c1, a.specialDirection)
            board[r2][c2] = Candy(b.type, r2, c2, b.specialDirection)
            return false
        }

        pendingSpecialActivated = false
        pendingColorBombTargetType = -1
        val special = chooseSpecial(matches, r1, c1, r2, c2, requestedRocketDirection)
        pendingSpecialCell = special?.first
        pendingSpecialType = special?.second ?: -1
        pendingSpecialDirection = special?.third ?: 0

        if (special != null) {
            val cell = special.first
            board[cell.first][cell.second] = Candy(special.second, cell.first, cell.second, special.third)
            pendingMatches = matches - cell
        } else {
            pendingMatches = matches
        }
        return true
    }

    fun prepareCascade(): Boolean {
        if (pendingMatches.isNotEmpty()) return true
        val matches = findMatches()
        if (matches.isEmpty()) return false
        pendingSpecialCell = null
        pendingSpecialType = -1
        pendingSpecialDirection = 0
        pendingSpecialActivated = false
        pendingColorBombTargetType = -1
        pendingMatches = matches
        return true
    }

    fun resolveNextStep(): ResolveStep? {
        if (pendingMatches.isEmpty() && !prepareCascade()) return null

        val matches = pendingMatches
        val specialCell = pendingSpecialCell
        val specialType = pendingSpecialType
        val specialDirection = pendingSpecialDirection
        val specialActivated = pendingSpecialActivated

        pendingMatches = emptySet()
        pendingSpecialCell = null
        pendingSpecialType = -1
        pendingSpecialDirection = 0
        pendingSpecialActivated = false
        pendingColorBombTargetType = -1

        if (matches.isEmpty()) return ResolveStep(0, emptySet(), emptyList(), specialCell, specialType, specialDirection, specialActivated)

        addScore(matches.size * if (specialActivated) 32 else 30)
        for ((r, c) in matches) board[r][c] = null
        val falling = compactAndRefill(specialCell)

        return ResolveStep(
            matchedCount = matches.size,
            matchedCells = matches,
            fallingCandies = falling,
            specialCell = specialCell,
            specialType = specialType,
            specialDirection = specialDirection,
            specialActivated = specialActivated
        )
    }

    fun detonateColorBomb(row: Int, col: Int, requestedType: Int = -1): ColorBombDetonation? {
        val bomb = get(row, col) ?: return null
        if (bomb.type != Candy.COLOR_BOMB_TYPE) return null

        val targetType = if (requestedType in 0 until Candy.NUM_TYPES) {
            requestedType
        } else {
            mostCommonNormalType()
        }
        val cells = collectColorBombCells(row, col, targetType)

        var affected = 0
        for ((r, c) in cells) {
            if (board[r][c] != null) {
                board[r][c] = null
                affected++
            }
        }
        addScore(affected * 32)

        return ColorBombDetonation(
            centerCell = row to col,
            targetType = targetType,
            clearedCells = cells.toList(),
            fallingCandies = compactAndRefill(),
            affectedCount = affected
        )
    }

    private fun collectColorBombCells(row: Int, col: Int, targetType: Int): LinkedHashSet<Pair<Int, Int>> {
        val cells = LinkedHashSet<Pair<Int, Int>>()
        val sameType = targetType in 0 until Candy.NUM_TYPES

        if (sameType) {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (board[r][c]?.type == targetType) {
                        cells += r to c
                        for (dr in -1..1) {
                            for (dc in -1..1) {
                                val nr = r + dr
                                val nc = c + dc
                                if (nr in 0 until rows && nc in 0 until cols) {
                                    cells += nr to nc
                                }
                            }
                        }
                    }
                }
            }
        } else {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (board[r][c] != null) cells += r to c
                }
            }
        }

        cells += row to col
        return cells
    }

    private fun mostCommonNormalType(): Int {
        val counts = IntArray(Candy.NUM_TYPES)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val type = board[r][c]?.type ?: continue
                if (type in 0 until Candy.NUM_TYPES) counts[type]++
            }
        }

        var bestType = 0
        var bestCount = -1
        for (type in 0 until Candy.NUM_TYPES) {
            if (counts[type] > bestCount) {
                bestCount = counts[type]
                bestType = type
            }
        }
        return bestType
    }

    fun detonateBomb(row: Int, col: Int, directionRow: Int, directionCol: Int): BombDetonation? {
        val bomb = get(row, col) ?: return null
        if (bomb.type != Candy.BOMB_TYPE) return null

        val cells = LinkedHashSet<Pair<Int, Int>>()
        for (c in 0 until cols) cells += row to c
        for (r in 0 until rows) cells += r to col
        val dr = directionRow.coerceIn(-1, 1)
        val dc = directionCol.coerceIn(-1, 1)
        if (dr != 0 || dc != 0) {
            val a = row + dr to col + dc
            val b = row - dr to col - dc
            if (a.first in 0 until rows && a.second in 0 until cols) cells += a
            if (b.first in 0 until rows && b.second in 0 until cols) cells += b
        }

        var affected = 0
        for ((r, c) in cells) {
            if (board[r][c] != null) {
                board[r][c] = null
                affected++
            }
        }
        addScore(affected * 24)
        return BombDetonation(cells.toList(), compactAndRefill(), affected)
    }

    fun prepareRocketLaunch(row: Int, col: Int): Pair<Int, Int>? {
        val rocket = get(row, col) ?: return null
        if (rocket.type != Candy.ROCKET_TYPE) return null
        return if (rocket.specialDirection == 1) {
            (if (row < rows / 2) rows - 1 else 0) to col
        } else {
            row to if (col < cols / 2) cols - 1 else 0
        }
    }

    fun finishRocketLaunch(startRow: Int, startCol: Int, targetRow: Int, targetCol: Int): RocketLaunch? {
        val rocket = get(startRow, startCol) ?: return null
        if (rocket.type != Candy.ROCKET_TYPE) return null

        val cells = LinkedHashSet<Pair<Int, Int>>()
        if (rocket.specialDirection == 1) for (r in 0 until rows) cells += r to startCol
        else for (c in 0 until cols) cells += startRow to c

        var affected = 0
        for ((r, c) in cells) {
            if (board[r][c] != null) {
                board[r][c] = null
                affected++
            }
        }
        addScore(affected * 36)
        return RocketLaunch(startRow to startCol, targetRow to targetCol, compactAndRefill(), affected)
    }

    fun removeCell(row: Int, col: Int): List<FallingCandy> {
        if (get(row, col) == null) return emptyList()
        board[row][col] = null
        addScore(45)
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
        addScore(cleared * 12)
        return compactAndRefill()
    }

    fun shuffle() {
        val values = ArrayList<Int>(rows * cols)
        repeat(rows) { r -> repeat(cols) {
            val type = board[r][it]?.type ?: Random.nextInt(Candy.NUM_TYPES)
            values += if (type in 0 until Candy.NUM_TYPES) type else Random.nextInt(Candy.NUM_TYPES)
        } }
        repeat(160) {
            values.shuffle()
            var i = 0
            for (r in 0 until rows) for (c in 0 until cols) board[r][c] = Candy(values[i++], r, c)
            if (findMatches().isEmpty() && hasPossibleMove()) return
        }
        fillFreshPlayableBoard()
    }

    fun hasPossibleMove(): Boolean {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (c + 1 < cols && wouldSwapCreateMatch(r, c, r, c + 1)) return true
                if (r + 1 < rows && wouldSwapCreateMatch(r, c, r + 1, c)) return true
            }
        }
        return false
    }

    fun addMoves(amount: Int) {
        if (amount > 0) movesLeft = UNLIMITED_MOVES
    }

    private fun addScore(basePoints: Int) {
        if (basePoints <= 0) return
        val added = basePoints.toLong() * SCORE_MULTIPLIER.toLong()
        score = (score.toLong() + added).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun isGameOver(): Boolean = false

    private fun fillFreshPlayableBoard() {
        repeat(500) {
            for (r in 0 until rows) for (c in 0 until cols) board[r][c] = Candy(Random.nextInt(Candy.NUM_TYPES), r, c)
            if (findMatches().isEmpty() && hasPossibleMove()) return
        }
    }

    private fun compactAndRefill(lockedCell: Pair<Int, Int>? = null): List<FallingCandy> {
        val falling = mutableListOf<FallingCandy>()
        for (c in 0 until cols) {
            if (lockedCell?.second == c) {
                compactSegment(c, lockedCell.first + 1, rows - 1, falling)
                compactSegment(c, 0, lockedCell.first - 1, falling)
            } else {
                compactSegment(c, 0, rows - 1, falling)
            }
        }
        return falling
    }

    private fun compactSegment(column: Int, top: Int, bottom: Int, falling: MutableList<FallingCandy>) {
        if (top > bottom) return
        var write = bottom
        for (r in bottom downTo top) {
            val cell = board[r][column] ?: continue
            if (write != r) {
                falling += FallingCandy(cell.type, column, r.toFloat(), write.toFloat(), cell.specialDirection)
            }
            board[write][column] = Candy(cell.type, write, column, cell.specialDirection)
            if (write != r) board[r][column] = null
            write--
        }
        for (r in write downTo top) {
            val type = Random.nextInt(Candy.NUM_TYPES)
            board[r][column] = Candy(type, r, column)
            falling += FallingCandy(type, column, (top - (write - r + 1)).toFloat(), r.toFloat())
        }
    }

    private fun chooseSpecial(
        matches: Set<Pair<Int, Int>>,
        r1: Int,
        c1: Int,
        r2: Int,
        c2: Int,
        requestedRocketDirection: Int
    ): Triple<Pair<Int, Int>, Int, Int>? {
        val sixHorizontal = matches.filter { hasRunAtLeast(it.first, it.second, true, 6, matches) }
        val sixVertical = matches.filter { hasRunAtLeast(it.first, it.second, false, 6, matches) }
        if (sixHorizontal.isNotEmpty() || sixVertical.isNotEmpty()) {
            val candidates = if (sixHorizontal.isNotEmpty()) sixHorizontal else sixVertical
            val cell = when {
                r1 to c1 in candidates -> r1 to c1
                r2 to c2 in candidates -> r2 to c2
                else -> candidates[candidates.size / 2]
            }
            return Triple(cell, Candy.COLOR_BOMB_TYPE, 0)
        }

        if (matches.size >= 5) {
            val intersection = matches.firstOrNull { cell ->
                hasRunAtLeast(cell.first, cell.second, true, 3, matches) &&
                hasRunAtLeast(cell.first, cell.second, false, 3, matches)
            }
            val cell = intersection ?: if (r1 to c1 in matches) r1 to c1 else matches.first()
            return Triple(cell, Candy.BOMB_TYPE, 0)
        }

        val horizontal = matches.filter { hasRunAtLeast(it.first, it.second, true, 4, matches) }
        val vertical = matches.filter { hasRunAtLeast(it.first, it.second, false, 4, matches) }
        if (horizontal.isEmpty() && vertical.isEmpty()) return null

        val useHorizontal = when {
            horizontal.isNotEmpty() && vertical.isEmpty() -> true
            horizontal.isEmpty() && vertical.isNotEmpty() -> false
            else -> requestedRocketDirection == 0
        }
        val candidates = if (useHorizontal) horizontal else vertical
        val cell = when {
            r1 to c1 in candidates -> r1 to c1
            r2 to c2 in candidates -> r2 to c2
            else -> candidates[candidates.size / 2]
        }
        return Triple(cell, Candy.ROCKET_TYPE, if (useHorizontal) 0 else 1)
    }

    private fun hasRunAtLeast(row: Int, col: Int, horizontal: Boolean, minLength: Int, set: Set<Pair<Int, Int>>): Boolean {
        var count = 1
        var cursor = if (horizontal) col - 1 else row - 1
        while (cursor >= 0) {
            val cell = if (horizontal) row to cursor else cursor to col
            if (cell !in set) break
            count++
            cursor--
        }
        cursor = if (horizontal) col + 1 else row + 1
        val max = if (horizontal) cols else rows
        while (cursor < max) {
            val cell = if (horizontal) row to cursor else cursor to col
            if (cell !in set) break
            count++
            cursor++
        }
        return count >= minLength
    }

    private fun wouldSwapCreateMatch(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        val a = board[r1][c1] ?: return false
        val b = board[r2][c2] ?: return false
        if (a.type !in 0 until Candy.NUM_TYPES || b.type !in 0 until Candy.NUM_TYPES) return false
        board[r1][c1] = b
        board[r2][c2] = a
        val result = findMatches().isNotEmpty()
        board[r1][c1] = a
        board[r2][c2] = b
        return result
    }

    fun findMatches(): Set<Pair<Int, Int>> {
        val result = LinkedHashSet<Pair<Int, Int>>()

        for (r in 0 until rows) {
            var run = 1
            for (c in 1 until cols) {
                val a = board[r][c - 1]
                val b = board[r][c]
                if (isMatchable(a) && isMatchable(b) && a!!.type == b!!.type) run++
                else {
                    if (run >= 3) for (k in c - run until c) result += r to k
                    run = 1
                }
            }
            if (run >= 3) for (k in cols - run until cols) result += r to k
        }

        for (c in 0 until cols) {
            var run = 1
            for (r in 1 until rows) {
                val a = board[r - 1][c]
                val b = board[r][c]
                if (isMatchable(a) && isMatchable(b) && a!!.type == b!!.type) run++
                else {
                    if (run >= 3) for (k in r - run until r) result += k to c
                    run = 1
                }
            }
            if (run >= 3) for (k in rows - run until rows) result += k to c
        }
        return result
    }

    private fun isMatchable(candy: Candy?): Boolean =
        candy != null && candy.type in 0 until Candy.NUM_TYPES

    private fun isAdjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        val dr = abs(r1 - r2)
        val dc = abs(c1 - c2)
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1)
    }
}

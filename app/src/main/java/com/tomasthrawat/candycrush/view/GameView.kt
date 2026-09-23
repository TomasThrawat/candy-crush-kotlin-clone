package com.tomasthrawat.candycrush.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.tomasthrawat.candycrush.R
import com.tomasthrawat.candycrush.model.Candy
import com.tomasthrawat.candycrush.model.FallingCandy
import com.tomasthrawat.candycrush.model.GameBoard
import com.tomasthrawat.candycrush.viewmodel.GameViewModel
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    private enum class Screen { MENU, GAME, SHOP, HELPERS, LEVEL_MAP }
    private enum class Helper(val title: String, val price: Int) {
        HAMMER("HAMMER", 60),
        CROSS("CROSS BLAST", 90),
        MOVES("+5 MOVES", 120)
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        val size: Float,
        val type: Int
    )

    private data class FloatingText(
        var x: Float,
        var y: Float,
        val text: String,
        var life: Float
    )

    private var viewModel: GameViewModel? = null
    private var board: GameBoard = GameBoard(8, 8)
    private val prefs = context.getSharedPreferences("candy_rush", Context.MODE_PRIVATE)
    private val sound = GameSoundManager(context)

    private var screen = Screen.MENU
    private var paused = false
    private var levelComplete = false
    private var levelFailed = false
    private var helper: Helper? = null
    private var menuOpen = false
    private var mapScroll = 0f
    private var started = false
    private var lastFrameMs = SystemClock.uptimeMillis()

    private var selectedCell: Pair<Int, Int>? = null
    private var downX = 0f
    private var downY = 0f
    private var downRow = -1
    private var downCol = -1
    private var rocketStart: Pair<Int, Int>? = null
    private var rocketTarget: Pair<Int, Int>? = null

    private var swapAnimator: ValueAnimator? = null
    private var matchAnimator: ValueAnimator? = null
    private var fallAnimator: ValueAnimator? = null
    private var specialAnimator: ValueAnimator? = null
    private var shuffleAnimator: ValueAnimator? = null

    private var swapProgress = 0f
    private var matchProgress = 0f
    private var fallProgress = 0f
    private var specialProgress = 0f
    private var shuffleProgress = 0f

    private var swapFrom: Pair<Int, Int>? = null
    private var swapTo: Pair<Int, Int>? = null
    private var swapAType = -1
    private var swapBType = -1
    private var swapWasValid = false

    private val matchedVisuals = LinkedHashMap<Pair<Int, Int>, Int>()
    private var fallingCandies: List<FallingCandy> = emptyList()
    private var combo = 0
    private var lastActionTime = 0L

    private val particles = ArrayList<Particle>(160)
    private val floatingTexts = ArrayList<FloatingText>(12)

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val smallTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candyPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tempRect = RectF()
    private val tempPath = Path()

    private var backgroundShader: Shader? = null

    private val candyBitmaps = arrayOfNulls<Bitmap>(Candy.NUM_TYPES)
    private var jarBitmap: Bitmap? = null

    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardSize = 0f
    private var cellSize = 0f
    private var bottomBarTop = 0f

    private val accent = Color.rgb(255, 215, 108)
    private val cream = Color.rgb(255, 245, 222)
    private val panel = Color.rgb(45, 39, 73)
    private val panel2 = Color.rgb(61, 50, 92)
    private val backgroundTop = Color.rgb(33, 18, 53)
    private val backgroundBottom = Color.rgb(14, 26, 45)

    init {
        isClickable = true
        titlePaint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        textPaint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        smallTextPaint.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)

        outlinePaint.style = Paint.Style.STROKE
        val candyIds = intArrayOf(
            R.drawable.candy_ref_0,
            R.drawable.candy_ref_1,
            R.drawable.candy_ref_2,
            R.drawable.candy_ref_3,
            R.drawable.candy_ref_4,
            R.drawable.candy_ref_5
        )
        for (i in candyIds.indices) {
            candyBitmaps[i] = BitmapFactory.decodeResource(resources, candyIds[i])
        }
        jarBitmap = BitmapFactory.decodeResource(resources, R.drawable.peppermint_candy_jar)
    }

    fun attachViewModel(model: GameViewModel) {
        viewModel = model
        board = model.board
        if (!model.loadedFromStorage) {
            loadPersistentState(model)
            model.loadedFromStorage = true
        }
        invalidate()
    }

    fun startGame() {
        if (started) return
        started = true
        if (board.movesLeft <= 0) {
            startLevel(viewModel?.currentLevel ?: 1L)
        }
        screen = Screen.MENU
        invalidate()
    }

    private fun loadPersistentState(model: GameViewModel) {
        model.coins = prefs.getInt("coins", 150)
        model.currentLevel = prefs.getLong("current_level", 1L).coerceIn(1L, 30L)
        model.highestUnlockedLevel = prefs.getLong("highest_level", 1L).coerceIn(1L, 30L)
        for (i in 0..2) {
            model.helperCounts[i] = prefs.getInt("helper_" + i, model.helperCounts[i])
        }
        for (level in 1L..30L) {
            val best = prefs.getInt("best_" + level, 0)
            if (best > 0) model.bestScores[level] = best
        }

        val raw = prefs.getString("board", null)
        val snapshot = raw?.split(",")?.mapNotNull { it.toIntOrNull() }?.toIntArray()
        if (snapshot != null && snapshot.size == board.rows * board.cols) {
            board.restoreTypes(
                snapshot,
                prefs.getInt("board_score", 0),
                prefs.getInt("board_moves", 24),
                prefs.getInt("board_target", targetScoreForLevel(model.currentLevel))
            )
        } else {
            startLevel(model.currentLevel)
        }
    }

    private fun savePersistentState() {
        val model = viewModel ?: return
        val edit = prefs.edit()
        edit.putInt("coins", model.coins)
        edit.putLong("current_level", model.currentLevel)
        edit.putLong("highest_level", model.highestUnlockedLevel)
        edit.putString("board", board.snapshotTypes().joinToString(","))
        edit.putInt("board_score", board.score)
        edit.putInt("board_moves", board.movesLeft)
        edit.putInt("board_target", board.targetScore)
        for (i in 0..2) edit.putInt("helper_" + i, model.helperCounts[i])
        for ((level, best) in model.bestScores) edit.putInt("best_" + level, best)
        edit.apply()
    }

    private fun startLevel(level: Long) {
        val safe = level.coerceIn(1L, 30L)
        viewModel?.currentLevel = safe
        board.reset(movesForLevel(safe), targetScoreForLevel(safe))
        matchedVisuals.clear()
        fallingCandies = emptyList()
        selectedCell = null
        helper = null
        combo = 0
        paused = false
        levelComplete = false
        levelFailed = false
        menuOpen = false
        savePersistentState()
        invalidate()
    }

    private fun movesForLevel(level: Long): Int = when {
        level <= 5L -> 26
        level <= 12L -> 24
        level <= 20L -> 22
        else -> 20
    }

    private fun targetScoreForLevel(level: Long): Int =
        620 + (level - 1L).coerceAtMost(29L).toInt() * 72

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backgroundShader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            backgroundTop, backgroundBottom,
            Shader.TileMode.CLAMP
        )
        bottomBarTop = h - dp(86f)
        val side = dp(14f)
        val availableWidth = w - side * 2f
        val availableHeight = bottomBarTop - dp(120f)
        cellSize = min(
            (availableWidth - dp(10f)) / board.cols,
            availableHeight / board.rows
        )
        boardSize = cellSize * board.cols
        boardLeft = (w - boardSize) / 2f
        boardTop = dp(112f)
    }

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        when (screen) {
            Screen.MENU -> drawMainMenu(canvas)
            Screen.GAME -> drawGame(canvas)
            Screen.SHOP -> drawShop(canvas)
            Screen.HELPERS -> drawHelpers(canvas)
            Screen.LEVEL_MAP -> drawLevelMap(canvas)
        }
        if (isBusy() || particles.isNotEmpty() || floatingTexts.isNotEmpty() || selectedCell != null) {
            updateEffects()
            postInvalidateOnAnimation()
        }
    }

    private fun drawBackground(canvas: Canvas) {
        boardPaint.shader = backgroundShader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), boardPaint)
        boardPaint.shader = null

        for (i in 0 until 16) {
            val x = ((i * 97) % maxOf(width, 1)).toFloat()
            val y = dp((26 + (i * 61) % 620).toFloat())
            val r = dp((2 + i % 4).toFloat())
            boardPaint.color = Color.argb(42 + (i % 3) * 12, 255, 235, 170)
            canvas.drawCircle(x, y, r, boardPaint)
        }
    }

    private fun drawGame(canvas: Canvas) {
        drawTopHud(canvas)
        drawBoardPanel(canvas)
        drawBoard(canvas)
        drawBottomBar(canvas)

        if (shuffleProgress > 0f) drawShuffleOverlay(canvas)
        if (paused) drawPauseOverlay(canvas)
        if (levelComplete || levelFailed) drawResultOverlay(canvas)
        if (menuOpen) drawGameMenu(canvas)
    }

    private fun drawTopHud(canvas: Canvas) {
        titlePaint.textAlign = Paint.Align.LEFT
        titlePaint.color = cream
        titlePaint.textSize = sp(20f)
        canvas.drawText("LEVEL " + (viewModel?.currentLevel ?: 1L), dp(18f), dp(30f), titlePaint)
        titlePaint.color = Color.argb(175, 255, 245, 222)
        titlePaint.textSize = sp(11f)
        canvas.drawText("TARGET " + board.targetScore, dp(18f), dp(50f), titlePaint)

        drawHudChip(canvas, RectF(dp(138f), dp(12f), dp(216f), dp(60f)), "SCORE", board.score.toString(), accent)
        drawHudChip(canvas, RectF(dp(222f), dp(12f), dp(300f), dp(60f)), "MOVES", board.movesLeft.toString(), cream)

        val coinLeft = width - dp(91f)
        drawHudChip(canvas, RectF(coinLeft, dp(12f), width - dp(16f), dp(60f)), "COINS", (viewModel?.coins ?: 0).toString(), accent)

        rect.set(width - dp(60f), dp(70f), width - dp(16f), dp(84f))
        boardPaint.color = Color.argb(165, 255, 255, 255)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), boardPaint)
        boardPaint.color = accent
        rect.right = rect.left + rect.width() * (board.score.toFloat() / board.targetScore).coerceIn(0f, 1f)
        canvas.drawRoundRect(rect, dp(6f), dp(6f), boardPaint)

        boardPaint.color = Color.argb(205, 255, 255, 255)
        rect.set(width - dp(66f), dp(88f), width - dp(14f), dp(108f))
        canvas.drawRoundRect(rect, dp(8f), dp(8f), boardPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = backgroundTop
        textPaint.textSize = sp(9f)
        canvas.drawText("MENU", rect.centerX(), rect.centerY() + dp(3f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawHudChip(canvas: Canvas, chip: RectF, label: String, value: String, valueColor: Int) {
        boardPaint.color = Color.argb(80, 0, 0, 0)
        canvas.drawRoundRect(RectF(chip.left, chip.top + dp(4f), chip.right, chip.bottom + dp(4f)), dp(15f), dp(15f), boardPaint)
        boardPaint.color = panel2
        canvas.drawRoundRect(chip, dp(15f), dp(15f), boardPaint)

        smallTextPaint.textAlign = Paint.Align.CENTER
        smallTextPaint.color = Color.argb(150, 255, 245, 222)
        smallTextPaint.textSize = sp(8f)
        canvas.drawText(label, chip.centerX(), chip.top + dp(14f), smallTextPaint)

        smallTextPaint.color = valueColor
        smallTextPaint.textSize = sp(15f)
        canvas.drawText(value, chip.centerX(), chip.bottom - dp(10f), smallTextPaint)
        smallTextPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawBoardPanel(canvas: Canvas) {
        val p = dp(8f)
        rect.set(boardLeft - p, boardTop - p, boardLeft + boardSize + p, boardTop + boardSize + p)
        boardPaint.color = Color.argb(85, 0, 0, 0)
        canvas.drawRoundRect(RectF(rect.left, rect.top + dp(7f), rect.right, rect.bottom + dp(7f)), dp(24f), dp(24f), boardPaint)
        boardPaint.color = Color.rgb(49, 42, 73)
        canvas.drawRoundRect(rect, dp(24f), dp(24f), boardPaint)
        outlinePaint.color = Color.argb(95, 255, 236, 180)
        outlinePaint.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, dp(23f), dp(23f), outlinePaint)
    }

    private fun drawBoard(canvas: Canvas) {
        val fallingTargets = HashSet<Pair<Int, Int>>()
        for (falling in fallingCandies) {
            val row = falling.endRow.toInt()
            if (row in 0 until board.rows) fallingTargets += row to falling.column
        }

        for (r in 0 until board.rows) {
            for (c in 0 until board.cols) {
                val center = cellCenter(r, c)
                drawCellSlot(canvas, center.x, center.y, r, c)
                val cell = r to c

                if (cell in fallingTargets) continue
                val matchedType = matchedVisuals[cell]
                if (matchedType != null) {
                    val alpha = (255f * (1f - matchProgress)).toInt().coerceIn(0, 255)
                    val scale = 0.90f * (1f - matchProgress * 0.35f)
                    drawCandy(canvas, matchedType, center.x, center.y, scale, alpha)
                    continue
                }

                if (swapFrom == cell || swapTo == cell) continue
                board.get(r, c)?.let {
                    drawCandy(canvas, it.type, center.x, center.y, 0.88f, 255, it.specialDirection)
                }
            }
        }

        drawSwapCandies(canvas)
        drawFallingCandies(canvas)
        drawRocketAnimation(canvas)
        drawSpecialPulse(canvas)
        drawParticles(canvas)
        drawFloatingTexts(canvas)

        if (selectedCell != null && !isBusy()) {
            val center = cellCenter(selectedCell!!.first, selectedCell!!.second)
            val pulse = 1f + 0.07f * sin(SystemClock.uptimeMillis() / 115.0).toFloat()
            outlinePaint.color = Color.argb(225, 255, 232, 142)
            outlinePaint.strokeWidth = dp(2.5f)
            rect.set(
                center.x - cellSize * 0.40f * pulse,
                center.y - cellSize * 0.40f * pulse,
                center.x + cellSize * 0.40f * pulse,
                center.y + cellSize * 0.40f * pulse
            )
            canvas.drawRoundRect(rect, cellSize * 0.18f, cellSize * 0.18f, outlinePaint)
        }
    }

    private fun drawCellSlot(canvas: Canvas, cx: Float, cy: Float, row: Int, col: Int) {
        val gap = cellSize * 0.055f
        rect.set(
            cx - cellSize / 2f + gap,
            cy - cellSize / 2f + gap,
            cx + cellSize / 2f - gap,
            cy + cellSize / 2f - gap
        )
        boardPaint.color = Color.argb(if ((row + col) % 2 == 0) 58 else 44, 255, 255, 255)
        canvas.drawRoundRect(rect, cellSize * 0.16f, cellSize * 0.16f, boardPaint)
    }

    private fun drawCandy(
        canvas: Canvas,
        type: Int,
        cx: Float,
        cy: Float,
        scale: Float,
        alpha: Int,
        specialDirection: Int = 0
    ) {
        if (type < 0) return
        val size = cellSize * scale
        if (type in 0 until Candy.NUM_TYPES) {
            val bitmap = candyBitmaps[type]
            if (bitmap != null && !bitmap.isRecycled) {
                tempRect.set(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f)
                candyPaint.alpha = alpha
                canvas.drawBitmap(bitmap, null, tempRect, candyPaint)
                candyPaint.alpha = 255
            } else {
                val color = Candy.PALETTE[type % Candy.NUM_TYPES]
                boardPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawCircle(cx, cy, size * 0.37f, boardPaint)
                boardPaint.color = Color.argb(alpha / 2, 255, 255, 255)
                canvas.drawCircle(cx - size * 0.13f, cy - size * 0.13f, size * 0.09f, boardPaint)
            }
        } else if (type == Candy.BOMB_TYPE) {
            drawBomb(canvas, cx, cy, size, alpha)
        } else if (type == Candy.ROCKET_TYPE) {
            drawRocket(canvas, cx, cy, size, alpha, specialDirection)
        }
    }

    private fun drawBomb(canvas: Canvas, cx: Float, cy: Float, size: Float, alpha: Int) {
        boardPaint.color = Color.argb(alpha, 58, 48, 78)
        canvas.drawCircle(cx, cy, size * 0.37f, boardPaint)
        boardPaint.color = Color.argb(alpha, 255, 214, 102)
        canvas.drawCircle(cx - size * 0.11f, cy - size * 0.12f, size * 0.07f, boardPaint)

        tempPath.reset()
        for (i in 0 until 8) {
            val angle = i * Math.PI / 4.0
            val r1 = size * 0.23f
            val r2 = size * 0.39f
            val x1 = cx + cos(angle).toFloat() * r1
            val y1 = cy + sin(angle).toFloat() * r1
            val x2 = cx + cos(angle).toFloat() * r2
            val y2 = cy + sin(angle).toFloat() * r2
            if (i == 0) tempPath.moveTo(x1, y1) else tempPath.moveTo(x1, y1)
            tempPath.lineTo(x2, y2)
        }
        outlinePaint.color = Color.argb(alpha, 255, 224, 132)
        outlinePaint.strokeWidth = size * 0.045f
        canvas.drawPath(tempPath, outlinePaint)
    }

    private fun drawRocket(canvas: Canvas, cx: Float, cy: Float, size: Float, alpha: Int, direction: Int) {
        boardPaint.color = Color.argb(alpha, 250, 249, 241)
        rect.set(cx - size * 0.34f, cy - size * 0.26f, cx + size * 0.34f, cy + size * 0.26f)
        canvas.drawRoundRect(rect, size * 0.25f, size * 0.25f, boardPaint)
        boardPaint.color = Color.argb(alpha, 242, 103, 132)
        if (direction == 0) {
            rect.set(cx - size * 0.16f, cy - size * 0.20f, cx + size * 0.16f, cy + size * 0.20f)
        } else {
            rect.set(cx - size * 0.20f, cy - size * 0.16f, cx + size * 0.20f, cy + size * 0.16f)
        }
        canvas.drawRoundRect(rect, size * 0.12f, size * 0.12f, boardPaint)
        outlinePaint.color = Color.argb(alpha, 116, 84, 154)
        outlinePaint.strokeWidth = size * 0.035f
        if (direction == 0) {
            canvas.drawLine(cx - size * 0.48f, cy, cx - size * 0.15f, cy, outlinePaint)
            canvas.drawLine(cx + size * 0.15f, cy, cx + size * 0.48f, cy, outlinePaint)
        } else {
            canvas.drawLine(cx, cy - size * 0.48f, cx, cy - size * 0.15f, outlinePaint)
            canvas.drawLine(cx, cy + size * 0.15f, cx, cy + size * 0.48f, outlinePaint)
        }
    }

    private fun drawSwapCandies(canvas: Canvas) {
        val from = swapFrom ?: return
        val to = swapTo ?: return
        val a = cellCenter(from.first, from.second)
        val b = cellCenter(to.first, to.second)
        val p = ease(swapProgress)
        val ax = lerp(a.x, b.x, p)
        val ay = lerp(a.y, b.y, p)
        val bx = lerp(b.x, a.x, p)
        val by = lerp(b.y, a.y, p)
        val boost = 0.88f + sin(p * Math.PI).toFloat() * 0.045f
        drawCandy(canvas, swapAType, ax, ay, boost, 255)
        drawCandy(canvas, swapBType, bx, by, boost, 255)
    }

    private fun drawFallingCandies(canvas: Canvas) {
        val p = ease(fallProgress)
        for (fall in fallingCandies) {
            val row = lerp(fall.startRow, fall.endRow, p)
            val center = cellCenterAt(row, fall.column)
            val squash = 1f + sin(p * Math.PI).toFloat() * 0.045f
            drawCandy(canvas, fall.type, center.x, center.y, 0.88f * squash, 255, fall.specialDirection)
        }
    }

    private fun drawSpecialPulse(canvas: Canvas) {
        if (specialAnimator?.isRunning != true) return
        val cell = selectedCell ?: rocketStart ?: return
        val center = cellCenter(cell.first, cell.second)
        val p = ease(specialProgress)
        outlinePaint.color = Color.argb((190f * (1f - p)).toInt(), 255, 222, 118)
        outlinePaint.strokeWidth = dp(3f)
        canvas.drawCircle(center.x, center.y, cellSize * (0.36f + p * 0.24f), outlinePaint)
    }

    private fun drawRocketAnimation(canvas: Canvas) {
        val from = rocketStart ?: return
        val to = rocketTarget ?: return
        if (specialAnimator?.isRunning != true) return

        val a = cellCenter(from.first, from.second)
        val b = cellCenter(to.first, to.second)
        val p = ease(specialProgress)
        val x = lerp(a.x, b.x, p)
        val y = lerp(a.y, b.y, p)
        val angle = Math.toDegrees(kotlin.math.atan2((b.y - a.y).toDouble(), (b.x - a.x).toDouble())).toFloat()

        canvas.save()
        canvas.rotate(angle, x, y)
        boardPaint.color = Color.argb(245, 255, 245, 224)
        canvas.drawRoundRect(
            RectF(x - cellSize * 0.27f, y - cellSize * 0.11f, x + cellSize * 0.27f, y + cellSize * 0.11f),
            cellSize * 0.11f,
            cellSize * 0.11f,
            boardPaint
        )
        boardPaint.color = Color.argb(230, 242, 103, 132)
        canvas.drawCircle(x + cellSize * 0.12f, y, cellSize * 0.09f, boardPaint)
        canvas.restore()
    }

    private fun drawParticles(canvas: Canvas) {
        for (particle in particles) {
            val alpha = (255f * particle.life.coerceIn(0f, 1f)).toInt()
            val c = if (particle.type and 1 == 0) Color.rgb(255, 219, 118) else Color.rgb(255, 157, 188)
            boardPaint.color = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
            canvas.drawCircle(particle.x, particle.y, particle.size * particle.life.coerceAtLeast(0.2f), boardPaint)
        }
    }

    private fun drawFloatingTexts(canvas: Canvas) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = sp(13f)
        for (item in floatingTexts) {
            textPaint.color = Color.argb((255f * item.life).toInt().coerceIn(0, 255), 255, 238, 147)
            canvas.drawText(item.text, item.x, item.y, textPaint)
        }
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun updateEffects() {
        val now = SystemClock.uptimeMillis()
        val dt = ((now - lastFrameMs).coerceIn(1L, 34L)) / 1000f
        lastFrameMs = now

        val it = particles.iterator()
        while (it.hasNext()) {
            val particle = it.next()
            particle.x += particle.vx * dt * 60f
            particle.y += particle.vy * dt * 60f
            particle.vy += 0.13f * cellSize
            particle.life -= dt
            if (particle.life <= 0f) it.remove()
        }

        val ft = floatingTexts.iterator()
        while (ft.hasNext()) {
            val item = ft.next()
            item.y -= cellSize * 0.02f
            item.life -= dt
            if (item.life <= 0f) ft.remove()
        }
    }

    private fun isBusy(): Boolean =
        swapAnimator?.isRunning == true ||
        matchAnimator?.isRunning == true ||
        fallAnimator?.isRunning == true ||
        specialAnimator?.isRunning == true ||
        shuffleAnimator?.isRunning == true
 
    private fun drawBottomBar(canvas: Canvas) {
        rect.set(dp(12f), bottomBarTop + dp(9f), width - dp(12f), height - dp(10f))
        boardPaint.color = Color.argb(235, 25, 24, 46)
        canvas.drawRoundRect(rect, dp(25f), dp(25f), boardPaint)

        val helperList = Helper.values()
        val slotW = (width - dp(36f)) / 3f
        for (i in helperList.indices) {
            val item = helperList[i]
            val cx = dp(18f) + slotW * i + slotW / 2f
            val active = helper == item
            boardPaint.color = if (active) Color.rgb(112, 82, 148) else Color.rgb(55, 48, 79)
            canvas.drawRoundRect(
                RectF(cx - slotW * 0.38f, bottomBarTop + dp(18f), cx + slotW * 0.38f, height - dp(20f)),
                dp(18f), dp(18f), boardPaint
            )
            drawHelperGlyph(canvas, cx, bottomBarTop + dp(39f), i)
            smallTextPaint.textAlign = Paint.Align.CENTER
            smallTextPaint.color = if (active) accent else Color.argb(190, 255, 245, 222)
            smallTextPaint.textSize = sp(9f)
            canvas.drawText((viewModel?.helperCounts?.getOrNull(i) ?: 0).toString(), cx, bottomBarTop + dp(67f), smallTextPaint)
            smallTextPaint.textAlign = Paint.Align.LEFT
        }

        smallTextPaint.color = Color.argb(145, 255, 245, 222)
        smallTextPaint.textSize = sp(8f)
        canvas.drawText("TAP CANDY • SWIPE TO SWAP", dp(18f), bottomBarTop + dp(12f), smallTextPaint)
    }

    private fun drawHelperGlyph(canvas: Canvas, cx: Float, cy: Float, index: Int) {
        outlinePaint.style = Paint.Style.STROKE
        outlinePaint.strokeWidth = dp(2.2f)
        outlinePaint.color = accent
        when (index) {
            0 -> {
                canvas.save()
                canvas.rotate(-42f, cx, cy)
                canvas.drawRoundRect(RectF(cx - dp(3f), cy - dp(11f), cx + dp(3f), cy + dp(7f)), dp(2f), dp(2f), outlinePaint)
                canvas.drawLine(cx - dp(8f), cy - dp(6f), cx + dp(8f), cy - dp(6f), outlinePaint)
                canvas.restore()
            }
            1 -> {
                canvas.drawCircle(cx, cy, dp(9f), outlinePaint)
                canvas.drawLine(cx - dp(6f), cy - dp(6f), cx + dp(6f), cy + dp(6f), outlinePaint)
                canvas.drawLine(cx + dp(6f), cy - dp(6f), cx - dp(6f), cy + dp(6f), outlinePaint)
            }
            else -> {
                canvas.drawCircle(cx, cy, dp(9f), outlinePaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.textSize = sp(10f)
                textPaint.color = accent
                canvas.drawText("+5", cx, cy + dp(4f), textPaint)
                textPaint.textAlign = Paint.Align.LEFT
            }
        }
    }

    private fun drawMainMenu(canvas: Canvas) {
        val cx = width / 2f
        titlePaint.textAlign = Paint.Align.CENTER
        titlePaint.color = cream
        titlePaint.textSize = sp(43f)
        canvas.drawText("CANDY", cx, dp(98f), titlePaint)
        titlePaint.color = accent
        titlePaint.textSize = sp(48f)
        canvas.drawText("RUSH", cx, dp(145f), titlePaint)

        jarBitmap?.let {
            tempRect.set(cx - dp(75f), dp(164f), cx + dp(75f), dp(286f))
            candyPaint.alpha = 245
            canvas.drawBitmap(it, null, tempRect, candyPaint)
            candyPaint.alpha = 255
        }

        drawMenuButton(canvas, RectF(dp(42f), dp(316f), width - dp(42f), dp(374f)), "PLAY")
        drawMenuButton(canvas, RectF(dp(42f), dp(388f), width - dp(42f), dp(446f)), "LEVEL MAP")
        drawMenuButton(canvas, RectF(dp(42f), dp(460f), width - dp(42f), dp(518f)), "HELPERS")
        drawMenuButton(canvas, RectF(dp(42f), dp(532f), width - dp(42f), dp(590f)), "SHOP")

        smallTextPaint.textAlign = Paint.Align.CENTER
        smallTextPaint.color = Color.argb(145, 255, 245, 222)
        smallTextPaint.textSize = sp(10f)
        canvas.drawText("MATCH • CASCADE • COMBO", cx, height - dp(23f), smallTextPaint)
        smallTextPaint.textAlign = Paint.Align.LEFT
        titlePaint.textAlign = Paint.Align.LEFT
    }

    private fun drawMenuButton(canvas: Canvas, button: RectF, label: String) {
        boardPaint.color = Color.argb(75, 0, 0, 0)
        canvas.drawRoundRect(RectF(button.left, button.top + dp(5f), button.right, button.bottom + dp(5f)), dp(20f), dp(20f), boardPaint)
        boardPaint.color = panel2
        canvas.drawRoundRect(button, dp(20f), dp(20f), boardPaint)
        boardPaint.color = Color.argb(120, 255, 229, 150)
        canvas.drawRoundRect(
            RectF(button.left + dp(3f), button.top + dp(3f), button.right - dp(3f), button.top + dp(6f)),
            dp(2f), dp(2f), boardPaint
        )
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = cream
        textPaint.textSize = sp(17f)
        canvas.drawText(label, button.centerX(), button.centerY() + dp(6f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawShop(canvas: Canvas) {
        drawSubscreenHeader(canvas, "HELPER SHOP")
        val list = Helper.values()
        for (i in list.indices) {
            val y = dp(126f) + i * dp(116f)
            drawShopItem(canvas, list[i], y, viewModel?.helperCounts?.getOrNull(i) ?: 0)
        }
        drawFooterBack(canvas)
    }

    private fun drawShopItem(canvas: Canvas, item: Helper, y: Float, count: Int) {
        rect.set(dp(18f), y, width - dp(18f), y + dp(94f))
        boardPaint.color = panel
        canvas.drawRoundRect(rect, dp(22f), dp(22f), boardPaint)
        drawHelperGlyph(canvas, dp(58f), y + dp(47f), item.ordinal)

        textPaint.color = cream
        textPaint.textSize = sp(15f)
        canvas.drawText(item.title, dp(92f), y + dp(37f), textPaint)
        smallTextPaint.color = Color.argb(160, 255, 245, 222)
        smallTextPaint.textSize = sp(10f)
        canvas.drawText("OWNED " + count, dp(92f), y + dp(59f), smallTextPaint)

        val buy = RectF(width - dp(130f), y + dp(24f), width - dp(32f), y + dp(70f))
        boardPaint.color = if ((viewModel?.coins ?: 0) >= item.price) Color.rgb(111, 80, 145) else Color.rgb(72, 66, 88)
        canvas.drawRoundRect(buy, dp(14f), dp(14f), boardPaint)
        smallTextPaint.textAlign = Paint.Align.CENTER
        smallTextPaint.color = accent
        smallTextPaint.textSize = sp(11f)
        canvas.drawText("BUY " + item.price, buy.centerX(), buy.centerY() + dp(4f), smallTextPaint)
        smallTextPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawHelpers(canvas: Canvas) {
        drawSubscreenHeader(canvas, "HELPERS")
        val descriptions = arrayOf(
            "Remove one candy without using a move.",
            "Clear the full row and column at one cell.",
            "Add five moves immediately."
        )
        val list = Helper.values()
        for (i in list.indices) {
            val y = dp(122f) + i * dp(124f)
            rect.set(dp(18f), y, width - dp(18f), y + dp(101f))
            boardPaint.color = panel
            canvas.drawRoundRect(rect, dp(21f), dp(21f), boardPaint)
            drawHelperGlyph(canvas, dp(58f), y + dp(50f), i)

            textPaint.color = cream
            textPaint.textSize = sp(15f)
            canvas.drawText(list[i].title, dp(90f), y + dp(37f), textPaint)
            smallTextPaint.color = Color.argb(155, 255, 245, 222)
            smallTextPaint.textSize = sp(9f)
            canvas.drawText(descriptions[i], dp(90f), y + dp(60f), smallTextPaint)
            smallTextPaint.color = accent
            canvas.drawText("OWNED " + (viewModel?.helperCounts?.getOrNull(i) ?: 0), dp(90f), y + dp(82f), smallTextPaint)
        }
        drawFooterBack(canvas)
    }

    private fun drawSubscreenHeader(canvas: Canvas, title: String) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = cream
        textPaint.textSize = sp(24f)
        canvas.drawText(title, width / 2f, dp(48f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        smallTextPaint.color = accent
        smallTextPaint.textSize = sp(11f)
        canvas.drawText("COINS " + (viewModel?.coins ?: 0), dp(18f), dp(78f), smallTextPaint)
        boardPaint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRect(dp(18f), dp(91f), width - dp(18f), dp(92f), boardPaint)
    }

    private fun drawFooterBack(canvas: Canvas) {
        drawMenuButton(canvas, RectF(dp(30f), height - dp(70f), width - dp(30f), height - dp(20f)), "BACK")
    }

    private fun drawLevelMap(canvas: Canvas) {
        drawSubscreenHeader(canvas, "LEVEL MAP")
        val unlocked = viewModel?.highestUnlockedLevel ?: 1L
        val rowH = dp(92f)
        val mapTop = dp(112f)

        canvas.save()
        canvas.clipRect(0f, mapTop, width.toFloat(), height - dp(12f))
        canvas.translate(0f, -mapScroll)

        for (level in 1L..30L) {
            val index = (level - 1L).toInt()
            val row = index / 3
            val col = index % 3
            val x = width * (0.18f + 0.32f * col)
            val y = mapTop + rowH * row + dp(42f)
            val unlockedNow = level <= unlocked
            val current = level == (viewModel?.currentLevel ?: 1L)

            boardPaint.color = if (unlockedNow) Color.rgb(93, 68, 126) else Color.rgb(57, 55, 73)
            canvas.drawCircle(x, y + dp(4f), dp(23f), boardPaint)
            boardPaint.color = if (unlockedNow) accent else Color.argb(75, 255, 255, 255)
            canvas.drawCircle(x, y, dp(if (current) 25f else 22f), boardPaint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = if (unlockedNow) backgroundTop else Color.argb(125, 255, 255, 255)
            textPaint.textSize = sp(13f)
            canvas.drawText(level.toString(), x, y + dp(5f), textPaint)
            textPaint.textAlign = Paint.Align.LEFT

            if (current) {
                outlinePaint.color = Color.argb(235, 255, 248, 195)
                outlinePaint.strokeWidth = dp(2f)
                canvas.drawCircle(x, y, dp(31f), outlinePaint)
            }

            if (unlockedNow) {
                smallTextPaint.textAlign = Paint.Align.CENTER
                smallTextPaint.color = Color.argb(145, 255, 245, 222)
                smallTextPaint.textSize = sp(7f)
                canvas.drawText("BEST " + (viewModel?.bestScores?.get(level) ?: 0), x, y + dp(38f), smallTextPaint)
                smallTextPaint.textAlign = Paint.Align.LEFT
            }
        }
        canvas.restore()
    }

    private fun drawPauseOverlay(canvas: Canvas) {
        drawDim(canvas, 175)
        drawOverlayCard(canvas, "PAUSED", "Your board is saved safely.")
        drawMenuButton(canvas, RectF(dp(48f), height / 2f + dp(25f), width - dp(48f), height / 2f + dp(82f)), "RESUME")
    }

    private fun drawResultOverlay(canvas: Canvas) {
        drawDim(canvas, 185)
        val title = if (levelComplete) "LEVEL CLEAR" else "OUT OF MOVES"
        val detail = if (levelComplete) {
            "+" + rewardCoins() + " COINS   •   COMBO x" + combo
        } else {
            "TARGET " + board.targetScore + "   •   SCORE " + board.score
        }
        drawOverlayCard(canvas, title, detail)
        drawMenuButton(
            canvas,
            RectF(dp(48f), height / 2f + dp(29f), width - dp(48f), height / 2f + dp(86f)),
            if (levelComplete) "NEXT LEVEL" else "RETRY"
        )
    }

    private fun drawGameMenu(canvas: Canvas) {
        drawDim(canvas, 150)
        rect.set(dp(38f), dp(184f), width - dp(38f), dp(462f))
        boardPaint.color = Color.rgb(32, 28, 52)
        canvas.drawRoundRect(rect, dp(26f), dp(26f), boardPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = cream
        textPaint.textSize = sp(22f)
        canvas.drawText("GAME MENU", width / 2f, dp(226f), textPaint)
        textPaint.textAlign = Paint.Align.LEFT

        drawMenuButton(canvas, RectF(dp(58f), dp(256f), width - dp(58f), dp(309f)), "RESUME")
        drawMenuButton(canvas, RectF(dp(58f), dp(320f), width - dp(58f), dp(373f)), "RESTART")
        drawMenuButton(canvas, RectF(dp(58f), dp(384f), width - dp(58f), dp(437f)), "EXIT")
    }

    private fun drawOverlayCard(canvas: Canvas, title: String, detail: String) {
        rect.set(dp(32f), height / 2f - dp(96f), width - dp(32f), height / 2f + dp(9f))
        boardPaint.color = Color.rgb(32, 28, 52)
        canvas.drawRoundRect(rect, dp(28f), dp(28f), boardPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = accent
        textPaint.textSize = sp(25f)
        canvas.drawText(title, rect.centerX(), rect.top + dp(46f), textPaint)

        smallTextPaint.textAlign = Paint.Align.CENTER
        smallTextPaint.color = cream
        smallTextPaint.textSize = sp(10f)
        canvas.drawText(detail, rect.centerX(), rect.top + dp(73f), smallTextPaint)

        textPaint.textAlign = Paint.Align.LEFT
        smallTextPaint.textAlign = Paint.Align.LEFT
    }

    private fun drawDim(canvas: Canvas, alpha: Int) {
        boardPaint.color = Color.argb(alpha, 5, 5, 10)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), boardPaint)
    }

    private fun drawShuffleOverlay(canvas: Canvas) {
        drawDim(canvas, (95f * shuffleProgress).toInt())
        smallTextPaint.textAlign = Paint.Align.CENTER
        smallTextPaint.color = accent
        smallTextPaint.textSize = sp(12f)
        canvas.drawText("RESHUFFLING…", width / 2f, boardTop + boardSize / 2f, smallTextPaint)
        smallTextPaint.textAlign = Paint.Align.LEFT
    }

    private fun beginSwap(from: Pair<Int, Int>, to: Pair<Int, Int>, requestedDirection: Int) {
        if (isBusy() || paused || levelComplete || levelFailed) return
        val a = board.get(from.first, from.second) ?: return
        val b = board.get(to.first, to.second) ?: return

        swapFrom = from
        swapTo = to
        swapAType = a.type
        swapBType = b.type
        swapProgress = 0f

        swapWasValid = board.swap(
            from.first, from.second,
            to.first, to.second,
            requestedDirection
        )

        if (swapWasValid) {
            sound.playSwap(0.98f, 0.62f)
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        } else {
            sound.playSwap(0.72f, 0.35f)
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        swapAnimator?.cancel()
        swapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (swapWasValid) 150L else 175L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                swapProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    swapAnimator = null
                    if (swapWasValid) startMatchAnimation() else clearMoveVisuals()
                }
            })
            start()
        }
    }

    private fun startMatchAnimation() {
        val cells = board.pendingMatchCells()
        if (cells.isEmpty()) {
            continueResolution()
            return
        }

        matchedVisuals.clear()
        for (cell in cells) {
            val candy = board.get(cell.first, cell.second)
            matchedVisuals[cell] = candy?.type ?: 0
        }

        val now = SystemClock.uptimeMillis()
        combo = if (now - lastActionTime < 2200L) combo + 1 else 1
        lastActionTime = now
        matchProgress = 0f

        emitParticles(cells, 7)
        sound.playMatch(combo)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        matchAnimator?.cancel()
        matchAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 175L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                matchProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    matchAnimator = null
                    val step = board.resolveNextStep()
                    if (step == null) {
                        clearMoveVisuals()
                        return
                    }

                    val cx = width / 2f
                    floatingTexts += FloatingText(
                        cx,
                        boardTop - dp(8f),
                        "+" + (step.matchedCount * 30) + if (combo > 1) "  COMBO x" + combo else "",
                        0.95f
                    )

                    if (step.specialCell != null && step.specialType >= 0) {
                        floatingTexts += FloatingText(
                            cx,
                            boardTop + dp(17f),
                            if (step.specialType == Candy.BOMB_TYPE) "BOMB CREATED" else "ROCKET CREATED",
                            0.95f
                        )
                        emitParticles(setOf(step.specialCell), 15)
                    }

                    fallingCandies = step.fallingCandies
                    matchProgress = 0f
                    matchedVisuals.clear()

                    if (fallingCandies.isNotEmpty()) startFallAnimation() else continueResolution()
                }
            })
            start()
        }
    }

    private fun startFallAnimation() {
        fallProgress = 0f
        fallAnimator?.cancel()
        fallAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 175L + min(95L, fallingCandies.size * 3L)
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                fallProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fallAnimator = null
                    fallingCandies = emptyList()
                    continueResolution()
                }
            })
            start()
        }
    }

    private fun continueResolution() {
        matchedVisuals.clear()
        swapFrom = null
        swapTo = null
        swapProgress = 0f

        if (board.score >= board.targetScore) {
            completeLevel()
            return
        }

        if (board.movesLeft <= 0) {
            failLevel()
            return
        }

        if (board.prepareCascade()) {
            startMatchAnimation()
            return
        }

        if (!board.hasPossibleMove()) {
            startShuffle()
            return
        }

        combo = 0
        savePersistentState()
        invalidate()
    }

    private fun startShuffle() {
        board.shuffle()
        shuffleProgress = 1f
        sound.playSwap(0.65f, 0.32f)
        shuffleAnimator?.cancel()
        shuffleAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 330L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shuffleProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shuffleAnimator = null
                    shuffleProgress = 0f
                    invalidate()
                }
            })
            start()
        }
    }

    private fun activateSpecial(row: Int, col: Int) {
        if (isBusy() || levelComplete || levelFailed) return
        val candy = board.get(row, col) ?: return

        when (candy.type) {
            Candy.BOMB_TYPE -> {
                val result = board.detonateBomb(row, col, 0, 1) ?: return
                emitParticles(result.explosionCells.toSet(), 5)
                specialProgress = 0f
                specialAnimator?.cancel()
                specialAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 220L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        specialProgress = it.animatedValue as Float
                        invalidate()
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            specialAnimator = null
                            fallingCandies = result.fallingCandies
                            if (fallingCandies.isNotEmpty()) startFallAnimation() else continueResolution()
                        }
                    })
                    start()
                }
                sound.playReward()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
            Candy.ROCKET_TYPE -> {
                val target = board.prepareRocketLaunch(row, col) ?: return
                val result = board.finishRocketLaunch(row, col, target.first, target.second) ?: return
                rocketStart = row to col
                rocketTarget = target
                specialProgress = 0f
                specialAnimator?.cancel()
                specialAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 270L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        specialProgress = it.animatedValue as Float
                        invalidate()
                    }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            specialAnimator = null
                            rocketStart = null
                            rocketTarget = null
                            emitParticles(setOf(row to col), 16)
                            fallingCandies = result.fallingCandies
                            if (fallingCandies.isNotEmpty()) startFallAnimation() else continueResolution()
                        }
                    })
                    start()
                }
                sound.playReward()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            }
        }
    }

    private fun activateHelper(item: Helper) {
        val model = viewModel ?: return
        val count = model.helperCounts[item.ordinal]
        if (count <= 0) return
        helper = if (helper == item) null else item
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        invalidate()
    }

    private fun useHelper(row: Int, col: Int) {
        val model = viewModel ?: return
        val item = helper ?: return
        if (model.helperCounts[item.ordinal] <= 0 || isBusy() || levelComplete || levelFailed) return

        when (item) {
            Helper.HAMMER -> {
                if (board.get(row, col) == null) return
                board.removeCell(row, col)
                model.helperCounts[item.ordinal]--
                emitParticles(setOf(row to col), 12)
                floatingTexts += FloatingText(
                    cellCenter(row, col).x,
                    cellCenter(row, col).y - dp(12f),
                    "HAMMER",
                    0.8f
                )
                sound.playReward()
                helper = null
            }
            Helper.CROSS -> {
                board.clearCross(row, col)
                model.helperCounts[item.ordinal]--
                emitParticles(setOf(row to col), 20)
                sound.playReward()
                helper = null
            }
            Helper.MOVES -> {
                board.addMoves(5)
                model.helperCounts[item.ordinal]--
                floatingTexts += FloatingText(width / 2f, boardTop - dp(8f), "+5 MOVES", 0.9f)
                sound.playReward()
                helper = null
            }
        }

        savePersistentState()
        if (board.score >= board.targetScore) completeLevel()
        else if (board.prepareCascade()) startMatchAnimation()
        else if (!board.hasPossibleMove()) startShuffle()
        invalidate()
    }

    private fun completeLevel() {
        if (levelComplete) return
        levelComplete = true
        levelFailed = false
        helper = null

        val model = viewModel ?: return
        val level = model.currentLevel
        val best = model.bestScores[level] ?: 0
        if (board.score > best) model.bestScores[level] = board.score

        model.coins += rewardCoins()
        val next = (level + 1L).coerceAtMost(30L)
        if (next > model.highestUnlockedLevel) model.highestUnlockedLevel = next

        sound.playReward()
        emitBurst(width / 2f, height / 2f, 42)
        savePersistentState()
        invalidate()
    }

    private fun failLevel() {
        if (levelFailed) return
        levelFailed = true
        levelComplete = false
        helper = null
        savePersistentState()
        sound.playSwap(0.60f, 0.25f)
        invalidate()
    }

    private fun rewardCoins(): Int = 12 + min(18, combo * 2)

    private fun emitParticles(cells: Set<Pair<Int, Int>>, perCell: Int) {
        for (cell in cells) {
            val center = cellCenter(cell.first, cell.second)
            repeat(perCell.coerceAtMost(10)) {
                val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
                val speed = cellSize * (0.035f + Random.nextFloat() * 0.075f)
                particles += Particle(
                    center.x,
                    center.y,
                    cos(angle.toDouble()).toFloat() * speed,
                    sin(angle.toDouble()).toFloat() * speed - cellSize * 0.03f,
                    0.55f + Random.nextFloat() * 0.4f,
                    cellSize * (0.022f + Random.nextFloat() * 0.028f),
                    Random.nextInt()
                )
            }
        }
        if (particles.size > 150) particles.subList(0, particles.size - 150).clear()
    }

    private fun emitBurst(x: Float, y: Float, count: Int) {
        repeat(count) {
            val angle = Random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = dp(2f) + Random.nextFloat() * dp(7f)
            particles += Particle(
                x,
                y,
                cos(angle.toDouble()).toFloat() * speed,
                sin(angle.toDouble()).toFloat() * speed,
                0.8f + Random.nextFloat() * 0.5f,
                dp(2f) + Random.nextFloat() * dp(4f),
                it
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount > 1) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downRow = -1
                downCol = -1
                if (screen == Screen.GAME) {
                    val cell = cellFromPoint(event.x, event.y)
                    if (cell != null) {
                        downRow = cell.first
                        downCol = cell.second
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (screen == Screen.LEVEL_MAP) {
                    val delta = event.y - downY
                    if (abs(delta) > dp(4f)) {
                        mapScroll = (mapScroll - delta).coerceIn(0f, maxMapScroll())
                        downY = event.y
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                when (screen) {
                    Screen.MENU -> handleMenuTap(event.x, event.y)
                    Screen.SHOP -> handleShopTap(event.x, event.y)
                    Screen.HELPERS -> handleHelpersTap(event.x, event.y)
                    Screen.LEVEL_MAP -> handleMapTap(event.x, event.y)
                    Screen.GAME -> handleGameUp(event)
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                clearTouch()
                return true
            }
        }
        return true
    }

    private fun handleGameUp(event: MotionEvent) {
        if (levelComplete || levelFailed) {
            val button = RectF(dp(48f), height / 2f + dp(29f), width - dp(48f), height / 2f + dp(86f))
            if (hit(button, event.x, event.y)) {
                if (levelComplete) {
                    startLevel(((viewModel?.currentLevel ?: 1L) + 1L).coerceAtMost(30L))
                } else {
                    startLevel(viewModel?.currentLevel ?: 1L)
                }
            }
            clearTouch()
            invalidate()
            return
        }

        if (menuOpen) {
            handleGameMenuTap(event.x, event.y)
            clearTouch()
            invalidate()
            return
        }

        if (paused) {
            val resume = RectF(dp(48f), height / 2f + dp(25f), width - dp(48f), height / 2f + dp(82f))
            if (hit(resume, event.x, event.y)) paused = false
            clearTouch()
            invalidate()
            return
        }

        if (hit(RectF(width - dp(70f), dp(86f), width - dp(10f), dp(112f)), event.x, event.y)) {
            menuOpen = true
            clearTouch()
            invalidate()
            return
        }

        if (event.y >= bottomBarTop) {
            val slotW = (width - dp(36f)) / 3f
            val index = ((event.x - dp(18f)) / slotW).toInt().coerceIn(0, 2)
            activateHelper(Helper.values()[index])
            clearTouch()
            return
        }

        val cell = cellFromPoint(event.x, event.y) ?: run {
            clearTouch()
            return
        }

        val dx = event.x - downX
        val actualDy = event.y - downY
        val dragDistance = maxOf(abs(dx), abs(actualDy))
        val origin = if (downRow >= 0 && downCol >= 0) downRow to downCol else cell

        if (dragDistance > cellSize * 0.28f) {
            val horizontal = abs(dx) > abs(actualDy)
            val nr = origin.first + if (!horizontal && actualDy > 0f) 1 else if (!horizontal) -1 else 0
            val nc = origin.second + if (horizontal && dx > 0f) 1 else if (horizontal) -1 else 0
            if (nr in 0 until board.rows && nc in 0 until board.cols) {
                beginSwap(origin, nr to nc, if (horizontal) 0 else 1)
            }
            selectedCell = null
            clearTouch()
            return
        }

        if (helper != null) {
            useHelper(cell.first, cell.second)
            clearTouch()
            return
        }

        val selected = selectedCell
        if (selected == null) {
            selectedCell = cell
        } else if (selected == cell) {
            val type = board.get(cell.first, cell.second)?.type ?: -1
            if (type == Candy.BOMB_TYPE || type == Candy.ROCKET_TYPE) {
                activateSpecial(cell.first, cell.second)
            }
            selectedCell = null
        } else if (isAdjacent(selected, cell)) {
            val direction = if (selected.first == cell.first) 0 else 1
            beginSwap(selected, cell, direction)
            selectedCell = null
        } else {
            selectedCell = cell
        }

        clearTouch()
        invalidate()
    }

    private fun handleGameMenuTap(x: Float, y: Float) {
        when {
            hit(RectF(dp(58f), dp(256f), width - dp(58f), dp(309f)), x, y) -> {
                menuOpen = false
                paused = false
            }
            hit(RectF(dp(58f), dp(320f), width - dp(58f), dp(373f)), x, y) -> {
                startLevel(viewModel?.currentLevel ?: 1L)
                screen = Screen.GAME
            }
            hit(RectF(dp(58f), dp(384f), width - dp(58f), dp(437f)), x, y) -> {
                menuOpen = false
                paused = false
                screen = Screen.MENU
                savePersistentState()
            }
        }
    }

    private fun handleMenuTap(x: Float, y: Float) {
        when {
            hit(RectF(dp(42f), dp(316f), width - dp(42f), dp(374f)), x, y) -> {
                startLevel(viewModel?.currentLevel ?: 1L)
                screen = Screen.GAME
            }
            hit(RectF(dp(42f), dp(388f), width - dp(42f), dp(446f)), x, y) -> {
                screen = Screen.LEVEL_MAP
            }
            hit(RectF(dp(42f), dp(460f), width - dp(42f), dp(518f)), x, y) -> {
                screen = Screen.HELPERS
            }
            hit(RectF(dp(42f), dp(532f), width - dp(42f), dp(590f)), x, y) -> {
                screen = Screen.SHOP
            }
        }
        invalidate()
    }

    private fun handleShopTap(x: Float, y: Float) {
        if (y >= height - dp(80f)) {
            screen = Screen.MENU
            invalidate()
            return
        }
        val list = Helper.values()
        for (i in list.indices) {
            val top = dp(126f) + i * dp(116f)
            val buy = RectF(width - dp(130f), top + dp(24f), width - dp(32f), top + dp(70f))
            if (hit(buy, x, y)) {
                val model = viewModel ?: return
                val item = list[i]
                if (model.coins >= item.price) {
                    model.coins -= item.price
                    model.helperCounts[i]++
                    sound.playReward()
                    savePersistentState()
                    invalidate()
                }
                return
            }
        }
    }

    private fun handleHelpersTap(x: Float, y: Float) {
        if (y >= height - dp(80f)) {
            screen = Screen.MENU
            invalidate()
        }
    }

    private fun handleMapTap(x: Float, y: Float) {
        if (y >= height - dp(80f)) {
            screen = Screen.MENU
            invalidate()
            return
        }
        val level = hitLevelAt(x, y) ?: return
        if (level <= (viewModel?.highestUnlockedLevel ?: 1L)) {
            startLevel(level)
            screen = Screen.GAME
            invalidate()
        }
    }

    private fun hitLevelAt(x: Float, y: Float): Long? {
        val rowH = dp(92f)
        val mapTop = dp(112f)
        val localY = y + mapScroll - mapTop
        if (localY < 0f) return null

        val row = (localY / rowH).toInt()
        val colCenters = floatArrayOf(0.18f, 0.50f, 0.82f)
        var result: Long? = null
        var bestDistance = dp(30f)

        for (col in 0..2) {
            val cx = width * colCenters[col]
            val cy = mapTop + rowH * row + dp(42f) - mapScroll
            val distance = kotlin.math.hypot(
                (x - cx).toDouble(),
                (y - cy).toDouble()
            ).toFloat()
            if (distance < bestDistance) {
                val level = (row * 3 + col + 1).toLong()
                if (level in 1L..30L) {
                    result = level
                    bestDistance = distance
                }
            }
        }
        return result
    }

    private fun isAdjacent(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean =
        (abs(a.first - b.first) == 1 && a.second == b.second) ||
        (abs(a.second - b.second) == 1 && a.first == b.first)

    private fun cellFromPoint(x: Float, y: Float): Pair<Int, Int>? {
        if (x < boardLeft || y < boardTop || x >= boardLeft + boardSize || y >= boardTop + boardSize) return null
        val col = ((x - boardLeft) / cellSize).toInt()
        val row = ((y - boardTop) / cellSize).toInt()
        return if (row in 0 until board.rows && col in 0 until board.cols) row to col else null
    }

    private fun cellCenter(row: Int, col: Int): PointF =
        PointF(
            boardLeft + col * cellSize + cellSize / 2f,
            boardTop + row * cellSize + cellSize / 2f
        )

    private fun cellCenterAt(row: Float, col: Int): PointF =
        PointF(
            boardLeft + col * cellSize + cellSize / 2f,
            boardTop + row * cellSize + cellSize / 2f
        )

    private fun clearMoveVisuals() {
        swapFrom = null
        swapTo = null
        matchedVisuals.clear()
        fallingCandies = emptyList()
        swapProgress = 0f
        matchProgress = 0f
        rocketStart = null
        rocketTarget = null
        invalidate()
    }

    private fun clearTouch() {
        downRow = -1
        downCol = -1
    }

    private fun hit(r: RectF, x: Float, y: Float): Boolean = r.contains(x, y)

    private fun maxMapScroll(): Float =
        maxOf(0f, dp(112f) + dp(92f) * 10f - (height - dp(16f)))

    private fun ease(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return 1f - (1f - x) * (1f - x) * (1f - x)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    override fun onDetachedFromWindow() {
        cancelAnimations()
        savePersistentState()
        sound.release()
        super.onDetachedFromWindow()
    }

    private fun cancelAnimations() {
        swapAnimator?.cancel()
        matchAnimator?.cancel()
        fallAnimator?.cancel()
        specialAnimator?.cancel()
        shuffleAnimator?.cancel()
        swapAnimator = null
        matchAnimator = null
        fallAnimator = null
        specialAnimator = null
        shuffleAnimator = null
    }
}

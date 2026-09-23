package com.tomasthrawat.candycrush.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.tomasthrawat.candycrush.model.GameBoard
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val board = GameBoard(8, 8)
    private val sound = GameSoundManager(context.applicationContext)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 25, 28, 43)
    }
    private val boardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(80, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(
            "sans-serif",
            android.graphics.Typeface.BOLD
        )
    }
    private val secondaryTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(
            "sans-serif",
            android.graphics.Typeface.NORMAL
        )
    }
    private val accentTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 211, 79)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(
            "sans-serif",
            android.graphics.Typeface.BOLD
        )
    }
    private val candyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val candyHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(105, 255, 255, 255)
    }
    private val candyEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        color = Color.argb(110, 255, 255, 255)
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.WHITE
    }
    private val sparklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val palette = intArrayOf(
        Color.rgb(255, 78, 89),
        Color.rgb(255, 202, 64),
        Color.rgb(67, 211, 123),
        Color.rgb(75, 150, 255),
        Color.rgb(185, 105, 245),
        Color.rgb(255, 151, 62)
    )

    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardSize = 0f
    private var cellSize = 0f
    private var gap = 0f
    private var headerHeight = 0f

    private var selectedRow = -1
    private var selectedCol = -1

    private var movingFromRow = -1
    private var movingFromCol = -1
    private var movingToRow = -1
    private var movingToCol = -1
    private var movingFromType = 0
    private var movingToType = 0
    private var swapProgress = 0f

    private var swapAnimator: ValueAnimator? = null
    private var successAnimator: ValueAnimator? = null

    private var successPulse = 0f
    private var gameOver = false

    init {
        isClickable = true
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            dp(900f),
            Color.rgb(34, 22, 61),
            Color.rgb(17, 32, 57),
            Shader.TileMode.CLAMP
        )
    }

    fun startGame() {
        cancelAnimations()
        board.reset()
        selectedRow = -1
        selectedCol = -1
        gameOver = false
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        headerHeight = dp(92f)

        val horizontalPadding = dp(10f)
        val bottomPadding = dp(12f)
        val availableWidth = w - (horizontalPadding * 2f)
        val availableHeight = h - headerHeight - bottomPadding - dp(8f)

        boardSize = min(availableWidth, availableHeight).coerceAtLeast(0f)
        boardLeft = (w - boardSize) / 2f
        boardTop = headerHeight + maxOf(
            dp(6f),
            (availableHeight - boardSize) / 2f
        )

        gap = min(dp(7f), boardSize * 0.028f)
        cellSize = if (board.cols > 0) {
            (boardSize - gap * (board.cols - 1)) / board.cols
        } else {
            0f
        }

        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            h.toFloat().coerceAtLeast(1f),
            Color.rgb(34, 22, 61),
            Color.rgb(17, 32, 57),
            Shader.TileMode.CLAMP
        )

        textPaint.textSize = dp(25f)
        secondaryTextPaint.textSize = dp(13f)
        accentTextPaint.textSize = dp(19f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            backgroundPaint
        )

        drawHeader(canvas)
        drawBoardPanel(canvas)

        for (r in 0 until board.rows) {
            for (c in 0 until board.cols) {
                if (isMovingCell(r, c)) continue

                val candy = board.get(r, c) ?: continue
                val center = cellCenter(r, c)
                val selected = r == selectedRow && c == selectedCol
                val pulseScale = 1f + successPulse * 0.025f
                drawCandy(
                    canvas,
                    candy.type,
                    center.first,
                    center.second,
                    pulseScale,
                    selected
                )
            }
        }

        if (swapAnimator?.isRunning == true) {
            val fromCenter = cellCenter(movingFromRow, movingFromCol)
            val toCenter = cellCenter(movingToRow, movingToCol)
            val p = easeInOut(swapProgress)

            val firstX = lerp(fromCenter.first, toCenter.first, p)
            val firstY = lerp(fromCenter.second, toCenter.second, p)
            val secondX = lerp(toCenter.first, fromCenter.first, p)
            val secondY = lerp(toCenter.second, fromCenter.second, p)

            drawCandy(canvas, movingFromType, firstX, firstY, 1.045f, false)
            drawCandy(canvas, movingToType, secondX, secondY, 1.045f, false)
        }

        if (successPulse > 0f) {
            drawSparkles(canvas, successPulse)
        }

        if (gameOver) {
            drawGameOver(canvas)
        }
    }

    private fun drawHeader(canvas: Canvas) {
        canvas.drawText("CANDY RUSH", width / 2f, dp(33f), textPaint)

        val subtitle = if (gameOver) {
            "Tap anywhere to start a new round"
        } else {
            "Tap a candy, then tap an adjacent one"
        }
        canvas.drawText(subtitle, width / 2f, dp(56f), secondaryTextPaint)

        val chipWidth = dp(128f)
        val chipHeight = dp(25f)
        val chipY = dp(66f)
        val radius = chipHeight / 2f

        val scoreRect = RectF(
            width / 2f - chipWidth - dp(6f),
            chipY,
            width / 2f - dp(6f),
            chipY + chipHeight
        )
        val movesRect = RectF(
            width / 2f + dp(6f),
            chipY,
            width / 2f + chipWidth + dp(6f),
            chipY + chipHeight
        )

        cardPaint.color = Color.argb(58, 255, 255, 255)
        canvas.drawRoundRect(scoreRect, radius, radius, cardPaint)
        canvas.drawRoundRect(movesRect, radius, radius, cardPaint)

        canvas.drawText(
            "SCORE  ${board.score}",
            scoreRect.centerX(),
            chipY + dp(18f),
            accentTextPaint
        )
        canvas.drawText(
            "MOVES  ${board.movesLeft}",
            movesRect.centerX(),
            chipY + dp(18f),
            secondaryTextPaint
        )
    }

    private fun drawBoardPanel(canvas: Canvas) {
        val panel = RectF(
            boardLeft - dp(7f),
            boardTop - dp(7f),
            boardLeft + boardSize + dp(7f),
            boardTop + boardSize + dp(7f)
        )
        canvas.drawRoundRect(panel, dp(22f), dp(22f), boardPaint)
        canvas.drawRoundRect(panel, dp(22f), dp(22f), boardBorderPaint)
    }

    private fun drawCandy(
        canvas: Canvas,
        type: Int,
        centerX: Float,
        centerY: Float,
        scale: Float,
        selected: Boolean
    ) {
        if (cellSize <= 0f) return

        val size = cellSize * 0.88f * scale
        val half = size / 2f

        canvas.save()
        canvas.translate(centerX, centerY)

        if (type == 2) {
            canvas.rotate(45f)
        }

        val rect = RectF(-half, -half, half, half)
        candyPaint.color = palette[type % palette.size]
        candyPaint.shader = null

        when (type) {
            1, 4, 5 -> canvas.drawCircle(0f, 0f, half * 0.92f, candyPaint)
            3 -> canvas.drawPath(hexagonPath(half * 0.94f), candyPaint)
            else -> canvas.drawRoundRect(
                rect,
                half * 0.26f,
                half * 0.26f,
                candyPaint
            )
        }

        if (type == 2) {
            canvas.rotate(-45f)
        }

        candyEdgePaint.color = Color.argb(100, 255, 255, 255)
        when (type) {
            1, 4, 5 -> canvas.drawCircle(
                0f,
                0f,
                half * 0.92f,
                candyEdgePaint
            )
            3 -> canvas.drawPath(
                hexagonPath(half * 0.94f),
                candyEdgePaint
            )
            else -> canvas.drawRoundRect(
                rect,
                half * 0.26f,
                half * 0.26f,
                candyEdgePaint
            )
        }

        val highlight = RectF(
            -half * 0.48f,
            -half * 0.48f,
            -half * 0.02f,
            -half * 0.10f
        )
        canvas.drawOval(highlight, candyHighlightPaint)

        val glint = Path().apply {
            moveTo(-half * 0.28f, -half * 0.08f)
            lineTo(half * 0.18f, -half * 0.36f)
        }
        candyEdgePaint.color = Color.argb(150, 255, 255, 255)
        candyEdgePaint.strokeWidth = dp(1.3f)
        canvas.drawPath(glint, candyEdgePaint)

        if (selected) {
            val selectionRect = RectF(
                -half - dp(4f),
                -half - dp(4f),
                half + dp(4f),
                half + dp(4f)
            )
            canvas.drawRoundRect(
                selectionRect,
                dp(10f),
                dp(10f),
                selectionPaint
            )
        }

        canvas.restore()
    }

    private fun drawSparkles(canvas: Canvas, progress: Float) {
        val centerX = width / 2f
        val centerY = boardTop + boardSize / 2f
        val radius = boardSize * (0.18f + (1f - progress) * 0.18f)
        sparklePaint.alpha = (progress * 220f).toInt().coerceIn(0, 220)

        repeat(8) { index ->
            val angle = Math.PI * 2.0 * index / 8.0
            val x = centerX + cos(angle).toFloat() * radius
            val y = centerY + sin(angle).toFloat() * radius
            val arm = dp(5f) * progress
            canvas.drawRect(
                x - arm,
                y - dp(1f),
                x + arm,
                y + dp(1f),
                sparklePaint
            )
            canvas.drawRect(
                x - dp(1f),
                y - arm,
                x + dp(1f),
                y + arm,
                sparklePaint
            )
        }

        sparklePaint.alpha = 255
    }

    private fun drawGameOver(canvas: Canvas) {
        val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 8, 10, 20)
        }
        canvas.drawRect(
            0f,
            headerHeight,
            width.toFloat(),
            height.toFloat(),
            overlay
        )

        val box = RectF(
            width / 2f - dp(135f),
            boardTop + boardSize / 2f - dp(58f),
            width / 2f + dp(135f),
            boardTop + boardSize / 2f + dp(58f)
        )
        cardPaint.color = Color.argb(235, 31, 35, 53)
        canvas.drawRoundRect(box, dp(22f), dp(22f), cardPaint)
        canvas.drawRoundRect(box, dp(22f), dp(22f), boardBorderPaint)

        canvas.drawText(
            "ROUND OVER",
            box.centerX(),
            box.centerY() - dp(12f),
            textPaint
        )
        canvas.drawText(
            "Tap to play again",
            box.centerX(),
            box.centerY() + dp(18f),
            secondaryTextPaint
        )
    }

    private fun beginSwap(r1: Int, c1: Int, r2: Int, c2: Int) {
        if (swapAnimator?.isRunning == true) return

        val first = board.get(r1, c1) ?: return
        val second = board.get(r2, c2) ?: return

        movingFromRow = r1
        movingFromCol = c1
        movingToRow = r2
        movingToCol = c2
        movingFromType = first.type
        movingToType = second.type
        swapProgress = 0f

        selectedRow = -1
        selectedCol = -1
        sound.playSwap()

        swapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 185L
            interpolator = DecelerateInterpolator(1.4f)

            addUpdateListener {
                swapProgress = it.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    val success = board.swap(r1, c1, r2, c2)
                    if (success) {
                        sound.playMatch()
                        startSuccessPulse()
                    } else {
                        startReverseSwap()
                    }
                }
            })

            start()
        }
    }

    private fun startReverseSwap() {
        swapAnimator?.cancel()

        swapAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 125L
            interpolator = DecelerateInterpolator()

            addUpdateListener {
                swapProgress = it.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    swapProgress = 0f
                    clearMovingState()
                    sound.playSwap(0.78f)
                    invalidate()
                }
            })

            start()
        }
    }

    private fun startSuccessPulse() {
        successAnimator?.cancel()
        successAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220L

            addUpdateListener {
                successPulse = it.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    successPulse = 0f
                    clearMovingState()
                    if (board.isGameOver()) {
                        gameOver = true
                    }
                    invalidate()
                }
            })

            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (swapAnimator?.isRunning == true || successAnimator?.isRunning == true) {
            return true
        }

        if (gameOver) {
            startGame()
            return true
        }

        if (cellSize <= 0f) return true

        val dx = event.x - boardLeft
        val dy = event.y - boardTop

        if (dx < 0f || dy < 0f || dx > boardSize || dy > boardSize) {
            return true
        }

        val step = cellSize + gap
        val col = (dx / step).toInt()
        val row = (dy / step).toInt()

        if (row !in 0 until board.rows || col !in 0 until board.cols) {
            return true
        }

        val cellLeft = col * step
        val cellTop = row * step

        if (dx > cellLeft + cellSize || dy > cellTop + cellSize) {
            return true
        }

        if (selectedRow == -1) {
            selectedRow = row
            selectedCol = col
        } else {
            val fromRow = selectedRow
            val fromCol = selectedCol

            if (fromRow == row && fromCol == col) {
                selectedRow = -1
                selectedCol = -1
            } else {
                val adjacent = abs(fromRow - row) + abs(fromCol - col) == 1
                if (adjacent) {
                    beginSwap(fromRow, fromCol, row, col)
                } else {
                    selectedRow = row
                    selectedCol = col
                }
            }
        }

        invalidate()
        return true
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        sound.release()
        super.onDetachedFromWindow()
    }

    private fun cancelAnimations() {
        swapAnimator?.cancel()
        successAnimator?.cancel()
        swapAnimator = null
        successAnimator = null
        successPulse = 0f
        swapProgress = 0f
        clearMovingState()
    }

    private fun clearMovingState() {
        movingFromRow = -1
        movingFromCol = -1
        movingToRow = -1
        movingToCol = -1
    }

    private fun isMovingCell(row: Int, col: Int): Boolean {
        return (row == movingFromRow && col == movingFromCol) ||
            (row == movingToRow && col == movingToCol)
    }

    private fun cellCenter(row: Int, col: Int): Pair<Float, Float> {
        val step = cellSize + gap
        return Pair(
            boardLeft + col * step + cellSize / 2f,
            boardTop + row * step + cellSize / 2f
        )
    }

    private fun hexagonPath(radius: Float): Path {
        val path = Path()

        repeat(6) { i ->
            val angle = Math.toRadians((60 * i - 30).toDouble())
            val x = cos(angle).toFloat() * radius
            val y = sin(angle).toFloat() * radius

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        path.close()
        return path
    }

    private fun easeInOut(value: Float): Float {
        return (value * value * (3f - 2f * value)).coerceIn(0f, 1f)
    }

    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}

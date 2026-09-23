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
import android.view.MotionEvent
import android.view.VelocityTracker
import com.tomasthrawat.candycrush.model.FallingCandy
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.tomasthrawat.candycrush.model.GameBoard
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    private val board = GameBoard(8, 8)
    private val sound = GameSoundManager(context.applicationContext)
    private val preferences = context.applicationContext
        .getSharedPreferences("candy_rush_progress", Context.MODE_PRIVATE)
    private val levelScroller = OverScroller(context)
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minimumFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity

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
    private val levelRoutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(5f)
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(90, 255, 255, 255)
    }
    private val levelNodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val levelNodeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val mapHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 16, 22, 41)
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
    private var levelChipRect = RectF()

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
    private var fallingAnimator: ValueAnimator? = null
    private var fallingCandies: List<FallingCandy> = emptyList()
    private var fallingProgress = 0f
    private var gameOver = false
    private var levelComplete = false

    private var currentLevel = preferences.getLong("current_level", 1L).coerceAtLeast(1L)
    private var highestUnlockedLevel =
        preferences.getLong("highest_unlocked_level", 1L).coerceAtLeast(1L)
    private var levelMapMode = false

    private var levelScrollY = 0
    private var mapDownX = 0f
    private var mapDownY = 0f
    private var mapLastY = 0f
    private var mapDragging = false
    private var mapVelocityTracker: VelocityTracker? = null

    private var gestureStartRow = -1
    private var gestureStartCol = -1
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var levelTransitionRunning = false

    init {
        isClickable = true
        overScrollMode = OVER_SCROLL_NEVER
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            dp(900f),
            Color.rgb(34, 22, 61),
            Color.rgb(17, 32, 57),
            Shader.TileMode.CLAMP
        )
        board.reset(movesForLevel(currentLevel))
    }

    fun startGame() {
        startLevel(currentLevel)
    }

    private fun startLevel(level: Long) {
        cancelAnimations()
        currentLevel = level.coerceAtLeast(1L)
        highestUnlockedLevel = max(highestUnlockedLevel, currentLevel)
        persistProgress()
        board.reset(movesForLevel(currentLevel))
        selectedRow = -1
        selectedCol = -1
        gameOver = false
        levelComplete = false
        levelMapMode = false
        levelScrollY = clampMapScroll(levelScrollY)
        invalidate()
    }

    private fun persistProgress() {
        preferences.edit()
            .putLong("current_level", currentLevel)
            .putLong("highest_unlocked_level", highestUnlockedLevel)
            .apply()
    }

    private fun targetScoreForLevel(level: Long): Int {
        val pattern = ((level - 1L) % 12L).toInt()
        return 180 + pattern * 35
    }

    private fun movesForLevel(level: Long): Int {
        val pattern = ((level - 1L) % 6L).toInt()
        return 20 + pattern
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        headerHeight = dp(124f)

        val horizontalPadding = dp(10f)
        val bottomPadding = dp(12f)
        val availableWidth = w - horizontalPadding * 2f
        val availableHeight = h - headerHeight - bottomPadding - dp(8f)

        boardSize = min(availableWidth, availableHeight).coerceAtLeast(0f)
        boardLeft = (w - boardSize) / 2f
        boardTop = headerHeight + max(
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
        accentTextPaint.textSize = dp(18f)
        levelChipRect = RectF(
            width / 2f - dp(86f),
            dp(59f),
            width / 2f + dp(86f),
            dp(84f)
        )
        levelScrollY = clampMapScroll(levelScrollY)
    }

    override fun computeScroll() {
        super.computeScroll()
        if (levelScroller.computeScrollOffset()) {
            levelScrollY = clampMapScroll(levelScroller.currY)
            invalidate()
        }
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

        if (levelMapMode) {
            drawLevelMap(canvas)
            return
        }

        drawHeader(canvas)
        drawBoardPanel(canvas)

        for (r in 0 until board.rows) {
            for (c in 0 until board.cols) {
                if (isMovingCell(r, c) || isFallingDestination(r, c)) continue

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

        if (fallingAnimator?.isRunning == true || fallingCandies.isNotEmpty()) {
            drawFallingCandies(canvas)
        }

        if (successPulse > 0f) {
            drawSparkles(canvas, successPulse)
        }

        if (gameOver || levelComplete) {
            drawResultOverlay(canvas)
        }
    }

    private fun drawHeader(canvas: Canvas) {
        canvas.drawText("CANDY RUSH", width / 2f, dp(33f), textPaint)

        val subtitle = when {
            levelComplete -> "Swipe up for the next level"
            gameOver -> "Swipe down to retry or up when unlocked"
            else -> "Swipe a candy to swap • tap the level for the map"
        }
        canvas.drawText(subtitle, width / 2f, dp(55f), secondaryTextPaint)

        val chipRadius = dp(13f)
        cardPaint.color = Color.argb(58, 255, 255, 255)
        canvas.drawRoundRect(
            levelChipRect,
            chipRadius,
            chipRadius,
            cardPaint
        )
        canvas.drawText(
            "LEVEL " + currentLevel + "  •  TARGET " + targetScoreForLevel(currentLevel),
            levelChipRect.centerX(),
            dp(77f),
            accentTextPaint
        )

        val chipWidth = dp(128f)
        val chipHeight = dp(25f)
        val chipY = dp(92f)
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
            "SCORE  " + board.score,
            scoreRect.centerX(),
            chipY + dp(18f),
            accentTextPaint
        )
        canvas.drawText(
            "MOVES  " + board.movesLeft,
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

        val rect = RectF(-half, -half, half, half)
        candyPaint.color = palette[type % palette.size]
        candyPaint.shader = null

        when (type) {
            1, 4, 5 -> canvas.drawCircle(0f, 0f, half * 0.92f, candyPaint)
            2 -> canvas.drawPath(diamondPath(half * 0.96f), candyPaint)
            3 -> canvas.drawPath(hexagonPath(half * 0.94f), candyPaint)
            else -> canvas.drawRoundRect(
                rect,
                half * 0.26f,
                half * 0.26f,
                candyPaint
            )
        }

        candyEdgePaint.color = Color.argb(100, 255, 255, 255)
        when (type) {
            1, 4, 5 -> canvas.drawCircle(0f, 0f, half * 0.92f, candyEdgePaint)
            2 -> canvas.drawPath(diamondPath(half * 0.96f), candyEdgePaint)
            3 -> canvas.drawPath(hexagonPath(half * 0.94f), candyEdgePaint)
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
            when (type) {
                2 -> canvas.drawPath(diamondPath(half + dp(4f)), selectionPaint)
                3 -> canvas.drawPath(hexagonPath(half + dp(4f)), selectionPaint)
                1, 4, 5 -> canvas.drawCircle(0f, 0f, half + dp(4f), selectionPaint)
                else -> {
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
            }
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

    private fun drawResultOverlay(canvas: Canvas) {
        val overlay = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(125, 8, 10, 20)
        }
        canvas.drawRect(0f, headerHeight, width.toFloat(), height.toFloat(), overlay)

        val box = RectF(
            width / 2f - dp(145f),
            boardTop + boardSize / 2f - dp(68f),
            width / 2f + dp(145f),
            boardTop + boardSize / 2f + dp(68f)
        )
        cardPaint.color = Color.argb(238, 31, 35, 53)
        canvas.drawRoundRect(box, dp(22f), dp(22f), cardPaint)
        canvas.drawRoundRect(box, dp(22f), dp(22f), boardBorderPaint)

        val title = if (levelComplete) {
            "LEVEL " + currentLevel + " COMPLETE"
        } else {
            "ROUND OVER"
        }
        val action = if (levelComplete) {
            "Tap to play level " + (currentLevel + 1L)
        } else {
            "Tap to retry level " + currentLevel
        }

        canvas.drawText(title, box.centerX(), box.centerY() - dp(14f), textPaint)
        canvas.drawText(action, box.centerX(), box.centerY() + dp(18f), secondaryTextPaint)
    }

    private fun drawLevelMap(canvas: Canvas) {
        val maxVisibleLevel = highestUnlockedLevel + 6L
        val firstVisible = firstMapLevel(maxVisibleLevel)
        val lastVisible = lastMapLevel(maxVisibleLevel)

        canvas.save()
        canvas.clipRect(0f, mapHeaderHeight(), width.toFloat(), height.toFloat())
        canvas.translate(0f, -levelScrollY.toFloat())

        for (level in firstVisible..lastVisible) {
            if (level < lastVisible) {
                val start = levelNodeCenter(level)
                val end = levelNodeCenter(level + 1L)
                levelRoutePaint.color = if (level < highestUnlockedLevel) {
                    Color.argb(150, 255, 211, 79)
                } else {
                    Color.argb(85, 255, 255, 255)
                }
                canvas.drawLine(start.first, start.second, end.first, end.second, levelRoutePaint)
            }
        }

        for (level in firstVisible..lastVisible) {
            val center = levelNodeCenter(level)
            val unlocked = level <= highestUnlockedLevel
            val current = level == currentLevel
            val radius = if (current) dp(27f) else dp(23f)

            levelNodePaint.color = when {
                current -> Color.rgb(255, 202, 64)
                unlocked -> Color.rgb(74, 91, 150)
                else -> Color.rgb(38, 44, 66)
            }
            canvas.drawCircle(center.first, center.second, radius, levelNodePaint)

            levelNodeBorderPaint.color = when {
                current -> Color.WHITE
                unlocked -> Color.argb(190, 255, 255, 255)
                else -> Color.argb(75, 255, 255, 255)
            }
            canvas.drawCircle(center.first, center.second, radius, levelNodeBorderPaint)

            textPaint.textSize = if (level >= 1000L) dp(11f) else dp(14f)
            textPaint.color = if (unlocked || current) Color.WHITE else Color.argb(110, 255, 255, 255)
            canvas.drawText(level.toString(), center.first, center.second + dp(5f), textPaint)

            if (current) {
                secondaryTextPaint.textSize = dp(11f)
                canvas.drawText(
                    "CURRENT",
                    center.first,
                    center.second + radius + dp(16f),
                    secondaryTextPaint
                )
            }
        }

        canvas.restore()
        drawMapHeader(canvas, maxVisibleLevel)
    }

    private fun drawMapHeader(canvas: Canvas, maxVisibleLevel: Long) {
        val headerBottom = mapHeaderHeight()
        canvas.drawRect(0f, 0f, width.toFloat(), headerBottom, mapHeaderPaint)

        val backRect = RectF(dp(10f), dp(12f), dp(92f), dp(42f))
        cardPaint.color = Color.argb(60, 255, 255, 255)
        canvas.drawRoundRect(backRect, dp(15f), dp(15f), cardPaint)
        secondaryTextPaint.textSize = dp(13f)
        canvas.drawText("BACK", backRect.centerX(), dp(32f), secondaryTextPaint)

        textPaint.textSize = dp(23f)
        textPaint.color = Color.WHITE
        canvas.drawText("LEVEL MAP", width / 2f, dp(34f), textPaint)

        secondaryTextPaint.textSize = dp(12f)
        canvas.drawText(
            "Swipe up/down • Tap an unlocked level",
            width / 2f,
            dp(55f),
            secondaryTextPaint
        )

        accentTextPaint.textSize = dp(14f)
        canvas.drawText(
            "UNLOCKED " + highestUnlockedLevel + "   •   VIEWING TO " + maxVisibleLevel,
            width / 2f,
            dp(77f),
            accentTextPaint
        )
    }

    private fun mapHeaderHeight(): Float = dp(92f)

    private fun mapRowHeight(): Float = dp(105f)

    private fun levelNodeCenter(level: Long): Pair<Float, Float> {
        val zeroBased = level - 1L
        val row = zeroBased / 3L
        val rawColumn = (zeroBased % 3L).toInt()
        val column = if (row % 2L == 0L) rawColumn else 2 - rawColumn

        val x = width * when (column) {
            0 -> 0.22f
            1 -> 0.50f
            else -> 0.78f
        }
        val y = mapHeaderHeight() + dp(48f) + row * mapRowHeight()
        return Pair(x, y)
    }

    private fun firstMapLevel(maxLevel: Long): Long {
        val row = floor(
            (levelScrollY.toFloat() - mapHeaderHeight() - mapRowHeight() * 1.5f) / mapRowHeight()
        ).toLong()
        return max(1L, row * 3L + 1L).coerceAtMost(maxLevel)
    }

    private fun lastMapLevel(maxLevel: Long): Long {
        val bottom = levelScrollY + height
        val row = ceil(
            (bottom - mapHeaderHeight() + mapRowHeight()) / mapRowHeight()
        ).toLong()
        return max(1L, min(maxLevel, row.coerceAtLeast(0L) * 3L + 3L))
    }

    private fun mapContentHeight(maxLevel: Long): Int {
        val lastY = levelNodeCenter(maxLevel).second
        return max(height, (lastY + dp(100f)).toInt())
    }

    private fun maxMapScroll(): Int {
        val maxLevel = highestUnlockedLevel + 6L
        return max(0, mapContentHeight(maxLevel) - height)
    }

    private fun clampMapScroll(value: Int): Int = value.coerceIn(0, maxMapScroll())

    private fun openLevelMap() {
        cancelAnimations()
        levelMapMode = true

        val desired = (levelNodeCenter(currentLevel).second - height * 0.48f).toInt()
        val target = clampMapScroll(desired)

        levelScroller.forceFinished(true)
        levelScrollY = target
        invalidate()
    }

    private fun closeLevelMap() {
        levelScroller.forceFinished(true)
        levelMapMode = false
        levelScrollY = clampMapScroll(levelScrollY)
        invalidate()
    }

    private fun scrollMapBy(deltaY: Float) {
        val next = clampMapScroll(levelScrollY + deltaY.toInt())
        if (next != levelScrollY) {
            levelScrollY = next
            invalidate()
        }
    }

    private fun hitLevelAt(x: Float, y: Float): Long? {
        val maxLevel = highestUnlockedLevel + 6L
        val first = firstMapLevel(maxLevel)
        val last = lastMapLevel(maxLevel)
        val contentY = y + levelScrollY

        var nearestLevel: Long? = null
        var nearestDistance = Float.MAX_VALUE

        for (level in first..last) {
            val center = levelNodeCenter(level)
            val dx = center.first - x
            val dy = center.second - contentY
            val distance = sqrt(dx * dx + dy * dy)
            if (distance <= dp(31f) && distance < nearestDistance) {
                nearestLevel = level
                nearestDistance = distance
            }
        }

        return nearestLevel
    }

    private fun onMapTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                levelScroller.forceFinished(true)
                mapVelocityTracker?.recycle()
                mapVelocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
                mapDownX = event.x
                mapDownY = event.y
                mapLastY = event.y
                mapDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                mapVelocityTracker?.addMovement(event)
                val dy = event.y - mapLastY

                if (!mapDragging) {
                    val moved = abs(event.x - mapDownX) > touchSlop ||
                        abs(event.y - mapDownY) > touchSlop
                    if (moved) mapDragging = true
                }

                if (mapDragging) {
                    scrollMapBy(-dy)
                    mapLastY = event.y
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                mapVelocityTracker?.addMovement(event)
                mapVelocityTracker?.computeCurrentVelocity(1000)
                val velocityY = mapVelocityTracker?.yVelocity ?: 0f

                if (!mapDragging) {
                    val backRect = RectF(dp(10f), dp(12f), dp(92f), dp(42f))
                    if (backRect.contains(event.x, event.y)) {
                        closeLevelMap()
                    } else if (event.y >= mapHeaderHeight()) {
                        val tappedLevel = hitLevelAt(event.x, event.y)
                        if (tappedLevel != null && tappedLevel <= highestUnlockedLevel) {
                            startLevel(tappedLevel)
                        }
                    }
                } else if (abs(velocityY) >= minimumFlingVelocity) {
                    levelScroller.fling(
                        0,
                        levelScrollY,
                        0,
                        -velocityY.toInt(),
                        0,
                        0,
                        0,
                        maxMapScroll()
                    )
                    invalidate()
                }

                mapVelocityTracker?.recycle()
                mapVelocityTracker = null
                mapDragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mapVelocityTracker?.recycle()
                mapVelocityTracker = null
                mapDragging = false
                return true
            }

            else -> return true
        }
    }

    private fun cellFromPoint(x: Float, y: Float): Pair<Int, Int>? {
        if (cellSize <= 0f) return null

        val boardX = x - boardLeft
        val boardY = y - boardTop
        if (boardX < 0f || boardY < 0f || boardX > boardSize || boardY > boardSize) {
            return null
        }

        val step = cellSize + gap
        val col = (boardX / step).toInt()
        val row = (boardY / step).toInt()
        if (row !in 0 until board.rows || col !in 0 until board.cols) return null

        val cellLeft = col * step
        val cellTop = row * step
        if (boardX > cellLeft + cellSize || boardY > cellTop + cellSize) return null

        return row to col
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
                        startResolutionAnimation()
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

    private fun startResolutionAnimation() {
        if (fallingAnimator?.isRunning == true) return

        val step = board.resolveNextStep()
        if (step == null) {
            fallingCandies = emptyList()
            fallingProgress = 0f
            startSuccessPulse()
            return
        }

        sound.playMatch()
        fallingCandies = step.fallingCandies
        fallingProgress = 0f

        if (fallingCandies.isEmpty()) {
            post { continueResolutionAnimation() }
            return
        }

        fallingAnimator?.cancel()
        fallingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 560L
            interpolator = DecelerateInterpolator(1.7f)

            addUpdateListener {
                fallingProgress = it.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    fallingProgress = 1f
                    fallingAnimator = null
                    fallingCandies = emptyList()
                    invalidate()
                    continueResolutionAnimation()
                }

                override fun onAnimationCancel(animation: Animator) {
                    fallingAnimator = null
                    fallingCandies = emptyList()
                    fallingProgress = 0f
                }
            })
            start()
        }
    }

    private fun continueResolutionAnimation() {
        if (!isAttachedToWindow) return
        if (board.prepareCascade()) {
            startResolutionAnimation()
        } else {
            startSuccessPulse()
        }
    }

    private fun drawFallingCandies(canvas: Canvas) {
        val progress = easeOut(fallingProgress)

        for (falling in fallingCandies) {
            val start = cellCenterAt(falling.startRow, falling.column)
            val end = cellCenterAt(falling.endRow, falling.column)
            val x = lerp(start.first, end.first, progress)
            val y = lerp(start.second, end.second, progress)
            drawCandy(canvas, falling.type, x, y, 1f, false)
        }
    }

    private fun isFallingDestination(row: Int, col: Int): Boolean {
        if (fallingCandies.isEmpty()) return false
        return fallingCandies.any { it.column == col && it.endRow.toInt() == row }
    }

    private fun cellCenterAt(row: Float, col: Int): Pair<Float, Float> {
        val step = cellSize + gap
        return Pair(
            boardLeft + col * step + cellSize / 2f,
            boardTop + row * step + cellSize / 2f
        )
    }

    private fun easeOut(value: Float): Float {
        val t = (1f - value).coerceIn(0f, 1f)
        return 1f - t * t * t
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

                    if (board.score >= targetScoreForLevel(currentLevel)) {
                        levelComplete = true
                        highestUnlockedLevel = max(highestUnlockedLevel, currentLevel + 1L)
                        persistProgress()
                    } else if (board.isGameOver()) {
                        gameOver = true
                    }

                    invalidate()
                }
            })
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (levelMapMode) return onMapTouch(event)
        if (levelTransitionRunning) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                gestureStartX = event.x
                gestureStartY = event.y

                val startCell = cellFromPoint(event.x, event.y)
                gestureStartRow = startCell?.first ?: -1
                gestureStartCol = startCell?.second ?: -1
                return true
            }

            MotionEvent.ACTION_MOVE -> return true

            MotionEvent.ACTION_UP -> {
                val dx = event.x - gestureStartX
                val dy = event.y - gestureStartY
                val startRow = gestureStartRow
                val startCol = gestureStartCol

                parent?.requestDisallowInterceptTouchEvent(false)
                gestureStartRow = -1
                gestureStartCol = -1

                if (swapAnimator?.isRunning == true ||
                    successAnimator?.isRunning == true ||
                    fallingAnimator?.isRunning == true
                ) {
                    return true
                }

                if (startRow in 0 until board.rows && startCol in 0 until board.cols) {
                    val threshold = max(dp(18f), min(cellSize * 0.30f, dp(32f)))
                    val horizontal = abs(dx) >= abs(dy)

                    if (max(abs(dx), abs(dy)) >= threshold) {
                        val targetRow = when {
                            !horizontal && dy < 0f -> startRow - 1
                            !horizontal -> startRow + 1
                            else -> startRow
                        }
                        val targetCol = when {
                            horizontal && dx > 0f -> startCol + 1
                            horizontal -> startCol - 1
                            else -> startCol
                        }

                        if (targetRow in 0 until board.rows &&
                            targetCol in 0 until board.cols
                        ) {
                            beginSwap(startRow, startCol, targetRow, targetCol)
                            invalidate()
                        }
                        return true
                    }
                }

                if (levelChipRect.contains(event.x, event.y)) {
                    openLevelMap()
                    return true
                }

                if (gameOver) {
                    startLevel(currentLevel)
                    return true
                }

                if (levelComplete) {
                    navigateLevelBySwipe(1)
                    return true
                }

                val cell = cellFromPoint(event.x, event.y) ?: return true
                val row = cell.first
                val col = cell.second

                if (selectedRow == -1) {
                    selectedRow = row
                    selectedCol = col
                } else {
                    val fromRow = selectedRow
                    val fromCol = selectedCol

                    if (fromRow == row && fromCol == col) {
                        selectedRow = -1
                        selectedCol = -1
                    } else if (abs(fromRow - row) + abs(fromCol - col) == 1) {
                        beginSwap(fromRow, fromCol, row, col)
                    } else {
                        selectedRow = row
                        selectedCol = col
                    }
                }

                invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                gestureStartRow = -1
                gestureStartCol = -1
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            else -> return true
        }
    }

    private fun navigateLevelBySwipe(direction: Int) {
        if (levelTransitionRunning || direction == 0) return

        if (swapAnimator?.isRunning == true || successAnimator?.isRunning == true || fallingAnimator?.isRunning == true) {
            return
        }

        val target = currentLevel + direction.toLong()
        if (target < 1L) {
            if (gameOver && direction < 0) startLevel(currentLevel)
            return
        }

        highestUnlockedLevel = max(highestUnlockedLevel, target)
        persistProgress()
        animateLevelPage(direction, target)
    }

    private fun animateLevelPage(direction: Int, targetLevel: Long) {
        levelTransitionRunning = true
        cancelAnimations()
        selectedRow = -1
        selectedCol = -1

        val exitDistance = -direction * height.toFloat()

        animate()
            .translationY(exitDistance)
            .setDuration(180L)
            .setInterpolator(DecelerateInterpolator(1.35f))
            .withEndAction {
                startLevel(targetLevel)
                translationY = -exitDistance
                animate()
                    .translationY(0f)
                    .setDuration(220L)
                    .setInterpolator(DecelerateInterpolator(1.25f))
                    .withEndAction {
                        levelTransitionRunning = false
                        invalidate()
                    }
                    .start()
            }
            .start()
    }

    override fun onDetachedFromWindow() {
        cancelAnimations()
        animate().cancel()
        translationY = 0f
        levelTransitionRunning = false
        levelScroller.forceFinished(true)
        mapVelocityTracker?.recycle()
        mapVelocityTracker = null
        sound.release()
        super.onDetachedFromWindow()
    }

    private fun cancelAnimations() {
        swapAnimator?.cancel()
        successAnimator?.cancel()
        fallingAnimator?.cancel()
        swapAnimator = null
        successAnimator = null
        fallingAnimator = null
        fallingCandies = emptyList()
        fallingProgress = 0f
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

    private fun diamondPath(radius: Float): Path {
        return Path().apply {
            moveTo(0f, -radius)
            lineTo(radius, 0f)
            lineTo(0f, radius)
            lineTo(-radius, 0f)
            close()
        }
    }

    private fun hexagonPath(radius: Float): Path {
        val path = Path()
        repeat(6) { i ->
            val angle = Math.toRadians((60 * i - 30).toDouble())
            val x = cos(angle).toFloat() * radius
            val y = sin(angle).toFloat() * radius
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun easeInOut(value: Float): Float =
        (value * value * (3f - 2f * value)).coerceIn(0f, 1f)

    private fun lerp(a: Float, b: Float, t: Float): Float =
        a + (b - a) * t

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density
}

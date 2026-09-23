package com.tomasthrawat.candycrush.view

import com.tomasthrawat.candycrush.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.MotionEvent
import android.view.VelocityTracker
import com.tomasthrawat.candycrush.model.Candy
import com.tomasthrawat.candycrush.model.FallingCandy
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.OverScroller
import com.tomasthrawat.candycrush.model.GameBoard
import kotlin.math.abs
import kotlin.math.atan2
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

    private enum class Screen { MENU, GAME, SHOP, HELPERS }
    private enum class ActiveHelper { NONE, HAMMER, CROSS }

    private val board = GameBoard(8, 8)
    private val sound = GameSoundManager(context.applicationContext)
    private val preferences = context.applicationContext
        .getSharedPreferences("candy_rush_progress", Context.MODE_PRIVATE)
    private var screen = Screen.MENU
    private var activeHelper = ActiveHelper.NONE
    private var coins = preferences.getInt("coins", 250).coerceAtLeast(0)
    private var helperCounts = intArrayOf(
        preferences.getInt("helper_hammer", 3).coerceAtLeast(0),
        preferences.getInt("helper_cross", 2).coerceAtLeast(0),
        preferences.getInt("helper_moves", 2).coerceAtLeast(0)
    )
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
    private val candyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        isDither = true
    }
    private val referenceCandyBitmaps = arrayOf(
        BitmapFactory.decodeResource(resources, R.drawable.candy_ref_0),
        BitmapFactory.decodeResource(resources, R.drawable.candy_ref_1),
        BitmapFactory.decodeResource(resources, R.drawable.candy_ref_2),
        BitmapFactory.decodeResource(resources, R.drawable.candy_ref_3),
        BitmapFactory.decodeResource(resources, R.drawable.candy_ref_4),
        BitmapFactory.decodeResource(resources, R.drawable.candy_ref_5)
    )

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
    private val menuGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(50, 255, 255, 255)
    }

    // Palette follows the dominant colors present in the reference artwork.
    private val palette = intArrayOf(
        Color.rgb(195, 51, 134),
        Color.rgb(76, 166, 163),
        Color.rgb(71, 128, 168),
        Color.rgb(242, 163, 111),
        Color.rgb(85, 219, 75),
        Color.rgb(248, 229, 102)
    )

    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardSize = 0f
    private var cellSize = 0f
    private var gap = 0f
    private var headerHeight = 0f
    private var levelChipRect = RectF()
    private var helperHammerRect = RectF()
    private var helperCrossRect = RectF()
    private var helperMoveRect = RectF()
    private var menuPlayRect = RectF()
    private var menuShopRect = RectF()
    private var menuHelpersRect = RectF()
    private var screenBackRect = RectF()
    private var shopHammerRect = RectF()
    private var shopCrossRect = RectF()
    private var shopMovesRect = RectF()
    private var rocketStartCell: Pair<Int, Int>? = null
    private var rocketTargetCell: Pair<Int, Int>? = null
    private var rocketProgress = 0f
    private var rocketAnimator: ValueAnimator? = null

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
    private var bombAnimator: ValueAnimator? = null
    private var bombCell: Pair<Int, Int>? = null
    private var bombProgress = 0f
    private var bombDirectionRow = 0
    private var bombDirectionCol = 0
    private var explosionCells: List<Pair<Int, Int>> = emptyList()
    private var explosionProgress = 0f
    private var earthquakeAnimator: ValueAnimator? = null
    private var earthquakeProgress = 0f
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
        screen = Screen.MENU
        levelMapMode = false
        cancelAnimations()
        invalidate()
    }

    private fun openGame() {
        screen = Screen.GAME
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
        activeHelper = ActiveHelper.NONE
        levelScrollY = clampMapScroll(levelScrollY)
        invalidate()
    }

    private fun persistInventory() {
        preferences.edit()
            .putInt("coins", coins)
            .putInt("helper_hammer", helperCounts[0])
            .putInt("helper_cross", helperCounts[1])
            .putInt("helper_moves", helperCounts[2])
            .apply()
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

        headerHeight = dp(170f)

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

        val helperGap = dp(6f)
        val helperWidth = (width - dp(24f) - helperGap * 2f) / 3f
        val helperTop = dp(123f)
        val helperBottom = dp(160f)
        helperHammerRect = RectF(dp(12f), helperTop, dp(12f) + helperWidth, helperBottom)
        helperCrossRect = RectF(
            helperHammerRect.right + helperGap,
            helperTop,
            helperHammerRect.right + helperGap + helperWidth,
            helperBottom
        )
        helperMoveRect = RectF(
            helperCrossRect.right + helperGap,
            helperTop,
            helperCrossRect.right + helperGap + helperWidth,
            helperBottom
        )

        menuPlayRect = RectF(width * 0.14f, height * 0.34f, width * 0.86f, height * 0.44f)
        menuShopRect = RectF(width * 0.14f, height * 0.47f, width * 0.86f, height * 0.57f)
        menuHelpersRect = RectF(width * 0.14f, height * 0.60f, width * 0.86f, height * 0.70f)
        screenBackRect = RectF(dp(12f), dp(14f), dp(104f), dp(52f))
        shopHammerRect = RectF(width * 0.10f, height * 0.24f, width * 0.90f, height * 0.34f)
        shopCrossRect = RectF(width * 0.10f, height * 0.37f, width * 0.90f, height * 0.47f)
        shopMovesRect = RectF(width * 0.10f, height * 0.50f, width * 0.90f, height * 0.60f)

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

        when (screen) {
            Screen.MENU -> { drawMainMenu(canvas); return }
            Screen.SHOP -> { drawShop(canvas); return }
            Screen.HELPERS -> { drawHelpers(canvas); return }
            Screen.GAME -> Unit
        }

        if (levelMapMode) {
            drawLevelMap(canvas)
            return
        }

        canvas.save()
        if (earthquakeProgress > 0f) {
            val damping = 1f - earthquakeProgress
            val phase = earthquakeProgress * Math.PI * 18.0
            val shakeX = sin(phase).toFloat() * dp(7f) * damping
            val shakeY = cos(phase * 1.17).toFloat() * dp(4f) * damping
            canvas.translate(shakeX, shakeY)
        }

        drawHeader(canvas)
        drawHelperBar(canvas)
        drawBoardPanel(canvas)

        for (r in 0 until board.rows) {
            for (c in 0 until board.cols) {
                if (isMovingCell(r, c) ||
                    isFallingDestination(r, c) ||
                    (rocketStartCell?.first == r && rocketStartCell?.second == c)
                ) continue

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

        if (rocketAnimator?.isRunning == true) {
            drawRocketFlight(canvas)
        }

        if (bombAnimator?.isRunning == true) {
            drawBombCharge(canvas)
        }

        if (explosionProgress > 0f && explosionCells.isNotEmpty()) {
            drawBombExplosion(canvas)
        }

        if (successPulse > 0f) {
            drawSparkles(canvas, successPulse)
        }

        if (gameOver || levelComplete) {
            drawResultOverlay(canvas)
        }

        canvas.restore()
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
        candyPaint.shader = null

        when (type) {
            Candy.BOMB_TYPE -> drawBombCandy(canvas, half)
            Candy.ROCKET_TYPE -> drawRocketCandy(canvas, half)
            else -> drawReferenceCandy(canvas, type, half)
        }

        if (selected) {
            canvas.drawCircle(0f, 0f, half + dp(4f), selectionPaint)
        }

        canvas.restore()
    }

    private fun drawReferenceCandy(canvas: Canvas, type: Int, half: Float) {
        val bitmap = referenceCandyBitmaps.getOrNull(type) ?: return
        if (bitmap.isRecycled) return

        val scale = 1.08f
        val side = half * 2f * scale
        val maxDimension = max(bitmap.width, bitmap.height).toFloat().coerceAtLeast(1f)
        val bitmapWidth = side * (bitmap.width / maxDimension)
        val bitmapHeight = side * (bitmap.height / maxDimension)
        val dst = RectF(
            -bitmapWidth / 2f,
            -bitmapHeight / 2f,
            bitmapWidth / 2f,
            bitmapHeight / 2f
        )
        candyPaint.alpha = 255
        canvas.drawBitmap(bitmap, null, dst, candyPaint)
    }

    private fun drawWrappedCandy(canvas: Canvas, half: Float, color: Int) {
        drawSoftShadow(canvas, half, 0.70f, 0.10f)
        val side = Path().apply {
            moveTo(-half * 0.58f, -half * 0.34f)
            lineTo(-half * 0.88f, -half * 0.56f)
            lineTo(-half * 0.82f, half * 0.56f)
            lineTo(-half * 0.58f, half * 0.34f)
            close()
        }
        val sideRight = Path().apply {
            moveTo(half * 0.58f, -half * 0.34f)
            lineTo(half * 0.88f, -half * 0.56f)
            lineTo(half * 0.82f, half * 0.56f)
            lineTo(half * 0.58f, half * 0.34f)
            close()
        }
        candyPaint.shader = LinearGradient(
            0f, -half, 0f, half,
            lightenColor(color, 0.10f),
            darkenColor(color, 0.32f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(side, candyPaint)
        canvas.drawPath(sideRight, candyPaint)

        val body = RectF(-half * 0.62f, -half * 0.54f, half * 0.62f, half * 0.54f)
        candyPaint.shader = RadialGradient(
            -half * 0.28f,
            -half * 0.40f,
            half * 1.26f,
            intArrayOf(
                Color.WHITE,
                lightenColor(color, 0.34f),
                color,
                darkenColor(color, 0.32f)
            ),
            floatArrayOf(0f, 0.13f, 0.54f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(body, half * 0.34f, half * 0.34f, candyPaint)
        candyPaint.shader = null

        candyEdgePaint.color = Color.argb(155, 255, 255, 255)
        candyEdgePaint.strokeWidth = dp(1.2f)
        canvas.drawRoundRect(body, half * 0.34f, half * 0.34f, candyEdgePaint)
    }

    private fun drawGlossyRoundCandy(canvas: Canvas, half: Float, color: Int) {
        drawSoftShadow(canvas, half, 0.88f, 0.08f)
        candyPaint.shader = RadialGradient(
            -half * 0.34f,
            -half * 0.40f,
            half * 1.34f,
            intArrayOf(
                Color.WHITE,
                lightenColor(color, 0.38f),
                color,
                darkenColor(color, 0.44f)
            ),
            floatArrayOf(0f, 0.12f, 0.50f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, half * 0.92f, candyPaint)
        candyPaint.shader = null
        candyEdgePaint.color = Color.argb(150, 255, 255, 255)
        candyEdgePaint.strokeWidth = dp(1.4f)
        canvas.drawCircle(0f, 0f, half * 0.92f, candyEdgePaint)
    }

    private fun drawGemCandy(canvas: Canvas, half: Float, color: Int) {
        drawSoftShadow(canvas, half, 0.86f, 0.09f)
        val outer = diamondPath(half * 0.98f)
        candyPaint.shader = LinearGradient(
            -half, -half, half, half,
            lightenColor(color, 0.36f),
            color,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(outer, candyPaint)
        candyPaint.shader = null

        val bottomFacet = Path().apply {
            moveTo(-half * 0.98f, 0f)
            lineTo(0f, half * 0.98f)
            lineTo(half * 0.98f, 0f)
            lineTo(0f, half * 0.54f)
            close()
        }
        candyPaint.shader = LinearGradient(
            0f, 0f, 0f, half,
            color,
            darkenColor(color, 0.36f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(bottomFacet, candyPaint)
        candyPaint.shader = null

        val facet = Path().apply {
            moveTo(-half * 0.62f, -half * 0.06f)
            lineTo(0f, -half * 0.66f)
            lineTo(half * 0.52f, -half * 0.12f)
            lineTo(0f, half * 0.02f)
            close()
        }
        candyPaint.color = Color.argb(92, 255, 255, 255)
        canvas.drawPath(facet, candyPaint)

        candyEdgePaint.color = Color.argb(165, 255, 255, 255)
        candyEdgePaint.strokeWidth = dp(1.3f)
        canvas.drawPath(outer, candyEdgePaint)
    }

    private fun drawHexCandy(canvas: Canvas, half: Float, color: Int) {
        drawSoftShadow(canvas, half, 0.84f, 0.08f)
        val outer = hexagonPath(half * 0.95f)
        candyPaint.shader = RadialGradient(
            -half * 0.30f,
            -half * 0.36f,
            half * 1.18f,
            intArrayOf(
                Color.WHITE,
                lightenColor(color, 0.30f),
                color,
                darkenColor(color, 0.42f)
            ),
            floatArrayOf(0f, 0.13f, 0.54f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(outer, candyPaint)
        candyPaint.shader = null

        candyPaint.color = Color.argb(80, 255, 255, 255)
        canvas.drawPath(hexagonPath(half * 0.56f), candyPaint)

        candyEdgePaint.color = Color.argb(155, 255, 255, 255)
        candyEdgePaint.strokeWidth = dp(1.3f)
        canvas.drawPath(outer, candyEdgePaint)
    }

    private fun drawSwirlCandy(canvas: Canvas, half: Float, color: Int) {
        drawSoftShadow(canvas, half, 0.88f, 0.08f)
        val body = RectF(-half * 0.90f, -half * 0.66f, half * 0.90f, half * 0.66f)
        candyPaint.shader = RadialGradient(
            -half * 0.34f,
            -half * 0.38f,
            half * 1.25f,
            intArrayOf(
                Color.WHITE,
                lightenColor(color, 0.30f),
                color,
                darkenColor(color, 0.40f)
            ),
            floatArrayOf(0f, 0.12f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(body, candyPaint)
        candyPaint.shader = null

        candyPaint.color = Color.argb(58, 255, 255, 255)
        canvas.drawOval(
            RectF(-half * 0.60f, -half * 0.40f, half * 0.15f, half * 0.02f),
            candyPaint
        )

        candyEdgePaint.color = Color.argb(175, 255, 255, 255)
        candyEdgePaint.strokeWidth = dp(2.1f)
        val swirl = Path().apply {
            moveTo(-half * 0.58f, half * 0.06f)
            cubicTo(
                -half * 0.20f, -half * 0.52f,
                half * 0.10f, half * 0.52f,
                half * 0.58f, -half * 0.08f
            )
        }
        canvas.drawPath(swirl, candyEdgePaint)
        candyEdgePaint.strokeWidth = dp(1.2f)
    }

    private fun drawPillCandy(canvas: Canvas, half: Float, color: Int) {
        drawSoftShadow(canvas, half, 0.80f, 0.08f)
        val body = RectF(-half * 0.64f, -half * 0.76f, half * 0.64f, half * 0.76f)
        candyPaint.shader = LinearGradient(
            -half, -half, half * 0.85f, half,
            lightenColor(color, 0.36f),
            darkenColor(color, 0.40f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(body, half * 0.30f, half * 0.30f, candyPaint)
        candyPaint.shader = null

        candyPaint.color = Color.argb(92, 255, 255, 255)
        canvas.drawRoundRect(
            RectF(-half * 0.48f, -half * 0.54f, half * 0.34f, -half * 0.10f),
            half * 0.22f,
            half * 0.22f,
            candyPaint
        )
        candyEdgePaint.color = Color.argb(150, 255, 255, 255)
        candyEdgePaint.strokeWidth = dp(1.4f)
        canvas.drawRoundRect(body, half * 0.30f, half * 0.30f, candyEdgePaint)
    }

    private fun drawSoftShadow(canvas: Canvas, half: Float, widthScale: Float, offsetScale: Float) {
        candyPaint.shader = null
        candyPaint.color = Color.argb(48, 18, 22, 34)
        canvas.save()
        canvas.translate(half * offsetScale, half * offsetScale)
        canvas.drawCircle(0f, 0f, half * widthScale, candyPaint)
        canvas.restore()
    }

    private fun drawCandyHighlight(canvas: Canvas, half: Float) {
        candyHighlightPaint.alpha = 178
        canvas.drawOval(
            RectF(
                -half * 0.52f,
                -half * 0.52f,
                -half * 0.03f,
                -half * 0.12f
            ),
            candyHighlightPaint
        )

        candyHighlightPaint.alpha = 78
        canvas.drawCircle(
            half * 0.28f,
            half * 0.34f,
            half * 0.12f,
            candyHighlightPaint
        )
        candyHighlightPaint.alpha = 105
    }

    private fun lightenColor(color: Int, amount: Float): Int {
        val factor = amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) + (255 - Color.red(color)) * factor).toInt(),
            (Color.green(color) + (255 - Color.green(color)) * factor).toInt(),
            (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt()
        )
    }

    private fun darkenColor(color: Int, amount: Float): Int {
        val factor = 1f - amount.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt()
        )
    }

    private fun drawBombCandy(canvas: Canvas, half: Float) {
        candyPaint.shader = RadialGradient(
            -half * 0.28f,
            -half * 0.34f,
            half * 1.2f,
            intArrayOf(Color.WHITE, Color.rgb(96, 98, 108), Color.rgb(46, 47, 56), Color.rgb(15, 16, 20)),
            floatArrayOf(0f, 0.16f, 0.56f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(0f, 0f, half * 0.92f, candyPaint)
        candyPaint.shader = null

        candyEdgePaint.color = Color.rgb(255, 188, 54)
        candyEdgePaint.strokeWidth = dp(2f)
        canvas.drawCircle(0f, 0f, half * 0.92f, candyEdgePaint)

        candyPaint.color = Color.rgb(255, 170, 44)
        canvas.drawCircle(0f, half * 0.12f, half * 0.25f, candyPaint)

        candyPaint.color = Color.WHITE
        canvas.drawCircle(-half * 0.28f, -half * 0.32f, half * 0.12f, candyPaint)

        candyEdgePaint.color = Color.rgb(255, 221, 120)
        candyEdgePaint.strokeWidth = dp(2f)
        val fuse = Path().apply {
            moveTo(half * 0.16f, -half * 0.72f)
            cubicTo(
                half * 0.48f, -half * 0.92f,
                half * 0.72f, -half * 0.64f,
                half * 0.56f, -half * 0.36f
            )
        }
        canvas.drawPath(fuse, candyEdgePaint)
        canvas.drawCircle(half * 0.56f, -half * 0.36f, half * 0.07f, candyEdgePaint)
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

    private fun drawMainMenu(canvas: Canvas) {
        val cx = width / 2f

        sparklePaint.color = Color.argb(42, 255, 255, 255)
        canvas.drawCircle(cx, height * 0.22f, min(width, height) * 0.20f, sparklePaint)

        textPaint.color = Color.WHITE
        textPaint.textSize = dp(38f)
        canvas.drawText("CANDY RUSH", cx, height * 0.17f, textPaint)

        accentTextPaint.textSize = dp(15f)
        canvas.drawText("COZY MATCH • ROCKETS • HELPERS", cx, height * 0.215f, accentTextPaint)

        secondaryTextPaint.textSize = dp(14f)
        canvas.drawText(
            "LEVEL " + currentLevel + "   •   COINS " + coins,
            cx,
            height * 0.265f,
            secondaryTextPaint
        )

        drawMenuButton(canvas, menuPlayRect, "PLAY")
        drawMenuButton(canvas, menuShopRect, "SHOP")
        drawMenuButton(canvas, menuHelpersRect, "HELPERS")

        secondaryTextPaint.textSize = dp(12f)
        canvas.drawText(
            "Match 4 = rocket  •  Match 5 = directional bomb",
            cx,
            height * 0.76f,
            secondaryTextPaint
        )
    }

    private fun drawMenuButton(canvas: Canvas, rect: RectF, label: String) {
        cardPaint.color = Color.argb(92, 255, 255, 255)
        canvas.drawRoundRect(rect, dp(20f), dp(20f), cardPaint)
        cardPaint.color = Color.argb(35, 255, 255, 255)
        canvas.drawRoundRect(
            RectF(
                rect.left + dp(2f),
                rect.top + dp(2f),
                rect.right - dp(2f),
                rect.bottom - dp(2f)
            ),
            dp(18f),
            dp(18f),
            cardPaint
        )
        textPaint.textSize = dp(20f)
        textPaint.color = Color.WHITE
        canvas.drawText(label, rect.centerX(), rect.centerY() + dp(7f), textPaint)
    }

    private fun drawShop(canvas: Canvas) {
        textPaint.textSize = dp(29f)
        textPaint.color = Color.WHITE
        canvas.drawText("HELPER SHOP", width / 2f, dp(44f), textPaint)

        accentTextPaint.textSize = dp(17f)
        canvas.drawText("COINS  " + coins, width / 2f, dp(76f), accentTextPaint)

        drawShopItem(canvas, shopHammerRect, "HAMMER", "Remove one candy", 40, helperCounts[0])
        drawShopItem(canvas, shopCrossRect, "CROSS BLAST", "Clear row + column", 60, helperCounts[1])
        drawShopItem(canvas, shopMovesRect, "+5 MOVES", "Add five moves", 80, helperCounts[2])

        cardPaint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(screenBackRect, dp(17f), dp(17f), cardPaint)
        secondaryTextPaint.textSize = dp(13f)
        canvas.drawText(
            "BACK",
            screenBackRect.centerX(),
            screenBackRect.centerY() + dp(4f),
            secondaryTextPaint
        )
    }

    private fun drawShopItem(
        canvas: Canvas,
        rect: RectF,
        title: String,
        subtitle: String,
        price: Int,
        owned: Int
    ) {
        cardPaint.color = Color.argb(78, 255, 255, 255)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), cardPaint)

        textPaint.textSize = dp(18f)
        textPaint.color = Color.WHITE
        canvas.drawText(title, rect.centerX(), rect.top + dp(31f), textPaint)

        secondaryTextPaint.textSize = dp(12f)
        canvas.drawText(
            subtitle + " • " + price + " coins • owned " + owned,
            rect.centerX(),
            rect.bottom - dp(25f),
            secondaryTextPaint
        )
    }

    private fun drawHelpers(canvas: Canvas) {
        textPaint.textSize = dp(29f)
        textPaint.color = Color.WHITE
        canvas.drawText("HELPERS", width / 2f, dp(44f), textPaint)

        secondaryTextPaint.textSize = dp(14f)
        canvas.drawText(
            "Use them during a level after buying them in the shop.",
            width / 2f,
            dp(76f),
            secondaryTextPaint
        )

        drawHelperInfo(canvas, dp(105f), "HAMMER", "Tap it, then tap one candy", "x" + helperCounts[0])
        drawHelperInfo(canvas, dp(200f), "CROSS BLAST", "Tap it, then choose a candy", "x" + helperCounts[1])
        drawHelperInfo(canvas, dp(295f), "+5 MOVES", "Adds five moves immediately", "x" + helperCounts[2])

        accentTextPaint.textSize = dp(14f)
        canvas.drawText(
            "Match 4 creates a rocket that targets a random candy.",
            width / 2f,
            height * 0.60f,
            accentTextPaint
        )
        canvas.drawText(
            "Match 5 creates a bomb. Swipe it in the direction you want.",
            width / 2f,
            height * 0.64f,
            accentTextPaint
        )

        cardPaint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(screenBackRect, dp(17f), dp(17f), cardPaint)
        secondaryTextPaint.textSize = dp(13f)
        canvas.drawText(
            "BACK",
            screenBackRect.centerX(),
            screenBackRect.centerY() + dp(4f),
            secondaryTextPaint
        )
    }

    private fun drawHelperInfo(
        canvas: Canvas,
        top: Float,
        title: String,
        subtitle: String,
        count: String
    ) {
        val rect = RectF(dp(24f), top, width - dp(24f), top + dp(76f))
        cardPaint.color = Color.argb(62, 255, 255, 255)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), cardPaint)

        textPaint.textSize = dp(17f)
        textPaint.color = Color.WHITE
        canvas.drawText(title, rect.left + dp(18f), rect.top + dp(29f), textPaint)

        secondaryTextPaint.textSize = dp(12f)
        canvas.drawText(subtitle, rect.left + dp(18f), rect.bottom - dp(17f), secondaryTextPaint)

        accentTextPaint.textSize = dp(16f)
        canvas.drawText(count, rect.right - dp(26f), rect.centerY() + dp(6f), accentTextPaint)
    }

    private fun drawHelperBar(canvas: Canvas) {
        fun drawButton(rect: RectF, title: String, count: Int, selected: Boolean) {
            cardPaint.color = if (selected) {
                Color.argb(145, 255, 211, 79)
            } else {
                Color.argb(58, 255, 255, 255)
            }
            canvas.drawRoundRect(rect, dp(15f), dp(15f), cardPaint)

            textPaint.textSize = dp(11f)
            textPaint.color = if (selected) Color.rgb(35, 29, 36) else Color.WHITE
            canvas.drawText(title, rect.centerX(), rect.top + dp(17f), textPaint)

            secondaryTextPaint.textSize = dp(10f)
            canvas.drawText(
                "x" + count,
                rect.centerX(),
                rect.bottom - dp(9f),
                secondaryTextPaint
            )
        }

        drawButton(
            helperHammerRect,
            "HAMMER",
            helperCounts[0],
            activeHelper == ActiveHelper.HAMMER
        )
        drawButton(
            helperCrossRect,
            "CROSS",
            helperCounts[1],
            activeHelper == ActiveHelper.CROSS
        )
        drawButton(
            helperMoveRect,
            "+5 MOVES",
            helperCounts[2],
            false
        )

        if (activeHelper != ActiveHelper.NONE) {
            secondaryTextPaint.textSize = dp(10f)
            val label = if (activeHelper == ActiveHelper.HAMMER) {
                "HAMMER ACTIVE • TAP A CANDY"
            } else {
                "CROSS ACTIVE • TAP A CANDY"
            }
            canvas.drawText(label, width / 2f, dp(168f), secondaryTextPaint)
        }
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

    private fun beginSwap(
        r1: Int,
        c1: Int,
        r2: Int,
        c2: Int,
        directionRow: Int = r2 - r1,
        directionCol: Int = c2 - c1
    ) {
        if (swapAnimator?.isRunning == true ||
            rocketAnimator?.isRunning == true ||
            bombAnimator?.isRunning == true
        ) return

        val first = board.get(r1, c1) ?: return
        val second = board.get(r2, c2) ?: return

        if (first.type == Candy.BOMB_TYPE) {
            activateDirectionalBomb(r1, c1, directionRow, directionCol)
            return
        }

        if (first.type == Candy.ROCKET_TYPE) {
            startRocketLaunch(r1 to c1)
            return
        }

        movingFromRow = r1
        movingFromCol = c1
        movingToRow = r2
        movingToCol = c2
        movingFromType = first.type
        movingToType = second.type
        swapProgress = 0f

        selectedRow = -1
        selectedCol = -1

        val success = board.swap(r1, c1, r2, c2)
        sound.playSwap(if (success) 1f else 0.78f)

        if (!success) {
            clearMovingState()
            invalidate()
            return
        }

        swapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 72L
            interpolator = DecelerateInterpolator(1.5f)

            addUpdateListener {
                swapProgress = it.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    swapAnimator = null
                    swapProgress = 1f
                    clearMovingState()
                    startResolutionAnimation()
                }

                override fun onAnimationCancel(animation: Animator) {
                    swapAnimator = null
                    clearMovingState()
                    swapProgress = 0f
                }
            })
            start()
        }
    }

    private fun startResolutionAnimation() {
        if (rocketAnimator?.isRunning == true || bombAnimator?.isRunning == true) return

        val step = board.resolveNextStep()
        if (step == null) {
            fallingCandies = emptyList()
            fallingProgress = 0f
            startSuccessPulse()
            return
        }

        sound.playMatch()
        coins += max(1, step.matchedCount / 3)
        persistInventory()

        fallingCandies = step.fallingCandies
        fallingProgress = 0f

        if (fallingCandies.isEmpty()) {
            continueResolutionAnimation()
        } else {
            startFallingAnimation(220L)
        }
    }

    private fun startFallingAnimation(durationMs: Long) {
        fallingAnimator?.cancel()
        fallingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.45f)

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

    private fun activateDirectionalBomb(
        row: Int,
        col: Int,
        directionRow: Int,
        directionCol: Int
    ) {
        if (bombAnimator?.isRunning == true || rocketAnimator?.isRunning == true) return

        val horizontal = abs(directionCol) >= abs(directionRow)
        val dr = if (horizontal) 0 else if (directionRow < 0) -1 else 1
        val dc = if (horizontal) {
            if (directionCol < 0) -1 else 1
        } else {
            0
        }

        val detonation = board.detonateBomb(row, col, dr, dc) ?: return

        bombCell = row to col
        bombDirectionRow = dr
        bombDirectionCol = dc
        bombProgress = 1f
        explosionCells = detonation.explosionCells
        explosionProgress = 1f
        fallingCandies = detonation.fallingCandies
        fallingProgress = 0f
        selectedRow = -1
        selectedCol = -1
        sound.playMatch()

        bombAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator(1.2f)

            addUpdateListener {
                val value = it.animatedValue as Float
                bombProgress = value
                explosionProgress = value
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    bombAnimator = null
                    bombCell = null
                    bombProgress = 0f
                    explosionProgress = 0f
                    explosionCells = emptyList()

                    if (fallingCandies.isNotEmpty()) {
                        startFallingAnimation(180L)
                    } else {
                        continueResolutionAnimation()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    bombAnimator = null
                    bombCell = null
                    bombProgress = 0f
                    explosionProgress = 0f
                    explosionCells = emptyList()
                    fallingCandies = emptyList()
                }
            })
            start()
        }
    }

    private fun startRocketLaunch(cell: Pair<Int, Int>) {
        if (rocketAnimator?.isRunning == true || bombAnimator?.isRunning == true) return

        val target = board.prepareRocketLaunch(cell.first, cell.second) ?: return
        rocketStartCell = cell
        rocketTargetCell = target
        rocketProgress = 0f
        selectedRow = -1
        selectedCol = -1
        sound.playMatch()

        rocketAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250L
            interpolator = DecelerateInterpolator(1.25f)

            addUpdateListener {
                rocketProgress = it.animatedValue as Float
                invalidate()
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rocketAnimator = null

                    val startCell = rocketStartCell
                    val targetCell = rocketTargetCell
                    rocketStartCell = null
                    rocketTargetCell = null
                    rocketProgress = 0f

                    if (startCell == null || targetCell == null) {
                        continueResolutionAnimation()
                        return
                    }

                    val launch = board.finishRocketLaunch(
                        startCell.first,
                        startCell.second,
                        targetCell.first,
                        targetCell.second
                    )

                    if (launch == null) {
                        continueResolutionAnimation()
                        return
                    }

                    coins += 2
                    persistInventory()
                    fallingCandies = launch.fallingCandies
                    fallingProgress = 0f

                    if (fallingCandies.isNotEmpty()) {
                        startFallingAnimation(180L)
                    } else {
                        continueResolutionAnimation()
                    }
                }

                override fun onAnimationCancel(animation: Animator) {
                    rocketAnimator = null
                    rocketStartCell = null
                    rocketTargetCell = null
                    rocketProgress = 0f
                }
            })
            start()
        }
    }

    private fun drawBombCharge(canvas: Canvas) {
        val cell = bombCell ?: return
        val center = cellCenter(cell.first, cell.second)
        val pulse = 1f + bombProgress * 0.18f
        candyEdgePaint.color = Color.argb(
            (bombProgress * 230f).toInt().coerceIn(0, 230),
            255, 184, 46
        )
        candyEdgePaint.strokeWidth = dp(3f)
        canvas.drawCircle(center.first, center.second, cellSize * 0.54f * pulse, candyEdgePaint)
    }

    private fun drawBombExplosion(canvas: Canvas) {
        val progress = explosionProgress.coerceIn(0f, 1f)
        if (explosionCells.isEmpty()) return

        for ((row, col) in explosionCells) {
            val center = cellCenter(row, col)
            val radius = cellSize * (0.15f + 0.38f * progress)
            val alpha = (progress * 235f).toInt().coerceIn(0, 235)

            candyEdgePaint.color = Color.argb(alpha, 255, 177, 38)
            candyEdgePaint.strokeWidth = dp(4f)
            canvas.drawCircle(center.first, center.second, radius, candyEdgePaint)

            sparklePaint.color = Color.rgb(255, 232, 140)
            sparklePaint.alpha = alpha
            canvas.drawCircle(
                center.first,
                center.second,
                cellSize * 0.15f * progress,
                sparklePaint
            )
        }
        sparklePaint.alpha = 255
    }

    private fun continueResolutionAnimation() {
        if (!isAttachedToWindow) return
        if (board.prepareCascade()) {
            startResolutionAnimation()
        } else {
            startSuccessPulse()
        }
    }

    private fun drawRocketCandy(canvas: Canvas, half: Float) {
        val body = Path().apply {
            moveTo(0f, -half * 0.98f)
            cubicTo(
                half * 0.55f, -half * 0.55f,
                half * 0.55f, half * 0.25f,
                0f, half * 0.74f
            )
            cubicTo(
                -half * 0.55f, half * 0.25f,
                -half * 0.55f, -half * 0.55f,
                0f, -half * 0.98f
            )
            close()
        }

        candyPaint.shader = LinearGradient(
            -half * 0.35f,
            -half,
            half * 0.25f,
            half,
            Color.WHITE,
            Color.rgb(255, 173, 58),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(body, candyPaint)
        candyPaint.shader = null

        candyEdgePaint.color = Color.argb(190, 255, 255, 255)
        candyEdgePaint.strokeWidth = dp(1.5f)
        canvas.drawPath(body, candyEdgePaint)

        sparklePaint.color = Color.rgb(255, 222, 109)
        canvas.drawCircle(0f, -half * 0.22f, half * 0.16f, sparklePaint)

        val finLeft = Path().apply {
            moveTo(-half * 0.30f, half * 0.30f)
            lineTo(-half * 0.82f, half * 0.74f)
            lineTo(-half * 0.18f, half * 0.62f)
            close()
        }
        canvas.drawPath(finLeft, sparklePaint)

        val finRight = Path().apply {
            moveTo(half * 0.30f, half * 0.30f)
            lineTo(half * 0.82f, half * 0.74f)
            lineTo(half * 0.18f, half * 0.62f)
            close()
        }
        canvas.drawPath(finRight, sparklePaint)
    }

    private fun drawRocketFlight(canvas: Canvas) {
        val start = rocketStartCell ?: return
        val target = rocketTargetCell ?: return
        val a = cellCenter(start.first, start.second)
        val b = cellCenter(target.first, target.second)
        val p = easeOut(rocketProgress)
        val x = lerp(a.first, b.first, p)
        val y = lerp(a.second, b.second, p)

        val angle = Math.toDegrees(
            atan2(
                (b.second - a.second).toDouble(),
                (b.first - a.first).toDouble()
            )
        ).toFloat() + 90f

        candyEdgePaint.color = Color.argb(135, 255, 205, 73)
        candyEdgePaint.strokeWidth = dp(3f)
        canvas.drawLine(
            x,
            y,
            lerp(a.first, x, 0.72f),
            lerp(a.second, y, 0.72f),
            candyEdgePaint
        )

        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        drawRocketCandy(canvas, cellSize * 0.42f)
        canvas.restore()

        candyEdgePaint.color = Color.argb(140, 255, 210, 90)
        candyEdgePaint.strokeWidth = dp(2f)
        canvas.drawCircle(
            b.first,
            b.second,
            cellSize * (0.30f + 0.10f * (1f - rocketProgress)),
            candyEdgePaint
        )
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
            duration = 120L
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
        when (screen) {
            Screen.MENU -> return onMenuTouch(event)
            Screen.SHOP -> return onShopTouch(event)
            Screen.HELPERS -> return onHelpersTouch(event)
            Screen.GAME -> Unit
        }

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
                val isTap = max(abs(dx), abs(dy)) < dp(18f)

                parent?.requestDisallowInterceptTouchEvent(false)
                gestureStartRow = -1
                gestureStartCol = -1

                if (helperHammerRect.contains(event.x, event.y) && isTap) {
                    activateOrToggleHelper(ActiveHelper.HAMMER)
                    return true
                }
                if (helperCrossRect.contains(event.x, event.y) && isTap) {
                    activateOrToggleHelper(ActiveHelper.CROSS)
                    return true
                }
                if (helperMoveRect.contains(event.x, event.y) && isTap) {
                    useExtraMoveHelper()
                    return true
                }

                if (swapAnimator?.isRunning == true ||
                    successAnimator?.isRunning == true ||
                    fallingAnimator?.isRunning == true ||
                    bombAnimator?.isRunning == true ||
                    rocketAnimator?.isRunning == true ||
                    earthquakeAnimator?.isRunning == true
                ) return true

                if (levelChipRect.contains(event.x, event.y) && isTap) {
                    openLevelMap()
                    return true
                }

                if (gameOver && isTap) {
                    startLevel(currentLevel)
                    return true
                }

                if (levelComplete && isTap) {
                    navigateLevelBySwipe(1)
                    return true
                }

                if (startRow in 0 until board.rows && startCol in 0 until board.cols) {
                    val threshold = max(dp(12f), min(cellSize * 0.24f, dp(28f)))

                    if (max(abs(dx), abs(dy)) >= threshold) {
                        val horizontal = abs(dx) >= abs(dy)
                        val directionRow = when {
                            !horizontal && dy < 0f -> -1
                            !horizontal -> 1
                            else -> 0
                        }
                        val directionCol = when {
                            horizontal && dx > 0f -> 1
                            horizontal -> -1
                            else -> 0
                        }
                        val targetRow = startRow + directionRow
                        val targetCol = startCol + directionCol

                        when (board.get(startRow, startCol)?.type) {
                            Candy.BOMB_TYPE -> activateDirectionalBomb(
                                startRow,
                                startCol,
                                directionRow,
                                directionCol
                            )
                            Candy.ROCKET_TYPE -> startRocketLaunch(startRow to startCol)
                            else -> {
                                if (targetRow in 0 until board.rows &&
                                    targetCol in 0 until board.cols
                                ) {
                                    beginSwap(
                                        startRow,
                                        startCol,
                                        targetRow,
                                        targetCol,
                                        directionRow,
                                        directionCol
                                    )
                                }
                            }
                        }
                        invalidate()
                        return true
                    }
                }

                if (!isTap) return true

                val cell = cellFromPoint(event.x, event.y) ?: return true
                val row = cell.first
                val col = cell.second

                if (activeHelper != ActiveHelper.NONE) {
                    useActiveHelper(row, col)
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
                    } else if (abs(fromRow - row) + abs(fromCol - col) == 1) {
                        beginSwap(
                            fromRow,
                            fromCol,
                            row,
                            col,
                            row - fromRow,
                            col - fromCol
                        )
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

    private fun activateOrToggleHelper(helper: ActiveHelper) {
        if (helper == ActiveHelper.HAMMER && helperCounts[0] <= 0) return
        if (helper == ActiveHelper.CROSS && helperCounts[1] <= 0) return
        activeHelper = if (activeHelper == helper) ActiveHelper.NONE else helper
        invalidate()
    }

    private fun useExtraMoveHelper() {
        if (helperCounts[2] <= 0) return
        helperCounts[2]--
        board.addMoves(5)
        persistInventory()
        activeHelper = ActiveHelper.NONE
        invalidate()
    }

    private fun useActiveHelper(row: Int, col: Int) {
        val falling = when (activeHelper) {
            ActiveHelper.HAMMER -> {
                if (helperCounts[0] <= 0) return
                helperCounts[0]--
                board.removeCell(row, col)
            }
            ActiveHelper.CROSS -> {
                if (helperCounts[1] <= 0) return
                helperCounts[1]--
                board.clearCross(row, col)
            }
            ActiveHelper.NONE -> return
        }

        persistInventory()
        activeHelper = ActiveHelper.NONE
        fallingCandies = falling
        fallingProgress = 0f
        sound.playMatch()

        if (fallingCandies.isNotEmpty()) {
            startFallingAnimation(180L)
        } else {
            continueResolutionAnimation()
        }
        invalidate()
    }

    private fun onMenuTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true

        when {
            menuPlayRect.contains(event.x, event.y) -> openGame()
            menuShopRect.contains(event.x, event.y) -> {
                screen = Screen.SHOP
                invalidate()
            }
            menuHelpersRect.contains(event.x, event.y) -> {
                screen = Screen.HELPERS
                invalidate()
            }
        }
        return true
    }

    private fun onShopTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true

        if (screenBackRect.contains(event.x, event.y)) {
            screen = Screen.MENU
            invalidate()
            return true
        }

        when {
            shopHammerRect.contains(event.x, event.y) -> buyHelper(0, 40)
            shopCrossRect.contains(event.x, event.y) -> buyHelper(1, 60)
            shopMovesRect.contains(event.x, event.y) -> buyHelper(2, 80)
        }
        return true
    }

    private fun buyHelper(index: Int, price: Int) {
        if (coins < price) return
        coins -= price
        helperCounts[index]++
        persistInventory()
        invalidate()
    }

    private fun onHelpersTouch(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_UP) return true

        if (screenBackRect.contains(event.x, event.y)) {
            screen = Screen.MENU
            invalidate()
        }
        return true
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
            .setDuration(95L)
            .setInterpolator(DecelerateInterpolator(1.35f))
            .withEndAction {
                startLevel(targetLevel)
                translationY = -exitDistance
                animate()
                    .translationY(0f)
                    .setDuration(120L)
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
        bombAnimator?.cancel()
        rocketAnimator?.cancel()
        earthquakeAnimator?.cancel()

        swapAnimator = null
        successAnimator = null
        fallingAnimator = null
        bombAnimator = null
        rocketAnimator = null
        earthquakeAnimator = null

        fallingCandies = emptyList()
        fallingProgress = 0f
        bombCell = null
        bombProgress = 0f
        bombDirectionRow = 0
        bombDirectionCol = 0
        explosionCells = emptyList()
        explosionProgress = 0f

        rocketStartCell = null
        rocketTargetCell = null
        rocketProgress = 0f

        earthquakeProgress = 0f
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

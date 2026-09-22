package com.tomasthrawat.candycrush.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.tomasthrawat.candycrush.model.GameBoard
import kotlin.math.min

/**
 * Custom View that renders the match-3 board and handles touch input.
 */
class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val board = GameBoard(8, 8)
    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = 48f
        textAlign = Paint.Align.CENTER
    }

    private var cellSize = 0f
    private var padding = 8f

    private var selectedRow = -1
    private var selectedCol = -1

    fun startGame() {
        board.refill()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cellSize = min(width, height) / board.cols.toFloat() - padding

        for (r in 0 until board.rows) {
            for (c in 0 until board.cols) {
                val candy = board.get(r, c) ?: continue
                val left = c * (cellSize + padding) + padding
                val top = r * (cellSize + padding) + padding
                val rect = RectF(left, top, left + cellSize, top + cellSize)
                tilePaint.color = candy.color
                canvas.drawRoundRect(rect, 24f, 24f, tilePaint)
                canvas.drawRoundRect(rect, 24f, 24f, borderPaint)

                if (r == selectedRow && c == selectedCol) {
                    val sel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFFFFF.toInt()
                        style = Paint.Style.STROKE
                        strokeWidth = 8f
                    }
                    canvas.drawRoundRect(rect, 24f, 24f, sel)
                }
            }
        }

        // score / moves overlay
        val infoY = (board.rows * (cellSize + padding) + padding + 60f)
        canvas.drawText("Score: ${board.score}", width / 2f, infoY, textPaint)
        canvas.drawText("Moves: ${board.movesLeft}", width / 2f, infoY + 60f, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (cellSize <= 0f) return true

        val c = (event.x / (cellSize + padding)).toInt()
        val r = (event.y / (cellSize + padding)).toInt()
        if (r !in 0 until board.rows || c !in 0 until board.cols) return true

        if (selectedRow == -1) {
            selectedRow = r
            selectedCol = c
        } else {
            board.swap(selectedRow, selectedCol, r, c)
            selectedRow = -1
            selectedCol = -1
            if (board.isGameOver()) board.refill()
        }
        invalidate()
        return true
    }
}

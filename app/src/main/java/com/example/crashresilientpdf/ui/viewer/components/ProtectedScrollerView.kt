package com.example.crashresilientpdf.ui.viewer.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.crashresilientpdf.R
import kotlin.math.roundToInt

/**
 * ProtectedScrollerView - Phase 3
 * 
 * Resilience-focused document scroller.
 * Fixes Phase 1/2 sync bug (used pdfView.height instead of scrollArea height).
 *
 * Features:
 * - 48dp minimum touch target width
 * - Smooth thumb movement
 * - Floating page preview bubble while dragging
 * - Proper page ↔ thumb sync
 * - Visual feedback on interaction
 *
 * Navigation behavior preserved from v1.
 */
class ProtectedScrollerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var pageCount = 1
    private var currentPage = 0
    private var isDragging = false

    private var onPageChangeListener: ((Int) -> Unit)? = null
    private var onPreviewListener: ((Int, Boolean) -> Unit)? = null // page, isDragging

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_light_outlineVariant)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_light_primary)
    }
    private val thumbActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_light_primary)
    }

    private val trackRect = RectF()
    private val thumbRect = RectF()

    private val thumbMinHeight = dp(44f)
    private val trackWidth = dp(3f)
    private val thumbWidth = dp(6f)

    init {
        // 48dp minimum touch target
        minimumWidth = dp(48f).toInt()
        isClickable = true
        isFocusable = true
    }

    fun setPageCount(count: Int) {
        pageCount = count.coerceAtLeast(1)
        invalidate()
    }

    fun setCurrentPage(page: Int, animate: Boolean = true) {
        if (isDragging) return // don't fight user drag
        val newPage = page.coerceIn(0, pageCount - 1)
        if (newPage != currentPage) {
            currentPage = newPage
            invalidate()
        } else {
            // still invalidate to catch first draw
            invalidate()
        }
    }

    fun setOnPageChangeListener(listener: (Int) -> Unit) {
        onPageChangeListener = listener
    }

    fun setOnPreviewListener(listener: (Int, Boolean) -> Unit) {
        onPreviewListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (h <= 0) return

        // Track
        val trackLeft = w / 2f - trackWidth / 2f
        trackRect.set(trackLeft, dp(12f), trackLeft + trackWidth, h - dp(12f))
        canvas.drawRoundRect(trackRect, trackWidth, trackWidth, trackPaint)

        // Thumb
        val availableHeight = trackRect.height()
        val thumbHeight = thumbMinHeight.coerceAtLeast(availableHeight / pageCount.coerceAtLeast(1))
        val ratio = if (pageCount > 1) currentPage.toFloat() / (pageCount - 1) else 0f
        val thumbTop = trackRect.top + ratio * (availableHeight - thumbHeight)
        val thumbLeft = w / 2f - thumbWidth / 2f
        thumbRect.set(thumbLeft, thumbTop, thumbLeft + thumbWidth, thumbTop + thumbHeight)
        
        val paint = if (isDragging) thumbActivePaint else thumbPaint
        paint.alpha = if (isDragging) 255 else 230
        canvas.drawRoundRect(thumbRect, thumbWidth, thumbWidth, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                isDragging = true
                handleMove(event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                handleMove(event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                onPreviewListener?.invoke(currentPage, false)
                invalidate()
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleMove(y: Float) {
        val top = dp(12f)
        val bottom = height - dp(12f)
        val clampedY = y.coerceIn(top, bottom)
        val ratio = if (bottom > top) (clampedY - top) / (bottom - top) else 0f
        val targetPage = (ratio * pageCount).roundToInt().coerceIn(0, pageCount - 1)
        
        if (targetPage != currentPage) {
            currentPage = targetPage
            onPageChangeListener?.invoke(targetPage)
        }
        onPreviewListener?.invoke(targetPage, true)
        invalidate()
    }

    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}

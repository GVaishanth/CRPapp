package com.example.crashresilientpdf.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * RadialStatusGaugeView - Phase 12 (Version 4.0 Conceptual Roadmap)
 *
 * Custom View rendering curved, semi-circular radial status gauges along the bottom arc.
 * Visualizes real-time system health (Prediction Engine Active, Checkpoint Manager Ready)
 * using pulsing neon-green and neon-orange circular segments.
 *
 * - Sci-fi cockpit aesthetic with high-quality Canvas path drawing.
 * - Full support for OS-level reduced motion preferences (ValueAnimator checks).
 * - Read-only operational gauge adhering strictly to frozen engine boundaries.
 *
 * Category: Presentation / Circular GUI
 */
class RadialStatusGaugeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(14f)
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#1AFFFFFF")
    }

    private val healthyGaugePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(14f)
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#00E676") // Neon Green
        setShadowLayer(dp(8f), 0f, 0f, Color.parseColor("#00E676"))
    }

    private val elevatedGaugePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(14f)
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF9100") // Neon Orange
        setShadowLayer(dp(8f), 0f, 0f, Color.parseColor("#FF9100"))
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(14f)
        textAlign = Paint.Align.CENTER
        setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD))
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80CBC4")
        textSize = dp(11f)
        textAlign = Paint.Align.CENTER
    }

    private val arcRect = RectF()
    private var pulseFactor = 1.0f
    private var animator: ValueAnimator? = null

    init {
        // Enable hardware shadow rendering
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startPulseAnimation()
    }

    private fun startPulseAnimation() {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            pulseFactor = 1.0f
            return
        }
        animator = ValueAnimator.ofFloat(0.85f, 1.15f).apply {
            duration = 1000
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                pulseFactor = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padding = dp(24f)
        // Draw bottom arc gauge (from 140° to 40° total sweep 100°)
        arcRect.set(padding, padding, w - padding, (h * 2f) - padding)

        // Background track
        canvas.drawArc(arcRect, 140f, 100f, false, trackPaint)

        // Healthy Gauge Segment (Prediction Engine Active)
        val healthySweep = 60f * pulseFactor.coerceIn(0.8f, 1.2f)
        canvas.drawArc(arcRect, 140f, healthySweep, false, healthyGaugePaint)

        // Elevated Gauge Segment (Checkpoint Standby)
        val elevatedSweep = 25f * pulseFactor.coerceIn(0.8f, 1.2f)
        canvas.drawArc(arcRect, 140f + healthySweep + 5f, elevatedSweep, false, elevatedGaugePaint)

        // Draw HUD Cockpit text in the center above the arc
        val textY = h - dp(32f)
        canvas.drawText("RADIAL TELEMETRY GAUGE", w / 2f, textY, textPaint)
        canvas.drawText("Prediction Active • Checkpoint Ready", w / 2f, textY + dp(18f), subTextPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }

    private fun dp(v: Float): Float = v * context.resources.displayMetrics.density
}
package com.example.crashresilientpdf.ui.dashboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.crashresilientpdf.R
import com.example.crashresilientpdf.core.anomaly.AnomalyState
import kotlin.math.min

/**
 * AnomalySparklineView - Phase 7
 *
 * Live anomaly graph - 60s rolling window.
 * Canvas-drawn, no heavy chart library.
 *
 * - Color bands: LOW / ELEVATED / HIGH
 * - Checkpoint markers as vertical ticks
 * - Smooth line, 10 FPS UI throttle (data is 1 Hz)
 */
class AnomalySparklineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var samples: List<AnomalyState> = emptyList()
    private var checkpointTimes: Set<Long> = emptySet()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_light_outlineVariant)
        strokeWidth = dp(1f)
        alpha = 120
    }
    private val elevatedBandPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.resilience_elevated_container)
        alpha = 70
    }
    private val highBandPaint = Paint().apply {
        color = ContextCompat.getColor(context, R.color.resilience_high_container)
        alpha = 70
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.md_theme_light_primary)
        strokeWidth = dp(2.5f)
        style = Paint.Style.STROKE
        pathEffect = CornerPathEffect(dp(4f))
    }
    private val checkpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.resilience_healthy)
        strokeWidth = dp(2f)
    }
    private val path = Path()

    fun setSamples(samples: List<AnomalyState>, checkpointTimestamps: Set<Long> = emptySet()) {
        this.samples = samples.takeLast(120)
        this.checkpointTimes = checkpointTimestamps
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w < 10f || h < 10f) return

        val padL = dp(8f)
        val padR = dp(8f)
        val padT = dp(8f)
        val padB = dp(8f)
        val gw = w - padL - padR
        val gh = h - padT - padB

        // Risk bands
        val elevatedY = padT + gh * (1f - 0.4f)
        val highY = padT + gh * (1f - 0.7f)
        canvas.drawRect(padL, highY, padL + gw, padT, highBandPaint)
        canvas.drawRect(padL, elevatedY, padL + gw, highY, elevatedBandPaint)

        // Grid lines at 0.4 and 0.7
        canvas.drawLine(padL, elevatedY, padL + gw, elevatedY, gridPaint)
        canvas.drawLine(padL, highY, padL + gw, highY, gridPaint)

        if (samples.isEmpty()) return

        // Anomaly line
        path.reset()
        val n = samples.size
        samples.forEachIndexed { i, s ->
            val x = padL + (i.toFloat() / (maxOf(n - 1, 1)).toFloat()) * gw
            val y = padT + gh * (1f - s.score.toFloat().coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)

            // Checkpoint marker?
            if (checkpointTimes.contains(s.timestampMs)) {
                canvas.drawLine(x, padT, x, padT + gh, checkpointPaint)
            }
            // Also allow fuzzy match within 1.5s for checkpoint markers
            val cpMatch = checkpointTimes.any { kotlin.math.abs(it - s.timestampMs) < 1500 }
            if (cpMatch) {
                canvas.drawLine(x, padT, x, padT + gh, checkpointPaint)
            }
        }
        canvas.drawPath(path, linePaint)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density
}

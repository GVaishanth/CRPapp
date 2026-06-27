package com.example.crashresilientpdf.ui.home

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.crashresilientpdf.R
import com.example.crashresilientpdf.core.checkpoint.ProtectedSession
import com.google.android.material.card.MaterialCardView
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * CircularRecentsCarouselView - Phase 12 (Version 4.0 Conceptual Roadmap)
 *
 * Custom FrameLayout implementing an interactive, orbiting circular recents carousel.
 * Emulates an orbiting wheel of floating circular preview nodes around a central core.
 *
 * - Rotational touch drag mechanics: spin the orbiting wheel via gesture fling/drag.
 * - Minimum 48dp touch targets and dynamic TalkBack accessibility mapping.
 * - Binds persistent ProtectedSession data without modifying core resilience engines.
 *
 * Category: Presentation / Circular GUI
 */
class CircularRecentsCarouselView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var sessions: List<ProtectedSession> = emptyList()
    private var onSessionClick: ((ProtectedSession) -> Unit)? = null

    private var currentAngleOffset = 0.0f
    private var lastTouchAngle = 0.0f
    private var isDragging = false

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun submit(list: List<ProtectedSession>, onClick: (ProtectedSession) -> Unit) {
        this.sessions = list
        this.onSessionClick = onClick
        removeAllViews()

        if (sessions.isEmpty()) return

        val nodeSize = dp(110f).toInt()
        val cornerRad = dp(55f)

        sessions.forEach { session ->
            val card = MaterialCardView(context).apply {
                layoutParams = LayoutParams(nodeSize, nodeSize)
                radius = cornerRad
                cardElevation = dp(6f)
                setCardBackgroundColor(Color.parseColor("#00332C")) // Deep premium slate/teal
                strokeWidth = dp(1f).toInt()
                strokeColor = Color.parseColor("#80CBC4") // Bright teal outline

                minimumWidth = dp(48f).toInt()
                minimumHeight = dp(48f).toInt()
                isClickable = true
                isFocusable = true
                contentDescription = "Open ${session.displayName}, Page ${session.lastPage + 1}"

                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt(), dp(12f).toInt())
                }

                val icon = ImageView(context).apply {
                    setImageResource(R.drawable.ic_shield_check)
                    setColorFilter(Color.parseColor("#00E676")) // Neon green
                    layoutParams = LinearLayout.LayoutParams(dp(28f).toInt(), dp(28f).toInt()).apply {
                        bottomMargin = dp(4f).toInt()
                    }
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                container.addView(icon)

                val title = TextView(context).apply {
                    text = session.displayName
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                    setTextColor(Color.WHITE)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                }
                container.addView(title)

                val pageText = TextView(context).apply {
                    text = "Page ${session.lastPage + 1}"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTextColor(Color.parseColor("#00E676")) // Neon green
                    gravity = Gravity.CENTER
                }
                container.addView(pageText)

                addView(container)

                setOnClickListener {
                    if (!isDragging) {
                        onSessionClick?.invoke(session)
                    }
                }
            }
            addView(card)
        }
        requestLayout()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val count = childCount
        if (count == 0) return

        val w = right - left
        val h = bottom - top
        val centerX = w / 2f
        val centerY = h / 2f

        // Orbit radius: fits comfortably around the 240dp central core
        val orbitRadius = (w / 2f) - dp(70f)

        for (i in 0 until count) {
            val child = getChildAt(i)
            val baseAngle = (i.toFloat() / count) * 2f * Math.PI.toFloat()
            val angle = baseAngle + currentAngleOffset

            val childWidth = child.layoutParams.width
            val childHeight = child.layoutParams.height

            val cX = centerX + orbitRadius * cos(angle)
            val cY = centerY + orbitRadius * sin(angle)

            val cLeft = (cX - childWidth / 2f).toInt()
            val cTop = (cY - childHeight / 2f).toInt()

            child.layout(cLeft, cTop, cLeft + childWidth, cTop + childHeight)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchAngle = getAngle(ev.x, ev.y)
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val currentAngle = getAngle(ev.x, ev.y)
                if (Math.abs(currentAngle - lastTouchAngle) > 0.05f) {
                    isDragging = true
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchAngle = getAngle(event.x, event.y)
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val currentAngle = getAngle(event.x, event.y)
                var delta = currentAngle - lastTouchAngle
                // Handle wrap around at -PI / PI
                if (delta > Math.PI) delta -= (2f * Math.PI.toFloat())
                if (delta < -Math.PI) delta += (2f * Math.PI.toFloat())

                currentAngleOffset += delta
                lastTouchAngle = currentAngle
                isDragging = true
                requestLayout()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getAngle(x: Float, y: Float): Float {
        val centerX = width / 2f
        val centerY = height / 2f
        return atan2(y - centerY, x - centerX)
    }

    private fun dp(v: Float): Float = v * context.resources.displayMetrics.density
}
package com.example.crashresilientpdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.github.barteksc.pdfviewer.PDFView
import kotlin.math.abs
import kotlin.random.Random
import android.view.View
import android.view.MotionEvent
import android.widget.FrameLayout
class MainActivity : AppCompatActivity() {

    private lateinit var pdfView: PDFView
    private lateinit var anomalyText: TextView
    private lateinit var riskText: TextView
    private var isUserDragging = false
    private var lastScroll = 0f
    private var anomalyScore = 0.0

    private lateinit var scrollThumb: View
    private var lastPage = -1

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy {
        getSharedPreferences("checkpoint", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollThumb = findViewById(R.id.scrollThumb)

        val scrollArea = findViewById<FrameLayout>(R.id.scrollArea)
        val upBtn = findViewById<Button>(R.id.upBtn)
        val downBtn = findViewById<Button>(R.id.downBtn)
        upBtn.setOnClickListener {
            val page = pdfView.currentPage
            if (page > 0) {
                pdfView.jumpTo(page - 1, true)
            }
        }

        downBtn.setOnClickListener {
            val page = pdfView.currentPage
            if (page < pdfView.pageCount - 1) {
                pdfView.jumpTo(page + 1, true)
            }
        }
        pdfView = findViewById(R.id.pdfView)
        anomalyText = findViewById(R.id.anomalyText)
        riskText = findViewById(R.id.riskText)
        val crashBtn = findViewById<Button>(R.id.crashBtn)

        pdfView.setOnLongClickListener {
            pdfView.jumpTo(0, true)
            true
        }

        var lastY = 0f

        scrollArea.setOnTouchListener { _: View, event: MotionEvent ->

            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    isUserDragging = true
                }

                MotionEvent.ACTION_MOVE -> {

                    val y = event.y

                    val areaHeight = scrollArea.height.toFloat()
                    val thumbHeight = scrollThumb.height.toFloat()

                    val clampedY = y.coerceIn(0f, areaHeight)

                    val ratio = clampedY / areaHeight

                    scrollThumb.y = ratio * (areaHeight - thumbHeight)

                    val targetPage = (ratio * pdfView.pageCount).toInt()
                    pdfView.jumpTo(targetPage,false)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    isUserDragging = false
                }
            }

            true
        }

        restoreCheckpoint()
        startMonitoring()

        crashBtn.setOnClickListener {
            // Save current state before "crash"
            saveCheckpoint()

            // Restart app
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)

            // Close current activity
            finish()

            // Kill process (simulate crash)
            Runtime.getRuntime().exit(0)
        }

        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            saveCheckpoint()
            restartApp()
        }
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {

                val memory = getMemoryUsage()
                val cpu = Random.nextDouble(0.1, 1.0)
                val scroll = getScrollSpeed()
                val renderDelay = Random.nextDouble(0.1, 1.0)

                anomalyScore = computeAnomaly(memory, cpu, scroll, renderDelay)

                updateUI()

                if (anomalyScore > 0.7) {
                    saveCheckpoint()
                }

                handler.postDelayed(this, 1000)
            }
        })
    }

    private fun getMemoryUsage(): Double {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()).toDouble() / runtime.maxMemory()
    }

    private fun getScrollSpeed(): Double {
        val current = pdfView.currentYOffset
        val speed = abs(current - lastScroll)
        lastScroll = current
        return speed.toDouble() / 1000
    }

    private fun computeAnomaly(m: Double, c: Double, s: Double, r: Double): Double {
        return (0.4 * m + 0.2 * c + 0.2 * s + 0.2 * r).coerceIn(0.0, 1.0)
    }

    private fun updateUI() {
        anomalyText.text = "Anomaly Score: %.2f".format(anomalyScore)

        if (anomalyScore > 0.7) {
            riskText.text = "⚠ HIGH RISK - Saving state"
        } else {
            riskText.text = "Risk: LOW"
        }
    }

    private fun saveCheckpoint() {
        val page = pdfView.currentPage

        prefs.edit()
            .putInt("page", page)
            .apply()
    }

    private fun restoreCheckpoint() {
        val page = prefs.getInt("page", 0)

        pdfView.fromAsset("sample.pdf")
            .onPageChange { pageNum: Int, pageCount: Int ->

                if (!isUserDragging) {

                    val ratio = pageNum.toFloat() / pageCount

                    scrollThumb.post {
                        scrollThumb.y =
                            ratio * (pdfView.height - scrollThumb.height)
                    }
                }
            }
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(false)
            .defaultPage(page)
            .load()
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
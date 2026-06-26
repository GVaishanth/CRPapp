package com.example.crashresilientpdf.core.anomaly

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import android.view.Choreographer
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * MetricsCollector - Phase 4
 *
 * Real, explainable system metrics. No Random values.
 * Every metric documents its source, range, and limitations.
 *
 * See docs/ANOMALY_MODEL.md for full documentation.
 */
class MetricsCollector(private val context: Context) {

    // --- Memory ---
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val runtime = Runtime.getRuntime()

    // --- CPU ---
    private var lastProcCpuNs = 0L
    private var lastWallNs = 0L
    private var lastThreadCpuNs = 0L
    private var procStatFile: RandomAccessFile? = null

    // /proc/self/stat fields: utime=14, stime=15 (1-indexed), in clock ticks
    private var clockTicksPerSec = 100L

    init {
        try {
            // Try to get actual clock ticks per second
            val ticks = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK)
            if (ticks > 0) clockTicksPerSec = ticks
        } catch (_: Exception) { }
        try {
            procStatFile = RandomAccessFile("/proc/self/stat", "r")
        } catch (_: Exception) { }
    }

    // --- Render / Jank ---
    private var frameCallback: Choreographer.FrameCallback? = null
    private var lastFrameNanos = 0L
    @Volatile private var droppedFrames = 0
    @Volatile private var totalFrames = 0
    private val frameWindowMs = 1000L
    private var windowStartMs = System.currentTimeMillis()

    private val choreographer: Choreographer? = try {
        Choreographer.getInstance()
    } catch (_: Exception) { null }

    fun start() {
        startFrameMonitoring()
    }

    fun stop() {
        stopFrameMonitoring()
        try { procStatFile?.close() } catch (_: Exception) {}
    }

    // -------------------------------------------------------------------------
    // Memory - 0.0 .. 1.0
    // Source: JVM heap ratio + ActivityManager memory pressure
    // -------------------------------------------------------------------------
    fun readMemory(): Double {
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()).toDouble()
        val heapMax = runtime.maxMemory().toDouble()
        val heapRatio = (heapUsed / heapMax).coerceIn(0.0, 1.0)

        // System memory pressure
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        // memInfo.availMem / memInfo.totalMem → free ratio, invert
        val sysPressure = if (memInfo.totalMem > 0) {
            1.0 - (memInfo.availMem.toDouble() / memInfo.totalMem.toDouble())
        } else 0.0

        // PSS - proportionate set size, app's actual RAM footprint
        val pssKb = try {
            val mems = activityManager.getProcessMemoryInfo(intArrayOf(Process.myPid()))
            if (mems.isNotEmpty()) mems[0].totalPss.toDouble() else 0.0
        } catch (_: Exception) { 0.0 }
        // Normalize PSS against a reasonable budget: 256 MB
        val pssRatio = (pssKb / (256 * 1024)).coerceIn(0.0, 1.0)

        // Weighted blend: heap is most predictive for Dalvik OOM
        val combined = 0.55 * heapRatio + 0.25 * sysPressure + 0.20 * pssRatio
        return combined.coerceIn(0.0, 1.0)
    }

    // -------------------------------------------------------------------------
    // CPU - 0.0 .. 1.0
    // Source: /proc/self/stat utime+stime delta, fallback to thread CPU time
    //
    // Limitation: /proc/self/stat is throttled on Android 8+ (O),
    // may return stale values under heavy restriction. We fall back to
    // Debug.threadCpuTimeNanos() which is always available but only measures
    // the calling thread (UI thread) - a reasonable proxy for UI jank risk.
    // Documented in ANOMALY_MODEL.md
    // -------------------------------------------------------------------------
    fun readCpu(): Double {
        val nowWall = System.nanoTime()

        // Try /proc/self/stat first
        val procRatio = readProcCpu()
        if (procRatio >= 0) {
            lastWallNs = nowWall
            return procRatio.coerceIn(0.0, 1.0)
        }

        // Fallback: UI thread CPU time
        val threadNs = Debug.threadCpuTimeNanos()
        if (lastThreadCpuNs > 0 && lastWallNs > 0) {
            val cpuDelta = (threadNs - lastThreadCpuNs).coerceAtLeast(0)
            val wallDelta = (nowWall - lastWallNs).coerceAtLeast(1)
            // thread CPU / wall time, clamped - UI thread saturating = high risk
            val ratio = (cpuDelta.toDouble() / wallDelta.toDouble()).coerceIn(0.0, 1.0)
            lastThreadCpuNs = threadNs
            lastWallNs = nowWall
            return ratio
        }
        lastThreadCpuNs = threadNs
        lastWallNs = nowWall
        return 0.15 // warm-up sample - conservative low
    }

    private fun readProcCpu(): Double {
        val f = procStatFile ?: return -1.0
        return try {
            f.seek(0)
            val line = f.readLine() ?: return -1.0
            // /proc/[pid]/stat is space-separated, comm field may contain spaces in parentheses
            // Find last ')', fields after that are predictable
            val endParen = line.lastIndexOf(')')
            if (endParen < 0) return -1.0
            val after = line.substring(endParen + 1).trim().split(Regex("\\s+"))
            // utime = field 14 → index 11 after comm, stime = field 15 → index 12
            if (after.size < 13) return -1.0
            val utime = after[11].toLongOrNull() ?: return -1.0
            val stime = after[12].toLongOrNull() ?: return -1.0
            val totalTicks = utime + stime
            val totalNs = totalTicks * 1_000_000_000L / clockTicksPerSec

            if (lastProcCpuNs > 0 && lastWallNs > 0) {
                val cpuDelta = (totalNs - lastProcCpuNs).coerceAtLeast(0)
                val wallDelta = (System.nanoTime() - lastWallNs).coerceAtLeast(1)
                // Normalize against single-core saturation.
                // Multi-core would need / numCores - we want "is this process hot"
                val ratio = cpuDelta.toDouble() / wallDelta.toDouble()
                lastProcCpuNs = totalNs
                ratio.coerceIn(0.0, 1.0)
            } else {
                lastProcCpuNs = totalNs
                -1.0 // need baseline
            }
        } catch (_: Exception) {
            -1.0
        }
    }

    // -------------------------------------------------------------------------
    // Render - 0.0 .. 1.0
    // Source: Choreographer frame timing → jank / dropped frame ratio
    //
    // Measures UI thread frame deadline misses. High jank = rendering pressure,
    // often precedes ANR / OOM in PDF rendering.
    // -------------------------------------------------------------------------
    fun readRender(): Double {
        val now = System.currentTimeMillis()
        if (now - windowStartMs >= frameWindowMs) {
            // rotate window
            synchronized(this) {
                droppedFrames = 0
                totalFrames = 0
                windowStartMs = now
            }
            return 0.05 // fresh window - low risk
        }
        val dropped: Int
        val total: Int
        synchronized(this) {
            dropped = droppedFrames
            total = totalFrames
        }
        if (total < 3) return 0.08 // not enough samples
        // dropped / total, with smoothing floor
        val jankRatio = dropped.toDouble() / total.toDouble()
        return jankRatio.coerceIn(0.0, 1.0)
    }

    private fun startFrameMonitoring() {
        val c = choreographer ?: return
        frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameNanos != 0L) {
                    val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000.0
                    // Expected ~16.67ms @ 60Hz. Allow 20ms grace.
                    // > 32ms = dropped at least 1 frame
                    synchronized(this@MetricsCollector) {
                        totalFrames++
                        if (deltaMs > 32.0) droppedFrames++
                    }
                }
                lastFrameNanos = frameTimeNanos
                c.postFrameCallback(this)
            }
        }
        c.postFrameCallback(frameCallback!!)
    }

    private fun stopFrameMonitoring() {
        frameCallback?.let { choreographer?.removeFrameCallback(it) }
        frameCallback = null
    }

    // -------------------------------------------------------------------------
    // Scroll - 0.0 .. 1.0
    // Source: PDFView Y-offset delta (provided by caller)
    // The collector does NOT read PDFView directly - caller injects scroll speed
    // Normalization happens in AnomalyModel
    // -------------------------------------------------------------------------
    fun normalizeScroll(rawScrollSpeed: Double): Double {
        // rawScrollSpeed = abs(deltaYOffset) / 1000, from PdfSessionController
        // Typical slow read: 0.0 - 0.3
        // Fast fling: 1.5 - 5.0+
        // Map with soft knee: speed / (speed + 1.2)
        val normalized = rawScrollSpeed / (rawScrollSpeed + 1.2)
        return normalized.coerceIn(0.0, 1.0)
    }
}

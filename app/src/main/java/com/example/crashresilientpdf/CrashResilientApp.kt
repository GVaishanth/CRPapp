package com.example.crashresilientpdf

import android.app.Application
import android.os.SystemClock
import com.example.crashresilientpdf.core.recovery.RecoveryManager

class CrashResilientApp : Application() {

    override fun onCreate() {
        super.onCreate()
        processStartElapsedMs = SystemClock.elapsedRealtime()
        // Install global crash handler early, before any Activity
        RecoveryManager.install(this)
    }

    companion object {
        @Volatile var processStartElapsedMs: Long = 0L
    }
}

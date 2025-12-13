package com.example.telegramalramrelay

import android.app.Application
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                val trace = sw.toString()
                getSharedPreferences("settings", MODE_PRIVATE).edit().putString("lastCrash", trace).apply()
            } catch (_: Exception) {}
            // Let system handle termination after we saved trace
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        }
    }
}

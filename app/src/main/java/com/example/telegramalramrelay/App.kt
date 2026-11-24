package com.example.telegramalramrelay

import android.app.Application
import com.example.telegramalramrelay.data.SettingsRepository
import com.example.telegramalramrelay.util.DailyResetScheduler
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        // Initialize repository early so legacy plaintext credentials migrate cleanly on app upgrade
        val repo = SettingsRepository.getInstance(this)
        repo.checkAndApplyDailyResetIfDue()
        DailyResetScheduler.scheduleNextReset(this)

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
            try {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                val trace = sw.toString()
                getSharedPreferences(SettingsRepository.PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(SettingsRepository.KEY_LAST_CRASH, trace)
                    .commit()
            } catch (_: Exception) {}

            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                System.exit(10)
            }
        }
    }
}

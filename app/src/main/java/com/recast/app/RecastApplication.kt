package com.recast.app

import android.app.Application
import android.os.Build
import java.io.File

class RecastApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // O processo ":office" só roda o LibreOffice: não precisa de filas, yt-dlp nem estado.
        if (!isOfficeProcess()) container = AppContainer(this)
    }

    private fun isOfficeProcess(): Boolean = currentProcessName().endsWith(":office")

    private fun currentProcessName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getProcessName()
        } else {
            runCatching { File("/proc/self/cmdline").readText().trimEnd('\u0000') }.getOrDefault(packageName)
        }
}

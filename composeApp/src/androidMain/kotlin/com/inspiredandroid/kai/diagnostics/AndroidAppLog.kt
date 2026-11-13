package com.inspiredandroid.kai.diagnostics

import android.content.Context
import com.inspiredandroid.kai.Version
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File

/** Keep recent diagnostics across process restarts, including the last uncaught failure. */
@OptIn(FlowPreview::class)
fun initializeAppLogging(context: Context) {
    val file = File(context.noBackupFilesDir, "diagnostics.log")
    val lock = Any()
    runCatching {
        if (file.exists()) AppLog.restore(file.readText().takeLast(AppLog.MAX_CHARS))
    }.onFailure { AppLog.error("Log", "Could not restore previous diagnostics", it) }

    fun persist() {
        synchronized(lock) {
            runCatching { file.writeText(AppLog.text.value) }
                .onFailure { android.util.Log.w("AppLog", "Could not save diagnostics", it) }
        }
    }

    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        AppLog.text.debounce(250).collect { persist() }
    }
    val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    if (previousHandler != null) {
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AppLog.error("Crash", "Uncaught failure on ${thread.name}", error)
            persist()
            previousHandler.uncaughtException(thread, error)
        }
    }
    AppLog.info("App", "Started Kai ${Version.appVersion}; Android ${android.os.Build.VERSION.RELEASE}")
}

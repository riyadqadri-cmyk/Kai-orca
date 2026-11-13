package com.inspiredandroid.kai

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.diagnostics.AppLog
import com.inspiredandroid.kai.diagnostics.initializeAppLogging
import com.inspiredandroid.kai.sandbox.sandboxModule
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class KaiApplication : Application() {

    private val taskScheduler: TaskScheduler by inject()

    override fun onCreate() {
        super.onCreate()
        initializeAppLogging(this)
        startKoin {
            androidContext(this@KaiApplication)
            modules(appModule, sandboxModule)
        }
        // Track app foreground state so the scheduler only pushes a heartbeat notification
        // when the in-app banner isn't visible. ViewModel lifecycle is the wrong signal —
        // it survives backgrounding and only clears on Activity destruction.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLog.info("App", "Entered foreground")
                taskScheduler.appInForeground = true
            }
            override fun onStop(owner: LifecycleOwner) {
                AppLog.info("App", "Entered background")
                taskScheduler.appInForeground = false
            }
        })
    }
}

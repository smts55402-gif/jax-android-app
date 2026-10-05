package com.jax.automation

import android.app.Application
import com.jax.automation.di.AppContainer
import com.jax.automation.tasks.QueueResumeWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class JaxApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val container by lazy { AppContainer(this, appScope) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { runCatching { container.registry.ensureSeeded() } }
        QueueResumeWorker.enqueue(this)
    }
}

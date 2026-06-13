package io.mikoshift.natsu

import android.app.Application
import io.mikoshift.natsu.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NatsuApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        applicationScope.launch {
            appContainer.authRepository.hydrateSession()
            appContainer.syncScheduler.schedulePeriodic()
        }
    }
}

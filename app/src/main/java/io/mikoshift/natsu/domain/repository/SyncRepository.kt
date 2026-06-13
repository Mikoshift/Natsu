package io.mikoshift.natsu.domain.repository

import kotlinx.coroutines.flow.Flow

interface SyncRepository {
    val isSyncing: Flow<Boolean>

    suspend fun syncAll(): Result<Unit>

    fun scheduleSync(delaySeconds: Long = 0)
}

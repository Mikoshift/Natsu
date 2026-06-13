package io.mikoshift.natsu.data.sync

import io.mikoshift.natsu.data.auth.SessionStore
import io.mikoshift.natsu.data.book.BookPackageZip
import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.local.DocumentLocalStore
import io.mikoshift.natsu.data.remote.ApiException
import io.mikoshift.natsu.data.remote.NatsuApiClient
import io.mikoshift.natsu.data.remote.dto.SyncDocumentsRequestDto
import io.mikoshift.natsu.data.remote.dto.UpdateReaderSettingsRequestDto
import io.mikoshift.natsu.data.settings.ReaderSettingsStore
import io.mikoshift.natsu.domain.model.AuthState
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.repository.SyncRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.io.File

class SyncRepositoryImpl(
    private val apiClient: NatsuApiClient,
    private val sessionStore: SessionStore,
    private val documentLocalStore: DocumentLocalStore,
    private val readerSettingsStore: ReaderSettingsStore,
    private val bookStorage: BookStorage,
    private val syncScheduler: SyncScheduler,
) : SyncRepository {
    private val syncing = MutableStateFlow(false)

    override val isSyncing: Flow<Boolean> = syncing.asStateFlow()

    override suspend fun syncAll(): Result<Unit> = runCatching {
        if (sessionStore.authState.first() !is AuthState.Authenticated) {
            return@runCatching
        }

        syncing.value = true
        try {
            pushDocuments()
            pushPackages()
            pullDocuments()
            pullPackages()
            syncReaderSettings()
        } catch (error: ApiException) {
            if (error.code == 401) {
                sessionStore.clearSession()
            }
            throw error
        } finally {
            syncing.value = false
        }
    }

    override fun scheduleSync(delaySeconds: Long) {
        syncScheduler.schedule(delaySeconds)
    }

    private suspend fun pushDocuments() {
        val dirtyDocuments = documentLocalStore.getDirtyDocuments()
        if (dirtyDocuments.isEmpty()) {
            return
        }

        val response = apiClient.pushDocuments(
            SyncDocumentsRequestDto(
                documents = dirtyDocuments.map(SyncDocumentMapper::toDto),
            ),
        )

        documentLocalStore.markSynced(dirtyDocuments.map { it.id })
        dirtyDocuments.filter { it.deleted }.forEach { document ->
            documentLocalStore.purgeDeleted(document.id)
        }

        sessionStore.setLastSyncAtMs(response.server_time_ms)
    }

    private suspend fun pushPackages() {
        val packageDirtyDocuments = documentLocalStore.getPackageDirtyDocuments()
        if (packageDirtyDocuments.isEmpty()) {
            return
        }

        packageDirtyDocuments.forEach { document ->
            val bookDir = bookStorage.bookDirectory(document.id)
            if (!BookPackageZip.hasManifest(bookDir)) {
                return@forEach
            }

            val zipFile = BookPackageZip.zipBookDir(bookDir)
            try {
                val response = apiClient.uploadPackage(document.id, zipFile)
                documentLocalStore.markPackageSynced(listOf(document.id))
                val remote = response.document
                documentLocalStore.upsertFromSync(
                    document.copy(
                        title = remote.title,
                        packageUpdatedAtMs = remote.package_updated_at_ms,
                        packageSha256 = remote.package_sha256,
                        packageDirty = false,
                        syncDirty = false,
                    ),
                )
                sessionStore.setLastSyncAtMs(response.server_time_ms)
            } finally {
                zipFile.delete()
            }
        }
    }

    private suspend fun pullDocuments() {
        val sinceMs = sessionStore.readLastSyncAtMs()
        val response = apiClient.pullDocuments(sinceMs)

        response.documents.forEach { remote ->
            val local = documentLocalStore.getById(remote.id)
            if (!SyncDocumentMapper.shouldApplyRemote(local, remote)) {
                return@forEach
            }

            if (remote.deleted) {
                local?.storagePath?.let(bookStorage::deleteBookPackage)
                if (local != null) {
                    documentLocalStore.purgeDeleted(remote.id)
                }
                return@forEach
            }

            val merged = SyncDocumentMapper.toLocal(remote, local)
            if (local == null) {
                documentLocalStore.upsertFromSync(
                    merged.copy(storagePath = merged.id),
                )
            } else {
                documentLocalStore.upsertFromSync(
                    merged.copy(storagePath = local.storagePath),
                )
            }
        }

        sessionStore.setLastSyncAtMs(response.server_time_ms)
    }

    private suspend fun pullPackages() {
        documentLocalStore.getAll().forEach { local ->
            if (!local.hasRemotePackage()) {
                return@forEach
            }

            val bookDir = bookStorage.bookDirectory(local.id)
            val localHasManifest = BookPackageZip.hasManifest(bookDir)
            val remoteHead = runCatching { apiClient.headPackage(local.id) }.getOrNull()
                ?: return@forEach

            if (remoteHead.contentLength <= 0L) {
                return@forEach
            }

            val shaMatches = localHasManifest &&
                local.packageSha256 != null &&
                remoteHead.sha256 == local.packageSha256

            if (shaMatches) {
                return@forEach
            }

            downloadPackage(local, bookDir, remoteHead.sha256, remoteHead.updatedAtMs)
        }
    }

    private suspend fun downloadPackage(
        local: Document,
        bookDir: File,
        remoteSha256: String?,
        remoteUpdatedAtMs: Long,
    ) {
        val zipFile = File.createTempFile("natsu_dl_", ".zip")
        try {
            apiClient.downloadPackage(local.id, zipFile)
            BookPackageZip.unzipToBookDir(zipFile, bookDir)
            documentLocalStore.upsertFromSync(
                local.copy(
                    storagePath = bookDir.absolutePath,
                    packageUpdatedAtMs = remoteUpdatedAtMs,
                    packageSha256 = remoteSha256,
                    packageDirty = false,
                ),
            )
        } finally {
            zipFile.delete()
        }
    }

    private suspend fun syncReaderSettings() {
        val localMeta = readerSettingsStore.readSyncMetadata()
        val localSettings = readerSettingsStore.readSettingsSnapshot()

        if (localMeta.syncDirty) {
            val pushResponse = apiClient.putReaderSettings(
                UpdateReaderSettingsRequestDto(
                    font_size_sp = localSettings.fontSizeSp,
                    line_spacing_multiplier = localSettings.lineSpacingMultiplier,
                    theme = localSettings.theme.name,
                    furigana_mode = localSettings.furiganaMode.name,
                    updated_at_ms = localMeta.updatedAtMs,
                ),
            )
            readerSettingsStore.applyRemoteMetadata(
                updatedAtMs = pushResponse.settings.updated_at_ms,
                syncDirty = false,
            )
            sessionStore.setLastSyncAtMs(pushResponse.server_time_ms)
            return
        }

        val remoteResponse = apiClient.getReaderSettings()
        val remote = remoteResponse.settings
        if (remote.updated_at_ms > localMeta.updatedAtMs) {
            readerSettingsStore.applyRemoteSettings(
                fontSizeSp = remote.font_size_sp,
                lineSpacingMultiplier = remote.line_spacing_multiplier,
                theme = remote.theme,
                furiganaMode = remote.furigana_mode,
                updatedAtMs = remote.updated_at_ms,
            )
        }
        sessionStore.setLastSyncAtMs(remoteResponse.server_time_ms)
    }
}

private fun Document.hasRemotePackage(): Boolean = packageUpdatedAtMs > 0L

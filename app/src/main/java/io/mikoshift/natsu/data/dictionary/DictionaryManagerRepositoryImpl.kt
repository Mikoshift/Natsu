package io.mikoshift.natsu.data.dictionary

import android.content.Context
import io.mikoshift.natsu.domain.model.DictionaryCatalogItem
import io.mikoshift.natsu.domain.model.DictionaryInstallState
import io.mikoshift.natsu.domain.model.InstalledDictionary
import io.mikoshift.natsu.domain.repository.DictionaryManagerRepository
import io.mikoshift.natsu.domain.repository.PriorityDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class DictionaryManagerRepositoryImpl(
    context: Context,
    private val catalogLoader: DictionaryCatalogLoader,
    private val localStore: DictionaryLocalStore,
    private val downloadManager: DictionaryDownloadManager,
    private val importer: TermBankImporter,
) : DictionaryManagerRepository {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadMutex = Mutex()
    private val downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    private val installedRecords = MutableStateFlow<Map<String, DictionaryRecord>>(emptyMap())
    private var cachedCatalog: List<DictionaryCatalogItem>? = null

    init {
        localStore.observeChanges()
            .onEach { refreshInstalledRecords() }
            .launchIn(scope)
    }

    override suspend fun getCatalog(): List<DictionaryCatalogItem> = getCatalogSync()

    override fun observeDictionaries(): Flow<List<InstalledDictionary>> =
        combine(
            downloadProgress,
            downloadingIds,
            installedRecords,
        ) { progress, downloading, installed ->
            buildDictionaryList(
                progress = progress,
                downloading = downloading,
                installed = installed,
            )
        }.onStart {
            refreshInstalledRecords()
        }

    private fun getCatalogSync(): List<DictionaryCatalogItem> {
        cachedCatalog?.let { return it }
        return catalogLoader.loadCatalog().also { cachedCatalog = it }
    }

    private fun buildDictionaryList(
        progress: Map<String, Float>,
        downloading: Set<String>,
        installed: Map<String, DictionaryRecord>,
    ): List<InstalledDictionary> {
        val catalog = getCatalogSync()
        return catalog.map { item ->
            val record = installed[item.id]
            val isDownloading = item.id in downloading
            InstalledDictionary(
                catalogId = item.id,
                name = item.name,
                description = item.description,
                sizeHintMb = item.sizeHintMb,
                title = record?.title,
                enabled = record?.enabled ?: false,
                priority = record?.priority ?: Int.MAX_VALUE,
                installState = when {
                    isDownloading -> DictionaryInstallState.Downloading
                    record != null -> DictionaryInstallState.Installed
                    else -> DictionaryInstallState.NotInstalled
                },
                downloadProgress = progress[item.id] ?: 0f,
            )
        }.sortedWith(
            compareBy<InstalledDictionary> {
                when (it.installState) {
                    DictionaryInstallState.Installed -> 0
                    DictionaryInstallState.Downloading -> 1
                    DictionaryInstallState.NotInstalled -> 2
                }
            }.thenBy { it.priority }
                .thenBy { it.name },
        )
    }

    override suspend fun downloadDictionary(catalogId: String): Result<Unit> =
        downloadMutex.withLock {
            val catalogItem = getCatalog().firstOrNull { it.id == catalogId }
                ?: return Result.failure(IllegalArgumentException("Unknown dictionary: $catalogId"))
            if (catalogId in downloadingIds.value) {
                return Result.failure(IllegalStateException("Already downloading"))
            }

            downloadingIds.value = downloadingIds.value + catalogId
            downloadProgress.value = downloadProgress.value + (catalogId to 0f)

            val cacheDir = File(appContext.cacheDir, "dictionary_downloads").apply { mkdirs() }
            val zipFile = File(cacheDir, "$catalogId.zip")
            val progressTracker = DownloadProgressTracker()

            try {
                downloadManager.download(catalogItem.downloadUrl, zipFile) { rawProgress ->
                    progressTracker.onDownloadProgress(rawProgress) { mapped ->
                        downloadProgress.value = downloadProgress.value + (catalogId to mapped)
                    }
                }.getOrThrow()

                progressTracker.onDownloadComplete { mapped ->
                    downloadProgress.value = downloadProgress.value + (catalogId to mapped)
                }

                var index: DictionaryArchiveIndex? = null
                localStore.replaceTermsStreaming(catalogId) { insertBatch ->
                    index = importer.importZip(
                        zipFile = zipFile,
                        catalogId = catalogId,
                        onBatch = insertBatch,
                        onImportProgress = { filesProcessed, totalFiles ->
                            progressTracker.onImportProgress(filesProcessed, totalFiles) { mapped ->
                                downloadProgress.value = downloadProgress.value + (catalogId to mapped)
                            }
                        },
                    )
                }
                val dictionaryIndex = index ?: error("Dictionary index missing after import")
                val priority = localStore.nextPriority()
                localStore.upsertDictionary(
                    DictionaryRecord(
                        id = catalogId,
                        title = dictionaryIndex.title,
                        revision = dictionaryIndex.revision,
                        enabled = true,
                        priority = priority,
                        installedAt = System.currentTimeMillis(),
                    ),
                )
                Result.success(Unit)
            } catch (error: Throwable) {
                localStore.deleteDictionary(catalogId)
                Result.failure(error)
            } finally {
                zipFile.delete()
                downloadingIds.value = downloadingIds.value - catalogId
                downloadProgress.value = downloadProgress.value - catalogId
            }
        }

    override suspend fun deleteDictionary(catalogId: String) {
        localStore.deleteDictionary(catalogId)
    }

    override suspend fun setEnabled(catalogId: String, enabled: Boolean) {
        localStore.setEnabled(catalogId, enabled)
    }

    override suspend fun movePriority(catalogId: String, direction: PriorityDirection) {
        val installed = localStore.getAllDictionaries().sortedBy { it.priority }
        val index = installed.indexOfFirst { it.id == catalogId }
        if (index < 0) return
        val targetIndex = when (direction) {
            PriorityDirection.Up -> index - 1
            PriorityDirection.Down -> index + 1
        }
        if (targetIndex !in installed.indices) return
        val mutable = installed.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(targetIndex, item)
        localStore.updatePriorities(mutable.map { it.id })
    }

    override suspend fun hasEnabledDictionaries(): Boolean =
        localStore.hasEnabledDictionaries()

    private suspend fun refreshInstalledRecords() {
        installedRecords.value = localStore.getAllDictionaries().associateBy { it.id }
    }

    private class DownloadProgressTracker {
        private var lastEmitAtMs = 0L
        private var lastEmittedProgress = -1f

        fun onDownloadProgress(rawProgress: Float, emit: (Float) -> Unit) {
            val mapped = rawProgress * DOWNLOAD_WEIGHT
            maybeEmit(mapped, force = rawProgress >= 1f, emit = emit)
        }

        fun onDownloadComplete(emit: (Float) -> Unit) {
            maybeEmit(DOWNLOAD_WEIGHT, force = true, emit = emit)
        }

        fun onImportProgress(filesProcessed: Int, totalFiles: Int, emit: (Float) -> Unit) {
            val fraction = if (totalFiles > 0) filesProcessed.toFloat() / totalFiles.toFloat() else 1f
            val mapped = DOWNLOAD_WEIGHT + IMPORT_WEIGHT * fraction
            maybeEmit(mapped, force = filesProcessed >= totalFiles, emit = emit)
        }

        private fun maybeEmit(mapped: Float, force: Boolean, emit: (Float) -> Unit) {
            val clamped = mapped.coerceIn(0f, 1f)
            val now = System.currentTimeMillis()
            val delta = kotlin.math.abs(clamped - lastEmittedProgress)
            if (
                force ||
                now - lastEmitAtMs >= THROTTLE_MS ||
                delta >= MIN_PROGRESS_DELTA
            ) {
                lastEmitAtMs = now
                lastEmittedProgress = clamped
                emit(clamped)
            }
        }

        companion object {
            private const val DOWNLOAD_WEIGHT = 0.4f
            private const val IMPORT_WEIGHT = 0.6f
            private const val THROTTLE_MS = 250L
            private const val MIN_PROGRESS_DELTA = 0.01f
        }
    }
}

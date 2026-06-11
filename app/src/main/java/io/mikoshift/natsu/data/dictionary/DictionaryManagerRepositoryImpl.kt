package io.mikoshift.natsu.data.dictionary

import android.content.Context
import io.mikoshift.natsu.domain.model.DictionaryCatalogItem
import io.mikoshift.natsu.domain.model.DictionaryInstallState
import io.mikoshift.natsu.domain.model.InstalledDictionary
import io.mikoshift.natsu.domain.repository.DictionaryManagerRepository
import io.mikoshift.natsu.domain.repository.PriorityDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.merge
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
    private val downloadMutex = Mutex()
    private val downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    private var cachedCatalog: List<DictionaryCatalogItem>? = null

    override suspend fun getCatalog(): List<DictionaryCatalogItem> {
        cachedCatalog?.let { return it }
        return catalogLoader.loadCatalog().also { cachedCatalog = it }
    }

    override fun observeDictionaries(): Flow<List<InstalledDictionary>> =
        merge(
            flowOf(Unit),
            localStore.observeChanges(),
            downloadProgress,
            downloadingIds,
        ).flatMapLatest {
            flow {
                emit(
                    buildDictionaryList(
                        progress = downloadProgress.value,
                        downloading = downloadingIds.value,
                    ),
                )
            }
        }

    private suspend fun buildDictionaryList(
        progress: Map<String, Float>,
        downloading: Set<String>,
    ): List<InstalledDictionary> {
        val catalog = getCatalog()
        val installed = localStore.getAllDictionaries().associateBy { it.id }
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

            try {
                downloadManager.download(catalogItem.downloadUrl, zipFile) { progress ->
                    downloadProgress.value = downloadProgress.value + (catalogId to progress)
                }.getOrThrow()

                downloadProgress.value = downloadProgress.value + (catalogId to 0.99f)
                var index: DictionaryArchiveIndex? = null
                localStore.replaceTermsStreaming(catalogId) { insertBatch ->
                    index = importer.importZip(zipFile, catalogId, insertBatch)
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
}

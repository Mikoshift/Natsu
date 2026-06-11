package io.mikoshift.natsu.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import io.mikoshift.natsu.data.dictionary.DictionaryCatalogLoader
import io.mikoshift.natsu.data.dictionary.DictionaryDownloadManager
import io.mikoshift.natsu.data.dictionary.DictionaryLocalStore
import io.mikoshift.natsu.data.dictionary.DictionaryManagerRepositoryImpl
import io.mikoshift.natsu.data.dictionary.MultiDictionaryRepository
import io.mikoshift.natsu.data.dictionary.TermBankImporter
import io.mikoshift.natsu.data.local.DocumentLocalStore
import io.mikoshift.natsu.data.settings.ReaderSettingsStore
import io.mikoshift.natsu.data.reader.KuromojiTokenizer
import io.mikoshift.natsu.data.reader.TextFileImporter
import io.mikoshift.natsu.data.repository.DocumentRepositoryImpl
import io.mikoshift.natsu.domain.repository.DictionaryManagerRepository
import io.mikoshift.natsu.domain.repository.DictionaryRepository
import io.mikoshift.natsu.domain.repository.DocumentRepository
import io.mikoshift.natsu.domain.repository.TextTokenizer
import io.mikoshift.natsu.ui.dictionaries.DictionariesViewModel
import io.mikoshift.natsu.ui.library.LibraryViewModel
import io.mikoshift.natsu.ui.reader.ReaderViewModel
import io.mikoshift.natsu.ui.settings.SettingsViewModel

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val documentLocalStore: DocumentLocalStore by lazy { DocumentLocalStore(appContext) }
    private val textFileImporter: TextFileImporter by lazy { TextFileImporter(appContext) }
    private val dictionaryLocalStore: DictionaryLocalStore by lazy { DictionaryLocalStore(appContext) }
    private val dictionaryCatalogLoader: DictionaryCatalogLoader by lazy { DictionaryCatalogLoader(appContext) }
    private val dictionaryDownloadManager: DictionaryDownloadManager by lazy { DictionaryDownloadManager() }
    private val termBankImporter: TermBankImporter by lazy { TermBankImporter() }
    val readerSettingsStore: ReaderSettingsStore by lazy { ReaderSettingsStore(appContext) }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepositoryImpl(
            documentLocalStore = documentLocalStore,
            textFileImporter = textFileImporter,
        )
    }

    val dictionaryManagerRepository: DictionaryManagerRepository by lazy {
        DictionaryManagerRepositoryImpl(
            context = appContext,
            catalogLoader = dictionaryCatalogLoader,
            localStore = dictionaryLocalStore,
            downloadManager = dictionaryDownloadManager,
            importer = termBankImporter,
        )
    }

    val dictionaryRepository: DictionaryRepository by lazy {
        MultiDictionaryRepository(dictionaryLocalStore)
    }

    val textTokenizer: TextTokenizer by lazy { KuromojiTokenizer() }

    val viewModelFactory: ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return when {
                    modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                        LibraryViewModel(documentRepository) as T
                    modelClass.isAssignableFrom(DictionariesViewModel::class.java) ->
                        DictionariesViewModel(dictionaryManagerRepository) as T
                    modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                        SettingsViewModel(
                            readerSettingsStore = readerSettingsStore,
                            textTokenizer = textTokenizer,
                        ) as T
                    modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
                        ReaderViewModel(
                            documentRepository = documentRepository,
                            dictionaryRepository = dictionaryRepository,
                            textTokenizer = textTokenizer,
                            readerSettingsStore = readerSettingsStore,
                        ) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
        }
}

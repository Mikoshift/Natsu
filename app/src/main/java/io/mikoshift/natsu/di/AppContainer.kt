package io.mikoshift.natsu.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.gson.Gson
import io.mikoshift.natsu.BuildConfig
import io.mikoshift.natsu.data.auth.AuthRepositoryImpl
import io.mikoshift.natsu.data.auth.SessionStore
import io.mikoshift.natsu.data.book.BookImportCoordinator
import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.book.import.EpubBookImporter
import io.mikoshift.natsu.data.book.import.MarkdownBookImporter
import io.mikoshift.natsu.data.book.import.PlainTextBookImporter
import io.mikoshift.natsu.data.book.load.EpubFormatLoader
import io.mikoshift.natsu.data.book.load.HtmlFormatLoader
import io.mikoshift.natsu.data.book.load.ManifestReadingContentLoader
import io.mikoshift.natsu.data.book.load.MarkdownFormatLoader
import io.mikoshift.natsu.data.book.load.PlainTextFormatLoader
import io.mikoshift.natsu.data.dictionary.DictionaryCatalogLoader
import io.mikoshift.natsu.data.dictionary.DictionaryDownloadManager
import io.mikoshift.natsu.data.dictionary.DictionaryLocalStore
import io.mikoshift.natsu.data.dictionary.DictionaryManagerRepositoryImpl
import io.mikoshift.natsu.data.dictionary.MultiDictionaryRepository
import io.mikoshift.natsu.data.dictionary.TermBankImporter
import io.mikoshift.natsu.data.local.DocumentLocalStore
import io.mikoshift.natsu.data.remote.AuthInterceptor
import io.mikoshift.natsu.data.remote.NatsuApiClient
import io.mikoshift.natsu.data.repository.DocumentRepositoryImpl
import io.mikoshift.natsu.data.repository.ReadingContentRepositoryImpl
import io.mikoshift.natsu.data.settings.ReaderSettingsStore
import io.mikoshift.natsu.data.reader.KuromojiTokenizer
import io.mikoshift.natsu.data.reader.ReadingLayoutBuilder
import io.mikoshift.natsu.data.sync.SyncRepositoryImpl
import io.mikoshift.natsu.data.sync.SyncScheduler
import io.mikoshift.natsu.domain.repository.AuthRepository
import io.mikoshift.natsu.domain.repository.DictionaryManagerRepository
import io.mikoshift.natsu.domain.repository.DictionaryRepository
import io.mikoshift.natsu.domain.repository.DocumentRepository
import io.mikoshift.natsu.domain.repository.ReadingContentRepository
import io.mikoshift.natsu.domain.repository.SyncRepository
import io.mikoshift.natsu.domain.repository.TextTokenizer
import io.mikoshift.natsu.ui.auth.AuthViewModel
import io.mikoshift.natsu.ui.dictionaries.DictionariesViewModel
import io.mikoshift.natsu.ui.library.LibraryViewModel
import io.mikoshift.natsu.ui.reader.ReaderViewModel
import io.mikoshift.natsu.ui.settings.SettingsViewModel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(if (BuildConfig.DEBUG) 30 else 10, TimeUnit.SECONDS)
            .readTimeout(if (BuildConfig.DEBUG) 30 else 10, TimeUnit.SECONDS)
            .writeTimeout(if (BuildConfig.DEBUG) 30 else 10, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(sessionStore))
            .build()
    }

    private val gson: Gson by lazy { Gson() }

    val natsuApiClient: NatsuApiClient by lazy {
        NatsuApiClient(
            baseUrl = BuildConfig.API_BASE_URL,
            client = okHttpClient,
            gson = gson,
        )
    }

    val syncScheduler: SyncScheduler by lazy { SyncScheduler(appContext) }

    private val documentLocalStore: DocumentLocalStore by lazy { DocumentLocalStore(appContext) }
    private val bookStorage: BookStorage by lazy { BookStorage(appContext) }

    val syncRepository: SyncRepository by lazy {
        SyncRepositoryImpl(
            apiClient = natsuApiClient,
            sessionStore = sessionStore,
            documentLocalStore = documentLocalStore,
            readerSettingsStore = readerSettingsStore,
            bookStorage = bookStorage,
            syncScheduler = syncScheduler,
        )
    }

    val authRepository: AuthRepository by lazy {
        AuthRepositoryImpl(
            apiClient = natsuApiClient,
            sessionStore = sessionStore,
            syncRepository = syncRepository,
        )
    }

    private val bookImportCoordinator: BookImportCoordinator by lazy {
        BookImportCoordinator(
            context = appContext,
            importers = listOf(
                EpubBookImporter(
                    context = appContext,
                    bookStorage = bookStorage,
                ),
                MarkdownBookImporter(
                    context = appContext,
                    bookStorage = bookStorage,
                ),
                PlainTextBookImporter(
                    context = appContext,
                    bookStorage = bookStorage,
                ),
            ),
        )
    }
    private val manifestReadingContentLoader: ManifestReadingContentLoader by lazy {
        ManifestReadingContentLoader(
            bookStorage = bookStorage,
            formatLoaders = listOf(
                HtmlFormatLoader(),
                PlainTextFormatLoader(),
                MarkdownFormatLoader(),
                EpubFormatLoader(),
            ),
        )
    }
    private val readingLayoutBuilder: ReadingLayoutBuilder by lazy { ReadingLayoutBuilder() }
    private val dictionaryLocalStore: DictionaryLocalStore by lazy { DictionaryLocalStore(appContext) }
    private val dictionaryCatalogLoader: DictionaryCatalogLoader by lazy { DictionaryCatalogLoader(appContext) }
    private val dictionaryDownloadManager: DictionaryDownloadManager by lazy {
        DictionaryDownloadManager(client = okHttpClient)
    }
    private val termBankImporter: TermBankImporter by lazy { TermBankImporter() }
    val readerSettingsStore: ReaderSettingsStore by lazy { ReaderSettingsStore(appContext) }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepositoryImpl(
            documentLocalStore = documentLocalStore,
            bookImportCoordinator = bookImportCoordinator,
            bookStorage = bookStorage,
            manifestReadingContentLoader = manifestReadingContentLoader,
            readingLayoutBuilder = readingLayoutBuilder,
            syncRepository = syncRepository,
        )
    }

    val readingContentRepository: ReadingContentRepository by lazy {
        ReadingContentRepositoryImpl(
            documentLocalStore = documentLocalStore,
            bookStorage = bookStorage,
            manifestReadingContentLoader = manifestReadingContentLoader,
            readingLayoutBuilder = readingLayoutBuilder,
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
                            authRepository = authRepository,
                            syncRepository = syncRepository,
                        ) as T
                    modelClass.isAssignableFrom(AuthViewModel::class.java) ->
                        AuthViewModel(
                            application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
                                "Application missing from ViewModel extras"
                            } as Application,
                            authRepository = authRepository,
                        ) as T
                    modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
                        ReaderViewModel(
                            documentRepository = documentRepository,
                            readingContentRepository = readingContentRepository,
                            dictionaryRepository = dictionaryRepository,
                            textTokenizer = textTokenizer,
                            readerSettingsStore = readerSettingsStore,
                            syncRepository = syncRepository,
                        ) as T
                    else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
                }
            }
        }
}

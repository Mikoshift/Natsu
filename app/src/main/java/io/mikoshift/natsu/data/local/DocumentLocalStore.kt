package io.mikoshift.natsu.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ReadingLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class DocumentLocalStore(context: Context) {
    private val helper = DocumentsOpenHelper(context.applicationContext)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val writeMutex = Mutex()

    fun observeDocuments(): Flow<Unit> = changes.asSharedFlow()

    fun notifyDocumentsChanged() {
        changes.tryEmit(Unit)
    }

    suspend fun getPackageDirtyDocuments(): List<Document> = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.query(
                TABLE,
                DOCUMENT_COLUMNS,
                "package_dirty = 1 AND deleted = 0",
                null,
                null,
                null,
                "updated_at_ms ASC",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toDocument())
                    }
                }
            }
        }
    }

    suspend fun getAll(): List<Document> = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.query(
                TABLE,
                DOCUMENT_COLUMNS,
                "deleted = 0",
                null,
                null,
                null,
                "imported_at DESC",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toDocument())
                    }
                }
            }
        }
    }

    suspend fun getDirtyDocuments(): List<Document> = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.query(
                TABLE,
                DOCUMENT_COLUMNS,
                "sync_dirty = 1",
                null,
                null,
                null,
                "updated_at_ms ASC",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.toDocument())
                    }
                }
            }
        }
    }

    suspend fun getById(id: String): Document? = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.query(
                TABLE,
                DOCUMENT_COLUMNS,
                "id = ?",
                arrayOf(id),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.toDocument() else null
            }
        }
    }

    suspend fun insert(document: Document) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val stored = document.copy(
            updatedAtMs = if (document.updatedAtMs > 0L) document.updatedAtMs else now,
            syncDirty = true,
            packageDirty = true,
        )
        writeMutex.withLock {
            helper.writableDatabase.use { db ->
                db.insertWithOnConflict(
                    TABLE,
                    null,
                    stored.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        changes.tryEmit(Unit)
    }

    suspend fun upsertFromSync(document: Document) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            helper.writableDatabase.use { db ->
                db.insertWithOnConflict(
                    TABLE,
                    null,
                    document.copy(syncDirty = false).toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
        }
        changes.tryEmit(Unit)
    }

    suspend fun updateTitle(id: String, title: String) = withContext(Dispatchers.IO) {
        markDirty(id) { db ->
            db.execSQL(
                "UPDATE $TABLE SET title = ? WHERE id = ?",
                arrayOf<Any>(title, id),
            )
        }
    }

    suspend fun updateCharCount(id: String, charCount: Int) = withContext(Dispatchers.IO) {
        markDirty(id) { db ->
            db.execSQL(
                "UPDATE $TABLE SET char_count = ? WHERE id = ?",
                arrayOf<Any>(charCount, id),
            )
        }
    }

    suspend fun updateReadingPosition(
        id: String,
        globalCharOffset: Int,
        paragraphIndex: Int,
        locator: ReadingLocator?,
    ) = withContext(Dispatchers.IO) {
        markDirty(id) { db ->
            db.execSQL(
                """
                UPDATE $TABLE
                SET last_read_char_offset = ?,
                    last_read_paragraph_index = ?,
                    last_read_section_id = ?,
                    last_read_block_index = ?,
                    last_read_block_char_offset = ?
                WHERE id = ?
                """.trimIndent(),
                arrayOf<Any?>(
                    globalCharOffset,
                    paragraphIndex,
                    locator?.sectionId,
                    locator?.blockIndex ?: 0,
                    locator?.charOffset ?: 0,
                    id,
                ),
            )
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        writeMutex.withLock {
            helper.writableDatabase.use { db ->
                db.execSQL(
                    """
                    UPDATE $TABLE
                    SET deleted = 1,
                        updated_at_ms = ?,
                        sync_dirty = 1
                    WHERE id = ?
                    """.trimIndent(),
                    arrayOf<Any>(now, id),
                )
            }
        }
        changes.tryEmit(Unit)
    }

    suspend fun purgeDeleted(id: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            helper.writableDatabase.use { db ->
                db.delete(TABLE, "id = ?", arrayOf(id))
            }
        }
        changes.tryEmit(Unit)
    }

    suspend fun markPackageSynced(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        writeMutex.withLock {
            helper.writableDatabase.use { db ->
                ids.forEach { id ->
                    db.execSQL(
                        "UPDATE $TABLE SET package_dirty = 0 WHERE id = ?",
                        arrayOf<Any>(id),
                    )
                }
            }
        }
    }

    suspend fun markSynced(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        writeMutex.withLock {
            helper.writableDatabase.use { db ->
                ids.forEach { id ->
                    db.execSQL(
                        "UPDATE $TABLE SET sync_dirty = 0 WHERE id = ?",
                        arrayOf<Any>(id),
                    )
                }
            }
        }
    }

    private suspend fun markDirty(
        id: String,
        update: (SQLiteDatabase) -> Unit,
    ) {
        val now = System.currentTimeMillis()
        writeMutex.withLock {
            helper.writableDatabase.use { db ->
                update(db)
                db.execSQL(
                    "UPDATE $TABLE SET updated_at_ms = ?, sync_dirty = 1 WHERE id = ?",
                    arrayOf<Any>(now, id),
                )
            }
        }
        changes.tryEmit(Unit)
    }

    private class DocumentsOpenHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(CREATE_TABLE_SQL)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 3) {
                db.execSQL("DROP TABLE IF EXISTS $TABLE")
                onCreate(db)
                return
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN last_read_section_id TEXT")
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN last_read_block_index INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN last_read_block_char_offset INTEGER NOT NULL DEFAULT 0",
                )
            }
            if (oldVersion < 5) {
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN updated_at_ms INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN sync_dirty INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0",
                )
            }
            if (oldVersion < 6) {
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN package_dirty INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN package_updated_at_ms INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE $TABLE ADD COLUMN package_sha256 TEXT",
                )
                db.execSQL(
                    "UPDATE $TABLE SET package_dirty = 1 WHERE deleted = 0",
                )
            }
        }
    }

    companion object {
        private const val DB_NAME = "natsu_documents.db"
        private const val DB_VERSION = 6
        private const val TABLE = "documents"

        private val CREATE_TABLE_SQL = """
            CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                storage_path TEXT NOT NULL,
                source_format TEXT NOT NULL,
                imported_at INTEGER NOT NULL,
                char_count INTEGER NOT NULL DEFAULT 0,
                last_read_char_offset INTEGER NOT NULL DEFAULT 0,
                last_read_paragraph_index INTEGER NOT NULL DEFAULT 0,
                last_read_section_id TEXT,
                last_read_block_index INTEGER NOT NULL DEFAULT 0,
                last_read_block_char_offset INTEGER NOT NULL DEFAULT 0,
                updated_at_ms INTEGER NOT NULL DEFAULT 0,
                sync_dirty INTEGER NOT NULL DEFAULT 0,
                package_dirty INTEGER NOT NULL DEFAULT 0,
                package_updated_at_ms INTEGER NOT NULL DEFAULT 0,
                package_sha256 TEXT,
                deleted INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()

        private val DOCUMENT_COLUMNS = arrayOf(
            "id",
            "title",
            "storage_path",
            "source_format",
            "imported_at",
            "char_count",
            "last_read_char_offset",
            "last_read_paragraph_index",
            "last_read_section_id",
            "last_read_block_index",
            "last_read_block_char_offset",
            "updated_at_ms",
            "sync_dirty",
            "package_dirty",
            "package_updated_at_ms",
            "package_sha256",
            "deleted",
        )
    }
}

private fun android.database.Cursor.toDocument(): Document {
    val sectionId = getString(8)
    val blockIndex = getInt(9)
    val blockCharOffset = getInt(10)
    val locator = sectionId?.takeIf { it.isNotBlank() }?.let {
        ReadingLocator(
            sectionId = it,
            blockIndex = blockIndex,
            charOffset = blockCharOffset,
        )
    }
    return Document(
        id = getString(0),
        title = getString(1),
        storagePath = getString(2),
        sourceFormat = BookFormat.fromManifestValue(getString(3)),
        importedAt = getLong(4),
        charCount = getInt(5),
        lastReadCharOffset = getInt(6),
        lastReadParagraphIndex = getInt(7),
        lastReadLocator = locator,
        updatedAtMs = getLong(11),
        syncDirty = getInt(12) == 1,
        packageDirty = getInt(13) == 1,
        packageUpdatedAtMs = getLong(14),
        packageSha256 = getString(15),
        deleted = getInt(16) == 1,
    )
}

private fun Document.toContentValues(): android.content.ContentValues =
    android.content.ContentValues().apply {
        put("id", id)
        put("title", title)
        put("storage_path", storagePath)
        put("source_format", sourceFormat.manifestValue)
        put("imported_at", importedAt)
        put("char_count", charCount)
        put("last_read_char_offset", lastReadCharOffset)
        put("last_read_paragraph_index", lastReadParagraphIndex)
        put("last_read_section_id", lastReadLocator?.sectionId)
        put("last_read_block_index", lastReadLocator?.blockIndex ?: 0)
        put("last_read_block_char_offset", lastReadLocator?.charOffset ?: 0)
        put("updated_at_ms", updatedAtMs)
        put("sync_dirty", if (syncDirty) 1 else 0)
        put("package_dirty", if (packageDirty) 1 else 0)
        put("package_updated_at_ms", packageUpdatedAtMs)
        put("package_sha256", packageSha256)
        put("deleted", if (deleted) 1 else 0)
    }

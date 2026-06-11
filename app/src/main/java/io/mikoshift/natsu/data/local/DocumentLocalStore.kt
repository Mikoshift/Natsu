package io.mikoshift.natsu.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.mikoshift.natsu.domain.model.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

class DocumentLocalStore(context: Context) {
    private val helper = DocumentsOpenHelper(context.applicationContext)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun observeDocuments(): Flow<Unit> = changes.asSharedFlow()

    suspend fun getAll(): List<Document> = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.query(
                TABLE,
                arrayOf("id", "title", "file_path", "imported_at", "last_read_paragraph_index"),
                null,
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

    suspend fun getById(id: String): Document? = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.query(
                TABLE,
                arrayOf("id", "title", "file_path", "imported_at", "last_read_paragraph_index"),
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
        helper.writableDatabase.use { db ->
            db.insertWithOnConflict(
                TABLE,
                null,
                document.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        changes.tryEmit(Unit)
    }

    suspend fun updateReadingPosition(id: String, paragraphIndex: Int) = withContext(Dispatchers.IO) {
        helper.writableDatabase.use { db ->
            db.execSQL(
                "UPDATE $TABLE SET last_read_paragraph_index = ? WHERE id = ?",
                arrayOf<Any>(paragraphIndex, id),
            )
        }
        changes.tryEmit(Unit)
    }

    private class DocumentsOpenHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    file_path TEXT NOT NULL,
                    imported_at INTEGER NOT NULL,
                    last_read_paragraph_index INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val DB_NAME = "natsu_documents.db"
        private const val DB_VERSION = 1
        private const val TABLE = "documents"
    }
}

private fun android.database.Cursor.toDocument(): Document =
    Document(
        id = getString(0),
        title = getString(1),
        filePath = getString(2),
        importedAt = getLong(3),
        lastReadParagraphIndex = getInt(4),
    )

private fun Document.toContentValues(): android.content.ContentValues =
    android.content.ContentValues().apply {
        put("id", id)
        put("title", title)
        put("file_path", filePath)
        put("imported_at", importedAt)
        put("last_read_paragraph_index", lastReadParagraphIndex)
    }

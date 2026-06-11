package io.mikoshift.natsu.data.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class DictionaryRecord(
    val id: String,
    val title: String,
    val revision: String,
    val enabled: Boolean,
    val priority: Int,
    val installedAt: Long,
)

data class TermRecord(
    val dictionaryId: String,
    val expression: String,
    val reading: String,
    val glossesJson: String,
    val score: Int,
)

data class TermLookupRow(
    val dictionaryId: String,
    val dictionaryTitle: String,
    val dictionaryPriority: Int,
    val expression: String,
    val reading: String,
    val glossesJson: String,
    val score: Int,
)

class DictionaryLocalStore(context: Context) {
    private val appContext = context.applicationContext
    private val helper = DictionaryOpenHelper(appContext)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun observeChanges() = changes.asSharedFlow()

    suspend fun getAllDictionaries(): List<DictionaryRecord> = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.query(
                TABLE_DICTIONARIES,
                arrayOf("id", "title", "revision", "enabled", "priority", "installed_at"),
                null,
                null,
                null,
                null,
                "priority ASC, installed_at ASC",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            DictionaryRecord(
                                id = cursor.getString(0),
                                title = cursor.getString(1),
                                revision = cursor.getString(2),
                                enabled = cursor.getInt(3) == 1,
                                priority = cursor.getInt(4),
                                installedAt = cursor.getLong(5),
                            ),
                        )
                    }
                }
            }
        }
    }

    suspend fun getDictionary(id: String): DictionaryRecord? = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.query(
                TABLE_DICTIONARIES,
                arrayOf("id", "title", "revision", "enabled", "priority", "installed_at"),
                "id = ?",
                arrayOf(id),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    DictionaryRecord(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        revision = cursor.getString(2),
                        enabled = cursor.getInt(3) == 1,
                        priority = cursor.getInt(4),
                        installedAt = cursor.getLong(5),
                    )
                } else {
                    null
                }
            }
        }
    }

    suspend fun upsertDictionary(record: DictionaryRecord) = withContext(Dispatchers.IO) {
        helper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT OR REPLACE INTO $TABLE_DICTIONARIES
                (id, title, revision, enabled, priority, installed_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf(
                    record.id,
                    record.title,
                    record.revision,
                    if (record.enabled) 1 else 0,
                    record.priority,
                    record.installedAt,
                ),
            )
        }
        changes.tryEmit(Unit)
    }

    suspend fun deleteDictionary(id: String) = withContext(Dispatchers.IO) {
        helper.writableDatabase.use { db ->
            db.delete(TABLE_TERMS, "dictionary_id = ?", arrayOf(id))
            db.delete(TABLE_DICTIONARIES, "id = ?", arrayOf(id))
        }
        changes.tryEmit(Unit)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        helper.writableDatabase.use { db ->
            db.execSQL(
                "UPDATE $TABLE_DICTIONARIES SET enabled = ? WHERE id = ?",
                arrayOf<Any>(if (enabled) 1 else 0, id),
            )
        }
        changes.tryEmit(Unit)
    }

    suspend fun updatePriorities(orderedIds: List<String>) = withContext(Dispatchers.IO) {
        helper.writableDatabase.use { db ->
            db.beginTransaction()
            try {
                orderedIds.forEachIndexed { index, id ->
                    db.execSQL(
                        "UPDATE $TABLE_DICTIONARIES SET priority = ? WHERE id = ?",
                        arrayOf<Any>(index, id),
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        changes.tryEmit(Unit)
    }

    suspend fun replaceTerms(dictionaryId: String, terms: List<TermRecord>) = withContext(Dispatchers.IO) {
        helper.writableDatabase.use { db ->
            db.beginTransaction()
            try {
                db.delete(TABLE_TERMS, "dictionary_id = ?", arrayOf(dictionaryId))
                val statement = db.compileStatement(
                    """
                    INSERT INTO $TABLE_TERMS
                    (dictionary_id, expression, reading, glosses, score)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                )
                terms.forEach { term ->
                    statement.clearBindings()
                    statement.bindString(1, term.dictionaryId)
                    statement.bindString(2, term.expression)
                    statement.bindString(3, term.reading)
                    statement.bindString(4, term.glossesJson)
                    statement.bindLong(5, term.score.toLong())
                    statement.executeInsert()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        changes.tryEmit(Unit)
    }

    suspend fun lookupTerms(queries: List<String>): List<TermLookupRow> = withContext(Dispatchers.IO) {
        if (queries.isEmpty()) return@withContext emptyList()
        helper.readableDatabase.use { db ->
            val results = linkedMapOf<String, TermLookupRow>()
            for (query in queries) {
                db.rawQuery(
                    """
                    SELECT t.dictionary_id, d.title, d.priority, t.expression, t.reading, t.glosses, t.score
                    FROM $TABLE_TERMS t
                    INNER JOIN $TABLE_DICTIONARIES d ON d.id = t.dictionary_id
                    WHERE d.enabled = 1
                      AND (t.expression = ? OR t.reading = ?)
                    ORDER BY d.priority ASC, t.score DESC
                    """.trimIndent(),
                    arrayOf(query, query),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val dictionaryId = cursor.getString(0)
                        val key = "$dictionaryId:${cursor.getString(3)}:${cursor.getString(4)}"
                        if (key !in results) {
                            results[key] = TermLookupRow(
                                dictionaryId = dictionaryId,
                                dictionaryTitle = cursor.getString(1),
                                dictionaryPriority = cursor.getInt(2),
                                expression = cursor.getString(3),
                                reading = cursor.getString(4),
                                glossesJson = cursor.getString(5),
                                score = cursor.getInt(6),
                            )
                        }
                    }
                }
            }
            results.values.sortedWith(
                compareBy<TermLookupRow> { it.dictionaryPriority }
                    .thenByDescending { it.score },
            )
        }
    }

    suspend fun hasEnabledDictionaries(): Boolean = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.rawQuery(
                "SELECT COUNT(*) FROM $TABLE_DICTIONARIES WHERE enabled = 1",
                null,
            ).use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) > 0
            }
        }
    }

    suspend fun nextPriority(): Int = withContext(Dispatchers.IO) {
        helper.readableDatabase.use { db ->
            db.rawQuery(
                "SELECT COALESCE(MAX(priority), -1) + 1 FROM $TABLE_DICTIONARIES",
                null,
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    private class DictionaryOpenHelper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE_DICTIONARIES (
                    id TEXT PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    revision TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    priority INTEGER NOT NULL DEFAULT 0,
                    installed_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $TABLE_TERMS (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    dictionary_id TEXT NOT NULL,
                    expression TEXT NOT NULL,
                    reading TEXT NOT NULL,
                    glosses TEXT NOT NULL,
                    score INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(dictionary_id) REFERENCES $TABLE_DICTIONARIES(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_terms_expr ON $TABLE_TERMS(expression)")
            db.execSQL("CREATE INDEX idx_terms_reading ON $TABLE_TERMS(reading)")
            db.execSQL("CREATE INDEX idx_terms_dict ON $TABLE_TERMS(dictionary_id)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        private const val DB_NAME = "natsu_dictionaries.db"
        private const val DB_VERSION = 1
        private const val TABLE_DICTIONARIES = "dictionaries"
        private const val TABLE_TERMS = "terms"
    }
}

fun parseGlossesJson(json: String): List<String> {
    val array = JSONArray(json)
    return buildList {
        for (index in 0 until array.length()) {
            add(array.getString(index))
        }
    }
}

fun glossesToJson(glosses: List<String>): String = JSONArray(glosses).toString()

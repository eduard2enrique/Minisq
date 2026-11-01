package com.example.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.net.Uri
import com.example.model.ColumnInfo
import com.example.model.ForeignKeyInfo
import com.example.model.SqlExecutionResult
import com.example.model.TableSchemaDetail
import com.example.model.TableSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStream

class DatabaseManager(private val context: Context) {

    private val dbDir: File
        get() {
            val dir = File(context.filesDir, "sqlite_databases")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val prefs = context.getSharedPreferences("minisql_db_templates", Context.MODE_PRIVATE)

    private var currentDbName: String = "facultad_ciencias.db"
    private var activeDatabase: SQLiteDatabase? = null

    init {
        ensureInitialDatabase()
    }

    private fun ensureInitialDatabase() {
        val facultadFile = File(dbDir, "facultad_ciencias.db")
        if (!facultadFile.exists()) {
            createSampleFacultadCiencias(facultadFile)
            prefs.edit().putString("template_facultad_ciencias.db", "facultad_ciencias").apply()
        }
        val cubaFile = File(dbDir, "cuba_geografia.db")
        if (!cubaFile.exists()) {
            createSampleCuba(cubaFile)
            prefs.edit().putString("template_cuba_geografia.db", "cuba").apply()
        }
        val storeFile = File(dbDir, "tienda_comercial.db")
        if (!storeFile.exists()) {
            createSampleStore(storeFile)
            prefs.edit().putString("template_tienda_comercial.db", "sample").apply()
        }
    }

    @Synchronized
    fun getActiveDatabase(): SQLiteDatabase {
        val db = activeDatabase
        if (db != null && db.isOpen) {
            return db
        }
        val file = File(dbDir, currentDbName)
        val opened = SQLiteDatabase.openOrCreateDatabase(file, null)
        // Enable foreign key constraints in SQLite
        opened.execSQL("PRAGMA foreign_keys = ON;")
        activeDatabase = opened
        return opened
    }

    @Synchronized
    fun switchDatabase(name: String) {
        val normalized = if (name.endsWith(".db")) name else "$name.db"
        if (currentDbName == normalized && activeDatabase?.isOpen == true) {
            return
        }
        try {
            activeDatabase?.close()
        } catch (_: Exception) {}
        activeDatabase = null
        currentDbName = normalized
        val file = File(dbDir, currentDbName)
        if (!file.exists()) {
            createSampleDatabase(currentDbName)
        }
    }

    fun getCurrentDbName(): String = currentDbName

    fun listDatabases(): List<String> {
        val files = dbDir.listFiles { file -> file.isFile && (file.name.endsWith(".db") || file.name.endsWith(".sqlite")) }
        val names = files?.map { it.name }?.sorted() ?: emptyList()
        return if (names.isEmpty()) listOf(currentDbName) else names
    }

    fun createNewDatabase(name: String, template: String = "empty"): Boolean {
        val baseName = name.trim().removeSuffix(".db").removeSuffix(".sqlite").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val dbName = "$baseName.db"
        val file = File(dbDir, dbName)
        if (file.exists()) return false

        when (template) {
            "facultad_ciencias", "centro_estudiantil" -> createSampleFacultadCiencias(file)
            "cuba" -> createSampleCuba(file)
            "ecommerce" -> createSampleEcommerce(file)
            "university" -> createSampleUniversity(file)
            "projects" -> createSampleProjects(file)
            "clinic" -> createSampleClinic(file)
            "sample" -> createSampleStore(file)
            else -> {
                val db = SQLiteDatabase.openOrCreateDatabase(file, null)
                db.execSQL("PRAGMA foreign_keys = ON;")
                db.close()
            }
        }
        prefs.edit().putString("template_$dbName", template).apply()
        switchDatabase(dbName)
        return true
    }

    @Synchronized
    fun resetDatabase(name: String): Boolean {
        val baseName = name.trim().removeSuffix(".db").removeSuffix(".sqlite").replace(Regex("[^a-zA-Z0-9_]"), "_")
        val dbName = "$baseName.db"
        val file = File(dbDir, dbName)
        val wasActive = (currentDbName == dbName)

        if (wasActive) {
            try {
                activeDatabase?.close()
            } catch (_: Exception) {}
            activeDatabase = null
        }

        // Determine template from saved prefs or name heuristics
        val storedTemplate = prefs.getString("template_$dbName", null)
        val template = storedTemplate ?: when {
            dbName.contains("facultad", ignoreCase = true) || dbName.contains("centro", ignoreCase = true) || dbName.contains("estudiantil", ignoreCase = true) || dbName.contains("ciencia", ignoreCase = true) -> "facultad_ciencias"
            dbName.contains("cuba", ignoreCase = true) -> "cuba"
            dbName.contains("ecommerce", ignoreCase = true) -> "ecommerce"
            dbName.contains("universidad", ignoreCase = true) || dbName.contains("university", ignoreCase = true) -> "university"
            dbName.contains("proyecto", ignoreCase = true) || dbName.contains("project", ignoreCase = true) -> "projects"
            dbName.contains("clinica", ignoreCase = true) || dbName.contains("clinic", ignoreCase = true) -> "clinic"
            dbName.startsWith("base_", ignoreCase = true) -> "empty"
            dbName.contains("mi_base_de_datos", ignoreCase = true) -> "sample"
            else -> "facultad_ciencias"
        }

        if (file.exists()) {
            file.delete()
        }

        when (template) {
            "facultad_ciencias", "centro_estudiantil" -> createSampleFacultadCiencias(file)
            "cuba" -> createSampleCuba(file)
            "ecommerce" -> createSampleEcommerce(file)
            "university" -> createSampleUniversity(file)
            "projects" -> createSampleProjects(file)
            "clinic" -> createSampleClinic(file)
            "sample" -> createSampleStore(file)
            else -> {
                val db = SQLiteDatabase.openOrCreateDatabase(file, null)
                db.execSQL("PRAGMA foreign_keys = ON;")
                db.close()
            }
        }

        if (wasActive) {
            switchDatabase(dbName)
        }
        return true
    }

    fun deleteDatabase(name: String): Boolean {
        val file = File(dbDir, name)
        if (currentDbName == name) {
            try {
                activeDatabase?.close()
            } catch (_: Exception) {}
            activeDatabase = null
        }
        val deleted = file.delete()
        if (deleted) {
            prefs.edit().remove("template_$name").apply()
        }
        val remaining = listDatabases()
        if (remaining.isNotEmpty()) {
            switchDatabase(remaining.first())
        } else {
            val defaultFile = File(dbDir, "facultad_ciencias.db")
            createSampleFacultadCiencias(defaultFile)
            switchDatabase("facultad_ciencias.db")
        }
        return deleted
    }

    fun renameDatabase(oldName: String, newName: String): Boolean {
        val clean = newName.trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
        val target = if (clean.endsWith(".db")) clean else "$clean.db"
        val oldFile = File(dbDir, oldName)
        val newFile = File(dbDir, target)
        if (!oldFile.exists() || newFile.exists()) return false

        if (currentDbName == oldName) {
            try {
                activeDatabase?.close()
            } catch (_: Exception) {}
            activeDatabase = null
        }
        val renamed = oldFile.renameTo(newFile)
        if (renamed) {
            val stored = prefs.getString("template_$oldName", null)
            if (stored != null) {
                prefs.edit().putString("template_$target", stored).remove("template_$oldName").apply()
            }
            if (currentDbName == oldName) {
                switchDatabase(target)
            }
        }
        return renamed
    }

    suspend fun executeSql(sql: String): SqlExecutionResult = withContext(Dispatchers.IO) {
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) {
            return@withContext SqlExecutionResult(
                isSuccess = false,
                message = "La consulta SQL está vacía.",
                querySql = sql
            )
        }

        val startTime = System.nanoTime()
        val db = getActiveDatabase()

        // Robust parsing of statements taking quotes and comments into account
        val statements = SqlScriptHelper.splitScriptIntoStatements(trimmed)

        if (statements.isEmpty()) {
            return@withContext SqlExecutionResult(
                isSuccess = false,
                message = "El script no contiene sentencias SQL ejecutables.",
                querySql = sql
            )
        }

        if (statements.size > 1) {
            // Multi-statement execution inside a transaction
            return@withContext executeMultipleStatements(db, statements, sql, startTime)
        }

        // Single statement
        val singleSql = statements.first()
        val upper = singleSql.uppercase().trimStart()

        val isQuery = upper.startsWith("SELECT") ||
                upper.startsWith("PRAGMA") ||
                upper.startsWith("EXPLAIN") ||
                upper.startsWith("WITH")

        try {
            if (isQuery) {
                val cursor = db.rawQuery(singleSql, null)
                cursor.use { c ->
                    val columnCount = c.columnCount
                    val columns = (0 until columnCount).map { c.getColumnName(it) }
                    val rows = mutableListOf<List<String>>()
                    val maxRows = 1000 // safe display limit

                    var count = 0
                    while (c.moveToNext() && count < maxRows) {
                        val row = mutableListOf<String>()
                        for (i in 0 until columnCount) {
                            val value = when (c.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> "NULL"
                                Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
                                Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toString()
                                Cursor.FIELD_TYPE_STRING -> c.getString(i) ?: ""
                                Cursor.FIELD_TYPE_BLOB -> "[BLOB ${c.getBlob(i).size} bytes]"
                                else -> c.getString(i) ?: ""
                            }
                            row.add(value)
                        }
                        rows.add(row)
                        count++
                    }

                    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                    val totalRows = c.count
                    val message = if (totalRows > maxRows) {
                        "Ejecutado con éxito en ${elapsedMs} ms ($totalRows filas totales, mostrando las primeras $maxRows)"
                    } else {
                        "Ejecutado con éxito en ${elapsedMs} ms ($totalRows filas devueltas)"
                    }

                    SqlExecutionResult(
                        isSuccess = true,
                        message = message,
                        columns = columns,
                        rows = rows,
                        affectedRows = totalRows,
                        elapsedMs = elapsedMs,
                        querySql = singleSql,
                        isQuery = true
                    )
                }
            } else {
                // Non-query DDL / DML
                var affected = 0
                if (upper.startsWith("UPDATE") || upper.startsWith("DELETE")) {
                    val stmt = db.compileStatement(singleSql)
                    affected = stmt.executeUpdateDelete()
                } else if (upper.startsWith("INSERT") || upper.startsWith("REPLACE")) {
                    val stmt = db.compileStatement(singleSql)
                    val id = stmt.executeInsert()
                    affected = if (id != -1L) 1 else 0
                } else {
                    db.execSQL(singleSql)
                    affected = 1
                }

                val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
                val message = when {
                    upper.startsWith("CREATE") -> "Tabla u objeto creado exitosamente en ${elapsedMs} ms."
                    upper.startsWith("DROP") -> "Tabla eliminada exitosamente en ${elapsedMs} ms."
                    upper.startsWith("ALTER") -> "Estructura modificada exitosamente en ${elapsedMs} ms."
                    upper.startsWith("REPLACE") -> "Registro reemplazado/insertado exitosamente ($affected filas afectadas) en ${elapsedMs} ms."
                    upper.startsWith("INSERT") -> "Registro insertado exitosamente ($affected filas afectadas) en ${elapsedMs} ms."
                    upper.startsWith("UPDATE") -> "$affected registros actualizados en ${elapsedMs} ms."
                    upper.startsWith("DELETE") -> "$affected registros eliminados en ${elapsedMs} ms."
                    else -> "Sentencia ejecutada exitosamente en ${elapsedMs} ms."
                }

                SqlExecutionResult(
                    isSuccess = true,
                    message = message,
                    affectedRows = affected,
                    elapsedMs = elapsedMs,
                    querySql = singleSql,
                    isQuery = false
                )
            }
        } catch (e: SQLiteException) {
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            SqlExecutionResult(
                isSuccess = false,
                message = "Error SQLite: ${e.localizedMessage ?: e.message}",
                elapsedMs = elapsedMs,
                querySql = singleSql,
                isQuery = isQuery
            )
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            SqlExecutionResult(
                isSuccess = false,
                message = "Error de ejecución: ${e.localizedMessage ?: e.message}",
                elapsedMs = elapsedMs,
                querySql = singleSql,
                isQuery = isQuery
            )
        }
    }

    private fun executeMultipleStatements(
        db: SQLiteDatabase,
        statements: List<String>,
        fullSql: String,
        startTime: Long
    ): SqlExecutionResult {
        var executedCount = 0
        val createdTables = SqlScriptHelper.extractCreatedTableNames(fullSql)
        try {
            db.beginTransaction()
            for (stmt in statements) {
                db.execSQL(stmt)
                executedCount++
            }
            db.setTransactionSuccessful()
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            val tableMsg = if (createdTables.isNotEmpty()) {
                " (Tablas creadas: ${createdTables.joinToString(", ")})"
            } else ""

            return SqlExecutionResult(
                isSuccess = true,
                message = "Se ejecutaron $executedCount sentencias correctamente en ${elapsedMs} ms$tableMsg.",
                affectedRows = executedCount,
                elapsedMs = elapsedMs,
                querySql = fullSql,
                isQuery = false
            )
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            return SqlExecutionResult(
                isSuccess = false,
                message = "Fallo en la sentencia ${executedCount + 1}: ${e.localizedMessage ?: e.message}",
                elapsedMs = elapsedMs,
                querySql = fullSql,
                isQuery = false
            )
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    suspend fun executeSqlScript(script: String): ScriptExecutionSummary = withContext(Dispatchers.IO) {
        val db = getActiveDatabase()
        SqlScriptHelper.executeSqlScript(db, script)
    }

    suspend fun getTables(): List<TableSummary> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TableSummary>()
        val db = getActiveDatabase()
        try {
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_metadata' ORDER BY name;",
                null
            )
            cursor.use { c ->
                while (c.moveToNext()) {
                    val tableName = c.getString(0)
                    var count = 0L
                    var colCount = 0
                    try {
                        val countCursor = db.rawQuery("SELECT COUNT(*) FROM \"$tableName\"", null)
                        countCursor.use { cc ->
                            if (cc.moveToFirst()) count = cc.getLong(0)
                        }
                    } catch (_: Exception) {}

                    try {
                        val infoCursor = db.rawQuery("PRAGMA table_info(\"$tableName\");", null)
                        infoCursor.use { ic ->
                            colCount = ic.count
                        }
                    } catch (_: Exception) {}

                    list.add(TableSummary(name = tableName, rowCount = count, columnCount = colCount))
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun getTableSchema(tableName: String): TableSchemaDetail = withContext(Dispatchers.IO) {
        val db = getActiveDatabase()
        val columns = mutableListOf<ColumnInfo>()
        val foreignKeys = mutableListOf<ForeignKeyInfo>()
        val indexes = mutableListOf<String>()
        var createSql = ""

        try {
            // Columns
            val colCursor = db.rawQuery("PRAGMA table_info(\"$tableName\");", null)
            colCursor.use { c ->
                while (c.moveToNext()) {
                    columns.add(
                        ColumnInfo(
                            cid = c.getInt(0),
                            name = c.getString(1),
                            type = c.getString(2),
                            notNull = c.getInt(3) == 1,
                            defaultValue = c.getString(4),
                            isPrimaryKey = c.getInt(5) > 0
                        )
                    )
                }
            }

            // Foreign Keys
            val fkCursor = db.rawQuery("PRAGMA foreign_key_list(\"$tableName\");", null)
            fkCursor.use { c ->
                while (c.moveToNext()) {
                    foreignKeys.add(
                        ForeignKeyInfo(
                            id = c.getInt(0),
                            fromColumn = c.getString(3),
                            targetTable = c.getString(2),
                            toColumn = c.getString(4) ?: "id"
                        )
                    )
                }
            }

            // Indexes
            val idxCursor = db.rawQuery("PRAGMA index_list(\"$tableName\");", null)
            idxCursor.use { c ->
                while (c.moveToNext()) {
                    indexes.add(c.getString(1))
                }
            }

            // Create SQL
            val sqlCursor = db.rawQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name=?;", arrayOf(tableName))
            sqlCursor.use { c ->
                if (c.moveToFirst()) {
                    createSql = c.getString(0) ?: ""
                }
            }
        } catch (_: Exception) {}

        TableSchemaDetail(
            tableName = tableName,
            columns = columns,
            foreignKeys = foreignKeys,
            indexes = indexes,
            createSql = createSql
        )
    }

    suspend fun getAllTablesSchema(): List<TableSchemaDetail> = withContext(Dispatchers.IO) {
        val tables = getTables()
        tables.map { getTableSchema(it.name) }
    }

    suspend fun insertTableRow(tableName: String, values: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        val db = getActiveDatabase()
        val nonNullEntries = values.filter { it.value.isNotBlank() }
        if (nonNullEntries.isEmpty()) {
            return@withContext try {
                db.execSQL("INSERT INTO \"$tableName\" DEFAULT VALUES;")
                true
            } catch (_: Exception) { false }
        }

        val cols = nonNullEntries.keys.joinToString(", ") { "\"$it\"" }
        val placeholders = nonNullEntries.keys.joinToString(", ") { "?" }
        val args = nonNullEntries.values.toTypedArray()

        try {
            db.execSQL("INSERT INTO \"$tableName\" ($cols) VALUES ($placeholders);", args)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteTableRow(tableName: String, pkCol: String, pkVal: String): Boolean = withContext(Dispatchers.IO) {
        val db = getActiveDatabase()
        try {
            db.execSQL("DELETE FROM \"$tableName\" WHERE \"$pkCol\" = ?;", arrayOf(pkVal))
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteMultipleRows(tableName: String, pkCol: String, pkValues: List<String>): SqlExecutionResult = withContext(Dispatchers.IO) {
        if (pkValues.isEmpty()) {
            return@withContext SqlExecutionResult(isSuccess = true, message = "No se seleccionaron filas.")
        }
        val db = getActiveDatabase()
        try {
            val placeholders = pkValues.joinToString(", ") { "?" }
            val count = db.delete(tableName, "\"$pkCol\" IN ($placeholders)", pkValues.toTypedArray())
            SqlExecutionResult(
                isSuccess = true,
                message = "Se eliminaron $count fila(s) de '$tableName'.",
                affectedRows = count
            )
        } catch (e: Exception) {
            SqlExecutionResult(
                isSuccess = false,
                message = "Error al eliminar filas: ${e.message}"
            )
        }
    }

    suspend fun getReferencedTableOptions(targetTable: String, targetCol: String, limit: Int = 100): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val db = getActiveDatabase()
        val list = mutableListOf<Pair<String, String>>()
        try {
            val cursor = db.rawQuery("SELECT * FROM \"$targetTable\" LIMIT $limit;", null)
            cursor.use { c ->
                val colNames = c.columnNames.toList()
                val targetColIndex = colNames.indexOf(targetCol).takeIf { it >= 0 } ?: 0
                val labelIndex = colNames.indexOfFirst {
                    it != targetCol && (it.contains("nom", ignoreCase = true) || it.contains("name", ignoreCase = true) || it.contains("tit", ignoreCase = true) || it.contains("desc", ignoreCase = true))
                }.takeIf { it >= 0 } ?: colNames.indexOfFirst { it != targetCol }.takeIf { it >= 0 } ?: targetColIndex

                while (c.moveToNext()) {
                    val idVal = c.getString(targetColIndex) ?: ""
                    val labelVal = if (labelIndex != targetColIndex) c.getString(labelIndex) ?: "" else ""
                    val display = if (labelVal.isNotBlank() && labelVal != idVal) "$idVal • $labelVal" else idVal
                    if (idVal.isNotBlank()) {
                        list.add(Pair(idVal, display))
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun updateTableRow(tableName: String, pkCol: String, pkVal: String, values: Map<String, String>): Boolean = withContext(Dispatchers.IO) {
        val db = getActiveDatabase()
        val setClauses = values.keys.filter { it != pkCol }.joinToString(", ") { "\"$it\" = ?" }
        val args = values.filter { it.key != pkCol }.values.toMutableList()
        args.add(pkVal)

        try {
            db.execSQL("UPDATE \"$tableName\" SET $setClauses WHERE \"$pkCol\" = ?;", args.toTypedArray())
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun dropTable(tableName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getActiveDatabase().execSQL("DROP TABLE IF EXISTS \"$tableName\";")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun clearTable(tableName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            getActiveDatabase().execSQL("DELETE FROM \"$tableName\";")
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun addColumn(tableName: String, columnName: String, columnType: String, isNotNull: Boolean = false, defaultValue: String = ""): SqlExecutionResult = withContext(Dispatchers.IO) {
        val cleanCol = columnName.trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
        if (cleanCol.isBlank()) {
            return@withContext SqlExecutionResult(isSuccess = false, message = "El nombre de la columna no puede estar vacío.")
        }
        val type = if (columnType.isBlank()) "TEXT" else columnType.uppercase().trim()
        val notNullClause = if (isNotNull && defaultValue.isNotBlank()) " NOT NULL DEFAULT $defaultValue" else if (defaultValue.isNotBlank()) " DEFAULT $defaultValue" else ""
        val sql = "ALTER TABLE \"$tableName\" ADD COLUMN \"$cleanCol\" $type$notNullClause;"
        return@withContext executeSql(sql)
    }

    suspend fun dropColumn(tableName: String, columnName: String): SqlExecutionResult = withContext(Dispatchers.IO) {
        val db = getActiveDatabase()
        // 1. Try native SQLite 3.35+ DROP COLUMN syntax
        try {
            db.execSQL("ALTER TABLE \"$tableName\" DROP COLUMN \"$columnName\";")
            return@withContext SqlExecutionResult(
                isSuccess = true,
                message = "Columna '$columnName' eliminada exitosamente de '$tableName'."
            )
        } catch (e: Exception) {
            // 2. Fallback: Transactional table rebuild procedure for earlier SQLite engines or complex schemas
            try {
                val schema = getTableSchema(tableName)
                val remainingCols = schema?.columns?.filter { !it.name.equals(columnName, ignoreCase = true) } ?: emptyList()
                if (remainingCols.isEmpty()) {
                    return@withContext SqlExecutionResult(
                        isSuccess = false,
                        message = "No se puede eliminar la columna: la tabla debe conservar al menos una columna."
                    )
                }

                db.beginTransaction()
                val tempTable = "_temp_${tableName}_${System.currentTimeMillis()}"
                val colDefs = remainingCols.map { col ->
                    val sb = StringBuilder("\"${col.name}\" ${col.type.ifBlank { "TEXT" }}")
                    if (col.isPrimaryKey) sb.append(" PRIMARY KEY")
                    if (col.notNull && !col.isPrimaryKey) sb.append(" NOT NULL")
                    if (!col.defaultValue.isNullOrBlank()) sb.append(" DEFAULT ${col.defaultValue}")
                    sb.toString()
                }.joinToString(", ")

                val remainingNamesEscaped = remainingCols.joinToString(", ") { "\"${it.name}\"" }

                db.execSQL("CREATE TABLE \"$tempTable\" ($colDefs);")
                db.execSQL("INSERT INTO \"$tempTable\" ($remainingNamesEscaped) SELECT $remainingNamesEscaped FROM \"$tableName\";")
                db.execSQL("DROP TABLE \"$tableName\";")
                db.execSQL("ALTER TABLE \"$tempTable\" RENAME TO \"$tableName\";")
                db.setTransactionSuccessful()
                db.endTransaction()

                SqlExecutionResult(
                    isSuccess = true,
                    message = "Columna '$columnName' eliminada exitosamente de '$tableName'."
                )
            } catch (ex: Exception) {
                try {
                    if (db.inTransaction()) db.endTransaction()
                } catch (_: Exception) {}
                SqlExecutionResult(
                    isSuccess = false,
                    message = "Error al eliminar columna: ${ex.message}"
                )
            }
        }
    }

    suspend fun importDatabaseFromUri(uri: Uri, newName: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val baseName = newName?.trim()?.ifEmpty { null }
                ?: (uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "imported_${System.currentTimeMillis()}.db")
            val cleanName = if (baseName.endsWith(".db") || baseName.endsWith(".sqlite")) baseName else "$baseName.db"
            val targetFile = File(dbDir, cleanName)

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("No se pudo leer el archivo seleccionado."))

            // Verify SQLite header
            val header = ByteArray(16)
            FileInputStream(targetFile).use { it.read(header) }
            val headerStr = String(header)
            if (!headerStr.startsWith("SQLite format 3")) {
                targetFile.delete()
                return@withContext Result.failure(Exception("El archivo no es una base de datos SQLite 3 válida."))
            }

            switchDatabase(cleanName)
            Result.success(cleanName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportDatabaseToStream(outputStream: OutputStream): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(dbDir, currentDbName)
            FileInputStream(file).use { input ->
                input.copyTo(outputStream)
            }
            outputStream.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun exportQueryResultCsv(result: SqlExecutionResult): String = withContext(Dispatchers.Default) {
        val sb = StringBuilder()
        // Headers
        sb.append(result.columns.joinToString(",") { escapeCsv(it) }).append("\n")
        // Rows
        for (row in result.rows) {
            sb.append(row.joinToString(",") { escapeCsv(it) }).append("\n")
        }
        sb.toString()
    }

    suspend fun exportQueryResultJson(result: SqlExecutionResult): String = withContext(Dispatchers.Default) {
        val jsonArray = JSONArray()
        for (row in result.rows) {
            val obj = JSONObject()
            result.columns.forEachIndexed { index, colName ->
                val value = row.getOrNull(index) ?: ""
                obj.put(colName, value)
            }
            jsonArray.put(obj)
        }
        jsonArray.toString(2)
    }

    suspend fun exportDatabaseSqlDump(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        sb.append("-- MiniSQL DUMP: $currentDbName\n")
        sb.append("-- Generado el ${java.util.Date()}\n")
        sb.append("PRAGMA foreign_keys=OFF;\nBEGIN TRANSACTION;\n\n")

        val db = getActiveDatabase()
        val tables = getTables()
        for (t in tables) {
            val schema = getTableSchema(t.name)
            if (schema.createSql.isNotBlank()) {
                val safeCreate = if (!schema.createSql.contains("IF NOT EXISTS", ignoreCase = true)) {
                    schema.createSql.replaceFirst(Regex("""(?i)CREATE\s+TABLE\s+"""), "CREATE TABLE IF NOT EXISTS ")
                } else schema.createSql
                sb.append("$safeCreate;\n")
            }
            // Dump rows with INSERT OR IGNORE INTO for idempotent execution
            val cursor = db.rawQuery("SELECT * FROM \"${t.name}\"", null)
            cursor.use { c ->
                val colCount = c.columnCount
                while (c.moveToNext()) {
                    val values = (0 until colCount).map { i ->
                        when (c.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> "NULL"
                            Cursor.FIELD_TYPE_INTEGER -> c.getLong(i).toString()
                            Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i).toString()
                            Cursor.FIELD_TYPE_STRING -> "'${c.getString(i).replace("'", "''")}'"
                            else -> "'${c.getString(i).replace("'", "''")}'"
                        }
                    }
                    sb.append("INSERT OR IGNORE INTO \"${t.name}\" VALUES (${values.joinToString(", ")});\n")
                }
            }
            sb.append("\n")
        }
        sb.append("COMMIT;\nPRAGMA foreign_keys=ON;\n")
        sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun createSampleDatabase(fileName: String) {
        val file = File(dbDir, fileName)
        createSampleStore(file)
    }

    private fun createSampleStore(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS clientes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    email TEXT UNIQUE,
                    ciudad TEXT,
                    saldo REAL DEFAULT 0.0
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS productos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    categoria TEXT,
                    precio REAL NOT NULL,
                    stock INTEGER DEFAULT 0
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS pedidos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    cliente_id INTEGER,
                    fecha TEXT NOT NULL,
                    estado TEXT DEFAULT 'Pendiente',
                    total REAL,
                    FOREIGN KEY (cliente_id) REFERENCES clientes(id) ON DELETE CASCADE
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS detalles_pedido (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pedido_id INTEGER,
                    producto_id INTEGER,
                    cantidad INTEGER NOT NULL,
                    precio_unitario REAL NOT NULL,
                    FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE CASCADE,
                    FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE RESTRICT
                );
            """.trimIndent())

            // Initial records (idempotent with INSERT OR IGNORE)
            db.execSQL("""
                INSERT OR IGNORE INTO clientes (id, nombre, email, ciudad, saldo) VALUES
                (1, 'Ana Torres', 'ana@example.com', 'Madrid', 450.50),
                (2, 'Carlos Ruiz', 'carlos@example.com', 'Barcelona', 120.00),
                (3, 'Lucía Méndez', 'lucia@example.com', 'Valencia', 890.25),
                (4, 'Eduardo Sanamé', 'eduardo@example.com', 'La Habana', 1250.00),
                (5, 'Beatriz Gómez', 'beatriz@example.com', 'Sevilla', 310.75),
                (6, 'Javier Delgado', 'javier@example.com', 'Bilbao', 65.20),
                (7, 'Camila Fonseca', 'camila@example.com', 'Santiago de Cuba', 540.00),
                (8, 'Marcos Alarcón', 'marcos@example.com', 'Málaga', 780.90);
            """.trimIndent())

            db.execSQL("""
                INSERT OR IGNORE INTO productos (id, nombre, categoria, precio, stock) VALUES
                (1, 'Laptop Pro 15', 'Electrónica', 1199.99, 12),
                (2, 'Mouse Inalámbrico', 'Accesorios', 24.50, 45),
                (3, 'Monitor 4K HDR', 'Electrónica', 349.00, 8),
                (4, 'Teclado Mecánico RGB', 'Accesorios', 79.99, 25),
                (5, 'Auriculares Cancelación Ruido', 'Audio', 189.00, 18),
                (6, 'Webcam UltraHD 60fps', 'Video', 99.50, 30),
                (7, 'Disco SSD NVMe 2TB', 'Almacenamiento', 145.00, 40),
                (8, 'Silla Ergonómica Pro', 'Muebles', 229.00, 10),
                (9, 'Cable USB-C Blindado 2m', 'Cables', 12.99, 100),
                (10, 'Hub USB Multi-Puerto 8-en-1', 'Accesorios', 49.90, 22);
            """.trimIndent())

            db.execSQL("""
                INSERT OR IGNORE INTO pedidos (id, cliente_id, fecha, estado, total) VALUES
                (1, 1, '2026-10-18', 'Entregado', 1224.49),
                (2, 2, '2026-10-21', 'En proceso', 349.00),
                (3, 3, '2026-10-24', 'Entregado', 268.99),
                (4, 4, '2026-10-25', 'Enviado', 1344.99),
                (5, 5, '2026-10-27', 'Entregado', 124.40),
                (6, 6, '2026-10-28', 'Pendiente', 49.90),
                (7, 7, '2026-10-29', 'Enviado', 538.00),
                (8, 8, '2026-10-30', 'Entregado', 229.00);
            """.trimIndent())

            db.execSQL("""
                INSERT OR IGNORE INTO detalles_pedido (pedido_id, producto_id, cantidad, precio_unitario) VALUES
                (1, 1, 1, 1199.99),
                (1, 2, 1, 24.50),
                (2, 3, 1, 349.00),
                (3, 4, 1, 79.99),
                (3, 5, 1, 189.00),
                (4, 1, 1, 1199.99),
                (4, 7, 1, 145.00),
                (5, 2, 2, 24.50),
                (5, 9, 2, 12.99),
                (5, 10, 1, 49.90),
                (6, 10, 1, 49.90),
                (7, 3, 1, 349.00),
                (7, 5, 1, 189.00),
                (8, 8, 1, 229.00);
            """.trimIndent())
        } finally {
            db.close()
        }
    }

    private fun createSampleCuba(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS provincias (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL UNIQUE,
                    capital TEXT NOT NULL,
                    region TEXT NOT NULL,
                    poblacion INTEGER NOT NULL,
                    area_km2 REAL NOT NULL
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS lugares_interes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    provincia_id INTEGER NOT NULL,
                    nombre TEXT NOT NULL,
                    tipo TEXT NOT NULL,
                    patrimonio_unesco INTEGER DEFAULT 0,
                    FOREIGN KEY (provincia_id) REFERENCES provincias(id) ON DELETE CASCADE
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS platos_tradicionales (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    tipo TEXT NOT NULL,
                    ingrediente_principal TEXT NOT NULL,
                    region_origen TEXT NOT NULL
                );
            """.trimIndent())

            // Las 15 provincias y el municipio especial de Cuba
            db.execSQL("""
                INSERT OR IGNORE INTO provincias (id, nombre, capital, region, poblacion, area_km2) VALUES
                (1, 'Pinar del Río', 'Pinar del Río', 'Occidental', 583000, 8884.5),
                (2, 'Artemisa', 'Artemisa', 'Occidental', 514000, 4004.2),
                (3, 'La Habana', 'La Habana', 'Occidental', 2137000, 728.3),
                (4, 'Mayabeque', 'San José de las Lajas', 'Occidental', 384000, 3732.7),
                (5, 'Matanzas', 'Matanzas', 'Occidental', 716000, 11798.6),
                (6, 'Cienfuegos', 'Cienfuegos', 'Central', 406000, 4186.6),
                (7, 'Villa Clara', 'Santa Clara', 'Central', 773000, 8413.1),
                (8, 'Sancti Spíritus', 'Sancti Spíritus', 'Central', 465000, 6777.3),
                (9, 'Ciego de Ávila', 'Ciego de Ávila', 'Central', 435000, 6910.4),
                (10, 'Camagüey', 'Camagüey', 'Central', 767000, 15386.2),
                (11, 'Las Tunas', 'Las Tunas', 'Oriental', 533000, 6595.3),
                (12, 'Holguín', 'Holguín', 'Oriental', 1022000, 9215.8),
                (13, 'Granma', 'Bayamo', 'Oriental', 819000, 8376.8),
                (14, 'Santiago de Cuba', 'Santiago de Cuba', 'Oriental', 1045000, 6234.2),
                (15, 'Guantánamo', 'Guantánamo', 'Oriental', 506000, 6186.2),
                (16, 'Isla de la Juventud', 'Nueva Gerona', 'Especial', 83000, 2419.3);
            """.trimIndent())

            // Lugares de interés patrimoniales, naturales y culturales
            db.execSQL("""
                INSERT OR IGNORE INTO lugares_interes (provincia_id, nombre, tipo, patrimonio_unesco) VALUES
                (3, 'El Capitolio Nacional', 'Monumento Nacional', 0),
                (3, 'Castillo de los Tres Reyes del Morro', 'Fortaleza Colonial', 1),
                (3, 'La Habana Vieja y sus Fortificaciones', 'Centro Histórico', 1),
                (1, 'Valle de Viñales', 'Paisaje Cultural / Parque Nacional', 1),
                (1, 'Cueva del Indio', 'Espeleológico y Natural', 0),
                (5, 'Balneario de Varadero', 'Playa y Polo Turístico', 0),
                (5, 'Cuevas de Bellamar', 'Monumento Nacional Espeleológico', 0),
                (6, 'Centro Histórico de Cienfuegos', 'Arquitectura Colonial Neoclásica', 1),
                (6, 'Palacio de Valle', 'Monumento Arquitectónico', 0),
                (7, 'Monumento y Memorial Che Guevara', 'Sitio Histórico', 0),
                (7, 'Cayo Santa María', 'Reserva Turística Costera', 0),
                (8, 'Trinidad y Valle de los Ingenios', 'Ciudad Museo Colonial', 1),
                (9, 'Jardines del Rey (Cayo Coco y Guillermo)', 'Parque Natural y Marino', 0),
                (10, 'Centro Histórico de Camagüey', 'Ciudad Colonial', 1),
                (12, 'Playa Guardalavaca', 'Playa y Arrecifes', 0),
                (12, 'Parque Nacional Alejandro de Humboldt', 'Biodiversidad del Caribe', 1),
                (13, 'Parque Nacional Desembarco del Granma', 'Monumento Natural y Marino', 1),
                (14, 'Castillo de San Pedro de la Roca', 'Fortaleza Militar', 1),
                (14, 'Basílica Santuario de El Cobre', 'Santuario Histórico y Religioso', 0),
                (15, 'Viaducto La Farola', 'Maravilla de la Ingeniería Civil', 0),
                (15, 'Yunque de Baracoa', 'Monumento Nacional', 0);
            """.trimIndent())

            // Platos y delicias gastronómicas tradicionales de Cuba
            db.execSQL("""
                INSERT OR IGNORE INTO platos_tradicionales (nombre, tipo, ingrediente_principal, region_origen) VALUES
                ('Ropa Vieja', 'Plato Fuerte', 'Carne de Res Deshebrada', 'Nacional'),
                ('Ajiaco Criollo', 'Guiso Tradicional', 'Viandas surtidas y carnes', 'Camagüey'),
                ('Moros y Cristianos', 'Guarnición', 'Arroz y Frijoles Negros', 'Occidental'),
                ('Congrí Oriental', 'Guarnición', 'Arroz y Frijoles Colorados', 'Oriental'),
                ('Lechón Asado en Púa', 'Plato Fuerte', 'Cerdo criollo marinado en mojo', 'Nacional'),
                ('Yuca con Mojo Criollo', 'Acompañamiento', 'Yuca hervida con ajo y naranja agria', 'Nacional'),
                ('Tostones Rellenos', 'Entrante', 'Plátano verde frito con picadillo', 'Nacional'),
                ('Tamal en Cazuela', 'Plato Tradicional', 'Maíz tierno rallado y cerdo', 'Central'),
                ('Cucurucho de Baracoa', 'Postre Tradicional', 'Coco rallado, miel y frutas en yagua', 'Guantánamo'),
                ('Casabe', 'Pan Tradicional Ancestral', 'Harina de Yuca tostada', 'Oriental');
            """.trimIndent())
        } finally {
            db.close()
        }
    }

    private fun createSampleEcommerce(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS usuarios (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    usuario TEXT UNIQUE NOT NULL,
                    rol TEXT DEFAULT 'cliente',
                    activo INTEGER DEFAULT 1
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS categorias (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS articulos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    categoria_id INTEGER,
                    nombre TEXT NOT NULL,
                    precio REAL NOT NULL,
                    FOREIGN KEY (categoria_id) REFERENCES categorias(id)
                );
            """.trimIndent())

            db.execSQL("INSERT OR IGNORE INTO usuarios (usuario, rol) VALUES ('admin_root', 'administrador'), ('usuario1', 'cliente'), ('invitado', 'cliente');")
            db.execSQL("INSERT OR IGNORE INTO categorias (nombre) VALUES ('Hardware'), ('Software'), ('Servicios');")
            db.execSQL("INSERT OR IGNORE INTO articulos (categoria_id, nombre, precio) VALUES (1, 'Memoria RAM 32GB', 115.0), (1, 'Disco NVMe 1TB', 85.5), (2, 'Licencia IDE Pro', 199.0);")
        } finally {
            db.close()
        }
    }

    private fun createSampleFacultadCiencias(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")

            // 1. Tabla de Profesores (12 registros con Prof. Eduardo Sanamé Durán)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS profesores (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    especialidad TEXT NOT NULL,
                    email TEXT UNIQUE NOT NULL,
                    departamento TEXT NOT NULL
                );
            """.trimIndent())

            // 2. Tabla de Materias (12 registros: Informática, Datos, Física, Matemática y Ciencias Avanzadas)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS materias (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    codigo TEXT UNIQUE NOT NULL,
                    creditos INTEGER NOT NULL,
                    semestre INTEGER NOT NULL,
                    profesor_id INTEGER,
                    FOREIGN KEY (profesor_id) REFERENCES profesores(id) ON DELETE SET NULL
                );
            """.trimIndent())

            // 3. Tabla de Estudiantes (12 registros relacionados)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS estudiantes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    matricula TEXT UNIQUE NOT NULL,
                    materia_id INTEGER,
                    promedio REAL NOT NULL,
                    ano_carrera INTEGER NOT NULL,
                    FOREIGN KEY (materia_id) REFERENCES materias(id) ON DELETE CASCADE
                );
            """.trimIndent())

            // Inserción de 12 Profesores
            db.execSQL("""
                INSERT OR IGNORE INTO profesores (id, nombre, especialidad, email, departamento) VALUES
                (1, 'Prof. Eduardo Sanamé Durán', 'Ciencias de Datos e Inteligencia Artificial', 'eduardo.saname@facultadciencias.edu', 'Informática y Computación'),
                (2, 'Dra. Celia Méndez Batista', 'Física Cuántica y Mecánica Teórica', 'celia.mendez@facultadciencias.edu', 'Ciencias Exactas'),
                (3, 'Dr. Alejandro Morales Peña', 'Matemática Discreta y Álgebra Superior', 'alejandro.morales@facultadciencias.edu', 'Matemáticas'),
                (4, 'Ing. Roberto Navarro Silva', 'Ciberseguridad y Criptografía Aplicada', 'roberto.navarro@facultadciencias.edu', 'Ciberseguridad'),
                (5, 'Dra. Mariana Valdés Cruz', 'Bioinformática y Genómica Computacional', 'mariana.valdes@facultadciencias.edu', 'Biotecnología'),
                (6, 'Dr. Ernesto Fonseca Reyes', 'Robótica Autónoma y Sistemas Embebidos', 'ernesto.fonseca@facultadciencias.edu', 'Mecatrónica'),
                (7, 'MSc. Beatriz Quintana León', 'Informática y Algoritmos Complejos', 'beatriz.quintana@facultadciencias.edu', 'Informática y Computación'),
                (8, 'Dr. Carlos Daniel Osorio', 'Computación Cuántica y Nanociencias', 'carlos.osorio@facultadciencias.edu', 'Física Avanzada'),
                (9, 'Dra. Yanet Salgado Padrón', 'Astrofísica y Modelado Planetario', 'yanet.salgado@facultadciencias.edu', 'Astronomía y Física'),
                (10, 'Dr. Manuel Carballo Prieto', 'Redes Neuronales y Aprendizaje Profundo', 'manuel.carballo@facultadciencias.edu', 'Inteligencia Artificial'),
                (11, 'MSc. Dayana Fuentes Rivas', 'Ingeniería de Software y Arquitectura Cloud', 'dayana.fuentes@facultadciencias.edu', 'Informática y Computación'),
                (12, 'Dr. Guillermo Toledo Soto', 'Optimización Numérica y Métodos Cuantitativos', 'guillermo.toledo@facultadciencias.edu', 'Matemáticas');
            """.trimIndent())

            // Inserción de 12 Materias
            db.execSQL("""
                INSERT OR IGNORE INTO materias (id, nombre, codigo, creditos, semestre, profesor_id) VALUES
                (1, 'Informática Fundamental', 'INF-101', 4, 1, 7),
                (2, 'Ciencias de Datos', 'DAT-201', 5, 3, 1),
                (3, 'Física Clásica y Mecánica', 'FIS-102', 4, 2, 2),
                (4, 'Matemática Superior y Álgebra', 'MAT-103', 5, 1, 3),
                (5, 'Inteligencia Artificial Avanzada', 'IA-301', 5, 4, 1),
                (6, 'Ciberseguridad y Redes', 'SEC-202', 4, 3, 4),
                (7, 'Robótica Autónoma', 'ROB-302', 4, 4, 6),
                (8, 'Bioinformática Computacional', 'BIO-401', 4, 4, 5),
                (9, 'Astrofísica Teórica y Observacional', 'AST-305', 4, 3, 9),
                (10, 'Deep Learning y Visión por Computador', 'IA-402', 5, 5, 10),
                (11, 'Computación en la Nube y DevOps', 'CLD-303', 4, 4, 11),
                (12, 'Optimización y Métodos Numéricos', 'MAT-204', 4, 3, 12);
            """.trimIndent())

            // Inserción de 12 Estudiantes
            db.execSQL("""
                INSERT OR IGNORE INTO estudiantes (id, nombre, matricula, materia_id, promedio, ano_carrera) VALUES
                (1, 'Gabriel Alonso Pérez', 'EST-2024-001', 2, 96.5, 3),
                (2, 'Laura Martínez Gómez', 'EST-2024-002', 5, 98.0, 4),
                (3, 'Carlos Javier Romero', 'EST-2025-003', 1, 89.0, 1),
                (4, 'Amanda Torres Díaz', 'EST-2024-004', 3, 91.5, 2),
                (5, 'Yordanis Vega Cruz', 'EST-2025-005', 4, 94.0, 1),
                (6, 'Daniela Ramos Acosta', 'EST-2024-006', 6, 95.5, 3),
                (7, 'Héctor Luis Domínguez', 'EST-2023-007', 7, 92.0, 4),
                (8, 'Patricia Solís Blanco', 'EST-2023-008', 8, 97.2, 4),
                (9, 'Javier Alejandro Suárez', 'EST-2024-009', 9, 93.5, 3),
                (10, 'Camila Delgado Herrera', 'EST-2023-010', 10, 98.5, 5),
                (11, 'Rodrigo Mendoza Gil', 'EST-2024-011', 11, 90.0, 4),
                (12, 'Natalia Cordero Paz', 'EST-2025-012', 12, 94.8, 2);
            """.trimIndent())
        } finally {
            db.close()
        }
    }

    private fun createSampleUniversity(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS profesores (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    departamento TEXT NOT NULL
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cursos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    titulo TEXT NOT NULL,
                    creditos INTEGER DEFAULT 3,
                    profesor_id INTEGER,
                    FOREIGN KEY (profesor_id) REFERENCES profesores(id)
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS estudiantes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    matricula TEXT UNIQUE NOT NULL
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS calificaciones (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    estudiante_id INTEGER,
                    curso_id INTEGER,
                    nota REAL CHECK(nota >= 0 AND nota <= 100),
                    FOREIGN KEY (estudiante_id) REFERENCES estudiantes(id),
                    FOREIGN KEY (curso_id) REFERENCES cursos(id)
                );
            """.trimIndent())

            db.execSQL("INSERT OR IGNORE INTO profesores (nombre, departamento) VALUES ('Dr. Sanamé Durán', 'Bases de Datos'), ('Dra. Elena Ramos', 'Algoritmos');")
            db.execSQL("INSERT OR IGNORE INTO cursos (titulo, creditos, profesor_id) VALUES ('SQL Avanzado', 4, 1), ('Estructura de Datos', 3, 2);")
            db.execSQL("INSERT OR IGNORE INTO estudiantes (nombre, matricula) VALUES ('Marcos Gómez', 'EST-2026-01'), ('Sofía Morales', 'EST-2026-02'), ('David Paz', 'EST-2026-03');")
            db.execSQL("INSERT OR IGNORE INTO calificaciones (estudiante_id, curso_id, nota) VALUES (1, 1, 98.5), (2, 1, 92.0), (3, 2, 88.0);")
        } finally {
            db.close()
        }
    }

    private fun createSampleProjects(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS proyectos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    descripcion TEXT,
                    presupuesto REAL DEFAULT 0.0,
                    estado TEXT DEFAULT 'Activo'
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS miembros (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    rol TEXT NOT NULL,
                    email TEXT UNIQUE
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS tareas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    proyecto_id INTEGER,
                    miembro_id INTEGER,
                    titulo TEXT NOT NULL,
                    prioridad TEXT DEFAULT 'Media',
                    completada INTEGER DEFAULT 0,
                    FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE,
                    FOREIGN KEY (miembro_id) REFERENCES miembros(id) ON DELETE SET NULL
                );
            """.trimIndent())

            db.execSQL("INSERT OR IGNORE INTO proyectos (nombre, descripcion, presupuesto, estado) VALUES ('App Móvil MiniSQL', 'Desarrollo de editor SQLite para Android', 12500.0, 'En progreso'), ('Portal Web Clientes', 'Panel de administración en la nube', 8400.0, 'Planificación');")
            db.execSQL("INSERT OR IGNORE INTO miembros (nombre, rol, email) VALUES ('Eduardo Sanamé', 'Líder Técnico', 'eduardo@ejemplo.com'), ('Ana Gómez', 'Diseñadora UI/UX', 'ana@ejemplo.com'), ('Carlos Vera', 'Desarrollador Backend', 'carlos@ejemplo.com');")
            db.execSQL("INSERT OR IGNORE INTO tareas (proyecto_id, miembro_id, titulo, prioridad, completada) VALUES (1, 1, 'Implementar resaltado sintáctico', 'Alta', 1), (1, 2, 'Diseño de interfaz de tablas y proyectos', 'Alta', 1), (1, 3, 'Optimizar consultas SQLite', 'Media', 0), (2, 1, 'Arquitectura de base de datos', 'Alta', 0);")
        } finally {
            db.close()
        }
    }

    private fun createSampleClinic(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS medicos (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    especialidad TEXT NOT NULL,
                    telefono TEXT
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS pacientes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    edad INTEGER,
                    ciudad TEXT,
                    grupo_sanguineo TEXT
                );
            """.trimIndent())
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS citas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    paciente_id INTEGER,
                    medico_id INTEGER,
                    fecha TEXT NOT NULL,
                    motivo TEXT,
                    costo REAL DEFAULT 50.0,
                    FOREIGN KEY (paciente_id) REFERENCES pacientes(id) ON DELETE CASCADE,
                    FOREIGN KEY (medico_id) REFERENCES medicos(id)
                );
            """.trimIndent())

            db.execSQL("INSERT OR IGNORE INTO medicos (nombre, especialidad, telefono) VALUES ('Dr. Roberto Blanco', 'Cardiología', '+34 600 123 456'), ('Dra. Laura Díaz', 'Pediatría', '+34 600 987 654');")
            db.execSQL("INSERT OR IGNORE INTO pacientes (nombre, edad, ciudad, grupo_sanguineo) VALUES ('Marta Silva', 34, 'Madrid', 'O+'), ('Pedro Morales', 62, 'Barcelona', 'A+'), ('Elena Rios', 19, 'Valencia', 'B-');")
            db.execSQL("INSERT OR IGNORE INTO citas (paciente_id, medico_id, fecha, motivo, costo) VALUES (1, 1, '2026-10-28', 'Chequeo preventivo', 75.0), (2, 1, 'Seguimiento presión arterial', 60.0), (3, 2, 'Consulta general', 50.0);")
        } finally {
            db.close()
        }
    }

    suspend fun getDatabaseTableSummaries(dbName: String): List<TableSummary> = withContext(Dispatchers.IO) {
        val target = if (dbName.endsWith(".db")) dbName else "$dbName.db"
        val file = File(dbDir, target)
        if (!file.exists()) return@withContext emptyList()
        val list = mutableListOf<TableSummary>()
        try {
            val db = if (currentDbName == target && activeDatabase?.isOpen == true) {
                activeDatabase!!
            } else {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            }
            val shouldClose = (db != activeDatabase)
            try {
                val cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_metadata' ORDER BY name;",
                    null
                )
                cursor.use { c ->
                    while (c.moveToNext()) {
                        val tableName = c.getString(0)
                        var count = 0L
                        var colCount = 0
                        try {
                            val countCursor = db.rawQuery("SELECT COUNT(*) FROM \"$tableName\"", null)
                            countCursor.use { cc -> if (cc.moveToFirst()) count = cc.getLong(0) }
                        } catch (_: Exception) {}
                        try {
                            val infoCursor = db.rawQuery("PRAGMA table_info(\"$tableName\");", null)
                            infoCursor.use { ic -> colCount = ic.count }
                        } catch (_: Exception) {}
                        list.add(TableSummary(name = tableName, rowCount = count, columnCount = colCount))
                    }
                }
            } finally {
                if (shouldClose) {
                    try { db.close() } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
        list
    }
}

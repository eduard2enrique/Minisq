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

    private var currentDbName: String = "otorgamiento_carreras.db"
    private var activeDatabase: SQLiteDatabase? = null

    init {
        ensureInitialDatabase()
    }

    private fun ensureInitialDatabase() {
        val otorgamientoFile = File(dbDir, "otorgamiento_carreras.db")
        val currentOtorgamientoVer = prefs.getInt("otorgamiento_db_ver", 0)
        val TARGET_OTORGAMIENTO_VER = 5
        if (!otorgamientoFile.exists() || currentOtorgamientoVer < TARGET_OTORGAMIENTO_VER) {
            if (otorgamientoFile.exists()) {
                otorgamientoFile.delete()
            }
            createSampleOtorgamientoCarreras(otorgamientoFile)
            prefs.edit()
                .putString("template_otorgamiento_carreras.db", "otorgamiento_carreras")
                .putInt("otorgamiento_db_ver", TARGET_OTORGAMIENTO_VER)
                .apply()
        }
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
            "otorgamiento_carreras", "ingreso_superior" -> createSampleOtorgamientoCarreras(file)
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
            dbName.contains("otorgamiento", ignoreCase = true) || dbName.contains("carrera", ignoreCase = true) || dbName.contains("ingreso", ignoreCase = true) -> "otorgamiento_carreras"
            dbName.contains("facultad", ignoreCase = true) || dbName.contains("centro", ignoreCase = true) || dbName.contains("estudiantil", ignoreCase = true) || dbName.contains("ciencia", ignoreCase = true) -> "facultad_ciencias"
            dbName.contains("cuba", ignoreCase = true) -> "cuba"
            dbName.contains("ecommerce", ignoreCase = true) -> "ecommerce"
            dbName.contains("universidad", ignoreCase = true) || dbName.contains("university", ignoreCase = true) -> "university"
            dbName.contains("proyecto", ignoreCase = true) || dbName.contains("project", ignoreCase = true) -> "projects"
            dbName.contains("clinica", ignoreCase = true) || dbName.contains("clinic", ignoreCase = true) -> "clinic"
            dbName.startsWith("base_", ignoreCase = true) -> "empty"
            dbName.contains("mi_base_de_datos", ignoreCase = true) -> "sample"
            else -> "otorgamiento_carreras"
        }

        if (file.exists()) {
            file.delete()
        }

        when (template) {
            "otorgamiento_carreras", "ingreso_superior" -> createSampleOtorgamientoCarreras(file)
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
            val defaultFile = File(dbDir, "otorgamiento_carreras.db")
            createSampleOtorgamientoCarreras(defaultFile)
            switchDatabase("otorgamiento_carreras.db")
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

    private fun createSampleOtorgamientoCarreras(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            db.execSQL("PRAGMA foreign_keys = ON;")

            // 1. DDL
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS agrupaciones_carreras (
                    id_agrupacion INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre_agrupacion TEXT NOT NULL
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS universidades (
                    id_universidad INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre_universidad TEXT NOT NULL,
                    siglas TEXT UNIQUE NOT NULL,
                    provincia TEXT NOT NULL
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS carreras (
                    id_carrera INTEGER PRIMARY KEY AUTOINCREMENT,
                    codigo_carrera TEXT NOT NULL,
                    nombre_carrera TEXT NOT NULL,
                    id_universidad INTEGER NOT NULL,
                    id_agrupacion INTEGER NOT NULL,
                    via_ingreso TEXT NOT NULL DEFAULT 'Preuniversitario',
                    FOREIGN KEY (id_universidad) REFERENCES universidades(id_universidad),
                    FOREIGN KEY (id_agrupacion) REFERENCES agrupaciones_carreras(id_agrupacion)
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS estudiantes (
                    id_estudiante INTEGER PRIMARY KEY AUTOINCREMENT,
                    carnet_identidad TEXT UNIQUE NOT NULL,
                    carnet_preuniversitario TEXT UNIQUE NOT NULL,
                    nombre_completo TEXT NOT NULL,
                    sexo TEXT CHECK (sexo IN ('M', 'F')),
                    via_ingreso TEXT NOT NULL,
                    indice_academico DECIMAL(5,2) NOT NULL CHECK (indice_academico BETWEEN 0 AND 100),
                    provincia TEXT NOT NULL,
                    municipio TEXT NOT NULL,
                    centro_estudio TEXT NOT NULL
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS plazas_oferta (
                    id_plaza INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_carrera INTEGER NOT NULL,
                    id_universidad INTEGER NOT NULL,
                    provincia TEXT NOT NULL,
                    municipio TEXT,
                    cantidad_plazas INTEGER NOT NULL CHECK (cantidad_plazas > 0),
                    ambito TEXT NOT NULL CHECK (ambito IN ('municipal', 'provincial', 'nacional')),
                    FOREIGN KEY (id_carrera) REFERENCES carreras(id_carrera),
                    FOREIGN KEY (id_universidad) REFERENCES universidades(id_universidad)
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS boletas_solicitud (
                    id_boleta INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_estudiante INTEGER NOT NULL,
                    id_carrera INTEGER NOT NULL,
                    orden_prioridad INTEGER NOT NULL CHECK (orden_prioridad BETWEEN 1 AND 10),
                    FOREIGN KEY (id_estudiante) REFERENCES estudiantes(id_estudiante),
                    FOREIGN KEY (id_carrera) REFERENCES carreras(id_carrera)
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS examenes_ingreso (
                    id_examen INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_estudiante INTEGER NOT NULL UNIQUE,
                    nota_matematica DECIMAL(5,2),
                    nota_espanol DECIMAL(5,2),
                    nota_historia DECIMAL(5,2),
                    FOREIGN KEY (id_estudiante) REFERENCES estudiantes(id_estudiante)
                );
            """.trimIndent())

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS otorgamientos (
                    id_otorgamiento INTEGER PRIMARY KEY AUTOINCREMENT,
                    id_estudiante INTEGER NOT NULL UNIQUE,
                    id_plaza INTEGER NOT NULL,
                    prioridad_otorgada INTEGER NOT NULL,
                    ces TEXT,
                    sma TEXT,
                    fase_otorgamiento INTEGER NOT NULL DEFAULT 1,
                    estado TEXT DEFAULT 'Otorgado' CHECK (estado IN ('Otorgado', 'No otorgado')),
                    FOREIGN KEY (id_estudiante) REFERENCES estudiantes(id_estudiante),
                    FOREIGN KEY (id_plaza) REFERENCES plazas_oferta(id_plaza)
                );
            """.trimIndent())

            // 2. DML
            // 2.1 Agrupaciones
            db.execSQL("""
                INSERT INTO agrupaciones_carreras (id_agrupacion, nombre_agrupacion) VALUES 
                (1, 'Ciencias Médicas y Farmacéuticas'),
                (2, 'Ciencias Técnicas y Exactas'),
                (3, 'Ciencias Sociales y Humanísticas'),
                (4, 'Ciencias Económicas y Empresariales'),
                (5, 'Ciencias Pedagógicas'),
                (6, 'Ciencias Agropecuarias y Cultura Física'),
                (7, 'Técnico Superior');
            """.trimIndent())

            // 2.2 Universidades
            db.execSQL("""
                INSERT INTO universidades (id_universidad, nombre_universidad, siglas, provincia) VALUES 
                (1, 'Instituto Superior Minero Metalúrgico de Moa', 'ISMM', 'Holguín'),
                (2, 'Universidad de Holguín', 'UHo', 'Holguín'),
                (3, 'Universidad de Oriente', 'UO', 'Santiago de Cuba'),
                (4, 'Universidad de La Habana', 'UH', 'La Habana'),
                (5, 'Universidad Tecnológica de La Habana', 'CUJAE', 'La Habana'),
                (6, 'Universidad de Ciencias Informáticas', 'UCI', 'La Habana'),
                (7, 'Universidad de Camagüey', 'UC', 'Camagüey'),
                (8, 'Universidad de Granma', 'UG', 'Granma'),
                (9, 'Universidad de Las Tunas', 'ULT', 'Las Tunas'),
                (10, 'Escuela Nacional de Formación Aduanera', 'ENFA', 'La Habana'),
                (11, 'Universidad de Ciencias Médicas de Holguín', 'UCMHo', 'Holguín');
            """.trimIndent())

            // 2.3 Carreras (Plan Oficial de Holguín: 85 carreras completas)
            db.execSQL("INSERT INTO carreras (id_carrera, codigo_carrera, nombre_carrera, id_universidad, id_agrupacion, via_ingreso) VALUES (1, '102', 'Ingeniería Geología', 1, 2, 'Preuniversitario'), (2, '103', 'Ingeniería Minas', 1, 2, 'Preuniversitario'), (3, '104', 'Ingeniería Metalúrgica y Materiales', 1, 2, 'Preuniversitario'), (4, '110', 'Ingeniería Informática', 2, 2, 'Preuniversitario'), (5, '105', 'Ingeniería Eléctrica', 2, 2, 'Preuniversitario'), (6, '1051', 'Ingeniería Eléctrica', 1, 2, 'Preuniversitario'), (7, '106', 'Ingeniería Mecánica', 2, 2, 'Preuniversitario'), (8, '1061', 'Ingeniería Mecánica', 1, 2, 'Preuniversitario'), (9, '107', 'Ingeniería Química', 3, 2, 'Preuniversitario'), (10, '108', 'Ingeniería en Automática', 3, 2, 'Preuniversitario'), (11, '109', 'Ingeniería Telecomunicaciones y Electrónica', 3, 2, 'Preuniversitario'), (12, '111', 'Ingeniería Industrial', 2, 2, 'Preuniversitario'), (13, '112', 'Arquitectura', 7, 2, 'Preuniversitario'), (14, '113', 'Ingeniería Hidráulica', 2, 2, 'Preuniversitario'), (15, '114', 'Ingeniería Civil', 2, 2, 'Preuniversitario'), (16, '115', 'Licenciado Matemática', 2, 2, 'Preuniversitario'), (17, '116', 'Licenciado Ciencia de la Computación', 3, 2, 'Preuniversitario');");
            db.execSQL("INSERT INTO carreras (id_carrera, codigo_carrera, nombre_carrera, id_universidad, id_agrupacion, via_ingreso) VALUES (18, '117', 'Licenciado Física', 3, 2, 'Preuniversitario'), (19, '118', 'Licenciado Química', 3, 2, 'Preuniversitario'), (20, '119', 'Licenciado Geografía', 4, 2, 'Preuniversitario'), (21, '120', 'Ingeniería Agrícola', 8, 6, 'Preuniversitario'), (22, '123', 'Ingeniería Biomédica', 3, 2, 'Preuniversitario'), (23, '125', 'Licenciado Física Nuclear', 4, 2, 'Preuniversitario'), (24, '126', 'Licenciado en Radioquímica', 4, 2, 'Preuniversitario'), (25, '127', 'Licenciado Meteorología', 4, 2, 'Preuniversitario'), (26, '128', 'Ingeniería Tecnologías Nucleares y Energéticas', 4, 2, 'Preuniversitario'), (27, '129', 'Ingeniería Geofísica', 5, 2, 'Preuniversitario'), (28, '131', 'Ingeniería en Ciencias Informáticas', 6, 2, 'Preuniversitario'), (29, '133', 'Ingeniería en Ciberseguridad', 6, 2, 'Preuniversitario'), (30, '201', 'Licenciado Bioquímica', 4, 2, 'Preuniversitario'), (31, '202', 'Licenciado Microbiología', 4, 2, 'Preuniversitario'), (32, '203', 'Licenciado Ciencias Alimentarias', 7, 2, 'Preuniversitario'), (33, '204', 'Licenciado Ciencias Farmacéuticas', 3, 1, 'Preuniversitario'), (34, '205', 'Licenciado Biología', 3, 2, 'Preuniversitario');");
            db.execSQL("INSERT INTO carreras (id_carrera, codigo_carrera, nombre_carrera, id_universidad, id_agrupacion, via_ingreso) VALUES (35, '301', 'Licenciado Historia', 2, 3, 'Preuniversitario'), (36, '302', 'Licenciado Derecho', 2, 3, 'Preuniversitario'), (37, '303', 'Licenciado Historia del Arte', 3, 3, 'Preuniversitario'), (38, '305', 'Licenciado Ciencia de la Información', 2, 3, 'Preuniversitario'), (39, '306', 'Licenciado Psicología', 3, 3, 'Preuniversitario'), (40, '307', 'Licenciado Letras', 3, 3, 'Preuniversitario'), (41, '308', 'Licenciado Filosofía', 3, 3, 'Preuniversitario'), (42, '309', 'Licenciado Lengua Rusa', 4, 3, 'Preuniversitario'), (43, '310', 'Licenciado Lengua Inglesa', 2, 3, 'Preuniversitario'), (44, '311', 'Licenciado Lengua Alemana', 4, 3, 'Preuniversitario'), (45, '312', 'Licenciado Lengua Francesa', 4, 3, 'Preuniversitario'), (46, '313', 'Licenciado Sociología', 3, 3, 'Preuniversitario'), (47, '314', 'Lic. Gestión Sociocultural para el Desarrollo', 2, 3, 'Preuniversitario'), (48, '315', 'Licenciado Comunicación Social', 9, 3, 'Preuniversitario'), (49, '325', 'Diseño Industrial', 4, 3, 'Preuniversitario'), (50, '333', 'Diseño en Comunicación Visual', 4, 3, 'Preuniversitario'), (51, '501', 'Licenciado Cultura Física', 2, 6, 'Preuniversitario');");
            db.execSQL("INSERT INTO carreras (id_carrera, codigo_carrera, nombre_carrera, id_universidad, id_agrupacion, via_ingreso) VALUES (52, '601', 'Ingeniería Agronomía', 2, 6, 'Preuniversitario'), (53, '602', 'Ingeniería Forestal', 8, 6, 'Preuniversitario'), (54, '603', 'Medicina Veterinaria y Zootecnia', 8, 6, 'Preuniversitario'), (55, '701', 'Licenciado Educación Matemática', 2, 5, 'Preuniversitario'), (56, '702', 'Licenciado Educación Español Literatura', 2, 5, 'Preuniversitario'), (57, '703', 'Licenciado Educación en Física', 2, 5, 'Preuniversitario'), (58, '704', 'Licenciado Educación Química', 2, 5, 'Preuniversitario'), (59, '705', 'Licenciado Educación Biología', 2, 5, 'Preuniversitario'), (60, '706', 'Licenciado Educación Geografía', 2, 5, 'Preuniversitario'), (61, '707', 'Licenciado Educación Laboral', 2, 5, 'Preuniversitario'), (62, '708', 'Licenciado Educación Informática', 2, 5, 'Preuniversitario'), (63, '711', 'Lic. Educ. Marxismo Leninismo e Historia', 2, 5, 'Preuniversitario'), (64, '713', 'Licenciado Educación Especial', 2, 5, 'Preuniversitario'), (65, '714', 'Licenciado Educación Primaria', 2, 5, 'Preuniversitario'), (66, '715', 'Licenciado Educación Preescolar', 2, 5, 'Preuniversitario'), (67, '720', 'Licenciado Educación Logopedia', 2, 5, 'Preuniversitario'), (68, '721', 'Licenciado Educación Pedagogía Agropecuaria', 2, 5, 'Preuniversitario');");
            db.execSQL("INSERT INTO carreras (id_carrera, codigo_carrera, nombre_carrera, id_universidad, id_agrupacion, via_ingreso) VALUES (69, '724', 'Licenciado Educación Mecánica', 2, 5, 'Preuniversitario'), (70, '725', 'Licenciado Educación Construcción', 2, 5, 'Preuniversitario'), (71, '726', 'Licenciado Educación Eléctrica', 2, 5, 'Preuniversitario'), (72, '728', 'Licenciado Educación Economía', 2, 5, 'Preuniversitario'), (73, '731', 'Licenciado Educación Lenguas Extranjeras', 2, 5, 'Preuniversitario'), (74, '740', 'Licenciado Educación Artística', 2, 5, 'Preuniversitario'), (75, '801', 'Licenciado Economía', 2, 4, 'Preuniversitario'), (76, '802', 'Licenciado Contabilidad y Finanzas', 2, 4, 'Preuniversitario'), (77, '8021', 'Licenciado Contabilidad y Finanzas', 1, 4, 'Preuniversitario'), (78, '803', 'Licenciado Turismo', 2, 4, 'Preuniversitario'), (79, '921', 'TS en Aduana', 10, 7, 'Técnico Superior'), (80, '938', 'TS en Redes y Seguridad Informática', 6, 7, 'Técnico Superior'), (81, '949', 'TS en Asistencia turística', 2, 7, 'Técnico Superior'), (82, '950', 'TS en Servicios técnicos para el Turismo', 2, 7, 'Técnico Superior'), (83, '953', 'TS en Auditoría', 2, 7, 'Técnico Superior'), (84, '954', 'TS Interpretación de lengua de señas cubana', 2, 7, 'Técnico Superior'), (85, '1003', 'TS en Gestión de riesgo de impago en el sector turístico', 2, 7, 'Técnico Superior');");

            // 2.4 Plazas Oferta Oficiales (983 vacantes en total)
            db.execSQL("INSERT INTO plazas_oferta (id_plaza, id_carrera, id_universidad, provincia, municipio, cantidad_plazas, ambito) VALUES (1, 1, 1, 'Holguín', 'Moa', 11, 'municipal'), (2, 2, 1, 'Holguín', 'Moa', 8, 'municipal'), (3, 3, 1, 'Holguín', 'Moa', 6, 'municipal'), (4, 4, 2, 'Holguín', 'Holguín', 39, 'provincial'), (5, 5, 2, 'Holguín', 'Holguín', 10, 'provincial'), (6, 6, 1, 'Holguín', 'Moa', 18, 'municipal'), (7, 7, 2, 'Holguín', 'Holguín', 40, 'provincial'), (8, 8, 1, 'Holguín', 'Moa', 21, 'municipal'), (9, 9, 3, 'Santiago de Cuba', NULL, 5, 'nacional'), (10, 10, 3, 'Santiago de Cuba', NULL, 12, 'nacional'), (11, 11, 3, 'Santiago de Cuba', NULL, 2, 'nacional'), (12, 12, 2, 'Holguín', 'Holguín', 40, 'provincial'), (13, 13, 7, 'Camagüey', NULL, 10, 'nacional'), (14, 14, 2, 'Holguín', 'Holguín', 18, 'provincial'), (15, 15, 2, 'Holguín', 'Holguín', 54, 'provincial'), (16, 16, 2, 'Holguín', 'Holguín', 14, 'provincial'), (17, 17, 3, 'Santiago de Cuba', NULL, 9, 'nacional');");
            db.execSQL("INSERT INTO plazas_oferta (id_plaza, id_carrera, id_universidad, provincia, municipio, cantidad_plazas, ambito) VALUES (18, 18, 3, 'Santiago de Cuba', NULL, 4, 'nacional'), (19, 19, 3, 'Santiago de Cuba', NULL, 8, 'nacional'), (20, 20, 4, 'La Habana', NULL, 3, 'nacional'), (21, 21, 8, 'Granma', NULL, 9, 'nacional'), (22, 22, 3, 'Santiago de Cuba', NULL, 4, 'nacional'), (23, 23, 4, 'La Habana', NULL, 2, 'nacional'), (24, 24, 4, 'La Habana', NULL, 1, 'nacional'), (25, 25, 4, 'La Habana', NULL, 1, 'nacional'), (26, 26, 4, 'La Habana', NULL, 1, 'nacional'), (27, 27, 5, 'La Habana', NULL, 4, 'nacional'), (28, 28, 6, 'La Habana', NULL, 44, 'nacional'), (29, 29, 6, 'La Habana', NULL, 4, 'nacional'), (30, 30, 4, 'La Habana', NULL, 4, 'nacional'), (31, 31, 4, 'La Habana', NULL, 2, 'nacional'), (32, 32, 7, 'Camagüey', NULL, 4, 'nacional'), (33, 33, 3, 'Santiago de Cuba', NULL, 4, 'nacional'), (34, 34, 3, 'Santiago de Cuba', NULL, 5, 'nacional');");
            db.execSQL("INSERT INTO plazas_oferta (id_plaza, id_carrera, id_universidad, provincia, municipio, cantidad_plazas, ambito) VALUES (35, 35, 2, 'Holguín', 'Holguín', 9, 'provincial'), (36, 36, 2, 'Holguín', 'Holguín', 39, 'provincial'), (37, 37, 3, 'Santiago de Cuba', NULL, 2, 'nacional'), (38, 38, 2, 'Holguín', 'Holguín', 10, 'provincial'), (39, 39, 3, 'Santiago de Cuba', NULL, 1, 'nacional'), (40, 40, 3, 'Santiago de Cuba', NULL, 1, 'nacional'), (41, 41, 3, 'Santiago de Cuba', NULL, 1, 'nacional'), (42, 42, 4, 'La Habana', NULL, 1, 'nacional'), (43, 43, 2, 'Holguín', 'Holguín', 13, 'provincial'), (44, 44, 4, 'La Habana', NULL, 1, 'nacional'), (45, 45, 4, 'La Habana', NULL, 3, 'nacional'), (46, 46, 3, 'Santiago de Cuba', NULL, 8, 'nacional'), (47, 47, 2, 'Holguín', 'Holguín', 5, 'provincial'), (48, 48, 9, 'Las Tunas', NULL, 10, 'nacional'), (49, 49, 4, 'La Habana', NULL, 3, 'nacional'), (50, 50, 4, 'La Habana', NULL, 2, 'nacional'), (51, 51, 2, 'Holguín', 'Holguín', 17, 'provincial');");
            db.execSQL("INSERT INTO plazas_oferta (id_plaza, id_carrera, id_universidad, provincia, municipio, cantidad_plazas, ambito) VALUES (52, 52, 2, 'Holguín', 'Holguín', 50, 'provincial'), (53, 53, 8, 'Granma', NULL, 4, 'nacional'), (54, 54, 8, 'Granma', NULL, 12, 'nacional'), (55, 55, 2, 'Holguín', 'Holguín', 3, 'provincial'), (56, 56, 2, 'Holguín', 'Holguín', 3, 'provincial'), (57, 57, 2, 'Holguín', 'Holguín', 3, 'provincial'), (58, 58, 2, 'Holguín', 'Holguín', 3, 'provincial'), (59, 59, 2, 'Holguín', 'Holguín', 3, 'provincial'), (60, 60, 2, 'Holguín', 'Holguín', 3, 'provincial'), (61, 61, 2, 'Holguín', 'Holguín', 3, 'provincial'), (62, 62, 2, 'Holguín', 'Holguín', 3, 'provincial'), (63, 63, 2, 'Holguín', 'Holguín', 3, 'provincial'), (64, 64, 2, 'Holguín', 'Holguín', 3, 'provincial'), (65, 65, 2, 'Holguín', 'Holguín', 3, 'provincial'), (66, 66, 2, 'Holguín', 'Holguín', 3, 'provincial'), (67, 67, 2, 'Holguín', 'Holguín', 3, 'provincial'), (68, 68, 2, 'Holguín', 'Holguín', 2, 'provincial');");
            db.execSQL("INSERT INTO plazas_oferta (id_plaza, id_carrera, id_universidad, provincia, municipio, cantidad_plazas, ambito) VALUES (69, 69, 2, 'Holguín', 'Holguín', 2, 'provincial'), (70, 70, 2, 'Holguín', 'Holguín', 2, 'provincial'), (71, 71, 2, 'Holguín', 'Holguín', 2, 'provincial'), (72, 72, 2, 'Holguín', 'Holguín', 2, 'provincial'), (73, 73, 2, 'Holguín', 'Holguín', 5, 'provincial'), (74, 74, 2, 'Holguín', 'Holguín', 3, 'provincial'), (75, 75, 2, 'Holguín', 'Holguín', 50, 'provincial'), (76, 76, 2, 'Holguín', 'Holguín', 45, 'provincial'), (77, 77, 1, 'Holguín', 'Moa', 25, 'municipal'), (78, 78, 2, 'Holguín', 'Holguín', 15, 'provincial'), (79, 79, 10, 'La Habana', NULL, 40, 'nacional'), (80, 80, 6, 'La Habana', NULL, 33, 'nacional'), (81, 81, 2, 'Holguín', 'Holguín', 20, 'provincial'), (82, 82, 2, 'Holguín', 'Holguín', 15, 'provincial'), (83, 83, 2, 'Holguín', 'Holguín', 25, 'provincial'), (84, 84, 2, 'Holguín', 'Holguín', 15, 'provincial'), (85, 85, 2, 'Holguín', 'Holguín', 30, 'provincial');");

            // 2.5 Estudiantes (30 de Moa + 5 adicionales realistas de Holguín/Moa)
            db.execSQL("""
                INSERT INTO estudiantes (carnet_identidad, carnet_preuniversitario, nombre_completo, sexo, via_ingreso, indice_academico, provincia, municipio, centro_estudio) VALUES
                ('95010112345', '2026-001', 'Melisa María Fat Tamayo', 'F', 'Preuniversitario', 98.50, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'),
                ('95020223456', '2026-002', 'Leonardo Leyva Rodríguez', 'M', 'Preuniversitario', 97.80, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95030334567', '2026-003', 'Dayana Martínez Batista', 'F', 'Preuniversitario', 97.20, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95040445678', '2026-004', 'Adrián Morales Fernández', 'M', 'Preuniversitario', 96.50, 'Holguín', 'Moa', 'IPU Pedro Soto Alba'),
                ('95050556789', '2026-005', 'Camila Herrera Domínguez', 'F', 'Preuniversitario', 96.00, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'),
                ('95060667890', '2026-006', 'Javier Silva Cruz', 'M', 'Preuniversitario', 95.50, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95070778901', '2026-007', 'Daniela Ramos Acosta', 'F', 'Preuniversitario', 95.10, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95080889012', '2026-008', 'Gabriel Alonso Pérez', 'M', 'Preuniversitario', 94.80, 'Holguín', 'Moa', 'IPU Pedro Soto Alba'),
                ('95090990123', '2026-009', 'Laura Martínez Gómez', 'F', 'Preuniversitario', 94.20, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95101001234', '2026-010', 'Carlos Javier Romero', 'M', 'Preuniversitario', 93.90, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'),
                ('95111112345', '2026-011', 'Amanda Torres Díaz', 'F', 'Preuniversitario', 93.50, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95121223456', '2026-012', 'Yordanis Vega Cruz', 'M', 'Preuniversitario', 93.00, 'Holguín', 'Moa', 'IPU Pedro Soto Alba'),
                ('95131334567', '2026-013', 'Patricia Solís Blanco', 'F', 'Preuniversitario', 92.60, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95141445678', '2026-014', 'Héctor Luis Domínguez', 'M', 'Preuniversitario', 92.10, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95151556789', '2026-015', 'Rodrigo Mendoza Gil', 'M', 'Preuniversitario', 91.80, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'),
                ('95161667890', '2026-016', 'Natalia Cordero Paz', 'F', 'Preuniversitario', 91.50, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95171778901', '2026-017', 'Marcos Aurelio Tamayo', 'M', 'Preuniversitario', 91.20, 'Holguín', 'Moa', 'IPU Pedro Soto Alba'),
                ('95181889012', '2026-018', 'Claudia Patricia Leyva', 'F', 'Preuniversitario', 90.80, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95191990123', '2026-019', 'Jorge Luis Benítez', 'M', 'Preuniversitario', 90.50, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95202001234', '2026-020', 'Beatriz Morales Cruz', 'F', 'Preuniversitario', 90.10, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'),
                ('95212112345', '2026-021', 'David González Pérez', 'M', 'Preuniversitario', 89.80, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95222223456', '2026-022', 'Yanelis Pupo Almaguer', 'F', 'Preuniversitario', 89.50, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95232334567', '2026-023', 'Dariel Sánchez Peña', 'M', 'Preuniversitario', 89.20, 'Holguín', 'Moa', 'IPU Pedro Soto Alba'),
                ('95242445678', '2026-024', 'Lianet Cruz Batista', 'F', 'Preuniversitario', 88.90, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95252556789', '2026-025', 'Yosvany Rivas Cedeño', 'M', 'Preuniversitario', 88.50, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'),
                ('95262667890', '2026-026', 'Mileydis Fornaris Castillo', 'F', 'Preuniversitario', 88.10, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95272778901', '2026-027', 'Ernesto Luis Fonseca', 'M', 'Preuniversitario', 87.80, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95282889012', '2026-028', 'Ana Karla Santiesteban', 'F', 'Preuniversitario', 87.50, 'Holguín', 'Holguín', 'IPVCE José Martí Pérez'),
                ('95292990123', '2026-029', 'Maikel Ronda Ochoa', 'M', 'Preuniversitario', 86.90, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95303001234', '2026-030', 'Liset Barreda Matos', 'F', 'Preuniversitario', 86.50, 'Holguín', 'Moa', 'IPU Pedro Soto Alba'),
                ('95313112345', '2026-031', 'Yoandry Hechavarría Ortiz', 'M', 'Preuniversitario', 96.70, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95323223456', '2026-032', 'Gretel Carralero Leyva', 'F', 'Preuniversitario', 95.80, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95333334567', '2026-033', 'Osmel Pérez Miranda', 'M', 'Preuniversitario', 94.50, 'Holguín', 'Moa', 'IPU Pedro Soto Alba'),
                ('95343445678', '2026-034', 'Eduardo Enrique Sanamé', 'M', 'Preuniversitario', 97.40, 'Holguín', 'Moa', 'IPU Antonio López Fernández'),
                ('95353556789', '2026-035', 'Beatriz Montero Guilarte', 'F', 'Preuniversitario', 92.80, 'Holguín', 'Moa', 'IPU Antonio López Fernández');
            """.trimIndent())

            // 2.6 Exámenes de Ingreso
            db.execSQL("""
                INSERT INTO examenes_ingreso (id_estudiante, nota_matematica, nota_espanol, nota_historia) VALUES
                (1, 95, 92, 90), (2, 98, 95, 96), (3, 90, 88, 92), (4, 88, 90, 85), (5, 85, 88, 80),
                (6, 92, 90, 88), (7, 85, 80, 82), (8, 75, 78, 80), (9, 80, 75, 78), (10, 70, 72, 75),
                (11, 85, 82, 80), (12, 80, 85, 78), (13, 82, 80, 85), (14, 78, 80, 75), (15, 75, 78, 72),
                (16, 88, 85, 82), (17, 82, 80, 78), (18, 90, 95, 92), (19, 75, 70, 72), (20, 80, 82, 80),
                (21, 85, 88, 85), (22, 72, 75, 70), (23, 80, 78, 82), (24, 78, 75, 80), (25, 82, 85, 80),
                (26, 70, 75, 72), (27, 85, 80, 78), (28, 80, 82, 85), (29, 75, 78, 75), (30, 72, 70, 75),
                (31, 91, 89, 90), (32, 94, 88, 91), (33, 84, 82, 85), (34, 96, 94, 95), (35, 78, 80, 82);
            """.trimIndent())

            // 2.7 Boletas de Solicitud (Con hasta 10 opciones de prioridad)
            db.execSQL("""
                INSERT INTO boletas_solicitud (id_estudiante, id_carrera, orden_prioridad) VALUES
                (1, 1, 1), (1, 4, 2), (1, 2, 3), (1, 5, 4), (1, 9, 5), (1, 11, 6), (1, 12, 7), (1, 6, 8), (1, 7, 9), (1, 8, 10),
                (2, 4, 1), (2, 28, 2), (2, 5, 3), (2, 2, 4), (2, 12, 5), (2, 6, 6), (2, 7, 7), (2, 8, 8), (2, 9, 9), (2, 10, 10),
                (3, 1, 1), (3, 3, 2), (3, 2, 3), (3, 9, 4), (3, 11, 5), (3, 4, 6), (3, 5, 7), (3, 10, 8), (3, 6, 9), (3, 7, 10),
                (4, 27, 1), (4, 4, 2), (4, 1, 3), (4, 10, 4), (4, 11, 5), (4, 6, 6), (4, 7, 7), (4, 8, 8), (4, 2, 9), (4, 3, 10),
                (5, 4, 1), (5, 6, 2), (5, 7, 3), (5, 8, 4), (5, 5, 5), (5, 12, 6), (5, 1, 7), (5, 2, 8), (5, 9, 9), (5, 10, 10),
                (6, 6, 1), (6, 4, 2), (6, 7, 3), (6, 8, 4), (6, 12, 5), (6, 5, 6), (6, 1, 7), (6, 2, 8), (6, 9, 9), (6, 10, 10),
                (7, 29, 1), (7, 28, 2), (7, 11, 3), (7, 10, 4), (7, 2, 5), (7, 3, 6), (7, 4, 7), (7, 5, 8), (7, 6, 9), (7, 7, 10),
                (8, 6, 1), (8, 4, 2), (8, 7, 3), (8, 8, 4), (8, 12, 5), (8, 5, 6), (8, 9, 7), (8, 10, 8), (8, 1, 9), (8, 2, 10),
                (9, 2, 1), (9, 3, 2), (9, 1, 3), (9, 4, 4), (9, 5, 5), (9, 9, 6), (9, 11, 7), (9, 6, 8), (9, 7, 9), (9, 8, 10),
                (10, 4, 1), (10, 28, 2), (10, 6, 3), (10, 7, 4), (10, 8, 5), (10, 12, 6), (10, 1, 7), (10, 2, 8), (10, 9, 9), (10, 10, 10),
                (11, 6, 1), (11, 8, 2), (11, 7, 3), (11, 12, 4), (11, 4, 5), (11, 5, 6), (11, 1, 7), (11, 2, 8), (11, 9, 9), (11, 10, 10),
                (12, 1, 1), (12, 9, 2), (12, 11, 3), (12, 10, 4), (12, 4, 5), (12, 2, 6), (12, 5, 7), (12, 6, 8), (12, 7, 9), (12, 8, 10),
                (13, 7, 1), (13, 6, 2), (13, 8, 3), (13, 12, 4), (13, 4, 5), (13, 5, 6), (13, 9, 7), (13, 10, 8), (13, 1, 9), (13, 2, 10),
                (14, 8, 1), (14, 7, 2), (14, 6, 3), (14, 12, 4), (14, 4, 5), (14, 5, 6), (14, 1, 7), (14, 2, 8), (14, 9, 9), (14, 10, 10),
                (15, 23, 1), (15, 27, 2), (15, 1, 3), (15, 11, 4), (15, 2, 5), (15, 3, 6), (15, 4, 7), (15, 5, 8), (15, 6, 9), (15, 7, 10),
                (16, 1, 1), (16, 4, 2), (16, 9, 3), (16, 11, 4), (16, 2, 5), (16, 5, 6), (16, 12, 7), (16, 6, 8), (16, 7, 9), (16, 8, 10),
                (17, 9, 1), (17, 2, 2), (17, 10, 3), (17, 1, 4), (17, 11, 5), (17, 3, 6), (17, 4, 7), (17, 5, 8), (17, 6, 9), (17, 7, 10),
                (18, 4, 1), (18, 6, 2), (18, 5, 3), (18, 7, 4), (18, 8, 5), (18, 12, 6), (18, 1, 7), (18, 2, 8), (18, 9, 9), (18, 10, 10),
                (19, 11, 1), (19, 9, 2), (19, 1, 3), (19, 10, 4), (19, 2, 5), (19, 3, 6), (19, 4, 7), (19, 5, 8), (19, 6, 9), (19, 7, 10),
                (20, 28, 1), (20, 8, 2), (20, 7, 3), (20, 12, 4), (20, 4, 5), (20, 5, 6), (20, 1, 7), (20, 2, 8), (20, 9, 9), (20, 10, 10),
                (21, 12, 1), (21, 10, 2), (21, 4, 3), (21, 6, 4), (21, 7, 5), (21, 8, 6), (21, 1, 7), (21, 2, 8), (21, 9, 9), (21, 5, 10),
                (22, 2, 1), (22, 3, 2), (22, 1, 3), (22, 4, 4), (22, 9, 5), (22, 11, 6), (22, 5, 7), (22, 6, 8), (22, 7, 9), (22, 8, 10),
                (23, 24, 1), (23, 1, 2), (23, 11, 3), (23, 10, 4), (23, 2, 5), (23, 3, 6), (23, 4, 7), (23, 5, 8), (23, 6, 9), (23, 7, 10),
                (24, 5, 1), (24, 4, 2), (24, 6, 3), (24, 7, 4), (24, 8, 5), (24, 12, 6), (24, 1, 7), (24, 2, 8), (24, 9, 9), (24, 10, 10),
                (25, 29, 1), (25, 9, 2), (25, 1, 3), (25, 11, 4), (25, 2, 5), (25, 3, 6), (25, 4, 7), (25, 5, 8), (25, 6, 9), (25, 7, 10),
                (26, 6, 1), (26, 8, 2), (26, 7, 3), (26, 12, 4), (26, 4, 5), (26, 5, 6), (26, 1, 7), (26, 2, 8), (26, 9, 9), (26, 10, 10),
                (27, 1, 1), (27, 11, 2), (27, 9, 3), (27, 10, 4), (27, 2, 5), (27, 3, 6), (27, 4, 7), (27, 5, 8), (27, 6, 9), (27, 7, 10),
                (28, 27, 1), (28, 10, 2), (28, 1, 3), (28, 11, 4), (28, 2, 5), (28, 3, 6), (28, 4, 7), (28, 5, 8), (28, 6, 9), (28, 7, 10),
                (29, 4, 1), (29, 5, 2), (29, 6, 3), (29, 7, 4), (29, 8, 5), (29, 12, 6), (29, 1, 7), (29, 2, 8), (29, 9, 9), (29, 10, 10),
                (30, 6, 1), (30, 7, 2), (30, 8, 3), (30, 12, 4), (30, 4, 5), (30, 5, 6), (30, 1, 7), (30, 2, 8), (30, 9, 9), (30, 10, 10),
                (31, 4, 1), (31, 2, 2), (31, 5, 3), (31, 1, 4), (31, 9, 5), (31, 11, 6), (31, 6, 7), (31, 7, 8), (31, 8, 9), (31, 12, 10),
                (32, 6, 1), (32, 4, 2), (32, 7, 3), (32, 8, 4), (32, 5, 5), (32, 12, 6), (32, 1, 7), (32, 2, 8), (32, 9, 9), (32, 10, 10),
                (33, 9, 1), (33, 10, 2), (33, 1, 3), (33, 11, 4), (33, 4, 5), (33, 2, 6), (33, 3, 7), (33, 5, 8), (33, 6, 9), (33, 7, 10),
                (34, 4, 1), (34, 1, 2), (34, 5, 3), (34, 2, 4), (34, 9, 5), (34, 11, 6), (34, 6, 7), (34, 7, 8), (34, 8, 9), (34, 12, 10),
                (35, 7, 1), (35, 6, 2), (35, 8, 3), (35, 12, 4), (35, 4, 5), (35, 5, 6), (35, 9, 7), (35, 10, 8), (35, 1, 9), (35, 2, 10);
            """.trimIndent())

            // 2.8 Otorgamientos (Simulación oficial según Escalafón vs. Plazas ofertadas)
            db.execSQL("""
                INSERT INTO otorgamientos (id_estudiante, id_plaza, prioridad_otorgada, ces, sma, fase_otorgamiento, estado) VALUES
                (1, 4, 2, 'UHo', 'Apto FAR', 1, 'Otorgado'),
                (2, 4, 1, 'UHo', 'Apto FAR', 1, 'Otorgado'),
                (3, 1, 1, 'ISMM', 'Diferido', 1, 'Otorgado'),
                (4, 9, 1, 'UO', 'Apto FAR', 1, 'Otorgado'),
                (5, 4, 1, 'UHo', 'Apto FAR', 1, 'Otorgado'),
                (6, 6, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (7, 9, 1, 'UO', 'Diferida', 1, 'Otorgado'),
                (8, 6, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (9, 2, 1, 'ISMM', 'Diferido', 1, 'Otorgado'),
                (10, 4, 1, 'UHo', 'Apto FAR', 1, 'Otorgado'),
                (11, 6, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (12, 1, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (13, 7, 1, 'UHo', 'Diferida', 1, 'Otorgado'),
                (14, 8, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (15, 9, 1, 'UO', 'Diferida', 1, 'Otorgado'),
                (16, 1, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (17, 9, 1, 'UO', 'Diferido', 1, 'Otorgado'),
                (18, 4, 1, 'UHo', 'Diferida', 1, 'Otorgado'),
                (19, 11, 1, 'UO', 'Apto FAR', 1, 'Otorgado'),
                (20, 6, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (21, 12, 1, 'UHo', 'Apto FAR', 1, 'Otorgado'),
                (22, 2, 1, 'ISMM', 'Diferido', 1, 'Otorgado'),
                (23, 9, 1, 'UO', 'Diferida', 1, 'Otorgado'),
                (24, 5, 1, 'UHo', 'Apto FAR', 1, 'Otorgado'),
                (25, 10, 1, 'UO', 'Diferida', 1, 'Otorgado'),
                (26, 6, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (27, 1, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (28, 9, 1, 'UO', 'Diferida', 1, 'Otorgado'),
                (29, 4, 1, 'UHo', 'Apto FAR', 1, 'Otorgado'),
                (30, 6, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (31, 4, 1, 'UHo', 'Apto FAR', 1, 'Otorgado'),
                (32, 6, 1, 'ISMM', 'Apto FAR', 1, 'Otorgado'),
                (33, 9, 1, 'UO', 'Diferida', 1, 'Otorgado'),
                (34, 4, 1, 'UHo', 'Diferida', 1, 'Otorgado'),
                (35, 7, 1, 'UHo', 'Apto FAR', 1, 'Otorgado');
            """.trimIndent())
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

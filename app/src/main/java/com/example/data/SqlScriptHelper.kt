package com.example.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.SqlExecutionResult
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

data class LoadedSqlFile(
    val fileName: String,
    val fileSizeFormatted: String,
    val rawSql: String,
    val statementCount: Int,
    val tableNames: List<String>,
    val indexNames: List<String>,
    val insertCount: Int,
    val lineCount: Int
)

data class ScriptExecutionSummary(
    val isSuccess: Boolean,
    val message: String,
    val totalStatements: Int,
    val executedCount: Int,
    val createdTables: List<String>,
    val elapsedMs: Long,
    val errorStatement: String? = null
)

object SqlScriptHelper {

    private val CREATE_TABLE_PATTERN = Pattern.compile(
        """(?i)CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?["`]?([a-zA-Z0-9_]+)["`]?""",
        Pattern.CASE_INSENSITIVE
    )

    private val CREATE_INDEX_PATTERN = Pattern.compile(
        """(?i)CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF\s+NOT\s+EXISTS\s+)?["`]?([a-zA-Z0-9_]+)["`]?""",
        Pattern.CASE_INSENSITIVE
    )

    private val INSERT_INTO_PATTERN = Pattern.compile(
        """(?i)INSERT\s+INTO\s+["`]?([a-zA-Z0-9_]+)["`]?""",
        Pattern.CASE_INSENSITIVE
    )

    /**
     * Splits a multi-statement SQL script into individual executable statements.
     * Accurately handles:
     * - Single line comments (-- ...)
     * - Multi-line block comments
     * - String literals with escaped quotes
     * - Identifiers in double quotes or backticks
     * - Semicolons inside quotes are ignored and not treated as statement delimiters
     */
    fun splitScriptIntoStatements(script: String): List<String> {
        val statements = mutableListOf<String>()
        val currentStmt = StringBuilder()

        var inSingleQuote = false
        var inDoubleQuote = false
        var inBacktick = false
        var inLineComment = false
        var inBlockComment = false

        val chars = script.toCharArray()
        var i = 0
        while (i < chars.size) {
            val c = chars[i]
            val nextC = if (i + 1 < chars.size) chars[i + 1] else '\u0000'

            // Handle start of line comment
            if (!inSingleQuote && !inDoubleQuote && !inBacktick && !inBlockComment && !inLineComment) {
                if (c == '-' && nextC == '-') {
                    inLineComment = true
                    i += 2
                    continue
                }
            }

            // Inside line comment: advance until end of line
            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false
                    currentStmt.append('\n')
                }
                i++
                continue
            }

            // Handle start of block comment
            if (!inSingleQuote && !inDoubleQuote && !inBacktick && !inLineComment && !inBlockComment) {
                if (c == '/' && nextC == '*') {
                    inBlockComment = true
                    i += 2
                    continue
                }
            }

            // Inside block comment: advance until end of comment
            if (inBlockComment) {
                if (c == '*' && nextC == '/') {
                    inBlockComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }

            // Handle single quotes (strings)
            if (c == '\'' && !inDoubleQuote && !inBacktick) {
                // Check for escaped quote in standard SQL ('')
                if (inSingleQuote && nextC == '\'') {
                    currentStmt.append("''")
                    i += 2
                    continue
                }
                inSingleQuote = !inSingleQuote
                currentStmt.append(c)
                i++
                continue
            }

            // Handle double quotes (identifiers)
            if (c == '"' && !inSingleQuote && !inBacktick) {
                inDoubleQuote = !inDoubleQuote
                currentStmt.append(c)
                i++
                continue
            }

            // Handle backticks (MySQL / SQLite backtick identifiers)
            if (c == '`' && !inSingleQuote && !inDoubleQuote) {
                inBacktick = !inBacktick
                currentStmt.append(c)
                i++
                continue
            }

            // Statement delimiter: semicolon outside any quote or comment
            if (c == ';' && !inSingleQuote && !inDoubleQuote && !inBacktick) {
                val stmt = currentStmt.toString().trim()
                if (stmt.isNotEmpty()) {
                    statements.add(stmt)
                }
                currentStmt.clear()
                i++
                continue
            }

            currentStmt.append(c)
            i++
        }

        val remaining = currentStmt.toString().trim()
        if (remaining.isNotEmpty()) {
            statements.add(remaining)
        }

        return statements
    }

    /**
     * Extracts table names created by CREATE TABLE statements in the script.
     */
    fun extractCreatedTableNames(script: String): List<String> {
        val tables = mutableListOf<String>()
        val matcher = CREATE_TABLE_PATTERN.matcher(script)
        while (matcher.find()) {
            val name = matcher.group(1)
            if (name != null && !tables.contains(name)) {
                tables.add(name)
            }
        }
        return tables
    }

    /**
     * Extracts index names created by CREATE INDEX statements in the script.
     */
    fun extractCreatedIndexNames(script: String): List<String> {
        val indexes = mutableListOf<String>()
        val matcher = CREATE_INDEX_PATTERN.matcher(script)
        while (matcher.find()) {
            val name = matcher.group(1)
            if (name != null && !indexes.contains(name)) {
                indexes.add(name)
            }
        }
        return indexes
    }

    /**
     * Counts the number of INSERT INTO statements in the script.
     */
    fun countInserts(script: String): Int {
        var count = 0
        val matcher = INSERT_INTO_PATTERN.matcher(script)
        while (matcher.find()) {
            count++
        }
        return count
    }

    /**
     * Reads a SQL file from a content Uri (e.g. from SAF OpenDocument/GetContent picker).
     */
    fun readSqlFromUri(context: Context, uri: Uri): Result<LoadedSqlFile> {
        return runCatching {
            var fileName = uri.lastPathSegment ?: "archivo.sql"
            var fileSize = 0L

            // Try to resolve display name and size from ContentResolver for content URIs
            try {
                if (uri.scheme == "content") {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) {
                                cursor.getString(nameIndex)?.let { fileName = it }
                            }
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val rawSql = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")

            if (fileSize == 0L) {
                fileSize = rawSql.toByteArray(Charsets.UTF_8).size.toLong()
            }

            val statements = splitScriptIntoStatements(rawSql)
            val tables = extractCreatedTableNames(rawSql)
            val indexes = extractCreatedIndexNames(rawSql)
            val inserts = countInserts(rawSql)
            val lineCount = rawSql.lines().size

            val sizeFormatted = formatFileSize(fileSize)

            LoadedSqlFile(
                fileName = fileName,
                fileSizeFormatted = sizeFormatted,
                rawSql = rawSql,
                statementCount = statements.size,
                tableNames = tables,
                indexNames = indexes,
                insertCount = inserts,
                lineCount = lineCount
            )
        }
    }

    /**
     * Parses raw SQL string content directly into LoadedSqlFile.
     */
    fun parseSqlContent(fileName: String, rawSql: String): LoadedSqlFile {
        val statements = splitScriptIntoStatements(rawSql)
        val tables = extractCreatedTableNames(rawSql)
        val indexes = extractCreatedIndexNames(rawSql)
        val inserts = countInserts(rawSql)
        val lineCount = rawSql.lines().size
        val sizeBytes = rawSql.toByteArray(Charsets.UTF_8).size.toLong()

        return LoadedSqlFile(
            fileName = fileName,
            fileSizeFormatted = formatFileSize(sizeBytes),
            rawSql = rawSql,
            statementCount = statements.size,
            tableNames = tables,
            indexNames = indexes,
            insertCount = inserts,
            lineCount = lineCount
        )
    }

    /**
     * Executes a complete SQL script (with multiple DDL/DML statements) inside a single transaction.
     */
    fun executeSqlScript(db: SQLiteDatabase, script: String): ScriptExecutionSummary {
        val startTime = System.nanoTime()
        val statements = splitScriptIntoStatements(script)
        if (statements.isEmpty()) {
            return ScriptExecutionSummary(
                isSuccess = true,
                message = "El script no contiene sentencias SQL ejecutables.",
                totalStatements = 0,
                executedCount = 0,
                createdTables = emptyList(),
                elapsedMs = 0L
            )
        }

        val tablesCreated = extractCreatedTableNames(script)
        var executedCount = 0
        var currentStmtText = ""

        try {
            db.beginTransaction()
            for (stmt in statements) {
                currentStmtText = stmt
                db.execSQL(stmt)
                executedCount++
            }
            db.setTransactionSuccessful()

            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            val tableMsg = if (tablesCreated.isNotEmpty()) {
                " (${tablesCreated.size} tablas: ${tablesCreated.joinToString(", ")})"
            } else ""

            val message = "Script ejecutado con éxito: $executedCount de ${statements.size} sentencias aplicadas en $elapsedMs ms$tableMsg."

            return ScriptExecutionSummary(
                isSuccess = true,
                message = message,
                totalStatements = statements.size,
                executedCount = executedCount,
                createdTables = tablesCreated,
                elapsedMs = elapsedMs
            )
        } catch (e: Exception) {
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
            val snippet = if (currentStmtText.length > 80) currentStmtText.take(80) + "..." else currentStmtText
            return ScriptExecutionSummary(
                isSuccess = false,
                message = "Error en la sentencia ${executedCount + 1}: ${e.localizedMessage ?: e.message} -> [$snippet]",
                totalStatements = statements.size,
                executedCount = executedCount,
                createdTables = tablesCreated,
                elapsedMs = elapsedMs,
                errorStatement = currentStmtText
            )
        } finally {
            try {
                db.endTransaction()
            } catch (_: Exception) {}
        }
    }

    /**
     * Generates a sample .sql file on the device storage with realistic tables,
     * foreign keys, indexes, and sample data. Allows instant testing of file loading on device or emulator.
     */
    fun createSampleSqlFile(context: Context, filename: String = "empresa_erp_estructura.sql"): File {
        val file = File(context.cacheDir, filename)
        val sqlContent = """
            -- ========================================================
            -- Script SQL: Estructura y Datos de Sistema Empresarial
            -- Generado para importación directa en SQLite
            -- ========================================================

            -- Tabla de Departamentos
            CREATE TABLE IF NOT EXISTS departamentos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo TEXT NOT NULL UNIQUE,
                nombre TEXT NOT NULL,
                presupuesto_anual REAL DEFAULT 50000.0,
                activo INTEGER DEFAULT 1
            );

            -- Tabla de Empleados con Clave Foránea a Departamentos
            CREATE TABLE IF NOT EXISTS empleados (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                apellidos TEXT NOT NULL,
                email TEXT UNIQUE,
                cargo TEXT DEFAULT 'Especialista',
                salario REAL NOT NULL,
                fecha_contratacion TEXT NOT NULL,
                departamento_id INTEGER,
                FOREIGN KEY (departamento_id) REFERENCES departamentos(id) ON DELETE SET NULL
            );

            -- Tabla de Proyectos
            CREATE TABLE IF NOT EXISTS proyectos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                titulo TEXT NOT NULL,
                estado TEXT CHECK(estado IN ('Planificado', 'En Progreso', 'Completado', 'Cancelado')),
                presupuesto REAL,
                fecha_inicio TEXT,
                fecha_fin TEXT
            );

            -- Tabla Intermedia de Asignaciones (Muchos a Muchos)
            CREATE TABLE IF NOT EXISTS asignaciones (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                empleado_id INTEGER NOT NULL,
                proyecto_id INTEGER NOT NULL,
                horas_semanales REAL DEFAULT 20.0,
                FOREIGN KEY (empleado_id) REFERENCES empleados(id) ON DELETE CASCADE,
                FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE
            );

            -- Índices para optimizar consultas frecuentes
            CREATE INDEX IF NOT EXISTS idx_empleados_depto ON empleados(departamento_id);
            CREATE INDEX IF NOT EXISTS idx_asignaciones_emp_proy ON asignaciones(empleado_id, proyecto_id);

            -- Datos Iniciales de Departamentos
            INSERT INTO departamentos (codigo, nombre, presupuesto_anual, activo) VALUES 
                ('TI', 'Tecnología e Infraestructura', 180000.00, 1),
                ('MKT', 'Marketing Digital y Ventas', 95000.00, 1),
                ('RRHH', 'Talento y Recursos Humanos', 60000.00, 1),
                ('FIN', 'Finanzas y Contabilidad', 110000.00, 1);

            -- Datos Iniciales de Empleados
            INSERT INTO empleados (nombre, apellidos, email, cargo, salario, fecha_contratacion, departamento_id) VALUES 
                ('Alejandro', 'Ruiz Morales', 'alejandro.ruiz@empresa.com', 'Senior Tech Lead', 54000.00, '2023-01-15', 1),
                ('Beatriz', 'Fernández Gil', 'beatriz.fernandez@empresa.com', 'Fullstack Engineer', 42000.00, '2023-06-01', 1),
                ('Carmen', 'Navarro Ortiz', 'carmen.navarro@empresa.com', 'Marketing Lead', 38000.00, '2023-03-10', 2),
                ('Daniel', 'Herrera Torres', 'daniel.herrera@empresa.com', 'Analista Financiero', 39500.00, '2022-11-20', 4),
                ('Elena', 'Vega Romero', 'elena.vega@empresa.com', 'HR Specialist', 33000.00, '2024-02-01', 3),
                ('Fernando', 'Serrano Castro', 'fernando.serrano@empresa.com', 'Data Analyst', 41000.00, '2023-09-15', 1);

            -- Datos Iniciales de Proyectos
            INSERT INTO proyectos (titulo, estado, presupuesto, fecha_inicio, fecha_fin) VALUES 
                ('Migración Cloud y Microservicios', 'En Progreso', 75000.00, '2026-01-10', '2026-11-30'),
                ('Campaña Expansión Internacional', 'Planificado', 40000.00, '2026-05-01', '2026-09-30'),
                ('Portal de Autoservicio de Empleados', 'Completado', 22000.00, '2025-08-01', '2026-02-15');

            -- Asignaciones de Empleados a Proyectos
            INSERT INTO asignaciones (empleado_id, proyecto_id, horas_semanales) VALUES 
                (1, 1, 30.0),
                (2, 1, 40.0),
                (6, 1, 20.0),
                (3, 2, 35.0),
                (5, 3, 15.0);
        """.trimIndent()

        FileOutputStream(file).use { fos ->
            fos.write(sqlContent.toByteArray(Charsets.UTF_8))
        }
        return file
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        }
    }
}

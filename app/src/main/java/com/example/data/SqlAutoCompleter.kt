package com.example.data

import com.example.model.TableSchemaDetail
import com.example.ui.components.SqlSyntaxVisualTransformation

enum class SuggestionCategory(val label: String) {
    KEYWORD("SQL"),
    TABLE("Tabla"),
    COLUMN("Columna"),
    FUNCTION("Función"),
    SNIPPET("Plantilla"),
    OPERATOR("Operador")
}

data class SqlSuggestion(
    val insertText: String,
    val displayText: String = insertText,
    val category: SuggestionCategory,
    val detail: String? = null,
    val cursorOffsetFromEnd: Int = 0
)

object SqlAutoCompleter {

    // Common clauses and keywords for fast suggestions
    val COMMON_KEYWORDS = listOf(
        "SELECT", "FROM", "WHERE", "JOIN", "INNER JOIN", "LEFT JOIN", "ON",
        "ORDER BY", "GROUP BY", "HAVING", "LIMIT", "OFFSET",
        "AND", "OR", "NOT", "IN", "LIKE", "IS NULL", "IS NOT NULL", "BETWEEN",
        "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM",
        "CREATE TABLE", "DROP TABLE", "ALTER TABLE", "DISTINCT", "AS"
    )

    val COMMON_FUNCTIONS = listOf(
        SqlSuggestion("COUNT(*)", "COUNT(*)", SuggestionCategory.FUNCTION, "Conteo de filas"),
        SqlSuggestion("COUNT()", "COUNT()", SuggestionCategory.FUNCTION, "Conteo de valores no nulos", cursorOffsetFromEnd = 1),
        SqlSuggestion("SUM()", "SUM()", SuggestionCategory.FUNCTION, "Suma acumulada", cursorOffsetFromEnd = 1),
        SqlSuggestion("AVG()", "AVG()", SuggestionCategory.FUNCTION, "Promedio aritmético", cursorOffsetFromEnd = 1),
        SqlSuggestion("MIN()", "MIN()", SuggestionCategory.FUNCTION, "Valor mínimo", cursorOffsetFromEnd = 1),
        SqlSuggestion("MAX()", "MAX()", SuggestionCategory.FUNCTION, "Valor máximo", cursorOffsetFromEnd = 1),
        SqlSuggestion("ROUND(, 2)", "ROUND(col, 2)", SuggestionCategory.FUNCTION, "Redondear decimales", cursorOffsetFromEnd = 4),
        SqlSuggestion("COALESCE(, '')", "COALESCE(col, val)", SuggestionCategory.FUNCTION, "Valor alternativo si es NULL", cursorOffsetFromEnd = 5),
        SqlSuggestion("UPPER()", "UPPER()", SuggestionCategory.FUNCTION, "Convertir a mayúsculas", cursorOffsetFromEnd = 1),
        SqlSuggestion("LOWER()", "LOWER()", SuggestionCategory.FUNCTION, "Convertir a minúsculas", cursorOffsetFromEnd = 1),
        SqlSuggestion("STRFTIME('%Y-%m-%d', )", "STRFTIME()", SuggestionCategory.FUNCTION, "Formato de fecha", cursorOffsetFromEnd = 1)
    )

    fun getSuggestions(
        fullText: String,
        cursorIndex: Int,
        availableTables: List<String> = emptyList(),
        availableColumns: List<String> = emptyList(),
        tableSchemas: List<TableSchemaDetail> = emptyList()
    ): List<SqlSuggestion> {
        val safeCursor = cursorIndex.coerceIn(0, fullText.length)
        val textBeforeCursor = fullText.substring(0, safeCursor)
        val currentWord = extractCurrentWord(fullText, safeCursor)

        // Detect if user typed a table prefix with dot, e.g. "clientes." or "c."
        val dotQualifier = extractTableDotQualifier(textBeforeCursor)
        if (dotQualifier != null) {
            val matchingTable = tableSchemas.find { it.tableName.equals(dotQualifier, ignoreCase = true) }
            if (matchingTable != null) {
                return matchingTable.columns
                    .filter { it.name.startsWith(currentWord, ignoreCase = true) }
                    .map { col ->
                        SqlSuggestion(
                            insertText = col.name,
                            displayText = col.name,
                            category = SuggestionCategory.COLUMN,
                            detail = "${matchingTable.tableName} (${col.type})"
                        )
                    }
            }
        }

        // If the user has typed 1 or more characters of a word
        if (currentWord.isNotEmpty()) {
            return getPrefixMatchingSuggestions(
                currentWord = currentWord,
                fullText = fullText,
                availableTables = availableTables,
                availableColumns = availableColumns,
                tableSchemas = tableSchemas
            )
        }

        // If current word is empty, provide context-based recommendations
        return getContextAwareSuggestions(
            textBeforeCursor = textBeforeCursor,
            availableTables = availableTables,
            availableColumns = availableColumns,
            tableSchemas = tableSchemas
        )
    }

    private fun getPrefixMatchingSuggestions(
        currentWord: String,
        fullText: String,
        availableTables: List<String>,
        availableColumns: List<String>,
        tableSchemas: List<TableSchemaDetail>
    ): List<SqlSuggestion> {
        val results = mutableListOf<SqlSuggestion>()
        val referencedTableNames = extractReferencedTables(fullText, availableTables)

        // 1. Common Keywords matching prefix
        COMMON_KEYWORDS.filter { it.startsWith(currentWord, ignoreCase = true) }
            .forEach { kw ->
                results.add(
                    SqlSuggestion(
                        insertText = "$kw ",
                        displayText = kw,
                        category = SuggestionCategory.KEYWORD,
                        detail = "Palabra clave SQL"
                    )
                )
            }

        // 2. Schema Tables matching prefix
        availableTables.filter { it.startsWith(currentWord, ignoreCase = true) || it.contains(currentWord, ignoreCase = true) }
            .forEach { tbl ->
                val schema = tableSchemas.find { it.tableName == tbl }
                val colCount = schema?.columns?.size ?: 0
                results.add(
                    SqlSuggestion(
                        insertText = "$tbl ",
                        displayText = tbl,
                        category = SuggestionCategory.TABLE,
                        detail = "Tabla del esquema ($colCount columnas)"
                    )
                )
            }

        // 3. Schema Columns matching prefix (prioritizing tables already mentioned in query)
        val colSuggestions = mutableListOf<SqlSuggestion>()
        if (tableSchemas.isNotEmpty()) {
            for (schema in tableSchemas) {
                val isReferenced = referencedTableNames.contains(schema.tableName)
                for (col in schema.columns) {
                    if (col.name.startsWith(currentWord, ignoreCase = true) || col.name.contains(currentWord, ignoreCase = true)) {
                        val pkBadge = if (col.isPrimaryKey) " [PK]" else ""
                        val priorityPrefix = if (isReferenced) "" else "(${schema.tableName}) "
                        colSuggestions.add(
                            SqlSuggestion(
                                insertText = col.name,
                                displayText = "$priorityPrefix${col.name}",
                                category = SuggestionCategory.COLUMN,
                                detail = "${schema.tableName}.${col.name} ${col.type}$pkBadge"
                            )
                        )
                    }
                }
            }
        } else {
            availableColumns.filter { it.startsWith(currentWord, ignoreCase = true) || it.contains(currentWord, ignoreCase = true) }
                .forEach { col ->
                    colSuggestions.add(
                        SqlSuggestion(
                            insertText = col,
                            displayText = col,
                            category = SuggestionCategory.COLUMN,
                            detail = "Columna de la base de datos"
                        )
                    )
                }
        }
        results.addAll(colSuggestions.distinctBy { it.displayText })

        // 4. SQL Functions matching prefix
        COMMON_FUNCTIONS.filter { it.displayText.startsWith(currentWord, ignoreCase = true) }
            .forEach { results.add(it) }

        // 5. Data types matching prefix (if typing in table creation)
        SqlSyntaxVisualTransformation.DATA_TYPES.filter { it.startsWith(currentWord, ignoreCase = true) }
            .take(4)
            .forEach { dt ->
                results.add(
                    SqlSuggestion(
                        insertText = "$dt ",
                        displayText = dt,
                        category = SuggestionCategory.KEYWORD,
                        detail = "Tipo de dato SQLite"
                    )
                )
            }

        // 6. Schema Snippets if matching beginning of statement
        if (currentWord.equals("s", ignoreCase = true) || currentWord.equals("sel", ignoreCase = true)) {
            val sampleTable = availableTables.firstOrNull() ?: "clientes"
            results.add(
                SqlSuggestion(
                    insertText = "SELECT * FROM $sampleTable WHERE ",
                    displayText = "SELECT * FROM $sampleTable",
                    category = SuggestionCategory.SNIPPET,
                    detail = "Consulta rápida a tabla '$sampleTable'"
                )
            )
        } else if (currentWord.equals("i", ignoreCase = true) || currentWord.equals("ins", ignoreCase = true)) {
            val sampleTable = availableTables.firstOrNull() ?: "clientes"
            results.add(
                SqlSuggestion(
                    insertText = "INSERT INTO $sampleTable ",
                    displayText = "INSERT INTO $sampleTable",
                    category = SuggestionCategory.SNIPPET,
                    detail = "Inserción en '$sampleTable'"
                )
            )
        }

        return results.distinctBy { it.insertText }.take(12)
    }

    private fun getContextAwareSuggestions(
        textBeforeCursor: String,
        availableTables: List<String>,
        availableColumns: List<String>,
        tableSchemas: List<TableSchemaDetail>
    ): List<SqlSuggestion> {
        val trimmed = textBeforeCursor.trim()
        val upper = trimmed.uppercase()
        val results = mutableListOf<SqlSuggestion>()

        // 1. Beginning of query or blank
        if (trimmed.isEmpty()) {
            results.add(SqlSuggestion("SELECT ", "SELECT", SuggestionCategory.KEYWORD, "Consultar registros"))
            val firstTable = availableTables.firstOrNull()
            if (firstTable != null) {
                results.add(
                    SqlSuggestion(
                        insertText = "SELECT * FROM $firstTable;\n",
                        displayText = "SELECT * FROM $firstTable",
                        category = SuggestionCategory.SNIPPET,
                        detail = "Consulta completa a la tabla principal"
                    )
                )
            }
            results.add(SqlSuggestion("INSERT INTO ", "INSERT INTO", SuggestionCategory.KEYWORD, "Insertar nueva fila"))
            results.add(SqlSuggestion("UPDATE ", "UPDATE", SuggestionCategory.KEYWORD, "Modificar registros"))
            results.add(SqlSuggestion("DELETE FROM ", "DELETE FROM", SuggestionCategory.KEYWORD, "Eliminar registros"))
            results.add(SqlSuggestion("CREATE TABLE ", "CREATE TABLE", SuggestionCategory.KEYWORD, "Crear nueva tabla"))
            return results
        }

        // 2. Right after FROM or JOIN or INTO
        if (upper.endsWith("FROM") || upper.endsWith("JOIN") || upper.endsWith("INTO") ||
            upper.endsWith("TABLE") || upper.endsWith("UPDATE")) {
            // Priority: Schema Tables!
            availableTables.forEach { tbl ->
                val schema = tableSchemas.find { it.tableName == tbl }
                val colNames = schema?.columns?.take(4)?.joinToString(", ") { it.name } ?: ""
                val desc = if (colNames.isNotEmpty()) "Cols: $colNames" else "Tabla"
                results.add(
                    SqlSuggestion(
                        insertText = "$tbl ",
                        displayText = tbl,
                        category = SuggestionCategory.TABLE,
                        detail = desc
                    )
                )
            }
            return results
        }

        // 3. Right after SELECT
        if (upper.endsWith("SELECT") || upper.endsWith("SELECT DISTINCT") || upper.endsWith(",")) {
            results.add(SqlSuggestion("* ", "* (todas)", SuggestionCategory.KEYWORD, "Todas las columnas"))
            // Add columns from schema
            val referencedTables = extractReferencedTables(textBeforeCursor, availableTables)
            val candidateSchemas = if (referencedTables.isNotEmpty()) {
                tableSchemas.filter { referencedTables.contains(it.tableName) }
            } else tableSchemas

            candidateSchemas.flatMap { s ->
                s.columns.map { c ->
                    SqlSuggestion(
                        insertText = "${c.name}, ",
                        displayText = c.name,
                        category = SuggestionCategory.COLUMN,
                        detail = "${s.tableName}.${c.name} (${c.type})"
                    )
                }
            }.take(8).forEach { results.add(it) }

            results.add(SqlSuggestion("COUNT(*) ", "COUNT(*)", SuggestionCategory.FUNCTION, "Contar registros"))
            results.add(SqlSuggestion("FROM ", "FROM", SuggestionCategory.KEYWORD, "Especificar tabla origen"))
            return results
        }

        // 4. After a table name in FROM
        val referencedTables = extractReferencedTables(textBeforeCursor, availableTables)
        if (referencedTables.isNotEmpty() && !upper.contains("WHERE") && !upper.contains("ORDER BY") && !upper.contains("LIMIT")) {
            results.add(SqlSuggestion("WHERE ", "WHERE", SuggestionCategory.KEYWORD, "Filtrar resultados con condición"))
            results.add(SqlSuggestion("ORDER BY ", "ORDER BY", SuggestionCategory.KEYWORD, "Ordenar registros"))
            results.add(SqlSuggestion("GROUP BY ", "GROUP BY", SuggestionCategory.KEYWORD, "Agrupar resultados"))
            results.add(SqlSuggestion("INNER JOIN ", "INNER JOIN", SuggestionCategory.KEYWORD, "Unir con otra tabla"))
            results.add(SqlSuggestion("LEFT JOIN ", "LEFT JOIN", SuggestionCategory.KEYWORD, "Unión izquierda"))
            results.add(SqlSuggestion("LIMIT 10;", "LIMIT 10;", SuggestionCategory.KEYWORD, "Limitar a 10 resultados"))
            return results
        }

        // 5. Right after WHERE, AND, OR, ON, HAVING
        if (upper.endsWith("WHERE") || upper.endsWith("AND") || upper.endsWith("OR") || upper.endsWith("ON") || upper.endsWith("HAVING")) {
            val candidateSchemas = if (referencedTables.isNotEmpty()) {
                tableSchemas.filter { referencedTables.contains(it.tableName) }
            } else tableSchemas

            candidateSchemas.flatMap { s ->
                s.columns.map { c ->
                    SqlSuggestion(
                        insertText = "${c.name} ",
                        displayText = c.name,
                        category = SuggestionCategory.COLUMN,
                        detail = "${s.tableName}.${c.name} (${c.type})"
                    )
                }
            }.take(8).forEach { results.add(it) }

            return results
        }

        // 6. Generic intelligent fallback suggestions
        results.add(SqlSuggestion("WHERE ", "WHERE", SuggestionCategory.KEYWORD, "Filtrar por condición"))
        results.add(SqlSuggestion("AND ", "AND", SuggestionCategory.KEYWORD, "Condición adicional"))
        results.add(SqlSuggestion("OR ", "OR", SuggestionCategory.KEYWORD, "Condición alternativa"))
        results.add(SqlSuggestion("ORDER BY ", "ORDER BY", SuggestionCategory.KEYWORD, "Ordenar filas"))
        results.add(SqlSuggestion("LIMIT ", "LIMIT", SuggestionCategory.KEYWORD, "Número máximo de filas"))

        return results
    }

    fun extractCurrentWord(text: String, cursor: Int): String {
        if (cursor <= 0 || cursor > text.length) return ""
        var start = cursor - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_' || text[start] == '*')) {
            start--
        }
        return text.substring(start + 1, cursor)
    }

    private fun extractTableDotQualifier(textBeforeCursor: String): String? {
        val trimmed = textBeforeCursor.trimEnd()
        val lastDotIndex = trimmed.lastIndexOf('.')
        if (lastDotIndex <= 0) return null
        var start = lastDotIndex - 1
        while (start >= 0 && (trimmed[start].isLetterOrDigit() || trimmed[start] == '_')) {
            start--
        }
        val qualifier = trimmed.substring(start + 1, lastDotIndex)
        return qualifier.ifEmpty { null }
    }

    private fun extractReferencedTables(sql: String, availableTables: List<String>): List<String> {
        val found = mutableListOf<String>()
        val upper = sql.uppercase()
        for (tbl in availableTables) {
            val pattern = "\\b${tbl.uppercase()}\\b".toRegex()
            if (pattern.containsMatchIn(upper)) {
                found.add(tbl)
            }
        }
        return found
    }
}

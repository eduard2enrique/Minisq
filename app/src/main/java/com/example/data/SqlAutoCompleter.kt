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
    val cursorOffsetFromEnd: Int = 0,
    val tableTag: String? = null
)

object SqlAutoCompleter {

    data class TableRef(
        val tableName: String,
        val alias: String? = null
    )

    private enum class SqlClause {
        NONE,
        SELECT,
        FROM,
        JOIN,
        ON,
        WHERE,
        GROUP_BY,
        HAVING,
        ORDER_BY,
        LIMIT,
        OFFSET,
        INSERT,
        INTO,
        VALUES,
        UPDATE,
        SET,
        CREATE,
        ALTER,
        DROP
    }

    private enum class SqlTokenKind {
        WORD,
        NUMBER,
        STRING,
        OPERATOR,
        COMMA,
        DOT,
        OPEN_PAREN,
        CLOSE_PAREN
    }

    private data class SqlToken(
        val text: String,
        val upper: String,
        val kind: SqlTokenKind
    )

    private data class StatementSlice(
        val statementFull: String,
        val statementBeforeWord: String,
        val insideUnclosedQuoteOrComment: Boolean
    )

    // Common clauses and keywords for fallback matching
    val COMMON_KEYWORDS = listOf(
        "SELECT", "FROM", "WHERE", "JOIN", "INNER JOIN", "LEFT JOIN", "ON",
        "ORDER BY", "GROUP BY", "HAVING", "LIMIT", "OFFSET",
        "AND", "OR", "NOT", "IN", "LIKE", "IS NULL", "IS NOT NULL", "BETWEEN",
        "ASC", "DESC", "INSERT INTO", "VALUES", "UPDATE", "SET", "DELETE FROM",
        "CREATE TABLE", "DROP TABLE", "ALTER TABLE", "DISTINCT", "AS"
    )

    private val NON_ALIAS_KEYWORDS = setOf(
        "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "CROSS", "FULL", "OUTER", "NATURAL",
        "ON", "USING", "ORDER", "GROUP", "HAVING", "LIMIT", "OFFSET", "UNION",
        "EXCEPT", "INTERSECT", "SET", "VALUES", "SELECT", "FROM", "AS", "AND",
        "OR", "NOT", "INTO", "WHEN", "ELSE", "END", "FOR", "IF", "EXISTS"
    )

    private val COMPARISON_OPERATORS = setOf("=", "!=", "<>", "<", ">", "<=", ">=", "LIKE", "IN", "BETWEEN")

    val COMMON_FUNCTIONS = listOf(
        SqlSuggestion("COUNT(*) ", "COUNT(*)", SuggestionCategory.FUNCTION, "Conteo de filas"),
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
        val currentWord = extractCurrentWord(fullText, safeCursor)
        val wordStart = (safeCursor - currentWord.length).coerceAtLeast(0)

        val slice = extractCurrentStatementSlice(fullText, safeCursor, wordStart)
        if (slice.insideUnclosedQuoteOrComment) {
            return emptyList()
        }

        val canonicalTables = if (availableTables.isNotEmpty()) {
            availableTables
        } else {
            tableSchemas.map { it.tableName }
        }

        // Parse table references (and aliases) across the entire current statement so FROM/JOIN
        // declared after the cursor (e.g. when editing SELECT columns) are still recognized.
        val tableRefs = extractTableReferences(slice.statementFull, canonicalTables)

        // 1. Dot-qualified column completion: e.g. "clientes." or "c." or "c.nom"
        val dotQualifier = extractTableDotQualifier(fullText.substring(0, wordStart))
        if (dotQualifier != null) {
            val resolvedTableName = tableRefs.find {
                it.alias.equals(dotQualifier, ignoreCase = true) ||
                    it.tableName.equals(dotQualifier, ignoreCase = true)
            }?.tableName ?: canonicalTables.find { it.equals(dotQualifier, ignoreCase = true) }

            val matchingSchema = resolvedTableName?.let { name ->
                tableSchemas.find { it.tableName.equals(name, ignoreCase = true) }
            }

            if (matchingSchema != null) {
                val qualifiedCols = matchingSchema.columns
                    .let { cols ->
                        if (currentWord.isEmpty()) cols
                        else filterByWordMatch(cols, currentWord) { it.name }
                    }
                    .map { col ->
                        val pkBadge = if (col.isPrimaryKey) " [PK]" else ""
                        SqlSuggestion(
                            insertText = "${col.name} ",
                            displayText = col.name,
                            category = SuggestionCategory.COLUMN,
                            detail = "${matchingSchema.tableName}.${col.name} (${col.type}$pkBadge)",
                            tableTag = matchingSchema.tableName
                        )
                    }
                if (qualifiedCols.isNotEmpty()) return qualifiedCols
            }
        }

        // 2. Determine contextual candidates from the active SQL clause before currentWord
        val contextualCandidates = buildClauseCandidates(
            statementBeforeWord = slice.statementBeforeWord,
            tableRefs = tableRefs,
            availableTables = canonicalTables,
            availableColumns = availableColumns,
            tableSchemas = tableSchemas
        )

        // 3. When no prefix is typed yet, return clause-driven suggestions directly
        if (currentWord.isEmpty()) {
            return deduplicateSuggestions(contextualCandidates)
        }

        // 4. When typing a prefix, first filter contextual candidates for the active clause
        val filteredContextual = filterSuggestionsByPrefix(contextualCandidates, currentWord)
        if (filteredContextual.isNotEmpty()) {
            return deduplicateSuggestions(filteredContextual).take(16)
        }

        // 5. Fallback prefix matching (for non-standard expressions, DDL data types, or functions)
        val fallback = buildFallbackPrefixSuggestions(
            currentWord = currentWord,
            tableRefs = tableRefs,
            availableTables = canonicalTables,
            availableColumns = availableColumns,
            tableSchemas = tableSchemas
        )
        return deduplicateSuggestions(fallback).take(14)
    }

    private fun buildClauseCandidates(
        statementBeforeWord: String,
        tableRefs: List<TableRef>,
        availableTables: List<String>,
        availableColumns: List<String>,
        tableSchemas: List<TableSchemaDetail>
    ): List<SqlSuggestion> {
        val tokens = tokenizeSql(statementBeforeWord)
        val results = mutableListOf<SqlSuggestion>()

        // Case 1: Start of empty statement
        if (tokens.isEmpty()) {
            results.add(SqlSuggestion("SELECT ", "SELECT", SuggestionCategory.KEYWORD, "Consultar registros"))
            availableTables.take(3).forEach { tbl ->
                results.add(
                    SqlSuggestion(
                        insertText = "SELECT * FROM $tbl;\n",
                        displayText = "SELECT * FROM $tbl",
                        category = SuggestionCategory.SNIPPET,
                        detail = "Consulta rápida a '$tbl'",
                        tableTag = tbl
                    )
                )
            }
            results.add(SqlSuggestion("INSERT INTO ", "INSERT INTO", SuggestionCategory.KEYWORD, "Insertar nueva fila"))
            results.add(SqlSuggestion("UPDATE ", "UPDATE", SuggestionCategory.KEYWORD, "Modificar registros"))
            results.add(SqlSuggestion("DELETE FROM ", "DELETE FROM", SuggestionCategory.KEYWORD, "Eliminar registros"))
            results.add(SqlSuggestion("CREATE TABLE ", "CREATE TABLE", SuggestionCategory.KEYWORD, "Crear nueva tabla"))
            return results
        }

        val lastToken = tokens.last()
        val prevToken = tokens.getOrNull(tokens.size - 2)
        val clauseInfo = detectActiveClause(tokens)
        val activeClause = clauseInfo.clause

        // Case 2: Immediately after SELECT / SELECT DISTINCT / ',' inside SELECT projection
        if (activeClause == SqlClause.SELECT) {
            val expectsProjection = lastToken.upper == "SELECT" ||
                lastToken.upper == "DISTINCT" ||
                lastToken.kind == SqlTokenKind.COMMA ||
                lastToken.kind == SqlTokenKind.OPEN_PAREN ||
                (lastToken.kind == SqlTokenKind.OPERATOR && lastToken.upper in setOf("+", "-", "*", "/", "||"))

            if (expectsProjection) {
                if (lastToken.upper == "SELECT" || lastToken.kind == SqlTokenKind.COMMA) {
                    results.add(SqlSuggestion("* ", "* (todas)", SuggestionCategory.KEYWORD, "Todas las columnas"))
                }
                if (lastToken.upper == "SELECT") {
                    results.add(SqlSuggestion("DISTINCT ", "DISTINCT", SuggestionCategory.KEYWORD, "Valores sin duplicados"))
                }

                // Columns from referenced tables first, or all active DB tables clearly tagged
                results.addAll(
                    buildColumnSuggestions(
                        tableRefs = tableRefs,
                        tableSchemas = tableSchemas,
                        availableColumns = availableColumns,
                        trailingSpace = true,
                        maxItems = 24
                    )
                )

                results.addAll(COMMON_FUNCTIONS.take(5))
                results.add(SqlSuggestion("FROM ", "FROM", SuggestionCategory.KEYWORD, "Especificar tabla origen"))
                return results
            }

            if (lastToken.upper == "AS") {
                results.add(SqlSuggestion(", ", ", (otra columna)", SuggestionCategory.OPERATOR, "Agregar otra columna"))
                results.add(SqlSuggestion("FROM ", "FROM", SuggestionCategory.KEYWORD, "Especificar tabla origen"))
                return results
            }

            // Otherwise user already entered a column/expression/alias in SELECT:
            // Prioritize FROM and smart "FROM <table>" transitions matching that column's table!
            results.add(SqlSuggestion("FROM ", "FROM", SuggestionCategory.KEYWORD, "Elegir tabla origen"))

            val matchedTablesForColumn = findTablesOwningColumn(lastToken.text, tableSchemas)
            val orderedTables = (matchedTablesForColumn + availableTables).distinct()
            if (lastToken.upper != "*") {
                orderedTables.take(4).forEach { tbl ->
                    val hint = if (matchedTablesForColumn.contains(tbl)) {
                        "Tabla que contiene '${lastToken.text}'"
                    } else {
                        "Consultar desde '$tbl'"
                    }
                    results.add(
                        SqlSuggestion(
                            insertText = "FROM $tbl ",
                            displayText = "FROM $tbl",
                            category = SuggestionCategory.SNIPPET,
                            detail = hint,
                            tableTag = tbl
                        )
                    )
                }
                results.add(SqlSuggestion(", ", ", (otra columna)", SuggestionCategory.OPERATOR, "Agregar otra columna"))
                results.add(SqlSuggestion("AS ", "AS", SuggestionCategory.KEYWORD, "Asignar alias a la columna"))
            } else {
                orderedTables.take(4).forEach { tbl ->
                    results.add(
                        SqlSuggestion(
                            insertText = "FROM $tbl ",
                            displayText = "FROM $tbl",
                            category = SuggestionCategory.SNIPPET,
                            detail = "Consultar todas las columnas de '$tbl'",
                            tableTag = tbl
                        )
                    )
                }
            }
            return results
        }

        // Case 3: Expecting a table name after FROM, JOIN, INTO, UPDATE, DROP TABLE, ALTER TABLE
        val isExpectingTable = (activeClause in setOf(SqlClause.FROM, SqlClause.JOIN, SqlClause.INTO, SqlClause.UPDATE, SqlClause.DROP, SqlClause.ALTER)) &&
            (lastToken.upper in setOf("FROM", "JOIN", "INTO", "UPDATE", "TABLE") || lastToken.kind == SqlTokenKind.COMMA)

        if (isExpectingTable) {
            availableTables.forEach { tbl ->
                val schema = tableSchemas.find { it.tableName.equals(tbl, ignoreCase = true) }
                val colNames = schema?.columns?.take(4)?.joinToString(", ") { it.name } ?: ""
                val desc = if (colNames.isNotEmpty()) "Cols: $colNames" else "Tabla de la base activa"
                results.add(
                    SqlSuggestion(
                        insertText = "$tbl ",
                        displayText = tbl,
                        category = SuggestionCategory.TABLE,
                        detail = desc,
                        tableTag = if (schema != null) "${schema.columns.size} cols" else null
                    )
                )
            }
            return results
        }

        // Case 4: Right after a table (or alias) in FROM or JOIN
        if (activeClause == SqlClause.JOIN) {
            // In a JOIN without ON yet: ON is top priority!
            results.add(SqlSuggestion("ON ", "ON", SuggestionCategory.KEYWORD, "Condición de unión entre tablas"))
            results.add(SqlSuggestion("WHERE ", "WHERE", SuggestionCategory.KEYWORD, "Filtrar resultados"))
            results.add(SqlSuggestion("INNER JOIN ", "INNER JOIN", SuggestionCategory.KEYWORD, "Unir con otra tabla"))
            results.add(SqlSuggestion("LEFT JOIN ", "LEFT JOIN", SuggestionCategory.KEYWORD, "Unión izquierda"))
            results.add(SqlSuggestion("ORDER BY ", "ORDER BY", SuggestionCategory.KEYWORD, "Ordenar resultados"))
            return results
        }

        if (activeClause == SqlClause.FROM) {
            results.add(SqlSuggestion("WHERE ", "WHERE", SuggestionCategory.KEYWORD, "Filtrar resultados con condición"))
            results.add(SqlSuggestion("ORDER BY ", "ORDER BY", SuggestionCategory.KEYWORD, "Ordenar registros"))
            results.add(SqlSuggestion("GROUP BY ", "GROUP BY", SuggestionCategory.KEYWORD, "Agrupar resultados"))
            results.add(SqlSuggestion("INNER JOIN ", "INNER JOIN", SuggestionCategory.KEYWORD, "Unir con otra tabla"))
            results.add(SqlSuggestion("LEFT JOIN ", "LEFT JOIN", SuggestionCategory.KEYWORD, "Unión izquierda"))
            results.add(SqlSuggestion("LIMIT 10;", "LIMIT 10;", SuggestionCategory.KEYWORD, "Limitar a 10 resultados"))
            return results
        }

        // Case 5: Inside WHERE, ON, HAVING, or SET
        if (activeClause in setOf(SqlClause.WHERE, SqlClause.ON, SqlClause.HAVING, SqlClause.SET)) {
            if (lastToken.upper in setOf("WHERE", "ON", "HAVING", "AND", "OR", "NOT", "SET") ||
                lastToken.kind == SqlTokenKind.OPEN_PAREN ||
                lastToken.kind == SqlTokenKind.COMMA
            ) {
                results.addAll(
                    buildColumnSuggestions(
                        tableRefs = tableRefs,
                        tableSchemas = tableSchemas,
                        availableColumns = availableColumns,
                        trailingSpace = true,
                        maxItems = 24
                    )
                )
                results.addAll(COMMON_FUNCTIONS.filter { it.displayText.startsWith("UPPER") || it.displayText.startsWith("LOWER") || it.displayText.startsWith("COALESCE") || it.displayText.startsWith("COUNT") })
                return results
            }

            if (lastToken.upper in COMPARISON_OPERATORS) {
                // If inside JOIN ON, offer columns from referenced tables first for FK = PK!
                if (activeClause == SqlClause.ON) {
                    results.addAll(
                        buildColumnSuggestions(
                            tableRefs = tableRefs,
                            tableSchemas = tableSchemas,
                            availableColumns = availableColumns,
                            trailingSpace = true,
                            maxItems = 16
                        )
                    )
                }
                results.add(SqlSuggestion("'' ", "'texto'", SuggestionCategory.OPERATOR, "Cadena de texto entre comillas", cursorOffsetFromEnd = 2))
                results.add(SqlSuggestion("'%texto%' ", "'%texto%'", SuggestionCategory.OPERATOR, "Patrón LIKE con comodines", cursorOffsetFromEnd = 3))
                results.add(SqlSuggestion("('', '') ", "('v1', 'v2')", SuggestionCategory.OPERATOR, "Lista de valores para IN", cursorOffsetFromEnd = 7))
                results.add(SqlSuggestion("NULL ", "NULL", SuggestionCategory.KEYWORD, "Valor nulo"))
                if (activeClause != SqlClause.ON) {
                    results.addAll(
                        buildColumnSuggestions(
                            tableRefs = tableRefs,
                            tableSchemas = tableSchemas,
                            availableColumns = availableColumns,
                            trailingSpace = true,
                            maxItems = 8
                        )
                    )
                }
                return results
            }

            if (lastToken.upper == "IS") {
                results.add(SqlSuggestion("NULL ", "NULL", SuggestionCategory.KEYWORD, "Valor nulo"))
                results.add(SqlSuggestion("NOT NULL ", "NOT NULL", SuggestionCategory.KEYWORD, "Valor no nulo"))
                return results
            }

            // Did user just type a column name in WHERE/ON/HAVING/SET? Offer comparison operators!
            val prevWasConditionStarter = prevToken != null && (
                prevToken.upper in setOf("WHERE", "ON", "HAVING", "AND", "OR", "NOT", "SET") ||
                    prevToken.kind == SqlTokenKind.OPEN_PAREN ||
                    prevToken.kind == SqlTokenKind.COMMA ||
                    prevToken.kind == SqlTokenKind.DOT
                )
            if (lastToken.kind == SqlTokenKind.WORD && prevWasConditionStarter) {
                results.add(SqlSuggestion("= ", "=", SuggestionCategory.OPERATOR, "Igual a"))
                results.add(SqlSuggestion("= '' ", "= 'texto'", SuggestionCategory.OPERATOR, "Comparar texto entre comillas", cursorOffsetFromEnd = 2))
                results.add(SqlSuggestion("!= ", "!=", SuggestionCategory.OPERATOR, "Distinto de"))
                results.add(SqlSuggestion("> ", ">", SuggestionCategory.OPERATOR, "Mayor que"))
                results.add(SqlSuggestion("< ", "<", SuggestionCategory.OPERATOR, "Menor que"))
                results.add(SqlSuggestion(">= ", ">=", SuggestionCategory.OPERATOR, "Mayor o igual que"))
                results.add(SqlSuggestion("<= ", "<=", SuggestionCategory.OPERATOR, "Menor o igual que"))
                results.add(SqlSuggestion("LIKE '%%' ", "LIKE '%patrón%'", SuggestionCategory.KEYWORD, "Búsqueda por patrón", cursorOffsetFromEnd = 3))
                results.add(SqlSuggestion("IN ('', '') ", "IN ('v1', 'v2')", SuggestionCategory.KEYWORD, "Coincide con lista", cursorOffsetFromEnd = 7))
                results.add(SqlSuggestion("IS NULL ", "IS NULL", SuggestionCategory.KEYWORD, "Es nulo"))
                results.add(SqlSuggestion("IS NOT NULL ", "IS NOT NULL", SuggestionCategory.KEYWORD, "No es nulo"))
                results.add(SqlSuggestion("BETWEEN ", "BETWEEN", SuggestionCategory.KEYWORD, "Rango entre dos valores"))
                return results
            }

            // Otherwise user completed a predicate value: offer logical connectors or next clauses
            results.add(SqlSuggestion("AND ", "AND", SuggestionCategory.KEYWORD, "Agregar condición obligatoria"))
            results.add(SqlSuggestion("OR ", "OR", SuggestionCategory.KEYWORD, "Agregar condición alternativa"))
            results.add(SqlSuggestion("ORDER BY ", "ORDER BY", SuggestionCategory.KEYWORD, "Ordenar resultados"))
            results.add(SqlSuggestion("GROUP BY ", "GROUP BY", SuggestionCategory.KEYWORD, "Agrupar resultados"))
            if (activeClause == SqlClause.GROUP_BY || tokens.any { it.upper == "GROUP" }) {
                results.add(SqlSuggestion("HAVING ", "HAVING", SuggestionCategory.KEYWORD, "Filtrar grupos"))
            }
            results.add(SqlSuggestion("LIMIT 10;", "LIMIT 10;", SuggestionCategory.KEYWORD, "Limitar resultados"))
            return results
        }

        // Case 6: GROUP BY / ORDER BY
        if (activeClause == SqlClause.GROUP_BY || activeClause == SqlClause.ORDER_BY) {
            if (lastToken.upper == "BY" || lastToken.kind == SqlTokenKind.COMMA) {
                results.addAll(
                    buildColumnSuggestions(
                        tableRefs = tableRefs,
                        tableSchemas = tableSchemas,
                        availableColumns = availableColumns,
                        trailingSpace = true,
                        maxItems = 24
                    )
                )
                if (activeClause == SqlClause.ORDER_BY) {
                    results.add(SqlSuggestion("COUNT(*) ", "COUNT(*)", SuggestionCategory.FUNCTION, "Ordenar por conteo"))
                }
                return results
            }
            if (activeClause == SqlClause.ORDER_BY && lastToken.upper !in setOf("ASC", "DESC")) {
                results.add(SqlSuggestion("DESC ", "DESC", SuggestionCategory.KEYWORD, "Orden descendente (mayor a menor)"))
                results.add(SqlSuggestion("ASC ", "ASC", SuggestionCategory.KEYWORD, "Orden ascendente (menor a mayor)"))
            }
            results.add(SqlSuggestion(", ", ", (otra columna)", SuggestionCategory.OPERATOR, "Agregar otra columna"))
            if (activeClause == SqlClause.GROUP_BY) {
                results.add(SqlSuggestion("HAVING ", "HAVING", SuggestionCategory.KEYWORD, "Filtrar grupos"))
                results.add(SqlSuggestion("ORDER BY ", "ORDER BY", SuggestionCategory.KEYWORD, "Ordenar grupos"))
            }
            results.add(SqlSuggestion("LIMIT 10;", "LIMIT 10;", SuggestionCategory.KEYWORD, "Limitar resultados"))
            return results
        }

        // Case 7: LIMIT / OFFSET
        if (activeClause == SqlClause.LIMIT || activeClause == SqlClause.OFFSET) {
            if (lastToken.upper == "LIMIT") {
                results.add(SqlSuggestion("10;", "10;", SuggestionCategory.OPERATOR, "Primeras 10 filas"))
                results.add(SqlSuggestion("25;", "25;", SuggestionCategory.OPERATOR, "Primeras 25 filas"))
                results.add(SqlSuggestion("50;", "50;", SuggestionCategory.OPERATOR, "Primeras 50 filas"))
                results.add(SqlSuggestion("100;", "100;", SuggestionCategory.OPERATOR, "Primeras 100 filas"))
            } else {
                results.add(SqlSuggestion("OFFSET 0;", "OFFSET 0;", SuggestionCategory.KEYWORD, "Desplazamiento inicial"))
            }
            return results
        }

        // Case 8: VALUES / UPDATE / DELETE / INSERT context
        if (lastToken.upper == "VALUES") {
            results.add(SqlSuggestion("('', '')", "('v1', 'v2')", SuggestionCategory.OPERATOR, "Valores de texto", cursorOffsetFromEnd = 6))
            return results
        }

        if (activeClause == SqlClause.UPDATE && lastToken.upper != "UPDATE") {
            results.add(SqlSuggestion("SET ", "SET", SuggestionCategory.KEYWORD, "Asignar valores a columnas"))
            return results
        }

        // Generic contextual fallback
        results.add(SqlSuggestion("WHERE ", "WHERE", SuggestionCategory.KEYWORD, "Filtrar por condición"))
        results.add(SqlSuggestion("AND ", "AND", SuggestionCategory.KEYWORD, "Condición adicional"))
        results.add(SqlSuggestion("OR ", "OR", SuggestionCategory.KEYWORD, "Condición alternativa"))
        results.add(SqlSuggestion("ORDER BY ", "ORDER BY", SuggestionCategory.KEYWORD, "Ordenar filas"))
        results.add(SqlSuggestion("LIMIT 10;", "LIMIT 10;", SuggestionCategory.KEYWORD, "Número máximo de filas"))
        return results
    }

    private fun buildColumnSuggestions(
        tableRefs: List<TableRef>,
        tableSchemas: List<TableSchemaDetail>,
        availableColumns: List<String>,
        trailingSpace: Boolean,
        maxItems: Int
    ): List<SqlSuggestion> {
        val colSuggestions = mutableListOf<SqlSuggestion>()
        val suffix = if (trailingSpace) " " else ""

        if (tableSchemas.isNotEmpty()) {
            val referencedOrderedSchemas = if (tableRefs.isNotEmpty()) {
                tableRefs.mapNotNull { ref ->
                    tableSchemas.find { it.tableName.equals(ref.tableName, ignoreCase = true) }?.let { ref to it }
                }
            } else {
                tableSchemas.map { TableRef(it.tableName, null) to it }
            }

            val multipleReferencedWithAlias = tableRefs.size > 1
            for ((ref, schema) in referencedOrderedSchemas) {
                for (col in schema.columns) {
                    val pkBadge = if (col.isPrimaryKey) " [PK]" else ""
                    val prefixQualifier = if (multipleReferencedWithAlias && !ref.alias.isNullOrBlank()) {
                        "${ref.alias}."
                    } else ""
                    val insertName = "$prefixQualifier${col.name}"
                    colSuggestions.add(
                        SqlSuggestion(
                            insertText = "$insertName$suffix",
                            displayText = insertName,
                            category = SuggestionCategory.COLUMN,
                            detail = "${schema.tableName}.${col.name} (${col.type}$pkBadge)",
                            tableTag = schema.tableName
                        )
                    )
                }
            }
        } else {
            availableColumns.forEach { col ->
                colSuggestions.add(
                    SqlSuggestion(
                        insertText = "$col$suffix",
                        displayText = col,
                        category = SuggestionCategory.COLUMN,
                        detail = "Columna de la base activa"
                    )
                )
            }
        }

        return colSuggestions.take(maxItems)
    }

    private fun buildFallbackPrefixSuggestions(
        currentWord: String,
        tableRefs: List<TableRef>,
        availableTables: List<String>,
        availableColumns: List<String>,
        tableSchemas: List<TableSchemaDetail>
    ): List<SqlSuggestion> {
        val results = mutableListOf<SqlSuggestion>()

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

        availableTables.filter { it.startsWith(currentWord, ignoreCase = true) || it.contains(currentWord, ignoreCase = true) }
            .forEach { tbl ->
                val schema = tableSchemas.find { it.tableName.equals(tbl, ignoreCase = true) }
                val colCount = schema?.columns?.size ?: 0
                results.add(
                    SqlSuggestion(
                        insertText = "$tbl ",
                        displayText = tbl,
                        category = SuggestionCategory.TABLE,
                        detail = "Tabla del esquema ($colCount columnas)",
                        tableTag = if (colCount > 0) "$colCount cols" else null
                    )
                )
            }

        val cols = buildColumnSuggestions(
            tableRefs = tableRefs,
            tableSchemas = tableSchemas,
            availableColumns = availableColumns,
            trailingSpace = true,
            maxItems = 40
        )
        results.addAll(filterSuggestionsByPrefix(cols, currentWord))

        COMMON_FUNCTIONS.filter { it.displayText.startsWith(currentWord, ignoreCase = true) }
            .forEach { results.add(it) }

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

        return results
    }

    private fun filterSuggestionsByPrefix(
        candidates: List<SqlSuggestion>,
        prefix: String
    ): List<SqlSuggestion> {
        val startsWithMatches = candidates.filter {
            it.displayText.startsWith(prefix, ignoreCase = true) ||
                it.insertText.trim().startsWith(prefix, ignoreCase = true)
        }
        val containsMatches = candidates.filter { candidate ->
            candidate !in startsWithMatches && (
                candidate.displayText.contains(prefix, ignoreCase = true) ||
                    candidate.insertText.contains(prefix, ignoreCase = true)
                )
        }
        return startsWithMatches + containsMatches
    }

    private fun <T> filterByWordMatch(
        items: List<T>,
        prefix: String,
        selector: (T) -> String
    ): List<T> {
        val starts = items.filter { selector(it).startsWith(prefix, ignoreCase = true) }
        val contains = items.filter { !selector(it).startsWith(prefix, ignoreCase = true) && selector(it).contains(prefix, ignoreCase = true) }
        return starts + contains
    }

    private fun deduplicateSuggestions(items: List<SqlSuggestion>): List<SqlSuggestion> {
        return items.distinctBy { "${it.category}_${it.tableTag}_${it.displayText}_${it.insertText}" }
    }

    private fun findTablesOwningColumn(
        columnName: String,
        tableSchemas: List<TableSchemaDetail>
    ): List<String> {
        if (columnName.isBlank()) return emptyList()
        val bareName = columnName.substringAfterLast('.')
        return tableSchemas
            .filter { schema -> schema.columns.any { it.name.equals(bareName, ignoreCase = true) } }
            .map { it.tableName }
    }

    private data class ClauseDetectionResult(
        val clause: SqlClause
    )

    private fun detectActiveClause(tokens: List<SqlToken>): ClauseDetectionResult {
        var current = SqlClause.NONE
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val next = tokens.getOrNull(i + 1)
            if (t.kind == SqlTokenKind.WORD) {
                when (t.upper) {
                    "SELECT" -> current = SqlClause.SELECT
                    "FROM" -> current = SqlClause.FROM
                    "JOIN" -> current = SqlClause.JOIN
                    "INNER", "LEFT", "RIGHT", "CROSS", "FULL", "NATURAL" -> {
                        if (next?.upper == "JOIN") {
                            current = SqlClause.JOIN
                            i++
                        }
                    }
                    "ON" -> current = SqlClause.ON
                    "WHERE" -> current = SqlClause.WHERE
                    "GROUP" -> {
                        if (next?.upper == "BY") {
                            current = SqlClause.GROUP_BY
                            i++
                        }
                    }
                    "HAVING" -> current = SqlClause.HAVING
                    "ORDER" -> {
                        if (next?.upper == "BY") {
                            current = SqlClause.ORDER_BY
                            i++
                        }
                    }
                    "LIMIT" -> current = SqlClause.LIMIT
                    "OFFSET" -> current = SqlClause.OFFSET
                    "INSERT" -> current = SqlClause.INSERT
                    "INTO" -> current = SqlClause.INTO
                    "VALUES" -> current = SqlClause.VALUES
                    "UPDATE" -> current = SqlClause.UPDATE
                    "SET" -> current = SqlClause.SET
                    "CREATE" -> current = SqlClause.CREATE
                    "ALTER" -> current = SqlClause.ALTER
                    "DROP" -> current = SqlClause.DROP
                }
            }
            i++
        }
        return ClauseDetectionResult(current)
    }

    private fun extractCurrentStatementSlice(
        fullText: String,
        safeCursor: Int,
        wordStart: Int
    ): StatementSlice {
        var inSingleQuote = false
        var inLineComment = false
        var inBlockComment = false
        var stmtStart = 0
        var stmtEnd = fullText.length
        var insideQuoteOrCommentAtCursor = false

        var i = 0
        while (i < fullText.length) {
            if (i == safeCursor) {
                insideQuoteOrCommentAtCursor = inSingleQuote || inLineComment || inBlockComment
            }
            val c = fullText[i]
            val next = fullText.getOrNull(i + 1)

            when {
                inLineComment -> {
                    if (c == '\n') inLineComment = false
                }
                inBlockComment -> {
                    if (c == '*' && next == '/') {
                        inBlockComment = false
                        i++
                    }
                }
                inSingleQuote -> {
                    if (c == '\'') {
                        if (next == '\'') {
                            i++ // Escaped single quote ''
                        } else {
                            inSingleQuote = false
                        }
                    }
                }
                else -> {
                    when {
                        c == '\'' -> inSingleQuote = true
                        c == '-' && next == '-' -> {
                            inLineComment = true
                            i++
                        }
                        c == '/' && next == '*' -> {
                            inBlockComment = true
                            i++
                        }
                        c == ';' -> {
                            if (i < safeCursor) {
                                stmtStart = i + 1
                            } else if (stmtEnd == fullText.length) {
                                stmtEnd = i
                            }
                        }
                    }
                }
            }
            i++
        }

        if (safeCursor >= fullText.length) {
            insideQuoteOrCommentAtCursor = inSingleQuote || inLineComment || inBlockComment
        }

        val safeStart = stmtStart.coerceIn(0, fullText.length)
        val safeEnd = stmtEnd.coerceIn(safeStart, fullText.length)
        val safeWordStart = wordStart.coerceIn(safeStart, safeEnd)

        return StatementSlice(
            statementFull = fullText.substring(safeStart, safeEnd),
            statementBeforeWord = fullText.substring(safeStart, safeWordStart),
            insideUnclosedQuoteOrComment = insideQuoteOrCommentAtCursor
        )
    }

    private fun tokenizeSql(sql: String): List<SqlToken> {
        val tokens = mutableListOf<SqlToken>()
        var i = 0
        val len = sql.length
        while (i < len) {
            val c = sql[i]
            val next = sql.getOrNull(i + 1)
            when {
                c.isWhitespace() -> i++
                c == '-' && next == '-' -> {
                    i += 2
                    while (i < len && sql[i] != '\n') i++
                }
                c == '/' && next == '*' -> {
                    i += 2
                    while (i < len - 1 && !(sql[i] == '*' && sql[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(len)
                }
                c == '\'' -> {
                    val start = i
                    i++
                    while (i < len) {
                        if (sql[i] == '\'') {
                            if (sql.getOrNull(i + 1) == '\'') i += 2
                            else {
                                i++
                                break
                            }
                        } else i++
                    }
                    val lit = sql.substring(start, i)
                    tokens.add(SqlToken(lit, lit.uppercase(), SqlTokenKind.STRING))
                }
                c == ',' -> {
                    tokens.add(SqlToken(",", ",", SqlTokenKind.COMMA))
                    i++
                }
                c == '.' -> {
                    tokens.add(SqlToken(".", ".", SqlTokenKind.DOT))
                    i++
                }
                c == '(' -> {
                    tokens.add(SqlToken("(", "(", SqlTokenKind.OPEN_PAREN))
                    i++
                }
                c == ')' -> {
                    tokens.add(SqlToken(")", ")", SqlTokenKind.CLOSE_PAREN))
                    i++
                }
                c == '|' && next == '|' -> {
                    tokens.add(SqlToken("||", "||", SqlTokenKind.OPERATOR))
                    i += 2
                }
                (c == '!' && next == '=') || (c == '<' && next == '>') || (c == '<' && next == '=') || (c == '>' && next == '=') -> {
                    val op = sql.substring(i, i + 2)
                    tokens.add(SqlToken(op, op, SqlTokenKind.OPERATOR))
                    i += 2
                }
                c in setOf('=', '<', '>', '+', '-', '*', '/') -> {
                    val op = c.toString()
                    tokens.add(SqlToken(op, op, SqlTokenKind.OPERATOR))
                    i++
                }
                c.isDigit() -> {
                    val start = i
                    while (i < len && (sql[i].isDigit() || sql[i] == '.')) i++
                    val num = sql.substring(start, i)
                    tokens.add(SqlToken(num, num, SqlTokenKind.NUMBER))
                }
                c.isLetter() || c == '_' -> {
                    val start = i
                    while (i < len && (sql[i].isLetterOrDigit() || sql[i] == '_')) i++
                    val word = sql.substring(start, i)
                    tokens.add(SqlToken(word, word.uppercase(), SqlTokenKind.WORD))
                }
                else -> i++
            }
        }
        return tokens
    }

    fun extractCurrentWord(text: String, cursor: Int): String {
        if (cursor <= 0 || cursor > text.length) return ""
        var start = cursor - 1
        while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_' || text[start] == '*')) {
            start--
        }
        return text.substring(start + 1, cursor)
    }

    private fun extractTableDotQualifier(textBeforeCurrentWord: String): String? {
        if (!textBeforeCurrentWord.endsWith(".")) return null
        val dotIndex = textBeforeCurrentWord.length - 1
        if (dotIndex <= 0) return null
        var start = dotIndex - 1
        while (start >= 0 && (textBeforeCurrentWord[start].isLetterOrDigit() || textBeforeCurrentWord[start] == '_')) {
            start--
        }
        val qualifier = textBeforeCurrentWord.substring(start + 1, dotIndex)
        return qualifier.ifEmpty { null }
    }

    fun extractTableReferences(sql: String, availableTables: List<String>): List<TableRef> {
        if (availableTables.isEmpty() || sql.isBlank()) return emptyList()
        val refs = mutableListOf<TableRef>()
        val tokens = tokenizeSql(sql)

        fun resolveCanonicalTable(name: String): String? =
            availableTables.find { it.equals(name, ignoreCase = true) }

        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            if (tok.kind == SqlTokenKind.WORD && tok.upper in setOf("FROM", "JOIN", "UPDATE", "INTO")) {
                var idx = i + 1
                // Handle single or comma-separated table references after FROM/JOIN/UPDATE/INTO
                while (idx < tokens.size) {
                    val tblTok = tokens[idx]
                    if (tblTok.kind != SqlTokenKind.WORD) break
                    val canonical = resolveCanonicalTable(tblTok.text)
                    if (canonical != null) {
                        var alias: String? = null
                        val next1 = tokens.getOrNull(idx + 1)
                        val next2 = tokens.getOrNull(idx + 2)
                        if (next1?.kind == SqlTokenKind.WORD && next1.upper == "AS" &&
                            next2?.kind == SqlTokenKind.WORD && next2.upper !in NON_ALIAS_KEYWORDS
                        ) {
                            alias = next2.text
                            idx += 2
                        } else if (next1?.kind == SqlTokenKind.WORD &&
                            next1.upper !in NON_ALIAS_KEYWORDS &&
                            resolveCanonicalTable(next1.text) == null
                        ) {
                            alias = next1.text
                            idx += 1
                        }
                        if (refs.none { it.tableName.equals(canonical, ignoreCase = true) && it.alias.equals(alias, ignoreCase = true) }) {
                            refs.add(TableRef(canonical, alias))
                        }
                        // Continue only if followed by a comma (e.g., FROM a, b) inside FROM
                        if (tok.upper == "FROM" && tokens.getOrNull(idx + 1)?.kind == SqlTokenKind.COMMA) {
                            idx += 2
                            continue
                        }
                    }
                    break
                }
            }
            i++
        }

        // Fallback: if no explicit FROM/JOIN match found, detect bare table word mentions
        if (refs.isEmpty()) {
            val upper = sql.uppercase()
            for (tbl in availableTables) {
                val pattern = "\\b${Regex.escape(tbl.uppercase())}\\b".toRegex()
                if (pattern.containsMatchIn(upper)) {
                    refs.add(TableRef(tbl, null))
                }
            }
        }
        return refs
    }
}

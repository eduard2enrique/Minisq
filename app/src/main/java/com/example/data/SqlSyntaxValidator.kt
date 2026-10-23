package com.example.data

import android.database.sqlite.SQLiteDatabase
import java.util.regex.Pattern

enum class IssueSeverity {
    ERROR, WARNING, INFO
}

data class SqlSyntaxIssue(
    val message: String,
    val line: Int = 1,
    val startIndex: Int = 0,
    val endIndex: Int = 0,
    val severity: IssueSeverity = IssueSeverity.ERROR,
    val suggestion: String? = null
)

object SqlSyntaxValidator {

    private val TYPO_CANDIDATES = mapOf(
        "SELEC" to "SELECT",
        "SELETC" to "SELECT",
        "SEELCT" to "SELECT",
        "SLECT" to "SELECT",
        "FORM" to "FROM",
        "FORM" to "FROM",
        "WHER" to "WHERE",
        "WEHRE" to "WHERE",
        "WERE" to "WHERE",
        "ORDEN" to "ORDER",
        "ORDERBY" to "ORDER BY",
        "GROUPBY" to "GROUP BY",
        "HAVIN" to "HAVING",
        "INSER" to "INSERT",
        "INTOU" to "INTO",
        "UPDAT" to "UPDATE",
        "DELET" to "DELETE",
        "CREAT" to "CREATE",
        "TABLEE" to "TABLE"
    )

    fun validate(sql: String): List<SqlSyntaxIssue> {
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) return emptyList()

        val issues = mutableListOf<SqlSyntaxIssue>()

        // 1. Check unclosed quotes
        checkUnclosedQuotes(sql, issues)

        // 2. Check unbalanced parentheses
        checkParentheses(sql, issues)

        // 3. Check dangling commas or double commas
        checkCommas(sql, issues)

        // 4. Check keyword typos
        checkTypos(sql, issues)

        // 5. Check clause ordering & structural patterns
        checkClauseStructure(sql, issues)

        return issues
    }

    /**
     * Optional deep validation using SQLite native EXPLAIN parser (catches table & syntax errors)
     */
    fun validateWithEngine(db: SQLiteDatabase?, sql: String): SqlSyntaxIssue? {
        if (db == null || !db.isOpen) return null
        val trimmed = sql.trim()
        if (trimmed.isEmpty()) return null

        // Only test single statements to avoid executing side effects
        if (trimmed.contains(";")) {
            val parts = trimmed.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size > 1) return null
        }

        val upper = trimmed.uppercase()
        // If SELECT/WITH, use EXPLAIN to validate without running
        if (upper.startsWith("SELECT") || upper.startsWith("WITH") || upper.startsWith("PRAGMA")) {
            try {
                val cursor = db.rawQuery("EXPLAIN $trimmed", null)
                cursor.close()
                return null
            } catch (e: Exception) {
                val msg = cleanErrorMessage(e.message ?: "Error de sintaxis")
                return SqlSyntaxIssue(
                    message = msg,
                    severity = IssueSeverity.ERROR
                )
            }
        }
        return null
    }

    private fun checkUnclosedQuotes(sql: String, issues: MutableList<SqlSyntaxIssue>) {
        var inSingle = false
        var singleStart = -1
        var inDouble = false
        var doubleStart = -1

        var i = 0
        while (i < sql.length) {
            val c = sql[i]
            // Skip single-line comments
            if (!inSingle && !inDouble && c == '-' && i + 1 < sql.length && sql[i + 1] == '-') {
                while (i < sql.length && sql[i] != '\n') i++
                continue
            }

            if (c == '\'') {
                if (!inDouble) {
                    if (inSingle) {
                        // Check if escaped ''
                        if (i + 1 < sql.length && sql[i + 1] == '\'') {
                            i++ // Skip second quote
                        } else {
                            inSingle = false
                        }
                    } else {
                        inSingle = true
                        singleStart = i
                    }
                }
            } else if (c == '\"') {
                if (!inSingle) {
                    if (inDouble) {
                        if (i + 1 < sql.length && sql[i + 1] == '\"') {
                            i++
                        } else {
                            inDouble = false
                        }
                    } else {
                        inDouble = true
                        doubleStart = i
                    }
                }
            }
            i++
        }

        if (inSingle) {
            val line = getLineNumber(sql, singleStart)
            issues.add(
                SqlSyntaxIssue(
                    message = "Comilla simple sin cerrar (cadena de texto incompleta)",
                    line = line,
                    startIndex = singleStart,
                    endIndex = sql.length,
                    severity = IssueSeverity.ERROR,
                    suggestion = "Añade una comilla de cierre ' al final del texto"
                )
            )
        }

        if (inDouble) {
            val line = getLineNumber(sql, doubleStart)
            issues.add(
                SqlSyntaxIssue(
                    message = "Comilla doble sin cerrar en identificador",
                    line = line,
                    startIndex = doubleStart,
                    endIndex = sql.length,
                    severity = IssueSeverity.ERROR,
                    suggestion = "Cierra la comilla doble \""
                )
            )
        }
    }

    private fun checkParentheses(sql: String, issues: MutableList<SqlSyntaxIssue>) {
        val stack = mutableListOf<Int>()
        var inString = false
        var i = 0

        while (i < sql.length) {
            val c = sql[i]
            if (c == '\'' || c == '\"') {
                inString = !inString
            } else if (!inString) {
                if (c == '(') {
                    stack.add(i)
                } else if (c == ')') {
                    if (stack.isEmpty()) {
                        issues.add(
                            SqlSyntaxIssue(
                                message = "Paréntesis de cierre ')' inesperado sin apertura previa",
                                line = getLineNumber(sql, i),
                                startIndex = i,
                                endIndex = i + 1,
                                severity = IssueSeverity.ERROR,
                                suggestion = "Elimina este ')' o abre un '(' antes"
                            )
                        )
                    } else {
                        stack.removeAt(stack.lastIndex)
                    }
                }
            }
            i++
        }

        if (stack.isNotEmpty()) {
            val firstUnclosed = stack.first()
            issues.add(
                SqlSyntaxIssue(
                    message = "Paréntesis sin cerrar: faltan ${stack.size} paréntesis de cierre ')'",
                    line = getLineNumber(sql, firstUnclosed),
                    startIndex = firstUnclosed,
                    endIndex = sql.length,
                    severity = IssueSeverity.ERROR,
                    suggestion = "Cierra la expresión con ')'"
                )
            )
        }
    }

    private fun checkCommas(sql: String, issues: MutableList<SqlSyntaxIssue>) {
        // Dangling comma before major clauses: , FROM | , WHERE | , ) | , ;
        val danglingPattern = Pattern.compile(",\\s*(FROM|WHERE|GROUP|ORDER|LIMIT|\\)|;|$)", Pattern.CASE_INSENSITIVE)
        val matcher = danglingPattern.matcher(sql)
        while (matcher.find()) {
            val keyword = matcher.group(1) ?: ""
            val pos = matcher.start()
            val textClause = if (keyword.isBlank()) "al final de la consulta" else "antes de '$keyword'"
            issues.add(
                SqlSyntaxIssue(
                    message = "Coma sobrante detectada $textClause",
                    line = getLineNumber(sql, pos),
                    startIndex = pos,
                    endIndex = pos + 1,
                    severity = IssueSeverity.WARNING,
                    suggestion = "Elimina la coma sobrante ','"
                )
            )
        }

        // Consecutive commas: ,,
        val doubleCommaPattern = Pattern.compile(",\\s*,")
        val doubleMatcher = doubleCommaPattern.matcher(sql)
        while (doubleMatcher.find()) {
            val pos = doubleMatcher.start()
            issues.add(
                SqlSyntaxIssue(
                    message = "Comas consecutivas detectadas (,,)",
                    line = getLineNumber(sql, pos),
                    startIndex = pos,
                    endIndex = doubleMatcher.end(),
                    severity = IssueSeverity.ERROR,
                    suggestion = "Deja únicamente una coma separadora"
                )
            )
        }
    }

    private fun checkTypos(sql: String, issues: MutableList<SqlSyntaxIssue>) {
        val wordPattern = Pattern.compile("\\b([A-Za-z_]+)\\b")
        val matcher = wordPattern.matcher(sql)
        while (matcher.find()) {
            val word = matcher.group(1) ?: continue
            val upper = word.uppercase()
            if (TYPO_CANDIDATES.containsKey(upper)) {
                val correct = TYPO_CANDIDATES[upper]
                issues.add(
                    SqlSyntaxIssue(
                        message = "Posible error tipográfico '$word': ¿quisiste escribir '$correct'?",
                        line = getLineNumber(sql, matcher.start()),
                        startIndex = matcher.start(),
                        endIndex = matcher.end(),
                        severity = IssueSeverity.WARNING,
                        suggestion = "Reemplazar por $correct"
                    )
                )
            }
        }
    }

    private fun checkClauseStructure(sql: String, issues: MutableList<SqlSyntaxIssue>) {
        val cleanSql = removeStringsAndComments(sql).uppercase()

        // 1. SELECT without FROM when clauses like WHERE, GROUP BY, ORDER BY exist
        if (cleanSql.contains("SELECT") && !cleanSql.contains("FROM")) {
            if (cleanSql.contains("WHERE") || cleanSql.contains("GROUP BY") || cleanSql.contains("ORDER BY")) {
                issues.add(
                    SqlSyntaxIssue(
                        message = "Consulta SELECT contiene WHERE/ORDER BY pero no tiene cláusula FROM",
                        severity = IssueSeverity.ERROR,
                        suggestion = "Especifica la tabla con FROM (ej. FROM clientes)"
                    )
                )
            }
        }

        // 2. INSERT without INTO
        val insertWithoutInto = Pattern.compile("\\bINSERT\\s+([A-Za-z_][A-Za-z0-9_]*)\\b(?!\\s*INTO)", Pattern.CASE_INSENSITIVE)
        val insertMatcher = insertWithoutInto.matcher(cleanSql)
        if (insertMatcher.find()) {
            val token = insertMatcher.group(1) ?: ""
            if (!token.equals("INTO", ignoreCase = true) && !token.equals("OR", ignoreCase = true)) {
                issues.add(
                    SqlSyntaxIssue(
                        message = "Falta la palabra clave 'INTO' después de INSERT (ej. INSERT INTO $token ...)",
                        severity = IssueSeverity.ERROR,
                        suggestion = "Usa INSERT INTO $token"
                    )
                )
            }
        }

        // 3. DELETE without FROM
        val deleteWithoutFrom = Pattern.compile("\\bDELETE\\s+([A-Za-z_][A-Za-z0-9_]*)\\b", Pattern.CASE_INSENSITIVE)
        val deleteMatcher = deleteWithoutFrom.matcher(cleanSql)
        if (deleteMatcher.find()) {
            val nextWord = deleteMatcher.group(1) ?: ""
            if (!nextWord.equals("FROM", ignoreCase = true)) {
                issues.add(
                    SqlSyntaxIssue(
                        message = "En SQLite la sentencia DELETE requiere FROM (ej. DELETE FROM $nextWord)",
                        severity = IssueSeverity.ERROR,
                        suggestion = "Usa DELETE FROM $nextWord"
                    )
                )
            }
        }

        // 4. Clauses ordering in SELECT
        val selectIdx = cleanSql.indexOf("SELECT")
        val fromIdx = cleanSql.indexOf("FROM")
        val whereIdx = cleanSql.indexOf("WHERE")
        val groupIdx = cleanSql.indexOf("GROUP BY")
        val orderIdx = cleanSql.indexOf("ORDER BY")

        if (selectIdx != -1 && fromIdx != -1 && whereIdx != -1) {
            if (whereIdx < fromIdx) {
                issues.add(
                    SqlSyntaxIssue(
                        message = "La cláusula WHERE debe colocarse después de FROM",
                        severity = IssueSeverity.ERROR,
                        suggestion = "Coloca WHERE después de FROM tabla"
                    )
                )
            }
        }

        if (whereIdx != -1 && groupIdx != -1 && whereIdx > groupIdx) {
            issues.add(
                SqlSyntaxIssue(
                    message = "La cláusula WHERE debe ir antes de GROUP BY (usa HAVING para filtrar grupos)",
                    severity = IssueSeverity.ERROR,
                    suggestion = "Mueve WHERE antes de GROUP BY"
                )
            )
        }

        if (whereIdx != -1 && orderIdx != -1 && whereIdx > orderIdx) {
            issues.add(
                SqlSyntaxIssue(
                    message = "La cláusula WHERE debe colocarse antes de ORDER BY",
                    severity = IssueSeverity.ERROR,
                    suggestion = "Coloca WHERE antes de ORDER BY"
                )
            )
        }

        if (groupIdx != -1 && orderIdx != -1 && groupIdx > orderIdx) {
            issues.add(
                SqlSyntaxIssue(
                    message = "La cláusula GROUP BY debe colocarse antes de ORDER BY",
                    severity = IssueSeverity.ERROR,
                    suggestion = "Mueve GROUP BY antes de ORDER BY"
                )
            )
        }

        // 5. JOIN without ON
        val joinPattern = Pattern.compile("\\b(INNER\\s+JOIN|LEFT\\s+JOIN|JOIN)\\s+([A-Za-z_][A-Za-z0-9_]*)(?!\\s+ON\\b|\\s+USING\\b)", Pattern.CASE_INSENSITIVE)
        val joinMatcher = joinPattern.matcher(cleanSql)
        while (joinMatcher.find()) {
            val joinType = joinMatcher.group(1)
            val joinedTable = joinMatcher.group(2)
            // Check if there is an ON after the table
            val afterJoin = cleanSql.substring(joinMatcher.end()).trimStart()
            if (!afterJoin.startsWith("ON") && !afterJoin.startsWith("USING") && !afterJoin.startsWith("AS") && !afterJoin.matches(Regex("^[A-Za-z_][A-Za-z0-9_]*\\s+(ON|USING).*"))) {
                issues.add(
                    SqlSyntaxIssue(
                        message = "$joinType con '$joinedTable' requiere una condición ON (ej. ON a.id = b.id)",
                        severity = IssueSeverity.WARNING,
                        suggestion = "Añade ON tabla1.col = tabla2.col"
                    )
                )
            }
        }
    }

    private fun removeStringsAndComments(sql: String): String {
        return sql
            .replace(Regex("--[^\n]*"), " ")
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
            .replace(Regex("'(?:''|[^'])*'"), "''")
            .replace(Regex("\"(?:\"\"|[^\"])*\""), "\"\"")
    }

    private fun getLineNumber(sql: String, charIndex: Int): Int {
        if (charIndex <= 0) return 1
        val safeIndex = charIndex.coerceAtMost(sql.length)
        return sql.substring(0, safeIndex).count { it == '\n' } + 1
    }

    private fun cleanErrorMessage(raw: String): String {
        return raw.replace(Regex("(?i)near \"[^\"]*\": syntax error"), "Error de sintaxis de SQLite en la expresión.")
            .replace("no such table:", "Tabla no encontrada:")
            .replace("no such column:", "Columna no encontrada:")
    }
}

package com.example.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.example.data.IssueSeverity
import com.example.data.SqlSyntaxIssue
import java.util.regex.Pattern

/**
 * High-performance, IDE-grade syntax highlighter for SQL statements in Jetpack Compose.
 *
 * Implements lexical separation for:
 * - SQL Clauses & Keywords (SELECT, FROM, WHERE, JOIN, ORDER BY, etc.)
 * - Schema Tables (clientes, productos, pedidos, etc.)
 * - Schema Columns (id, nombre, email, ciudad, saldo, etc.)
 * - Built-in SQL Functions (COUNT, SUM, AVG, COALESCE, STRFTIME, etc.)
 * - SQLite Data Types (INTEGER, TEXT, REAL, BLOB, BOOLEAN, etc.)
 * - String Literals ('text' and "identifiers")
 * - Numeric Literals (Integers, Decimals, Hex)
 * - Single-line and Multi-line Comments (-- comment and /* block */)
 * - Operators (=, <>, !=, LIKE, ||, +, -, etc.)
 * - Real-time syntax issue underlines (warnings and errors)
 */
class SqlSyntaxVisualTransformation(
    private val isDark: Boolean = true,
    private val issues: List<SqlSyntaxIssue> = emptyList(),
    private val schemaTables: Set<String> = emptySet(),
    private val schemaColumns: Set<String> = emptySet()
) : VisualTransformation {

    companion object {
        // 1. Major SQL Clauses & Statement Keywords
        val KEYWORDS = setOf(
            "SELECT", "FROM", "WHERE", "AND", "OR", "NOT", "IN", "LIKE", "GLOB", "IS", "NULL", "AS",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "CROSS", "NATURAL", "ON", "USING",
            "ORDER", "BY", "GROUP", "HAVING", "LIMIT", "OFFSET", "ASC", "DESC",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE",
            "CREATE", "TABLE", "DROP", "ALTER", "ADD", "COLUMN", "RENAME", "TO",
            "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "CASCADE", "RESTRICT", "NO", "ACTION",
            "UNIQUE", "CHECK", "DEFAULT", "INDEX", "VIEW", "TRIGGER", "TEMPORARY", "TEMP", "IF",
            "TRANSACTION", "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE",
            "PRAGMA", "EXPLAIN", "QUERY", "PLAN", "VACUUM", "ATTACH", "DETACH",
            "UNION", "ALL", "EXCEPT", "INTERSECT",
            "CASE", "WHEN", "THEN", "ELSE", "END",
            "EXISTS", "BETWEEN", "WITH", "RECURSIVE", "DISTINCT", "COLLATE"
        )

        // 2. Built-in SQL & SQLite Functions
        val FUNCTIONS = setOf(
            "COUNT", "SUM", "AVG", "MIN", "MAX", "TOTAL", "ABS", "ROUND",
            "COALESCE", "IFNULL", "NULLIF", "TYPEOF", "RANDOM", "HEX", "ZEROBLOB",
            "LENGTH", "LOWER", "UPPER", "SUBSTR", "SUBSTRING", "TRIM", "LTRIM", "RTRIM", "REPLACE", "INSTR",
            "GROUP_CONCAT", "STRING_AGG",
            "DATE", "TIME", "DATETIME", "JULIANDAY", "STRFTIME", "UNIXEPOCH",
            "CAST", "CHAR", "UNICODE", "LIKELY", "UNLIKELY"
        )

        // 3. Data Types & Storage Classes
        val DATA_TYPES = setOf(
            "INTEGER", "INT", "BIGINT", "SMALLINT", "TINYINT",
            "TEXT", "VARCHAR", "CHAR", "CLOB", "NVARCHAR", "NCHAR",
            "REAL", "DOUBLE", "FLOAT", "DECIMAL", "NUMERIC",
            "BLOB",
            "BOOLEAN", "BOOL", "TIMESTAMP", "AUTOINCREMENT"
        )

        // All vocabulary for autocomplete
        val ALL_VOCABULARY = (KEYWORDS + FUNCTIONS + DATA_TYPES).toList().sorted()

        // Compiled Regular Expressions for invariant language syntax
        private val COMMENTS_REGEX = Pattern.compile("(--[^\r\n]*|/\\*[\\s\\S]*?(\\*/|$))")
        private val STRINGS_REGEX = Pattern.compile("('(?:''|[^'\\r\\n])*'?|\"(?:\"\"|[^\"\\r\\n])*\"?)")
        private val NUMBERS_REGEX = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?)\\b")
        private val KEYWORDS_REGEX = Pattern.compile(
            "\\b(${KEYWORDS.joinToString("|")})\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val FUNCTIONS_REGEX = Pattern.compile(
            "\\b(${FUNCTIONS.joinToString("|")})\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val DATA_TYPES_REGEX = Pattern.compile(
            "\\b(${DATA_TYPES.joinToString("|")})\\b",
            Pattern.CASE_INSENSITIVE
        )
        private val OPERATORS_REGEX = Pattern.compile("(=|!=|<>|<=|>=|<|>|\\|\\||\\+|-|\\*|/|%|&|\\||~)")
    }

    // Dynamic patterns for active database schema tables and columns
    private val tablesPattern: Pattern? by lazy {
        val validTables = schemaTables.filter { it.isNotBlank() && !KEYWORDS.contains(it.uppercase()) }
        if (validTables.isNotEmpty()) {
            Pattern.compile(
                "\\b(${validTables.joinToString("|") { Pattern.quote(it) }})\\b",
                Pattern.CASE_INSENSITIVE
            )
        } else null
    }

    private val columnsPattern: Pattern? by lazy {
        val validColumns = schemaColumns.filter {
            it.isNotBlank() && !KEYWORDS.contains(it.uppercase()) && !schemaTables.contains(it)
        }
        if (validColumns.isNotEmpty()) {
            Pattern.compile(
                "\\b(${validColumns.joinToString("|") { Pattern.quote(it) }})\\b",
                Pattern.CASE_INSENSITIVE
            )
        } else null
    }

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(raw)

        // Semantic Colors palette based on current theme (Dark / Light)
        val colorClause = if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)   // Electric Indigo / Deep Indigo
        val colorTable = if (isDark) Color(0xFF2DD4BF) else Color(0xFF0D9488)    // Vibrant Teal / Pine Teal (Schema Tables)
        val colorColumn = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)   // Sky Blue / Ocean Blue (Schema Columns)
        val colorFunction = if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED) // Soft Violet / Deep Violet
        val colorType = if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C)     // Rose / Crimson
        val colorString = if (isDark) Color(0xFF34D399) else Color(0xFF059669)   // Mint Emerald / Forest
        val colorNumber = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)   // Amber Gold
        val colorComment = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)  // Slate Muted
        val colorOperator = if (isDark) Color(0xFFA5B4FC) else Color(0xFF475569) // Soft Slate Lavender

        // Store occupied ranges for comments and string literals so other tokens don't highlight inside them
        val protectedRanges = mutableListOf<IntRange>()

        // 1. Highlighting Comments (highest precedence)
        val commentMatcher = COMMENTS_REGEX.matcher(raw)
        while (commentMatcher.find()) {
            val start = commentMatcher.start()
            val end = commentMatcher.end()
            builder.addStyle(
                SpanStyle(
                    color = colorComment,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Normal
                ),
                start,
                end
            )
            protectedRanges.add(start until end)
        }

        // 2. Highlighting String Literals
        val stringMatcher = STRINGS_REGEX.matcher(raw)
        while (stringMatcher.find()) {
            val start = stringMatcher.start()
            val end = stringMatcher.end()
            if (!isRangeProtected(start, end, protectedRanges)) {
                builder.addStyle(
                    SpanStyle(
                        color = colorString,
                        fontWeight = FontWeight.Medium
                    ),
                    start,
                    end
                )
                protectedRanges.add(start until end)
            }
        }

        // 3. Highlight Built-in Functions
        val funcMatcher = FUNCTIONS_REGEX.matcher(raw)
        while (funcMatcher.find()) {
            val start = funcMatcher.start()
            val end = funcMatcher.end()
            if (!isRangeProtected(start, end, protectedRanges)) {
                builder.addStyle(
                    SpanStyle(
                        color = colorFunction,
                        fontWeight = FontWeight.Bold
                    ),
                    start,
                    end
                )
            }
        }

        // 4. Highlight Data Types
        val typeMatcher = DATA_TYPES_REGEX.matcher(raw)
        while (typeMatcher.find()) {
            val start = typeMatcher.start()
            val end = typeMatcher.end()
            if (!isRangeProtected(start, end, protectedRanges)) {
                builder.addStyle(
                    SpanStyle(
                        color = colorType,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start,
                    end
                )
            }
        }

        // 5. Highlight SQL Clauses and Keywords (SELECT, FROM, WHERE, etc.)
        val kwMatcher = KEYWORDS_REGEX.matcher(raw)
        while (kwMatcher.find()) {
            val start = kwMatcher.start()
            val end = kwMatcher.end()
            if (!isRangeProtected(start, end, protectedRanges)) {
                builder.addStyle(
                    SpanStyle(
                        color = colorClause,
                        fontWeight = FontWeight.Bold
                    ),
                    start,
                    end
                )
            }
        }

        // 6. Highlight Schema Tables (clientes, productos, etc.)
        tablesPattern?.let { pattern ->
            val tableMatcher = pattern.matcher(raw)
            while (tableMatcher.find()) {
                val start = tableMatcher.start()
                val end = tableMatcher.end()
                if (!isRangeProtected(start, end, protectedRanges)) {
                    builder.addStyle(
                        SpanStyle(
                            color = colorTable,
                            fontWeight = FontWeight.Bold
                        ),
                        start,
                        end
                    )
                }
            }
        }

        // 7. Highlight Schema Columns (id, nombre, email, etc.)
        columnsPattern?.let { pattern ->
            val colMatcher = pattern.matcher(raw)
            while (colMatcher.find()) {
                val start = colMatcher.start()
                val end = colMatcher.end()
                if (!isRangeProtected(start, end, protectedRanges)) {
                    builder.addStyle(
                        SpanStyle(
                            color = colorColumn,
                            fontWeight = FontWeight.SemiBold
                        ),
                        start,
                        end
                    )
                }
            }
        }

        // 8. Highlight Numeric Literals
        val numMatcher = NUMBERS_REGEX.matcher(raw)
        while (numMatcher.find()) {
            val start = numMatcher.start()
            val end = numMatcher.end()
            if (!isRangeProtected(start, end, protectedRanges)) {
                builder.addStyle(
                    SpanStyle(
                        color = colorNumber,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start,
                    end
                )
            }
        }

        // 9. Highlight Operators
        val opMatcher = OPERATORS_REGEX.matcher(raw)
        while (opMatcher.find()) {
            val start = opMatcher.start()
            val end = opMatcher.end()
            if (!isRangeProtected(start, end, protectedRanges)) {
                builder.addStyle(
                    SpanStyle(
                        color = colorOperator,
                        fontWeight = FontWeight.Medium
                    ),
                    start,
                    end
                )
            }
        }

        // 10. Highlight Syntactic Issues & Errors with visual underlines
        for (issue in issues) {
            val start = issue.startIndex.coerceIn(0, raw.length)
            val end = issue.endIndex.coerceIn(start, raw.length)
            if (end > start) {
                val errorTint = if (issue.severity == IssueSeverity.ERROR) {
                    Color(0x40EF4444)
                } else {
                    Color(0x35F59E0B)
                }
                builder.addStyle(
                    SpanStyle(
                        background = errorTint,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.Bold
                    ),
                    start,
                    end
                )
            }
        }

        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }

    private fun isRangeProtected(start: Int, end: Int, protectedList: List<IntRange>): Boolean {
        for (range in protectedList) {
            if (maxOf(start, range.first) < minOf(end, range.last + 1)) {
                return true
            }
        }
        return false
    }
}

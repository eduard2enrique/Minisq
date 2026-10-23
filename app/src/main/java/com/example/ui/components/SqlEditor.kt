package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.IssueSeverity
import com.example.data.SqlSyntaxIssue
import com.example.data.SqlSyntaxValidator
import com.example.ui.theme.DarkEditorBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceBorder
import com.example.ui.theme.SqlError
import com.example.ui.theme.SqlSuccess
import com.example.ui.theme.SqlWarning

@Composable
fun SqlEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    availableTables: List<String> = emptyList(),
    availableColumns: List<String> = emptyList(),
    modifier: Modifier = Modifier,
    isDark: Boolean = true,
    onValidationChanged: (List<SqlSyntaxIssue>) -> Unit = {}
) {
    var fontSizeSp by remember { mutableFloatStateOf(14f) }
    var showDiagnosticsDetails by remember { mutableStateOf(true) }
    var showSyntaxLegendDialog by remember { mutableStateOf(false) }

    // Real-time static syntax validation
    val syntaxIssues by remember(value.text) {
        derivedStateOf {
            val list = SqlSyntaxValidator.validate(value.text)
            onValidationChanged(list)
            list
        }
    }

    val errorCount by remember(syntaxIssues) {
        derivedStateOf { syntaxIssues.count { it.severity == IssueSeverity.ERROR } }
    }
    val warningCount by remember(syntaxIssues) {
        derivedStateOf { syntaxIssues.count { it.severity == IssueSeverity.WARNING } }
    }

    // Undo / Redo history tracking
    val undoStack = remember { mutableStateListOf<TextFieldValue>() }
    val redoStack = remember { mutableStateListOf<TextFieldValue>() }

    fun updateValueWithHistory(newValue: TextFieldValue) {
        if (newValue.text != value.text) {
            undoStack.add(value)
            if (undoStack.size > 30) undoStack.removeAt(0)
            redoStack.clear()
        }
        onValueChange(newValue)
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(value)
            onValueChange(prev)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(value)
            onValueChange(next)
        }
    }

    fun formatSql() {
        val formatted = formatSqlText(value.text)
        if (formatted != value.text) {
            updateValueWithHistory(TextFieldValue(formatted, TextRange(formatted.length)))
        }
    }

    fun applyQuickFix(issue: SqlSyntaxIssue) {
        val raw = value.text
        if (issue.message.contains("Comilla simple sin cerrar")) {
            val fixed = "$raw'"
            updateValueWithHistory(TextFieldValue(fixed, TextRange(fixed.length)))
        } else if (issue.message.contains("Paréntesis sin cerrar")) {
            val fixed = "$raw)"
            updateValueWithHistory(TextFieldValue(fixed, TextRange(fixed.length)))
        } else if (issue.message.contains("Coma sobrante") && issue.startIndex in raw.indices) {
            val fixed = raw.substring(0, issue.startIndex) + raw.substring(issue.startIndex + 1)
            updateValueWithHistory(TextFieldValue(fixed, TextRange(issue.startIndex.coerceAtMost(fixed.length))))
        } else if (issue.suggestion != null && issue.suggestion.startsWith("Reemplazar por ")) {
            val replacement = issue.suggestion.removePrefix("Reemplazar por ")
            if (issue.startIndex in 0..issue.endIndex && issue.endIndex <= raw.length) {
                val fixed = raw.substring(0, issue.startIndex) + replacement + raw.substring(issue.endIndex)
                updateValueWithHistory(TextFieldValue(fixed, TextRange(issue.startIndex + replacement.length)))
            }
        }
    }

    val linesCount by remember(value.text) {
        derivedStateOf {
            if (value.text.isEmpty()) 1 else value.text.count { it == '\n' } + 1
        }
    }

    // Autocomplete word detection
    val cursorIndex = value.selection.start
    val currentWord = remember(value.text, cursorIndex) {
        extractCurrentWord(value.text, cursorIndex)
    }

    val suggestions by remember(currentWord, availableTables, availableColumns) {
        derivedStateOf {
            if (currentWord.length < 2) emptyList()
            else {
                val list = mutableListOf<Pair<String, String>>() // word to type badge
                // Tables
                availableTables.filter { it.contains(currentWord, ignoreCase = true) }
                    .forEach { list.add(it to "tabla") }
                // Columns
                availableColumns.filter { it.contains(currentWord, ignoreCase = true) }
                    .forEach { list.add(it to "columna") }
                // Built-in SQL functions
                SqlSyntaxVisualTransformation.FUNCTIONS.filter { it.startsWith(currentWord, ignoreCase = true) }
                    .forEach { list.add(it to "función") }
                // Data types
                SqlSyntaxVisualTransformation.DATA_TYPES.filter { it.startsWith(currentWord, ignoreCase = true) }
                    .forEach { list.add(it to "tipo") }
                // Clauses & Keywords
                SqlSyntaxVisualTransformation.KEYWORDS.filter { it.startsWith(currentWord, ignoreCase = true) }
                    .forEach { list.add(it to "sql") }
                list.distinctBy { it.first }.take(10)
            }
        }
    }

    fun applySuggestion(suggestion: String) {
        val raw = value.text
        val start = (cursorIndex - currentWord.length).coerceAtLeast(0)
        val end = cursorIndex.coerceAtMost(raw.length)
        val newText = raw.substring(0, start) + suggestion + " " + raw.substring(end)
        val newCursor = start + suggestion.length + 1
        updateValueWithHistory(TextFieldValue(newText, TextRange(newCursor)))
    }

    val visualTransformation = remember(isDark, syntaxIssues) {
        SqlSyntaxVisualTransformation(isDark, syntaxIssues)
    }
    val editorBg = if (isDark) DarkEditorBackground else MaterialTheme.colorScheme.surfaceVariant
    val borderColor = if (isDark) DarkSurfaceBorder else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        color = editorBg,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Editor Toolbar matching screenshot + syntax status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "SQL",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    )

                    IconButton(
                        onClick = { undo() },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Undo,
                            contentDescription = "Deshacer",
                            tint = if (undoStack.isNotEmpty()) textPrimary else textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { redo() },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("redo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Redo,
                            contentDescription = "Rehacer",
                            tint = if (redoStack.isNotEmpty()) textPrimary else textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { formatSql() },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("format_sql_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Dar formato SQL",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = { showSyntaxLegendDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("syntax_palette_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Paleta de resaltado de sintaxis",
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Live Syntax Validation Badge
                    if (value.text.isNotBlank()) {
                        if (syntaxIssues.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = SqlSuccess.copy(alpha = 0.15f),
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SqlSuccess,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Válido",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = SqlSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            val badgeColor = if (errorCount > 0) SqlError else SqlWarning
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = badgeColor.copy(alpha = 0.18f),
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .clickable { showDiagnosticsDetails = !showDiagnosticsDetails }
                                    .testTag("syntax_issues_badge")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (errorCount > 0) Icons.Default.Error else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (errorCount > 0) "$errorCount error(es)" else "$warningCount aviso(s)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = if (showDiagnosticsDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = badgeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$linesCount ${if (linesCount == 1) "línea" else "líneas"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = textMuted
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { if (fontSizeSp > 11f) fontSizeSp -= 1f },
                            modifier = Modifier.size(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("A-", style = MaterialTheme.typography.labelMedium, color = textPrimary)
                        }

                        TextButton(
                            onClick = { if (fontSizeSp < 22f) fontSizeSp += 1f },
                            modifier = Modifier.size(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("A+", style = MaterialTheme.typography.labelMedium, color = textPrimary)
                        }
                    }
                }
            }

            // Syntax Diagnostics Panel (when errors/warnings detected)
            AnimatedVisibility(
                visible = syntaxIssues.isNotEmpty() && showDiagnosticsDetails,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (errorCount > 0) SqlError.copy(alpha = 0.12f)
                            else SqlWarning.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    syntaxIssues.take(3).forEach { issue ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (issue.severity == IssueSeverity.ERROR) Icons.Default.Error else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (issue.severity == IssueSeverity.ERROR) SqlError else SqlWarning,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Línea ${issue.line}: ${issue.message}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (issue.severity == IssueSeverity.ERROR) SqlError else SqlWarning,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (issue.suggestion != null) {
                                TextButton(
                                    onClick = { applyQuickFix(issue) },
                                    modifier = Modifier.height(26.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                                ) {
                                    Text(
                                        text = "Corregir ➔",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Autocomplete suggestions chip row
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sugerencias:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    suggestions.forEach { (word, badge) ->
                        AssistChip(
                            onClick = { applySuggestion(word) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(word, style = MaterialTheme.typography.labelSmall)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        badge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                labelColor = textPrimary
                            ),
                            border = null,
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            // Code Canvas with Line Numbers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp, max = 280.dp)
                    .padding(8.dp)
            ) {
                val verticalScroll = rememberScrollState()
                val horizontalScroll = rememberScrollState()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(verticalScroll)
                ) {
                    // Line numbers column
                    val lineNumbers = (1..linesCount).joinToString("\n")
                    Text(
                        text = lineNumbers,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.4f).sp,
                            color = textMuted.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Normal
                        ),
                        modifier = Modifier
                            .padding(end = 12.dp, start = 4.dp)
                    )

                    // Actual code text field
                    BasicTextField(
                        value = value,
                        onValueChange = { updateValueWithHistory(it) },
                        visualTransformation = visualTransformation,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.4f).sp,
                            color = textPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScroll)
                            .testTag("sql_input_field"),
                        decorationBox = { innerTextField ->
                            if (value.text.isEmpty()) {
                                Text(
                                    text = "-- Escribe tu consulta SQL aquí (ej. SELECT * FROM clientes;)",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = fontSizeSp.sp,
                                        lineHeight = (fontSizeSp * 1.4f).sp,
                                        color = textMuted.copy(alpha = 0.4f)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }
        }
    }

    if (showSyntaxLegendDialog) {
        SqlSyntaxLegendDialog(
            isDark = isDark,
            onDismiss = { showSyntaxLegendDialog = false }
        )
    }
}

@Composable
fun SqlSyntaxLegendDialog(
    isDark: Boolean,
    onDismiss: () -> Unit
) {
    val bg = if (isDark) DarkSurface else MaterialTheme.colorScheme.surface
    val colorClause = if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)
    val colorFunction = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val colorType = if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C)
    val colorString = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    val colorNumber = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706)
    val colorComment = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    val colorOperator = if (isDark) Color(0xFFA5B4FC) else Color(0xFF475569)

    data class SyntaxCategory(
        val name: String,
        val color: Color,
        val examples: String,
        val description: String
    )

    val categories = listOf(
        SyntaxCategory("Cláusulas y Palabras Clave", colorClause, "SELECT, FROM, WHERE, JOIN, GROUP BY, INSERT INTO, CREATE TABLE", "Sentencias e instrucciones DML / DDL de SQLite"),
        SyntaxCategory("Funciones Integradas", colorFunction, "COUNT(), SUM(), AVG(), COALESCE(), STRFTIME(), ROUND()", "Funciones nativas de agregación, matemáticas y cadenas"),
        SyntaxCategory("Tipos de Datos", colorType, "INTEGER, TEXT, REAL, BLOB, BOOLEAN, AUTOINCREMENT", "Afinidades de tipo para definición de esquemas"),
        SyntaxCategory("Literales de Texto (Strings)", colorString, "'Juan Pérez', 'Activo', '2026-10-23'", "Cadenas de caracteres delimitadas por comillas simples"),
        SyntaxCategory("Literales Numéricos", colorNumber, "42, 19.99, 0xFF, -10", "Valores numéricos enteros, flotantes y hexadecimales"),
        SyntaxCategory("Comentarios", colorComment, "-- Comentario de línea o /* Bloque */", "Notas explicativas y documentación ignoradas por SQLite"),
        SyntaxCategory("Operadores y Puntuación", colorOperator, "=, !=, <>, <, >, <=, >=, ||, +, *, /", "Operaciones de comparación, lógicas y aritméticas"),
        SyntaxCategory("Diagnóstico Sintáctico", SqlError, "Subrayado de errores en tiempo real", "Detección preventiva de comillas o paréntesis abiertos y comas sobrantes")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = colorClause,
                    modifier = Modifier.size(24.dp)
                )
                Text("Resaltado de Sintaxis SQL", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "El editor resalta automáticamente los elementos clave del lenguaje SQL para máxima legibilidad visual:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Interactive code preview box
                Surface(
                    color = if (isDark) DarkEditorBackground else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "-- Consulta con resaltado activo:",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = colorComment,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                        Row {
                            Text("SELECT ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorClause)
                            Text("ciudad, ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("COUNT", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorFunction)
                            Text("(*), ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("AVG", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorFunction)
                            Text("(saldo) ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row {
                            Text("FROM ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorClause)
                            Text("clientes ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("WHERE ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colorClause)
                            Text("saldo ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("> ", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = colorOperator)
                            Text("100.50;", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colorNumber)
                        }
                    }
                }

                // Category items
                categories.forEach { cat ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(cat.color)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cat.color
                                )
                                Text(
                                    text = cat.examples,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = cat.description,
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )
}

private fun extractCurrentWord(text: String, cursor: Int): String {
    if (cursor <= 0 || cursor > text.length) return ""
    var start = cursor - 1
    while (start >= 0 && (text[start].isLetterOrDigit() || text[start] == '_')) {
        start--
    }
    return text.substring(start + 1, cursor)
}

fun formatSqlText(sql: String): String {
    var s = sql.trim()
    val keywordsUpper = listOf(
        "SELECT", "FROM", "WHERE", "JOIN", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN",
        "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "INSERT INTO", "VALUES", "UPDATE",
        "SET", "DELETE FROM", "CREATE TABLE", "DROP TABLE", "ALTER TABLE", "PRIMARY KEY",
        "FOREIGN KEY", "REFERENCES", "AND", "OR", "ON", "AS", "NOT NULL", "DEFAULT"
    )

    // Capitalize keywords with regex
    for (kw in keywordsUpper) {
        val pattern = "\\b$kw\\b".toRegex(RegexOption.IGNORE_CASE)
        s = pattern.replace(s) { kw }
    }

    // Add clean line breaks before major clauses
    val clauses = listOf("FROM", "WHERE", "GROUP BY", "ORDER BY", "HAVING", "LIMIT", "LEFT JOIN", "INNER JOIN", "JOIN", "VALUES", "SET")
    for (cl in clauses) {
        val pattern = "\\s+($cl\\b)".toRegex()
        s = pattern.replace(s, "\n$1")
    }

    return s
}

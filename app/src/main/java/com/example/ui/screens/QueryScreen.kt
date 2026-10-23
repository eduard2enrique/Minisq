package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseManager
import com.example.data.LoadedSqlFile
import com.example.data.QueryRepository
import com.example.data.SqlScriptHelper
import com.example.model.QueryHistoryItem
import com.example.model.SqlExecutionResult
import com.example.model.TableSummary
import com.example.ui.components.LoadSqlOptionsDialog
import com.example.ui.components.QueryHistoryDialog
import com.example.ui.components.SavedQueriesDialog
import com.example.ui.components.SqlEditor
import com.example.ui.components.SqlExamplesDialog
import com.example.ui.components.SqlFileInspectionDialog
import com.example.ui.components.SqlQueryChart
import com.example.ui.components.SqlResultTable
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueryScreen(
    dbManager: DatabaseManager,
    queryRepo: QueryRepository,
    tables: List<TableSummary>,
    onRefreshTables: () -> Unit,
    isDark: Boolean = true,
    onExportCsvFile: (SqlExecutionResult) -> Unit,
    onExportJsonFile: (SqlExecutionResult) -> Unit,
    onOpenHistoryDrawer: () -> Unit = {},
    initialSqlToLoad: String? = null,
    triggerExecute: Boolean = false,
    onConsumedInitialSql: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sqlQuery by remember {
        mutableStateOf(
            TextFieldValue(
                text = "SELECT id, nombre, email, ciudad, saldo\nFROM clientes\nORDER BY id ASC;",
                selection = TextRange(0)
            )
        )
    }

    var executionResult by remember { mutableStateOf<SqlExecutionResult?>(null) }
    var isExecuting by remember { mutableStateOf(false) }
    var resultViewMode by remember { mutableIntStateOf(0) } // 0: Tabla, 1: Gráfico

    // Dialogs state
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var isSavingCurrent by remember { mutableStateOf(false) }
    var showExamplesDialog by remember { mutableStateOf(false) }
    var showSyntaxWarningDialog by remember { mutableStateOf(false) }
    var pendingSyntaxIssues by remember { mutableStateOf<List<com.example.data.SqlSyntaxIssue>>(emptyList()) }
    var showLoadOptionsDialog by remember { mutableStateOf(false) }
    var loadedSqlFileForInspection by remember { mutableStateOf<LoadedSqlFile?>(null) }

    // Launcher for picking .sql or .txt script files from phone storage
    val openSqlDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isExecuting = true
                val result = SqlScriptHelper.readSqlFromUri(context, uri)
                isExecuting = false
                result.onSuccess { loadedFile ->
                    sqlQuery = TextFieldValue(loadedFile.rawSql, TextRange(0))
                    loadedSqlFileForInspection = loadedFile
                }.onFailure { err ->
                    Toast.makeText(context, "Error al leer archivo SQL: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun loadSampleSqlDemo() {
        scope.launch {
            try {
                val sampleFile = SqlScriptHelper.createSampleSqlFile(context)
                val loadedFile = SqlScriptHelper.parseSqlContent(sampleFile.name, sampleFile.readText())
                sqlQuery = TextFieldValue(loadedFile.rawSql, TextRange(0))
                loadedSqlFileForInspection = loadedFile
            } catch (e: Exception) {
                Toast.makeText(context, "Error al generar demo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun executeLoadedSql(file: LoadedSqlFile) {
        scope.launch {
            isExecuting = true
            val res = dbManager.executeSql(file.rawSql)
            isExecuting = false
            executionResult = res
            loadedSqlFileForInspection = null
            if (res.isSuccess) {
                onRefreshTables()
                Toast.makeText(
                    context,
                    "Script aplicado con éxito en '${dbManager.getCurrentDbName()}'.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    context,
                    "Error al ejecutar script: ${res.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Available table names & column names for autocomplete
    val tableNames = remember(tables) { tables.map { it.name } }
    var allColumns by remember { mutableStateOf<List<String>>(emptyList()) }

    // Load columns on mount
    remember(tables) {
        scope.launch {
            val cols = mutableListOf<String>()
            for (t in tables) {
                val schema = dbManager.getTableSchema(t.name)
                cols.addAll(schema.columns.map { it.name })
            }
            allColumns = cols.distinct()
        }
    }

    fun executeCurrentSql(force: Boolean = false) {
        if (sqlQuery.text.isBlank()) {
            Toast.makeText(context, "Ingresa una consulta SQL primero", Toast.LENGTH_SHORT).show()
            return
        }

        if (!force) {
            val errors = com.example.data.SqlSyntaxValidator.validate(sqlQuery.text)
                .filter { it.severity == com.example.data.IssueSeverity.ERROR }
            if (errors.isNotEmpty()) {
                pendingSyntaxIssues = errors
                showSyntaxWarningDialog = true
                return
            }
        }

        isExecuting = true
        scope.launch {
            val res = dbManager.executeSql(sqlQuery.text)
            executionResult = res
            isExecuting = false

            // Record to history
            queryRepo.addHistoryItem(
                QueryHistoryItem(
                    sql = sqlQuery.text,
                    isSuccess = res.isSuccess,
                    elapsedMs = res.elapsedMs,
                    rowsCount = res.rows.size
                )
            )

            // Refresh tables if schema or data might have changed
            val upper = sqlQuery.text.uppercase()
            if (upper.contains("CREATE") || upper.contains("DROP") || upper.contains("ALTER") ||
                upper.contains("INSERT") || upper.contains("DELETE") || upper.contains("UPDATE")
            ) {
                onRefreshTables()
            }
        }
    }

    // Effect to handle externally selected query from side panel
    androidx.compose.runtime.LaunchedEffect(initialSqlToLoad, triggerExecute) {
        if (initialSqlToLoad != null) {
            sqlQuery = TextFieldValue(initialSqlToLoad, TextRange(initialSqlToLoad.length))
            if (triggerExecute) {
                executeCurrentSql()
            }
            onConsumedInitialSql()
        }
    }

    // Quick action templates
    fun insertTemplate(type: String) {
        val targetTable = tables.firstOrNull()?.name ?: "clientes"
        val snippet = when (type) {
            "create" -> """
                CREATE TABLE IF NOT EXISTS nueva_tabla (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    valor REAL DEFAULT 0.0,
                    creado_en TEXT DEFAULT CURRENT_TIMESTAMP
                );
            """.trimIndent()
            "insert" -> "INSERT INTO $targetTable (nombre, email, ciudad, saldo)\nVALUES ('Nuevo Usuario', 'usuario@correo.com', 'Madrid', 150.0);"
            "select" -> "SELECT * FROM $targetTable\nLIMIT 10;"
            "join" -> """
                SELECT p.id, c.nombre, p.fecha, p.total
                FROM pedidos p
                INNER JOIN clientes c ON p.cliente_id = c.id
                ORDER BY p.id DESC;
            """.trimIndent()
            "group" -> """
                SELECT categoria, COUNT(*) as cantidad, AVG(precio) as precio_medio
                FROM productos
                GROUP BY categoria;
            """.trimIndent()
            else -> "SELECT * FROM $targetTable;"
        }
        sqlQuery = TextFieldValue(snippet, TextRange(snippet.length))
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Quick Action Chips matching screenshot
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { insertTemplate("create") },
                label = { Text("+ Crear tabla", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.testTag("quick_create_table_chip")
            )
            AssistChip(
                onClick = { insertTemplate("insert") },
                label = { Text("📥 Insertar", style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
            AssistChip(
                onClick = { insertTemplate("select") },
                label = { Text("🔍 Consultar", style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
            AssistChip(
                onClick = { insertTemplate("join") },
                label = { Text("🔗 Join", style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
            AssistChip(
                onClick = { insertTemplate("group") },
                label = { Text("📊 Agrupar", style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
            AssistChip(
                onClick = onOpenHistoryDrawer,
                label = { Text("🕒 Historial (20)", style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("quick_history_drawer_chip")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Main SQL Editor Canvas
        SqlEditor(
            value = sqlQuery,
            onValueChange = { sqlQuery = it },
            availableTables = tableNames,
            availableColumns = allColumns,
            isDark = isDark,
            modifier = Modifier.testTag("sql_editor_component")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom Action Buttons matching screenshot
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Execute Primary Button
            Button(
                onClick = { executeCurrentSql() },
                enabled = !isExecuting,
                modifier = Modifier
                    .height(44.dp)
                    .testTag("execute_sql_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(22.dp)
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ejecutando...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Ejecutar",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ejecutar", fontWeight = FontWeight.Bold)
                }
            }

            // Clear Button
            FilledTonalButton(
                onClick = { sqlQuery = TextFieldValue("", TextRange(0)) },
                modifier = Modifier.height(44.dp).testTag("clear_sql_button"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = "Limpiar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Limpiar")
            }

            // Examples Button
            FilledTonalButton(
                onClick = { showExamplesDialog = true },
                modifier = Modifier.height(44.dp).testTag("sql_examples_button"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = "Ejemplos",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Ejemplos")
            }

            // Save Query Button
            FilledTonalButton(
                onClick = {
                    isSavingCurrent = true
                    showSavedDialog = true
                },
                modifier = Modifier.height(44.dp).testTag("save_query_button"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Guardar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Guardar")
            }

            // History Button (Opens lateral drawer panel)
            FilledTonalButton(
                onClick = onOpenHistoryDrawer,
                modifier = Modifier.height(44.dp).testTag("query_history_button"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Historial lateral (20)",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Historial (20)")
            }

            // Load SQL Menu Button (Opens full options: Phone file, Demo file, Saved queries, Examples)
            FilledTonalButton(
                onClick = {
                    showLoadOptionsDialog = true
                },
                modifier = Modifier.height(44.dp).testTag("load_query_button"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = "Cargar",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cargar")
            }

            // Direct Phone .sql File Picker Button
            FilledTonalButton(
                onClick = {
                    openSqlDocumentLauncher.launch(arrayOf("*/*"))
                },
                modifier = Modifier.height(44.dp).testTag("load_sql_file_direct_btn"),
                shape = RoundedCornerShape(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Cargar archivo .sql del teléfono",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cargar .sql")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Results Section
        val result = executionResult
        if (result != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (result.isSuccess) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    }
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = result.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Results visualization (Table or Chart) if query returned rows
            if (result.isSuccess && result.isQuery && result.columns.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Visualización (${result.rows.size} filas)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (resultViewMode == 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { resultViewMode = 0 }
                                    .testTag("toggle_view_table")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TableChart,
                                        contentDescription = "Tabla",
                                        tint = if (resultViewMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Tabla",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (resultViewMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (resultViewMode == 1) MaterialTheme.colorScheme.primary else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { resultViewMode = 1 }
                                    .testTag("toggle_view_chart")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = "Gráfico",
                                        tint = if (resultViewMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Gráfico",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (resultViewMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                if (resultViewMode == 0) {
                    SqlResultTable(
                        result = result,
                        onExportCsv = { onExportCsvFile(result) },
                        onExportJson = { onExportJsonFile(result) },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    SqlQueryChart(
                        result = result,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }
        }
    }

    // Dialogs
    if (showHistoryDialog) {
        QueryHistoryDialog(
            history = queryRepo.getHistory(),
            onSelectQuery = { sql ->
                sqlQuery = TextFieldValue(sql, TextRange(sql.length))
            },
            onClearHistory = { queryRepo.clearHistory() },
            onDismiss = { showHistoryDialog = false }
        )
    }

    if (showSavedDialog) {
        SavedQueriesDialog(
            savedQueries = queryRepo.getSavedQueries(),
            currentQuery = sqlQuery.text,
            onSelectQuery = { sql ->
                sqlQuery = TextFieldValue(sql, TextRange(sql.length))
            },
            onSaveQuery = { title, desc ->
                queryRepo.saveQuery(title, sqlQuery.text, desc)
                Toast.makeText(context, "Consulta guardada con éxito", Toast.LENGTH_SHORT).show()
            },
            onDeleteSaved = { id -> queryRepo.deleteSavedQuery(id) },
            isSavingCurrent = isSavingCurrent,
            onDismiss = { showSavedDialog = false }
        )
    }

    if (showExamplesDialog) {
        SqlExamplesDialog(
            examples = queryRepo.getEducationalExamples(),
            onSelectExample = { sql ->
                sqlQuery = TextFieldValue(sql, TextRange(sql.length))
            },
            onDismiss = { showExamplesDialog = false }
        )
    }

    // Modal: Syntax Warning Before Execution
    if (showSyntaxWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSyntaxWarningDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Error de Sintaxis Detectado")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Se detectaron posibles fallos sintácticos antes de enviar la consulta al motor SQLite:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    pendingSyntaxIssues.take(3).forEach { issue ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "• Línea ${issue.line}: ${issue.message}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (issue.suggestion != null) {
                                    Text(
                                        text = "  Sugerencia: ${issue.suggestion}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSyntaxWarningDialog = false
                        executeCurrentSql(force = true)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ejecutar de todos modos")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSyntaxWarningDialog = false }) {
                    Text("Corregir en editor")
                }
            }
        )
    }

    // Modal: Load SQL Options (Phone Storage, Demo File, Saved Queries, Examples)
    if (showLoadOptionsDialog) {
        LoadSqlOptionsDialog(
            onPickPhoneFile = {
                openSqlDocumentLauncher.launch(arrayOf("*/*"))
            },
            onLoadSampleFile = {
                loadSampleSqlDemo()
            },
            onOpenSavedQueries = {
                isSavingCurrent = false
                showSavedDialog = true
            },
            onOpenExamples = {
                showExamplesDialog = true
            },
            onDismiss = { showLoadOptionsDialog = false }
        )
    }

    // Modal: Inspection of Loaded SQL File with Tables & Direct Execution
    loadedSqlFileForInspection?.let { file ->
        SqlFileInspectionDialog(
            file = file,
            currentDatabaseName = dbManager.getCurrentDbName(),
            onExecuteImmediately = {
                executeLoadedSql(file)
            },
            onKeepInEditorOnly = {
                loadedSqlFileForInspection = null
                Toast.makeText(context, "Script transferido al editor. Listo para revisar.", Toast.LENGTH_SHORT).show()
            },
            onDismiss = {
                loadedSqlFileForInspection = null
            }
        )
    }
}

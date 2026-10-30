package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Input
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TableChart
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.ui.components.QueryReportDialog
import com.example.ui.components.SavedQueriesDialog
import com.example.ui.components.SqlEditor
import com.example.ui.components.SqlErrorTutorCard
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
    onExportReportFile: ((String) -> Unit)? = null,
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
    var showReportDialog by remember { mutableStateOf(false) }
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
    var allTableSchemas by remember { mutableStateOf<List<com.example.model.TableSchemaDetail>>(emptyList()) }

    // Load columns and schema details on mount / table changes
    remember(tables) {
        scope.launch {
            val schemas = mutableListOf<com.example.model.TableSchemaDetail>()
            val cols = mutableListOf<String>()
            for (t in tables) {
                val schema = dbManager.getTableSchema(t.name)
                schemas.add(schema)
                cols.addAll(schema.columns.map { it.name })
            }
            allColumns = cols.distinct()
            allTableSchemas = schemas
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
                SELECT
                    p.id,
                    c.nombre,
                    p.fecha,
                    p.total
                FROM pedidos p
                INNER JOIN clientes c
                    ON p.cliente_id = c.id
                ORDER BY p.id DESC;
            """.trimIndent()
            "group" -> """
                SELECT
                    categoria,
                    COUNT(*) AS cantidad,
                    ROUND(AVG(precio), 2) AS precio_medio
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
        // Quick Action Chips in sleek iOS squircle pills
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
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(15.dp)) },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.testTag("quick_create_table_chip")
            )
            AssistChip(
                onClick = { insertTemplate("insert") },
                label = { Text("Insertar", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Outlined.Input, contentDescription = null, modifier = Modifier.size(15.dp)) },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            AssistChip(
                onClick = { insertTemplate("select") },
                label = { Text("Consultar", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(15.dp)) },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            AssistChip(
                onClick = { insertTemplate("join") },
                label = { Text("Join", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp)) },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            AssistChip(
                onClick = { insertTemplate("group") },
                label = { Text("Agrupar", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(15.dp)) },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
            )
            AssistChip(
                onClick = onOpenHistoryDrawer,
                label = { Text("Historial (20)", style = MaterialTheme.typography.labelSmall) },
                leadingIcon = { Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(15.dp)) },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    labelColor = MaterialTheme.colorScheme.primary
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
            tableSchemas = allTableSchemas,
            isDark = isDark,
            modifier = Modifier.testTag("sql_editor_component")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // iOS & AI Studio Styled Bottom Action Center
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Primary Action & Quick Clear
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Execute Hero Button (iOS primary pill with electric blue & drawn play arrow)
                Button(
                    onClick = { executeCurrentSql() },
                    enabled = !isExecuting,
                    modifier = Modifier
                        .weight(1.65f)
                        .height(48.dp)
                        .testTag("execute_sql_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ejecutando...", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Ejecutar",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ejecutar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }

                // Clear Button (iOS soft card with drawn outline cleaning icon)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { sqlQuery = TextFieldValue("", TextRange(0)) }
                        .testTag("clear_sql_button"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = "Limpiar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Limpiar",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Row 2: Secondary Tool Strip (Balanced 4-button row with AI Studio drawn outline icons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Ejemplos (AI Studio Sparkle / AutoAwesome drawn outline)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showExamplesDialog = true }
                        .testTag("sql_examples_button"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Ejemplos",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Ejemplos",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 2. Historial (Drawn outline clock)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenHistoryDrawer() }
                        .testTag("query_history_button"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "Historial",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Historial",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 3. Guardar (Drawn outline bookmark / diskette)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            isSavingCurrent = true
                            showSavedDialog = true
                        }
                        .testTag("save_query_button"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = "Guardar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Guardar",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // 4. Cargar (Drawn outline folder / SQL picker)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showLoadOptionsDialog = true }
                        .testTag("load_query_button"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = "Cargar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Cargar",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
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
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (result.isSuccess) Icons.Outlined.CheckCircleOutline else Icons.Outlined.ErrorOutline,
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

            if (!result.isSuccess) {
                SqlErrorTutorCard(
                    errorMessage = result.message,
                    currentQuery = sqlQuery.text,
                    onApplyFix = { fixedSql ->
                        sqlQuery = TextFieldValue(fixedSql, TextRange(fixedSql.length))
                        Toast.makeText(context, "Sugerencia aplicada al editor SQL", Toast.LENGTH_SHORT).show()
                    },
                    onOpenExamples = { showExamplesDialog = true },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                                        imageVector = Icons.Outlined.TableChart,
                                        contentDescription = "Tabla",
                                        tint = if (resultViewMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Tabla",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (resultViewMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
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
                                        imageVector = Icons.Outlined.BarChart,
                                        contentDescription = "Gráfico",
                                        tint = if (resultViewMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Gráfico",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (resultViewMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        softWrap = false
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
                        onOpenReport = { showReportDialog = true },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else {
                    SqlQueryChart(
                        result = result,
                        onOpenReport = { showReportDialog = true },
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

    if (showReportDialog && executionResult != null) {
        QueryReportDialog(
            result = executionResult!!,
            activeDbName = dbManager.getCurrentDbName(),
            onDismiss = { showReportDialog = false },
            onSaveReportFile = { reportText ->
                onExportReportFile?.invoke(reportText)
                showReportDialog = false
            }
        )
    }

    // Modal: Syntax Warning Before Execution
    if (showSyntaxWarningDialog) {
        AlertDialog(
            onDismissRequest = { showSyntaxWarningDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
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
                showLoadOptionsDialog = false
                openSqlDocumentLauncher.launch(arrayOf("*/*"))
            },
            onLoadSampleFile = {
                showLoadOptionsDialog = false
                loadSampleSqlDemo()
            },
            onOpenSavedQueries = {
                showLoadOptionsDialog = false
                isSavingCurrent = false
                showSavedDialog = true
            },
            onOpenExamples = {
                showLoadOptionsDialog = false
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

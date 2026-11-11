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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DatabaseManager
import com.example.data.LoadedSqlFile
import com.example.data.QueryRepository
import com.example.data.SqlScriptHelper
import com.example.model.QueryHistoryItem
import com.example.model.SqlExecutionResult
import com.example.model.TableSummary
import com.example.ui.components.AutoResizeText
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun QueryScreen(
    dbManager: DatabaseManager,
    queryRepo: QueryRepository,
    tables: List<TableSummary>,
    onRefreshTables: () -> Unit,
    isDark: Boolean = true,
    activeDbName: String = dbManager.getCurrentDbName(),
    onExportCsvFile: (SqlExecutionResult) -> Unit,
    onExportJsonFile: (SqlExecutionResult) -> Unit,
    onExportReportFile: ((String) -> Unit)? = null,
    onOpenHistoryDrawer: () -> Unit = {},
    initialSqlToLoad: String? = null,
    triggerExecute: Boolean = false,
    onConsumedInitialSql: () -> Unit = {},
    persistedSqlQuery: TextFieldValue? = null,
    onSqlQueryChange: ((TextFieldValue) -> Unit)? = null,
    persistedExecutionResult: SqlExecutionResult? = null,
    onExecutionResultChange: ((SqlExecutionResult?) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    val scrollState = rememberScrollState()
    val resultsRequester = remember { BringIntoViewRequester() }
    var resultsSectionY by remember { mutableIntStateOf(-1) }
    var scrollToResultsTrigger by remember { mutableIntStateOf(0) }

    var executionResult by remember { mutableStateOf<SqlExecutionResult?>(persistedExecutionResult) }
    var isExecuting by remember { mutableStateOf(false) }
    var resultViewMode by remember { mutableIntStateOf(0) } // 0: Tabla, 1: Gráfico
    var fabOffset by remember { mutableStateOf(Offset.Zero) }
    var isManuallyPositioned by remember { mutableStateOf(false) }

    fun scrollToResults() {
        keyboardController?.hide()
        scope.launch {
            if (resultsSectionY > 0) {
                val targetY = (resultsSectionY - 16).coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(
                    targetY,
                    animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                )
            } else {
                try {
                    resultsRequester.bringIntoView()
                } catch (_: Exception) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        }
    }

    fun scrollToEditor() {
        scope.launch {
            scrollState.animateScrollTo(
                0,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
    }

    androidx.compose.runtime.LaunchedEffect(scrollToResultsTrigger) {
        if (scrollToResultsTrigger > 0 && executionResult != null) {
            keyboardController?.hide()
            kotlinx.coroutines.delay(120)
            if (resultsSectionY <= 0) {
                kotlinx.coroutines.delay(80)
            }
            if (resultsSectionY > 0) {
                val targetY = (resultsSectionY - 16).coerceIn(0, scrollState.maxValue)
                scrollState.animateScrollTo(
                    targetY,
                    animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                )
            } else {
                try {
                    resultsRequester.bringIntoView()
                } catch (_: Exception) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        }
    }

    val initialSql = remember(activeDbName) {
        persistedSqlQuery?.text?.takeIf { it.isNotBlank() }
            ?: getDefaultQueryForDatabase(activeDbName, tables.map { it.name })
    }
    var sqlQuery by remember(activeDbName) {
        mutableStateOf(
            persistedSqlQuery ?: TextFieldValue(
                text = initialSql,
                selection = TextRange(initialSql.length)
            )
        )
    }

    LaunchedEffect(persistedSqlQuery) {
        if (persistedSqlQuery != null && persistedSqlQuery.text != sqlQuery.text) {
            sqlQuery = persistedSqlQuery
        }
    }

    fun updateSqlQuery(newValue: TextFieldValue) {
        sqlQuery = newValue
        onSqlQueryChange?.invoke(newValue)
    }

    var lastDb by remember { mutableStateOf(activeDbName) }
    androidx.compose.runtime.LaunchedEffect(activeDbName) {
        if (activeDbName != lastDb && initialSqlToLoad == null && persistedSqlQuery == null) {
            lastDb = activeDbName
            val newSql = getDefaultQueryForDatabase(activeDbName, tables.map { it.name })
            updateSqlQuery(TextFieldValue(newSql, TextRange(newSql.length)))
        }
    }

    // Dialogs state
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showSavedDialog by remember { mutableStateOf(false) }
    var isSavingCurrent by remember { mutableStateOf(false) }
    var showExamplesDialog by remember { mutableStateOf(false) }
    var rememberedExamplesCategory by rememberSaveable { mutableStateOf("Todas") }
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
                    updateSqlQuery(TextFieldValue(loadedFile.rawSql, TextRange(0)))
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
                updateSqlQuery(TextFieldValue(loadedFile.rawSql, TextRange(0)))
                loadedSqlFileForInspection = loadedFile
            } catch (e: Exception) {
                Toast.makeText(context, "Error al generar demo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun executeLoadedSql(file: LoadedSqlFile) {
        keyboardController?.hide()
        scope.launch {
            isExecuting = true
            val res = dbManager.executeSql(file.rawSql)
            isExecuting = false
            executionResult = res
            onExecutionResultChange?.invoke(res)
            scrollToResultsTrigger++
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

    // Load columns and schema details safely whenever active DB or tables list changes
    LaunchedEffect(activeDbName, tables) {
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

        keyboardController?.hide()
        isExecuting = true
        scope.launch {
            val res = dbManager.executeSql(sqlQuery.text)
            executionResult = res
            onExecutionResultChange?.invoke(res)
            isExecuting = false
            scrollToResultsTrigger++

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
            updateSqlQuery(TextFieldValue(initialSqlToLoad, TextRange(initialSqlToLoad.length)))
            if (triggerExecute) {
                executeCurrentSql()
            }
            onConsumedInitialSql()
        }
    }

    // Quick action templates (Accurate for active database: Otorgamiento, Tienda, etc.)
    fun insertTemplate(type: String) {
        val snippet = getSnippetForDatabase(type, activeDbName, tables.map { it.name })
        updateSqlQuery(TextFieldValue(snippet, TextRange(snippet.length)))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val maxWidthPx = constraints.maxWidth.toFloat()
        val maxHeightPx = constraints.maxHeight.toFloat()
        val edgeMarginPx = with(density) { 16.dp.toPx() }
        var fabSize by remember { mutableStateOf(IntSize.Zero) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .padding(bottom = 76.dp)
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
            onValueChange = { updateSqlQuery(it) },
            availableTables = tableNames,
            availableColumns = allColumns,
            tableSchemas = allTableSchemas,
            isDark = isDark,
            onExecuteSql = { executeCurrentSql() },
            isExecuting = isExecuting,
            modifier = Modifier.testTag("sql_editor_component")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // iOS & AI Studio Styled Bottom Action Center
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: Quick Query Actions (Limpiar + Ejemplos)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear Editor Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { updateSqlQuery(TextFieldValue("", TextRange(0))) }
                        .testTag("clear_sql_button"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Limpiar",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Examples Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showExamplesDialog = true }
                        .testTag("sql_examples_button"),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
                    tonalElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = "Ejemplos",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Ejemplos",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Row 2: Secondary Toolset (Historial + Guardar + Cargar .sql)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Historial
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
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
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = "Historial",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Historial",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Guardar
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
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
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BookmarkBorder,
                            contentDescription = "Guardar",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Guardar",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Cargar .sql
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
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
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FolderOpen,
                            contentDescription = "Cargar SQL",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "Cargar",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Quick Jump to Results if a result already exists
            if (executionResult != null) {
                Surface(
                    onClick = { scrollToResults() },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("jump_to_results_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (executionResult!!.isSuccess) "Ver visualización de la consulta (${executionResult!!.rows.size} filas) ⬇" else "Ver diagnóstico del error ⬇",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Results Section Anchor & Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    resultsSectionY = coordinates.positionInParent().y.toInt()
                }
                .bringIntoViewRequester(resultsRequester)
        ) {
            val result = executionResult
            if (result != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header with title and "Subir al editor"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (result.isSuccess) Icons.Outlined.Assessment else Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = if (result.isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Resultado",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (result.isSuccess && result.elapsedMs > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "${result.elapsedMs} ms",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { scrollToEditor() }
                                .testTag("scroll_to_editor_top_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = "Subir al editor",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Subir al editor",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

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
                    availableTables = tables.map { it.name },
                    onApplyFix = { fixedSql ->
                        updateSqlQuery(TextFieldValue(fixedSql, TextRange(fixedSql.length)))
                        Toast.makeText(context, "Sugerencia aplicada al editor SQL", Toast.LENGTH_SHORT).show()
                    },
                    onOpenExamples = { category ->
                        rememberedExamplesCategory = category
                        showExamplesDialog = true
                    },
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
                        AutoResizeText(
                            text = "Visualización (${result.rows.size} filas)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            minFontSize = 9.sp,
                            targetFontSize = 12.sp
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

            // Bottom quick return button
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = { scrollToEditor() },
                    modifier = Modifier.testTag("scroll_to_editor_bottom_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Volver arriba al editor SQL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        }
        }
    }

        // Smart Intelligent Floating "Ejecutar" Button
        // Docks naturally at bottom-right above the keyboard, clamped strictly within visible bounds
        val lineCount = remember(sqlQuery.text) { sqlQuery.text.count { it == '\n' } + 1 }
        val smartVerticalOffset by animateFloatAsState(
            targetValue = if (isManuallyPositioned) 0f else {
                when {
                    lineCount in 2..4 -> -6f * (lineCount - 1)
                    lineCount >= 5 -> -24f
                    else -> 0f
                }
            },
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "smartFabOffset"
        )
        val hasSqlContent = sqlQuery.text.isNotBlank()
        val minOffsetX = if (fabSize.width > 0) -(maxWidthPx - fabSize.width - edgeMarginPx * 1.5f).coerceAtLeast(0f) else -400f
        val minOffsetY = if (fabSize.height > 0) -(maxHeightPx - fabSize.height - edgeMarginPx * 1.5f).coerceAtLeast(0f) else -400f
        val effectiveOffsetX = fabOffset.x.coerceIn(minOffsetX, 0f)
        val effectiveOffsetY = (fabOffset.y + if (!isManuallyPositioned) smartVerticalOffset else 0f).coerceIn(minOffsetY, 0f)

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .imePadding()
                .padding(bottom = 12.dp, end = 16.dp)
                .onSizeChanged { fabSize = it }
                .offset { IntOffset(effectiveOffsetX.roundToInt(), effectiveOffsetY.roundToInt()) }
                .testTag("floating_execute_sql_button"),
            shape = RoundedCornerShape(22.dp),
            color = if (hasSqlContent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (hasSqlContent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            shadowElevation = if (hasSqlContent) 8.dp else 3.dp,
            tonalElevation = 6.dp,
            border = BorderStroke(
                1.dp,
                if (hasSqlContent) Color.White.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            ) {
                // Drag handle (Grip indicator) - allows dragging within visible screen bounds
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (hasSqlContent) Color.White.copy(alpha = 0.20f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                        .pointerInput(minOffsetX, minOffsetY) {
                            detectDragGestures(
                                onDragStart = { isManuallyPositioned = true },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val updated = fabOffset + dragAmount
                                    fabOffset = Offset(
                                        x = updated.x.coerceIn(minOffsetX, 0f),
                                        y = updated.y.coerceIn(minOffsetY, 0f)
                                    )
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DragIndicator,
                        contentDescription = "Arrastrar botón flotante",
                        tint = if (hasSqlContent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // If manually positioned by user, offer quick re-dock anchor button
                if (isManuallyPositioned) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasSqlContent) Color.White.copy(alpha = 0.18f)
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                            .clickable {
                                fabOffset = Offset.Zero
                                isManuallyPositioned = false
                                Toast.makeText(context, "Seguimiento inteligente activado", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = "Re-anclar a posición inteligente",
                            tint = if (hasSqlContent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Tap target to execute current SQL
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !isExecuting) { executeCurrentSql() }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(17.dp),
                            strokeWidth = 2.dp,
                            color = if (hasSqlContent) Color.White else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ejecutando...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (hasSqlContent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = "Ejecutar",
                            modifier = Modifier.size(19.dp),
                            tint = if (hasSqlContent) Color.White else MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Ejecutar",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = if (hasSqlContent) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showHistoryDialog) {
        QueryHistoryDialog(
            history = queryRepo.getHistory(),
            onSelectQuery = { sql ->
                updateSqlQuery(TextFieldValue(sql, TextRange(sql.length)))
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
                updateSqlQuery(TextFieldValue(sql, TextRange(sql.length)))
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
            examples = queryRepo.getEducationalExamplesForDb(activeDbName),
            initialCategory = rememberedExamplesCategory,
            onCategoryChanged = { rememberedExamplesCategory = it },
            onSelectExample = { sql ->
                updateSqlQuery(TextFieldValue(sql, TextRange(sql.length)))
            },
            onExecuteExample = { sql ->
                updateSqlQuery(TextFieldValue(sql, TextRange(sql.length)))
                executeCurrentSql(force = true)
            },
            onDismiss = {
                showExamplesDialog = false
            }
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

fun getDefaultQueryForDatabase(activeDb: String, tableNames: List<String>): String {
    val isClinica = activeDb.contains("clinica", ignoreCase = true) ||
            activeDb.contains("clinic", ignoreCase = true) ||
            (tableNames.contains("citas") && tableNames.contains("medicos"))
    val isSecretaria = activeDb.contains("secretaria", ignoreCase = true) ||
            tableNames.contains("reasignaciones") ||
            (tableNames.contains("grupos") && tableNames.contains("materias"))
    val isOtorgamiento = activeDb.contains("otorgamiento", ignoreCase = true) ||
            tableNames.contains("boletas_solicitud") ||
            (tableNames.contains("estudiantes") && tableNames.contains("carreras"))
    val isFacultad = (tableNames.contains("profesores") && tableNames.contains("materias")) ||
            activeDb.contains("facultad", ignoreCase = true)
    val isCuba = activeDb.contains("cuba", ignoreCase = true) || tableNames.contains("provincias")
    val isTienda = tableNames.contains("clientes") || tableNames.contains("pedidos") ||
            activeDb.contains("tienda", ignoreCase = true)
    val isEcommerce = activeDb.contains("ecommerce", ignoreCase = true) ||
            (tableNames.contains("articulos") && tableNames.contains("categorias"))
    val isUniversity = activeDb.contains("university", ignoreCase = true) ||
            activeDb.contains("universidad", ignoreCase = true) ||
            (tableNames.contains("calificaciones") && tableNames.contains("cursos"))
    val isProjects = activeDb.contains("project", ignoreCase = true) ||
            activeDb.contains("proyecto", ignoreCase = true) ||
            (tableNames.contains("proyectos") && tableNames.contains("tareas"))

    return when {
        isClinica -> """
            SELECT
                c.id AS cita_id,
                p.nombre AS paciente,
                m.nombre AS medico,
                m.especialidad,
                c.fecha,
                c.motivo,
                c.costo
            FROM citas c
            INNER JOIN pacientes p ON c.paciente_id = p.id
            INNER JOIN medicos m ON c.medico_id = m.id
            ORDER BY c.fecha DESC;
        """.trimIndent()

        isSecretaria -> """
            -- Base de Datos: Secretaría Escolar
            -- Tablas: grupos, materias, estudiantes_matricula, reasignaciones
            SELECT * FROM grupos;
        """.trimIndent()

        isOtorgamiento -> """
            -- Escalafón Oficial Provincial (50% Índice Académico + 50% Exámenes de Ingreso)
            SELECT 
                ROW_NUMBER() OVER (
                    ORDER BY 
                        ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) DESC,
                        ex.nota_matematica DESC,
                        e.indice_academico DESC
                ) AS lugar_escalafon,
                e.carnet_identidad,
                e.nombre_completo,
                e.municipio,
                e.indice_academico,
                ex.nota_matematica AS mat,
                ex.nota_espanol AS esp,
                ex.nota_historia AS hist,
                ROUND((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0, 2) AS prom_examenes,
                ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS puntuacion_final
            FROM estudiantes e
            JOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante
            ORDER BY lugar_escalafon ASC;
        """.trimIndent()

        isFacultad -> """
            SELECT
                e.matricula,
                e.nombre AS estudiante,
                m.nombre AS materia,
                p.nombre AS profesor,
                e.promedio
            FROM estudiantes e
            INNER JOIN materias m ON e.materia_id = m.id
            INNER JOIN profesores p ON m.profesor_id = p.id
            ORDER BY e.promedio DESC;
        """.trimIndent()

        isCuba -> """
            SELECT
                p.nombre AS provincia,
                p.capital,
                p.region,
                l.nombre AS lugar_turistico,
                l.tipo
            FROM provincias p
            LEFT JOIN lugares_interes l ON p.id = l.provincia_id
            ORDER BY p.id ASC;
        """.trimIndent()

        isTienda -> """
            SELECT
                p.id AS pedido_num,
                c.nombre AS cliente,
                p.fecha,
                p.total,
                p.estado
            FROM pedidos p
            INNER JOIN clientes c ON p.cliente_id = c.id
            ORDER BY p.id DESC
            LIMIT 10;
        """.trimIndent()

        isEcommerce -> """
            SELECT
                a.id,
                a.nombre AS articulo,
                c.nombre AS categoria,
                a.precio
            FROM articulos a
            INNER JOIN categorias c ON a.categoria_id = c.id
            ORDER BY a.precio DESC;
        """.trimIndent()

        isUniversity -> """
            SELECT
                e.matricula,
                e.nombre AS estudiante,
                c.titulo AS curso,
                p.nombre AS profesor,
                cal.nota
            FROM calificaciones cal
            INNER JOIN estudiantes e ON cal.estudiante_id = e.id
            INNER JOIN cursos c ON cal.curso_id = c.id
            INNER JOIN profesores p ON c.profesor_id = p.id
            ORDER BY cal.nota DESC;
        """.trimIndent()

        isProjects -> """
            SELECT
                pr.nombre AS proyecto,
                t.titulo AS tarea,
                t.prioridad,
                m.nombre AS responsable,
                t.completada
            FROM tareas t
            INNER JOIN proyectos pr ON t.proyecto_id = pr.id
            LEFT JOIN miembros m ON t.miembro_id = m.id
            ORDER BY t.completada ASC, pr.nombre ASC;
        """.trimIndent()

        tableNames.isNotEmpty() -> """
            SELECT * FROM "${tableNames.first()}"
            LIMIT 20;
        """.trimIndent()

        else -> "-- Base de datos en blanco. Diseña una tabla o importa un archivo SQL para comenzar."
    }
}

internal fun getSnippetForDatabase(type: String, activeDb: String, tableNames: List<String>): String {
    val isOtorgamiento = activeDb.contains("otorgamiento", ignoreCase = true) ||
            tableNames.contains("boletas_solicitud") ||
            (tableNames.contains("estudiantes") && tableNames.contains("carreras"))
    val isFacultad = (tableNames.contains("profesores") && tableNames.contains("materias")) ||
            activeDb.contains("facultad", ignoreCase = true)
    val isCuba = activeDb.contains("cuba", ignoreCase = true) || tableNames.contains("provincias")
    val isTienda = tableNames.contains("clientes") || tableNames.contains("pedidos") ||
            activeDb.contains("tienda", ignoreCase = true)
    val isSecretaria = activeDb.contains("secretaria", ignoreCase = true) ||
            tableNames.contains("grupos") || tableNames.contains("reasignaciones")
    val isClinica = activeDb.contains("clinica", ignoreCase = true) ||
            activeDb.contains("clinic", ignoreCase = true) ||
            (tableNames.contains("citas") && tableNames.contains("medicos"))
    val isEcommerce = activeDb.contains("ecommerce", ignoreCase = true) ||
            (tableNames.contains("articulos") && tableNames.contains("categorias"))
    val isUniversity = activeDb.contains("university", ignoreCase = true) ||
            activeDb.contains("universidad", ignoreCase = true) ||
            (tableNames.contains("calificaciones") && tableNames.contains("cursos"))
    val isProjects = activeDb.contains("project", ignoreCase = true) ||
            activeDb.contains("proyecto", ignoreCase = true) ||
            (tableNames.contains("proyectos") && tableNames.contains("tareas"))
    val firstTable = tableNames.firstOrNull() ?: "nueva_tabla"

    return when (type) {
        "create" -> """
            CREATE TABLE IF NOT EXISTS nueva_tabla (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre TEXT NOT NULL,
                descripcion TEXT,
                valor REAL DEFAULT 0.0,
                creado_en TEXT DEFAULT CURRENT_TIMESTAMP
            );
        """.trimIndent()

        "insert" -> when {
            isClinica -> """
                INSERT INTO citas (paciente_id, medico_id, fecha, motivo, costo)
                VALUES (1, 2, '2026-11-25', 'Consulta de valoración pediátrica', 65.0);
            """.trimIndent()

            isSecretaria -> """
                INSERT INTO estudiantes_matricula (
                    numero_expediente,
                    nombre_completo,
                    sexo,
                    id_grupo,
                    promedio_ingreso,
                    estado_matricula
                ) VALUES (
                    'EXP-2026-${(100..999).random()}',
                    'Karel Ramos Ferrás',
                    'M',
                    1,
                    94.30,
                    'Activo'
                );
            """.trimIndent()

            isOtorgamiento -> """
                INSERT INTO estudiantes (
                    carnet_identidad,
                    carnet_preuniversitario,
                    nombre_completo,
                    sexo,
                    via_ingreso,
                    indice_academico,
                    provincia,
                    municipio,
                    centro_estudio
                ) VALUES (
                    '950808${(10000..99999).random()}',
                    '2026-${(100..999).random()}',
                    'Alejandro Morales Cruz',
                    'M',
                    'Preuniversitario',
                    96.20,
                    'Holguín',
                    'Moa',
                    'IPU Antonio López Fernández'
                );
            """.trimIndent()

            isFacultad -> """
                INSERT INTO estudiantes (nombre, matricula, materia_id, promedio, ano_carrera)
                VALUES ('Nuevo Alumno', 'EST-2026-${(100..999).random()}', 2, 95.0, 1);
            """.trimIndent()

            isCuba -> """
                INSERT INTO lugares_interes (provincia_id, nombre, tipo, patrimonio_unesco)
                VALUES (12, 'Cayo Saetía', 'Parque Natural y Playa', 0);
            """.trimIndent()

            isTienda -> """
                INSERT INTO clientes (nombre, email, ciudad, saldo)
                VALUES ('Carlos Manuel Pérez', 'carlos_${(100..999).random()}@ejemplo.cu', 'Moa', 250.0);
            """.trimIndent()

            isEcommerce -> """
                INSERT INTO articulos (categoria_id, nombre, precio)
                VALUES (1, 'Teclado USB Pro ${(10..99).random()}', 49.90);
            """.trimIndent()

            isUniversity -> """
                INSERT INTO estudiantes (nombre, matricula)
                VALUES ('Lucía Herrera', 'EST-2026-${(100..999).random()}');
            """.trimIndent()

            isProjects -> """
                INSERT INTO proyectos (nombre, descripcion, presupuesto, estado)
                VALUES ('Nuevo Proyecto ${(10..99).random()}', 'Proyecto de prueba en aula', 3500.0, 'Activo');
            """.trimIndent()

            else -> """
                INSERT INTO "$firstTable" DEFAULT VALUES;
            """.trimIndent()
        }

        "select" -> when {
            isClinica -> """
                SELECT id, paciente_id, medico_id, fecha, motivo, costo
                FROM citas
                ORDER BY fecha DESC;
            """.trimIndent()

            isSecretaria -> """
                SELECT id_estudiante, numero_expediente, nombre_completo, sexo, id_grupo, promedio_ingreso, estado_matricula
                FROM estudiantes_matricula
                ORDER BY promedio_ingreso DESC;
            """.trimIndent()

            isOtorgamiento -> """
                -- Boleta de Solicitud: Las 10 carreras elegidas por el estudiante #1
                SELECT
                    b.orden_prioridad AS prioridad,
                    c.nombre_carrera AS carrera_solicitada,
                    u.siglas AS universidad,
                    u.provincia AS sede,
                    a.nombre_agrupacion AS area
                FROM boletas_solicitud b
                INNER JOIN carreras c ON b.id_carrera = c.id_carrera
                INNER JOIN universidades u ON c.id_universidad = u.id_universidad
                INNER JOIN agrupaciones_carreras a ON c.id_agrupacion = a.id_agrupacion
                WHERE b.id_estudiante = 1
                ORDER BY b.orden_prioridad ASC;
            """.trimIndent()

            isFacultad -> """
                SELECT id, matricula, nombre, materia_id, promedio
                FROM estudiantes
                ORDER BY promedio DESC
                LIMIT 15;
            """.trimIndent()

            isCuba -> """
                SELECT id, nombre, capital, region, poblacion
                FROM provincias
                ORDER BY id ASC;
            """.trimIndent()

            isTienda -> """
                SELECT id, nombre, email, ciudad, saldo
                FROM clientes
                ORDER BY id ASC
                LIMIT 15;
            """.trimIndent()

            isEcommerce -> """
                SELECT id, categoria_id, nombre, precio
                FROM articulos
                ORDER BY precio DESC;
            """.trimIndent()

            isUniversity -> """
                SELECT id, nombre, matricula
                FROM estudiantes
                ORDER BY id ASC;
            """.trimIndent()

            isProjects -> """
                SELECT id, nombre, presupuesto, estado
                FROM proyectos
                ORDER BY id ASC;
            """.trimIndent()

            else -> """
                SELECT * FROM "$firstTable"
                LIMIT 20;
            """.trimIndent()
        }

        "join" -> when {
            isClinica -> """
                -- Citas Médicas con datos completos de Pacientes y Especialistas
                SELECT
                    c.id AS cita_id,
                    p.nombre AS paciente,
                    p.edad,
                    m.nombre AS medico,
                    m.especialidad,
                    c.fecha,
                    c.motivo,
                    c.costo
                FROM citas c
                INNER JOIN pacientes p ON c.paciente_id = p.id
                INNER JOIN medicos m ON c.medico_id = m.id
                ORDER BY c.fecha DESC;
            """.trimIndent()

            isSecretaria -> """
                -- Estudiantes con su Grupo Oficial, Aula y Profesor Tutor
                SELECT
                    e.numero_expediente,
                    e.nombre_completo AS estudiante,
                    g.codigo_grupo AS grupo,
                    g.aula,
                    g.profesor_tutor,
                    e.promedio_ingreso
                FROM estudiantes_matricula e
                INNER JOIN grupos g ON e.id_grupo = g.id_grupo
                ORDER BY g.codigo_grupo, e.nombre_completo;
            """.trimIndent()

            isOtorgamiento -> """
                -- Otorgamiento Oficial: Cruce de Escalafón, Opción Alcanzada y Carrera Asignada
                SELECT
                    e.nombre_completo AS estudiante,
                    ROUND((e.indice_academico * 0.5) + (((ex.nota_matematica + ex.nota_espanol + ex.nota_historia) / 3.0) * 0.5), 2) AS escalafon,
                    o.prioridad_otorgada AS opcion_alcanzada,
                    c.nombre_carrera AS carrera_otorgada,
                    u.siglas AS universidad,
                    o.estado
                FROM otorgamientos o
                INNER JOIN estudiantes e ON o.id_estudiante = e.id_estudiante
                INNER JOIN examenes_ingreso ex ON e.id_estudiante = ex.id_estudiante
                INNER JOIN plazas_oferta p ON o.id_plaza = p.id_plaza
                INNER JOIN carreras c ON p.id_carrera = c.id_carrera
                INNER JOIN universidades u ON p.id_universidad = u.id_universidad
                ORDER BY escalafon DESC;
            """.trimIndent()

            isFacultad -> """
                SELECT
                    e.matricula,
                    e.nombre AS estudiante,
                    m.nombre AS materia,
                    p.nombre AS profesor,
                    e.promedio
                FROM estudiantes e
                INNER JOIN materias m ON e.materia_id = m.id
                INNER JOIN profesores p ON m.profesor_id = p.id
                ORDER BY e.promedio DESC;
            """.trimIndent()

            isCuba -> """
                SELECT
                    p.nombre AS provincia,
                    p.capital,
                    l.nombre AS atractivo_turistico,
                    l.tipo
                FROM provincias p
                INNER JOIN lugares_interes l ON p.id = l.provincia_id
                ORDER BY p.nombre ASC;
            """.trimIndent()

            isTienda -> """
                SELECT
                    p.id AS pedido_id,
                    c.nombre AS cliente,
                    c.ciudad,
                    p.fecha,
                    p.total,
                    p.estado
                FROM pedidos p
                INNER JOIN clientes c ON p.cliente_id = c.id
                ORDER BY p.id DESC;
            """.trimIndent()

            isEcommerce -> """
                SELECT
                    a.id,
                    a.nombre AS articulo,
                    c.nombre AS categoria,
                    a.precio
                FROM articulos a
                INNER JOIN categorias c ON a.categoria_id = c.id
                ORDER BY a.precio DESC;
            """.trimIndent()

            isUniversity -> """
                SELECT
                    e.nombre AS estudiante,
                    c.titulo AS curso,
                    p.nombre AS profesor,
                    cal.nota
                FROM calificaciones cal
                INNER JOIN estudiantes e ON cal.estudiante_id = e.id
                INNER JOIN cursos c ON cal.curso_id = c.id
                INNER JOIN profesores p ON c.profesor_id = p.id
                ORDER BY cal.nota DESC;
            """.trimIndent()

            isProjects -> """
                SELECT
                    pr.nombre AS proyecto,
                    t.titulo AS tarea,
                    t.prioridad,
                    m.nombre AS responsable
                FROM tareas t
                INNER JOIN proyectos pr ON t.proyecto_id = pr.id
                LEFT JOIN miembros m ON t.miembro_id = m.id
                ORDER BY pr.nombre ASC;
            """.trimIndent()

            else -> """
                SELECT * FROM "$firstTable"
                LIMIT 20;
            """.trimIndent()
        }

        "group" -> when {
            isClinica -> """
                -- Recaudación y Volumen de Citas por Especialidad Médica
                SELECT
                    m.especialidad,
                    COUNT(c.id) AS total_citas,
                    ROUND(SUM(c.costo), 2) AS recaudacion_total,
                    ROUND(AVG(c.costo), 2) AS costo_promedio
                FROM medicos m
                LEFT JOIN citas c ON m.id = c.medico_id
                GROUP BY m.id, m.especialidad
                ORDER BY total_citas DESC, recaudacion_total DESC;
            """.trimIndent()

            isSecretaria -> """
                -- Matrícula y Promedio por Grupo Escolar
                SELECT
                    g.codigo_grupo AS grupo,
                    g.grado,
                    g.especialidad,
                    COUNT(e.id_estudiante) AS total_alumnos,
                    ROUND(AVG(e.promedio_ingreso), 2) AS promedio_grupo
                FROM grupos g
                LEFT JOIN estudiantes_matricula e ON g.id_grupo = e.id_grupo
                GROUP BY g.id_grupo, g.codigo_grupo
                ORDER BY g.grado, g.codigo_grupo;
            """.trimIndent()

            isOtorgamiento -> """
                -- Demanda de Carreras: Solicitudes en 1ra opción vs. total en las 10 opciones
                SELECT
                    c.nombre_carrera AS carrera,
                    u.siglas AS universidad,
                    COUNT(CASE WHEN b.orden_prioridad = 1 THEN 1 END) AS solicitudes_1ra_opcion,
                    COUNT(CASE WHEN b.orden_prioridad <= 3 THEN 1 END) AS en_primeras_3_opciones,
                    COUNT(b.id_boleta) AS total_menciones_en_boletas
                FROM carreras c
                INNER JOIN universidades u ON c.id_universidad = u.id_universidad
                LEFT JOIN boletas_solicitud b ON c.id_carrera = b.id_carrera
                GROUP BY c.id_carrera, c.nombre_carrera, u.siglas
                ORDER BY solicitudes_1ra_opcion DESC, total_menciones_en_boletas DESC;
            """.trimIndent()

            isFacultad -> """
                SELECT
                    m.nombre AS materia,
                    COUNT(e.id) AS total_estudiantes,
                    ROUND(AVG(e.promedio), 2) AS promedio_materia
                FROM materias m
                LEFT JOIN estudiantes e ON m.id = e.materia_id
                GROUP BY m.id, m.nombre
                ORDER BY promedio_materia DESC;
            """.trimIndent()

            isCuba -> """
                SELECT
                    p.region,
                    COUNT(p.id) AS cantidad_provincias,
                    SUM(p.poblacion) AS poblacion_total
                FROM provincias p
                GROUP BY p.region
                ORDER BY poblacion_total DESC;
            """.trimIndent()

            isTienda -> """
                SELECT
                    c.ciudad,
                    COUNT(c.id) AS cantidad_clientes,
                    ROUND(AVG(c.saldo), 2) AS saldo_medio
                FROM clientes c
                GROUP BY c.ciudad
                ORDER BY cantidad_clientes DESC;
            """.trimIndent()

            isEcommerce -> """
                SELECT
                    c.nombre AS categoria,
                    COUNT(a.id) AS total_articulos,
                    ROUND(AVG(a.precio), 2) AS precio_promedio
                FROM categorias c
                LEFT JOIN articulos a ON c.id = a.categoria_id
                GROUP BY c.id, c.nombre;
            """.trimIndent()

            isUniversity -> """
                SELECT
                    c.titulo AS curso,
                    COUNT(cal.id) AS evaluados,
                    ROUND(AVG(cal.nota), 2) AS nota_promedio
                FROM cursos c
                LEFT JOIN calificaciones cal ON c.id = cal.curso_id
                GROUP BY c.id, c.titulo;
            """.trimIndent()

            isProjects -> """
                SELECT
                    pr.nombre AS proyecto,
                    COUNT(t.id) AS total_tareas,
                    pr.presupuesto
                FROM proyectos pr
                LEFT JOIN tareas t ON pr.id = t.proyecto_id
                GROUP BY pr.id, pr.nombre;
            """.trimIndent()

            else -> """
                SELECT COUNT(*) AS total_filas FROM "$firstTable";
            """.trimIndent()
        }

        else -> "SELECT * FROM \"$firstTable\" LIMIT 10;"
    }
}


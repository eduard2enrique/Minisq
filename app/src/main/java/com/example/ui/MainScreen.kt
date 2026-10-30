package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DesignServices
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DatabaseManager
import com.example.data.QueryRepository
import com.example.model.SqlExecutionResult
import com.example.model.TableSummary
import com.example.ui.components.DatabaseSelectorDialog
import com.example.ui.components.NewDatabaseDialog
import com.example.ui.components.ProjectsHubDialog
import androidx.compose.material.icons.outlined.FolderOpen
import com.example.ui.components.HistorySideDrawer
import com.example.ui.screens.DesignerScreen
import com.example.ui.screens.ImportExportScreen
import com.example.ui.screens.QueryScreen
import com.example.ui.screens.TablesScreen
import com.example.ui.theme.SqlBlue
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    dbManager: DatabaseManager,
    queryRepo: QueryRepository,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Consulta, 1: Diseñar, 2: Importar, 3: Tablas
    var showDbSelectorDialog by remember { mutableStateOf(false) }
    var showNewDbDialog by remember { mutableStateOf(false) }

    var currentDbName by remember { mutableStateOf(dbManager.getCurrentDbName()) }
    var dbList by remember { mutableStateOf(dbManager.listDatabases()) }
    var tablesList by remember { mutableStateOf<List<TableSummary>>(emptyList()) }

    // History Side Drawer state
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val historyList by queryRepo.historyFlow.collectAsStateWithLifecycle(initialValue = queryRepo.getLast20Queries())

    // Externally selected SQL to load/execute in QueryScreen
    var externalSelectedSql by remember { mutableStateOf<String?>(null) }
    var externalTriggerExecute by remember { mutableStateOf(false) }

    // Refresh tables and database info
    fun refreshDatabaseState() {
        scope.launch {
            currentDbName = dbManager.getCurrentDbName()
            dbList = dbManager.listDatabases()
            tablesList = dbManager.getTables()
        }
    }

    LaunchedEffect(Unit) {
        refreshDatabaseState()
    }

    // CSV and JSON export helpers
    var pendingExportCsvData by remember { mutableStateOf<String?>(null) }
    var pendingExportJsonData by remember { mutableStateOf<String?>(null) }
    var pendingExportReportData by remember { mutableStateOf<String?>(null) }

    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null && pendingExportCsvData != null) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingExportCsvData!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Archivo CSV guardado.", Toast.LENGTH_SHORT).show()
                pendingExportCsvData = null
            }
        }
    }

    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null && pendingExportJsonData != null) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingExportJsonData!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Archivo JSON guardado.", Toast.LENGTH_SHORT).show()
                pendingExportJsonData = null
            }
        }
    }

    val createReportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null && pendingExportReportData != null) {
            scope.launch {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(pendingExportReportData!!.toByteArray(Charsets.UTF_8))
                }
                Toast.makeText(context, "Informe guardado correctamente.", Toast.LENGTH_SHORT).show()
                pendingExportReportData = null
            }
        }
    }

    val totalRowsCount = remember(tablesList) {
        tablesList.sumOf { it.rowCount }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HistorySideDrawer(
                history = historyList,
                onSelectQuery = { sql ->
                    selectedTab = 0
                    externalSelectedSql = sql
                    externalTriggerExecute = false
                    scope.launch { drawerState.close() }
                },
                onExecuteQuery = { sql ->
                    selectedTab = 0
                    externalSelectedSql = sql
                    externalTriggerExecute = true
                    scope.launch { drawerState.close() }
                },
                onDeleteQuery = { id -> queryRepo.deleteHistoryItem(id) },
                onClearHistory = { queryRepo.clearHistory() },
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // Footer matching screenshot with edge-to-edge background and navigation bar insets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HorizontalDivider(
                        modifier = Modifier
                            .width(100.dp)
                            .padding(bottom = 6.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        thickness = 1.5.dp
                    )
                    Text(
                        text = "Creado para la enseñanza de SQL",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "© 2026 Prof. Eduardo Enrique Sanamé Durán",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // 1. Top Header Bar (iOS / AI Studio Minimalist Nav)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // MiniSQL App Badge in smooth iOS squircle
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(SqlBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "PRO",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Black,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Text(
                                text = "MiniSQL Pro 1.0",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            Text(
                                text = "SQLite Studio",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        // Lateral History Drawer Toggle Button
                        IconButton(
                            onClick = {
                                scope.launch {
                                    if (drawerState.isClosed) drawerState.open() else drawerState.close()
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("top_history_drawer_btn")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (historyList.isNotEmpty()) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text("${historyList.size.coerceAtMost(20)}")
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.History,
                                    contentDescription = "Panel lateral de historial",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("theme_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                contentDescription = "Cambiar tema",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Button(
                            onClick = { showNewDbDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 9.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("new_db_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nueva BD",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // 2. Active Database Card in smooth iOS card styling
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { showDbSelectorDialog = true }
                        .testTag("active_db_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)) {
                                Text(
                                    text = "BASE ACTIVA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        letterSpacing = 1.2.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                val displayName = currentDbName.removeSuffix(".db")
                                val dynamicFontSize = when {
                                    displayName.length > 22 -> 11.5.sp
                                    displayName.length > 16 -> 13.sp
                                    else -> 15.sp
                                }
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = dynamicFontSize
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.TableChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "${tablesList.size} tablas • $totalRowsCount filas",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "▾",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Navigation Tabs in authentic iOS Segmented Control
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            Triple(0, "Consulta", Icons.Outlined.Terminal),
                            Triple(1, "Diseñar", Icons.Outlined.DesignServices),
                            Triple(2, "Importar", Icons.Outlined.UploadFile),
                            Triple(3, "Tablas", Icons.Outlined.TableChart)
                        )

                        tabs.forEach { (index, title, icon) ->
                            val isSelected = selectedTab == index
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(13.dp))
                                    .clickable { selectedTab = index }
                                    .testTag("tab_$index"),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent,
                                shape = RoundedCornerShape(13.dp),
                                border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)) else null,
                                tonalElevation = if (isSelected) 3.dp else 0.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Content Area for the active tab
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    BackHandler(enabled = selectedTab != 0) {
                        selectedTab = 0
                    }

                    when (selectedTab) {
                        0 -> QueryScreen(
                            dbManager = dbManager,
                            queryRepo = queryRepo,
                            tables = tablesList,
                            onRefreshTables = { refreshDatabaseState() },
                            isDark = isDarkTheme,
                            onExportCsvFile = { result ->
                                scope.launch {
                                    val csv = dbManager.exportQueryResultCsv(result)
                                    pendingExportCsvData = csv
                                    createCsvLauncher.launch("consulta_resultado_${System.currentTimeMillis()}.csv")
                                }
                            },
                            onExportJsonFile = { result ->
                                scope.launch {
                                    val json = dbManager.exportQueryResultJson(result)
                                    pendingExportJsonData = json
                                    createJsonLauncher.launch("consulta_resultado_${System.currentTimeMillis()}.json")
                                }
                            },
                            onExportReportFile = { reportText ->
                                pendingExportReportData = reportText
                                createReportLauncher.launch("informe_consulta_${System.currentTimeMillis()}.txt")
                            },
                            onOpenHistoryDrawer = {
                                scope.launch { drawerState.open() }
                            },
                            initialSqlToLoad = externalSelectedSql,
                            triggerExecute = externalTriggerExecute,
                            onConsumedInitialSql = {
                                externalSelectedSql = null
                                externalTriggerExecute = false
                            }
                        )
                        1 -> DesignerScreen(
                            dbManager = dbManager,
                            onRefreshTables = { refreshDatabaseState() }
                        )
                        2 -> ImportExportScreen(
                            dbManager = dbManager,
                            onDatabaseChanged = { refreshDatabaseState() },
                            onNavigateToTables = { selectedTab = 3 },
                            onNavigateToQuery = { sql ->
                                selectedTab = 0
                                if (sql != null) {
                                    externalSelectedSql = sql
                                    externalTriggerExecute = false
                                }
                            }
                        )
                        3 -> TablesScreen(
                            dbManager = dbManager,
                            tables = tablesList,
                            onRefreshTables = { refreshDatabaseState() },
                            activeDbName = currentDbName,
                            onNavigateToDesigner = { selectedTab = 1 },
                            onNavigateToImport = { selectedTab = 2 },
                            onNavigateToSql = { starterSql ->
                                selectedTab = 0
                                externalSelectedSql = starterSql
                                externalTriggerExecute = false
                            },
                            onResetCurrentDb = {
                                val ok = dbManager.resetDatabase(currentDbName)
                                if (ok) {
                                    refreshDatabaseState()
                                    Toast.makeText(context, "Base '$currentDbName' restablecida a su estado original.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal: Quick New Database Dialog
    if (showNewDbDialog) {
        NewDatabaseDialog(
            existingDatabases = dbList,
            onCreateBlank = { name ->
                val ok = dbManager.createNewDatabase(name, "empty")
                if (ok) {
                    refreshDatabaseState()
                    selectedTab = 1 // Immediately open table designer as requested by user!
                    Toast.makeText(context, "Base '$name' creada. ¡Diseña tu primera tabla!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Ya existe una base de datos con ese nombre.", Toast.LENGTH_SHORT).show()
                }
            },
            onCreateWithTemplate = { name, templateId ->
                val ok = dbManager.createNewDatabase(name, templateId)
                if (ok) {
                    refreshDatabaseState()
                    selectedTab = 3 // View tables
                    Toast.makeText(context, "Base '$name' creada con plantilla.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Ya existe una base de datos con ese nombre.", Toast.LENGTH_SHORT).show()
                }
            },
            onOpenProjectsHub = {
                showDbSelectorDialog = true
            },
            onDismiss = { showNewDbDialog = false }
        )
    }

    // Modal: Projects Hub & Database Manager Dialog
    if (showDbSelectorDialog) {
        ProjectsHubDialog(
            dbManager = dbManager,
            currentDb = currentDbName,
            databases = dbList,
            onSelectDb = { name ->
                dbManager.switchDatabase(name)
                refreshDatabaseState()
                Toast.makeText(context, "Base activa: $name", Toast.LENGTH_SHORT).show()
            },
            onCreateDb = { name, template ->
                val ok = dbManager.createNewDatabase(name, template)
                if (ok) {
                    refreshDatabaseState()
                    if (template == "empty") {
                        selectedTab = 1 // Open designer for blank DB
                    }
                    Toast.makeText(context, "Proyecto '$name' creado.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Ya existe un proyecto con ese nombre.", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteDb = { name ->
                val ok = dbManager.deleteDatabase(name)
                if (ok) {
                    refreshDatabaseState()
                    Toast.makeText(context, "Proyecto eliminado.", Toast.LENGTH_SHORT).show()
                }
            },
            onRenameDb = { oldName, newName ->
                val ok = dbManager.renameDatabase(oldName, newName)
                if (ok) {
                    refreshDatabaseState()
                    Toast.makeText(context, "Proyecto renombrado.", Toast.LENGTH_SHORT).show()
                }
            },
            onResetDb = { name ->
                val ok = dbManager.resetDatabase(name)
                if (ok) {
                    refreshDatabaseState()
                    Toast.makeText(context, "Base '$name' restablecida a su estado original.", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showDbSelectorDialog = false }
        )
    }
}

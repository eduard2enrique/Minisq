package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Upload
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
                // Footer matching screenshot with navigation bar insets
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .background(MaterialTheme.colorScheme.background)
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
                // 1. Top Header Bar matching screenshot
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // MiniSQL App Badge
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SqlBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SQL",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Text(
                                text = "MiniSQL",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "v2.0.33",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            modifier = Modifier.testTag("top_history_drawer_btn")
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
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Panel lateral de historial",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                selectedTab = 3 // Quick jump to tables search
                            },
                            modifier = Modifier.testTag("top_search_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = onToggleTheme,
                            modifier = Modifier.testTag("theme_toggle_btn")
                        ) {
                            Icon(
                                imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Cambiar tema",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = { showDbSelectorDialog = true },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(36.dp)
                                .testTag("new_db_button")
                        ) {
                            Text(
                                text = "Nueva BD",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // 2. Active Database Card matching screenshot
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { showDbSelectorDialog = true }
                        .testTag("active_db_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = "BASE ACTIVA",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentDbName.removeSuffix(".db"),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 15.sp
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${tablesList.size} tablas • $totalRowsCount filas ▼",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 3. Navigation Tabs matching screenshot
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tabs = listOf(
                        Triple(0, ">_ Consulta", Icons.Default.Terminal),
                        Triple(1, "Diseñar", Icons.Default.Draw),
                        Triple(2, "Importar", Icons.Default.Upload),
                        Triple(3, "Tablas", Icons.Default.Storage)
                    )

                    tabs.forEach { (index, title, icon) ->
                        val isSelected = selectedTab == index
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { selectedTab = index }
                                .testTag("tab_$index"),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
                            onDatabaseChanged = { refreshDatabaseState() }
                        )
                        3 -> TablesScreen(
                            dbManager = dbManager,
                            tables = tablesList,
                            onRefreshTables = { refreshDatabaseState() }
                        )
                    }
                }
            }
        }
    }

    // Modal: Database Selector & Manager Dialog
    if (showDbSelectorDialog) {
        DatabaseSelectorDialog(
            currentDb = currentDbName,
            databases = dbList,
            onSelectDb = { name ->
                dbManager.switchDatabase(name)
                refreshDatabaseState()
                Toast.makeText(context, "Base de datos activa: $name", Toast.LENGTH_SHORT).show()
            },
            onCreateDb = { name, template ->
                val ok = dbManager.createNewDatabase(name, template)
                if (ok) {
                    refreshDatabaseState()
                    Toast.makeText(context, "Base de datos '$name' creada.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Ya existe una base de datos con ese nombre.", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteDb = { name ->
                dbManager.deleteDatabase(name)
                refreshDatabaseState()
                Toast.makeText(context, "Base de datos eliminada.", Toast.LENGTH_SHORT).show()
            },
            onRenameDb = { oldName, newName ->
                val ok = dbManager.renameDatabase(oldName, newName)
                if (ok) {
                    refreshDatabaseState()
                    Toast.makeText(context, "Base de datos renombrada.", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showDbSelectorDialog = false }
        )
    }
}

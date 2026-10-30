package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DatabaseManager
import com.example.model.ColumnInfo
import com.example.model.SqlExecutionResult
import com.example.model.TableSchemaDetail
import com.example.model.TableSummary
import com.example.ui.theme.SqlWarning
import kotlinx.coroutines.launch

@Composable
fun TablesScreen(
    dbManager: DatabaseManager,
    tables: List<TableSummary>,
    onRefreshTables: () -> Unit,
    activeDbName: String = dbManager.getCurrentDbName(),
    onNavigateToDesigner: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onNavigateToSql: (String) -> Unit = {},
    onResetCurrentDb: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTableName by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selectedTableName != null) {
        selectedTableName = null
    }

    // Table detail state
    var selectedTableSchema by remember { mutableStateOf<TableSchemaDetail?>(null) }
    var tableDataResult by remember { mutableStateOf<SqlExecutionResult?>(null) }
    var selectedDetailTab by remember { mutableIntStateOf(0) } // 0: Datos, 1: Estructura, 2: SQL DDL
    var isLoadingData by remember { mutableStateOf(false) }

    // Dialog state
    var showInsertRowDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showDropConfirmDialog by remember { mutableStateOf(false) }
    var showAddColumnDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var columnToDelete by remember { mutableStateOf<String?>(null) }
    var editingRowIndex by remember { mutableStateOf<Int?>(null) }

    // SQLite-style row selection state
    val selectedRowIndices = remember { mutableStateListOf<Int>() }
    var showDeleteMultipleDialog by remember { mutableStateOf(false) }

    fun loadTableData(tableName: String, showFeedback: Boolean = false) {
        isLoadingData = true
        selectedRowIndices.clear()
        scope.launch {
            val schema = dbManager.getTableSchema(tableName)
            selectedTableSchema = schema
            val dataRes = dbManager.executeSql("SELECT * FROM \"$tableName\" LIMIT 200;")
            tableDataResult = dataRes
            isLoadingData = false
            if (showFeedback) {
                val rowCount = dataRes.rows.size
                Toast.makeText(context, "Tabla recargada: $rowCount registro(s)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(selectedTableName) {
        selectedTableName?.let { loadTableData(it) }
    }

    val filteredTables = remember(tables, searchQuery) {
        if (searchQuery.isBlank()) tables
        else tables.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        if (selectedTableName == null) {
            if (tables.isEmpty()) {
                // Empty state matching the user's mockup
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header: Icon + "Tablas"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tablas",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Base ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val cleanDb = activeDbName.removeSuffix(".db")
                        val dynamicFontSize = when {
                            cleanDb.length > 22 -> 11.5.sp
                            cleanDb.length > 15 -> 13.sp
                            else -> 14.sp
                        }
                        Text(
                            text = cleanDb,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = dynamicFontSize),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = " · todavía sin tablas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Large clipboard icon
                    Icon(
                        imageVector = Icons.Outlined.Assignment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(76.dp)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Base de datos lista",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Elige cómo quieres crear tu primera tabla.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    // Button 1: + Crear tabla
                    Button(
                        onClick = onNavigateToDesigner,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(48.dp)
                            .testTag("empty_state_create_table_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Crear tabla",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Button 2: Importar CSV / Excel
                    FilledTonalButton(
                        onClick = onNavigateToImport,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(48.dp)
                            .testTag("empty_state_import_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Importar CSV / Excel",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Button 3: >_ Crear con SQL
                    TextButton(
                        onClick = {
                            val starterSql = "-- Crear primera tabla en ${activeDbName.removeSuffix(".db")} (segura contra duplicados)\nCREATE TABLE IF NOT EXISTS \"mi_primera_tabla\" (\n  \"id\" INTEGER PRIMARY KEY AUTOINCREMENT,\n  \"nombre\" TEXT NOT NULL,\n  \"fecha_creacion\" TEXT DEFAULT CURRENT_TIMESTAMP\n);\n\n-- Inserción segura que no falla al re-ejecutar:\nINSERT OR IGNORE INTO \"mi_primera_tabla\" (\"id\", \"nombre\")\nVALUES (1, 'Primer Registro');\n\nSELECT * FROM \"mi_primera_tabla\";\n"
                            onNavigateToSql(starterSql)
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(44.dp)
                            .testTag("empty_state_sql_btn")
                    ) {
                        Text(
                            text = ">_ ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Crear con SQL",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Table Overview List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar tablas en ${activeDbName.removeSuffix(".db")}...", style = MaterialTheme.typography.bodySmall) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .padding(end = 8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onRefreshTables) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Actualizar")
                        }
                        if (onResetCurrentDb != null) {
                            IconButton(
                                onClick = { showResetConfirmDialog = true },
                                modifier = Modifier.testTag("reset_db_header_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RestartAlt,
                                    contentDescription = "Restablecer de fábrica",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredTables.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "No se encontraron tablas que coincidan con \"$searchQuery\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
                    ) {
                    items(filteredTables) { table ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTableName = table.name },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.TableChart,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = table.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${table.rowCount} filas • ${table.columnCount} columnas",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text("Ver detalles ➔", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Selected Table Detail View
            val tableName = selectedTableName!!
            val schema = selectedTableSchema

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
                ) {
                    IconButton(onClick = {
                        selectedTableName = null
                        onRefreshTables()
                    }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver")
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        val dynamicTableFontSize = when {
                            tableName.length > 22 -> 12.sp
                            tableName.length > 15 -> 14.sp
                            else -> 16.sp
                        }
                        Text(
                            text = tableName,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = dynamicTableFontSize),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${tableDataResult?.rows?.size ?: 0} registros",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(
                        onClick = { showInsertRowDialog = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Fila", style = MaterialTheme.typography.labelSmall)
                    }

                    IconButton(
                        onClick = { loadTableData(tableName, showFeedback = true) },
                        modifier = Modifier.testTag("reload_table_data_btn")
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Recargar datos de la tabla")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-tabs: Datos, Estructura, SQL DDL
            TabRow(
                selectedTabIndex = selectedDetailTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedDetailTab == 0,
                    onClick = { selectedDetailTab = 0 },
                    text = { Text("Datos") }
                )
                Tab(
                    selected = selectedDetailTab == 1,
                    onClick = { selectedDetailTab = 1 },
                    text = { Text("Estructura") }
                )
                Tab(
                    selected = selectedDetailTab == 2,
                    onClick = { selectedDetailTab = 2 },
                    text = { Text("SQL DDL") }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoadingData) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedDetailTab) {
                    0 -> {
                        // Data View
                        val data = tableDataResult
                        if (data == null || data.rows.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.TableChart,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            "La tabla '${tableName}' no tiene registros",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Comienza añadiendo el primer registro con el asistente didáctico.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { showInsertRowDialog = true },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Insertar Primer Registro")
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    // SQLite Contextual Selection Banner
                                    if (selectedRowIndices.isNotEmpty()) {
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 6.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "${selectedRowIndices.size} fila(s) marcada(s)",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                                    )
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    IconButton(onClick = {
                                                        val csv = buildString {
                                                            append(data.columns.joinToString(",") + "\n")
                                                            selectedRowIndices.sorted().forEach { rIdx ->
                                                                append(data.rows[rIdx].joinToString(",") + "\n")
                                                            }
                                                        }
                                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        cb.setPrimaryClip(ClipData.newPlainText("Filas seleccionadas", csv))
                                                        Toast.makeText(context, "${selectedRowIndices.size} fila(s) copiada(s) al portapapeles", Toast.LENGTH_SHORT).show()
                                                    }) {
                                                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar filas", tint = MaterialTheme.colorScheme.primary)
                                                    }

                                                    IconButton(onClick = { showDeleteMultipleDialog = true }) {
                                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Eliminar filas", tint = MaterialTheme.colorScheme.error)
                                                    }

                                                    IconButton(onClick = { selectedRowIndices.clear() }) {
                                                        Icon(Icons.Outlined.Close, contentDescription = "Cancelar selección")
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    val hScroll = rememberScrollState()
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .horizontalScroll(hScroll)
                                        ) {
                                            // Header
                                            val allSelected = data.rows.isNotEmpty() && selectedRowIndices.size == data.rows.size
                                            Row(
                                                modifier = Modifier
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Checkbox All (SQLite Header Square)
                                                Box(
                                                    modifier = Modifier.width(42.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Checkbox(
                                                        checked = allSelected,
                                                        onCheckedChange = { isChecked ->
                                                            if (isChecked) {
                                                                selectedRowIndices.clear()
                                                                selectedRowIndices.addAll(data.rows.indices)
                                                            } else {
                                                                selectedRowIndices.clear()
                                                            }
                                                        },
                                                        colors = CheckboxDefaults.colors(
                                                            checkedColor = MaterialTheme.colorScheme.primary
                                                        ),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                }

                                                Text("#", modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)

                                                data.columns.forEach { col ->
                                                    val colInfo = schema?.columns?.find { it.name.equals(col, ignoreCase = true) }
                                                    val isPk = colInfo?.isPrimaryKey == true
                                                    Row(
                                                        modifier = Modifier
                                                            .width(130.dp)
                                                            .padding(horizontal = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        if (isPk) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Key,
                                                                contentDescription = "Clave primaria",
                                                                tint = Color(0xFFFBBF24),
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                        }
                                                        Text(
                                                            text = col,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.weight(1f, fill = false),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = MaterialTheme.typography.bodySmall
                                                        )
                                                        if (colInfo != null) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                            ) {
                                                                Text(
                                                                    text = colInfo.type.take(4),
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                Text("Acciones", modifier = Modifier.width(70.dp), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            }

                                            // Rows
                                            LazyColumn(
                                                modifier = Modifier.weight(1f),
                                                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp)
                                            ) {
                                                itemsIndexed(data.rows) { idx, row ->
                                                    val isSelected = selectedRowIndices.contains(idx)
                                                    val rowBg = when {
                                                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                        idx % 2 == 0 -> MaterialTheme.colorScheme.surface
                                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .background(rowBg)
                                                            .clickable {
                                                                if (isSelected) selectedRowIndices.remove(idx)
                                                                else selectedRowIndices.add(idx)
                                                            }
                                                            .padding(vertical = 4.dp, horizontal = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        // SQLite Row Checkbox Square
                                                        Box(
                                                            modifier = Modifier.width(42.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Checkbox(
                                                                checked = isSelected,
                                                                onCheckedChange = { checked ->
                                                                    if (checked) selectedRowIndices.add(idx)
                                                                    else selectedRowIndices.remove(idx)
                                                                },
                                                                colors = CheckboxDefaults.colors(
                                                                    checkedColor = MaterialTheme.colorScheme.primary
                                                                ),
                                                                modifier = Modifier.size(24.dp)
                                                            )
                                                        }

                                                        Text("${idx + 1}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall)
                                                        row.forEach { valStr ->
                                                            Text(
                                                                text = valStr,
                                                                modifier = Modifier.width(130.dp).padding(horizontal = 4.dp),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 2,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                        // Row action buttons: Edit & Delete
                                                        Row(
                                                            modifier = Modifier.width(70.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            IconButton(
                                                                onClick = { editingRowIndex = idx },
                                                                modifier = Modifier.size(28.dp).testTag("edit_row_${idx}_btn")
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.Edit,
                                                                    contentDescription = "Editar fila",
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    val pkCol = schema?.columns?.find { it.isPrimaryKey }?.name ?: data.columns.firstOrNull() ?: "id"
                                                                    val pkIndex = data.columns.indexOf(pkCol)
                                                                    val pkVal = row.getOrNull(if (pkIndex >= 0) pkIndex else 0) ?: ""
                                                                    scope.launch {
                                                                        dbManager.deleteTableRow(tableName, pkCol, pkVal)
                                                                        loadTableData(tableName)
                                                                    }
                                                                },
                                                                modifier = Modifier.size(28.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.DeleteOutline,
                                                                    contentDescription = "Borrar fila",
                                                                    tint = MaterialTheme.colorScheme.error,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                // Fila interactiva moderna para añadir nuevo registro (Estilo Notion / Supabase)
                                                item {
                                                    Surface(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { showInsertRowDialog = true }
                                                            .testTag("insert_row_below_last_btn"),
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 9.dp, horizontal = 12.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.Start
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Outlined.Add,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Text(
                                                                text = "Nueva fila en '$tableName'...",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.weight(1f, fill = false)
                                                            )
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                            ) {
                                                                Text(
                                                                    text = "+ Añadir",
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Barra limpia de resumen y acción de inserción rápida
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp, bottom = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${data.rows.size} filas • ${data.columns.size} columnas",
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )

                                        FilledTonalButton(
                                            onClick = { showInsertRowDialog = true },
                                            modifier = Modifier.height(34.dp).testTag("bottom_insert_row_btn"),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Nueva Fila", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                // Prominent Floating Action Button for Inserting Records
                                ExtendedFloatingActionButton(
                                    onClick = { showInsertRowDialog = true },
                                    icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                                    text = { Text("Nuevo Registro") },
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp)
                                        .testTag("table_add_row_fab")
                                )
                            }
                        }
                    }

                    1 -> {
                        // Structure View
                        if (schema != null) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Columnas (${schema.columns.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Button(
                                            onClick = { showAddColumnDialog = true },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Añadir Columna", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                                items(schema.columns) { col ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                if (col.isPrimaryKey) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Key,
                                                        contentDescription = "PK",
                                                        tint = SqlWarning,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                }
                                                Column {
                                                    Text(
                                                        text = col.name,
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                    if (!col.defaultValue.isNullOrBlank()) {
                                                        Text(
                                                            text = "default: ${col.defaultValue}",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        text = col.type.ifBlank { "TEXT" },
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (col.notNull) {
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = "NOT NULL",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                // Delete Column Button
                                                IconButton(
                                                    onClick = { columnToDelete = col.name },
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.DeleteOutline,
                                                        contentDescription = "Eliminar columna",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(17.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (schema.foreignKeys.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Claves Foráneas (Relaciones)", style = MaterialTheme.typography.titleSmall)
                                    }
                                    items(schema.foreignKeys) { fk ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                        ) {
                                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Outlined.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("${fk.fromColumn} ➔ ${fk.targetTable}(${fk.toColumn})", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showDeleteConfirmDialog = true },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Vaciar Filas")
                                        }

                                        Button(
                                            onClick = { showDropConfirmDialog = true },
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Eliminar Tabla")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // SQL DDL View
                        val createSql = schema?.createSql ?: "-- No hay DDL disponible"
                        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Definición SQLite:", style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("SQL DDL", createSql))
                                    Toast.makeText(context, "DDL copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Outlined.ContentCopy, contentDescription = "Copiar SQL")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = createSql,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal: Smart Educational Insert Row Dialog (with FK Pickers, Live SQL & IME Safety)
    if (showInsertRowDialog && selectedTableSchema != null) {
        val schema = selectedTableSchema!!
        val inputValues = remember { mutableStateMapOf<String, String>() }
        val fkCandidates = remember { mutableStateMapOf<String, List<Pair<String, String>>>() }
        var isInserting by remember { mutableStateOf(false) }

        LaunchedEffect(schema) {
            schema.foreignKeys.forEach { fk ->
                scope.launch {
                    val opts = dbManager.getReferencedTableOptions(fk.targetTable, fk.toColumn)
                    fkCandidates[fk.fromColumn] = opts
                }
            }
        }

        // Educational Live SQL Generator
        val liveSql = remember(inputValues.toMap()) {
            val nonBlank = inputValues.filter { it.value.isNotBlank() }
            if (nonBlank.isEmpty()) {
                "INSERT INTO \"${schema.tableName}\" DEFAULT VALUES;"
            } else {
                val cols = nonBlank.keys.joinToString(", ") { "\"$it\"" }
                val vals = nonBlank.values.joinToString(", ") { v ->
                    if (v.toDoubleOrNull() != null) v else "'$v'"
                }
                "INSERT INTO \"${schema.tableName}\" ($cols)\nVALUES ($vals);"
            }
        }

        Dialog(
            onDismissRequest = { if (!isInserting) showInsertRowDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Add,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val tName = schema.tableName
                                val dynamicTitleSize = when {
                                    tName.length > 20 -> 13.sp
                                    tName.length > 14 -> 14.5.sp
                                    else -> 16.sp
                                }
                                Text(
                                    text = "Nuevo Registro en '$tName'",
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = dynamicTitleSize),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { showInsertRowDialog = false }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cerrar")
                            }
                        }

                        Text(
                            text = "Rellena los valores de los campos. Las claves foráneas y fechas cuentan con ayudantes automáticos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Campos del esquema (${schema.columns.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            FilledTonalButton(
                                onClick = {
                                    val randomNum = (10..99).random()
                                    schema.columns.forEach { col ->
                                        val lower = col.name.lowercase()
                                        val isAutoPk = col.isPrimaryKey && col.type.contains("INT", ignoreCase = true)
                                        if (!isAutoPk) {
                                            val generated = when {
                                                lower.contains("email") || lower.contains("correo") -> "usuario$randomNum@ejemplo.com"
                                                lower.contains("nom") || lower.contains("name") -> listOf("Ana Morales", "Carlos Ruiz", "Lucía Sanz", "Javier Soto", "Marta Gil").random()
                                                lower.contains("ciudad") || lower.contains("city") -> listOf("Madrid", "Barcelona", "Valencia", "Sevilla", "Bilbao").random()
                                                lower.contains("precio") || lower.contains("saldo") || lower.contains("costo") || lower.contains("total") || lower.contains("valor") || lower.contains("presupuesto") -> "${(15..450).random()}.${(10..99).random()}"
                                                lower.contains("stock") || lower.contains("cant") || lower.contains("edad") || lower.contains("credito") -> "${(1..50).random()}"
                                                lower.contains("fecha") || lower.contains("date") -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                lower.contains("estado") || lower.contains("status") -> listOf("Activo", "Pendiente", "Completado").random()
                                                lower.contains("rol") -> listOf("cliente", "admin", "editor").random()
                                                lower.contains("prioridad") -> listOf("Alta", "Media", "Baja").random()
                                                lower.contains("telefono") || lower.contains("phone") -> "+34 600 ${(100000..999999).random()}"
                                                lower.contains("tit") || lower.contains("desc") -> "Registro de prueba #$randomNum"
                                                col.type.contains("INT", ignoreCase = true) -> "$randomNum"
                                                col.type.contains("REAL", ignoreCase = true) -> "$randomNum.50"
                                                else -> "Valor $randomNum"
                                            }
                                            inputValues[col.name] = generated
                                        }
                                    }
                                },
                                modifier = Modifier.height(32.dp).testTag("magic_fill_button"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "⚡ Rellenar datos demo",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = true)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            schema.columns.forEach { col ->
                                val isAutoPk = col.isPrimaryKey && col.type.contains("INT", ignoreCase = true)
                                val fk = schema.foreignKeys.find { it.fromColumn == col.name }
                                val options = fkCandidates[col.name] ?: emptyList()
                                var showFkDropdown by remember { mutableStateOf(false) }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (col.isPrimaryKey) SqlWarning.copy(alpha = 0.5f)
                                        else if (fk != null) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        // Header badge per column type
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (col.isPrimaryKey) {
                                                    Icon(Icons.Outlined.Key, contentDescription = "PK", tint = SqlWarning, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Clave Primaria (PK)", style = MaterialTheme.typography.labelSmall, color = SqlWarning, fontWeight = FontWeight.Bold)
                                                } else if (fk != null) {
                                                    Icon(Icons.Outlined.Link, contentDescription = "FK", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Relación ➔ ${fk.targetTable}(${fk.toColumn})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                                } else {
                                                    Text(col.type, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }

                                            // Date shortcut helper
                                            if (col.type.contains("DATE", ignoreCase = true) || col.name.contains("fecha", ignoreCase = true) || col.name.contains("date", ignoreCase = true)) {
                                                AssistChip(
                                                    onClick = {
                                                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                                        inputValues[col.name] = today
                                                    },
                                                    label = { Text("Hoy", style = MaterialTheme.typography.labelSmall) },
                                                    leadingIcon = { Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(14.dp)) },
                                                    modifier = Modifier.height(28.dp),
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                                    )
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        OutlinedTextField(
                                            value = inputValues[col.name] ?: "",
                                            onValueChange = { inputValues[col.name] = it },
                                            label = { Text("${col.name} (${col.type})${if (col.notNull && !isAutoPk) " *" else ""}") },
                                            placeholder = {
                                                if (isAutoPk) Text("(Automático por SQLite)")
                                                else if (col.defaultValue != null) Text("Default: ${col.defaultValue}")
                                                else if (fk != null) Text("ID de ${fk.targetTable}")
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp)
                                        )

                                        // Foreign Key Smart Picker Dropdown
                                        if (fk != null && options.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Box {
                                                FilledTonalButton(
                                                    onClick = { showFkDropdown = true },
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (inputValues[col.name].isNullOrBlank()) "Seleccionar de ${fk.targetTable}..."
                                                               else "Vinculado a: ${options.find { it.first == inputValues[col.name] }?.second ?: inputValues[col.name]}",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Icon(Icons.Outlined.ArrowDropDown, contentDescription = null)
                                                }

                                                DropdownMenu(
                                                    expanded = showFkDropdown,
                                                    onDismissRequest = { showFkDropdown = false }
                                                ) {
                                                    options.forEach { opt ->
                                                        DropdownMenuItem(
                                                            text = {
                                                                Text("${opt.second} (ID=${opt.first})", style = MaterialTheme.typography.bodySmall)
                                                            },
                                                            onClick = {
                                                                inputValues[col.name] = opt.first
                                                                showFkDropdown = false
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Educational Live SQL Preview
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sentencia SQL generada en vivo (Educativo):",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 100.dp)
                            ) {
                                Text(
                                    text = liveSql,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .verticalScroll(rememberScrollState())
                                )
                            }
                        }

                        // Permanently Anchored Bottom Action Bar: Always Elevated, Solid, and Visible
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isInserting = true
                                            val success = dbManager.insertTableRow(schema.tableName, inputValues.toMap())
                                            isInserting = false
                                            if (success) {
                                                loadTableData(schema.tableName)
                                                inputValues.clear()
                                                Toast.makeText(context, "Registro insertado. Listo para el siguiente.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Error al insertar registro.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("save_and_add_another_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isInserting
                                ) {
                                    Text("+ Guardar y Otro", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isInserting = true
                                            val success = dbManager.insertTableRow(schema.tableName, inputValues.toMap())
                                            isInserting = false
                                            if (success) {
                                                loadTableData(schema.tableName)
                                                showInsertRowDialog = false
                                                Toast.makeText(context, "Registro guardado exitosamente.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Error al insertar registro.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("save_row_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isInserting
                                ) {
                                    Text("Guardar", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Edit Existing Row Dialog (with Pinned Bottom Save Button & Pre-populated Fields)
    val editRowIdx = editingRowIndex
    val curData = tableDataResult
    if (editRowIdx != null && selectedTableSchema != null && curData != null && editRowIdx < curData.rows.size) {
        val schema = selectedTableSchema!!
        val targetRow = curData.rows[editRowIdx]
        val pkCol = schema.columns.find { it.isPrimaryKey }?.name ?: curData.columns.firstOrNull() ?: "id"
        val pkIndex = curData.columns.indexOf(pkCol)
        val pkVal = targetRow.getOrNull(if (pkIndex >= 0) pkIndex else 0) ?: ""

        val editInputValues = remember(editRowIdx) {
            mutableStateMapOf<String, String>().apply {
                curData.columns.forEachIndexed { i, colName ->
                    this[colName] = targetRow.getOrNull(i) ?: ""
                }
            }
        }
        var isUpdating by remember { mutableStateOf(false) }

        Dialog(
            onDismissRequest = { if (!isUpdating) editingRowIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .safeDrawingPadding()
                    .imePadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Header Row with Title and Close Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false).padding(end = 6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val tName = schema.tableName
                                val dynamicTitleSize = when {
                                    tName.length > 20 -> 13.sp
                                    tName.length > 14 -> 14.5.sp
                                    else -> 16.sp
                                }
                                Text(
                                    text = "Editar Fila #${editRowIdx + 1} en '$tName'",
                                    style = MaterialTheme.typography.titleMedium.copy(fontSize = dynamicTitleSize),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { editingRowIndex = null }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Cerrar")
                            }
                        }

                        Text(
                            text = "Modifica los campos del registro. La clave primaria '$pkCol' ($pkVal) se utiliza para identificar la fila única.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Scrollable Input Fields Container
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            schema.columns.forEach { col ->
                                val isPk = col.isPrimaryKey
                                val currentValue = editInputValues[col.name] ?: ""

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isPk) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = col.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (isPk) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = SqlWarning.copy(alpha = 0.2f)
                                                    ) {
                                                        Text(
                                                            text = "PK",
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                            color = SqlWarning,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            ) {
                                                Text(
                                                    text = col.type,
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        OutlinedTextField(
                                            value = currentValue,
                                            onValueChange = { newVal ->
                                                editInputValues[col.name] = newVal
                                            },
                                            enabled = !isPk,
                                            modifier = Modifier.fillMaxWidth().testTag("edit_field_${col.name}"),
                                            placeholder = { Text("Valor para ${col.name}...") },
                                            singleLine = true,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Permanently Anchored Bottom Action Bar: Always Elevated, Solid, and Visible Above Keyboard
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp,
                            shadowElevation = 8.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { editingRowIndex = null },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("cancel_edit_row_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isUpdating
                                ) {
                                    Text("Cancelar", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            isUpdating = true
                                            val success = dbManager.updateTableRow(schema.tableName, pkCol, pkVal, editInputValues.toMap())
                                            isUpdating = false
                                            if (success) {
                                                loadTableData(schema.tableName)
                                                editingRowIndex = null
                                                Toast.makeText(context, "Registro actualizado exitosamente.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Error al actualizar registro.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("save_edit_row_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isUpdating
                                ) {
                                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Guardar Cambios", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Delete Multiple Selected Rows Confirmation
    if (showDeleteMultipleDialog && selectedTableName != null && selectedRowIndices.isNotEmpty()) {
        val tName = selectedTableName!!
        val count = selectedRowIndices.size
        val data = tableDataResult
        val pkCol = selectedTableSchema?.columns?.find { it.isPrimaryKey }?.name ?: data?.columns?.firstOrNull() ?: "id"
        val pkIndex = data?.columns?.indexOf(pkCol) ?: 0

        val pkVals = selectedRowIndices.mapNotNull { rIdx ->
            data?.rows?.getOrNull(rIdx)?.getOrNull(if (pkIndex >= 0) pkIndex else 0)
        }

        AlertDialog(
            onDismissRequest = { showDeleteMultipleDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("¿Eliminar $count fila(s)?")
                }
            },
            text = {
                Text("Se eliminarán permanentemente las $count filas seleccionadas de la tabla '$tName'. Esta operación no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val res = dbManager.deleteMultipleRows(tName, pkCol, pkVals)
                            Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                            selectedRowIndices.clear()
                            loadTableData(tName)
                            showDeleteMultipleDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar $count Fila(s)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMultipleDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal: Add Column to Existing Table (with Keyboard / IME Safety)
    if (showAddColumnDialog && selectedTableName != null) {
        val tName = selectedTableName!!
        var newColName by remember { mutableStateOf("") }
        var newColType by remember { mutableStateOf("TEXT") }
        var newColNotNull by remember { mutableStateOf(false) }
        var newColDefault by remember { mutableStateOf("") }

        Dialog(
            onDismissRequest = { showAddColumnDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .safeDrawingPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Añadir Columna a '$tName'",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = newColName,
                                onValueChange = { newColName = it },
                                label = { Text("Nombre de la nueva columna *") },
                                placeholder = { Text("ej. telefono, descuento") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )

                            Text("Tipo de dato:", style = MaterialTheme.typography.labelSmall)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("INTEGER", "TEXT", "REAL", "BLOB").forEach { t ->
                                    val isSel = newColType == t
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { newColType = t },
                                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = t,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material3.Checkbox(
                                    checked = newColNotNull,
                                    onCheckedChange = { newColNotNull = it }
                                )
                                Text("NOT NULL", style = MaterialTheme.typography.bodySmall)
                            }

                            OutlinedTextField(
                                value = newColDefault,
                                onValueChange = { newColDefault = it },
                                label = { Text("Valor por defecto (opcional)") },
                                placeholder = { Text("ej. 0 o 'activo'") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showAddColumnDialog = false }) {
                                Text("Cancelar")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        val res = dbManager.addColumn(tName, newColName, newColType, newColNotNull, newColDefault)
                                        Toast.makeText(context, res.message, if (res.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                                        if (res.isSuccess) {
                                            loadTableData(tName)
                                            onRefreshTables()
                                            showAddColumnDialog = false
                                        }
                                    }
                                },
                                enabled = newColName.isNotBlank(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Crear Columna")
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Delete Column Confirmation
    if (columnToDelete != null && selectedTableName != null) {
        val col = columnToDelete!!
        val tName = selectedTableName!!

        AlertDialog(
            onDismissRequest = { columnToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("¿Eliminar columna '$col'?")
                }
            },
            text = {
                Text("Se eliminará permanentemente la columna '$col' de la tabla '$tName' y todos los datos contenidos en ella. Esta acción no se puede deshacer.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val res = dbManager.dropColumn(tName, col)
                            Toast.makeText(context, res.message, if (res.isSuccess) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
                            loadTableData(tName)
                            onRefreshTables()
                            columnToDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar Columna")
                }
            },
            dismissButton = {
                TextButton(onClick = { columnToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Modal: Truncate confirmation
    if (showDeleteConfirmDialog && selectedTableName != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("¿Vaciar tabla?") },
            text = { Text("Se eliminarán todos los registros de la tabla '$selectedTableName'. Esta acción no se puede deshacer.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            dbManager.clearTable(selectedTableName!!)
                            loadTableData(selectedTableName!!)
                            showDeleteConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Vaciar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Modal: Drop table confirmation
    if (showDropConfirmDialog && selectedTableName != null) {
        AlertDialog(
            onDismissRequest = { showDropConfirmDialog = false },
            title = { Text("¿Eliminar tabla?") },
            text = { Text("Se eliminará completamente la tabla '$selectedTableName' y su estructura.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            dbManager.dropTable(selectedTableName!!)
                            selectedTableName = null
                            onRefreshTables()
                            showDropConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { showDropConfirmDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Modal: Reset active database confirmation
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CleaningServices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restablecer Base de Datos")
                }
            },
            text = {
                Text(
                    "¿Deseas restaurar \"$activeDbName\" a su estado original de fábrica?\n\nSe restablecerán las tablas y datos de ejemplo iniciales (o se limpiará a 0 tablas si fue creada en blanco). Los cambios manuales se descartarán.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        selectedTableName = null
                        onResetCurrentDb?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Restablecer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
